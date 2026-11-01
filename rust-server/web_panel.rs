use crate::model::secret_eq;
use crate::{
    App, lock_unpoison, log_event,
    model::{
        self, ClientGroup, PasswordEntry, RoutingRule, is_expired, now, random_password,
        random_token,
    },
    protocol, read_unpoison,
};
use axum::{
    Json, Router,
    extract::{DefaultBodyLimit, Path, Query, State},
    http::{HeaderMap, StatusCode, header},
    response::{IntoResponse, Response},
    routing::{get, post, put},
};
use serde::{Deserialize, Serialize};
use std::{
    io::Read,
    net::{Ipv4Addr, SocketAddr},
    sync::{Arc, LazyLock, Mutex, atomic::Ordering},
    time::{Duration, Instant},
};

#[derive(Clone)]
struct WebState {
    app: Arc<App>,
}

#[derive(Deserialize)]
struct CreateClientRequest {
    name: String,
    days: i64,
    #[serde(default)]
    workers: Option<u32>,
    #[serde(default)]
    password: Option<String>,
    #[serde(default)]
    hash: String,
    #[serde(default)]
    vk_token: bool,
    #[serde(default, alias = "dtls_port")]
    peer_port: Option<u16>,
    #[serde(default)]
    local_port: Option<u16>,
    #[serde(default)]
    group_id: Option<String>,
    /// 0 — без лимита; поле не передано — не менять (при создании — одно устройство).
    #[serde(default)]
    max_devices: Option<u32>,
}

/// 1 хранится как `None` (значение по умолчанию), 0 — без лимита.
fn validate_max_devices(value: u32) -> Result<Option<u32>, &'static str> {
    match value {
        1 => Ok(None),
        n if n <= model::MAX_DEVICES_LIMIT => Ok(Some(n)),
        _ => Err("max_devices must be 0 (unlimited) or between 1 and 1000"),
    }
}

#[derive(Deserialize, Default)]
struct UnbindQuery {
    /// Пусто — отвязать все устройства ссылки.
    #[serde(default)]
    device: String,
}

#[derive(Serialize)]
struct DeviceInfo {
    id: String,
    name: String,
    ip: String,
    bound_at: i64,
    last_seen: i64,
    online: bool,
}

fn device_infos(
    sessions: &dashmap::DashMap<u64, Arc<crate::protocol::Session>>,
    password: &str,
    devices: &std::collections::BTreeMap<String, model::BoundDevice>,
) -> Vec<DeviceInfo> {
    let online: std::collections::HashSet<String> = sessions
        .iter()
        .filter(|entry| entry.value().password == password)
        .map(|entry| {
            let id = crate::protocol::session_device_id(entry.value());
            if id.is_empty() {
                model::LEGACY_DEVICE_ID.to_owned()
            } else {
                id
            }
        })
        .collect();
    devices
        .iter()
        .map(|(id, device)| DeviceInfo {
            id: id.clone(),
            name: if id == model::LEGACY_DEVICE_ID && device.name.is_empty() {
                "Старая версия приложения".to_owned()
            } else {
                device.name.clone()
            },
            ip: device.ip.clone(),
            bound_at: device.bound_at,
            last_seen: device.last_seen,
            online: online.contains(id),
        })
        .collect()
}

/// Отвязать устройство (или все) и оборвать их сессии.
async fn unbind_client_devices(
    state: &WebState,
    password: &str,
    device_id: &str,
) -> Result<(), Response> {
    let (revision, removed) = {
        let mut db = state.app.db.write().await;
        let removed = model::unbind_devices(&mut db, password, device_id);
        let revision = (!removed.is_empty()).then(|| state.app.db_persistence.submit(db.clone()));
        (revision, removed)
    };
    if let Some(revision) = revision {
        wait_for_persistence(state, revision).await?;
    }
    if device_id.is_empty() {
        crate::protocol::drop_password_sessions(&state.app, password);
        crate::protocol::forget_idle_password_epoch(&state.app, password);
    } else {
        for id in removed {
            crate::protocol::drop_device_sessions(&state.app, password, &id);
        }
    }
    Ok(())
}

fn validate_workers(workers: Option<u32>) -> Result<Option<u32>, &'static str> {
    match workers {
        None | Some(0) => Ok(None),
        Some(w) if (3..=100).contains(&w) => Ok(Some(w)),
        _ => Err("workers must be 0 (default) or between 3 and 100"),
    }
}

#[derive(Deserialize)]
struct ExtendClientRequest {
    days: i64,
}

#[derive(Deserialize)]
struct SettingsRequest {
    #[serde(default)]
    main_password: Option<String>,
    #[serde(default)]
    dns_provider: Option<String>,
    #[serde(default)]
    dns_primary: Option<String>,
    #[serde(default)]
    dns_secondary: Option<String>,
    #[serde(default)]
    auto_restart_interval_hours: Option<u8>,
    #[serde(default)]
    vk_token: Option<String>,
    #[serde(default)]
    vk_hashes: Option<String>,
    #[serde(default)]
    jitsi_room: Option<String>,
    #[serde(default)]
    jitsi_seats: Option<u8>,
}

#[derive(Clone, Copy)]
struct DnsProvider {
    id: &'static str,
    primary: &'static str,
    secondary: &'static str,
}

const DNS_PROVIDERS: [DnsProvider; 13] = [
    DnsProvider {
        id: "yandex",
        primary: "77.88.8.8",
        secondary: "77.88.8.1",
    },
    DnsProvider {
        id: "cloudflare",
        primary: "1.1.1.1",
        secondary: "1.0.0.1",
    },
    DnsProvider {
        id: "google",
        primary: "8.8.8.8",
        secondary: "8.8.4.4",
    },
    DnsProvider {
        id: "adguard",
        primary: "94.140.14.14",
        secondary: "94.140.15.14",
    },
    DnsProvider {
        id: "xbox",
        primary: "111.88.96.54",
        secondary: "111.88.96.55",
    },
    DnsProvider {
        id: "comms",
        primary: "83.220.169.155",
        secondary: "212.109.195.93",
    },
    DnsProvider {
        id: "geohide",
        primary: "193.233.112.67",
        secondary: "193.233.112.88",
    },
    DnsProvider {
        id: "quad9",
        primary: "9.9.9.9",
        secondary: "149.112.112.112",
    },
    DnsProvider {
        id: "opendns",
        primary: "208.67.222.222",
        secondary: "208.67.220.220",
    },
    DnsProvider {
        id: "rostelecom",
        primary: "95.189.32.74",
        secondary: "195.208.5.1",
    },
    DnsProvider {
        id: "nextdns",
        primary: "45.90.28.181",
        secondary: "45.90.30.181",
    },
    DnsProvider {
        id: "bizone",
        primary: "195.208.6.1",
        secondary: "195.208.7.1",
    },
    DnsProvider {
        id: "nsdi",
        primary: "195.208.4.1",
        secondary: "195.208.5.1",
    },
];

fn dns_provider_by_id(id: &str) -> Option<DnsProvider> {
    DNS_PROVIDERS
        .iter()
        .copied()
        .find(|provider| provider.id == id)
}

pub(crate) fn dns_provider_is_known(id: &str) -> bool {
    dns_provider_by_id(id).is_some()
}

pub(crate) fn dns_provider_id(dns: &str) -> Option<&'static str> {
    DNS_PROVIDERS
        .iter()
        .find(|provider| dns == format!("{},{}", provider.primary, provider.secondary))
        .map(|provider| provider.id)
}

fn provider_dns(provider: DnsProvider) -> String {
    format!("{},{}", provider.primary, provider.secondary)
}

pub(crate) fn provider_dns_by_id(id: &str) -> Option<String> {
    dns_provider_by_id(id).map(provider_dns)
}

fn manual_dns(primary: Option<&str>, secondary: Option<&str>) -> Result<String, &'static str> {
    let Some(primary) = primary else {
        return Err("primary DNS is required");
    };
    let primary = primary.trim();
    let secondary = secondary.unwrap_or_default().trim();
    if primary.is_empty() {
        return Err("primary DNS is required");
    }
    if primary.parse::<Ipv4Addr>().is_err()
        || (!secondary.is_empty() && secondary.parse::<Ipv4Addr>().is_err())
    {
        return Err("DNS must be a valid IPv4 address");
    }
    Ok(if secondary.is_empty() {
        primary.to_owned()
    } else {
        format!("{primary},{secondary}")
    })
}

pub(crate) fn deploy_dns_profile_or_yandex(existing_dns: &str) -> String {
    let addresses: Vec<_> = existing_dns
        .split(',')
        .map(str::trim)
        .filter(|address| !address.is_empty())
        .collect();
    if addresses
        .iter()
        .any(|&a| a == "95.182.120.241" || a == "37.230.192.51")
    {
        return "193.233.112.67,193.233.112.88".to_owned();
    }
    if addresses
        .iter()
        .any(|&a| a == "111.88.96.50" || a == "111.88.96.51")
    {
        return "111.88.96.54,111.88.96.55".to_owned();
    }
    let first = addresses.first().copied();
    let selected = DNS_PROVIDERS
        .iter()
        .filter_map(|provider| {
            let matches = addresses
                .iter()
                .filter(|address| **address == provider.primary || **address == provider.secondary)
                .count();
            (matches > 0).then_some((
                provider,
                (
                    matches,
                    usize::from(first == Some(provider.primary)),
                    usize::from(first == Some(provider.secondary)),
                ),
            ))
        })
        .max_by_key(|(_, score)| *score)
        .map(|(provider, _)| *provider)
        .unwrap_or(DNS_PROVIDERS[0]);
    provider_dns(selected)
}

const WEB_BODY_LIMIT_BYTES: usize = 4 * 1024 * 1024;
const MAX_CLIENT_NAME_BYTES: usize = 128;
const MAX_PROXY_PROFILE_NAME_BYTES: usize = 64;

fn validate_text_field(
    value: &str,
    max_bytes: usize,
    message: &'static str,
) -> Result<(), &'static str> {
    if value.len() > max_bytes || value.chars().any(char::is_control) {
        return Err(message);
    }
    Ok(())
}

fn validate_client_name(name: &str) -> Result<(), &'static str> {
    validate_text_field(
        name,
        MAX_CLIENT_NAME_BYTES,
        "client name is too long or invalid",
    )
}

fn validate_proxy_profile_name(name: &str) -> Result<(), &'static str> {
    validate_text_field(
        name,
        MAX_PROXY_PROFILE_NAME_BYTES,
        "proxy profile name is too long or invalid",
    )
}

#[derive(Serialize)]
struct ClientInfo {
    password: String,
    down: i64,
    up: i64,
    expires: i64,
    active: bool,
    active_sessions: usize,
    vk_hash: String,
    ports: String,
    ip: String,
    name: String,
    vk_hashes: String,
    vk_token: bool,
    peer_port: u16,
    local_port: u16,
    #[serde(default)]
    group_id: String,
    #[serde(default)]
    routing_rule_id: String,
    #[serde(default)]
    link: String,
    #[serde(default)]
    sub_uuid: String,
    #[serde(default)]
    workers: Option<u32>,
    /// 0 — без лимита.
    max_devices: u32,
    devices: Vec<DeviceInfo>,
}

fn url_encode(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for b in input.bytes() {
        match b {
            b'a'..=b'z' | b'A'..=b'Z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                out.push(b as char);
            }
            _ => {
                use std::fmt::Write;
                let _ = write!(out, "%{:02X}", b);
            }
        }
    }
    out
}

pub const MRTT_CIPHER_KEY_SEED: &str = "mrtt-chiper-key";

pub fn encrypt_mrtt_link(payload: &str) -> String {
    use aws_lc_rs::aead::{AES_256_GCM, Aad, LessSafeKey, Nonce, UnboundKey};
    use base64::Engine;
    use hmac::{Hmac, KeyInit, Mac};
    use sha2::{Digest, Sha256};

    let key_hash = Sha256::digest(MRTT_CIPHER_KEY_SEED.as_bytes());
    let unbound = match UnboundKey::new(&AES_256_GCM, &key_hash) {
        Ok(k) => k,
        Err(_) => {
            let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
            return format!("mrtt://{b64}");
        }
    };
    let key = LessSafeKey::new(unbound);

    let mut iv = [0u8; 12];
    type HmacSha256 = Hmac<Sha256>;
    if let Ok(mut mac) = HmacSha256::new_from_slice(&key_hash) {
        mac.update(payload.as_bytes());
        let result = mac.finalize().into_bytes();
        iv.copy_from_slice(&result[..12]);
    } else {
        use rand::RngCore;
        rand::rngs::OsRng.fill_bytes(&mut iv);
    }
    let nonce = match Nonce::try_assume_unique_for_key(&iv) {
        Ok(n) => n,
        Err(_) => {
            let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
            return format!("mrtt://{b64}");
        }
    };

    let mut in_out = payload.as_bytes().to_vec();
    if key
        .seal_in_place_append_tag(nonce, Aad::empty(), &mut in_out)
        .is_err()
    {
        let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
        return format!("mrtt://{b64}");
    }

    let mut combined = Vec::with_capacity(12 + in_out.len());
    combined.extend_from_slice(&iv);
    combined.extend_from_slice(&in_out);

    let b64 = base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(&combined);
    format!("mrtt://{b64}")
}

#[allow(dead_code)]
pub fn decrypt_mrtt_link(raw_link: &str) -> Option<String> {
    use aws_lc_rs::aead::{AES_256_GCM, Aad, LessSafeKey, Nonce, UnboundKey};
    use base64::Engine;
    use sha2::{Digest, Sha256};

    let payload = raw_link.trim();
    let payload = payload.strip_prefix("mrtt://")?.trim();
    let clean = payload.replace(['\n', '\r'], "");
    let bytes = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .decode(&clean)
        .or_else(|_| base64::engine::general_purpose::URL_SAFE.decode(&clean))
        .or_else(|_| base64::engine::general_purpose::STANDARD_NO_PAD.decode(&clean))
        .or_else(|_| base64::engine::general_purpose::STANDARD.decode(&clean))
        .ok()?;

    if bytes.len() >= 28 {
        let key_hash = Sha256::digest(MRTT_CIPHER_KEY_SEED.as_bytes());
        if let Ok(unbound) = UnboundKey::new(&AES_256_GCM, &key_hash) {
            let key = LessSafeKey::new(unbound);
            let mut iv = [0u8; 12];
            iv.copy_from_slice(&bytes[..12]);
            if let Ok(nonce) = Nonce::try_assume_unique_for_key(&iv) {
                let mut ciphertext = bytes[12..].to_vec();
                if let Ok(decrypted) = key.open_in_place(nonce, Aad::empty(), &mut ciphertext)
                    && let Ok(s) = String::from_utf8(decrypted.to_vec())
                {
                    return Some(s);
                }
            }
        }
    }

    None
}

