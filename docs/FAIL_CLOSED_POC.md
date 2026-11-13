# Fail-closed egress PoC (research, no runtime changes)

Related: private issue `saymer-alt/keenetic-auto-setup-dev#223`, Phase B.3.
Branch: `research/fail-closed-poc` — **Draft PR, do not merge as-is**; the
runtime integration needs its own review and a lab run.

## Problem

Owner policy is **NO DIRECT**: client traffic that arrives over TURN must
never egress the VPS through the default route. The current `rust-server`
violates this in five places (all found by code reading of `eddc7fab`
= upstream `dev`/`stable`):

| # | Location | Behavior |
| --- | --- | --- |
| 1 | `rust-server/main.rs:1443-1459` (`local_proxy_monitor_loop`) | No active local-proxy profile → TPROXY route removed → **"direct VPS route restored"** |
| 2 | `rust-server/main.rs:1466-1489` | SOCKS5 port stops answering → route deactivated → **"clients switched to the direct route"**, retry every 3 s |
| 3 | `rust-server/main.rs:1510-1536` | Port down before route exists → **"traffic goes direct"** while retrying (pauses up to 120 s) |
| 4 | `rust-server/main.rs:1576` | `ProxyRoute::connect` fails → **"Direct VPS route is active; retry in 3s"** |
| 5 | `rust-server/model.rs:157-164` (`exit_table`) | Invalid/removed `default_exit` id → `None` → cascade clients land on the main table (direct) |

