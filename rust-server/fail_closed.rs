//! Fail-closed egress decision for the MRTT server (research PoC).
//!
//! Owner policy: **NO DIRECT** — client traffic arriving over TURN must never
//! egress through the VPS default route. Today `local_proxy_monitor_loop`
//! (main.rs) deactivates the TPROXY route when the local SOCKS5 stops
//! listening and lets clients fall back to direct egress ("clients switched
//! to the direct route", main.rs:1482; also main.rs:1452, 1522, 1576 and
//! web_panel.rs:3562). This module replaces that fallback with an explicit
//! **blackhole** decision.
//!
//! The type system is the policy: `Egress` has no `Direct` variant, so no
//! caller can express "restore the direct route" while fail-closed mode is on.
//!
//! The logic is pure (no tokio/libc/iptables) so it can be unit-tested
//! anywhere; the monitor loop is expected to execute the returned `ip rule`
//! plan the same way `exit_routes_loop` executes `exit_routes::plan`.
//!
//! # Audit v2 (branch `research/fail-closed-audit-fixes`)
//!
//! Re-review of the first PoC (Draft PR #1) found two defects here; both are
//! fixed on this branch with negative tests:
//!
//! 1. **A block rule without its blackhole route was treated as done.**
//!    `plan(client_rule_installed = true, blackhole_installed = false, Egress::Block)`
//!    returned an empty plan, but a rule that looks up an empty table makes
//!    the kernel fall through to the next policy rule — ultimately the main
//!    table, i.e. direct egress while the operator believes clients are
//!    blocked. `plan` now repairs the missing route whether or not the rule
//!    is already in place.
//! 2. **Premature unlock before TPROXY readiness.** The decision keyed on
//!    `port_is_listening()` alone, so the block rule was removed as soon as
//!    the SOCKS5 port answered a TCP probe — while the TPROXY data path
//!    (tproxy child spawn retries, rule activation) was not active yet: a
//!    direct-egress window. `EgressState` gains `socks_route_ready`; both
//!    `decide` and `plan` refuse the unlock without it.
//!
//! # Audit v3 (final pass, same branch)
//!
//! 3. **`block_rule_installed` matched any rule at pref 5290 / table 5291.**
//!    A rule installed for a *different* client made the parser report the
//!    block as installed for this one, so `plan` skipped the `rule add` and
//!    the client silently fell through to the main table — the same
//!    direct-egress failure as audit fix 1, but per client. The parser now
//!    requires exact canonical correspondence of the `from` selector
//!    (family-aware, host bits masked, bare host IP == `/32` or `/128`,
//!    `all` matches only `all`); errors, garbage and truncated lines parse
//!    as NOT installed so the plan re-installs (fail-closed direction).
//!
//! IPv6 applicability: the parsers accept `ip -6 rule show` and
//! `ip -6 route show table 5291` output unchanged (same text format, pinned
//! by tests). The `plan` fragments are family-agnostic; the executor must
//! run them as `ip -6 …` for IPv6 client matches. Table 5291 is
//! family-scoped in the kernel, so both families can share the number, but
//! rule/route state and `socks_route_ready` must be tracked per family, and
//! iproute2 defaults to IPv4 when neither `-4` nor `-6` is given — the
//! integration must query both families explicitly.
//!
//! Integration sketch (not part of this PoC branch):
//!   1. `mod fail_closed;` in main.rs.
//!   2. In `local_proxy_monitor_loop`, replace the two "direct" branches with
//!      `fail_closed::decide(...)` + command execution.
//!   3. Add `no_direct: bool` to the server config (default true) and thread
//!      it through `App`.

/// Policy-routing preference for the blackhole rule. Sits just above the
/// exit-server rules (`exit_routes::RULE_PREF` = 5300) so that a blackhole
/// wins over the main table, and below the proxy fwmark rules so that an
/// active, healthy SOCKS route is unaffected.
pub const BLOCK_RULE_PREF: &str = "5290";

/// Routing table reserved for the blackhole default route. Kept out of the
/// range used by cascade exit tables (2001+ in `model.rs`) and the proxy
/// mark tables. Per-family in the kernel: IPv4 and IPv6 rules/routes in
/// table 5291 are independent state.
pub const BLOCK_TABLE: u32 = 5291;