fn make_client_link(
    host: &str,
    port: u16,
    password: &str,
    vk_hashes: &str,
    vk_token: bool,
    shared_token: &str,
) -> String {
    let mut payload = format!(
        "connect?v=2&host={}&peer={}",
        url_encode(host),
        if port == 0 { 46000 } else { port },
    );
    if !password.is_empty() {
        payload.push_str("&password=");
        payload.push_str(&url_encode(password));
    }
    let hashes: Vec<&str> = vk_hashes
        .split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .collect();
    if !hashes.is_empty() {
        payload.push_str("&hashes=");
        payload.push_str(
            &hashes
                .iter()
                .map(|h| url_encode(h))
                .collect::<Vec<_>>()
                .join("+"),
        );
    } else if vk_token && !shared_token.is_empty() {
        payload.push_str("&token=");
        payload.push_str(&url_encode(shared_token));
    }
    if let Some(room) = crate::jitsi_relay::current_room() {
        payload.push_str("&jitsi=");
        payload.push_str(&url_encode(&room));
        payload.push_str(&format!("&seats={}", crate::jitsi_relay::current_seats()));
    }
    encrypt_mrtt_link(&payload)
}

#[inline]
fn make_main_link(host: &str, port: u16, db: &model::Database) -> String {
    let vk_hashes = if db.main_vk_hashes.is_empty() {
        &db.vk_hashes
    } else {
        &db.main_vk_hashes
    };
    make_client_link(
        host,
        port,
        &db.main_password,
        vk_hashes,
        db.main_vk_token_enabled,
        &db.vk_access_token,
    )
}

#[inline]
fn make_entry_link(
    host: &str,
    port: u16,
    password: &str,
    entry: &model::PasswordEntry,
    shared_token: &str,
) -> String {
    make_client_link(
        host,
        port,
        password,
        &entry.vk_hashes,
        entry.vk_token_enabled,
        shared_token,
    )
}

async fn refresh_credentials_report(state: &WebState) -> Result<(), String> {
    match crate::protocol::refresh_credentials(&state.app).await {
        Ok(()) => Ok(()),
        Err(error) => {
            let message = format!("{error:#}");
            log_event(
                &state.app,
                "ERROR",
                "AUTH",
                &format!("Change saved but credential reload failed: {message}"),
            );
            Err(message)
        }
    }
}

async fn wait_for_persistence(state: &WebState, revision: u64) -> Result<(), Response> {
    match tokio::time::timeout(
        Duration::from_secs(15),
        state.app.db_persistence.wait(revision),
    )
    .await
    {
        Ok(Ok(())) => Ok(()),
        Ok(Err(error)) => {
            eprintln!("[WEB] Failed to persist database mutation: {error:#}");
            Err((
                StatusCode::INTERNAL_SERVER_ERROR,
                "failed to persist change",
            )
                .into_response())
        }
        Err(_) => Err((
            StatusCode::GATEWAY_TIMEOUT,
            "database persistence is taking too long; refresh before retrying",
        )
            .into_response()),
    }
}

fn proc_kib(text: &str, field: &str) -> Option<u64> {
    text.lines()
        .find(|line| line.starts_with(field))?
        .split_whitespace()
        .nth(1)?
        .parse()
        .ok()
}

fn read_proc_text<'a>(path: &str, buffer: &'a mut [u8]) -> Option<&'a str> {
    let mut file = std::fs::File::open(path).ok()?;
    let len = file.read(buffer).ok()?;
    std::str::from_utf8(&buffer[..len]).ok()
}

#[derive(Clone, Copy, Default)]
struct ProcessMemory {
    rss: u64,
    peak: u64,
    anonymous: u64,
    file: u64,
    shared: u64,
    swap: u64,
}

fn process_memory(text: &str) -> ProcessMemory {
    ProcessMemory {
        rss: proc_kib(text, "VmRSS:").unwrap_or(0),
        peak: proc_kib(text, "VmHWM:").unwrap_or(0),
        anonymous: proc_kib(text, "RssAnon:").unwrap_or(0),
        file: proc_kib(text, "RssFile:").unwrap_or(0),
        shared: proc_kib(text, "RssShmem:").unwrap_or(0),
        swap: proc_kib(text, "VmSwap:").unwrap_or(0),
    }
}

#[derive(Clone, Copy, Default)]
struct ProcessMemoryRollup {
    pss: u64,
    pss_anon: u64,
    anonymous: u64,
    file: u64,
    shmem: u64,
    private: u64,
    shared: u64,
    swap_pss: u64,
}

fn process_memory_rollup(text: &str) -> ProcessMemoryRollup {
    let private_clean = proc_kib(text, "Private_Clean:").unwrap_or(0);
    let private_dirty = proc_kib(text, "Private_Dirty:").unwrap_or(0);
    let shared_clean = proc_kib(text, "Shared_Clean:").unwrap_or(0);
    let shared_dirty = proc_kib(text, "Shared_Dirty:").unwrap_or(0);
    ProcessMemoryRollup {
        pss: proc_kib(text, "Pss:").unwrap_or(0),
        pss_anon: proc_kib(text, "Pss_Anon:").unwrap_or(0),
        anonymous: proc_kib(text, "Anonymous:").unwrap_or(0),
        file: proc_kib(text, "Pss_File:").unwrap_or(0),
        shmem: proc_kib(text, "Pss_Shmem:").unwrap_or(0),
        private: private_clean.saturating_add(private_dirty),
        shared: shared_clean.saturating_add(shared_dirty),
        swap_pss: proc_kib(text, "SwapPss:").unwrap_or(0),
    }
}

#[derive(Default)]
struct SystemStatsCache {
    sampled_at: Option<Instant>,
    process_memory: ProcessMemory,
    process_rollup: ProcessMemoryRollup,
    total_ram: u64,
}

static SYSTEM_STATS_CACHE: LazyLock<Mutex<SystemStatsCache>> =
    LazyLock::new(|| Mutex::new(SystemStatsCache::default()));
const SYSTEM_STATS_CACHE_TTL: Duration = Duration::from_secs(60);

fn cached_system_stats() -> (ProcessMemory, ProcessMemoryRollup, u64) {
    let mut cache = lock_unpoison(&SYSTEM_STATS_CACHE);
    let refresh = cache
        .sampled_at
        .is_none_or(|sampled_at| sampled_at.elapsed() >= SYSTEM_STATS_CACHE_TTL);
    if refresh {
        let mut buffer = [0_u8; 4096];
        cache.process_memory = read_proc_text("/proc/self/status", &mut buffer)
            .map(process_memory)
            .unwrap_or_default();
        cache.process_rollup = read_proc_text("/proc/self/smaps_rollup", &mut buffer)
            .map(process_memory_rollup)
            .unwrap_or_default();
        cache.total_ram = read_proc_text("/proc/meminfo", &mut buffer)
            .and_then(|text| proc_kib(text, "MemTotal:"))
            .unwrap_or(0);
        cache.sampled_at = Some(Instant::now());
    }
    (cache.process_memory, cache.process_rollup, cache.total_ram)
}

fn format_memory_kib(kib: u64) -> String {
    let (value, unit) = if kib >= 1024 * 1024 {
        (kib as f64 / (1024.0 * 1024.0), "GB")
    } else {
        (kib as f64 / 1024.0, "MB")
    };
    let mut value = format!("{value:.2}");
    while value.ends_with('0') {
        value.pop();
    }
    if value.ends_with('.') {
        value.pop();
    }
    format!("{value} {unit}")
}

fn format_total_memory_kib(kib: u64) -> String {
    if kib >= 1024 * 1024 {
        let mut value = format!("{:.1}", kib as f64 / (1024.0 * 1024.0));
        if value.ends_with(".0") {
            value.truncate(value.len() - 2);
        }
        format!("{value} GB")
    } else {
        format!("{} MB", (kib + 512) / 1024)
    }
}

async fn stats(State(state): State<WebState>, _headers: HeaderMap) -> impl IntoResponse {
    let (process_memory, process_rollup, total_ram) = cached_system_stats();
    let io = *read_unpoison(&protocol::GLOBAL_IO_COUNTERS);

    let cpu_total = state.app.cpu_percent.load(Ordering::Relaxed);
    let cpu_cores = state.app.cpu_cores.load(Ordering::Relaxed).max(1);
    let cpu_capacity = cpu_cores.saturating_mul(100);
    let cpu_normalized = cpu_total
        .saturating_mul(100)
        .checked_div(cpu_capacity)
        .unwrap_or(0)
        .min(100);
    let log_ring_bytes = crate::lock_unpoison(&state.app.logs)
        .iter()
        .map(String::len)
        .sum::<usize>();
    let db = state.app.db.read().await;
    let (
        local_proxy_active,
        local_proxy_tcp_sessions,
        local_proxy_udp_flows,
        local_proxy_tcp_peak,
        local_proxy_udp_peak,
        local_proxy_tcp_total,
        local_proxy_udp_total,
    ) = {
        let route = state.app.proxy_route.read().await;
        let active = route.as_ref().is_some_and(|route| route.is_alive());
        let proxy_stats = route
            .as_ref()
            .map(|route| route.diagnostic_snapshot())
            .unwrap_or_default();
        (
            active,
            proxy_stats.tcp_active,
            proxy_stats.udp_active,
            proxy_stats.tcp_peak,
            proxy_stats.udp_peak,
            proxy_stats.tcp_total,
            proxy_stats.udp_total,
        )
    };
    Json(serde_json::json!({
        "ram": format!("{} / {}", format_memory_kib(process_memory.rss), format_total_memory_kib(total_ram)),
        "ram_used": format_memory_kib(process_memory.rss),
        "ram_total": format_total_memory_kib(total_ram),
        "ram_peak": format_memory_kib(process_memory.peak),
        "ram_anonymous": format_memory_kib(process_memory.anonymous),
        "ram_file": format_memory_kib(process_memory.file),
        "ram_shared": format_memory_kib(process_memory.shared),
        "ram_swap": format_memory_kib(process_memory.swap),
        "ram_pss": format_memory_kib(process_rollup.pss),
        "ram_private": format_memory_kib(process_rollup.private),
        "ram_shared_rollup": format_memory_kib(process_rollup.shared),
        "ram_anon_rollup": format_memory_kib(process_rollup.pss_anon),
        "ram_anonymous_rollup": format_memory_kib(process_rollup.anonymous),
        "ram_file_rollup": format_memory_kib(process_rollup.file),
        "ram_shmem_rollup": format_memory_kib(process_rollup.shmem),
        "ram_swap_pss": format_memory_kib(process_rollup.swap_pss),
        "ram_used_kib": process_memory.rss,
        "ram_peak_kib": process_memory.peak,
        "ram_anonymous_kib": process_memory.anonymous,
        "ram_file_kib": process_memory.file,
        "ram_shared_kib": process_memory.shared,
        "ram_swap_kib": process_memory.swap,
        "ram_pss_kib": process_rollup.pss,
        "ram_private_kib": process_rollup.private,
        "ram_shared_rollup_kib": process_rollup.shared,
        "ram_anon_rollup_kib": process_rollup.pss_anon,
        "ram_anonymous_rollup_kib": process_rollup.anonymous,
        "ram_file_rollup_kib": process_rollup.file,
        "ram_shmem_rollup_kib": process_rollup.shmem,
        "ram_swap_pss_kib": process_rollup.swap_pss,
        "cpu_total": cpu_total,
        "cpu_capacity": cpu_capacity,
        "cpu_normalized": cpu_normalized,
        "status": "Active",
        "peer_port": state.app.listen.port(),
        "public_ip": state.app.public_ip.read().await.clone(),
        "uptime": now().saturating_sub(state.app.started),
        "active": state.app.sessions.len(),
        "total": state.app.total_connections.load(Ordering::Relaxed),
        "up": state.app.bytes_from_client.load(Ordering::Relaxed),
        "down": state.app.bytes_to_client.load(Ordering::Relaxed),
        "udp_rx_packets": io.udp_rx_packets,
        "udp_rx_bytes": io.udp_rx_bytes,
        "udp_rx_errors": io.udp_rx_errors,
        "udp_tx_packets": io.udp_tx_packets,
        "udp_tx_bytes": io.udp_tx_bytes,
        "udp_tx_errors": io.udp_tx_errors,
        "passwords": db.passwords.len(),
        "transport": "RTP/SRTP",
        "tunnel": "userspace-tun",
        "allocator": crate::allocator_name(),
        "local_proxy_enabled": !db.local_proxy.active_profile_id.is_empty(),
        "local_proxy_port": db.local_proxy.active_profile().map(|p| p.port).unwrap_or(0),
        "local_proxy_active": local_proxy_active,
        "local_proxy_port_listening": state.app.proxy_port_listening.load(Ordering::Acquire),
        "local_proxy_health_error": crate::read_unpoison(&state.app.proxy_health_error).clone(),
        "local_proxy_tcp_sessions": local_proxy_tcp_sessions,
        "local_proxy_udp_flows": local_proxy_udp_flows,
        "local_proxy_tcp_peak": local_proxy_tcp_peak,
        "local_proxy_udp_peak": local_proxy_udp_peak,
        "local_proxy_tcp_total": local_proxy_tcp_total,
        "local_proxy_udp_total": local_proxy_udp_total,
        "hot_sessions": protocol::ACTIVE_SESSIONS_GAUGE.load(Ordering::Relaxed),
        "hot_session_capacity": protocol::HOT_SESSION_CAPACITY_GAUGE.load(Ordering::Relaxed),
        "public_session_capacity": state.app.sessions.capacity(),
        "mem_engine_epochs": protocol::epoch_snapshot_len(),
        "mem_password_epochs": state.app.password_epochs.len(),
        "mem_password_epoch_capacity": state.app.password_epochs.capacity(),
        "mem_derived_keys": state.app.derived_keys.len(),
        "mem_log_ring": crate::lock_unpoison(&state.app.logs).len(),
        "mem_log_bytes": log_ring_bytes,
        "mem_dpi_ring": protocol::dpi_ring_len(),
        "mem_stream_repairs": protocol::STREAM_REPAIRS_GAUGE.load(Ordering::Relaxed),
        "mem_stream_inventory": protocol::STREAM_INVENTORY_GAUGE.load(Ordering::Relaxed),
        "memory_trim_count": state.app.memory_trim_count.load(Ordering::Relaxed),
        "memory_trim_last_unix": state.app.memory_trim_last_unix.load(Ordering::Relaxed)
    }))
}