Note: merely *listening* on a local SOCKS5 port is not a safety property
(the issue's own warning): the monitor decides by `port_is_listening()`
(TCP connect probe, `proxy_route.rs:320`) and still chooses direct.

## What this branch adds

`rust-server/fail_closed.rs` — a **pure, dependency-free decision module**:

* `Egress { Socks, Block }` — the enum has **no `Direct` variant**, so a
  direct fallback cannot be expressed while enforcement is on.
* `decide(policy, state)` — total decision function: healthy SOCKS → `Socks`,
  anything else (dead port, no profile) → `Block` (blackhole policy routing).
* `block_rule_installed()` / `blackhole_route_installed()` — parsers for
  `ip -4 rule show` / `ip route show table`, in the style of `exit_routes.rs`.
* `plan(...)` — minimal `ip rule/route` command plan (idempotent, removal
  first), mirroring `exit_routes::plan`.
* 12 unit tests, including regression pins for points 1-3 and a full
  transition-matrix sweep asserting no command can open direct egress.

The module is **not** wired into the build (no `mod fail_closed;` line was
added), so this branch cannot change runtime behavior.

## Verification (actually executed)

```
rustc 1.100.0 (0ef1bc386 2026-11-09), x86_64-pc-windows-gnu, minimal profile
rustc --edition 2021 --test rust-server/fail_closed.rs → 12 passed; 0 failed
rustc --edition 2024 --test rust-server/fail_closed.rs → 12 passed; 0 failed
```

Edition 2024 is the crate edition (`Cargo.toml`), so the file is drop-in.

**Not verified here:** kernel-level behavior (blackhole table + TPROXY
interaction), the monitor-loop patch, and `cargo check` of the whole crate
(requires the full dependency tree + Linux). Those belong to a follow-up PR
plus one lab run on a test VPS.

## Proposed integration (next PR, after owner review)

1. `mod fail_closed;` in `main.rs`; add `no_direct: bool` (default `true`) to
   server config; thread it through `App`.
2. In `local_proxy_monitor_loop`, replace branches 1-4 above with
   `fail_closed::decide(...)` and execute the plan with the same
   `ip`-command machinery `exit_routes_loop` already uses.
3. When a profile is removed by an operator (panel `local_proxy_deactivate`),
   log that egress is now **blocked**, not "direct restored".
4. Point 5: if `exit_table()` returns `None` for a client's desired exit and
   enforcement is on, install the same block rule for that client instead of
   leaving it on the main table.
5. Regression tests: the 12 unit tests plus one integration test that runs
   `decide` against the real monitor-loop states.

## Audit v2 (branch `research/fail-closed-audit-fixes`)

Re-review of this PoC (Draft PR #1) found two defects **in the module
itself**; both are fixed on the follow-up branch with negative tests:

1. **Block rule without blackhole route was treated as done.**
   `plan(block_installed = true, blackhole_installed = false, Egress::Block)`
   returned an empty plan ("idempotent"), but a rule that looks up an empty
   table makes the kernel fall through to the next policy rule — ultimately
   the main table, i.e. direct egress while clients are believed to be
   blocked. Fixed: `plan` repairs the missing route regardless of rule state
   (`rule_without_blackhole_route_is_repaired`).
2. **Premature unlock before TPROXY readiness.** `decide` keyed on
   `port_is_listening()` alone, so the plan removed the block rule as soon
   as the SOCKS5 port answered a TCP probe — while the TPROXY data path
   (tproxy child spawn retries, rule activation) was not active yet: a
   direct-egress window. Fixed: `EgressState.socks_route_ready` gates both
   `decide` and `plan`; without readiness an `Egress::Socks` request is
   demoted to `Block` (`listening_port_without_tproxy_readiness_blocks`,
   `unlock_is_refused_until_tproxy_ready`).

A property sweep (`plan_converges_to_decided_state`) additionally proves
that executing the plan once converges to the decided state from every
(rule, route) start state. Test count: 12 → 15.

## Audit v3 (final pass, same branch)

A final review pass fixed a third defect and pinned IPv6 applicability:

3. **`block_rule_installed` matched any rule at pref 5290 / table 5291.**
   A rule installed for a *different* client made the parser report the
   block as installed for this one, so `plan` skipped the `rule add` and
   the client silently fell through to the main table (direct egress) —
   the per-client twin of defect 1. The parser now requires exact
   canonical correspondence of the `from` selector: family-aware (IPv4
   never matches an IPv6 selector), host bits masked, bare host IP
   equivalent to `/32` / `/128`, IPv6 compared in parsed (not textual)
   form so kernel compression cannot break matching, and a blanket
   `from all` rule never counts as a per-client rule. Errors, garbage and
   truncated `ip rule` output parse as NOT installed, so the plan
   re-installs (fail-closed direction).

New negative tests: two different clients (a foreign rule never leaks
into this client's state; unlock is client-scoped), missing route (empty
table, error text), `ip rule` errors/garbage (empty output, RTNETLINK
errors, truncated lines, wrong table number, unparsable selector),
initial blocking at t=0 with nothing ready, premature TPROXY unlock
without readiness, and a parser+planner round-trip sweep proving
convergence from every (rule, route, readiness) start state in both
families. Test count: 15 → 22.

IPv6 applicability (test-pinned, no code duplication): the parsers
accept `ip -6 rule show` and `ip -6 route show table 5291` output
unchanged (same text format); the `plan` fragments are family-agnostic
and the executor must run them as `ip -6 …` for IPv6 client matches.
Table 5291 is family-scoped in the kernel, so both families can share
the number, but rule/route state and `socks_route_ready` must be tracked
per family — and iproute2 defaults to IPv4 without an explicit `-4`/`-6`
flag, so the integration must query both families explicitly.

Verification of this pass (executed locally, Windows, workspace-local
rustc):

```
rustc 1.100.0 (0ef1bc386 2026-11-09), x86_64-pc-windows-gnu, minimal profile
rustc --edition 2021 --test rust-server/fail_closed.rs → 22 passed; 0 failed
rustc --edition 2024 --test rust-server/fail_closed.rs → 22 passed; 0 failed
```

## License note

Repo root `LICENSE` is PolyForm Noncommercial 1.0.0, while
`rust-server/Cargo.toml` declares `license = "Unlicense"`. This
inconsistency predates this branch and needs an owner decision before any
redistribution decision; this PoC inherits the repo status quo.
