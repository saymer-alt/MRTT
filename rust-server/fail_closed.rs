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
//!    `plan(block_installed = true, blackhole_installed = false, Egress::Block)`
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
    /// gated on this: a listening port alone is not readiness.
    pub socks_route_ready: bool,
}

/// Decision for the current tick.
///
/// * `Some(Egress::Socks)` — the TPROXY path is verified ready; ensure any
///   stale block rule is removed.
/// * `Some(Egress::Block)` — ensure the blackhole rule **and** route are
///   installed (and the SOCKS route torn down if present).
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
/// only added together with the first rule that needs it — except that a
/// missing route is repaired even when the rule is already present (audit
/// fix 1: rule into an empty table falls through to the main table).
///
/// An `Egress::Socks` request without `socks_route_ready` is demoted to
/// `Egress::Block` (audit fix 2): no plan step may open a direct window
/// before the TPROXY path is verified ready.
pub fn plan(
    block_installed: bool,
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
            if !block_installed {
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

    fn state(profile_active: bool, port_listening: bool, socks_route_ready: bool) -> EgressState {
        EgressState { profile_active, port_listening, socks_route_ready }
    }

    fn apply(steps: &[Vec<String>], block_installed: &mut bool, blackhole_installed: &mut bool) {
        for step in steps {
            match (step[0].as_str(), step[1].as_str()) {
                ("route", "add") => *blackhole_installed = true,
                ("rule", "add") => *block_installed = true,
                ("rule", "del") => *block_installed = false,
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
                    for block_installed in [true, false] {
                        for blackhole_installed in [true, false] {
                            let (mut block, mut route) = (block_installed, blackhole_installed);
                            let steps =
                                plan(block, route, socks_route_ready, decision, "10.66.67.0/24");
                            apply(&steps, &mut block, &mut route);
                            match decision {
                                Egress::Block => {
                                    assert!(
                                        block && route,
                                        "block not effective after plan \
                                         (start rule={block_installed} route={blackhole_installed}): \
                                         end rule={block} route={route}"
                                    );
                                }
                                Egress::Socks => {
                                    assert!(
                                        !block,
                                        "block rule still present after unlock \
                                         (start rule={block_installed} route={blackhole_installed})"
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
