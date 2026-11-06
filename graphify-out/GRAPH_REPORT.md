# Graph Report - mrtt  (2026-11-06)

## Corpus Check
- 270 files · ~283,153 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 63 file(s) not represented in the graph (top: .xml 37, (none) 8, .bat 7)

## Summary
- 6809 nodes · 17026 edges · 266 communities (200 shown, 66 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 511 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `f5f63e65`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- rust-server/protocol.rs
- TunnelService
- turn_core.rs
- dtls.rs
- SettingsStore
- ServersTab.kt
- logging.rs
- rust-server/main.rs
- SettingsTab.kt
- ClientBuilder
- MainActivity.kt
- ExceptionsTab.kt
- TunnelLifecycleState
- TunnelWorkersSlider.kt
- rust-client/main.rs
- auth.rs
- session.rs
- web_panel.rs
- tun_device.rs
- QrScannerDialog.kt
- TvDevicePolicy.kt
- TunnelManager
- turn_stream.rs
- obfs.rs
- turn_integration_tests.rs
- vk_js_calls.rs
- tproxy.rs
- worker.rs
- tcp.rs
- TurnAllocation
- dns.rs
- .parse
- deploy.sh
- dataplane.rs
- .get
- ProtocolEngine
- captcha.rs
- safari/mod.rs
- tokio_io.rs
- turn_ip_pool.rs
- TrafficSnapshot
- App
- super
- rust-client/packet.rs
- captcha_slider.rs
- NativeCore
- TunnelManager.kt
- dispatcher.rs
- sni.rs
- MaxAuthWebViewManager.kt
- super
- Events
- UdpSocket
- ImportExportDialog.kt
- wire_protocol.rs
- model.rs
- imp/mod.rs
- context
- .onCreate
- Inner
- CaptchaWebViewManager.kt
- TproxyStats
- DtlsClientHandshake
- rust-client/protocol.rs
- .push
- jitsi_room.rs
- direct_relay.rs
- ShutdownCoordinator
- DeviceQuotaRegistry
- resolveStoredSecret
- rust-server/packet.rs
- TurnCredentials
- clients_get
- Dispatcher
- jitsi_relay.rs
- PhysicalNetworkTracker
- NetworkCapture
- TokioIo
- ConnectionStartBlocker
- HotSession
- firefox/mod.rs
- TunnelService.kt
- DtlsServerHandshake
- ImpersonateOS
- .normalize
- T
- DeployResultPolicyTest
- DeployScriptPreflightTest
- proxy_route.rs
- handle_tcp_session
- udp.rs
- request_configuration
- BufferClassPool
- setup_nat_and_firewall
- performDeploy
- .onVpnRecoverableFailure
- Client
- tun.rs
- asserttrue
- .start_with_domain_routing
- device.rs
- InfoTab.kt
- .updateLog
- TunRecoveryPolicyTest
- VkHashValidationStatus
- linux_workers_hold_per_stream_rate_through_server_dataplane
- Participant
- runner.rs
- log_info
- DeployOperations.kt
- stack.rs
- Impersonate
- Test
- .onCreate
- MaxAuthLifecycleTest.kt
- ordering
- .create
- SessionRuntime
- edge/mod.rs
- log_warn
- Test
- .send_server_flight
- Extractor
- DirectRelay
- UplinkState
- TcpStream
- setup_letsencrypt_ip_tls
- Shared
- join_as
- lock_password_epoch
- opera/mod.rs
- stream_control_payload
- ServerApi
- ManagementUi.kt
- StackBuilder
- CaptchaSession
- MaxAuthSession
- FiftyStreamRig
- ControlEvent
- VkAuthWebViewManager.kt
- .enqueue_diagnostic
- resolveTunnelPasswordStorage
- TunnelStartupProgress
- VkAuthSession
- writer_loop
- LogLine.kt
- build_linux.sh
- DnsResolver
- AppUpdate.kt
- WorkerRecoveryPolicy
- MrttMaxToken
- .send_client_flight
- TurnStreamWriter
- .legacy
- .accept
- SshHostKeyStore.kt
- CaptchaSolver
- DebugRecorder
- IpPacket
- ExtractorOptions
- FirewallRule
- IpRule
- cached_system_stats
- DtlsCertificate
- EphemeralSecret
- DataplaneLogic
- PacketSink
- parseServerRoutingRule
- die
- SettingsStore.kt
- worker_loop
- run_max_webview_smoke.sh
- arc
- WifiAutoPausePolicy.kt
- TunnelDropdownSetting.kt
- QuickToggleTileService
- NetworkPauseGate
- MrttMotion
- jsonobject
- RecordProtector
- IpFilters<'a>
- .shouldStop
- newTunnelSessionId
- LocalProxyProfile
- ExtractorResult
- PacketBuffer
- ProtocolCommand
- pump_out
- VkRateLimitType
- authenticate_inbound
- MaxAuthWebViewSmokeTest.kt
- .new
- log_step
- RoomTarget
- CallPayload
- InfoTab
- MRTT / MAX (экспериментальная интеграция)
- wrap.rs
- native_client_provenance.ps1
- BatterySaverTest
- TurnSubnetRegistry
- AppSelectionOrderingTest
- Preferences
- RecordingWriter
- MaxAuthSessionTest.kt
- Formatter
- server_asset_provenance.ps1
- SrtpMode
- uuid
- CredentialBroker
- shouldRequestBatteryOptimizationExemption
- CsqttrMaxToken.csproj
- test_max_webview_theme.cjs
- TunWriteState
- TurnAllocateError
- .isAllowed
- TunnelAuthSnapshot
- .scrape
- VpnWidgetProvider.kt
- HandshakeReassembler
- DriverEvent
- TurnRequestError
- app/build.gradle.kts
- vkCookieDeletionHeaders
- isVkProbeHttpResponse
- SubscriptionsSection.kt
- Value
- PacketBuf
- process_packet
- csqtt-client
- MRTT
- .next
- run_wsl_checks.sh
- EventHandler
- TunnelQuickActions.kt
- shouldConfirmTcpTransport
- .onReceive
- DnsDomainMap
- resolveConnectionSource
- parseMrttLink
- TvManifestTest
- StreamDriverEvent
- flush_limited_relay_queue
- SshKeyRuntimeDependencyTest.kt
- FlowState
- DeployOutputLevel
- MrttExtendedColors.kt
- ServerArchitecture
- SettingsStoreInitOrderTest.kt
- Изменения в этом архиве
- UdpReceiveErrors
- TurnControlTransport
- mrtt
- TurnStreamFrame

## God Nodes (most connected - your core abstractions)
1. `SettingsStore` - 156 edges
2. `TunnelManager` - 92 edges
3. `TunnelService` - 76 edges
4. `Impersonate` - 70 edges
5. `ClientBuilder` - 66 edges
6. `ProtocolEngine` - 59 edges
7. `Dispatcher` - 57 edges
8. `WebState` - 53 edges
9. `ServerControl` - 50 edges
10. `parseMrttLink()` - 47 edges

## Surprising Connections (you probably didn't know these)
- `Сделано в этой партии (2.5.1)` --references--> `ConnectionStartPolicy`  [INFERRED]
  TODO_NEXT_UPDATE.md → app/src/main/java/com/mrtt/client/ConnectionStartPolicy.kt
- `Сделано в этой партии (2.5.1)` --references--> `TunnelService`  [INFERRED]
  TODO_NEXT_UPDATE.md → app/src/main/java/com/mrtt/client/TunnelService.kt
- `Возможные ошибки загрузки` --references--> `main()`  [INFERRED]
  GITHUB_ACTIONS_GUIDE_RU.md → scripts/make_mrtt_link.py
- `Шаг 3. Создать отдельную ветку ДО копирования` --references--> `main()`  [INFERRED]
  GITHUB_ACTIONS_GUIDE_RU.md → scripts/make_mrtt_link.py
- `Шаг 5. Коммит и отправка ветки на GitHub` --references--> `main()`  [INFERRED]
  GITHUB_ACTIONS_GUIDE_RU.md → scripts/make_mrtt_link.py

## Import Cycles
- 1-file cycle: `rust-client/tun.rs -> rust-client/tun.rs`
- 1-file cycle: `shared/spsc_ring.rs -> shared/spsc_ring.rs`
- 1-file cycle: `rust-server/tproxy.rs -> rust-server/tproxy.rs`
- 1-file cycle: `rust-server/proxy_route.rs -> rust-server/proxy_route.rs`
- 1-file cycle: `rust-server/main.rs -> rust-server/main.rs`
- 1-file cycle: `rust-server/model.rs -> rust-server/model.rs`
- 1-file cycle: `rust-server/web_panel.rs -> rust-server/web_panel.rs`
- 2-file cycle: `rust-server/main.rs -> rust-server/protocol.rs -> rust-server/main.rs`
- 2-file cycle: `rust-client/auth.rs -> rust-client/vk_js_calls.rs -> rust-client/auth.rs`

## Communities (266 total, 66 thin omitted)

### Community 0 - "rust-server/protocol.rs"
Cohesion: 0.02
Nodes (74): ACTIVE_SESSIONS_GAUGE, CACHED_MONOTONIC_MS, CONTROL_EVENT_CAPACITY, CONTROL_TASK_CAPACITY, CRYPTO_OPS_COUNTER, DB_LOCK_TIMEOUT, DecodedPacket, DEVICE_KEY_SEPARATOR (+66 more)

### Community 1 - "TunnelService"
Cohesion: 0.08
Nodes (5): Network, isEligiblePhysicalNetwork(), ResolvedVkHashes, TunnelService, NetworkCallback

### Community 2 - "turn_core.rs"
Cohesion: 0.02
Nodes (84): ALLOCATION_TIMEOUT, CLEANUP_ERROR_RETRY, CLEANUP_TIMEOUT, CONTROL_MAX, CONTROL_TIMEOUT, AF_IPV4, AF_IPV6, ALLOCATION_REFRESH (+76 more)

### Community 3 - "dtls.rs"
Cohesion: 0.02
Nodes (72): ALERT_BAD_RECORD_MAC, ALERT_CLOSE_NOTIFY, ALERT_DECODE_ERROR, ALERT_DECRYPT_ERROR, ALERT_HANDSHAKE_FAILURE, ALERT_ILLEGAL_PARAMETER, ALERT_LEVEL_FATAL, ALERT_LEVEL_WARNING (+64 more)

### Community 5 - "ServersTab.kt"
Cohesion: 0.08
Nodes (10): BatterySaver, MrttApplication, ServerRepository, VpsServer, DeployProgressCard(), runDeployJob(), ServersTab(), Spacer24() (+2 more)

### Community 6 - "logging.rs"
Cohesion: 0.09
Nodes (13): COALESCE_BATCH, DIAGNOSTIC_CAPACITY, GateWriter, Logger, LOGGER, log_error!, log_info!, log_output! (+5 more)

### Community 7 - "rust-server/main.rs"
Cohesion: 0.04
Nodes (50): acquire_instance_lock(), App, append_utf8_prefix(), Args, arm_tproxy_child_parent_death(), async_main(), auto_restart_loop(), cpu_loop() (+42 more)

### Community 8 - "SettingsTab.kt"
Cohesion: 0.06
Nodes (33): appSectionCardBorderColor(), appSectionCardColor(), AppUpdateDialog(), AutoVkRiskDialog(), MaxTransportRiskDialog(), TcpTransportRiskDialog(), TimedWarningDialog(), BatterySaverInfoDialog() (+25 more)

### Community 9 - "ClientBuilder"
Cohesion: 0.04
Nodes (3): apply_http2_settings(), apply_impersonation(), ClientBuilder

### Community 10 - "MainActivity.kt"
Cohesion: 0.03
Nodes (28): updateIntervalHoursToMillis(), Captcha, General, Links, MrttConstants, Notifications, Patterns, Profiles (+20 more)

### Community 11 - "ExceptionsTab.kt"
Cohesion: 0.04
Nodes (14): MrttScreen(), ConnectionTab(), formatTunnelUptime(), TunnelConnectionPanel(), TunnelUptimeText(), ViewOutlineProvider, RoutingMode, ExcludeSelected (+6 more)

### Community 12 - "TunnelLifecycleState"
Cohesion: 0.08
Nodes (8): nextTunnelGenerationId(), reserveTunnelGenerationId(), TunnelLifecycleState, TunnelLifecycleTicket, Model, ModelTicket, TunnelLifecycleStateChaosTest, TunnelLifecycleStateTest

### Community 13 - "TunnelWorkersSlider.kt"
Cohesion: 0.07
Nodes (4): MrttOrb(), CompactSteppedSlider(), roundToGroup(), TunnelWorkersControl()

### Community 14 - "rust-client/main.rs"
Cohesion: 0.04
Nodes (33): bootstrap_keeps_control_lines_that_follow_it(), build_runtime(), EXIT_FATAL, EXIT_NORMAL, EXIT_TUN_LOST, invalid_totals_never_create_partial_or_excess_group(), leave_js_creator_after_ready_workers(), main() (+25 more)

### Community 15 - "auth.rs"
Cohesion: 0.06
Nodes (52): browser_client_for_profile(), browser_headers(), browser_headers_omit_user_agent_when_profile_has_no_override(), browser_identity(), build_browser_client(), BUILTIN_VK_CREDENTIALS, captcha_client_for_profile(), check_fatal_call() (+44 more)

### Community 16 - "session.rs"
Cohesion: 0.04
Nodes (44): await_session_task(), cancellation_aborts_and_awaits_pending_session_startup(), cancelled_session_aborts_every_child_task(), completed_reader_is_not_polled_twice(), completed_writer_is_not_polled_twice(), CONFIG_RESPONSE_TIMEOUT_MS, ConfigDeliveryState, configuration_timeout_is_recoverable() (+36 more)

### Community 17 - "web_panel.rs"
Cohesion: 0.05
Nodes (97): log_event(), api_client_vk_update(), api_clients_batch_delete(), api_clients_batch_extend(), api_clients_batch_group(), api_clients_batch_reset_traffic(), api_clients_batch_toggle(), api_diagnostics_dpi() (+89 more)

### Community 18 - "tun_device.rs"
Cohesion: 0.06
Nodes (44): extract_dst_ipv4(), BULK_HOLD, ClientRoute, compacted_session_slot_is_updated(), downlink_bulk_udp_with_small_packets_is_not_pinned(), downlink_cursor_round_robin_rotates_streams(), downlink_game_flow_with_large_packets_stays_pinned(), downlink_new_tcp_flows_rotate_without_tick_pinning() (+36 more)

### Community 21 - "TunnelManager"
Cohesion: 0.13
Nodes (3): ProcessIdentity, TunnelManager, TunnelParams

### Community 22 - "turn_stream.rs"
Cohesion: 0.09
Nodes (13): AsyncTurnStream, connect(), CONTROL_QUEUE_CAPACITY, DATA_QUEUE_CAPACITY, MAX_CONTROL_FRAME_BYTES, MAX_DATA_FRAME_BYTES, STREAM_CONNECT_TIMEOUT, STREAM_READ_BUFFER_BYTES (+5 more)

### Community 23 - "obfs.rs"
Cohesion: 0.09
Nodes (30): aes_ctr(), audio_roundtrip(), authenticated_audio_region_rejects_every_single_byte_tamper(), duration_ticks(), empty_payload_returns_error(), large_packet_padding_respects_mtu_boundary(), sized_roundtrip_case!, ObfsCipher (+22 more)

### Community 24 - "turn_integration_tests.rs"
Cohesion: 0.05
Nodes (50): run_session(), ALLOCATE_ERROR, ALLOCATE_REQUEST, ALLOCATE_SUCCESS, apply_e2e_dns(), assert_fatal_auth_case(), ATTR_CHANNEL_NUMBER, ATTR_ERROR_CODE (+42 more)

### Community 25 - "vk_js_calls.rs"
Cohesion: 0.10
Nodes (30): open_max_session(), start(), account_credentials_payload_matches_sdk_request(), ActiveCalls, ApiSession, Bootstrap, chained_api_error_exposes_code_and_message(), check_api_error() (+22 more)

### Community 26 - "tproxy.rs"
Cohesion: 0.03
Nodes (63): allowed_client(), ASSOCIATION_BACKOFF, ASSOCIATION_POOL_TARGET, bind_sockets(), bind_tcp_listener(), calc_checksum(), DEVICE_JUMBO_BUFFER_LIMIT, DEVICE_NORMAL_BUFFER_LIMIT (+55 more)

### Community 27 - "worker.rs"
Cohesion: 0.05
Nodes (41): cancelled_queued_reconnect_does_not_consume_the_next_slot(), cancelled_worker_start_does_not_wait_for_a_reserved_slot(), contains_os_error_code(), credential_cohort_index(), CREDENTIAL_FETCH_TIMEOUT, CREDENTIAL_POST_DELAY, every_credential_cohort_is_dedicated_to_one_complete_group(), FATAL_RETRY_BASE (+33 more)

### Community 28 - "tcp.rs"
Cohesion: 0.09
Nodes (6): TcpSocketControl, TcpSocketState, Close, Closed, Closing, Normal

### Community 29 - "TurnAllocation"
Cohesion: 0.06
Nodes (27): channel_data_padding(), channel_encoding_reuses_storage_and_zeroes_padding(), cleanup_limiter(), Completion, completion_sequences_are_independent(), CoreSnapshot, driver_loop(), driver_loop_stream() (+19 more)

### Community 30 - "dns.rs"
Cohesion: 0.09
Nodes (32): answer_packet(), build_query(), DNS_A_RECORD, DNS_AAAA_RECORD, DNS_CACHE_MAX_ENTRIES, DNS_CACHE_MAX_TTL, DNS_TIERS, DNS_TIMEOUT (+24 more)

### Community 31 - ".parse"
Cohesion: 0.11
Nodes (21): ActiveZero, CallUnavailable, CaptchaDone, CaptchaRequest, Config, Dtls, Error, Event (+13 more)

### Community 32 - "deploy.sh"
Cohesion: 0.04
Nodes (53): all_mrtt_process_pids(), DEBIAN_FRONTEND, DEPLOY_MODE, DOCKER_BUILD_TIMEOUT_SECONDS, EXIT_CUTOVER_FAILED, EXIT_INVALID_ARGUMENT, EXIT_PREFLIGHT_FAILED, IPT_COMMENT (+45 more)

### Community 33 - "dataplane.rs"
Cohesion: 0.07
Nodes (26): COMMAND_DRAIN_LIMIT, DATAPLANE_STACK_BYTES, DataplaneConfig, DataplaneHandle, DataplaneHandle<C>, DataplaneRuntime, DataplaneRuntime<C>, decrement_queue_len() (+18 more)

### Community 34 - ".get"
Cohesion: 0.14
Nodes (13): display_control_payload(), EndpointKey, hex_and_ascii_dump(), insert_bounded_endpoint(), is_control_payload(), is_dpi_control(), is_idle_keepalive(), parse_generation_id() (+5 more)

### Community 35 - "ProtocolEngine"
Cohesion: 0.11
Nodes (11): CredentialSet, EpochValue, hot_session_idle_reason(), latest_engine_state(), ProtocolEngine, publish_epochs_snapshot(), session_is_disconnected_password(), session_is_retired_by_epoch() (+3 more)

### Community 36 - "captcha.rs"
Cohesion: 0.09
Nodes (30): accepts_partial_window_init_like_go_json(), ALLOWED_CAPTCHA_DOMAINS, API_VERSION, base_values(), CaptchaCheck, CaptchaInit, CaptchaInitData, CaptchaPage (+22 more)

### Community 37 - "safari/mod.rs"
Cohesion: 0.06
Nodes (37): BrowserLeaksResponse, build_http2_settings(), build_safari_base_headers(), build_safari_settings(), build_user_agent(), new_safari_18_5_emulator(), new_safari_26_3_ios_emulator(), new_safari_26_3_macos_emulator() (+29 more)

### Community 38 - "tokio_io.rs"
Cohesion: 0.06
Nodes (25): packet_pool_size(), cmsg_align(), configured_udp_datagram_mode(), create_tun_device(), fair_share_allocation_and_control_slot_reservation(), FIREWALL_REMOVE_PASSES, is_fatal_tun_write_error(), is_tun_create_retryable() (+17 more)

### Community 39 - "turn_ip_pool.rs"
Cohesion: 0.15
Nodes (16): auto_mode_returns_none_for_workers(), current_time_ms(), distributes_evenly_across_healthy_ips(), FAILURE_COOLDOWN_MS, fallback_to_none_when_all_fail(), HEALTHY_STATE, mark_healthy_restores_endpoint(), parse_custom_turn_ips() (+8 more)

### Community 40 - "TrafficSnapshot"
Cohesion: 0.13
Nodes (18): should_emit(), client_group_vk_settings_persist_across_restarts(), concurrent_database_mutations_persist_the_highest_revision(), database_persistence_finishes_with_latest_coalesced_snapshot(), database_persistence_worker(), DatabasePersistence, DatabasePersistenceInner, DatabasePersistenceState (+10 more)

### Community 41 - "App"
Cohesion: 0.09
Nodes (54): lock_unpoison(), broadcast_tunnel_configuration(), build_credentials(), command(), compact_memory(), control_loop(), ControlWriter, CredentialAccess (+46 more)

### Community 42 - "super"
Cohesion: 0.11
Nodes (12): ATTR_FINGERPRINT, Attribute, Attributes, Class, Error, Indication, Success, decodes_pion_allocate_vector() (+4 more)

### Community 43 - "rust-client/packet.rs"
Cohesion: 0.08
Nodes (14): concurrent_deficit_never_exceeds_packet_budget_or_leaks(), exact_headroom_and_tail_capacity_are_accepted(), headroom_and_pool_reuse(), invalid_ranges_return_errors(), large_pool_retains_only_bounded_idle_storage(), maximum_read_area_is_accepted(), packet_budget_is_strict_and_recovers_after_release(), PACKET_CAPACITY (+6 more)

### Community 44 - "captcha_slider.rs"
Cohesion: 0.14
Nodes (18): apply_swaps(), build_cursor(), cursor_has_human_path(), CursorPoint, luma(), MAX_SLIDER_GRID, parse_number(), parse_puzzle() (+10 more)

### Community 45 - "NativeCore"
Cohesion: 0.14
Nodes (28): authenticated_allocation_mismatch_destroys_allocation(), bind_ready_channel(), channel_bind_bad_request_destroys_allocation(), channel_bind_success_does_not_change_maintenance_deadline(), complete_empty_success(), Control, decode_xor_address(), encode_xor_address() (+20 more)

### Community 46 - "TunnelManager.kt"
Cohesion: 0.07
Nodes (16): FullLog, TunnelLogPolicy, displayVkHash(), LogEntry, LogLevel, DBG, ERR, LOG (+8 more)

### Community 47 - "dispatcher.rs"
Cohesion: 0.05
Nodes (35): ADAPTIVE_ADJUST_INTERVAL, ADAPTIVE_DECREASE_FACTOR, ADAPTIVE_INCREASE_BYTES_PER_SEC, ADAPTIVE_MAX_RATE_BYTES_PER_SEC, ADAPTIVE_MIN_RATE_BYTES_PER_SEC, BULK_HOLD, FLOW_BURST_BYTES, FLOW_EVICT_EVERY (+27 more)

### Community 48 - "sni.rs"
Cohesion: 0.09
Nodes (23): extract_dns_info(), extract_dst_addr(), extract_src_addr(), extract_tls_sni(), Cidr, Single, parse_direct_domains(), parse_ip_packet_dns() (+15 more)

### Community 49 - "MaxAuthWebViewManager.kt"
Cohesion: 0.15
Nodes (4): WebChromeClient, WebViewClient, MaxAuthWebViewManager, MaxAuthDialog()

### Community 50 - "super"
Cohesion: 0.09
Nodes (7): run(), fetch_call_token(), MAX_API_KEY, MAX_APP_VERSION, MAX_CALLS_API, MAX_ORIGIN, ONEME_WS_URL

### Community 51 - "Events"
Cohesion: 0.08
Nodes (11): call_unavailable_is_deduplicated_per_hash(), disabled_events_do_not_build_payloads(), encode_event(), EVENT_PREFIX, Events, network_suspect_requires_a_timeout_burst_and_resets_after_recovery(), NETWORK_TIMEOUT_BURST_WINDOW, panel_restart_is_deduplicated_across_workers() (+3 more)

### Community 52 - "UdpSocket"
Cohesion: 0.18
Nodes (28): allocate_success(), assert_authenticated_request(), assert_fingerprint(), assert_message_integrity(), attribute_u32(), authenticated_error(), authenticated_flow_survives_pool_deficit_and_keeps_channel_data_zero_copy(), cancelled_connect_deallocates_server_allocation_after_late_success() (+20 more)

### Community 53 - "ImportExportDialog.kt"
Cohesion: 0.08
Nodes (6): MrttSegmentedControl(), MrttSettingRow(), ImportExportTab, EXPORT, IMPORT, saveJsonToDownloads()

### Community 54 - "wire_protocol.rs"
Cohesion: 0.09
Nodes (25): parse_path_probe(), PATH_PROBE_PREFIX, PathProbe, CLIENT_GETCONF_AUDIO_FIXTURE, FRAG_HEADER_LEN, FragHeader, fragment_packet(), MAX_FRAG_PAYLOAD (+17 more)

### Community 55 - "model.rs"
Cohesion: 0.05
Nodes (68): admit_device(), auto_restart_interval_defaults_to_disabled_and_persists_disabled(), CACHED_NOW, ClientGroup, Database, DATABASE_FILE, DATABASE_SAVE_LOCK, DATABASE_SCHEMA (+60 more)

### Community 56 - "imp/mod.rs"
Cohesion: 0.07
Nodes (22): BrowserSettings, CHROME_HEADER_TABLE_SIZE, CHROME_INITIAL_CONNECTION_WINDOW, CHROME_INITIAL_STREAM_WINDOW, CHROME_MAX_HEADER_LIST_SIZE, FIREFOX_HEADER_TABLE_SIZE, FIREFOX_INITIAL_CONNECTION_WINDOW, FIREFOX_INITIAL_STREAM_WINDOW (+14 more)

### Community 57 - "context"
Cohesion: 0.07
Nodes (30): turn_path_key(), configure_udp_socket_buffers(), connect_transport(), is_supported_turn_uri(), parse_authority(), parse_turn_uri(), resolve_turn_endpoints(), tcp_mode_uses_raw_tcp_for_turn_uris() (+22 more)

### Community 58 - ".onCreate"
Cohesion: 0.08
Nodes (6): ManlCaptchaActivity, object@L178, WebViewClient, shouldDiscardStaleSavedState(), UpdateLaunchGuard, UpdateLaunchGuardTest

### Community 59 - "Inner"
Cohesion: 0.10
Nodes (17): Message, allocation_refresh_deadline(), attribute_u32(), error_code(), Inner, NativeAddress, NativeEvent, Operation (+9 more)

### Community 60 - "CaptchaWebViewManager.kt"
Cohesion: 0.10
Nodes (4): CaptchaJSBridge, CaptchaWebViewManager, WebViewClient, Runnable

### Community 61 - "TproxyStats"
Cohesion: 0.12
Nodes (8): request_tproxy_status(), evict_oldest_udp_flow(), ProxyBufferPoolSnapshot, runtime_stats_track_active_peak_and_total(), TcpActiveGuard, TproxyBufferPools, TproxyStats, TproxyStatsSnapshot

### Community 62 - "DtlsClientHandshake"
Cohesion: 0.07
Nodes (18): handshake_message_label(), dtls_state_label(), certificate_p256_public_key(), DtlsClientHandshake, DtlsHandshakeState, AwaitingClientFlight, AwaitingServerFinished, AwaitingServerFlight (+10 more)

### Community 63 - "rust-client/protocol.rs"
Cohesion: 0.09
Nodes (17): auth_denied_reasons_distinguish_fatal_from_retryable(), config_request(), ConfigResponse, Config, NoConfig, device_fields(), MAX_STREAM_WORKERS, PANEL_RESTART_NOTICE (+9 more)

### Community 64 - ".push"
Cohesion: 0.13
Nodes (16): a_forged_server_flight_is_rejected(), dtls12_client_hello_matches_boring_ssl_calls_wire_format(), dtls12_client_hello_rebuild_keeps_identical_format(), dtls12_truncated_client_hello_is_rejected(), encode_u16_list(), extension_helpers_update_in_place(), extract_client_hello_random_matches(), fill_random_bytes() (+8 more)

### Community 65 - "jitsi_room.rs"
Cohesion: 0.06
Nodes (22): accept_answers_every_content_without_foreign_sources(), accept_contents(), BASE64, BATCH_BYTES, CHANNEL_WAIT, CLIENT_HELLO, DOCKER_DOMAIN, FALLBACK_RESOLVER (+14 more)

### Community 66 - "direct_relay.rs"
Cohesion: 0.10
Nodes (22): build_tcp_packet(), build_udp_packet(), craft_tcp_packet_into(), craft_udp_packet_into(), DIRECT_RELAY_INBOUND_CAPACITY, dns_query_slots(), ip_checksum(), MAX_CONCURRENT_DNS_QUERIES (+14 more)

### Community 67 - "ShutdownCoordinator"
Cohesion: 0.14
Nodes (12): disconnect_request(), NEXT_INCARNATION_ID, send_writer_bytes(), shutdown_never_uses_a_dropped_control_stream(), shutdown_uses_the_most_recently_active_control_stream_once(), ShutdownControlStream, ShutdownCoordinator, ShutdownRegistration (+4 more)

### Community 68 - "DeviceQuotaRegistry"
Cohesion: 0.10
Nodes (13): acquire_tcp_quota(), acquire_udp_quota(), adaptive_device_limit(), AdmissionError, DeviceLimit, GlobalLimit, Inactive, AdmissionState (+5 more)

### Community 69 - "resolveStoredSecret"
Cohesion: 0.06
Nodes (21): SecureStringStore, resolveStoredSecret(), StoredSecret, StoredSecretState, Missing, Readable, Unreadable, StoredSecretStateTest (+13 more)

### Community 70 - "rust-server/packet.rs"
Cohesion: 0.10
Nodes (13): PACKET_CAPACITY, packet_pool_discards_excess_idle_storage(), packet_pool_is_bounded_and_recovers_buffers(), PACKET_POOL_RETAINED_MAX, PacketBuf, PacketPool, PacketPoolSnapshot, socket_addr_to_storage() (+5 more)

### Community 71 - "TurnCredentials"
Cohesion: 0.12
Nodes (17): allocation_mismatch_retries_once_then_rotates_and_resets_after_allocate(), AllocationMismatch, Replaced, RetryCurrent, Rotated, CachedCredentials, credential_cache_never_reuses_a_credential_after_nine_allocate_attempts(), CredentialCache (+9 more)

### Community 72 - "clients_get"
Cohesion: 0.14
Nodes (24): BoundDevice, is_expired(), now(), PasswordEntry, refresh_cached_now(), api_links_get(), client_get(), ClientInfo (+16 more)

### Community 73 - "Dispatcher"
Cohesion: 0.10
Nodes (6): Dispatcher, spawn_critical(), Termination, Fatal, TunLost, WorkerChannels

### Community 74 - "jitsi_relay.rs"
Cohesion: 0.12
Nodes (12): Config, CONFIG, configure(), DATAGRAM_MAX, Flow, IDLE_FORGET, REJOIN_MAX, REJOIN_MIN (+4 more)

### Community 75 - "PhysicalNetworkTracker"
Cohesion: 0.13
Nodes (10): PhysicalNetworkPauseReason, AIRPLANE_MODE, OFFLINE, PhysicalNetworkTracker, PhysicalNetworkTransition, AVAILABLE, CHANGED, NONE (+2 more)

### Community 76 - "NetworkCapture"
Cohesion: 0.08
Nodes (19): IceServer, Credential, Urls, Username, NetworkCapture, ApiServer, CapturedAt, ConversationId (+11 more)

### Community 77 - "TokioIo"
Cohesion: 0.12
Nodes (7): ControlBuffer, recvmsg_preserves_udp_payload_and_peer_address(), RxOutcome, Batch, Drained, TokioIo, UdpTxSlot

### Community 78 - "ConnectionStartBlocker"
Cohesion: 0.15
Nodes (10): ConnectionStartBlocker, Hashes, Link, NoProfile, Password, Port, Server, StoredSecret (+2 more)

### Community 79 - "HotSession"
Cohesion: 0.08
Nodes (26): derive_wrap_key(), aes_ctr(), build_srtp_iv(), client_getconf_fixture_authenticates_with_matching_password_only(), client_getconf_fixture_rejects_every_single_bit_wire_mutation(), Credential, DownlinkSend, Backpressured (+18 more)

### Community 80 - "firefox/mod.rs"
Cohesion: 0.10
Nodes (18): BrowserLeaksResponse, build_firefox_settings(), build_headers(), build_http2_settings(), build_user_agent(), FIREFOX140_AKAMAI_HASH, FIREFOX140_AKAMAI_TEXT, FIREFOX140_JA4 (+10 more)

### Community 82 - "DtlsServerHandshake"
Cohesion: 0.19
Nodes (6): a_pinned_fingerprint_that_does_not_match_is_rejected(), complete_handshake(), DtlsServerHandshake, RecordKeys, rfc5764_dtls_srtp_key_exchange_roundtrip(), rfc5764_dtls_srtp_key_exchange_with_app_data_roundtrip()

### Community 83 - "ImpersonateOS"
Cohesion: 0.16
Nodes (24): selected_profiles_keep_tls_and_http_identity_aligned(), chrome_pool(), chrome_profiles_produce_diverse_versions_and_os_combinations(), default_fingerprint_is_chrome(), edge_pool(), edge_profiles_produce_diverse_combinations(), firefox_pool(), firefox_profiles_produce_diverse_combinations() (+16 more)

### Community 85 - "T"
Cohesion: 0.15
Nodes (16): T, advance(), channel(), notification_wakes_a_rearmed_empty_receiver(), preserves_fifo_and_capacity(), rearm_race_keeps_the_already_queued_value_visible(), Ring, Ring<T> (+8 more)

### Community 86 - "DeployResultPolicyTest"
Cohesion: 0.19
Nodes (6): deployFailureMessage(), DeployOutputLine, friendlyDeployError(), parseDeployOutputLine(), serverArchitectureForMachine(), DeployResultPolicyTest

### Community 88 - "proxy_route.rs"
Cohesion: 0.05
Nodes (60): activate_tproxy(), ACTIVE_RUNTIME, add_tproxy_rules(), add_tproxy_shared_rules(), append_socks_address(), cleanup_all_nat_exemptions(), cleanup_legacy_proxy_policy(), cleanup_legacy_quic_rule() (+52 more)

### Community 89 - "handle_tcp_session"
Cohesion: 0.11
Nodes (12): Association, create_association(), handle_tcp_session(), now_ms(), relay_direction(), session_expired(), spawn_refill(), TcpEvictionSlot (+4 more)

### Community 90 - "udp.rs"
Cohesion: 0.16
Nodes (3): ReadHalf, UdpSocket, WriteHalf

### Community 91 - "request_configuration"
Cohesion: 0.12
Nodes (17): SessionCipher, Obfs, Plain, cipher_pair(), handle_writer_command(), install_cipher_pair(), install_session_ciphers(), run_allocated_session() (+9 more)

### Community 92 - "BufferClassPool"
Cohesion: 0.08
Nodes (16): BufferClass, Jumbo, Normal, Tcp, BufferClassPool, opening_packets_have_a_fixed_global_ceiling(), OpeningBufferLease, OpeningBufferPool (+8 more)

### Community 93 - "setup_nat_and_firewall"
Cohesion: 0.28
Nodes (9): detect_wan_interface(), fw_add_forward(), fw_add_input_tcp(), fw_add_input_udp(), fw_add_masquerade(), fw_add_mss_clamping(), ipt_add_or_ensure(), is_ignored_wan_interface() (+1 more)

### Community 94 - "performDeploy"
Cohesion: 0.18
Nodes (7): DeployManager, createSSHClient(), DeploySSHClient, performDeploy(), performUninstall(), publishDeployOutput(), rootCommand()

### Community 95 - ".onVpnRecoverableFailure"
Cohesion: 0.18
Nodes (7): VpnFailureKind, PermissionRevoked, ServiceDestroyed, TunEstablishFailed, TunFdDeliveryFailed, VpnRecoveryPolicy, VpnRecoveryPolicyTest

### Community 96 - "Client"
Cohesion: 0.10
Nodes (4): Request, Client, get(), run()

### Community 97 - "tun.rs"
Cohesion: 0.12
Nodes (5): configure_nonblocking(), FdReceiver, persistent_receiver_accepts_a_replacement_fd(), receive_ack(), send_fd()

### Community 99 - ".start_with_domain_routing"
Cohesion: 0.19
Nodes (6): bind_udp(), is_dead_tun_error(), is_retryable_tun_error(), is_transient_tun_error(), log_dropped_packet(), tun_retry_backoff()

### Community 100 - "device.rs"
Cohesion: 0.14
Nodes (6): ingress_pending_while_a_kept_frame_waits_for_egress(), receive_keeps_in_buf_packet_when_out_buf_is_full(), VirtualDevice, VirtualRxToken, VirtualTxToken, VirtualTxToken<'a>

### Community 101 - "InfoTab.kt"
Cohesion: 0.09
Nodes (9): MrttColors, MrttGlassButton(), MrttShapes, MrttSizes, copyAddressToClipboard(), openBrowserLink(), measureSubnetRtt(), probeStunRtt() (+1 more)

### Community 102 - ".updateLog"
Cohesion: 0.12
Nodes (3): dnsProfileName(), TunVpnService, DnsProfileNameTest

### Community 104 - "VkHashValidationStatus"
Cohesion: 0.13
Nodes (6): VkHashValidationCodec, VkHashValidationStatus, Invalid, Valid, VkHashValidator, VkHashValidatorTest

### Community 105 - "linux_workers_hold_per_stream_rate_through_server_dataplane"
Cohesion: 0.16
Nodes (13): e2e_env_u64(), e2e_process_cpu_seconds(), e2e_thread_cpu_seconds(), e2e_tunnel_ip(), E2eTunResult, E2eTunRuntime, LimitedRelayMeter, LimitedRelayRuntime (+5 more)

### Community 106 - "Participant"
Cohesion: 0.18
Nodes (5): pump_back(), join(), Participant, peers_in(), RoomSender

### Community 107 - "runner.rs"
Cohesion: 0.19
Nodes (3): BoxFuture, BoxFuture<'a, T>, BoxFuture<'_, T>

### Community 108 - "log_info"
Cohesion: 0.13
Nodes (22): check_root(), detect_firewall(), detect_os(), do_status(), docker_build_candidate_image(), install_network_helper(), log_info(), main() (+14 more)

### Community 109 - "DeployOperations.kt"
Cohesion: 0.10
Nodes (12): deployAssetLabel(), deployEnvironmentValue(), deployFailureDetail(), deployMode(), dockerEnvironmentValue(), ensureBouncyCastle(), isSuccessfulDeployResult(), readableFileSize() (+4 more)

### Community 110 - "stack.rs"
Cohesion: 0.18
Nodes (3): Sink, channel_closed_err(), Stack

### Community 111 - "Impersonate"
Cohesion: 0.05
Nodes (44): base_chrome_headers(), BrowserLeaksResponse, build_chrome_settings(), build_http2_settings(), build_sec_ch_ua(), build_user_agent(), CHROME146_AKAMAI_HASH, CHROME146_AKAMAI_TEXT (+36 more)

### Community 112 - "Test"
Cohesion: 0.22
Nodes (7): normalizeVkModeSelection(), shouldConfirmAutoJsMode(), shouldReplaceUnavailableVkHash(), vkAuthModeForHashMode(), vkHashModeForAuthMode(), VkModeSelection, VkModePolicyTest

### Community 113 - ".onCreate"
Cohesion: 0.14
Nodes (3): DomainRoutingUpdate, BroadcastReceiver, BroadcastReceiver

### Community 115 - "ordering"
Cohesion: 0.17
Nodes (13): parse_stream_alive(), parse_stream_command(), parse_stream_repair(), StreamRepairCommand, command(), duplicate_repair_sequence_is_ignored(), invalid_worker_ids_are_ignored(), ready_or_alive_sequence_prevents_credential_escalation() (+5 more)

### Community 116 - ".create"
Cohesion: 0.23
Nodes (3): TcpListener, TcpListenerRunner, TcpSocketCreation

### Community 117 - "SessionRuntime"
Cohesion: 0.17
Nodes (15): is_config_response(), is_control_response(), is_panel_restart_notice(), apply_workers_limit(), is_ip_packet(), is_plain_ip_packet(), parse_workers_from_tunconf(), reader_loop() (+7 more)

### Community 118 - "edge/mod.rs"
Cohesion: 0.14
Nodes (14): base_edge_headers(), BrowserLeaksResponse, build_edge_settings(), build_http2_settings(), build_sec_ch_ua(), build_user_agent(), EDGE146_AKAMAI_HASH, EDGE146_AKAMAI_TEXT (+6 more)

### Community 119 - "log_warn"
Cohesion: 0.14
Nodes (24): assert_peer_port_is_available(), cleanup_legacy_proxy_interfaces(), cleanup_mrtt_netfilter_rules(), clear_runtime_config_preserving_database(), delete_marked_rules(), do_uninstall(), docker_container_for_pid(), docker_container_runs_mrtt() (+16 more)

### Community 120 - "Test"
Cohesion: 0.22
Nodes (3): dispatchTunnelStop(), effectiveWifiAutoPauseConnection(), WifiAutoPausePolicyTest

### Community 121 - ".send_server_flight"
Cohesion: 0.22
Nodes (10): choose_group(), choose_srtp_profile(), ClientHello, decode_u16_list(), decode_use_srtp(), Extension, has_extension(), parse_client_use_srtp() (+2 more)

### Community 123 - "DirectRelay"
Cohesion: 0.21
Nodes (7): DirectRelay, is_local_or_private_ip(), resolve_direct_domain(), run_full_preresolve_task(), test_direct_relay_bypasses_icmp_cleanly(), test_run_full_preresolve_task_preserves_dynamic_ips(), DomainRoutingConfig

### Community 124 - "UplinkState"
Cohesion: 0.16
Nodes (12): adaptive_rate_adjusts_at_most_once_per_interval(), adaptive_rate_backs_off_on_congestion_and_recovers(), adaptive_token_refill_uses_per_stream_rate(), flow_key(), FlowKey, V4, V6, is_small_udp() (+4 more)

### Community 125 - "TcpStream"
Cohesion: 0.18
Nodes (3): recv_stream_channel_data(), recv_stream_message(), TcpStream

### Community 126 - "setup_letsencrypt_ip_tls"
Cohesion: 0.19
Nodes (16): adopt_letsencrypt_certificate(), certificate_is_mrtt_letsencrypt(), certificate_is_mrtt_rcgen(), detect_public_ipv4(), disable_letsencrypt_renewal_timer(), generate_rcgen_fallback_certificate(), install_letsencrypt_renewal_timer(), is_public_ipv4() (+8 more)

### Community 127 - "Shared"
Cohesion: 0.25
Nodes (3): LogLine, Shared, writer_loop()

### Community 128 - "join_as"
Cohesion: 0.26
Nodes (10): answer_iq(), attr(), bridge_socket_url(), elements(), inner_text(), join_as(), offer_from_jingle(), offer_keeps_only_data_and_udp_candidates() (+2 more)

### Community 129 - "lock_password_epoch"
Cohesion: 0.13
Nodes (16): concurrent_session_admission_accepts_each_new_session_key(), CREDENTIALS_SNAPSHOT, current_password_epoch(), disconnect_request_authorized(), EPOCHS_SNAPSHOT, lock_existing_password_epoch(), lock_password_epoch(), PasswordEpochDecision (+8 more)

### Community 130 - "opera/mod.rs"
Cohesion: 0.14
Nodes (14): base_opera_headers(), BrowserLeaksResponse, build_http2_settings(), build_opera_settings(), build_sec_ch_ua(), build_user_agent(), new_opera_emulator(), OPERA129_AKAMAI_HASH (+6 more)

### Community 131 - "stream_control_payload"
Cohesion: 0.24
Nodes (8): stream_control_payload(), stream_control_payload_caps_untrusted_worker_list_at_u8_limit(), stream_control_payload_is_compact_and_deterministic(), stream_control_payload_represents_all_supported_workers(), stream_repair_round_deduplicates_and_escalates_by_sequence(), stream_repair_round_emits_alive_after_recovery(), StreamRepairRound, ten_stream_repair_rounds_converge_without_cross_client_state()

### Community 132 - "ServerApi"
Cohesion: 0.13
Nodes (4): ServerApi, ServerApiException, ServerNotRunningException, ServerApiResponseTest

### Community 133 - "ManagementUi.kt"
Cohesion: 0.07
Nodes (10): showRaisedToast(), AppItem, AppRow(), selectedApplicationsFirst(), logFileName(), shareLogFile(), ActionRunner, copyToClipboard() (+2 more)

### Community 134 - "StackBuilder"
Cohesion: 0.13
Nodes (3): IpFilters, StackBuilder, tcp_window_defaults_to_historical_and_threads_overrides()

### Community 135 - "CaptchaSession"
Cohesion: 0.24
Nodes (6): CaptchaSession, insert_header(), slider_check(), slider_request(), slider_saved_profile(), solve_slider()

### Community 137 - "FiftyStreamRig"
Cohesion: 0.33
Nodes (17): assert_fifty_stream_healthy(), collect_fifty_stream_report(), dispatch_one(), FiftyStreamReport, FiftyStreamRig, print_fifty_stream_report(), pump_bursts(), pump_constant() (+9 more)

### Community 138 - "ControlEvent"
Cohesion: 0.15
Nodes (14): client_fixture_creates_hot_session_and_control_event_through_engine(), client_fixture_with_wrong_server_key_cannot_create_a_hot_session(), ControlEvent, IngressBeforeTunnel, IoCounters, Payload, SessionClosed, SessionCreated (+6 more)

### Community 139 - "VkAuthWebViewManager.kt"
Cohesion: 0.12
Nodes (6): VkAuthDialog(), VkAuthPhase, LOGIN, TOKEN, WebViewClient, VkAuthWebViewManager

### Community 140 - ".enqueue_diagnostic"
Cohesion: 0.23
Nodes (13): broken_stdout_is_disabled_without_affecting_stderr(), concurrent_producers_keep_per_producer_order_without_deadlock(), flush_waits_for_written_data_and_flushes_both_sinks(), format_line(), formatted_lines_have_a_strict_memory_bound(), open_gate(), protocol_queue_has_reserved_capacity_and_writer_priority(), Records (+5 more)

### Community 141 - "resolveTunnelPasswordStorage"
Cohesion: 0.25
Nodes (3): resolveTunnelPasswordStorage(), shouldResetUnreadableTunnelPassword(), TunnelPasswordStorageTest

### Community 142 - "TunnelStartupProgress"
Cohesion: 0.15
Nodes (6): TunnelStartupProgress, TunnelStartupStage, READY, WAITING_FOR_CREDENTIALS, WAITING_FOR_TURN_OR_PEER, TunnelStartupProgressTest

### Community 145 - "LogLine.kt"
Cohesion: 0.16
Nodes (10): MrttBanner(), MrttEmptyState(), MrttLoadingState(), MrttSpacing, isErrorLog(), isGreenLog(), LogLine(), parseAnsiLogBody() (+2 more)

### Community 146 - "build_linux.sh"
Cohesion: 0.13
Nodes (15): AR_aarch64_unknown_linux_musl, AR_armv7_unknown_linux_musleabihf, AR_x86_64_unknown_linux_musl, build_variant(), CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER, CARGO_TARGET_ARMV7_UNKNOWN_LINUX_MUSLEABIHF_LINKER, CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER, CC_aarch64_unknown_linux_musl (+7 more)

### Community 147 - "DnsResolver"
Cohesion: 0.21
Nodes (6): dns_cache_hit_avoids_network_queries(), dns_stale_cache_serves_requests_when_dns_fails(), DnsCacheEntry, DnsResolver, global(), resolve_socket()

### Community 148 - "AppUpdate.kt"
Cohesion: 0.06
Nodes (29): applyNoCacheHeaders(), AppReleaseInfo, fetchGitHubApi(), fetchHttpText(), fetchLatestReleaseInfo(), isLegacyRelease(), isNewerVersion(), normalizeVersionTag() (+21 more)

### Community 150 - "MrttMaxToken"
Cohesion: 0.16
Nodes (3): MrttMaxToken, JsonOpts, JsonOpts

### Community 151 - ".send_client_flight"
Cohesion: 0.21
Nodes (11): compute_verify_data(), derive_cipher_key(), derive_dtls_srtp_deterministic(), derive_dtls_srtp_keys(), derive_extended_master_secret(), derive_master_secret(), derive_record_keys(), DtlsSrtpKeys (+3 more)

### Community 152 - "TurnStreamWriter"
Cohesion: 0.21
Nodes (6): stream_data_rejects_when_queue_full(), TurnStreamOutbound, Control, Data, TurnStreamWriter, writer_loop()

### Community 153 - ".legacy"
Cohesion: 0.23
Nodes (4): cached_now(), HotDebug, StreamDebugMetrics, StreamDebugSnapshot

### Community 154 - ".accept"
Cohesion: 0.21
Nodes (6): read_diagnostic_request(), ReplayState, run_dpi_server(), run_syscalls_server(), serve_syscalls_client(), SyscallsClient

### Community 156 - "CaptchaSolver"
Cohesion: 0.26
Nodes (4): CaptchaSolver, device_info(), VkCaptchaError, SavedProfile

### Community 157 - "DebugRecorder"
Cohesion: 0.26
Nodes (3): DebugRecorder, Enabled, Path

### Community 158 - "IpPacket"
Cohesion: 0.22
Nodes (4): IpPacket, Ipv4, Ipv6, IpPacket<T>

### Community 159 - "ExtractorOptions"
Cohesion: 0.14
Nodes (12): ExtractorOptions, AllowHeadedFallback, Debug, DebugDir, GroupIds, Headless, LoginPollInterval, LoginTimeout (+4 more)

### Community 160 - "FirewallRule"
Cohesion: 0.24
Nodes (6): FirewallRule, KernelNetworking, managed_firewall_rules(), purge_legacy_mss_clamp(), set_kernel_parameter(), tun_sink_accepts_a_valid_ipv4_packet()

### Community 161 - "IpRule"
Cohesion: 0.20
Nodes (12): evaluate_route(), ip_verdict(), IpRule, Range, is_direct_domain(), is_direct_domain_mode(), is_domain_in_list(), is_ru_zone() (+4 more)

### Community 162 - "cached_system_stats"
Cohesion: 0.19
Nodes (11): cached_system_stats(), proc_kib(), process_memory(), process_memory_reads_linux_status_fields(), process_memory_rollup(), process_memory_rollup_reads_attribution_fields(), ProcessMemory, ProcessMemoryRollup (+3 more)

### Community 163 - "DtlsCertificate"
Cohesion: 0.21
Nodes (6): civil_from_days(), days_from_civil(), DtlsCertificate, public_point_bytes(), random_u64(), utc_today()

### Community 164 - "EphemeralSecret"
Cohesion: 0.21
Nodes (5): dtls_rng(), EphemeralSecret, P256, X25519, verify_p256_signature()

### Community 167 - "parseServerRoutingRule"
Cohesion: 0.28
Nodes (5): isRoutingPackageName(), parseServerRoutingRule(), ServerRoutingRule, strings(), ServerRoutingRuleTest

### Community 168 - "die"
Cohesion: 0.14
Nodes (27): archive_mrtt_docker_state(), check_kernel_compatibility(), cleanup_deploy_uploads(), default_exit_code(), diagnose_systemd_failure(), die(), ensure_mrtt_directory(), ensure_tun_device() (+19 more)

### Community 170 - "worker_loop"
Cohesion: 0.08
Nodes (20): print_configuration(), config_flight_is_globally_exclusive_and_released_by_drop(), ConfigFlightGuard, credential_failure_delay(), credential_stream_id(), delivered_config_prevents_new_flight(), GroupContext, GroupCredentials (+12 more)

### Community 172 - "arc"
Cohesion: 0.27
Nodes (3): HEARTBEAT_TICKS, Stats, TICK

### Community 173 - "WifiAutoPausePolicy.kt"
Cohesion: 0.22
Nodes (7): completeWifiAutoPause(), runWifiAutoPauseTransition(), tunnelPowerIsOn(), WifiAutoPauseAction, NONE, PAUSE, RESUME

### Community 174 - "TunnelDropdownSetting.kt"
Cohesion: 0.30
Nodes (3): CompactDropdownSetting(), ModeIndicator, ModeProgressBar()

### Community 178 - "jsonobject"
Cohesion: 0.22
Nodes (3): MaxAuthCredentials, MaxAuthPage, MaxAuthPageTest

### Community 184 - "LocalProxyProfile"
Cohesion: 0.23
Nodes (4): LocalProxyProfile, LocalProxyState, config(), validates_proxy_credentials()

### Community 185 - "ExtractorResult"
Cohesion: 0.20
Nodes (9): ExtractorResult, AccessToken, ApiServer, Calls, ExternalUserId, ExtractedAtUtc, SessionKey, SessionSecretKey (+1 more)

### Community 187 - "ProtocolCommand"
Cohesion: 0.12
Nodes (14): ProtocolCommand, ActivateTunnel, BroadcastPlain, CompactMemory, DisconnectPassword, DropPassword, DropSession, InjectTun (+6 more)

### Community 188 - "pump_out"
Cohesion: 0.18
Nodes (8): batch_up(), batches_keep_order_per_endpoint_and_stay_small(), BridgeLink, Channel, Socket, endpoint_message(), endpoint_message_round_trip(), pump_out()

### Community 189 - "VkRateLimitType"
Cohesion: 0.22
Nodes (6): CallUnavailable, VkRateLimitError, VkRateLimitType, CaptchaLimit, FloodControl, RateLimit

### Community 190 - "authenticate_inbound"
Cohesion: 0.36
Nodes (4): is_rtp_packet(), authenticate_inbound(), ReplayProtection, ReplayWindow

### Community 192 - ".new"
Cohesion: 0.31
Nodes (7): test_dispatch_large_packet_direct_relay_bypass(), test_dispatch_malformed_packets_safety(), test_select_cascade_waterfall_cursor(), test_select_cursor_bounds_after_scale_down(), test_try_follow_stream_respects_deficit_window(), uplink_cascade_pacing_pins_spaced_ticks_to_worker_zero(), uplink_cascade_waterfall_fills_worker_zero_then_overflows()

### Community 193 - "log_step"
Cohesion: 0.36
Nodes (9): certbot_supports_ip_certificates(), ensure_docker(), ensure_letsencrypt_client(), install_prerequisites(), log_step(), migrate_legacy_install(), pkg_install(), pkg_install_with_refresh() (+1 more)

### Community 194 - "RoomTarget"
Cohesion: 0.27
Nodes (5): connect_tcp(), deliver(), open_bridge_socket(), parse_endpoint_message(), RoomTarget

### Community 195 - "CallPayload"
Cohesion: 0.20
Nodes (10): CallPayload, ConversationId, Endpoint, ExtractedAtUtc, GroupId, SignalingToken, Stun, Turn (+2 more)

### Community 196 - "InfoTab"
Cohesion: 0.31
Nodes (6): ExpandableSectionCard(), InfoHeroCard(), ProjectLinkRow(), WideActionTile(), InfoTab(), openUrlInBrowser()

### Community 197 - "MRTT / MAX (экспериментальная интеграция)"
Cohesion: 0.22
Nodes (8): GitHub Actions: тестовое APK для приватного репозитория, MRTT / MAX (экспериментальная интеграция), Настройка MAX и проверка, Проверки и прозрачность, Протокольные ограничения и диагностика, Сборка исходного кода (Windows), Только новая схема ссылок, Что именно реализовано

### Community 198 - "wrap.rs"
Cohesion: 0.12
Nodes (8): concurrent_shared_native_calls_are_stable(), derive_rustcrypto(), deterministic_and_password_bound(), wrap_equivalence_case!, WRAP_INFO, WRAP_KEY_LEN, WRAP_SALT, WRAP_SALT_BYTES

### Community 200 - "native_client_provenance.ps1"
Cohesion: 0.46
Nodes (7): Assert-Elf(), Assert-RegularFile(), Assert-RustArtifact(), Get-BuildInputHash(), Get-CargoPackage(), Get-Sha256(), Test-BytePattern()

### Community 205 - "RecordingWriter"
Cohesion: 0.20
Nodes (3): BoundedLine, BrokenWriter, RecordingWriter

### Community 207 - "Formatter"
Cohesion: 0.25
Nodes (3): BotChallenge, RateLimit, ShowTypeMismatch

### Community 208 - "server_asset_provenance.ps1"
Cohesion: 0.57
Nodes (6): Assert-RegularFile(), Assert-ServerBinary(), Get-BuildInputHash(), Get-CargoPackage(), Get-Sha256(), Get-WireProtocolRevision()

### Community 209 - "SrtpMode"
Cohesion: 0.29
Nodes (4): SrtpMode, Auto, Dtls, Wrap

### Community 211 - "CredentialBroker"
Cohesion: 0.32
Nodes (4): CallBackend, Max, Vk, CredentialBroker

### Community 213 - "CsqttrMaxToken.csproj"
Cohesion: 0.33
Nodes (5): net8.0, Microsoft.Playwright (1.47.0), System.CommandLine (2.0.0-beta4.22272.1), System.Text.Json (8.0.5), Microsoft.NET.Sdk

### Community 214 - "test_max_webview_theme.cjs"
Cohesion: 0.15
Nodes (8): assert, fs, path, root, sandbox, source, storage, vm

### Community 215 - "TunWriteState"
Cohesion: 0.33
Nodes (6): TunWriteState, Backoff, Complete, Dropped, Wait, Yield

### Community 216 - "TurnAllocateError"
Cohesion: 0.29
Nodes (3): turn_allocate_error_exposes_nested_io_source(), turn_allocate_error_exposes_structured_stun_code(), TurnAllocateError

### Community 221 - "HandshakeReassembler"
Cohesion: 0.31
Nodes (4): DtlsHandshakeMsg, HandshakeReassembler, parse_handshake_messages(), PartialHandshake

### Community 222 - "DriverEvent"
Cohesion: 0.40
Nodes (5): DriverEvent, Packet, Readable, Timer, Wake

### Community 226 - "isVkProbeHttpResponse"
Cohesion: 0.38
Nodes (3): isVkProbeHttpResponse(), vkProbeRetryDelayMs(), VkNetworkProbePolicyTest

### Community 227 - "SubscriptionsSection.kt"
Cohesion: 0.06
Nodes (46): ClientDevice, objects(), ProxyProfile, ProxyState, RoutingRuleData, ServerClient, ServerControl, ServerGroup (+38 more)

### Community 228 - "Value"
Cohesion: 0.38
Nodes (4): parses_captcha_error_numbers(), response_object(), string_value(), value_string()

### Community 230 - "PacketBuf"
Cohesion: 0.21
Nodes (7): is_ip_packet(), queue_profile_ingress_capacity_scales_and_is_bounded(), QueueProfile, return_packet_delivers_fifo(), strip_legacy_downlink_prefix(), test_dispatcher_inbound_capacity_and_overflow_replacement(), test_push_inbound_wakes_the_writer_whatever_the_queue_length()

### Community 231 - "process_packet"
Cohesion: 0.38
Nodes (7): channel_data_header(), is_stun(), PacketAction, Pump, Wait, process_deficit_packet(), process_packet()

### Community 232 - "csqtt-client"
Cohesion: 0.67
Nodes (3): mrtt-client, netstack-smoltcp, primp

### Community 233 - "MRTT"
Cohesion: 0.25
Nodes (7): CSQTT 2.5.0 и 2.1.9, MRTT, Благодарности, Лицензия, Обсуждение и поддержка, Особенности, Скачать

### Community 244 - "TunnelQuickActions.kt"
Cohesion: 0.60
Nodes (3): buildQuickStartIntent(), QuickTunnelAction, toggleTunnelFromQuickAction()

### Community 248 - "resolveConnectionSource"
Cohesion: 0.22
Nodes (8): ConnectionSource, resolveConnectionSource(), TODO после партии max-test (версия 2.5.1), Напоминания, Партия max-test: без веб-панели, Jitsi (versionCode 239), Планы на 2.5.2, Правила партии, Сделано в этой партии (2.5.1)

### Community 249 - "parseMrttLink"
Cohesion: 0.06
Nodes (26): MainActivity, ConnectionProfile, decodeSubscription(), encodeSubscription(), ProfileRepository, Subscription, decodeBase64Safe(), decodeQueryComponent() (+18 more)

### Community 251 - "StreamDriverEvent"
Cohesion: 0.33
Nodes (5): StreamDriverEvent, Frame, Timer, Wake, WriteFailure

### Community 252 - "flush_limited_relay_queue"
Cohesion: 0.43
Nodes (4): flush_limited_relay_queue(), next_limited_relay_deadline(), QueuedRelayPacket, StreamRateLimiter

### Community 254 - "FlowState"
Cohesion: 0.50
Nodes (3): bulk_udp_with_small_packets_loses_the_pin(), FlowState, game_flow_keeps_the_pin()

### Community 255 - "DeployOutputLevel"
Cohesion: 0.50
Nodes (4): DeployOutputLevel, ERR, LOG, OK

### Community 257 - "ServerArchitecture"
Cohesion: 0.50
Nodes (4): ServerArchitecture, AMD64, ARM64, ARMV7

### Community 259 - "Изменения в этом архиве"
Cohesion: 0.50
Nodes (3): Границы готовности, Изменения в этом архиве, Поправки сборки и тестирования 22 октября 2026

### Community 261 - "TurnControlTransport"
Cohesion: 0.67
Nodes (3): TurnControlTransport, Datagram, Stream

### Community 263 - "TurnStreamFrame"
Cohesion: 0.17
Nodes (13): copy_channel_data(), frame_start(), FrameKind, ChannelData, Control, StreamDecode, Frame, NeedMore (+5 more)

## Knowledge Gaps
- **329 isolated node(s):** `DEBIAN_FRONTEND`, `TERM`, `SCRIPT_VERSION`, `LOG_FILE`, `PEER_PORT` (+324 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1820 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **66 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SettingsStore` connect `SettingsStore` to `TunnelService`, `ServersTab.kt`, `SettingsTab.kt`, `MainActivity.kt`, `ExceptionsTab.kt`, `TunnelLifecycleState`, `resolveTunnelPasswordStorage`, `AppUpdate.kt`, `TunnelManager`, `SettingsStore.kt`, `ImportExportDialog.kt`, `InfoTab`, `resolveStoredSecret`, `TurnSubnetRegistry`, `Preferences`, `.normalize`, `TunnelAuthSnapshot`, `SubscriptionsSection.kt`, `InfoTab.kt`, `.updateLog`, `Test`, `MaxAuthLifecycleTest.kt`, `TunnelQuickActions.kt`, `resolveConnectionSource`, `parseMrttLink`?**
  _High betweenness centrality (0.095) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `SettingsStore` (e.g. with `.pendingBindingSurvivesBackgroundWithoutReloadingTheWebView()` and `.onCreate()`) actually correct?**
  _`SettingsStore` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `DEBIAN_FRONTEND`, `TERM`, `SCRIPT_VERSION` to the rest of the system?**
  _329 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `rust-server/protocol.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.024175824175824177 - nodes in this community are weakly interconnected._
- **Why does `TunnelManager` connect `TunnelManager` to `TunnelService`, `SubscriptionsSection.kt`, `SettingsStore`, `ServersTab.kt`, `.updateLog`, `parseServerRoutingRule`, `SettingsTab.kt`, `MainActivity.kt`, `ExceptionsTab.kt`, `TunnelLifecycleState`, `DeployOperations.kt`, `TunnelManager.kt`, `NetworkPauseGate`, `.onCreate`, `WorkerRecoveryPolicy`, `ImportExportDialog.kt`, `performDeploy`, `.onVpnRecoverableFailure`?**
  _High betweenness centrality (0.072) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `TunnelManager` (e.g. with `NetworkPauseGate` and `TunnelLifecycleState`) actually correct?**
  _`TunnelManager` has 3 INFERRED edges - model-reasoned connections that need verification._
- **Should `TunnelService` be split into smaller, more focused modules?**
  _Cohesion score 0.08128772635814889 - nodes in this community are weakly interconnected._