async fn memory_trim(State(state): State<WebState>) -> Response {
    match crate::trim_memory(&state.app).await {
        Ok(allocator_collected) => Json(serde_json::json!({
            "compacted": true,
            "allocator_collected": allocator_collected,
            "allocator_collection_scope": "not-supported-by-system-allocator",
            "trim_count": state.app.memory_trim_count.load(Ordering::Relaxed),
            "trimmed_at": state.app.memory_trim_last_unix.load(Ordering::Relaxed),
        }))
        .into_response(),
        Err(error) => {
            log_event(
                &state.app,
                "WARNING",
                "MEMORY",
                &format!("Memory trim skipped: {error:#}"),
            );
            (
                StatusCode::SERVICE_UNAVAILABLE,
                "memory trim is temporarily unavailable",
            )
                .into_response()
        }
    }
}

async fn settings_get(State(state): State<WebState>) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let dns = db.dns.clone();
    let mut parts = dns.splitn(2, ',');
    let primary = parts.next().unwrap_or("77.88.8.8").trim().to_owned();
    let secondary = parts.next().unwrap_or("77.88.8.1").trim().to_owned();
    let provider = if db.dns_provider == "custom" || dns_provider_by_id(&db.dns_provider).is_some()
    {
        db.dns_provider.as_str()
    } else {
        dns_provider_id(&dns).unwrap_or("custom")
    };
    let vk_hashes = if !db.vk_hashes.is_empty() {
        db.vk_hashes.clone()
    } else {
        db.passwords
            .values()
            .find(|p| !p.vk_hashes.is_empty())
            .map(|p| p.vk_hashes.clone())
            .unwrap_or_default()
    };
    Json(serde_json::json!({
        "main_password": db.main_password,
        "dns_provider": provider,
        "dns_primary": primary,
        "dns_secondary": secondary,
        "auto_restart_interval_hours": db.auto_restart_interval_hours(),
        "vk_token": db.vk_access_token,
        "vk_hashes": vk_hashes,
        "peer_port": state.app.listen.port(),
        "jitsi_room": db.jitsi_room,
        "jitsi_seats": db.jitsi_seats(),
        "restart_required": db.main_password != state.app.startup_main_password
    }))
}

async fn settings_post(
    State(state): State<WebState>,
    Json(request): Json<SettingsRequest>,
) -> Response {
    if let Some(password) = request.main_password.as_ref()
        && !password.is_empty()
        && password.len() > 128
    {
        return (
            StatusCode::BAD_REQUEST,
            "password length must be 1..128 or empty",
        )
            .into_response();
    }
    let dns = match request.dns_provider.as_deref() {
        Some("custom") | None
            if request.dns_primary.is_some() || request.dns_secondary.is_some() =>
        {
            match manual_dns(
                request.dns_primary.as_deref(),
                request.dns_secondary.as_deref(),
            ) {
                Ok(dns) => Some((dns, "custom".to_owned())),
                Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
            }
        }
        Some("custom") => {
            return (StatusCode::BAD_REQUEST, "primary DNS is required").into_response();
        }
        Some(provider_id) => {
            let Some(provider) = dns_provider_by_id(provider_id) else {
                return (StatusCode::BAD_REQUEST, "unknown DNS provider").into_response();
            };
            Some((provider_dns(provider), provider_id.to_owned()))
        }
        None => None,
    };
    if let Some(hours) = request.auto_restart_interval_hours
        && !matches!(hours, 0 | 12 | 24 | 48 | 72)
    {
        return (
            StatusCode::BAD_REQUEST,
            "auto restart interval must be 0, 12, 24, 48, or 72 hours",
        )
            .into_response();
    }
    let requested_vk_access_token = match request.vk_token {
        Some(token) => match normalize_vk_access_token(&token) {
            Ok(token) => Some(token),
            Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
        },
        None => None,
    };
    let requested_vk_hashes = match request.vk_hashes {
        Some(raw) => match normalize_client_vk_hashes(&raw) {
            Ok(hashes) => Some(hashes),
            Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
        },
        None => None,
    };
    let requested_jitsi_room = match request.jitsi_room.as_deref().map(str::trim) {
        Some("") => Some(String::new()),
        Some(raw) => match crate::jitsi_room::RoomTarget::parse(raw) {
            Some(room) => Some(room.to_string()),
            None => return (StatusCode::BAD_REQUEST, "invalid Jitsi room").into_response(),
        },
        None => None,
    };
    if let Some(seats) = request.jitsi_seats
        && !(1..=crate::model::MAX_JITSI_SEATS).contains(&seats)
    {
        return (StatusCode::BAD_REQUEST, "Jitsi seats must be 1..16").into_response();
    }
    let jitsi_changed = requested_jitsi_room.is_some() || request.jitsi_seats.is_some();
    let requested_auto_restart_interval = request.auto_restart_interval_hours;
    let (revision, retired_main_password, applied_dns) = {
        let mut db = state.app.db.write().await;
        let retired_main_password = if let Some(password) = request.main_password {
            if db.passwords.contains_key(&password) {
                return (
                    StatusCode::BAD_REQUEST,
                    "Пароль совпадает с паролем клиента",
                )
                    .into_response();
            }
            (db.main_password != password)
                .then(|| std::mem::replace(&mut db.main_password, password))
        } else {
            None
        };
        let applied_dns = dns
            .as_ref()
            .and_then(|(dns, _)| (db.dns != *dns).then(|| dns.clone()));
        if let Some((dns, provider)) = dns.as_ref() {
            db.dns.clone_from(dns);
            db.dns_provider.clone_from(provider);
        }
        if let Some(hours) = requested_auto_restart_interval {
            db.set_auto_restart_interval_hours(hours);
        }
        if let Some(token) = requested_vk_access_token {
            db.vk_access_token = token;
        }
        if let Some(hashes) = requested_vk_hashes {
            db.vk_hashes = hashes;
        }
        if let Some(room) = requested_jitsi_room {
            db.jitsi_room = room;
        }
        if let Some(seats) = request.jitsi_seats {
            db.jitsi_seats = Some(seats);
        }
        if jitsi_changed {
            crate::jitsi_relay::configure(crate::jitsi_relay::Config::from_db(&db));
        }
        (
            state.app.db_persistence.submit(db.clone()),
            retired_main_password,
            applied_dns,
        )
    };
    match state.app.db_persistence.wait(revision).await {
        Ok(()) => {}
        Err(error) => {
            eprintln!("[SETTINGS] Failed to persist settings: {error}");
            return (
                StatusCode::INTERNAL_SERVER_ERROR,
                "failed to persist settings",
            )
                .into_response();
        }
    }
    if let Some(hours) = requested_auto_restart_interval {
        state.app.auto_restart_interval_tx.send_replace(hours);
    }
    let main_password_changed = retired_main_password.is_some();
    if let Some(password) = retired_main_password
        && !password.is_empty()
    {
        state.app.derived_keys.remove(&password);
    }
    if main_password_changed && let Err(message) = refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-mrtt-reload-error", warning)], "saved").into_response();
    }
    if let Some(dns) = applied_dns {
        *state.app.dns.write().await = dns;
        match crate::protocol::broadcast_tunnel_configuration(&state.app).await {
            Ok(sent) => log_event(
                &state.app,
                "INFO",
                "DNS",
                &format!("DNS configuration applied to {sent} active paths"),
            ),
            Err(error) => {
                let warning = format!("saved but DNS apply failed: {error:#}");
                return (StatusCode::OK, [("x-mrtt-reload-error", warning)], "saved")
                    .into_response();
            }
        }
    }
    let restart_required = {
        let db = state.app.db.read().await;
        db.main_password != state.app.startup_main_password
    };
    Json(serde_json::json!({ "restart_required": restart_required })).into_response()
}

fn is_valid_hostname(name: &str) -> bool {
    if name.is_empty() || name.len() > 253 {
        return false;
    }
    name.split('.').all(|label| {
        !label.is_empty()
            && label.len() <= 63
            && label
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-')
            && !label.starts_with('-')
            && !label.ends_with('-')
    })
}

fn parse_host_header(host: &str) -> Option<(String, Option<String>)> {
    let host = host.trim();
    if host.is_empty() || host.len() > 255 {
        return None;
    }
    let (name, port) = if let Some(rest) = host.strip_prefix('[') {
        let (name, rest) = rest.split_once(']')?;
        let port = match rest.strip_prefix(':') {
            Some(p) => Some(p.to_string()),
            None if rest.is_empty() => None,
            None => return None,
        };
        (name.to_string(), port)
    } else if let Some((name, port)) = host.rsplit_once(':') {
        (name.to_string(), Some(port.to_string()))
    } else {
        (host.to_string(), None)
    };

    if let Some(port) = port.as_deref()
        && (port.is_empty() || port.len() > 5 || !port.bytes().all(|b| b.is_ascii_digit()))
    {
        return None;
    }

    if name.parse::<std::net::IpAddr>().is_err() && !is_valid_hostname(&name) {
        return None;
    }
    Some((name, port))
}

async fn resolve_client_host(headers: &HeaderMap, state: &WebState) -> String {
    let host_header = headers
        .get(header::HOST)
        .and_then(|h| h.to_str().ok())
        .and_then(parse_host_header)
        .map(|(name, _)| name)
        .unwrap_or_default();

    let is_local_or_unspecified = host_header.is_empty()
        || host_header == "0.0.0.0"
        || host_header == "127.0.0.1"
        || host_header == "localhost"
        || host_header == "::1";

    if !is_local_or_unspecified {
        return host_header.to_string();
    }

    let public_ip = state.app.public_ip.read().await.clone();
    if !public_ip.is_empty() && public_ip != "0.0.0.0" && public_ip != "127.0.0.1" {
        return public_ip;
    }

    let listen_ip = state.app.listen.ip().to_string();
    if listen_ip != "0.0.0.0" && listen_ip != "127.0.0.1" {
        return listen_ip;
    }

    if !host_header.is_empty() {
        return host_header.to_string();
    }

    "0.0.0.0".to_string()
}

#[derive(Deserialize, Default)]
struct ClientListQuery {
    #[serde(default)]
    group_id: Option<String>,
    #[serde(default)]
    active: Option<bool>,
    #[serde(default)]
    search: Option<String>,
}

fn count_active_sessions(
    sessions: &dashmap::DashMap<u64, Arc<crate::protocol::Session>>,
    password: &str,
) -> usize {
    if password.is_empty() {
        return 0;
    }
    let mut count = 0;
    for entry in sessions.iter() {
        if entry.value().password == password {
            count += 1;
        }
    }
    count
}

async fn clients_get(
    State(state): State<WebState>,
    headers: HeaderMap,
    Query(query): Query<ClientListQuery>,
) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;

    let mut list: Vec<ClientInfo> = db
        .passwords
        .iter()
        .map(|(password, entry)| {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            ClientInfo {
                password: password.clone(),
                down: entry.down_bytes,
                up: entry.up_bytes,
                expires: entry.expires_at,
                active: !entry.is_deactivated && !is_expired(entry),
                active_sessions: count_active_sessions(&state.app.sessions, password),
                vk_hash: entry.vk_hash.clone(),
                ports: entry.ports.clone(),
                ip: if entry.ip.is_empty() {
                    "10.66.67.3".to_string()
                } else {
                    entry.ip.clone()
                },
                name: entry.name.clone(),
                vk_hashes: entry.vk_hashes.clone(),
                vk_token: entry.vk_token_enabled,
                peer_port: client_peer_port,
                local_port: entry.local_port,
                group_id: entry.group_id.clone(),
                routing_rule_id: entry.routing_rule_id.clone(),
                link: make_entry_link(
                    &host,
                    client_peer_port,
                    password,
                    entry,
                    &db.vk_access_token,
                ),
                sub_uuid: entry.sub_uuid.clone(),
                workers: entry.workers,
                max_devices: entry.max_devices.unwrap_or(1),
                devices: device_infos(&state.app.sessions, password, &entry.devices),
            }
        })
        .collect();
    if !db.main_password.is_empty() {
        let mut session_up = 0;
        let mut session_down = 0;
        for s in state.app.sessions.iter() {
            let sess = s.value();
            if secret_eq(&sess.password, &db.main_password) {
                session_up += sess.up_bytes.load(Ordering::Relaxed) as i64;
                session_down += sess.down_bytes.load(Ordering::Relaxed) as i64;
            }
        }

        let main_peer_port = state.app.listen.port();
        list.push(ClientInfo {
            password: db.main_password.clone(),
            down: db.main_down_bytes + session_down,
            up: db.main_up_bytes + session_up,
            expires: 0,
            active: true,
            active_sessions: count_active_sessions(&state.app.sessions, &db.main_password),
            vk_hash: String::new(),
            ports: String::new(),
            ip: "10.66.67.2".to_string(),
            name: "Владелец".to_string(),
            vk_hashes: if db.main_vk_hashes.is_empty() {
                db.vk_hashes.clone()
            } else {
                db.main_vk_hashes.clone()
            },
            vk_token: db.main_vk_token_enabled,
            peer_port: main_peer_port,
            local_port: 0,
            group_id: String::new(),
            routing_rule_id: db.main_routing_rule_id.clone(),
            link: make_main_link(&host, main_peer_port, &db),
            sub_uuid: db.main_sub_uuid.clone(),
            workers: db.main_workers,
            max_devices: db.main_max_devices.unwrap_or(1),
            devices: device_infos(&state.app.sessions, &db.main_password, &db.main_devices),
        });
    }

    if let Some(ref gid) = query.group_id {
        let g = gid.trim();
        if g == "root" || g == "ungrouped" {
            list.retain(|c| c.group_id.is_empty());
        } else {
            list.retain(|c| c.group_id == g);
        }
    }
    if let Some(act) = query.active {
        list.retain(|c| c.active == act);
    }
    if let Some(ref s) = query.search {
        let q = s.trim().to_lowercase();
        if !q.is_empty() {
            list.retain(|c| {
                c.name.to_lowercase().contains(&q) || c.password.to_lowercase().contains(&q)
            });
        }
    }

    Json(list)
}

