//! Ping through the tunnel. The app's own UID is excluded from its VPN, so an HTTP request from Kotlin never touches the
//! tunnel. Instead the client itself sends a TCP SYN along the worker path to the host of the ping URL and times the answer:
//! it proves the whole way, phone to carrier to server to the internet, and needs nothing new on the server.

use std::net::Ipv4Addr;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use tokio::sync::oneshot;

const SYN: u8 = 0x02;
const RST: u8 = 0x04;
const ACK: u8 = 0x10;
const PACKET_LEN: usize = 40;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Target {
    pub src: Ipv4Addr,
    pub dst: Ipv4Addr,
    pub src_port: u16,
    pub dst_port: u16,
    pub seq: u32,
}

/// `<tunnel ip>|<target ip>|<port>` from the app; the port and sequence of the probe come from `nonce`.
pub fn parse_command(spec: &str, nonce: u32) -> Option<Target> {
    let mut parts = spec.split('|');
    let src = parts.next()?.trim().parse().ok()?;
    let dst = parts.next()?.trim().parse().ok()?;
    let dst_port = parts.next()?.trim().parse().ok().filter(|port| *port != 0)?;
    if parts.next().is_some() {
        return None;
    }
    Some(Target { src, dst, src_port: 40000 + (nonce % 20000) as u16, dst_port, seq: nonce })
}

fn checksum(parts: &[&[u8]]) -> u16 {
    let mut sum = 0u32;
    for part in parts {
        let mut chunks = part.chunks_exact(2);
        for chunk in &mut chunks {
            sum += u32::from(u16::from_be_bytes([chunk[0], chunk[1]]));
        }
        if let [last] = chunks.remainder() {
            sum += u32::from(*last) << 8;
        }
    }
    while sum > 0xffff {
        sum = (sum & 0xffff) + (sum >> 16);
    }
    !(sum as u16)
}

fn tcp_packet(target: &Target, flags: u8, seq: u32) -> [u8; PACKET_LEN] {
    let mut packet = [0u8; PACKET_LEN];
    packet[0] = 0x45;
    packet[2..4].copy_from_slice(&(PACKET_LEN as u16).to_be_bytes());
    packet[4..6].copy_from_slice(&(target.seq as u16).to_be_bytes());
    packet[6] = 0x40; // don't fragment
    packet[8] = 64;
    packet[9] = 6;
    packet[12..16].copy_from_slice(&target.src.octets());
    packet[16..20].copy_from_slice(&target.dst.octets());
    let header = checksum(&[&packet[..20]]);
    packet[10..12].copy_from_slice(&header.to_be_bytes());

    packet[20..22].copy_from_slice(&target.src_port.to_be_bytes());
    packet[22..24].copy_from_slice(&target.dst_port.to_be_bytes());
    packet[24..28].copy_from_slice(&seq.to_be_bytes());
    packet[32] = 5 << 4;
    packet[33] = flags;
    packet[34..36].copy_from_slice(&0xffffu16.to_be_bytes());
    let pseudo = [&packet[12..20], &[0, 6, 0, 20]].concat();
    let tcp = checksum(&[&pseudo, &packet[20..]]);
    packet[36..38].copy_from_slice(&tcp.to_be_bytes());
    packet
}

pub fn syn_packet(target: &Target) -> [u8; PACKET_LEN] {
    tcp_packet(target, SYN, target.seq)
}

/// Closes the half-open connection on the far side.
pub fn rst_packet(target: &Target) -> [u8; PACKET_LEN] {
    tcp_packet(target, RST, target.seq.wrapping_add(1))
}

/// The target's answer to our SYN: SYN+ACK, or RST when the port is closed, which still proves the path.
fn is_answer(packet: &[u8], target: &Target) -> bool {
    if packet.len() < 20 || packet[0] >> 4 != 4 || packet[9] != 6 {
        return false;
    }
    let tcp_start = usize::from(packet[0] & 0x0f) * 4;
    let Some(tcp) = packet.get(tcp_start..tcp_start + 20) else { return false };
    if packet[12..16] != target.dst.octets()
        || packet[16..20] != target.src.octets()
        || tcp[0..2] != target.dst_port.to_be_bytes()
        || tcp[2..4] != target.src_port.to_be_bytes()
    {
        return false;
    }
    let acked = u32::from_be_bytes([tcp[8], tcp[9], tcp[10], tcp[11]]) == target.seq.wrapping_add(1);
    let flags = tcp[13];
    acked && (flags & (SYN | ACK) == SYN | ACK || flags & RST != 0)
}

