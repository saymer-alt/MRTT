//! A Jitsi Meet room as a datagram carrier. Each participant is a guest with its own WebRTC connection to the
//! videobridge (JVB); bytes travel as `EndpointMessage`s over the bridge datachannel, addressed to one endpoint.
//! Everything runs inside this process: XMPP over WebSocket for signalling, webrtc-rs for ICE/DTLS/SCTP.

use anyhow::{Context, Result, anyhow, bail};
use base64::Engine as _;
use futures_util::{SinkExt, StreamExt};
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::{mpsc, watch};
use tokio_tungstenite::tungstenite::{Message, client::IntoClientRequest};
use webrtc::data_channel::{DataChannel, DataChannelEvent, RTCDataChannelInit};
use webrtc::peer_connection::{
    PeerConnection, PeerConnectionBuilder, PeerConnectionEventHandler, RTCIceGatheringState,
    RTCPeerConnectionState, RTCSessionDescription,
};

/// Display name prefix of the MRTT server's participants; clients address their packets to these endpoints.
pub const SERVER_DISPLAY: &str = "mrtt-s";
const STEP_TIMEOUT: Duration = Duration::from_secs(20);
const JOIN_TIMEOUT: Duration = Duration::from_secs(60);
const KEEPALIVE: Duration = Duration::from_secs(25);
const INBOUND_QUEUE: usize = 1024;
/// Docker installs (docker-jitsi-meet) serve this XMPP domain whatever the web host is.
const DOCKER_DOMAIN: &str = "meet.jitsi";
const BASE64: base64::engine::GeneralPurpose = base64::engine::general_purpose::STANDARD;

/// The features Jicofo expects from a Jitsi Meet client before it sends the session-initiate.
const FEATURES: &[&str] = &[
    "http://jabber.org/protocol/caps",
    "http://jitsi.org/json-encoded-sources",
    "http://jitsi.org/receive-multiple-video-streams",
    "http://jitsi.org/remb",
    "http://jitsi.org/source-name",
    "http://jitsi.org/tcc",
    "http://jitsi.org/visitors-1",
    "urn:ietf:rfc:4588",
    "urn:xmpp:jingle:1",
    "urn:xmpp:jingle:apps:dtls:0",
    "urn:xmpp:jingle:apps:rtp:1",
    "urn:xmpp:jingle:apps:rtp:audio",
    "urn:xmpp:jingle:apps:rtp:video",
    "urn:xmpp:jingle:transports:dtls-sctp:1",
    "urn:xmpp:jingle:transports:ice-udp:1",
];

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct RoomTarget {
    pub host: String,
    pub room: String,
}

impl RoomTarget {
    /// `host/room`, as carried in links and server settings.
    pub fn parse(value: &str) -> Option<Self> {
        let value = value.trim().trim_start_matches("https://").trim_start_matches("http://");
        let (host, room) = value.split_once('/')?;
        let room = room.trim_matches('/');
        let valid_host =
            !host.is_empty() && host.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | ':'));
        let valid_room = !room.is_empty() && room.len() <= 64 && room.chars().all(|c| c.is_ascii_alphanumeric());
        (valid_host && valid_room).then(|| Self { host: host.to_ascii_lowercase(), room: room.to_ascii_lowercase() })
    }
}

impl std::fmt::Display for RoomTarget {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}/{}", self.host, self.room)
    }
}

/// Sends datagrams to endpoints of the room; cheap to clone into other tasks.
#[derive(Clone)]
pub struct RoomSender {
    dc: Arc<dyn DataChannel>,
    closed: watch::Receiver<bool>,
}

impl RoomSender {
    pub async fn send(&self, to: &str, data: &[u8]) -> Result<()> {
        if *self.closed.borrow() {
            bail!("комната Jitsi закрыта");
        }
        self.dc.send_text(&endpoint_message(to, data)).await.map_err(|error| anyhow!("отправка в комнату: {error}"))
    }

    pub fn is_closed(&self) -> bool {
        *self.closed.borrow()
    }
}

/// One guest in the room with an open bridge datachannel.
pub struct Participant {
    endpoint: String,
    sender: RoomSender,
    pc: Arc<dyn PeerConnection>,
    inbound: mpsc::Receiver<(String, Vec<u8>)>,
    occupants: watch::Receiver<HashMap<String, String>>,
    xmpp: mpsc::UnboundedSender<String>,
    room_jid: String,
}

