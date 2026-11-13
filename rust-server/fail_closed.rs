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
/// mark tables.
pub const BLOCK_TABLE: u32 = 5291;

/// Where client traffic goes. Deliberately no `Direct` variant.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Egress {
    /// TPROXY route through the local SOCKS5 is up.
    Socks,
    /// Local SOCKS5 unavailable: drop client traffic (blackhole), never direct.
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
}

/// Decision for the current tick.
///
/// * `Some(Egress::Socks)` — ensure the SOCKS/TPROXY route is installed and
///   any stale block rule is removed.
/// * `Some(Egress::Block)` — ensure the blackhole rule is installed (and the
///   SOCKS route torn down if present).
/// * `None` — only possible with `enforce_no_direct == false` (legacy mode):
///   the loop may keep its historical behavior. In fail-closed mode this
///   never happens.
pub fn decide(policy: &FailClosedPolicy, state: EgressState) -> Option<Egress> {
    if !policy.enforce_no_direct {
        return None;
    }
    if state.profile_active && state.port_listening {
        Some(Egress::Socks)
    } else {
        Some(Egress::Block)
    }
}

/// Current block-rule state, parsed from `ip -4 rule show` the same way
/// `exit_routes::parse_rules` filters `exit_routes::RULE_PREF`.
pub fn block_rule_installed(ip_rule_show: &str) -> bool {
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
        let _ip = words.next();
        if words.next() != Some("lookup") {
            return false;
        }
        matches!(words.next(), Some(table) if table == BLOCK_TABLE.to_string())
    })
}

/// Whether the blackhole default route already exists, from
/// `ip route show table <BLOCK_TABLE>`.
pub fn blackhole_route_installed(table_route_show: &str) -> bool {
    table_route_show.lines().any(|line| {
        let mut words = line.split_whitespace();
        words.next() == Some("blackhole") && words.next() == Some("default")
    })
}

/// `ip` commands that move the kernel from `current` to `desired`.
///
/// Removal first (mirrors `exit_routes::plan`), and the blackhole route is
/// only added together with the first rule that needs it.
pub fn plan(
    block_installed: bool,
    blackhole_installed: bool,
    desired: Egress,
    client_match: &str,
) -> Vec<Vec<String>> {
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
            if !block_installed {
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
                steps.push(rule("add"));
            }
        }
        Egress::Socks => {
            if block_installed {
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

    fn state(profile_active: bool, port_listening: bool) -> EgressState {
        EgressState { profile_active, port_listening }
    }

    #[test]
    fn healthy_profile_routes_through_socks() {
        assert_eq!(decide(&ENFORCED, state(true, true)), Some(Egress::Socks));
    }

    #[test]
    fn dead_socks_blocks_instead_of_direct() {
        // Regression pin for main.rs:1482 ("clients switched to the direct
        // route") — the fix must decide Block, never leave direct as fallback.
        assert_eq!(decide(&ENFORCED, state(true, false)), Some(Egress::Block));
    }

    #[test]
    fn no_profile_blocks_instead_of_direct() {
        // Regression pin for main.rs:1452 ("direct VPS route restored").
        assert_eq!(decide(&ENFORCED, state(false, true)), Some(Egress::Block));
        assert_eq!(decide(&ENFORCED, state(false, false)), Some(Egress::Block));
    }

    #[test]
    fn decision_is_total_under_enforcement() {
        // Every input combination yields a decision; none of them can be
        // "direct" because Egress has no Direct variant.
        for profile_active in [true, false] {
            for port_listening in [true, false] {
                for route_installed in [true, false] {
                    let _ = route_installed; // not an input to decide(); kept in scope for exhaustiveness docs
                    let decision = decide(&ENFORCED, state(profile_active, port_listening));
                    assert!(decision.is_some());
                }
            }
        }
    }

    #[test]
    fn legacy_mode_still_allows_none() {
        // Explicit opt-out keeps the historical behavior; migration path for
        // operators who need a grace period.
        assert_eq!(decide(&LEGACY, state(true, false)), None);
    }

    #[test]
    fn block_plan_installs_blackhole_then_rule() {
        let steps = plan(false, false, Egress::Block, "10.66.67.0/24");
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
        let steps = plan(true, true, Egress::Block, "10.66.67.0/24");
        assert!(steps.is_empty());
    }

    #[test]
    fn socks_plan_removes_block_and_never_touches_main_route() {
        let steps = plan(true, true, Egress::Socks, "10.66.67.0/24");
        assert_eq!(steps.len(), 1);
        assert_eq!(steps[0][0], "rule");
        assert_eq!(steps[0][1], "del");
        assert!(steps.iter().all(|step| !step.iter().any(|word| word == "main")));
    }

    #[test]
    fn recovery_plan_does_not_duplicate_blackhole_removal() {
        // Swapping Block -> Socks must only remove the rule; the blackhole
        // route may stay (it is inert without a rule pointing at it).
        let steps = plan(true, true, Egress::Socks, "10.66.67.0/24");
        assert!(steps.iter().all(|step| step[0] == "rule"));
    }

    #[test]
    fn rule_parser_matches_only_our_pref() {
        let show = "0:\tfrom all lookup local\n\
                    5209:\tfrom all fwmark 0x1 lookup 100\n\
                    5300:\tfrom 10.66.67.3 lookup 2001\n\
                    5290:\tfrom 10.66.67.0/24 lookup 5291\n";
        assert!(block_rule_installed(show));
        assert!(!block_rule_installed("0:\tfrom all lookup local\n5300:\tfrom 10.66.67.3 lookup 2001\n"));
    }

    #[test]
    fn blackhole_parser_accepts_output_orderings() {
        assert!(blackhole_route_installed("blackhole default\n"));
        assert!(blackhole_route_installed("blackhole default  metric 5291\n"));
        assert!(!blackhole_route_installed("default via 10.0.0.1 dev eth0\n"));
    }

    #[test]
    fn transition_matrix_never_produces_direct_or_churn() {
        // Full state machine sweep: from every (block, blackhole) current
        // state, the plan for each decision is minimal and contains no
        // command that could open direct egress (no "rule del" for exit
        // tables, no "route del" of main, no "replace").
        for block_installed in [true, false] {
            for blackhole_installed in [true, false] {
                for desired in [Egress::Socks, Egress::Block] {
                    let steps = plan(block_installed, blackhole_installed, desired, "10.66.67.0/24");
                    for step in &steps {
                        assert!(step.iter().all(|word| word != "replace"));
                        if step[0] == "route" {
                            assert_eq!(step[1], "add");
                            assert_eq!(step[2], "blackhole");
                        }
                        if step[0] == "rule" && step[1] == "del" {
                            assert_eq!(desired, Egress::Socks);
                        }
                    }
                    if desired == Egress::Block && block_installed {
                        assert!(steps.is_empty());
                    }
                    if desired == Egress::Socks && !block_installed {
                        assert!(steps.is_empty());
                    }
                }
            }
        }
    }
}