async fn group_clients_get(
    State(state): State<WebState>,
    Path(id): Path<String>,
    headers: HeaderMap,
) -> Response {
    let db = state.app.db.read().await;
    if !db.groups.contains_key(&id) {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({ "error": "group not found" })),
        )
            .into_response();
    }
    let host = resolve_client_host(&headers, &state).await;
    let effective_vk_token = db
        .groups
        .get(&id)
        .and_then(|g| g.vk_token.as_deref())
        .unwrap_or(&db.vk_access_token);
    let list: Vec<ClientInfo> = db
        .passwords
        .iter()
        .filter(|(_, entry)| entry.group_id == id)
        .map(|(password, entry)| {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            ClientInfo {
                password: password.clone(),
                down: entry.down_bytes,
                up: entry.up_bytes,
                expires: entry.expires_at,
                active: !entry.is_deactivated && !is_expired(entry),
                active_sessions: count_active_sessions(&state.app.sessions, password),
                vk_hash: entry.vk_hash.clone(),
                ports: entry.ports.clone(),
                ip: if entry.ip.is_empty() {
                    "10.66.67.3".to_string()
                } else {
                    entry.ip.clone()
                },
                name: entry.name.clone(),
                vk_hashes: entry.vk_hashes.clone(),
                vk_token: entry.vk_token_enabled,
                peer_port: client_peer_port,
                local_port: entry.local_port,
                group_id: entry.group_id.clone(),
                routing_rule_id: entry.routing_rule_id.clone(),
                link: make_entry_link(&host, client_peer_port, password, entry, effective_vk_token),
                sub_uuid: entry.sub_uuid.clone(),
                workers: entry.workers,
                max_devices: entry.max_devices.unwrap_or(1),
                devices: device_infos(&state.app.sessions, password, &entry.devices),
            }
        })
        .collect();
    Json(list).into_response()
}

fn normalize_client_vk_hashes(raw: &str) -> Result<String, &'static str> {
    let value = raw.trim();
    if value.is_empty() {
        return Ok(String::new());
    }
    if value.len() > 1024 {
        return Err("VK hashes are too long");
    }
    if value.chars().any(char::is_whitespace) {
        return Err("VK hashes must be comma-separated without spaces");
    }
    let hashes: Vec<_> = value.split(',').collect();
    if hashes.len() > 6 || hashes.iter().any(|hash| hash.len() < 16) {
        return Err("provide 1 to 6 valid VK hashes");
    }
    Ok(hashes.join(","))
}

fn normalize_vk_access_token(raw: &str) -> Result<String, &'static str> {
    let token = raw.trim();
    if token.is_empty() {
        return Ok(String::new());
    }
    if token.len() > 4096 || token.chars().any(char::is_whitespace) {
        return Err("VK token is too long or contains whitespace");
    }
    Ok(token.to_owned())
}

async fn clients_create(
    State(state): State<WebState>,
    headers: HeaderMap,
    Json(request): Json<CreateClientRequest>,
) -> Response {
    if let Err(message) = validate_client_name(&request.name) {
        return (StatusCode::BAD_REQUEST, message).into_response();
    }
    if request.days < 0 || request.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be 0..3650").into_response();
    }
    let vk_hashes = match normalize_client_vk_hashes(&request.hash) {
        Ok(value) => value,
        Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
    };
    if request.vk_token && !vk_hashes.is_empty() {
        return (StatusCode::BAD_REQUEST, "choose VK hashes or VK token").into_response();
    }

    let default_peer_port = state.app.listen.port();
    let peer_port = request.peer_port.unwrap_or(default_peer_port);
    let local_port = request.local_port.unwrap_or(0);
    let ports = format!("{},0,{}", peer_port, local_port);
    let group_id = request.group_id.unwrap_or_default();
    let validated_workers = match validate_workers(request.workers) {
        Ok(w) => w,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };
    let validated_max_devices = match request.max_devices.map(validate_max_devices).transpose() {
        Ok(limit) => limit,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };

    let host = resolve_client_host(&headers, &state).await;

    let (password, expires, revision, link, sub_uuid) = {
        let mut db = state.app.db.write().await;
        if request.vk_token && db.vk_access_token.is_empty() {
            return (StatusCode::BAD_REQUEST, "configure a VK token first").into_response();
        }
        db.passwords.retain(|_, entry| !is_expired(entry));

        let client_name = if request.name.trim().is_empty() {
            model::generate_unique_client_name(model::existing_client_names_in_scope(
                &db, &group_id, None,
            ))
        } else {
            let trimmed = request.name.trim().to_string();
            if model::is_client_name_taken(
                model::existing_client_names_in_scope(&db, &group_id, None),
                &trimmed,
            ) {
                return (
                    StatusCode::BAD_REQUEST,
                    "Участник с таким именем уже существует",
                )
                    .into_response();
            }
            trimmed
        };

        let password = if let Some(ref custom_pw) = request.password {
            let pw = custom_pw.trim();
            if pw.is_empty() || pw.len() > 128 {
                return (
                    StatusCode::BAD_REQUEST,
                    "password must be 1..128 characters",
                )
                    .into_response();
            }
            if db.passwords.contains_key(pw) || pw == db.main_password {
                return (StatusCode::BAD_REQUEST, "password already exists").into_response();
            }
            pw.to_string()
        } else {
            let candidate = (0..64)
                .map(|_| random_password())
                .find(|c| !db.passwords.contains_key(c) && c != &db.main_password);
            let Some(p) = candidate else {
                return StatusCode::INTERNAL_SERVER_ERROR.into_response();
            };
            p
        };

        let expires = if request.days == 0 {
            0
        } else {
            now().saturating_add(request.days.saturating_mul(86400))
        };
        let sub_uuid = model::random_uuid_v4();
        let ip = model::get_next_ip(&db).unwrap_or_default();
        db.passwords.insert(
            password.clone(),
            PasswordEntry {
                ip,
                expires_at: expires,
                down_bytes: 0,
                up_bytes: 0,
                vk_hash: vk_hashes.clone(),
                ports,
                is_deactivated: false,
                name: client_name,
                vk_hashes: vk_hashes.clone(),
                vk_token_enabled: request.vk_token,
                dtls_port: peer_port,
                wg_port: 0,
                local_port,
                group_id: group_id.clone(),
                routing_rule_id: String::new(),
                sub_uuid: sub_uuid.clone(),
                workers: validated_workers,
                max_devices: validated_max_devices.unwrap_or(Some(0)),
                devices: Default::default(),
            },
        );
        let link = make_client_link(
            &host,
            peer_port,
            &password,
            &vk_hashes,
            request.vk_token,
            &db.vk_access_token,
        );
        let revision = state.app.db_persistence.submit(db.clone());
        (password, expires, revision, link, sub_uuid)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let reload_error = refresh_credentials_report(&state).await.err();

    Json(serde_json::json!({
        "password": password,
        "expires": expires,
        "peer_port": peer_port,
        "local_port": local_port,
        "vk_hashes": vk_hashes,
        "vk_token": request.vk_token,
        "group_id": group_id,
        "workers": validated_workers.unwrap_or(0),
        "reload_error": reload_error,
        "link": link,
        "sub_uuid": sub_uuid,
    }))
    .into_response()
}

async fn client_update(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Json(request): Json<CreateClientRequest>,
) -> Response {
    if let Err(message) = validate_client_name(&request.name) {
        return (StatusCode::BAD_REQUEST, message).into_response();
    }
    if request.days < 0 || request.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be 0..3650").into_response();
    }
    let vk_hashes = match normalize_client_vk_hashes(&request.hash) {
        Ok(value) => value,
        Err(message) => return (StatusCode::BAD_REQUEST, message).into_response(),
    };
    if request.vk_token && !vk_hashes.is_empty() {
        return (StatusCode::BAD_REQUEST, "choose VK hashes or VK token").into_response();
    }
    let validated_workers = match validate_workers(request.workers) {
        Ok(w) => w,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };
    let validated_max_devices = match request.max_devices.map(validate_max_devices).transpose() {
        Ok(limit) => limit,
        Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
    };

    let revision = {
        let mut db = state.app.db.write().await;
        if request.vk_token && db.vk_access_token.is_empty() {
            return (StatusCode::BAD_REQUEST, "configure a VK token first").into_response();
        }
        if secret_eq(&password, &db.main_password) {
            // Список показывает владельцу глобальные хеши, если своих нет: их эхо не делает их «своими».
            if !(db.main_vk_hashes.is_empty() && vk_hashes == db.vk_hashes) {
                db.main_vk_hashes = vk_hashes;
            }
            db.main_vk_token_enabled = request.vk_token;
            db.main_workers = validated_workers;
            if let Some(limit) = validated_max_devices {
                db.main_max_devices = limit;
            }
            state.app.db_persistence.submit(db.clone())
        } else {
            let current_group_id = match db.passwords.get(&password) {
                Some(e) => e.group_id.clone(),
                None => return StatusCode::NOT_FOUND.into_response(),
            };
            let target_group_id = request.group_id.as_deref().unwrap_or(&current_group_id);
            let scope_names =
                model::existing_client_names_in_scope(&db, target_group_id, Some(&password));
            let client_name = if request.name.trim().is_empty() {
                model::generate_unique_client_name(&scope_names)
            } else {
                let trimmed = request.name.trim().to_string();
                if model::is_client_name_taken(&scope_names, &trimmed) {
                    return (
                        StatusCode::BAD_REQUEST,
                        "Участник с таким именем уже существует",
                    )
                        .into_response();
                }
                trimmed
            };

            let entry = db.passwords.get_mut(&password).unwrap();

            let default_peer_port = state.app.listen.port();
            let fallback_port = if entry.dtls_port == 0 {
                default_peer_port
            } else {
                entry.dtls_port
            };
            let peer_port = request.peer_port.unwrap_or(fallback_port);
            let local_port = request.local_port.unwrap_or(0);

            entry.name = client_name;
            entry.expires_at = if request.days == 0 {
                0
            } else {
                now().saturating_add(request.days.saturating_mul(86400))
            };
            entry.vk_hash = vk_hashes.clone();
            entry.vk_hashes = vk_hashes;
            entry.vk_token_enabled = request.vk_token;
            entry.dtls_port = peer_port;
            entry.wg_port = 0;
            entry.local_port = local_port;
            entry.ports = format!("{},0,{}", peer_port, local_port);
            if let Some(ref g_id) = request.group_id {
                entry.group_id = g_id.clone();
            }
            entry.workers = validated_workers;
            if let Some(limit) = validated_max_devices {
                entry.max_devices = limit;
            }
            state.app.db_persistence.submit(db.clone())
        }
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Err(message) = refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-mrtt-reload-error", warning)], "saved").into_response();
    }
    StatusCode::OK.into_response()
}

async fn client_toggle(State(state): State<WebState>, Path(password): Path<String>) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        let Some(entry) = db.passwords.get_mut(&password) else {
            return StatusCode::NOT_FOUND.into_response();
        };
        entry.is_deactivated = !entry.is_deactivated;
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }

    crate::protocol::drop_password_sessions(&state.app, &password);
    if let Err(message) = refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-mrtt-reload-error", warning)], "saved").into_response();
    }

    StatusCode::OK.into_response()
}

async fn client_unbind(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Query(query): Query<UnbindQuery>,
) -> Response {
    if let Err(response) = unbind_client_devices(&state, &password, query.device.trim()).await {
        return response;
    }
    StatusCode::OK.into_response()
}

async fn client_delete(State(state): State<WebState>, Path(password): Path<String>) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        if db.passwords.remove(&password).is_none() {
            return StatusCode::NOT_FOUND.into_response();
        };
        state.app.derived_keys.remove(&password);
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }

    crate::protocol::drop_password_sessions(&state.app, &password);
    if let Err(message) = refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-mrtt-reload-error", warning)], "saved").into_response();
    }

    StatusCode::OK.into_response()
}

async fn client_get(
    State(state): State<WebState>,
    headers: HeaderMap,
    Path(password): Path<String>,
) -> Response {
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;

    if secret_eq(&password, &db.main_password) {
        let mut session_up = 0;
        let mut session_down = 0;
        let mut active_sessions = 0;
        for entry in state.app.sessions.iter() {
            let sess = entry.value();
            if secret_eq(&sess.password, &db.main_password) {
                active_sessions += 1;
                session_up += sess.up_bytes.load(Ordering::Relaxed) as i64;
                session_down += sess.down_bytes.load(Ordering::Relaxed) as i64;
            }
        }

        let main_peer_port = state.app.listen.port();
        let info = ClientInfo {
            password: db.main_password.clone(),
            down: db.main_down_bytes + session_down,
            up: db.main_up_bytes + session_up,
            expires: 0,
            active: true,
            active_sessions,
            vk_hash: String::new(),
            ports: String::new(),
            ip: "10.66.67.2".to_string(),
            name: "Главный пароль".to_string(),
            vk_hashes: if db.main_vk_hashes.is_empty() {
                db.vk_hashes.clone()
            } else {
                db.main_vk_hashes.clone()
            },
            vk_token: db.main_vk_token_enabled,
            peer_port: main_peer_port,
            local_port: 0,
            group_id: String::new(),
            routing_rule_id: db.main_routing_rule_id.clone(),
            link: make_main_link(&host, main_peer_port, &db),
            sub_uuid: db.main_sub_uuid.clone(),
            workers: db.main_workers,
            max_devices: db.main_max_devices.unwrap_or(1),
            devices: device_infos(&state.app.sessions, &db.main_password, &db.main_devices),
        };
        return Json(info).into_response();
    }

    if let Some(entry) = db.passwords.get(&password) {
        let active_sessions = count_active_sessions(&state.app.sessions, &password);
        let client_peer_port = if entry.dtls_port == 0 {
            state.app.listen.port()
        } else {
            entry.dtls_port
        };
        let info = ClientInfo {
            password: password.clone(),
            down: entry.down_bytes,
            up: entry.up_bytes,
            expires: entry.expires_at,
            active: !entry.is_deactivated && !is_expired(entry),
            active_sessions,
            vk_hash: entry.vk_hash.clone(),
            ports: entry.ports.clone(),
            ip: if entry.ip.is_empty() {
                "10.66.67.3".to_string()
            } else {
                entry.ip.clone()
            },
            name: entry.name.clone(),
            vk_hashes: entry.vk_hashes.clone(),
            vk_token: entry.vk_token_enabled,
            peer_port: client_peer_port,
            local_port: entry.local_port,
            group_id: entry.group_id.clone(),
            routing_rule_id: entry.routing_rule_id.clone(),
            link: make_entry_link(
                &host,
                client_peer_port,
                &password,
                entry,
                &db.vk_access_token,
            ),
            sub_uuid: entry.sub_uuid.clone(),
            workers: entry.workers,
            max_devices: entry.max_devices.unwrap_or(1),
            devices: device_infos(&state.app.sessions, &password, &entry.devices),
        };
        return Json(info).into_response();
    }

    (StatusCode::NOT_FOUND, "client not found").into_response()
}