impl Participant {
    pub fn endpoint(&self) -> &str {
        &self.endpoint
    }

    pub fn sender(&self) -> RoomSender {
        self.sender.clone()
    }

    /// Endpoints of other participants whose display name starts with `prefix`, sorted.
    pub fn peers(&self, prefix: &str) -> Vec<String> {
        peers_in(&self.occupants.borrow(), prefix)
    }

    pub fn occupants(&self) -> watch::Receiver<HashMap<String, String>> {
        self.occupants.clone()
    }

    /// Next datagram as (sender endpoint, bytes); None once the participant is gone.
    pub async fn recv(&mut self) -> Option<(String, Vec<u8>)> {
        self.inbound.recv().await
    }

    pub async fn leave(&self) {
        let _ = self.xmpp.send(format!(
            "<presence to=\"{}/{}\" type=\"unavailable\" xmlns=\"jabber:client\"/>",
            self.room_jid, self.endpoint
        ));
        let _ = self.pc.close().await;
    }
}

pub fn peers_in(occupants: &HashMap<String, String>, prefix: &str) -> Vec<String> {
    let mut list: Vec<String> =
        occupants.iter().filter(|(_, name)| name.starts_with(prefix)).map(|(id, _)| id.clone()).collect();
    list.sort();
    list
}

fn endpoint_message(to: &str, data: &[u8]) -> String {
    format!(r#"{{"colibriClass":"EndpointMessage","to":"{to}","msgPayload":{{"raw":"{}"}}}}"#, BASE64.encode(data))
}

/// (sender, bytes) of an `EndpointMessage` carrying raw bytes; None for any other bridge message.
fn parse_endpoint_message(text: &[u8]) -> Option<(String, Vec<u8>)> {
    let value: serde_json::Value = serde_json::from_slice(text).ok()?;
    if value.get("colibriClass")?.as_str()? != "EndpointMessage" {
        return None;
    }
    let from = value.get("from")?.as_str()?.to_owned();
    let raw = value.get("msgPayload")?.get("raw")?.as_str()?;
    Some((from, BASE64.decode(raw).ok()?))
}

fn attr(xml: &str, name: &str) -> Option<String> {
    for quote in ['\'', '"'] {
        let key = format!(" {name}={quote}");
        if let Some(index) = xml.find(&key) {
            let value = &xml[index + key.len()..];
            return Some(
                value[..value.find(quote)?].replace("&amp;", "&").replace("&apos;", "'").replace("&quot;", "\""),
            );
        }
    }
    None
}

/// Every `<tag …>…</tag>` or `<tag …/>` element of `xml`, raw.
fn elements<'a>(xml: &'a str, tag: &str) -> Vec<&'a str> {
    let mut out = Vec::new();
    let open = format!("<{tag}");
    let mut rest = xml;
    while let Some(index) = rest.find(&open) {
        let tail = &rest[index..];
        if !matches!(tail.as_bytes().get(open.len()), Some(b' ' | b'>' | b'/')) {
            rest = &tail[open.len()..];
            continue;
        }
        let Some(head_end) = tail.find('>') else { break };
        let end = if tail[..head_end].ends_with('/') {
            head_end + 1
        } else {
            let close = format!("</{tag}>");
            tail.find(&close).map_or(head_end + 1, |c| c + close.len())
        };
        out.push(&tail[..end]);
        rest = &tail[end..];
    }
    out
}

fn inner_text(element: &str) -> Option<&str> {
    let start = element.find('>')? + 1;
    let end = element.rfind("</")?;
    (start <= end).then(|| element[start..end].trim())
}

