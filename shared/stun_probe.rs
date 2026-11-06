//! The connectivity check that opens a WebRTC call, sent before DTLS on a direct connection so that the flow starts the
//! way a call does. Nothing depends on it: it runs before the session and a server that does not answer only costs a wait.
//! MESSAGE-INTEGRITY is random, since the two ends share no ICE password; on the wire it looks the same.

// Client and server each use one side of this file.
#![allow(dead_code)]

use std::net::SocketAddr;

const MAGIC: [u8; 4] = [0x21, 0x12, 0xa4, 0x42];
const BINDING_REQUEST: [u8; 2] = [0x00, 0x01];
const BINDING_SUCCESS: [u8; 2] = [0x01, 0x01];
const HEADER: usize = 20;
const FINGERPRINT_LEN: usize = 8;
const ATTR_USERNAME: u16 = 0x0006;
const ATTR_MESSAGE_INTEGRITY: u16 = 0x0008;
const ATTR_XOR_MAPPED_ADDRESS: u16 = 0x0020;
const ATTR_PRIORITY: u16 = 0x0024;
const ATTR_FINGERPRINT: u16 = 0x8028;
const ATTR_ICE_CONTROLLING: u16 = 0x802a;

fn crc32(data: &[u8]) -> u32 {
    let mut crc = !0u32;
    for byte in data {
        crc ^= u32::from(*byte);
        for _ in 0..8 {
            crc = (crc >> 1) ^ (0xedb8_8320 & 0u32.wrapping_sub(crc & 1));
        }
    }
    !crc
}

fn attribute(message: &mut Vec<u8>, kind: u16, value: &[u8]) {
    message.extend_from_slice(&kind.to_be_bytes());
    message.extend_from_slice(&(value.len() as u16).to_be_bytes());
    message.extend_from_slice(value);
    message.resize(message.len().next_multiple_of(4), 0);
}

/// Sets the length field and closes the message with its FINGERPRINT.
fn finish(mut message: Vec<u8>) -> Vec<u8> {
    let length = (message.len() - HEADER + FINGERPRINT_LEN) as u16;
    message[2..4].copy_from_slice(&length.to_be_bytes());
    let fingerprint = crc32(&message) ^ 0x5354_554e;
    attribute(&mut message, ATTR_FINGERPRINT, &fingerprint.to_be_bytes());
    message
}

fn fingerprint_valid(message: &[u8]) -> bool {
    if message.len() < HEADER + FINGERPRINT_LEN || message[4..8] != MAGIC {
        return false;
    }
    let declared = usize::from(u16::from_be_bytes([message[2], message[3]]));
    if declared + HEADER != message.len() {
        return false;
    }
    let (body, tail) = message.split_at(message.len() - FINGERPRINT_LEN);
    tail[..2] == ATTR_FINGERPRINT.to_be_bytes()
        && tail[4..] == (crc32(body) ^ 0x5354_554e).to_be_bytes()
}

/// A Binding Request as a browser sends it; every field comes from `random`. Returns it with its transaction id.
pub fn request(random: &[u8; 64]) -> (Vec<u8>, [u8; 12]) {
    let mut transaction = [0u8; 12];
    transaction.copy_from_slice(&random[..12]);
    let letters: Vec<u8> = random[12..21].iter().map(|byte| b'a' + byte % 26).collect();
    let mut username = letters[..4].to_vec();
    username.push(b':');
    username.extend_from_slice(&letters[4..]);
    let mut message = Vec::with_capacity(88);
    message.extend_from_slice(&BINDING_REQUEST);
    message.extend_from_slice(&[0, 0]);
    message.extend_from_slice(&MAGIC);
    message.extend_from_slice(&transaction);
    attribute(&mut message, ATTR_USERNAME, &username);
    attribute(&mut message, ATTR_ICE_CONTROLLING, &random[21..29]);
    attribute(&mut message, ATTR_PRIORITY, &[0x6e, 0x7f, random[29], 0xff]);
    attribute(&mut message, ATTR_MESSAGE_INTEGRITY, &random[30..50]);
    (finish(message), transaction)
}

/// Cheap first test for the data path: no tunnel packet starts with this byte.
pub fn may_be_request(packet: &[u8]) -> bool {
    packet.first() == Some(&BINDING_REQUEST[0])
}