async fn client_reset_traffic(
    State(state): State<WebState>,
    Path(password): Path<String>,
) -> Response {
    let result = {
        let mut db = state.app.db.write().await;
        if secret_eq(&password, &db.main_password) {
            db.main_up_bytes = 0;
            db.main_down_bytes = 0;
            for s in state.app.sessions.iter() {
                if s.value().password == password {
                    s.value().up_bytes.store(0, Ordering::Relaxed);
                    s.value().down_bytes.store(0, Ordering::Relaxed);
                }
            }
            let revision = state.app.db_persistence.submit(db.clone());
            Some(revision)
        } else if let Some(entry) = db.passwords.get_mut(&password) {
            entry.up_bytes = 0;
            entry.down_bytes = 0;
            for s in state.app.sessions.iter() {
                if s.value().password == password {
                    s.value().up_bytes.store(0, Ordering::Relaxed);
                    s.value().down_bytes.store(0, Ordering::Relaxed);
                }
            }
            let revision = state.app.db_persistence.submit(db.clone());
            Some(revision)
        } else {
            None
        }
    };

    let Some(revision) = result else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };

    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    Json(serde_json::json!({ "ok": true })).into_response()
}

async fn client_extend(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Json(request): Json<ExtendClientRequest>,
) -> Response {
    if request.days <= 0 || request.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be 1..3650").into_response();
    }
    let current = now();
    let result = {
        let mut db = state.app.db.write().await;
        if let Some(entry) = db.passwords.get_mut(&password) {
            let base = if entry.expires_at > current {
                entry.expires_at
            } else {
                current
            };
            let new_expires = base.saturating_add(request.days.saturating_mul(86400));
            entry.expires_at = new_expires;
            let revision = state.app.db_persistence.submit(db.clone());
            Some((new_expires, revision))
        } else {
            None
        }
    };

    let Some((expires, revision)) = result else {
        return (StatusCode::NOT_FOUND, "client not found").into_response();
    };

    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = refresh_credentials_report(&state).await;
    Json(serde_json::json!({ "ok": true, "expires": expires })).into_response()
}

#[derive(Serialize)]
struct ClientLinkItem {
    name: String,
    password: String,
    link: String,
    expires: i64,
    peer_port: u16,
    group_id: String,
    #[serde(default)]
    sub_uuid: String,
}

async fn api_links_get(State(state): State<WebState>, headers: HeaderMap) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let host = resolve_client_host(&headers, &state).await;
    let mut list: Vec<ClientLinkItem> = db
        .passwords
        .iter()
        .map(|(password, entry)| {
            let client_peer_port = if entry.dtls_port == 0 {
                state.app.listen.port()
            } else {
                entry.dtls_port
            };
            ClientLinkItem {
                name: entry.name.clone(),
                password: password.clone(),
                link: make_entry_link(
                    &host,
                    client_peer_port,
                    password,
                    entry,
                    &db.vk_access_token,
                ),
                expires: entry.expires_at,
                peer_port: client_peer_port,
                group_id: entry.group_id.clone(),
                sub_uuid: entry.sub_uuid.clone(),
            }
        })
        .collect();
    if !db.main_password.is_empty() {
        let main_peer_port = state.app.listen.port();
        list.push(ClientLinkItem {
            name: "Владелец".to_owned(),
            password: db.main_password.clone(),
            link: make_main_link(&host, main_peer_port, &db),
            expires: 0,
            peer_port: main_peer_port,
            group_id: String::new(),
            sub_uuid: db.main_sub_uuid.clone(),
        });
    }
    Json(list)
}

#[derive(Deserialize)]
struct BatchVkRequest {
    #[serde(default)]
    vk_token: Option<String>,
    #[serde(default)]
    vk_hashes: Option<String>,
    #[serde(default)]
    apply_to_all_clients: Option<bool>,
}

async fn api_links_batch_vk(
    State(state): State<WebState>,
    Json(request): Json<BatchVkRequest>,
) -> Response {
    let token = match request.vk_token {
        Some(t) => match normalize_vk_access_token(&t) {
            Ok(v) => Some(v),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        None => None,
    };
    let hashes = match request.vk_hashes {
        Some(h) => match normalize_client_vk_hashes(&h) {
            Ok(v) => Some(v),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        None => None,
    };
    let apply_all = request.apply_to_all_clients.unwrap_or(true);
    let mut updated_count = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        if let Some(ref t) = token {
            db.vk_access_token = t.clone();
        }
        if let Some(ref h) = hashes {
            db.vk_hashes = h.clone();
        }
        if apply_all {
            if let Some(ref h) = hashes {
                db.main_vk_hashes = h.clone();
            }
            if let Some(ref t) = token {
                db.main_vk_token_enabled = !t.is_empty();
            }
            for entry in db.passwords.values_mut() {
                if let Some(ref h) = hashes {
                    entry.vk_hashes = h.clone();
                }
                if let Some(ref t) = token {
                    entry.vk_token_enabled = !t.is_empty();
                }
                updated_count += 1;
            }
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = refresh_credentials_report(&state).await;
    Json(serde_json::json!({
        "ok": true,
        "updated_clients": updated_count
    }))
    .into_response()
}

#[derive(Deserialize)]
struct ClientVkUpdateRequest {
    #[serde(default)]
    vk_token: Option<bool>,
    #[serde(default)]
    vk_hashes: Option<String>,
}

async fn api_client_vk_update(
    State(state): State<WebState>,
    Path(password): Path<String>,
    Json(request): Json<ClientVkUpdateRequest>,
) -> Response {
    let hashes = match request.vk_hashes {
        Some(h) => match normalize_client_vk_hashes(&h) {
            Ok(v) => Some(v),
            Err(e) => return (StatusCode::BAD_REQUEST, e).into_response(),
        },
        None => None,
    };
    let revision = {
        let mut db = state.app.db.write().await;
        if secret_eq(&password, &db.main_password) {
            if let Some(h) = hashes {
                db.main_vk_hashes = h;
            }
            if let Some(token_enabled) = request.vk_token {
                db.main_vk_token_enabled = token_enabled;
            }
            state.app.db_persistence.submit(db.clone())
        } else {
            let Some(entry) = db.passwords.get_mut(&password) else {
                return (StatusCode::NOT_FOUND, "client not found").into_response();
            };
            if let Some(h) = hashes {
                entry.vk_hashes = h;
            }
            if let Some(token_enabled) = request.vk_token {
                entry.vk_token_enabled = token_enabled;
            }
            state.app.db_persistence.submit(db.clone())
        }
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = refresh_credentials_report(&state).await;
    Json(serde_json::json!({ "ok": true })).into_response()
}

#[derive(Deserialize)]
struct ApiDnsRequest {
    #[serde(default)]
    dns_provider: Option<String>,
    #[serde(default)]
    dns_primary: Option<String>,
    #[serde(default)]
    dns_secondary: Option<String>,
}

async fn api_settings_dns(
    State(state): State<WebState>,
    Json(request): Json<ApiDnsRequest>,
) -> Response {
    let dns = match request.dns_provider.as_deref() {
        Some("custom") | None
            if request.dns_primary.is_some() || request.dns_secondary.is_some() =>
        {
            match manual_dns(
                request.dns_primary.as_deref(),
                request.dns_secondary.as_deref(),
            ) {
                Ok(dns) => (dns, "custom".to_owned()),
                Err(msg) => return (StatusCode::BAD_REQUEST, msg).into_response(),
            }
        }
        Some("custom") => {
            return (StatusCode::BAD_REQUEST, "primary DNS is required").into_response();
        }
        Some(provider_id) => {
            let Some(provider) = dns_provider_by_id(provider_id) else {
                return (StatusCode::BAD_REQUEST, "unknown DNS provider").into_response();
            };
            (provider_dns(provider), provider_id.to_owned())
        }
        None => return (StatusCode::BAD_REQUEST, "missing DNS parameters").into_response(),
    };
    let (revision, applied_dns) = {
        let mut db = state.app.db.write().await;
        let applied_dns = (db.dns != dns.0).then(|| dns.0.clone());
        db.dns = dns.0;
        db.dns_provider = dns.1;
        (state.app.db_persistence.submit(db.clone()), applied_dns)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Some(new_dns) = applied_dns {
        *state.app.dns.write().await = new_dns;
        let _ = crate::protocol::broadcast_tunnel_configuration(&state.app).await;
    }
    Json(serde_json::json!({ "ok": true })).into_response()
}

#[derive(Deserialize)]
struct RestartIntervalRequest {
    interval_hours: u8,
}

async fn api_settings_restart_interval(
    State(state): State<WebState>,
    Json(request): Json<RestartIntervalRequest>,
) -> Response {
    if !matches!(request.interval_hours, 0 | 12 | 24 | 48 | 72) {
        return (
            StatusCode::BAD_REQUEST,
            "auto restart interval must be 0, 12, 24, 48, or 72 hours",
        )
            .into_response();
    }
    let revision = {
        let mut db = state.app.db.write().await;
        db.set_auto_restart_interval_hours(request.interval_hours);
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    state
        .app
        .auto_restart_interval_tx
        .send_replace(request.interval_hours);
    Json(serde_json::json!({ "ok": true, "interval_hours": request.interval_hours }))
        .into_response()
}

#[derive(Deserialize)]
struct MasterPasswordRequest {
    password: String,
}

async fn api_settings_master_password(
    State(state): State<WebState>,
    Json(request): Json<MasterPasswordRequest>,
) -> Response {
    if !request.password.is_empty() && request.password.len() > 128 {
        return (
            StatusCode::BAD_REQUEST,
            "password length must be 1..128 or empty",
        )
            .into_response();
    }
    let (revision, retired_password) = {
        let mut db = state.app.db.write().await;
        if !request.password.is_empty() && db.passwords.contains_key(&request.password) {
            return (
                StatusCode::BAD_REQUEST,
                "Пароль совпадает с паролем клиента",
            )
                .into_response();
        }
        let retired = (db.main_password != request.password)
            .then(|| std::mem::replace(&mut db.main_password, request.password));
        (state.app.db_persistence.submit(db.clone()), retired)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Some(p) = retired_password
        && !p.is_empty()
    {
        state.app.derived_keys.remove(&p);
    }
    let _ = refresh_credentials_report(&state).await;
    Json(serde_json::json!({ "ok": true })).into_response()
}

#[derive(Serialize, Deserialize, Clone)]
struct ServerExportData {
    version: u32,
    exported_at: i64,
    main_password: String,
    dns: String,
    dns_provider: String,
    auto_restart_interval_hours: Option<u8>,
    vk_access_token: String,
    vk_hashes: String,
    groups: std::collections::BTreeMap<String, ClientGroup>,
    routing_rules: std::collections::BTreeMap<String, RoutingRule>,
    clients: Vec<ExportClientEntry>,
}

#[derive(Serialize, Deserialize, Clone)]
struct ExportClientEntry {
    password: String,
    name: String,
    expires_at: i64,
    down_bytes: i64,
    up_bytes: i64,
    #[serde(default)]
    vk_hash: String,
    #[serde(default)]
    vk_hashes: String,
    #[serde(default)]
    vk_token_enabled: bool,
    #[serde(default)]
    ports: String,
    #[serde(default)]
    dtls_port: u16,
    #[serde(default)]
    local_port: u16,
    #[serde(default)]
    group_id: String,
    #[serde(default)]
    routing_rule_id: String,
    #[serde(default)]
    is_deactivated: bool,
    #[serde(default)]
    sub_uuid: String,
    #[serde(default)]
    workers: Option<u32>,
    #[serde(default)]
    ip: String,
    #[serde(default)]
    max_devices: Option<u32>,
}

async fn api_export_get(State(state): State<WebState>) -> impl IntoResponse {
    let db = state.app.db.read().await;
    let clients: Vec<ExportClientEntry> = db
        .passwords
        .iter()
        .map(|(pwd, entry)| ExportClientEntry {
            password: pwd.clone(),
            name: entry.name.clone(),
            expires_at: entry.expires_at,
            down_bytes: entry.down_bytes,
            up_bytes: entry.up_bytes,
            vk_hash: entry.vk_hash.clone(),
            vk_hashes: entry.vk_hashes.clone(),
            vk_token_enabled: entry.vk_token_enabled,
            ports: entry.ports.clone(),
            dtls_port: entry.dtls_port,
            local_port: entry.local_port,
            group_id: entry.group_id.clone(),
            routing_rule_id: entry.routing_rule_id.clone(),
            is_deactivated: entry.is_deactivated,
            sub_uuid: entry.sub_uuid.clone(),
            workers: entry.workers,
            ip: entry.ip.clone(),
            max_devices: entry.max_devices,
        })
        .collect();
    let export = ServerExportData {
        version: 1,
        exported_at: now(),
        main_password: db.main_password.clone(),
        dns: db.dns.clone(),
        dns_provider: db.dns_provider.clone(),
        auto_restart_interval_hours: db.auto_restart_interval_hours,
        vk_access_token: db.vk_access_token.clone(),
        vk_hashes: db.vk_hashes.clone(),
        groups: db.groups.clone(),
        routing_rules: db.routing_rules.clone(),
        clients,
    };
    Json(export)
}

#[derive(Deserialize)]
struct ServerImportPayload {
    #[serde(default)]
    import_clients: Option<bool>,
    #[serde(default)]
    import_settings: Option<bool>,
    #[serde(default)]
    config: Option<ServerExportData>,
    #[serde(flatten)]
    direct_config: Option<ServerExportData>,
}

async fn api_import_post(
    State(state): State<WebState>,
    Json(payload): Json<ServerImportPayload>,
) -> Response {
    let export_opt = payload.config.or(payload.direct_config);
    let Some(export) = export_opt else {
        return (StatusCode::BAD_REQUEST, "invalid or empty config").into_response();
    };
    let do_clients = payload.import_clients.unwrap_or(true);
    let do_settings = payload.import_settings.unwrap_or(true);
    let mut added_clients = 0usize;
    let mut applied_dns = None;
    let mut applied_interval = None;

    let revision = {
        let mut db = state.app.db.write().await;
        if do_clients {
            for c in export.clients {
                if !db.passwords.contains_key(&c.password) {
                    let sub_uuid = if c.sub_uuid.trim().is_empty() {
                        model::random_uuid_v4()
                    } else {
                        c.sub_uuid
                    };
                    let ip = model::import_ip(&db, &c.ip);
                    let client_name = if c.name.trim().is_empty()
                        || model::is_client_name_taken(
                            model::existing_client_names_in_scope(&db, &c.group_id, None),
                            &c.name,
                        ) {
                        model::generate_unique_client_name(model::existing_client_names_in_scope(
                            &db,
                            &c.group_id,
                            None,
                        ))
                    } else {
                        c.name
                    };
                    db.passwords.insert(
                        c.password.clone(),
                        PasswordEntry {
                            ip,
                            expires_at: c.expires_at,
                            down_bytes: c.down_bytes,
                            up_bytes: c.up_bytes,
                            vk_hash: c.vk_hash,
                            ports: c.ports,
                            is_deactivated: c.is_deactivated,
                            name: client_name,
                            vk_hashes: c.vk_hashes,
                            vk_token_enabled: c.vk_token_enabled,
                            dtls_port: c.dtls_port,
                            wg_port: 0,
                            local_port: c.local_port,
                            group_id: c.group_id,
                            routing_rule_id: c.routing_rule_id,
                            sub_uuid,
                            workers: c.workers.filter(|&w| (3..=100).contains(&w)),
                            max_devices: c
                                .max_devices
                                .filter(|&limit| limit <= model::MAX_DEVICES_LIMIT),
                            // Привязки устройств не переносим: их IP выдавал другой сервер.
                            devices: Default::default(),
                        },
                    );
                    added_clients += 1;
                }
            }
            for (gid, g) in export.groups {
                db.groups.entry(gid).or_insert(g);
            }
            for (rid, r) in export.routing_rules {
                db.routing_rules.entry(rid).or_insert(r);
            }
        }
        if do_settings {
            if !export.main_password.is_empty() && !db.passwords.contains_key(&export.main_password)
            {
                db.main_password = export.main_password;
            }
            if !export.dns.is_empty() {
                applied_dns = (db.dns != export.dns).then(|| export.dns.clone());
                db.dns = export.dns;
                db.dns_provider = export.dns_provider;
            }
            if let Some(hours) = export.auto_restart_interval_hours {
                db.set_auto_restart_interval_hours(hours);
                applied_interval = Some(hours);
            }
            if !export.vk_access_token.is_empty() {
                db.vk_access_token = export.vk_access_token;
            }
            if !export.vk_hashes.is_empty() {
                db.vk_hashes = export.vk_hashes;
            }
        }
        state.app.db_persistence.submit(db.clone())
    };

    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if let Some(dns) = applied_dns {
        *state.app.dns.write().await = dns;
        let _ = crate::protocol::broadcast_tunnel_configuration(&state.app).await;
    }
    if let Some(hours) = applied_interval {
        state.app.auto_restart_interval_tx.send_replace(hours);
    }
    let _ = refresh_credentials_report(&state).await;

    Json(serde_json::json!({
        "ok": true,
        "added_clients": added_clients,
        "settings_imported": do_settings
    }))
    .into_response()
}

#[derive(Serialize)]
struct GroupInfo {
    id: String,
    name: String,
    routing_rule_id: String,
    admin_uuid: String,
    sub_uuid: String,
    member_count: usize,
    up: i64,
    down: i64,
    active_sessions: usize,
}

async fn groups_get(State(state): State<WebState>) -> Response {
    let db = state.app.db.read().await;
    let mut list = Vec::new();
    for group in db.groups.values() {
        let mut member_count = 0;
        let mut up = 0i64;
        let mut down = 0i64;
        let mut active_sessions = 0;
        for (_pass, p_entry) in &db.passwords {
            if p_entry.group_id == group.id {
                member_count += 1;
                up += p_entry.up_bytes;
                down += p_entry.down_bytes;
                active_sessions += count_active_sessions(&state.app.sessions, _pass);
            }
        }
        list.push(GroupInfo {
            id: group.id.clone(),
            name: group.name.clone(),
            routing_rule_id: group.routing_rule_id.clone(),
            admin_uuid: group.admin_uuid.clone(),
            sub_uuid: group.sub_uuid.clone(),
            member_count,
            up,
            down,
            active_sessions,
        });
    }
    Json(list).into_response()
}

#[derive(Deserialize)]
struct CreateGroupRequest {
    name: String,
}

async fn groups_create(
    State(state): State<WebState>,
    Json(request): Json<CreateGroupRequest>,
) -> Response {
    let name = request.name.trim();
    if name.is_empty() || name.len() > 64 {
        return (StatusCode::BAD_REQUEST, "invalid group name").into_response();
    }
    let id = format!("grp_{}", random_token(8));
    let group = ClientGroup {
        id: id.clone(),
        name: name.to_string(),
        routing_rule_id: String::new(),
        admin_uuid: String::new(),
        sub_uuid: model::random_uuid_v4(),
        workers: None,
        vk_token: None,
        vk_hashes: None,
    };
    let revision = {
        let mut db = state.app.db.write().await;
        db.groups.insert(id, group.clone());
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    Json(group).into_response()
}

#[derive(Deserialize)]
struct UpdateGroupRequest {
    name: Option<String>,
    routing_rule_id: Option<String>,
}

async fn groups_update(
    State(state): State<WebState>,
    Path(id): Path<String>,
    Json(request): Json<UpdateGroupRequest>,
) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        let Some(group) = db.groups.get_mut(&id) else {
            return StatusCode::NOT_FOUND.into_response();
        };
        if let Some(name) = request.name {
            let n = name.trim();
            if n.is_empty() || n.len() > 64 {
                return (StatusCode::BAD_REQUEST, "invalid group name").into_response();
            }
            group.name = n.to_string();
        }
        if let Some(r_id) = request.routing_rule_id {
            group.routing_rule_id = r_id;
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    let _ = crate::protocol::broadcast_tunnel_configuration(&state.app).await;
    StatusCode::OK.into_response()
}

async fn groups_delete(State(state): State<WebState>, Path(id): Path<String>) -> Response {
    let (revision, deleted_passwords) = {
        let mut db = state.app.db.write().await;
        if db.groups.remove(&id).is_none() {
            return StatusCode::NOT_FOUND.into_response();
        }
        let mut deleted = Vec::new();
        db.passwords.retain(|pwd, entry| {
            if entry.group_id == id {
                deleted.push(pwd.clone());
                false
            } else {
                true
            }
        });
        for pwd in &deleted {
            state.app.derived_keys.remove(pwd);
        }
        let rev = state.app.db_persistence.submit(db.clone());
        (rev, deleted)
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    for pwd in &deleted_passwords {
        crate::protocol::drop_password_sessions(&state.app, pwd);
    }
    if let Err(message) = refresh_credentials_report(&state).await {
        let warning = format!("saved but reload failed: {message}");
        return (StatusCode::OK, [("x-mrtt-reload-error", warning)], "saved").into_response();
    }
    StatusCode::OK.into_response()
}

#[derive(Deserialize)]
struct BatchToggleRequest {
    passwords: Vec<String>,
    active: bool,
}

async fn api_clients_batch_toggle(
    State(state): State<WebState>,
    Json(req): Json<BatchToggleRequest>,
) -> Response {
    let mut updated = 0usize;
    let mut passwords_to_disconnect = Vec::new();
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if let Some(entry) = db.passwords.get_mut(pwd) {
                entry.is_deactivated = !req.active;
                updated += 1;
                if !req.active {
                    passwords_to_disconnect.push(pwd.clone());
                }
            }
        }
        if updated > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    for pwd in &passwords_to_disconnect {
        crate::protocol::drop_password_sessions(&state.app, pwd);
    }
    if updated > 0 {
        let _ = refresh_credentials_report(&state).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "updated": updated })),
    )
        .into_response()
}

#[derive(Deserialize)]
struct BatchExtendRequest {
    passwords: Vec<String>,
    days: i64,
}

async fn api_clients_batch_extend(
    State(state): State<WebState>,
    Json(req): Json<BatchExtendRequest>,
) -> Response {
    if req.days <= 0 || req.days > 3650 {
        return (StatusCode::BAD_REQUEST, "days must be between 1 and 3650").into_response();
    }
    let add_secs = req.days.saturating_mul(86400);
    let current_time = now();
    let mut updated = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if let Some(entry) = db.passwords.get_mut(pwd) {
                let base = if entry.expires_at > current_time {
                    entry.expires_at
                } else {
                    current_time
                };
                entry.expires_at = base.saturating_add(add_secs);
                updated += 1;
            }
        }
        if updated > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "updated": updated })),
    )
        .into_response()
}