/// Where client traffic goes. Deliberately no `Direct` variant.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Egress {
    /// TPROXY route through the local SOCKS5 is up and verified ready.
    Socks,
    /// Local SOCKS5 unavailable (or TPROXY not ready): drop client traffic
    /// (blackhole), never direct.
    Block,
}

/// Server-side fail-closed configuration.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct FailClosedPolicy {
    /// `true` (target default): NO DIRECT enforcement. `false`: legacy
    /// behavior where the monitor may leave clients on the default route.
    pub enforce_no_direct: bool,
}

/// Snapshot of the monitor loop inputs.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EgressState {
    /// A local SOCKS5 profile is configured and enabled (`db.local_proxy.active_profile()`).
    pub profile_active: bool,
    /// `proxy_route::port_is_listening(profile.port)` succeeded.
    pub port_listening: bool,
    /// The TPROXY data path is actually installed and active for clients
    /// (rules/routes confirmed present, tproxy child healthy) — not merely
    /// that the SOCKS5 port answers a TCP probe. The unlock decision is
    /// gated on this: a listening port alone is not readiness. Per family:
    /// IPv6 readiness is a separate fact from IPv4 readiness.
    pub socks_route_ready: bool,
}

/// Decision for the current tick.
///
/// * `Some(Egress::Socks)` — the TPROXY path is verified ready; ensure any
///   stale block rule for the client is removed.
/// * `Some(Egress::Block)` — ensure the blackhole rule **and** route are
///   installed for the client (and the SOCKS route torn down if present).
/// * `None` — only possible with `enforce_no_direct == false` (legacy mode):
///   the loop may keep its historical behavior. In fail-closed mode this
///   never happens.
pub fn decide(policy: &FailClosedPolicy, state: EgressState) -> Option<Egress> {
    if !policy.enforce_no_direct {
        return None;
    }
    if state.profile_active && state.port_listening && state.socks_route_ready {
        Some(Egress::Socks)
    } else {
        Some(Egress::Block)
    }
}

/// Canonical form of a policy-rule `from` selector: either the universal
/// selector (`all`, `0.0.0.0/0`, `::/0`) or a concrete network with the host
/// bits cleared and an explicit prefix length.
#[derive(Debug, PartialEq, Eq)]
enum Canonical {
    Universal,
    Network(Addr, u8),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Addr {
    V4([u8; 4]),
    V6([u8; 16]),
}

impl Addr {
    /// Zeroes every bit at position >= `prefix`.
    fn masked(self, prefix: u8) -> Addr {
        match self {
            Addr::V4(octets) => {
                let mut out = [0u8; 4];
                for (i, byte) in out.iter_mut().enumerate() {
                    *byte = octets[i] & mask_byte((prefix as usize).saturating_sub(i * 8));
                }
                Addr::V4(out)
            }
            Addr::V6(bytes) => {
                let mut out = [0u8; 16];
                for (i, byte) in out.iter_mut().enumerate() {
                    *byte = bytes[i] & mask_byte((prefix as usize).saturating_sub(i * 8));
                }
                Addr::V6(out)
            }
        }
    }
}

/// Bit mask keeping the first `keep` bits of one byte.
fn mask_byte(keep: usize) -> u8 {
    if keep >= 8 {
        0xFF
    } else if keep == 0 {
        0x00
    } else {
        (0xFFu8 << (8 - keep)) as u8
    }
}

#[derive(Debug, Clone, Copy)]
struct Selector {
    addr: Addr,
    prefix: u8,
}

impl Selector {
    /// Parses `from`-selector text as printed by `ip rule show`: `all`,
    /// `10.66.67.3` (bare host == /32), `10.66.67.0/24`,
    /// `fd00:aa:bb::/64` (IPv6 compared in parsed, not textual, form).
    /// Unparsable text is `None` — it never silently matches anything.
    fn parse(s: &str) -> Option<Selector> {
        let s = s.trim();
        if s.is_empty() {
            return None;
        }
        if s == "all" {
            return Some(Selector { addr: Addr::V4([0, 0, 0, 0]), prefix: 0 });
        }
        let (addr_part, prefix_part) = match s.split_once('/') {
            Some((addr, prefix)) => (addr, Some(prefix)),
            None => (s, None),
        };
        if let Some(prefix) = prefix_part {
            if prefix.is_empty() || !prefix.bytes().all(|b| b.is_ascii_digit()) {
                return None;
            }
        }
        let (addr, host_bits) = if addr_part.contains(':') {
            (Addr::V6(parse_v6(addr_part)?), 128u8)
        } else {
            (Addr::V4(parse_v4(addr_part)?), 32u8)
        };
        let prefix = match prefix_part {
            Some(prefix) => prefix
                .parse::<u8>()
                .ok()
                .filter(|len| *len <= host_bits)?,
            None => host_bits,
        };
        Some(Selector { addr, prefix })
    }