/// SDP offer with only the data section of Jicofo's session-initiate: we carry no audio or video.
fn offer_from_jingle(jingle: &str) -> Result<(String, String)> {
    let data = elements(jingle, "content")
        .into_iter()
        .find(|c| attr(c, "name").as_deref() == Some("data"))
        .context("в приглашении нет канала данных")?;
    let transport = elements(data, "transport").into_iter().next().context("нет transport")?;
    let ufrag = attr(transport, "ufrag").context("нет ufrag")?;
    let pwd = attr(transport, "pwd").context("нет pwd")?;
    let fingerprint = elements(transport, "fingerprint").into_iter().next().context("нет fingerprint")?;
    let hash = attr(fingerprint, "hash").unwrap_or_else(|| "sha-256".into());
    let fp = inner_text(fingerprint).context("пустой fingerprint")?;
    let port =
        elements(data, "sctpmap").into_iter().next().and_then(|s| attr(s, "number")).unwrap_or_else(|| "5000".into());
    let mut sdp = format!(
        "v=0\r\no=- 1 2 IN IP4 0.0.0.0\r\ns=-\r\nt=0 0\r\na=group:BUNDLE data\r\n\
         m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\nc=IN IP4 0.0.0.0\r\n\
         a=ice-ufrag:{ufrag}\r\na=ice-pwd:{pwd}\r\na=fingerprint:{hash} {fp}\r\na=setup:actpass\r\na=mid:data\r\na=sctp-port:{port}\r\n"
    );
    for candidate in elements(jingle, "candidate") {
        if attr(candidate, "protocol").as_deref() != Some("udp") {
            continue;
        }
        let (Some(ip), Some(cport)) = (attr(candidate, "ip"), attr(candidate, "port")) else { continue };
        let related = match (attr(candidate, "rel-addr"), attr(candidate, "rel-port")) {
            (Some(addr), Some(port)) => format!(" raddr {addr} rport {port}"),
            _ => String::new(),
        };
        sdp += &format!(
            "a=candidate:{} {} udp {} {ip} {cport} typ {}{related}\r\n",
            attr(candidate, "foundation").unwrap_or_else(|| "1".into()),
            attr(candidate, "component").unwrap_or_else(|| "1".into()),
            attr(candidate, "priority").unwrap_or_else(|| "1".into()),
            attr(candidate, "type").unwrap_or_else(|| "host".into()),
        );
    }
    sdp += "a=end-of-candidates\r\n";
    Ok((sdp, port))
}

/// session-accept body: every offered content answered with our ICE/DTLS, audio and video without sources.
fn accept_contents(jingle: &str, ufrag: &str, pwd: &str, fingerprint: &str, port: &str) -> String {
    let transport = |extra: &str| {
        format!(
            "<transport xmlns=\"urn:xmpp:jingle:transports:ice-udp:1\" ufrag=\"{ufrag}\" pwd=\"{pwd}\"><rtcp-mux/>\
             <fingerprint xmlns=\"urn:xmpp:jingle:apps:dtls:0\" hash=\"sha-256\" setup=\"active\">{fingerprint}</fingerprint>{extra}</transport>"
        )
    };
    let mut out = String::new();
    for content in elements(jingle, "content") {
        let name = attr(content, "name").unwrap_or_default();
        if name == "data" {
            out += &format!(
                "<content creator=\"initiator\" name=\"data\" senders=\"both\"><description xmlns=\"urn:xmpp:jingle:apps:rtp:1\" media=\"application\"/>{}</content>",
                transport(&format!(
                    "<sctpmap xmlns=\"urn:xmpp:jingle:transports:dtls-sctp:1\" number=\"{port}\" protocol=\"webrtc-datachannel\" streams=\"1024\"/>"
                ))
            );
        } else if let Some(description) = elements(content, "description").into_iter().next() {
            let mut kept = description.to_owned();
            for tag in ["source", "ssrc-group"] {
                for element in elements(description, tag) {
                    kept = kept.replace(element, "");
                }
            }
            out += &format!(
                "<content creator=\"initiator\" name=\"{name}\" senders=\"both\">{kept}{}</content>",
                transport("")
            );
        }
    }
    out
}

/// Answers IQ requests addressed to us (disco, ping, Jingle updates); true when `stanza` was one.
fn answer_iq(stanza: &str, out: &mpsc::UnboundedSender<String>) -> bool {
    if !stanza.starts_with("<iq") {
        return false;
    }
    let kind = attr(stanza, "type").unwrap_or_default();
    if kind != "get" && kind != "set" {
        return false;
    }
    let (Some(id), Some(from)) = (attr(stanza, "id"), attr(stanza, "from")) else { return true };
    let body = if stanza.contains("disco#info") {
        let features: String = FEATURES.iter().map(|f| format!("<feature var=\"{f}\"/>")).collect();
        format!("<query xmlns=\"http://jabber.org/protocol/disco#info\">{features}</query>")
    } else {
        String::new()
    };
    let _ = out.send(format!("<iq to=\"{from}\" id=\"{id}\" type=\"result\" xmlns=\"jabber:client\">{body}</iq>"));
    true
}