#[derive(Deserialize)]
struct BatchPasswordsRequest {
    passwords: Vec<String>,
}

async fn api_clients_batch_reset_traffic(
    State(state): State<WebState>,
    Json(req): Json<BatchPasswordsRequest>,
) -> Response {
    let mut updated = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if pwd == &db.main_password {
                db.main_down_bytes = 0;
                db.main_up_bytes = 0;
                updated += 1;
            } else if let Some(entry) = db.passwords.get_mut(pwd) {
                entry.down_bytes = 0;
                entry.up_bytes = 0;
                updated += 1;
            }
        }
        if updated > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    for pwd in &req.passwords {
        for s in state.app.sessions.iter() {
            if &s.password == pwd {
                s.down_bytes.store(0, Ordering::Relaxed);
                s.up_bytes.store(0, Ordering::Relaxed);
            }
        }
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "updated": updated })),
    )
        .into_response()
}

async fn api_clients_batch_delete(
    State(state): State<WebState>,
    Json(req): Json<BatchPasswordsRequest>,
) -> Response {
    let mut deleted = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        for pwd in &req.passwords {
            if db.passwords.remove(pwd).is_some() {
                state.app.derived_keys.remove(pwd);
                deleted += 1;
            }
        }
        if deleted > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    for pwd in &req.passwords {
        crate::protocol::drop_password_sessions(&state.app, pwd);
    }
    if deleted > 0 {
        let _ = refresh_credentials_report(&state).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "deleted": deleted })),
    )
        .into_response()
}

#[derive(Deserialize)]
struct BatchGroupAssignRequest {
    passwords: Vec<String>,
    group_id: String,
}

async fn api_clients_batch_group(
    State(state): State<WebState>,
    Json(req): Json<BatchGroupAssignRequest>,
) -> Response {
    let mut moved = 0usize;
    let revision = {
        let mut db = state.app.db.write().await;
        let gid = req.group_id.trim();
        if !gid.is_empty() && !db.groups.contains_key(gid) {
            return (StatusCode::BAD_REQUEST, "target group not found").into_response();
        }
        for pwd in &req.passwords {
            if let Some(entry) = db.passwords.get_mut(pwd) {
                entry.group_id = gid.to_string();
                moved += 1;
            }
        }
        if moved > 0 {
            Some(state.app.db_persistence.submit(db.clone()))
        } else {
            None
        }
    };
    if let Some(rev) = revision {
        let _ = wait_for_persistence(&state, rev).await;
    }
    (
        StatusCode::OK,
        Json(serde_json::json!({ "ok": true, "moved": moved })),
    )
        .into_response()
}

#[derive(Deserialize, Default)]
struct DpiDiagnosticsQuery {
    #[serde(default)]
    limit: Option<usize>,
}

async fn api_diagnostics_dpi(Query(query): Query<DpiDiagnosticsQuery>) -> impl IntoResponse {
    let limit = query.limit.unwrap_or(20).clamp(1, 100);
    let frames: Vec<protocol::DpiFrame> = protocol::DPI_RING
        .read()
        .map(|ring| {
            let count = limit.min(ring.len());
            ring.iter().skip(ring.len() - count).cloned().collect()
        })
        .unwrap_or_default();
    Json(serde_json::json!({
        "ok": true,
        "ring_len": protocol::dpi_ring_len(),
        "frames": frames,
    }))
}

#[derive(Serialize)]
struct RoutingRuleInfo {
    id: String,
    name: String,
    is_whitelist: bool,
    mode: String,
    packages: Vec<String>,
    template_domains: bool,
    direct_domains: Vec<String>,
    domains: Vec<String>,
    domains_is_whitelist: bool,
    domains_mode: String,
    ip_is_whitelist: bool,
    ip_mode: String,
    ips: Vec<String>,
    assigned_groups: Vec<String>,
    target_group_ids: Vec<String>,
    assigned_clients: Vec<String>,
    target_client_passwords: Vec<String>,
}

async fn routing_rules_get(State(state): State<WebState>) -> Response {
    let db = state.app.db.read().await;
    let mut list = Vec::new();
    for rule in db.routing_rules.values() {
        let mut assigned_groups = Vec::new();
        for group in db.groups.values() {
            if group.routing_rule_id == rule.id {
                assigned_groups.push(group.id.clone());
            }
        }
        let mut assigned_clients = Vec::new();
        if !db.main_password.is_empty() && db.main_routing_rule_id == rule.id {
            assigned_clients.push(db.main_password.clone());
        }
        for (pass, entry) in &db.passwords {
            if entry.routing_rule_id == rule.id {
                assigned_clients.push(pass.clone());
            }
        }
        list.push(RoutingRuleInfo {
            id: rule.id.clone(),
            name: rule.name.clone(),
            is_whitelist: rule.is_whitelist,
            mode: if rule.is_whitelist {
                "whitelist".to_string()
            } else {
                "blacklist".to_string()
            },
            packages: rule.packages.clone(),
            template_domains: rule.template_domains,
            direct_domains: rule.direct_domains.clone(),
            domains: rule.direct_domains.clone(),
            domains_is_whitelist: rule.domains_is_whitelist,
            domains_mode: if rule.domains_is_whitelist {
                "whitelist".to_string()
            } else {
                "blacklist".to_string()
            },
            ip_is_whitelist: rule.ip_is_whitelist,
            ip_mode: if rule.ip_is_whitelist {
                "whitelist".to_string()
            } else {
                "blacklist".to_string()
            },
            ips: rule.ips.clone(),
            assigned_groups: assigned_groups.clone(),
            target_group_ids: assigned_groups,
            assigned_clients: assigned_clients.clone(),
            target_client_passwords: assigned_clients,
        });
    }
    Json(list).into_response()
}

const TEMPLATE_DOMAINS_TXT: &str = include_str!("../app/src/main/assets/domains.txt");

async fn template_domains_get() -> impl IntoResponse {
    (
        [(
            axum::http::header::CONTENT_TYPE,
            "text/plain; charset=utf-8",
        )],
        TEMPLATE_DOMAINS_TXT,
    )
}

#[derive(Deserialize)]
struct CreateRoutingRuleRequest {
    #[serde(default)]
    name: String,
    #[serde(default)]
    is_whitelist: bool,
    #[serde(default)]
    mode: Option<String>,
    #[serde(default)]
    packages: Vec<String>,
    #[serde(default)]
    template_domains: bool,
    #[serde(default)]
    direct_domains: Vec<String>,
    #[serde(default)]
    domains: Option<Vec<String>>,
    #[serde(default)]
    domains_mode: Option<String>,
    #[serde(default)]
    domains_is_whitelist: bool,
    #[serde(default)]
    ip_mode: Option<String>,
    #[serde(default)]
    ip_is_whitelist: bool,
    #[serde(default)]
    ips: Vec<String>,
    #[serde(default)]
    assigned_groups: Vec<String>,
    #[serde(default)]
    target_group_ids: Option<Vec<String>>,
    #[serde(default)]
    assigned_clients: Vec<String>,
    #[serde(default)]
    target_client_passwords: Option<Vec<String>>,
}