    fn canonical(self) -> Canonical {
        if self.prefix == 0 {
            Canonical::Universal
        } else {
            Canonical::Network(self.addr.masked(self.prefix), self.prefix)
        }
    }
}

fn parse_v4(s: &str) -> Option<[u8; 4]> {
    let mut octets = [0u8; 4];
    let mut parts = s.split('.');
    for slot in octets.iter_mut() {
        let part = parts.next()?;
        if part.is_empty() || part.len() > 3 || !part.bytes().all(|b| b.is_ascii_digit()) {
            return None;
        }
        *slot = part.parse::<u8>().ok()?;
    }
    if parts.next().is_some() {
        return None;
    }
    Some(octets)
}

fn parse_v6_group(s: &str) -> Option<u16> {
    if s.is_empty() || s.len() > 4 || !s.bytes().all(|b| b.is_ascii_hexdigit()) {
        return None;
    }
    u16::from_str_radix(s, 16).ok()
}

/// RFC 4291 text form including `::` compression; scope/zone suffixes
/// (`%eth0`) are rejected. The kernel prints IPv6 rule selectors in
/// compressed form, so textual comparison would not do — hence parsing.
fn parse_v6(s: &str) -> Option<[u8; 16]> {
    if s.contains('%') {
        return None;
    }
    let (head, tail) = match s.split_once("::") {
        Some((head, tail)) => (head, Some(tail)),
        None => (s, None),
    };
    let mut groups = [0u16; 8];
    let mut count = 0usize;
    if !head.is_empty() {
        for part in head.split(':') {
            if count >= 8 {
                return None;
            }
            groups[count] = parse_v6_group(part)?;
            count += 1;
        }
    }
    let mut tail_count = 0usize;
    if let Some(tail) = tail {
        if !tail.is_empty() {
            for part in tail.split(':') {
                if count + tail_count >= 8 {
                    return None;
                }
                groups[7 - tail_count] = parse_v6_group(part)?;
                tail_count += 1;
            }
        }
    }
    if tail.is_some() {
        // "::" must elide at least one zero group.
        if count + tail_count >= 8 {
            return None;
        }
    } else if count != 8 {
        return None;
    }
    let mut bytes = [0u8; 16];
    for (i, group) in groups.iter().enumerate() {
        bytes[2 * i] = (group >> 8) as u8;
        bytes[2 * i + 1] = (group & 0xFF) as u8;
    }
    Some(bytes)
}

/// Exact selector correspondence: same canonical network, or both universal.
/// A blanket `all` rule never counts as a per-client rule (a broader rule
/// blocking everyone is a different state — for a concrete client the plan
/// still installs the client's own rule, which is harmless).
pub fn rule_from_matches(rule_from: &str, client_match: &str) -> bool {
    match (Selector::parse(rule_from), Selector::parse(client_match)) {
        (Some(rule), Some(client)) => rule.canonical() == client.canonical(),
        _ => false,
    }
}

/// Whether a block rule **for exactly `client_match`** is present in
/// `ip -4 rule show` (or `ip -6 rule show`) output.
///
/// Audit v3: pref 5290 and table 5291 alone are not enough — a rule for a
/// *different* client at the same pref/table says nothing about this client,
/// and treating it as installed leaves the client unblocked (fall-through to
/// direct egress). The `from` selector must correspond exactly to
/// `client_match`, canonically.
///
/// Error text, garbage, or a truncated line parses as NOT installed, so the
/// plan re-installs the block (fail-closed direction).
///
/// Family note: plain `ip rule show` defaults to IPv4; IPv6 state must be
/// checked with `ip -6 rule show` and the same parser.
pub fn block_rule_installed(ip_rule_show: &str, client_match: &str) -> bool {
    let want_table = BLOCK_TABLE.to_string();
    ip_rule_show.lines().any(|line| {
        let (pref, rest) = match line.split_once(':') {
            Some(parts) => parts,
            None => return false,
        };
        if pref.trim() != BLOCK_RULE_PREF {
            return false;
        }
        let mut words = rest.split_whitespace();
        if words.next() != Some("from") {
            return false;
        }
        let from = match words.next() {
            Some(selector) => selector,
            None => return false,
        };
        if words.next() != Some("lookup") {
            return false;
        }
        if !matches!(words.next(), Some(table) if table == want_table) {
            return false;
        }
        rule_from_matches(from, client_match)
    })
}

/// Whether the blackhole default route already exists, from
/// `ip route show table <BLOCK_TABLE>` (or `ip -6 route show table …`).
/// The route is per family; one blackhole default per family in table 5291.
pub fn blackhole_route_installed(table_route_show: &str) -> bool {
    table_route_show.lines().any(|line| {
        let mut words = line.split_whitespace();
        words.next() == Some("blackhole") && words.next() == Some("default")
    })
}

/// `ip` commands that move the kernel from `current` to `desired`.
///
/// Removal first (mirrors `exit_routes::plan`), and the blackhole route is
/// only added together with the first rule that needs it — except that a
/// missing route is repaired even when the rule is already present (audit
/// fix 1: rule into an empty table falls through to the main table).
///
/// `client_rule_installed` is per-client state: the result of
/// `block_rule_installed(ip_rule_show, client_match)` (audit fix 3), never a
/// pref/table-only check.
///
/// An `Egress::Socks` request without `socks_route_ready` is demoted to
/// `Egress::Block` (audit fix 2): no plan step may open a direct window
/// before the TPROXY path is verified ready.
///
/// The fragments are family-agnostic: run them through `ip -4` or `ip -6`
/// depending on the client match's family.
pub fn plan(
    client_rule_installed: bool,
    blackhole_installed: bool,
    socks_route_ready: bool,
    desired: Egress,
    client_match: &str,
) -> Vec<Vec<String>> {
    let desired = match desired {
        Egress::Socks if !socks_route_ready => Egress::Block,
        other => other,
    };
    let mut steps = Vec::new();
    let rule = |verb: &str| -> Vec<String> {
        vec![
            "rule".to_owned(),
            verb.to_owned(),
            "from".to_owned(),
            client_match.to_owned(),
            "lookup".to_owned(),
            BLOCK_TABLE.to_string(),
            "pref".to_owned(),
            BLOCK_RULE_PREF.to_owned(),
        ]
    };
    match desired {
        Egress::Block => {
            if !blackhole_installed {
                steps.push(
                    [
                        "route".to_owned(),
                        "add".to_owned(),
                        "blackhole".to_owned(),
                        "default".to_owned(),
                        "table".to_owned(),
                        BLOCK_TABLE.to_string(),
                    ]
                    .to_vec(),
                );
            }
            if !client_rule_installed {
                steps.push(rule("add"));
            }
        }
        Egress::Socks => {
            if client_rule_installed {
                steps.push(rule("del"));
            }
        }
    }
    steps
}

#[cfg(test)]
mod tests {
    use super::*;