/// The probe in flight; a new one replaces it. The downlink checks only one relaxed flag while nothing is in flight.
#[derive(Default)]
pub struct Slot {
    active: AtomicBool,
    pending: Mutex<Option<(Target, oneshot::Sender<()>)>>,
}

impl Slot {
    pub fn arm(&self, target: Target) -> oneshot::Receiver<()> {
        let (tx, rx) = oneshot::channel();
        if let Ok(mut pending) = self.pending.lock() {
            *pending = Some((target, tx));
            self.active.store(true, Ordering::Relaxed);
        }
        rx
    }

    pub fn disarm(&self) {
        self.active.store(false, Ordering::Relaxed);
        if let Ok(mut pending) = self.pending.lock() {
            *pending = None;
        }
    }

    /// True when `packet` is the answer: it is ours and must not reach the TUN.
    pub fn intercept(&self, packet: &[u8]) -> bool {
        if !self.active.load(Ordering::Relaxed) {
            return false;
        }
        let Ok(mut pending) = self.pending.lock() else { return false };
        if !pending.as_ref().is_some_and(|(target, _)| is_answer(packet, target)) {
            return false;
        }
        self.active.store(false, Ordering::Relaxed);
        if let Some((_, tx)) = pending.take() {
            let _ = tx.send(());
        }
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn target() -> Target {
        parse_command("10.66.67.3|142.250.74.4|443", 12345).unwrap()
    }

    fn answer(flags: u8, ack: u32) -> Vec<u8> {
        let t = target();
        let swapped = Target { src: t.dst, dst: t.src, src_port: t.dst_port, dst_port: t.src_port, seq: 999 };
        let mut packet = tcp_packet(&swapped, flags, 999).to_vec();
        packet[28..32].copy_from_slice(&ack.to_be_bytes());
        packet
    }

    #[test]
    fn command_is_validated() {
        let t = target();
        assert_eq!(t.src, Ipv4Addr::new(10, 66, 67, 3));
        assert_eq!(t.dst_port, 443);
        assert!((40000..60000).contains(&t.src_port));
        for bad in ["", "1.2.3.4|5.6.7.8", "1.2.3.4|5.6.7.8|0", "1.2.3.4|host|443", "1.2.3.4|5.6.7.8|443|x"] {
            assert_eq!(parse_command(bad, 1), None, "{bad}");
        }
    }

    #[test]
    fn checksums_verify_to_zero() {
        let packet = syn_packet(&target());
        assert_eq!(checksum(&[&packet[..20]]), 0);
        let pseudo = [&packet[12..20], &[0, 6, 0, 20]].concat();
        assert_eq!(checksum(&[&pseudo, &packet[20..]]), 0);
        assert_eq!(packet[33], SYN);
        assert_eq!(rst_packet(&target())[33], RST);
    }

    #[test]
    fn only_the_answer_is_taken_and_only_once() {
        let slot = Slot::default();
        let good = answer(SYN | ACK, 12346);
        assert!(!slot.intercept(&good), "nothing armed");
        let mut rx = slot.arm(target());
        assert!(!slot.intercept(&answer(SYN | ACK, 1)), "wrong ack");
        assert!(!slot.intercept(&answer(ACK, 12346)), "not an answer to SYN");
        assert!(!slot.intercept(&good[..30]), "truncated");
        assert!(rx.try_recv().is_err());
        assert!(slot.intercept(&good));
        assert!(rx.try_recv().is_ok());
        assert!(!slot.intercept(&good), "already answered");
        let _rx = slot.arm(target());
        assert!(slot.intercept(&answer(RST | ACK, 12346)), "closed port still proves the path");
    }
}
