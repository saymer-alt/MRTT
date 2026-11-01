//! The server's seats in its Jitsi room. Each seat is a participant named `mrtt-s<N>`; a client worker sends its
//! tunnel datagrams to one seat, and the seat hands them to the ordinary UDP peer port on 127.0.0.1 through a
//! socket of that worker's own. The protocol engine sees each worker as a local UDP peer and needs no change.

use crate::App;
use crate::jitsi_room::{self, RoomTarget};
use std::collections::HashMap;
use std::net::{Ipv4Addr, SocketAddr};
use std::sync::{Arc, OnceLock};
use std::time::{Duration, Instant};
use tokio::net::UdpSocket;
use tokio::sync::watch;

const IDLE_FORGET: Duration = Duration::from_secs(120);
const SWEEP_EVERY: Duration = Duration::from_secs(30);
const REJOIN_MIN: Duration = Duration::from_secs(3);
const REJOIN_MAX: Duration = Duration::from_secs(60);
const DATAGRAM_MAX: usize = 2048;

/// The room and how many seats to hold in it; no room keeps the server out of Jitsi.
#[derive(Clone, PartialEq, Eq)]
pub struct Config {
    pub room: Option<RoomTarget>,
    pub seats: usize,
}

impl Config {
    pub fn from_db(db: &crate::model::Database) -> Self {
        Self { room: RoomTarget::parse(&db.jitsi_room), seats: db.jitsi_seats() as usize }
    }
}

static CONFIG: OnceLock<watch::Sender<Config>> = OnceLock::new();

/// Holds the seats of the configured room and moves them whenever `configure` changes it.
pub fn start(app: Arc<App>, peer_port: u16, initial: Config) {
    let (tx, mut rx) = watch::channel(initial);
    let _ = CONFIG.set(tx);
    tokio::spawn(async move {
        loop {
            let config = rx.borrow_and_update().clone();
            let seats = match &config.room {
                Some(room) => spawn_seats(&app, room, config.seats, peer_port),
                None => Vec::new(),
            };
            if rx.changed().await.is_err() {
                break;
            }
            for seat in seats {
                seat.abort();
            }
        }
    });
}

pub fn configure(config: Config) {
    if let Some(tx) = CONFIG.get() {
        tx.send_if_modified(|current| {
            let changed = *current != config;
            *current = config;
            changed
        });
    }
}

/// The room links should carry, if the server sits in one.
pub fn current_room() -> Option<String> {
    CONFIG.get()?.borrow().room.as_ref().map(ToString::to_string)
}

/// Starts `seats` participants in `target`, each re-joining on its own when the room drops it.
fn spawn_seats(app: &Arc<App>, target: &RoomTarget, seats: usize, peer_port: u16) -> Vec<tokio::task::JoinHandle<()>> {
    (0..seats.max(1)).map(|seat| {
        let target = target.clone();
        let app = app.clone();
        tokio::spawn(async move {
            // Seats join one after another: a burst of parallel guests is refused by some instances.
            tokio::time::sleep(Duration::from_millis(700 * seat as u64)).await;
            let mut backoff = REJOIN_MIN;
            loop {
                let started = Instant::now();
                match run_seat(&app, &target, seat, peer_port).await {
                    Ok(()) => crate::log_event(&app, "WARN", "JITSI", &format!("место {seat} покинуло комнату {target}")),
                    Err(error) => crate::log_event(&app, "WARN", "JITSI", &format!("место {seat} в {target}: {error:#}")),
                }
                backoff = if started.elapsed() > REJOIN_MAX { REJOIN_MIN } else { (backoff * 2).min(REJOIN_MAX) };
                tokio::time::sleep(backoff).await;
            }
        })
    }).collect()
}

struct Flow {
    socket: Arc<UdpSocket>,
    last: Instant,
    reader: tokio::task::JoinHandle<()>,
}

// A seat aborted on a room change drops its flows; their readers must stop with them.
impl Drop for Flow {
    fn drop(&mut self) {
        self.reader.abort();
    }
}

async fn run_seat(app: &Arc<App>, target: &RoomTarget, seat: usize, peer_port: u16) -> anyhow::Result<()> {
    let mut participant = jitsi_room::join(target, &format!("{}{seat}", jitsi_room::SERVER_DISPLAY)).await?;
    crate::log_event(app, "INFO", "JITSI", &format!("место {seat} в комнате {target} (endpoint {})", participant.endpoint()));
    let peer = SocketAddr::from((Ipv4Addr::LOCALHOST, peer_port));
    let sender = participant.sender();
    let mut flows: HashMap<String, Flow> = HashMap::new();
    let mut sweep = tokio::time::interval(SWEEP_EVERY);
    let result = loop {
        tokio::select! {
            received = participant.recv() => {
                let Some((from, data)) = received else { break Ok(()) };
                let flow = match flows.get_mut(&from) {
                    Some(flow) => flow,
                    None => {
                        let socket = Arc::new(UdpSocket::bind((Ipv4Addr::LOCALHOST, 0)).await?);
                        socket.connect(peer).await?;
                        let reader = tokio::spawn(pump_back(socket.clone(), sender.clone(), from.clone()));
                        flows.entry(from.clone()).or_insert(Flow { socket, last: Instant::now(), reader })
                    }
                };
                flow.last = Instant::now();
                let _ = flow.socket.send(&data).await;
            }
            _ = sweep.tick() => {
                flows.retain(|_, flow| flow.last.elapsed() < IDLE_FORGET);
                if sender.is_closed() {
                    break Ok(());
                }
            }
        }
    };
    drop(flows);
    participant.leave().await;
    result
}

/// Replies of the protocol engine for one worker go back to that worker's endpoint.
async fn pump_back(socket: Arc<UdpSocket>, sender: jitsi_room::RoomSender, to: String) {
    let mut buffer = vec![0u8; DATAGRAM_MAX];
    while let Ok(len) = socket.recv(&mut buffer).await {
        if sender.send(&to, &buffer[..len]).await.is_err() {
            break;
        }
    }
}