    const ENFORCED: FailClosedPolicy = FailClosedPolicy { enforce_no_direct: true };
    const LEGACY: FailClosedPolicy = FailClosedPolicy { enforce_no_direct: false };

    const CLIENT_A: &str = "10.66.67.3";
    const CLIENT_B: &str = "10.66.68.0/24";

    fn state(profile_active: bool, port_listening: bool, socks_route_ready: bool) -> EgressState {
        EgressState { profile_active, port_listening, socks_route_ready }
    }

    fn apply(steps: &[Vec<String>], client_rule_installed: &mut bool, blackhole_installed: &mut bool) {
        for step in steps {
            match (step[0].as_str(), step[1].as_str()) {
                ("route", "add") => *blackhole_installed = true,
                ("rule", "add") => *client_rule_installed = true,
                ("rule", "del") => *client_rule_installed = false,
                other => panic!("unexpected plan step: {other:?}"),
            }
        }
    }

    #[test]
    fn healthy_profile_routes_through_socks() {
        assert_eq!(decide(&ENFORCED, state(true, true, true)), Some(Egress::Socks));
    }

    #[test]
    fn dead_socks_blocks_instead_of_direct() {
        // Regression pin for main.rs:1482 ("clients switched to the direct
        // route") — the fix must decide Block, never leave direct as fallback.
        assert_eq!(decide(&ENFORCED, state(true, false, false)), Some(Egress::Block));
    }