/// Tracks MUC presence: occupant nick -> display name.
fn track_presence(stanza: &str, room_jid: &str, occupants: &watch::Sender<HashMap<String, String>>) {
    if !stanza.starts_with("<presence") {
        return;
    }
    let Some(from) = attr(stanza, "from") else { return };
    let Some(nick) = from.strip_prefix(&format!("{room_jid}/")) else { return };
    if nick == "focus" {
        return;
    }
    let nick = nick.to_owned();
    if attr(stanza, "type").as_deref() == Some("unavailable") {
        occupants.send_modify(|map| {
            map.remove(&nick);
        });
    } else {
        let display = elements(stanza, "nick").into_iter().next().and_then(inner_text).unwrap_or("").to_owned();
        occupants.send_modify(|map| {
            map.insert(nick, display);
        });
    }
}

struct Xmpp {
    out: mpsc::UnboundedSender<String>,
    inbox: mpsc::UnboundedReceiver<String>,
}

impl Xmpp {
    async fn connect(target: &RoomTarget) -> Result<Self> {
        let host = &target.host;
        let mut request = format!("wss://{host}/xmpp-websocket?room={}", target.room).into_client_request()?;
        request.headers_mut().insert("Sec-WebSocket-Protocol", "xmpp".parse()?);
        request.headers_mut().insert("Origin", format!("https://{host}").parse()?);
        let (ws, _) = tokio_tungstenite::connect_async(request).await.with_context(|| format!("XMPP {host}"))?;
        let (mut sink, mut stream) = ws.split();
        let (out, mut out_rx) = mpsc::unbounded_channel::<String>();
        let (in_tx, inbox) = mpsc::unbounded_channel::<String>();
        tokio::spawn(async move {
            let mut keepalive = tokio::time::interval(KEEPALIVE);
            keepalive.tick().await;
            loop {
                let frame = tokio::select! {
                    message = out_rx.recv() => match message { Some(text) => Message::Text(text.into()), None => break },
                    // WebSocket ping keeps Prosody and proxies from dropping an idle stream; a whitespace
                    // text frame is rejected by Prosody (RFC 7395 3.3.3) and closes the socket.
                    _ = keepalive.tick() => Message::Ping(Vec::new().into()),
                };
                if sink.send(frame).await.is_err() {
                    break;
                }
            }
        });
        tokio::spawn(async move {
            while let Some(Ok(message)) = stream.next().await {
                if let Message::Text(text) = message
                    && in_tx.send(text.to_string()).is_err()
                {
                    break;
                }
            }
        });
        Ok(Self { out, inbox })
    }

    fn send(&self, text: String) -> Result<()> {
        self.out.send(text).map_err(|_| anyhow!("XMPP закрыт"))
    }

    /// Next stanza containing `needle`; IQ requests met on the way are answered.
    async fn wait(&mut self, needle: &str) -> Result<String> {
        tokio::time::timeout(STEP_TIMEOUT, async {
            loop {
                let stanza = self.inbox.recv().await.ok_or_else(|| anyhow!("XMPP закрыт"))?;
                if stanza.contains("<stream:error") || stanza.contains("<failure") {
                    bail!("XMPP: {}", stanza.chars().take(200).collect::<String>());
                }
                if stanza.contains(needle) {
                    answer_iq(&stanza, &self.out);
                    return Ok(stanza);
                }
                answer_iq(&stanza, &self.out);
            }
        })
        .await
        .map_err(|_| anyhow!("XMPP: нет ответа ({needle})"))?
    }

    /// Anonymous login; returns the bound JID.
    async fn login(&mut self, domain: &str) -> Result<String> {
        let open = format!("<open to=\"{domain}\" version=\"1.0\" xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\"/>");
        self.send(open.clone())?;
        let features = self.wait("features").await?;
        if !features.contains("ANONYMOUS") {
            bail!("сервер Jitsi не пускает гостей");
        }
        self.send("<auth mechanism=\"ANONYMOUS\" xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\"/>".into())?;
        self.wait("success").await?;
        self.send(open)?;
        self.wait("features").await?;
        self.send("<iq type=\"set\" id=\"bind\" xmlns=\"jabber:client\"><bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/></iq>".into())?;
        let bound = self.wait("<jid>").await?;
        let jid = bound.split("<jid>").nth(1).and_then(|s| s.split('<').next()).context("XMPP не выдал JID")?;
        Ok(jid.to_owned())
    }