async fn routing_rules_create(
    State(state): State<WebState>,
    Json(request): Json<CreateRoutingRuleRequest>,
) -> Response {
    let name_str = request.name.trim();
    let name = if name_str.is_empty() {
        "Правило".to_string()
    } else {
        name_str.chars().take(64).collect::<String>()
    };
    let is_whitelist = if let Some(ref m) = request.mode {
        m == "whitelist"
    } else {
        request.is_whitelist
    };
    let direct_domains = if !request.direct_domains.is_empty() {
        request.direct_domains
    } else {
        request.domains.unwrap_or_default()
    };
    let domains_is_whitelist = if let Some(ref m) = request.domains_mode {
        m == "whitelist"
    } else {
        request.domains_is_whitelist
    };
    let ip_is_whitelist = if let Some(ref m) = request.ip_mode {
        m == "whitelist"
    } else {
        request.ip_is_whitelist
    };
    let assigned_groups = if !request.assigned_groups.is_empty() {
        request.assigned_groups
    } else {
        request.target_group_ids.unwrap_or_default()
    };
    let assigned_clients = if !request.assigned_clients.is_empty() {
        request.assigned_clients
    } else {
        request.target_client_passwords.unwrap_or_default()
    };
    let id = format!("rule_{}", random_token(8));
    let rule = RoutingRule {
        id: id.clone(),
        name,
        is_whitelist,
        packages: request.packages,
        template_domains: request.template_domains,
        direct_domains,
        domains_is_whitelist,
        ip_is_whitelist,
        ips: request.ips,
    };
    let revision = {
        let mut db = state.app.db.write().await;
        for g_id in &assigned_groups {
            if let Some(g) = db.groups.get_mut(g_id) {
                g.routing_rule_id = id.clone();
            }
        }
        for c_pass in &assigned_clients {
            if let Some(p) = db.passwords.get_mut(c_pass) {
                p.routing_rule_id = id.clone();
            }
        }
        if !db.main_password.is_empty() && assigned_clients.contains(&db.main_password) {
            db.main_routing_rule_id = id.clone();
        }
        db.routing_rules.insert(id, rule.clone());
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    Json(rule).into_response()
}

async fn routing_rules_update(
    State(state): State<WebState>,
    Path(id): Path<String>,
    Json(request): Json<CreateRoutingRuleRequest>,
) -> Response {
    let name_str = request.name.trim();
    let name = if name_str.is_empty() {
        "Правило".to_string()
    } else {
        name_str.chars().take(64).collect::<String>()
    };
    let is_whitelist = if let Some(ref m) = request.mode {
        m == "whitelist"
    } else {
        request.is_whitelist
    };
    let direct_domains = if !request.direct_domains.is_empty() {
        request.direct_domains
    } else {
        request.domains.unwrap_or_default()
    };
    let domains_is_whitelist = if let Some(ref m) = request.domains_mode {
        m == "whitelist"
    } else {
        request.domains_is_whitelist
    };
    let ip_is_whitelist = if let Some(ref m) = request.ip_mode {
        m == "whitelist"
    } else {
        request.ip_is_whitelist
    };
    let assigned_groups = if !request.assigned_groups.is_empty() {
        request.assigned_groups
    } else {
        request.target_group_ids.unwrap_or_default()
    };
    let assigned_clients = if !request.assigned_clients.is_empty() {
        request.assigned_clients
    } else {
        request.target_client_passwords.unwrap_or_default()
    };
    let revision = {
        let mut db = state.app.db.write().await;
        let Some(rule) = db.routing_rules.get_mut(&id) else {
            return StatusCode::NOT_FOUND.into_response();
        };
        rule.name = name;
        rule.is_whitelist = is_whitelist;
        rule.packages = request.packages;
        rule.template_domains = request.template_domains;
        rule.direct_domains = direct_domains;
        rule.domains_is_whitelist = domains_is_whitelist;
        rule.ip_is_whitelist = ip_is_whitelist;
        rule.ips = request.ips;

        for group in db.groups.values_mut() {
            if assigned_groups.contains(&group.id) {
                group.routing_rule_id = id.clone();
            } else if group.routing_rule_id == id {
                group.routing_rule_id.clear();
            }
        }
        for (pass, entry) in db.passwords.iter_mut() {
            if assigned_clients.contains(pass) {
                entry.routing_rule_id = id.clone();
            } else if entry.routing_rule_id == id {
                entry.routing_rule_id.clear();
            }
        }
        if !db.main_password.is_empty() {
            if assigned_clients.contains(&db.main_password) {
                db.main_routing_rule_id = id.clone();
            } else if db.main_routing_rule_id == id {
                db.main_routing_rule_id.clear();
            }
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    StatusCode::OK.into_response()
}

async fn routing_rules_delete(State(state): State<WebState>, Path(id): Path<String>) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        if db.routing_rules.remove(&id).is_none() {
            return StatusCode::NOT_FOUND.into_response();
        }
        for group in db.groups.values_mut() {
            if group.routing_rule_id == id {
                group.routing_rule_id.clear();
            }
        }
        for entry in db.passwords.values_mut() {
            if entry.routing_rule_id == id {
                entry.routing_rule_id.clear();
            }
        }
        if db.main_routing_rule_id == id {
            db.main_routing_rule_id.clear();
        }
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    StatusCode::OK.into_response()
}

async fn reboot(State(state): State<WebState>) -> Response {
    if state.app.restart_pending.swap(true, Ordering::AcqRel) {
        return (
            StatusCode::ACCEPTED,
            [
                (header::RETRY_AFTER, "3"),
                (header::CACHE_CONTROL, "no-store"),
            ],
        )
            .into_response();
    }

    match crate::request_managed_restart(&state.app, "panel") {
        Ok(()) => (
            StatusCode::ACCEPTED,
            [
                (header::RETRY_AFTER, "3"),
                (header::CACHE_CONTROL, "no-store"),
            ],
        )
            .into_response(),
        Err(error) => {
            state.app.restart_pending.store(false, Ordering::Release);
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                format!("restart failed: {error:#}"),
            )
                .into_response()
        }
    }
}

#[derive(Deserialize)]
struct LogsToggleRequest {
    active: bool,
}

async fn logs_get(State(state): State<WebState>) -> impl IntoResponse {
    let lines = lock_unpoison(&state.app.logs)
        .iter()
        .cloned()
        .collect::<Vec<String>>();
    Json(serde_json::json!({
        "path": state.app.log_file_path.to_string_lossy(),
        "active": state.app.logging_active.load(Ordering::Relaxed),
        "lines": lines
    }))
}

async fn logs_toggle(
    State(state): State<WebState>,
    Json(request): Json<LogsToggleRequest>,
) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        db.logging_active = Some(request.active);
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    state
        .app
        .logging_active
        .store(request.active, Ordering::Relaxed);

    StatusCode::OK.into_response()
}

async fn logs_clear(State(state): State<WebState>) -> Response {
    lock_unpoison(&state.app.logs).clear();

    let path = state.app.log_file_path.clone();
    tokio::spawn(async move {
        let _ = tokio::fs::remove_file(&path).await;
    });

    StatusCode::OK.into_response()
}

#[derive(Deserialize)]
struct ProfileRequest {
    #[serde(default)]
    name: String,
    port: u16,
    #[serde(default)]
    username: String,
    #[serde(default)]
    password: String,
}

async fn local_proxy_get(State(state): State<WebState>) -> impl IntoResponse {
    let (proxy_state, active, tcp_sessions, udp_flows) = {
        let db = state.app.db.read().await;
        let route = state.app.proxy_route.read().await;
        let active = route.as_ref().is_some_and(|r| r.is_alive());
        let (tcp_sessions, udp_flows) =
            route.as_ref().map(|r| r.stats_snapshot()).unwrap_or((0, 0));
        (db.local_proxy.clone(), active, tcp_sessions, udp_flows)
    };
    let profiles: Vec<serde_json::Value> = proxy_state
        .profiles
        .iter()
        .map(|p| {
            serde_json::json!({
                "id": p.id,
                "name": p.name,
                "port": p.port,
                "username": p.username,
                "password": p.password,
            })
        })
        .collect();
    Json(serde_json::json!({
        "active_profile_id": proxy_state.active_profile_id,
        "profiles": profiles,
        "route_active": active,
        "port_listening": state.app.proxy_port_listening.load(std::sync::atomic::Ordering::Acquire),
        "health_error": crate::read_unpoison(&state.app.proxy_health_error).clone(),
        "tcp_sessions": tcp_sessions,
        "udp_flows": udp_flows,
    }))
}