    #[test]
    fn no_profile_blocks_instead_of_direct() {
        // Regression pin for main.rs:1452 ("direct VPS route restored").
        assert_eq!(decide(&ENFORCED, state(false, true, true)), Some(Egress::Block));
        assert_eq!(decide(&ENFORCED, state(false, false, false)), Some(Egress::Block));
    }

    #[test]
    fn listening_port_without_tproxy_readiness_blocks() {
        // Negative test (audit fix 2): a TCP probe on the SOCKS5 port is not
        // a data-path proof — TPROXY may still be installing (child spawn
        // retries, rule activation). Until `socks_route_ready` is confirmed
        // the decision stays Block; the unlock happens only when it is true.
        assert_eq!(decide(&ENFORCED, state(true, true, false)), Some(Egress::Block));
        assert_eq!(decide(&ENFORCED, state(true, true, true)), Some(Egress::Socks));
    }

    #[test]
    fn decision_is_total_under_enforcement() {
        // Every input combination yields a decision; none of them can be
        // "direct" because Egress has no Direct variant.
        for profile_active in [true, false] {
            for port_listening in [true, false] {
                for socks_route_ready in [true, false] {
                    let decision =
                        decide(&ENFORCED, state(profile_active, port_listening, socks_route_ready));
                    assert!(decision.is_some());
                }
            }
        }
    }

    #[test]
    fn legacy_mode_still_allows_none() {
        // Explicit opt-out keeps the historical behavior; migration path for
        // operators who need a grace period.
        assert_eq!(decide(&LEGACY, state(true, false, false)), None);
    }

    #[test]
    fn block_plan_installs_blackhole_then_rule() {
        let steps = plan(false, false, true, Egress::Block, "10.66.67.0/24");
        assert_eq!(
            steps,
            vec![
                ["route", "add", "blackhole", "default", "table", "5291"]
                    .iter()
                    .map(|s| s.to_string())
                    .collect::<Vec<_>>(),
                [
                    "rule", "add", "from", "10.66.67.0/24", "lookup", "5291", "pref", "5290"
                ]
                .iter()
                .map(|s| s.to_string())
                .collect::<Vec<_>>(),
            ]
        );
    }

    #[test]
    fn block_plan_is_idempotent() {
        let steps = plan(true, true, false, Egress::Block, "10.66.67.0/24");
        assert!(steps.is_empty());
    }

    #[test]
    fn rule_without_blackhole_route_is_repaired() {
        // Negative test (audit fix 1): a blocking rule whose lookup table has
        // no route is NOT a working block — the kernel falls through to the
        // next policy rule and ultimately to the main table (direct egress)
        // while the operator believes clients are blocked. The plan must not
        // treat this state as idempotent; it repairs the route regardless of
        // rule state and readiness.
        for socks_route_ready in [true, false] {
            let steps = plan(true, false, socks_route_ready, Egress::Block, "10.66.67.0/24");
            assert_eq!(steps.len(), 1);
            assert_eq!(steps[0][0], "route");
            assert_eq!(steps[0][1], "add");
            assert_eq!(steps[0][2], "blackhole");
            assert!(steps.iter().all(|s| !(s[0] == "rule" && s[1] == "add")));
        }
    }

    #[test]
    fn unlock_is_refused_until_tproxy_ready() {
        // Negative test (audit fix 2): with the block in place and TPROXY not
        // yet verified ready, no plan step may remove the block rule — that
        // would open a direct-egress window before the data path is active.
        let steps = plan(true, true, false, Egress::Socks, "10.66.67.0/24");
        assert!(steps.is_empty());

        // Half-installed block state with no readiness: stays blocked and
        // repairs the route instead of unlocking.
        let steps = plan(true, false, false, Egress::Socks, "10.66.67.0/24");
        assert_eq!(steps.len(), 1);
        assert_eq!((steps[0][0].as_str(), steps[0][2].as_str()), ("route", "blackhole"));
        assert!(steps.iter().all(|s| s.iter().all(|w| w != "del")));

        // No block installed, no readiness: the plan may only *add* block
        // state, never remove anything.
        let steps = plan(false, false, false, Egress::Socks, "10.66.67.0/24");
        assert_eq!(steps.len(), 2);
        assert!(steps.iter().all(|s| s[1] != "del"));
    }