/// The reply to a Binding Request from `peer`, never longer than the request, so it cannot amplify anything.
pub fn answer(packet: &[u8], peer: SocketAddr, random: &[u8; 20]) -> Option<Vec<u8>> {
    if packet.get(..2)? != BINDING_REQUEST || !fingerprint_valid(packet) {
        return None;
    }
    let mut message = Vec::with_capacity(packet.len());
    message.extend_from_slice(&BINDING_SUCCESS);
    message.extend_from_slice(&[0, 0]);
    message.extend_from_slice(&packet[4..HEADER]);
    let port = peer.port() ^ 0x2112;
    let mut mapped = vec![0];
    match peer.ip() {
        std::net::IpAddr::V4(ip) => {
            mapped.push(1);
            mapped.extend_from_slice(&port.to_be_bytes());
            mapped.extend(ip.octets().iter().zip(MAGIC).map(|(byte, mask)| byte ^ mask));
        }
        std::net::IpAddr::V6(ip) => {
            mapped.push(2);
            mapped.extend_from_slice(&port.to_be_bytes());
            let mask = MAGIC.iter().chain(&packet[8..HEADER]);
            mapped.extend(ip.octets().iter().zip(mask).map(|(byte, mask)| byte ^ mask));
        }
    }
    attribute(&mut message, ATTR_XOR_MAPPED_ADDRESS, &mapped);
    attribute(&mut message, ATTR_MESSAGE_INTEGRITY, random);
    let reply = finish(message);
    (reply.len() <= packet.len()).then_some(reply)
}

pub fn is_answer(packet: &[u8], transaction: &[u8; 12]) -> bool {
    packet.get(..2) == Some(&BINDING_SUCCESS[..])
        && packet.get(8..HEADER) == Some(&transaction[..])
        && fingerprint_valid(packet)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn random() -> [u8; 64] {
        std::array::from_fn(|index| (index as u8).wrapping_mul(37).wrapping_add(11))
    }

    #[test]
    fn crc_matches_the_standard_check_value() {
        assert_eq!(crc32(b"123456789"), 0xcbf4_3926);
    }

    #[test]
    fn request_is_well_formed() {
        let (message, transaction) = request(&random());
        assert_eq!(message.len() % 4, 0);
        assert_eq!(usize::from(u16::from_be_bytes([message[2], message[3]])) + HEADER, message.len());
        assert_eq!(message[4..8], MAGIC);
        assert_eq!(message[8..20], transaction);
        assert!(fingerprint_valid(&message));
        assert!(may_be_request(&message));
    }

    #[test]
    fn answer_round_trip_for_both_families() {
        let (message, transaction) = request(&random());
        for peer in ["203.0.113.7:46000", "[2001:db8::1]:46000"] {
            let peer: SocketAddr = peer.parse().unwrap();
            let reply = answer(&message, peer, &[7; 20]).unwrap();
            assert!(reply.len() <= message.len());
            assert!(is_answer(&reply, &transaction));
            assert!(!is_answer(&reply, &[0; 12]));
        }
        let reply = answer(&message, "203.0.113.7:46000".parse().unwrap(), &[7; 20]).unwrap();
        // XOR-MAPPED-ADDRESS decodes back to the peer.
        assert_eq!(&reply[20..22], &ATTR_XOR_MAPPED_ADDRESS.to_be_bytes());
        assert_eq!(u16::from_be_bytes([reply[26], reply[27]]) ^ 0x2112, 46000);
        assert_eq!(reply[28] ^ MAGIC[0], 203);
    }

    #[test]
    fn anything_else_gets_no_answer() {
        let peer: SocketAddr = "203.0.113.7:46000".parse().unwrap();
        let (mut message, _) = request(&random());
        assert!(answer(&message[..10], peer, &[0; 20]).is_none(), "truncated");
        assert!(answer(&[0u8; 60], peer, &[0; 20]).is_none(), "zeroes");
        message[30] ^= 1;
        assert!(answer(&message, peer, &[0; 20]).is_none(), "fingerprint");
        // An answer is not a request, so two ends cannot keep answering each other.
        let (good, _) = request(&random());
        let reply = answer(&good, peer, &[0; 20]).unwrap();
        assert!(answer(&reply, peer, &[0; 20]).is_none());
    }
}
