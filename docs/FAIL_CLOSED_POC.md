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

## License note

Repo root `LICENSE` is PolyForm Noncommercial 1.0.0, while
`rust-server/Cargo.toml` declares `license = "Unlicense"`. This
inconsistency predates this branch and needs an owner decision before any
redistribution decision; this PoC inherits the repo status quo.