    #[test]
    fn socks_plan_removes_block_and_never_touches_main_route() {
        let steps = plan(true, true, true, Egress::Socks, "10.66.67.0/24");
        assert_eq!(steps.len(), 1);
        assert_eq!(steps[0][0], "rule");
        assert_eq!(steps[0][1], "del");
        assert!(steps.iter().all(|step| !step.iter().any(|word| word == "main")));
    }

    #[test]
    fn recovery_plan_does_not_duplicate_blackhole_removal() {
        // Swapping Block -> Socks must only remove the rule; the blackhole
        // route may stay (it is inert without a rule pointing at it).
        let steps = plan(true, true, true, Egress::Socks, "10.66.67.0/24");
        assert!(steps.iter().all(|step| step[0] == "rule"));
    }

    #[test]
    fn rule_parser_matches_only_our_pref() {
        let show = "0:\tfrom all lookup local\n\
                    5209:\tfrom all fwmark 0x1 lookup 100\n\
                    5300:\tfrom 10.66.67.3 lookup 2001\n\
                    5290:\tfrom 10.66.67.3 lookup 5291\n";
        assert!(block_rule_installed(show, CLIENT_A));
        assert!(!block_rule_installed(
            "0:\tfrom all lookup local\n5300:\tfrom 10.66.67.3 lookup 2001\n",
            CLIENT_A
        ));
        // Right pref, wrong table number (5290 vs 5291): not installed.
        assert!(!block_rule_installed("5290:\tfrom 10.66.67.3 lookup 5290\n", CLIENT_A));
        // Trailing qualifiers after the table do not invalidate the rule.
        assert!(block_rule_installed("5290:\tfrom 10.66.67.3 lookup 5291 tos 0x10\n", CLIENT_A));
    }

    #[test]
    fn rule_parser_requires_exact_client_match() {
        // Negative test (audit fix 3): a block rule for ANOTHER client at the
        // same pref and table must not be reported as installed for this
        // client. The pre-fix parser matched any 5290/5291 rule, so the plan
        // skipped the `rule add` and the client silently fell through to the
        // main table (direct egress).
        let show = "5290:\tfrom 10.66.67.3 lookup 5291\n";
        assert!(block_rule_installed(show, CLIENT_A));
        assert!(!block_rule_installed(show, CLIENT_B));

        // A blanket `all` rule is not a per-client rule: the plan still
        // installs the client's own selector (harmless, fail-closed).
        assert!(!block_rule_installed("5290:\tfrom all lookup 5291\n", CLIENT_A));

        // Family isolation: an IPv4 rule never matches an IPv6 client, and
        // the other way around.
        assert!(!block_rule_installed(show, "fd00:aa:bb::/64"));
        assert!(!block_rule_installed(
            "5290:\tfrom fd00:aa:bb::/64 lookup 5291\n",
            CLIENT_A
        ));
    }

    #[test]
    fn rule_parser_canonicalizes_ip_forms() {
        // The kernel prints a bare host IP without /32 and may keep host
        // bits inside a printed prefix; exact match must survive both.
        assert!(block_rule_installed("5290:\tfrom 10.66.67.3 lookup 5291\n", "10.66.67.3/32"));
        assert!(block_rule_installed("5290:\tfrom 10.66.67.99/24 lookup 5291\n", "10.66.67.0/24"));
        assert!(block_rule_installed("5290:\tfrom 10.66.67.0/24 lookup 5291\n", "10.66.67.99/24"));

        // IPv6: kernel-compressed form vs fully spelled-out client_match.
        assert!(block_rule_installed(
            "5290:\tfrom fd00:aa:bb::/64 lookup 5291\n",
            "fd00:00aa:00bb:0000:0000:0000:0000:0000/64"
        ));
        assert!(block_rule_installed("5290:\tfrom fd00:0:0:0:0:0:0:1 lookup 5291\n", "fd00::1"));

        // Canonical equality is still exact: same address, different prefix
        // length (or a neighbor network) is NOT a match.
        assert!(!block_rule_installed("5290:\tfrom 10.66.67.0/24 lookup 5291\n", "10.66.67.0/25"));
        assert!(!block_rule_installed("5290:\tfrom 10.66.66.0/24 lookup 5291\n", "10.66.67.0/24"));
        assert!(!block_rule_installed("5290:\tfrom fd00:aa:bb::/64 lookup 5291\n", "fd00:aa:bc::/64"));
    }