    /// The MUC service of `domain`, from its disco#items (conference.* or muc.*).
    async fn muc_domain(&mut self, domain: &str) -> Result<String> {
        self.send(format!(
            "<iq type=\"get\" to=\"{domain}\" id=\"items\" xmlns=\"jabber:client\"><query xmlns=\"http://jabber.org/protocol/disco#items\"/></iq>"
        ))?;
        let items = self.wait("disco#items").await?;
        let jids: Vec<String> = elements(&items, "item").into_iter().filter_map(|i| attr(i, "jid")).collect();
        Ok(jids
            .iter()
            .find(|j| j.starts_with("conference."))
            .or_else(|| jids.iter().find(|j| j.starts_with("muc.")))
            .cloned()
            .unwrap_or_else(|| format!("conference.{domain}")))
    }
}

struct EventHandler {
    gathered: mpsc::UnboundedSender<()>,
    closed: Arc<watch::Sender<bool>>,
}

#[async_trait::async_trait]
impl PeerConnectionEventHandler for EventHandler {
    async fn on_ice_gathering_state_change(&self, state: RTCIceGatheringState) {
        if state == RTCIceGatheringState::Complete {
            let _ = self.gathered.send(());
        }
    }

    async fn on_connection_state_change(&self, state: RTCPeerConnectionState) {
        if matches!(state, RTCPeerConnectionState::Failed | RTCPeerConnectionState::Closed) {
            let _ = self.closed.send(true);
        }
    }
}

/// Joins `target` as a guest named `display` and opens the bridge datachannel.
pub async fn join(target: &RoomTarget, display: &str) -> Result<Participant> {
    tokio::time::timeout(JOIN_TIMEOUT, async {
        match join_as(target, display, &target.host).await {
            Err(error) if format!("{error:#}").contains("host-unknown") => join_as(target, display, DOCKER_DOMAIN).await,
            other => other,
        }
    })
    .await
    .map_err(|_| anyhow!("вход в комнату Jitsi {target}: таймаут"))?
    .with_context(|| format!("комната Jitsi {target}"))
}