async fn local_proxy_create(
    State(state): State<WebState>,
    Json(request): Json<ProfileRequest>,
) -> Response {
    if let Err(message) = validate_proxy_profile_name(&request.name) {
        return (StatusCode::BAD_REQUEST, message).into_response();
    }
    let port = if request.port == 0 {
        crate::model::DEFAULT_LOCAL_PROXY_PORT
    } else {
        request.port
    };
    let profile = crate::model::LocalProxyProfile {
        id: crate::model::LocalProxyProfile::new_id(),
        name: if request.name.is_empty() {
            format!("SOCKS5 :{port}")
        } else {
            request.name
        },
        port,
        username: request.username,
        password: request.password,
    };
    if let Err(error) = crate::proxy_route::validate_config(&profile) {
        return (StatusCode::BAD_REQUEST, error.to_string()).into_response();
    }
    let id = profile.id.clone();
    let revision = {
        let mut db = state.app.db.write().await;
        if db.local_proxy.profiles.len() >= 20 {
            return (StatusCode::BAD_REQUEST, "Too many profiles (max 20)").into_response();
        }
        db.local_proxy.profiles.push(profile);
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    log_event(
        &state.app,
        "INFO",
        "PROXY",
        &format!("SOCKS5 profile created: {id}"),
    );
    let port_listening = crate::proxy_route::port_is_listening(port).await;
    Json(serde_json::json!({ "id": id, "port_listening": port_listening })).into_response()
}

async fn local_proxy_update(
    State(state): State<WebState>,
    Path(id): Path<String>,
    Json(request): Json<ProfileRequest>,
) -> Response {
    if let Err(message) = validate_proxy_profile_name(&request.name) {
        return (StatusCode::BAD_REQUEST, message).into_response();
    }
    let port = if request.port == 0 {
        crate::model::DEFAULT_LOCAL_PROXY_PORT
    } else {
        request.port
    };
    let temp_profile = crate::model::LocalProxyProfile {
        id: id.clone(),
        name: request.name.clone(),
        port,
        username: request.username.clone(),
        password: request.password.clone(),
    };
    if let Err(error) = crate::proxy_route::validate_config(&temp_profile) {
        return (StatusCode::BAD_REQUEST, error.to_string()).into_response();
    }
    let (was_active, revision) = {
        let mut db = state.app.db.write().await;
        let was_active = db.local_proxy.active_profile_id == id;
        let Some(profile) = db.local_proxy.find_profile_mut(&id) else {
            return (StatusCode::NOT_FOUND, "Profile not found").into_response();
        };
        if !request.name.is_empty() {
            profile.name = request.name;
        }
        profile.port = port;
        profile.username = request.username;
        profile.password = request.password;
        (was_active, state.app.db_persistence.submit(db.clone()))
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if was_active {
        state.app.proxy_trigger.notify_one();
    }
    log_event(
        &state.app,
        "INFO",
        "PROXY",
        &format!("SOCKS5 profile updated: {id}"),
    );
    let port_listening = crate::proxy_route::port_is_listening(port).await;
    Json(serde_json::json!({ "updated": true, "port_listening": port_listening })).into_response()
}

async fn local_proxy_delete(State(state): State<WebState>, Path(id): Path<String>) -> Response {
    let (was_active, revision) = {
        let mut db = state.app.db.write().await;
        let was_active = db.local_proxy.active_profile_id == id;
        if !db.local_proxy.remove_profile(&id) {
            return (StatusCode::NOT_FOUND, "Profile not found").into_response();
        }
        (was_active, state.app.db_persistence.submit(db.clone()))
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    if was_active {
        state.app.proxy_trigger.notify_one();
    }
    log_event(
        &state.app,
        "INFO",
        "PROXY",
        &format!("SOCKS5 profile deleted: {id}"),
    );
    Json(serde_json::json!({ "deleted": true })).into_response()
}

async fn wait_proxy_route_applied(state: &WebState, want_active: bool) -> Result<(), String> {
    tokio::time::sleep(Duration::from_millis(250)).await;
    let deadline = std::time::Instant::now() + Duration::from_secs(8);
    loop {
        let route_active = state.app.proxy_route.read().await.is_some();
        let health_error = read_unpoison(&state.app.proxy_health_error).clone();
        if want_active {
            if route_active && health_error.is_none() {
                return Ok(());
            }
            if let Some(error) = health_error {
                return Err(error);
            }
        } else if !route_active {
            return Ok(());
        }
        if std::time::Instant::now() >= deadline {
            return Err("route is still applying; check status in a few seconds".to_owned());
        }
        tokio::time::sleep(Duration::from_millis(150)).await;
    }
}

async fn local_proxy_activate(State(state): State<WebState>, Path(id): Path<String>) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        if db.local_proxy.find_profile(&id).is_none() {
            return (StatusCode::NOT_FOUND, "Profile not found").into_response();
        }
        db.local_proxy.active_profile_id = id.clone();
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    log_event(
        &state.app,
        "INFO",
        "PROXY",
        "Local SOCKS5 settings saved; applying TPROXY route",
    );
    state.app.proxy_trigger.notify_one();
    match wait_proxy_route_applied(&state, true).await {
        Ok(()) => Json(serde_json::json!({ "activated": true })).into_response(),
        Err(error) => (StatusCode::BAD_GATEWAY, error).into_response(),
    }
}

async fn local_proxy_deactivate(State(state): State<WebState>) -> Response {
    let revision = {
        let mut db = state.app.db.write().await;
        db.local_proxy.active_profile_id.clear();
        state.app.db_persistence.submit(db.clone())
    };
    if let Err(response) = wait_for_persistence(&state, revision).await {
        return response;
    }
    log_event(
        &state.app,
        "INFO",
        "PROXY",
        "Local SOCKS5 routing disabled; restoring direct route",
    );
    state.app.proxy_trigger.notify_one();
    match wait_proxy_route_applied(&state, false).await {
        Ok(()) => Json(serde_json::json!({ "deactivated": true })).into_response(),
        Err(error) => (StatusCode::BAD_GATEWAY, error).into_response(),
    }
}

async fn logs_download(State(state): State<WebState>) -> impl IntoResponse {
    let path = state.app.log_file_path.clone();
    if let Ok(file) = tokio::fs::File::open(&path).await {
        let body = axum::body::Body::from_stream(tokio_util::io::ReaderStream::new(file));
        let headers = [
            (
                axum::http::header::CONTENT_TYPE,
                "text/plain; charset=utf-8",
            ),
            (
                axum::http::header::CONTENT_DISPOSITION,
                "attachment; filename=\"mrtt.log\"",
            ),
        ];
        (headers, body).into_response()
    } else {
        (axum::http::StatusCode::NOT_FOUND, "Log file not found").into_response()
    }
}

/// API управления. Слушает только 127.0.0.1: приложение ходит сюда через SSH-туннель,
/// поэтому наружу порт не открыт, а TLS и логин не нужны.
pub async fn run(app: Arc<App>) -> anyhow::Result<()> {
    let state = WebState { app: app.clone() };
    let router = Router::new()
        .route("/api/stats", get(stats))
        .route("/api/memory/trim", post(memory_trim))
        .route("/api/settings", get(settings_get).post(settings_post))
        .route("/api/clients", get(clients_get).post(clients_create))
        .route(
            "/api/clients/{password}",
            get(client_get).post(client_update).delete(client_delete),
        )
        .route("/api/clients/{password}/toggle", post(client_toggle))
        .route("/api/clients/{password}/unbind", post(client_unbind))
        .route(
            "/api/clients/{password}/reset-traffic",
            post(client_reset_traffic),
        )
        .route("/api/clients/{password}/extend", post(client_extend))
        .route("/api/clients/{password}/vk", post(api_client_vk_update))
        .route("/api/groups", get(groups_get).post(groups_create))
        .route("/api/groups/{id}", put(groups_update).delete(groups_delete))
        .route("/api/groups/{id}/clients", get(group_clients_get))
        .route(
            "/api/routing-rules",
            get(routing_rules_get).post(routing_rules_create),
        )
        .route(
            "/api/routing-rules/{id}",
            put(routing_rules_update).delete(routing_rules_delete),
        )
        .route("/api/template-domains", get(template_domains_get))
        .route("/api/logs", get(logs_get))
        .route("/api/logs/toggle", post(logs_toggle))
        .route("/api/logs/clear", post(logs_clear))
        .route("/api/logs/download", get(logs_download))
        .route(
            "/api/local-proxy",
            get(local_proxy_get).post(local_proxy_create),
        )
        .route(
            "/api/local-proxy/profiles/{id}",
            put(local_proxy_update).delete(local_proxy_delete),
        )
        .route("/api/local-proxy/activate/{id}", post(local_proxy_activate))
        .route("/api/local-proxy/deactivate", post(local_proxy_deactivate))
        .route("/api/links", get(api_links_get))
        .route("/api/links/batch-vk", post(api_links_batch_vk))
        .route("/api/settings/dns", post(api_settings_dns))
        .route(
            "/api/settings/restart-interval",
            post(api_settings_restart_interval),
        )
        .route(
            "/api/settings/master-password",
            post(api_settings_master_password),
        )
        .route("/api/clients/batch-toggle", post(api_clients_batch_toggle))
        .route("/api/clients/batch-extend", post(api_clients_batch_extend))
        .route(
            "/api/clients/batch-reset-traffic",
            post(api_clients_batch_reset_traffic),
        )
        .route("/api/clients/batch-delete", post(api_clients_batch_delete))
        .route("/api/clients/batch-group", post(api_clients_batch_group))
        .route("/api/diagnostics/dpi", get(api_diagnostics_dpi))
        .route("/api/export", get(api_export_get))
        .route("/api/import", post(api_import_post))
        .route("/api/reboot", post(reboot))
        .layer(DefaultBodyLimit::max(WEB_BODY_LIMIT_BYTES))
        .with_state(state);

    let address = SocketAddr::from(([127, 0, 0, 1], app.web_port));
    let listener = tokio::net::TcpListener::bind(address).await?;
    println!("[WEB] management API listening on {address}");
    axum::serve(listener, router).await?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{
        DNS_PROVIDERS, MAX_CLIENT_NAME_BYTES, count_active_sessions, decrypt_mrtt_link,
        deploy_dns_profile_or_yandex, dns_provider_by_id, dns_provider_id, encrypt_mrtt_link,
        make_client_link, manual_dns, normalize_client_vk_hashes, normalize_vk_access_token,
        process_memory, process_memory_rollup, provider_dns, validate_client_name,
        validate_proxy_profile_name, validate_workers,
    };

    #[test]
    fn dns_provider_catalog_is_complete_and_has_exact_yandex_default() {
        assert_eq!(DNS_PROVIDERS.len(), 13);
        let yandex = dns_provider_by_id("yandex").unwrap();
        assert_eq!(provider_dns(yandex), "77.88.8.8,77.88.8.1");
        assert_eq!(dns_provider_id("1.1.1.1,1.0.0.1"), Some("cloudflare"));
        assert_eq!(
            dns_provider_id("45.90.28.181,45.90.30.181"),
            Some("nextdns")
        );
        assert_eq!(dns_provider_id("195.208.6.1,195.208.7.1"), Some("bizone"));
        assert_eq!(dns_provider_id("195.208.4.1,195.208.5.1"), Some("nsdi"));
        assert_eq!(dns_provider_id("10.0.0.1"), None);
    }

    #[test]
    fn legacy_deploy_dns_migration_normalizes_known_partial_profiles_or_uses_yandex() {
        assert_eq!(
            deploy_dns_profile_or_yandex("111.88.96.55,203.0.113.7"),
            "111.88.96.54,111.88.96.55"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("111.88.96.51,203.0.113.7"),
            "111.88.96.54,111.88.96.55"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("95.182.120.241,203.0.113.7"),
            "193.233.112.67,193.233.112.88"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("8.8.8.8,1.1.1.1"),
            "8.8.8.8,8.8.4.4"
        );
        assert_eq!(
            deploy_dns_profile_or_yandex("203.0.113.7,198.51.100.9"),
            "77.88.8.8,77.88.8.1"
        );
    }

    #[test]
    fn manual_dns_accepts_one_or_two_ipv4_addresses() {
        assert_eq!(manual_dns(Some("1.1.1.1"), None).unwrap(), "1.1.1.1");
        assert_eq!(
            manual_dns(Some(" 1.1.1.1 "), Some(" 1.0.0.1 ")).unwrap(),
            "1.1.1.1,1.0.0.1"
        );
    }

    #[test]
    fn manual_dns_rejects_empty_primary_and_invalid_ipv4() {
        assert!(manual_dns(None, None).is_err());
        assert!(manual_dns(Some(""), None).is_err());
        assert!(manual_dns(Some("1.1.1.256"), None).is_err());
        assert!(manual_dns(Some("1.1.1.1"), Some("not-an-ip")).is_err());
    }

    #[test]
    fn persisted_text_fields_have_byte_and_control_character_limits() {
        assert!(validate_client_name("normal client").is_ok());
        assert!(validate_client_name(&"x".repeat(MAX_CLIENT_NAME_BYTES)).is_ok());
        assert!(validate_client_name(&"x".repeat(MAX_CLIENT_NAME_BYTES + 1)).is_err());
        assert!(validate_client_name("bad\nname").is_err());
        assert!(validate_proxy_profile_name(&"x".repeat(64)).is_ok());
        assert!(validate_proxy_profile_name(&"x".repeat(65)).is_err());
    }

    #[test]
    fn client_vk_hashes_accept_empty_or_one_to_six_values() {
        assert_eq!(normalize_client_vk_hashes("").unwrap(), "");
        for count in 1..=6 {
            let hashes = (1..=count)
                .map(|index| format!("abcdefghijklmnop{index}"))
                .collect::<Vec<_>>()
                .join(",");
            assert_eq!(normalize_client_vk_hashes(&hashes).unwrap(), hashes);
        }
    }

    #[test]
    fn client_vk_hashes_reject_spaces_short_values_and_more_than_six() {
        assert!(normalize_client_vk_hashes("abcdefghijklmnop, abcdefghijklmnop").is_err());
        assert!(normalize_client_vk_hashes("short").is_err());
        let seven = (1..=7)
            .map(|index| format!("abcdefghijklmnop{index}"))
            .collect::<Vec<_>>()
            .join(",");
        assert!(normalize_client_vk_hashes(&seven).is_err());
    }

    #[test]
    fn vk_token_accepts_one_compact_value_or_empty() {
        assert_eq!(
            normalize_vk_access_token("  vk1.token-_%2B  ").unwrap(),
            "vk1.token-_%2B"
        );
        assert_eq!(normalize_vk_access_token(" ").unwrap(), "");
        assert!(normalize_vk_access_token("token with space").is_err());
        assert!(normalize_vk_access_token(&"x".repeat(4097)).is_err());
    }

    #[test]
    fn process_memory_reads_linux_status_fields() {
        let memory = process_memory(
            "VmHWM:\t850000 kB\nVmRSS:\t400000 kB\nRssAnon:\t350000 kB\nRssFile:\t45000 kB\nRssShmem:\t5000 kB\nVmSwap:\t12000 kB\n",
        );
        assert_eq!(memory.rss, 400000);
        assert_eq!(memory.peak, 850000);
        assert_eq!(memory.anonymous, 350000);
        assert_eq!(memory.file, 45000);
        assert_eq!(memory.shared, 5000);
        assert_eq!(memory.swap, 12000);
    }

    #[test]
    fn process_memory_rollup_reads_attribution_fields() {
        let memory = process_memory_rollup(
            "Pss:\t700 kB\nPss_Anon:\t650 kB\nPss_File:\t40 kB\nPss_Shmem:\t10 kB\nAnonymous:\t680 kB\nShared_Clean:\t12 kB\nShared_Dirty:\t3 kB\nPrivate_Clean:\t20 kB\nPrivate_Dirty:\t640 kB\nSwapPss:\t7 kB\n",
        );
        assert_eq!(memory.pss, 700);
        assert_eq!(memory.pss_anon, 650);
        assert_eq!(memory.anonymous, 680);
        assert_eq!(memory.file, 40);
        assert_eq!(memory.shmem, 10);
        assert_eq!(memory.private, 660);
        assert_eq!(memory.shared, 15);
        assert_eq!(memory.swap_pss, 7);
    }

    #[test]
    fn mrtt_link_encryption_and_decryption() {
        let payload = "connect?v=2&host=1.2.3.4&peer=46000&password=testpass";
        let encrypted1 = encrypt_mrtt_link(payload);
        let encrypted2 = encrypt_mrtt_link(payload);
        assert!(encrypted1.starts_with("mrtt://"));
        assert_eq!(
            encrypted1, encrypted2,
            "link encryption must be deterministic for identical payload"
        );
        let decrypted = decrypt_mrtt_link(&encrypted1).expect("decryption should succeed");
        assert_eq!(decrypted, payload);
        let foreign_scheme = encrypted1.replacen("mrtt://", "other://", 1);
        assert!(
            decrypt_mrtt_link(&foreign_scheme).is_none(),
            "only mrtt:// links are accepted"
        );
    }

    #[test]
    fn mrtt_link_legacy_plaintext_rejected() {
        use base64::Engine;
        let payload = "connect?v=2&host=1.2.3.4&peer=46000&password=legacy";
        let legacy_b64 =
            base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(payload.as_bytes());
        let legacy_link = format!("mrtt://{legacy_b64}");
        assert!(decrypt_mrtt_link(&legacy_link).is_none());
    }

    #[test]
    fn make_client_link_contains_password_and_aes_roundtrip() {
        let link = make_client_link("1.2.3.4", 46000, "abc1234567890123", "", false, "");
        assert!(link.starts_with("mrtt://"));
        let decrypted = decrypt_mrtt_link(&link).expect("must decrypt");
        assert!(decrypted.contains("password=abc1234567890123"));
        assert!(!decrypted.contains("client="));
        assert!(!decrypted.contains("&workers="));
    }

    #[test]
    fn owner_link_contains_owner_password_without_workers() {
        let link = make_client_link("1.2.3.4", 46000, "ownerpass1234567", "", false, "");
        let decrypted = decrypt_mrtt_link(&link).expect("must decrypt");
        assert!(decrypted.contains("password=ownerpass1234567"));
        assert!(!decrypted.contains("client="));
        assert!(!decrypted.contains("&workers="));
    }

    #[test]
    fn count_active_sessions_isolates_passwords() {
        use crate::protocol::Session;
        use dashmap::DashMap;
        use std::net::SocketAddr;
        use std::sync::Arc;
        use std::sync::atomic::AtomicBool;

        let sessions = DashMap::new();
        let debug_flag = Arc::new(AtomicBool::new(false));
        let addr: SocketAddr = "127.0.0.1:46001".parse().unwrap();

        let s1 = Session::new(
            1,
            addr,
            "pass_a",
            1,
            "salt1",
            false,
            debug_flag.clone(),
            100,
        );
        sessions.insert(1, s1);

        assert_eq!(count_active_sessions(&sessions, "pass_a"), 1);
        assert_eq!(count_active_sessions(&sessions, "pass_b"), 0);

        let s_owner = Session::new(2, addr, "owner_pass", 2, "salt2", false, debug_flag, 100);
        sessions.insert(2, s_owner);

        assert_eq!(count_active_sessions(&sessions, "owner_pass"), 1);
        assert_eq!(count_active_sessions(&sessions, "pass_a"), 1);
        assert_eq!(count_active_sessions(&sessions, "pass_b"), 0);
    }

    #[test]
    fn workers_validation() {
        assert_eq!(validate_workers(None), Ok(None));
        assert_eq!(validate_workers(Some(0)), Ok(None));
        assert_eq!(validate_workers(Some(3)), Ok(Some(3)));
        assert_eq!(validate_workers(Some(36)), Ok(Some(36)));
        assert_eq!(validate_workers(Some(99)), Ok(Some(99)));
        assert_eq!(validate_workers(Some(100)), Ok(Some(100)));
        assert!(validate_workers(Some(1)).is_err());
        assert!(validate_workers(Some(2)).is_err());
        assert!(validate_workers(Some(101)).is_err());
    }
}