    #[test]
    fn rule_parser_robust_to_errors_and_garbage() {
        // Negative test: `ip rule` failures surface as stderr text or empty
        // output; the parser must report NOT installed so the plan
        // re-installs (fail-closed direction) instead of mistaking an error
        // for a healthy block.
        for garbage in [
            "",
            "We have an error talking to the kernel",
            "RTNETLINK answers: Operation not permitted\n",
            "ip: socket: Operation not permitted\n",
            "5290:\tfrom\n",                          // truncated rule
            "5290:\tlookup 5291\n",                   // no selector
            "5290:\tfrom 10.66.67.3\n",               // missing lookup
            "no-colon-here\n",
            "5290:\tfrom 10.66.67.3 lookup 52910\n",  // table number prefix of ours
            "5290:\tfrom hostname.invalid lookup 5291\n", // unparsable selector
        ] {
            assert!(
                !block_rule_installed(garbage, CLIENT_A),
                "parser false-positive on {garbage:?}"
            );
        }
    }

    #[test]
    fn two_clients_do_not_share_block_state() {
        let show = "5290:\tfrom 10.66.67.3 lookup 5291\n\
                    5290:\tfrom 10.66.68.0/24 lookup 5291\n";
        assert!(block_rule_installed(show, CLIENT_A));
        assert!(block_rule_installed(show, CLIENT_B));

        // Third client: not blocked even though two other rules exist; the
        // plan must install a rule with the client's OWN selector and never
        // delete the other clients' rules.
        let client_c = "10.66.69.0/24";
        let installed_for_c = block_rule_installed(show, client_c);
        assert!(!installed_for_c);
        let steps = plan(installed_for_c, true, true, Egress::Block, client_c);
        assert_eq!(steps.len(), 1);
        assert_eq!(steps[0][3], client_c);
        assert!(steps.iter().all(|s| !(s[0] == "rule" && s[1] == "del")));

        // Unlocking A removes only A's rule; B stays blocked.
        let steps = plan(true, true, true, Egress::Socks, CLIENT_A);
        assert_eq!(steps.len(), 1);
        assert_eq!(steps[0][3], CLIENT_A);
        assert_eq!(steps[0][1], "del");
    }

    #[test]
    fn initial_block_installs_full_state_when_nothing_is_ready() {
        // Negative test: at t=0 (no rule, no route, port down, TPROXY not
        // ready) the very first plan must already move the client to the
        // blackhole — blocking never waits for readiness, only unlocking
        // does.
        let steps = plan(false, false, false, Egress::Block, CLIENT_A);
        assert_eq!(steps.len(), 2);
        assert_eq!(steps[0][0], "route");
        assert_eq!(steps[1][0], "rule");
        let (mut installed, mut route) = (false, false);
        apply(&steps, &mut installed, &mut route);
        assert!(installed && route, "initial plan must install rule AND route");
    }

    #[test]
    fn ipv6_client_match_passes_through_plan_verbatim() {
        // IPv6 applicability: the plan fragments are family-agnostic — the
        // executor issues them as `ip -6 …` for IPv6 client matches (same
        // pref; table 5291 is family-scoped so both families can share the
        // number). The selector must be embedded byte-for-byte.
        let client = "fd00:aa:bb::/64";
        let steps = plan(false, true, true, Egress::Block, client);
        assert_eq!(
            steps,
            vec![["rule", "add", "from", client, "lookup", "5291", "pref", "5290"]
                .iter()
                .map(|s| s.to_string())
                .collect::<Vec<_>>()]
        );
        let del = plan(true, true, true, Egress::Socks, client);
        assert_eq!(del.len(), 1);
        assert_eq!(del[0][3], client);

        // The parser accepts IPv6 kernel output for the same selector, and
        // the blackhole parser accepts `ip -6 route show table 5291` output.
        assert!(block_rule_installed(
            "0:\tfrom all lookup local\n5290:\tfrom fd00:aa:bb::/64 lookup 5291\n",
            client
        ));
        assert!(blackhole_route_installed("blackhole default\n"));
    }