async fn join_as(target: &RoomTarget, display: &str, domain: &str) -> Result<Participant> {
    let mut xmpp = Xmpp::connect(target).await?;
    let jid = xmpp.login(domain).await?;
    let nick: String = jid.split('@').next().unwrap_or_default().chars().take(8).collect();
    let muc = xmpp.muc_domain(domain).await?;
    let room_jid = format!("{}@{muc}", target.room);
    xmpp.send(format!(
        "<iq to=\"focus.{domain}\" type=\"set\" id=\"focus\" xmlns=\"jabber:client\"><conference room=\"{room_jid}\" machine-uid=\"{nick}\" xmlns=\"http://jitsi.org/protocol/focus\"><property name=\"rtcstatsEnabled\" value=\"false\"/></conference></iq>"
    ))?;
    xmpp.wait("ready=").await?;
    xmpp.send(format!(
        "<presence to=\"{room_jid}/{nick}\" xmlns=\"jabber:client\"><x xmlns=\"http://jabber.org/protocol/muc\"/><SourceInfo>{{}}</SourceInfo><jitsi_participant_codecList>vp8</jitsi_participant_codecList><nick xmlns=\"http://jabber.org/protocol/nick\">{display}</nick></presence>"
    ))?;
    let (occupants_tx, occupants) = watch::channel(HashMap::new());
    // Presences of those already in the room arrive before our own (status 110).
    let initiate = loop {
        let stanza = xmpp.wait("<").await?;
        track_presence(&stanza, &room_jid, &occupants_tx);
        if stanza.contains("session-initiate") {
            break stanza;
        }
    };

    let (offer, port) = offer_from_jingle(&initiate)?;
    let (closed_tx, closed) = watch::channel(false);
    let closed_tx = Arc::new(closed_tx);
    let (gathered_tx, mut gathered) = mpsc::unbounded_channel();
    let pc = PeerConnectionBuilder::new()
        .with_handler(Arc::new(EventHandler { gathered: gathered_tx, closed: closed_tx.clone() }))
        .with_udp_addrs(vec!["0.0.0.0:0".to_string()])
        .build()
        .await
        .map_err(|error| anyhow!("WebRTC: {error}"))?;
    let pc: Arc<dyn PeerConnection> = Arc::new(pc);
    let offer = RTCSessionDescription::offer(offer).map_err(|error| anyhow!("SDP: {error}"))?;
    pc.set_remote_description(offer).await.map_err(|error| anyhow!("SDP: {error}"))?;
    let answer = pc.create_answer(None).await.map_err(|error| anyhow!("SDP answer: {error}"))?;
    pc.set_local_description(answer).await.map_err(|error| anyhow!("SDP answer: {error}"))?;
    let _ = tokio::time::timeout(Duration::from_secs(3), gathered.recv()).await;
    let local = pc.local_description().await.context("нет локального SDP")?.sdp;
    let line = |key: &str| local.lines().find_map(|l| l.strip_prefix(key)).map(|s| s.trim().to_owned());
    let ufrag = line("a=ice-ufrag:").context("нет ice-ufrag")?;
    let pwd = line("a=ice-pwd:").context("нет ice-pwd")?;
    let fingerprint = line("a=fingerprint:sha-256 ").context("нет fingerprint")?;
    let sid = attr(&initiate, "sid").context("нет sid")?;
    let initiator = attr(&initiate, "initiator").unwrap_or_default();
    let focus = attr(&initiate, "from").context("нет from")?;
    xmpp.send(format!(
        "<iq to=\"{focus}\" type=\"set\" id=\"accept\" xmlns=\"jabber:client\"><jingle xmlns=\"urn:xmpp:jingle:1\" action=\"session-accept\" sid=\"{sid}\" initiator=\"{initiator}\" responder=\"{jid}\">{}</jingle></iq>",
        accept_contents(&initiate, &ufrag, &pwd, &fingerprint, &port)
    ))?;

    let dc = pc
        .create_data_channel(
            "JVB data channel",
            Some(RTCDataChannelInit { ordered: true, protocol: "http://jitsi.org/protocols/colibri".into(), ..Default::default() }),
        )
        .await
        .map_err(|error| anyhow!("datachannel: {error}"))?;
    let (open_tx, mut open_rx) = mpsc::unbounded_channel::<()>();
    let (inbound_tx, inbound) = mpsc::channel(INBOUND_QUEUE);
    {
        let dc = dc.clone();
        let closed_tx = closed_tx.clone();
        tokio::spawn(async move {
            while let Some(event) = dc.poll().await {
                match event {
                    DataChannelEvent::OnOpen => {
                        let _ = open_tx.send(());
                    }
                    DataChannelEvent::OnMessage(message) => {
                        if let Some(datagram) = parse_endpoint_message(&message.data) {
                            // A full queue drops the datagram, as a congested UDP path would.
                            let _ = inbound_tx.try_send(datagram);
                        }
                    }
                    DataChannelEvent::OnClose => break,
                    _ => {}
                }
            }
            let _ = closed_tx.send(true);
        });
    }
    tokio::time::timeout(STEP_TIMEOUT, open_rx.recv())
        .await
        .map_err(|_| anyhow!("канал данных видеомоста не открылся"))?;
    dc.send_text(r#"{"colibriClass":"ClientHello"}"#).await.map_err(|error| anyhow!("ClientHello: {error}"))?;

    // Keep the XMPP side alive: answer Jicofo, follow who is in the room, notice the end of the session.
    let out = xmpp.out.clone();
    {
        let closed_tx = closed_tx.clone();
        let room_jid = room_jid.clone();
        tokio::spawn(async move {
            while let Some(stanza) = xmpp.inbox.recv().await {
                answer_iq(&stanza, &xmpp.out);
                track_presence(&stanza, &room_jid, &occupants_tx);
                if stanza.contains("session-terminate") || stanza.contains("<stream:error") {
                    break;
                }
            }
            let _ = closed_tx.send(true);
        });
    }
    Ok(Participant {
        endpoint: nick,
        sender: RoomSender { dc, closed },
        pc,
        inbound,
        occupants,
        xmpp: out,
        room_jid,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const INITIATE: &str = "<iq type='set' from='r@muc.meet.jitsi/focus'><jingle xmlns='urn:xmpp:jingle:1' action='session-initiate' sid='abc' initiator='r@muc.meet.jitsi/focus'>\
        <content creator='initiator' name='audio' senders='both'><description xmlns='urn:xmpp:jingle:apps:rtp:1' media='audio'><payload-type id='111' name='opus' clockrate='48000' channels='2'/><source ssrc='1' xmlns='urn:xmpp:jingle:apps:rtp:ssma:0'/></description>\
        <transport xmlns='urn:xmpp:jingle:transports:ice-udp:1' ufrag='uf' pwd='pw'><fingerprint hash='sha-256' setup='actpass' xmlns='urn:xmpp:jingle:apps:dtls:0'>AA:BB</fingerprint></transport></content>\
        <content creator='initiator' name='data' senders='both'><description xmlns='urn:xmpp:jingle:apps:rtp:1' media='application'/>\
        <transport xmlns='urn:xmpp:jingle:transports:ice-udp:1' ufrag='uf' pwd='pw'><fingerprint hash='sha-256' setup='actpass' xmlns='urn:xmpp:jingle:apps:dtls:0'>AA:BB</fingerprint>\
        <candidate component='1' foundation='1' generation='0' id='c1' ip='198.51.100.7' port='10000' priority='2130706431' protocol='udp' type='host'/>\
        <candidate component='1' foundation='2' generation='0' id='c2' ip='198.51.100.7' port='4443' priority='1' protocol='tcp' type='host'/>\
        <sctpmap xmlns='urn:xmpp:jingle:transports:dtls-sctp:1' number='5000' protocol='webrtc-datachannel' streams='1024'/></transport></content></jingle></iq>";

    #[test]
    fn offer_keeps_only_data_and_udp_candidates() {
        let (sdp, port) = offer_from_jingle(INITIATE).unwrap();
        assert_eq!(port, "5000");
        assert!(sdp.contains("m=application 9 UDP/DTLS/SCTP webrtc-datachannel"));
        assert!(sdp.contains("a=ice-ufrag:uf\r\na=ice-pwd:pw"));
        assert!(sdp.contains("a=fingerprint:sha-256 AA:BB"));
        assert!(sdp.contains("198.51.100.7 10000 typ host"));
        assert!(!sdp.contains("4443"));
        assert!(!sdp.contains("m=audio"));
    }

    #[test]
    fn accept_answers_every_content_without_foreign_sources() {
        let body = accept_contents(INITIATE, "u", "p", "CC:DD", "5000");
        assert!(body.contains("name=\"audio\"") && body.contains("name=\"data\""));
        assert!(body.contains("opus"));
        assert!(!body.contains("ssrc='1'"));
        assert!(body.contains("setup=\"active\">CC:DD<"));
        assert!(body.contains("number=\"5000\""));
    }

    #[test]
    fn endpoint_message_round_trip() {
        let wire = endpoint_message("abcd1234", b"\x00\x01payload");
        let incoming = wire.replace("\"to\"", "\"from\"");
        assert_eq!(parse_endpoint_message(incoming.as_bytes()), Some(("abcd1234".into(), b"\x00\x01payload".to_vec())));
        assert_eq!(parse_endpoint_message(br#"{"colibriClass":"ServerHello"}"#), None);
    }

    #[test]
    fn presence_tracking_follows_joins_and_leaves() {
        let (tx, rx) = watch::channel(HashMap::new());
        let room = "r@muc.meet.jitsi";
        track_presence("<presence from='r@muc.meet.jitsi/aa11bb22'><nick xmlns='http://jabber.org/protocol/nick'>mrtt-s0</nick></presence>", room, &tx);
        track_presence("<presence from='r@muc.meet.jitsi/focus'/>", room, &tx);
        track_presence("<presence from='r@muc.meet.jitsi/cc33dd44'><nick xmlns='x'>someone</nick></presence>", room, &tx);
        assert_eq!(peers_in(&rx.borrow(), SERVER_DISPLAY), ["aa11bb22"]);
        track_presence("<presence from='r@muc.meet.jitsi/aa11bb22' type='unavailable'/>", room, &tx);
        assert!(peers_in(&rx.borrow(), SERVER_DISPLAY).is_empty());
    }

    #[test]
    fn room_target_parsing() {
        assert_eq!(
            RoomTarget::parse("https://Meet.Egovm.ru/Mrtt0a1b"),
            Some(RoomTarget { host: "meet.egovm.ru".into(), room: "mrtt0a1b".into() })
        );
        assert_eq!(RoomTarget::parse("meet.egovm.ru"), None);
        assert_eq!(RoomTarget::parse("meet.egovm.ru/bad room"), None);
    }
}
