//! The client on a VPS, as the exit of a cascade: no VpnService here, so it opens its own TUN once the server has given
//! it an address, lets the hook set up routing, and hands the descriptor over the same socket Android uses.

use anyhow::{Context, Result, bail};
use nix::sys::socket::{
    AddressFamily, ControlMessage, MsgFlags, SockFlag, SockType, UnixAddr, connect, sendmsg, socket,
};
use std::io::IoSlice;
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};

pub struct ExitTun {
    pub name: String,
    pub socket: String,
    pub hook: String,
}

#[repr(C)]
struct InterfaceRequest {
    name: [u8; 16],
    flags: libc::c_short,
    padding: [u8; 22],
}

fn open_tun(name: &str) -> Result<OwnedFd> {
    if name.is_empty() || name.len() >= 16 {
        bail!("bad TUN name {name:?}");
    }
    let raw = unsafe { libc::open(c"/dev/net/tun".as_ptr(), libc::O_RDWR | libc::O_NONBLOCK | libc::O_CLOEXEC) };
    if raw < 0 {
        return Err(std::io::Error::last_os_error()).context("/dev/net/tun");
    }
    let tun = unsafe { OwnedFd::from_raw_fd(raw) };
    let mut request = InterfaceRequest {
        name: [0; 16],
        flags: (libc::IFF_TUN | libc::IFF_NO_PI) as libc::c_short,
        padding: [0; 22],
    };
    request.name[..name.len()].copy_from_slice(name.as_bytes());
    if unsafe { libc::ioctl(tun.as_raw_fd(), libc::TUNSETIFF as _, &mut request) } < 0 {
        return Err(std::io::Error::last_os_error()).context("TUNSETIFF");
    }
    Ok(tun)
}

fn hand_over(socket_name: &str, tun: &OwnedFd) -> Result<()> {
    let address = UnixAddr::new_abstract(socket_name.as_bytes())?;
    let connection = socket(AddressFamily::Unix, SockType::Stream, SockFlag::SOCK_CLOEXEC, None)?;
    connect(connection.as_raw_fd(), &address)?;
    let descriptors = [tun.as_raw_fd()];
    sendmsg::<UnixAddr>(
        connection.as_raw_fd(),
        &[IoSlice::new(&[1])],
        &[ControlMessage::ScmRights(&descriptors)],
        MsgFlags::empty(),
        None,
    )?;
    Ok(())
}

/// Called once with the address the exit server assigned to this client.
pub fn attach(exit: &ExitTun, ip: &str) -> Result<()> {
    let tun = open_tun(&exit.name)?;
    let status = std::process::Command::new(&exit.hook).arg(ip).status().context("hook")?;
    if !status.success() {
        bail!("hook failed: {status}");
    }
    hand_over(&exit.socket, &tun)
}