    #[test]
    fn blackhole_parser_accepts_output_orderings() {
        assert!(blackhole_route_installed("blackhole default\n"));
        assert!(blackhole_route_installed("blackhole default  metric 5291\n"));
        assert!(!blackhole_route_installed("default via 10.0.0.1 dev eth0\n"));
        // Missing route: an empty table, error text, and a plain default via
        // gateway all mean NOT installed (audit fix 1 precondition: a bare
        // rule in an empty table falls through to the main table).
        assert!(!blackhole_route_installed(""));
        assert!(!blackhole_route_installed("Nothing to show.\n"));
        assert!(!blackhole_route_installed("RTNETLINK answers: Invalid argument\n"));
    }

    #[test]
    fn plan_converges_to_decided_state() {
        // Property sweep: from every (rule, route) start state, executing the
        // plan for the decided egress exactly once must land in a state where
        // the decision actually holds — Block ⇒ rule AND route present
        // (blocking effective), Socks ⇒ no block rule left. This pins audit
        // fixes 1 and 2 at the state-machine level, not just per-case.
        for profile_active in [true, false] {
            for port_listening in [true, false] {
                for socks_route_ready in [true, false] {
                    let decision =
                        decide(&ENFORCED, state(profile_active, port_listening, socks_route_ready))
                            .unwrap();
                    for client_rule_installed in [true, false] {
                        for blackhole_installed in [true, false] {
                            let (mut block, mut route) =
                                (client_rule_installed, blackhole_installed);
                            let steps =
                                plan(block, route, socks_route_ready, decision, "10.66.67.0/24");
                            apply(&steps, &mut block, &mut route);
                            match decision {
                                Egress::Block => {
                                    assert!(
                                        block && route,
                                        "block not effective after plan \
                                         (start rule={client_rule_installed} route={blackhole_installed}): \
                                         end rule={block} route={route}"
                                    );
                                }
                                Egress::Socks => {
                                    assert!(
                                        !block,
                                        "block rule still present after unlock \
                                         (start rule={client_rule_installed} route={blackhole_installed})"
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    #[test]
    fn parser_and_plan_round_trip_converges() {
        // Audit v3 property: parser and planner agree. From every start
        // state, one plan execution — with `client_rule_installed` taken from
        // the parser over a synthesized `ip rule show` dump — converges to a
        // dump where the parser confirms the decided state. Also pins that a
        // foreign client's rule in the dump never changes this client's
        // parsed state.
        for client in ["10.66.67.0/24", "fd00:aa:bb::/64"] {
            let other_line = if client.starts_with("10.") {
                "5290:\tfrom 10.66.69.0/24 lookup 5291\n"
            } else {
                "5290:\tfrom fd00:cc::/32 lookup 5291\n"
            };
            for desired in [Egress::Block, Egress::Socks] {
                for rule_before in [true, false] {
                    for route_before in [true, false] {
                        for ready in [true, false] {
                            let own_line = if rule_before {
                                format!("5290:\tfrom {client} lookup 5291\n")
                            } else {
                                String::new()
                            };
                            let dump = format!("{own_line}{other_line}");
                            let table_dump = if route_before { "blackhole default\n" } else { "" };

                            let installed = block_rule_installed(&dump, client);
                            assert_eq!(installed, rule_before, "foreign rule leaked into own state");

                            let route_installed = blackhole_route_installed(table_dump);
                            assert_eq!(route_installed, route_before);

                            let (mut rule, mut route) = (installed, route_installed);
                            let steps = plan(rule, route, ready, desired, client);
                            apply(&steps, &mut rule, &mut route);

                            let dump_after = if rule {
                                format!("5290:\tfrom {client} lookup 5291\n{other_line}")
                            } else {
                                other_line.to_owned()
                            };
                            let table_after = if route { "blackhole default\n" } else { "" };
                            let blocked_now = block_rule_installed(&dump_after, client)
                                && blackhole_route_installed(table_after);
                            match (desired, ready) {
                                (Egress::Block, _) => assert!(
                                    blocked_now,
                                    "block not confirmed by parser (rule_before={rule_before}, \
                                     route_before={route_before}, ready={ready})"
                                ),
                                (Egress::Socks, true) => assert!(
                                    !block_rule_installed(&dump_after, client),
                                    "client still blocked after verified unlock"
                                ),
                                // Audit fix 2: without readiness the Socks
                                // request is demoted to Block.
                                (Egress::Socks, false) => assert!(
                                    blocked_now,
                                    "premature unlock without readiness (rule_before={rule_before})"
                                ),
                            }
                        }
                    }
                }
            }
        }
    }
}
