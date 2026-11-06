# Graph Report - mrtt  (2026-11-06)

## Corpus Check
- 273 files · ~287,935 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 63 file(s) not represented in the graph (top: .xml 37, (none) 8, .bat 7)

## Summary
- 6881 nodes · 17183 edges · 262 communities (194 shown, 68 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 512 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `35f30e48`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- rust-server/protocol.rs
- TunnelService
- turn_core.rs
- dtls.rs
- SettingsStore
- MainActivity.kt
- logging.rs
- rust-server/main.rs
- SettingsTab.kt
- ClientBuilder
- MrttConstants
- LogLine.kt
- TunnelLifecycleState
- MrttOrb.kt
- rust-client/main.rs
- auth.rs
- session.rs
- web_panel.rs
- tun_device.rs
- ExceptionsTab.kt
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
- .legacy
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
- tunnel_probe.rs
- Events
- UdpSocket
- QrScannerDialog.kt
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
- Загрузка MRTT через GitHub Desktop и сборка APK в отдельной ветке
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
- .keepsTunnelRequested
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
- netstack-smoltcp/src/lib.rs
- log_info
- DeployOperations.kt
- stack.rs
- Impersonate
- Test
- TunnelDropdownSetting.kt
- MaxAuthLifecycleTest.kt
- ordering
- relay_responses
- CaptchaSession
- command_output
- log_warn
- Test
- super
- Extractor
- DirectRelay
- UplinkState
- start_tproxy_child_with_retry
- setup_letsencrypt_ip_tls
- UpdateLaunchGuard.kt
- join_as
- lock_password_epoch
- opera/mod.rs
- stream_control_payload
- ServerApi
- ProxyRoute
- Formatter
- resolveStoredSecret
- MaxAuthSession
- FiftyStreamRig
- ControlEvent
- VkAuthWebViewManager.kt
- EventHandler
- resolveTunnelPasswordStorage
- TunnelStartupProgress
- VkAuthSession
- socks_command
- worker_loop
- build_linux.sh
- .startAutoCalls
- AppUpdate.kt
- WorkerRecoveryPolicy
- MrttMaxToken
- .send_client_flight
- .connect
- TcpSocketState
- .accept
- SshHostKeyStore.kt
- CaptchaSolver
- DebugRecorder
- IpPacket
- ExtractorOptions
- FirewallRule
- ProfileRepository
- cached_system_stats
- DtlsCertificate
- EphemeralSecret
- DataplaneLogic
- PacketSink
- parseServerRoutingRule
- die
- SettingsStore.kt
- cascade.sh
- run_max_webview_smoke.sh
- arc
- WifiAutoPausePolicy.kt
- Test
- QuickToggleTileService
- ServerArchitecture
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
- VkApiResult
- CallPayload
- InfoTab.kt
- MRTT / MAX (экспериментальная интеграция)
- wrap.rs
- .onCreate
- native_client_provenance.ps1
- BatterySaverTest
- TurnSubnetRegistry
- AppSelectionOrderingTest
- Preferences
- ViewOutlineProvider
- MaxAuthSessionTest.kt
- BufferClass
- server_asset_provenance.ps1
- result
- uuid
- SecureStringStore.kt
- CsqttrMaxToken.csproj
- test_max_webview_theme.cjs
- TunWriteState
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
- VpsServer
- PacketBuf
- process_packet
- csqtt-client
- MRTT
- .next
- run_wsl_checks.sh
- VkCallPoliciesTest.kt
- TunnelQuickActions.kt
- shouldConfirmTcpTransport
- .onReceive
- DnsDomainMap
- parseMrttLink
- TvManifestTest
- StreamDriverEvent
- flush_limited_relay_queue
- SshKeyRuntimeDependencyTest.kt
- formatMrttLink
- MrttExtendedColors.kt
- SettingsStoreInitOrderTest.kt
- Изменения в этом архиве
- mrtt
- AppUpdateTest.kt
- getconf_credential_access
- FALLBACK_RESOLVER

## God Nodes (most connected - your core abstractions)
1. `SettingsStore` - 156 edges
2. `TunnelManager` - 95 edges
3. `TunnelService` - 76 edges
4. `Impersonate` - 70 edges
5. `ClientBuilder` - 66 edges
6. `Dispatcher` - 60 edges
7. `ProtocolEngine` - 59 edges
8. `WebState` - 53 edges
9. `ServerControl` - 51 edges
10. `parseMrttLink()` - 48 edges

## Surprising Connections (you probably didn't know these)
- `Сделано в этой партии (2.5.1)` --references--> `ConnectionSource`  [INFERRED]
  TODO_NEXT_UPDATE.md → app/src/main/java/com/mrtt/client/ConnectionSource.kt
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

## Communities (262 total, 68 thin omitted)

### Community 0 - "rust-server/protocol.rs"
Cohesion: 0.02
Nodes (74): ACTIVE_SESSIONS_GAUGE, CACHED_MONOTONIC_MS, CONTROL_EVENT_CAPACITY, CONTROL_TASK_CAPACITY, CRYPTO_OPS_COUNTER, DB_LOCK_TIMEOUT, DecodedPacket, DEVICE_KEY_SEPARATOR (+66 more)

### Community 1 - "TunnelService"
Cohesion: 0.07
Nodes (5): Network, isEligiblePhysicalNetwork(), ResolvedVkHashes, TunnelService, NetworkCallback

### Community 2 - "turn_core.rs"
Cohesion: 0.02
Nodes (88): ALLOCATION_TIMEOUT, CLEANUP_ERROR_RETRY, CLEANUP_TIMEOUT, CONTROL_MAX, CONTROL_TIMEOUT, AF_IPV4, AF_IPV6, ALLOCATION_REFRESH (+80 more)

### Community 3 - "dtls.rs"
Cohesion: 0.02
Nodes (72): ALERT_BAD_RECORD_MAC, ALERT_CLOSE_NOTIFY, ALERT_DECODE_ERROR, ALERT_DECRYPT_ERROR, ALERT_HANDSHAKE_FAILURE, ALERT_ILLEGAL_PARAMETER, ALERT_LEVEL_FATAL, ALERT_LEVEL_WARNING (+64 more)

### Community 5 - "MainActivity.kt"
Cohesion: 0.04
Nodes (14): updateIntervalHoursToMillis(), BatterySaver, MainScreen(), NavItem, openReleaseUrl(), ProxyNavigationBar(), AppBackdrop(), MrttScreen() (+6 more)

### Community 6 - "logging.rs"
Cohesion: 0.06
Nodes (34): BoundedLine, broken_stdout_is_disabled_without_affecting_stderr(), BrokenWriter, COALESCE_BATCH, concurrent_producers_keep_per_producer_order_without_deadlock(), DIAGNOSTIC_CAPACITY, flush_waits_for_written_data_and_flushes_both_sinks(), format_line() (+26 more)

### Community 7 - "rust-server/main.rs"
Cohesion: 0.04
Nodes (54): acquire_instance_lock(), App, append_utf8_prefix(), Args, arm_tproxy_child_parent_death(), async_main(), auto_restart_loop(), cpu_loop() (+46 more)

### Community 8 - "SettingsTab.kt"
Cohesion: 0.04
Nodes (35): appSectionCardBorderColor(), appSectionCardColor(), AppUpdateDialog(), AutoVkRiskDialog(), MaxTransportRiskDialog(), TcpTransportRiskDialog(), TimedWarningDialog(), BatterySaverInfoDialog() (+27 more)

### Community 9 - "ClientBuilder"
Cohesion: 0.04
Nodes (3): apply_http2_settings(), apply_impersonation(), ClientBuilder

### Community 10 - "MrttConstants"
Cohesion: 0.05
Nodes (22): Captcha, General, Links, MrttConstants, Notifications, Patterns, Profiles, SecureStore (+14 more)

### Community 11 - "LogLine.kt"
Cohesion: 0.19
Nodes (9): isErrorLog(), isGreenLog(), LogLine(), parseAnsiLogBody(), resolveTagColors(), TagColors, LogsTab(), LogsTerminal() (+1 more)

### Community 12 - "TunnelLifecycleState"
Cohesion: 0.08
Nodes (8): nextTunnelGenerationId(), reserveTunnelGenerationId(), TunnelLifecycleState, TunnelLifecycleTicket, Model, ModelTicket, TunnelLifecycleStateChaosTest, TunnelLifecycleStateTest

### Community 13 - "MrttOrb.kt"
Cohesion: 0.06
Nodes (5): AppItem, AppRow(), MrttGlassButton(), MrttOrb(), AppCache

### Community 14 - "rust-client/main.rs"
Cohesion: 0.04
Nodes (35): bootstrap_keeps_control_lines_that_follow_it(), build_runtime(), EXIT_FATAL, EXIT_NORMAL, EXIT_TUN_LOST, invalid_totals_never_create_partial_or_excess_group(), leave_js_creator_after_ready_workers(), main() (+27 more)

### Community 15 - "auth.rs"
Cohesion: 0.07
Nodes (47): browser_client_for_profile(), browser_headers(), browser_headers_omit_user_agent_when_profile_has_no_override(), browser_identity(), build_browser_client(), BUILTIN_VK_CREDENTIALS, captcha_client_for_profile(), check_fatal_call() (+39 more)

### Community 16 - "session.rs"
Cohesion: 0.04
Nodes (49): await_session_task(), cancellation_aborts_and_awaits_pending_session_startup(), cancelled_session_aborts_every_child_task(), completed_reader_is_not_polled_twice(), completed_writer_is_not_polled_twice(), CONFIG_RESPONSE_TIMEOUT_MS, ConfigDeliveryState, configuration_timeout_is_recoverable() (+41 more)

### Community 17 - "web_panel.rs"
Cohesion: 0.05
Nodes (96): log_event(), api_client_vk_update(), api_clients_batch_delete(), api_clients_batch_extend(), api_clients_batch_group(), api_clients_batch_reset_traffic(), api_clients_batch_toggle(), api_diagnostics_dpi() (+88 more)

### Community 18 - "tun_device.rs"
Cohesion: 0.06
Nodes (44): extract_dst_ipv4(), BULK_HOLD, ClientRoute, compacted_session_slot_is_updated(), downlink_bulk_udp_with_small_packets_is_not_pinned(), downlink_cursor_round_robin_rotates_streams(), downlink_game_flow_with_large_packets_stays_pinned(), downlink_new_tcp_flows_rotate_without_tick_pinning() (+36 more)

### Community 19 - "ExceptionsTab.kt"
Cohesion: 0.04
Nodes (15): MrttApplication, showRaisedToast(), selectedApplicationsFirst(), logFileName(), shareLogFile(), ActionRunner, copyToClipboard(), qrBitmap() (+7 more)

### Community 21 - "TunnelManager"
Cohesion: 0.10
Nodes (6): FullLog, ServerRoutingRule, PendingLog, ProcessIdentity, TunnelManager, TunnelParams

### Community 22 - "turn_stream.rs"
Cohesion: 0.06
Nodes (32): AsyncTurnStream, connect(), CONTROL_QUEUE_CAPACITY, copy_channel_data(), DATA_QUEUE_CAPACITY, frame_start(), FrameKind, ChannelData (+24 more)

### Community 23 - "obfs.rs"
Cohesion: 0.08
Nodes (34): WorkerChannels, aes_ctr(), audio_roundtrip(), authenticated_audio_region_rejects_every_single_byte_tamper(), duration_ticks(), empty_payload_returns_error(), large_packet_padding_respects_mtu_boundary(), sized_roundtrip_case! (+26 more)

### Community 24 - "turn_integration_tests.rs"
Cohesion: 0.05
Nodes (48): run_session(), ALLOCATE_ERROR, ALLOCATE_REQUEST, ALLOCATE_SUCCESS, apply_e2e_dns(), assert_fatal_auth_case(), ATTR_CHANNEL_NUMBER, ATTR_ERROR_CODE (+40 more)

### Community 25 - "vk_js_calls.rs"
Cohesion: 0.07
Nodes (40): fetch_call_token(), MAX_API_KEY, MAX_APP_VERSION, MAX_CALLS_API, MAX_ORIGIN, ONEME_WS_URL, open_max_session(), start() (+32 more)

### Community 26 - "tproxy.rs"
Cohesion: 0.03
Nodes (48): ASSOCIATION_BACKOFF, ASSOCIATION_POOL_TARGET, DEVICE_JUMBO_BUFFER_LIMIT, DEVICE_NORMAL_BUFFER_LIMIT, DEVICE_TCP_FLOOR, DEVICE_TCP_SESSION_LIMIT, DEVICE_UDP_FLOOR, DEVICE_UDP_FLOW_LIMIT (+40 more)

### Community 27 - "worker.rs"
Cohesion: 0.04
Nodes (46): cancelled_queued_reconnect_does_not_consume_the_next_slot(), cancelled_worker_start_does_not_wait_for_a_reserved_slot(), contains_os_error_code(), credential_cohort_index(), credential_failure_delay(), CREDENTIAL_FETCH_TIMEOUT, CREDENTIAL_POST_DELAY, credential_stream_id() (+38 more)

### Community 28 - "tcp.rs"
Cohesion: 0.07
Nodes (7): recv_stream_channel_data(), recv_stream_message(), TcpListener, TcpListenerRunner, TcpSocketControl, TcpSocketCreation, TcpStream

### Community 29 - "TurnAllocation"
Cohesion: 0.06
Nodes (29): channel_data_padding(), channel_encoding_reuses_storage_and_zeroes_padding(), cleanup_limiter(), Completion, completion_sequences_are_independent(), CoreSnapshot, driver_loop(), driver_loop_stream() (+21 more)

### Community 30 - "dns.rs"
Cohesion: 0.07
Nodes (38): answer_packet(), build_query(), DNS_A_RECORD, DNS_AAAA_RECORD, dns_cache_hit_avoids_network_queries(), DNS_CACHE_MAX_ENTRIES, DNS_CACHE_MAX_TTL, dns_stale_cache_serves_requests_when_dns_fails() (+30 more)

### Community 31 - ".parse"
Cohesion: 0.10
Nodes (23): ActiveZero, CallUnavailable, CaptchaDone, CaptchaRequest, Config, Dtls, Error, Event (+15 more)

### Community 32 - "deploy.sh"
Cohesion: 0.04
Nodes (53): all_mrtt_process_pids(), DEBIAN_FRONTEND, DEPLOY_MODE, DOCKER_BUILD_TIMEOUT_SECONDS, EXIT_CUTOVER_FAILED, EXIT_INVALID_ARGUMENT, EXIT_PREFLIGHT_FAILED, IPT_COMMENT (+45 more)

### Community 33 - "dataplane.rs"
Cohesion: 0.07
Nodes (26): COMMAND_DRAIN_LIMIT, DATAPLANE_STACK_BYTES, DataplaneConfig, DataplaneHandle, DataplaneHandle<C>, DataplaneRuntime, DataplaneRuntime<C>, decrement_queue_len() (+18 more)

### Community 34 - ".get"
Cohesion: 0.10
Nodes (15): display_control_payload(), DownlinkSend, Backpressured, Sent, Stale, EndpointKey, hex_and_ascii_dump(), insert_bounded_endpoint() (+7 more)

### Community 35 - ".legacy"
Cohesion: 0.18
Nodes (5): cached_now(), refresh_cached_now(), HotDebug, StreamDebugMetrics, StreamDebugSnapshot

### Community 36 - "captcha.rs"
Cohesion: 0.09
Nodes (30): accepts_partial_window_init_like_go_json(), ALLOWED_CAPTCHA_DOMAINS, API_VERSION, base_values(), CaptchaCheck, CaptchaInit, CaptchaInitData, CaptchaPage (+22 more)

### Community 37 - "safari/mod.rs"
Cohesion: 0.06
Nodes (37): BrowserLeaksResponse, build_http2_settings(), build_safari_base_headers(), build_safari_settings(), build_user_agent(), new_safari_18_5_emulator(), new_safari_26_3_ios_emulator(), new_safari_26_3_macos_emulator() (+29 more)

### Community 38 - "tokio_io.rs"
Cohesion: 0.06
Nodes (23): packet_pool_size(), cmsg_align(), configured_udp_datagram_mode(), create_tun_device(), fair_share_allocation_and_control_slot_reservation(), FIREWALL_REMOVE_PASSES, is_fatal_tun_write_error(), is_tun_create_retryable() (+15 more)

### Community 39 - "turn_ip_pool.rs"
Cohesion: 0.15
Nodes (16): auto_mode_returns_none_for_workers(), current_time_ms(), distributes_evenly_across_healthy_ips(), FAILURE_COOLDOWN_MS, fallback_to_none_when_all_fail(), HEALTHY_STATE, mark_healthy_restores_endpoint(), parse_custom_turn_ips() (+8 more)

### Community 40 - "TrafficSnapshot"
Cohesion: 0.22
Nodes (9): should_emit(), persistence_queue_coalesces_traffic_and_ignores_late_completion(), persistence_snapshot_supersedes_queued_traffic(), PersistenceQueue, PersistenceUpdate, Snapshot, Traffic, TrafficCounters (+1 more)

### Community 41 - "App"
Cohesion: 0.09
Nodes (53): lock_unpoison(), broadcast_tunnel_configuration(), build_credentials(), command(), compact_memory(), control_loop(), ControlWriter, CredentialSet (+45 more)

### Community 42 - "super"
Cohesion: 0.10
Nodes (12): ATTR_FINGERPRINT, Attribute, Attributes, Class, Error, Indication, Success, decodes_pion_allocate_vector() (+4 more)

### Community 43 - "rust-client/packet.rs"
Cohesion: 0.08
Nodes (14): concurrent_deficit_never_exceeds_packet_budget_or_leaks(), exact_headroom_and_tail_capacity_are_accepted(), headroom_and_pool_reuse(), invalid_ranges_return_errors(), large_pool_retains_only_bounded_idle_storage(), maximum_read_area_is_accepted(), packet_budget_is_strict_and_recovers_after_release(), PACKET_CAPACITY (+6 more)

### Community 44 - "captcha_slider.rs"
Cohesion: 0.12
Nodes (19): response_object(), apply_swaps(), build_cursor(), cursor_has_human_path(), CursorPoint, luma(), MAX_SLIDER_GRID, parse_number() (+11 more)

### Community 45 - "NativeCore"
Cohesion: 0.15
Nodes (26): authenticated_allocation_mismatch_destroys_allocation(), bind_ready_channel(), channel_bind_bad_request_destroys_allocation(), channel_bind_success_does_not_change_maintenance_deadline(), complete_empty_success(), encode_xor_address(), invalid_integrity_keeps_the_existing_transaction_for_retransmission(), keepalive_uses_valid_channel_data_framing() (+18 more)

### Community 46 - "TunnelManager.kt"
Cohesion: 0.09
Nodes (13): displayVkHash(), dnsProfileName(), LogEntry, LogLevel, DBG, ERR, LOG, NET (+5 more)

### Community 47 - "dispatcher.rs"
Cohesion: 0.05
Nodes (38): ADAPTIVE_ADJUST_INTERVAL, ADAPTIVE_DECREASE_FACTOR, ADAPTIVE_INCREASE_BYTES_PER_SEC, ADAPTIVE_MAX_RATE_BYTES_PER_SEC, ADAPTIVE_MIN_RATE_BYTES_PER_SEC, BULK_HOLD, FLOW_BURST_BYTES, FLOW_EVICT_EVERY (+30 more)

### Community 48 - "sni.rs"
Cohesion: 0.09
Nodes (26): extract_dns_info(), extract_dst_addr(), extract_src_addr(), extract_tls_sni(), is_direct_domain(), is_direct_domain_mode(), is_domain_in_list(), is_ru_zone() (+18 more)

### Community 49 - "MaxAuthWebViewManager.kt"
Cohesion: 0.15
Nodes (4): WebChromeClient, WebViewClient, MaxAuthWebViewManager, MaxAuthDialog()

### Community 50 - "tunnel_probe.rs"
Cohesion: 0.14
Nodes (17): ACK, answer(), checksum(), checksums_verify_to_zero(), command_is_validated(), is_answer(), only_the_answer_is_taken_and_only_once(), PACKET_LEN (+9 more)

### Community 51 - "Events"
Cohesion: 0.09
Nodes (10): call_unavailable_is_deduplicated_per_hash(), disabled_events_do_not_build_payloads(), encode_event(), EVENT_PREFIX, Events, network_suspect_requires_a_timeout_burst_and_resets_after_recovery(), NETWORK_TIMEOUT_BURST_WINDOW, panel_restart_is_deduplicated_across_workers() (+2 more)

### Community 52 - "UdpSocket"
Cohesion: 0.18
Nodes (29): allocate_success(), assert_authenticated_request(), assert_fingerprint(), assert_message_integrity(), attribute_u32(), authenticated_error(), authenticated_flow_survives_pool_deficit_and_keeps_channel_data_zero_copy(), cancelled_connect_deallocates_server_allocation_after_late_success() (+21 more)

### Community 54 - "wire_protocol.rs"
Cohesion: 0.09
Nodes (25): parse_path_probe(), PATH_PROBE_PREFIX, PathProbe, CLIENT_GETCONF_AUDIO_FIXTURE, FRAG_HEADER_LEN, FragHeader, fragment_packet(), MAX_FRAG_PAYLOAD (+17 more)

### Community 55 - "model.rs"
Cohesion: 0.04
Nodes (79): admit_device(), auto_restart_interval_defaults_to_disabled_and_persists_disabled(), CACHED_NOW, client_group_vk_settings_persist_across_restarts(), ClientGroup, concurrent_database_mutations_persist_the_highest_revision(), Database, DATABASE_FILE (+71 more)

### Community 56 - "imp/mod.rs"
Cohesion: 0.08
Nodes (20): BrowserSettings, CHROME_HEADER_TABLE_SIZE, CHROME_INITIAL_CONNECTION_WINDOW, CHROME_INITIAL_STREAM_WINDOW, CHROME_MAX_HEADER_LIST_SIZE, FIREFOX_HEADER_TABLE_SIZE, FIREFOX_INITIAL_CONNECTION_WINDOW, FIREFOX_INITIAL_STREAM_WINDOW (+12 more)

### Community 57 - "context"
Cohesion: 0.08
Nodes (29): configure_udp_socket_buffers(), connect_transport(), is_supported_turn_uri(), parse_authority(), parse_turn_uri(), resolve_turn_endpoints(), tcp_mode_uses_raw_tcp_for_turn_uris(), tcp_uri_is_preserved_for_tcp_mode() (+21 more)

### Community 58 - ".onCreate"
Cohesion: 0.13
Nodes (3): ManlCaptchaActivity, object@L178, WebViewClient

### Community 59 - "Inner"
Cohesion: 0.10
Nodes (19): Message, allocation_refresh_deadline(), attribute_u32(), Control, decode_xor_address(), error_code(), Inner, NativeAddress (+11 more)

### Community 60 - "CaptchaWebViewManager.kt"
Cohesion: 0.10
Nodes (4): CaptchaJSBridge, CaptchaWebViewManager, WebViewClient, Runnable

### Community 61 - "TproxyStats"
Cohesion: 0.10
Nodes (10): evict_oldest_udp_flow(), ProxyBufferPool, ProxyBufferPoolSnapshot, run(), run_tcp(), run_udp(), runtime_stats_track_active_peak_and_total(), TcpActiveGuard (+2 more)

### Community 62 - "DtlsClientHandshake"
Cohesion: 0.07
Nodes (18): handshake_message_label(), dtls_state_label(), certificate_p256_public_key(), DtlsClientHandshake, DtlsHandshakeState, AwaitingClientFlight, AwaitingServerFinished, AwaitingServerFlight (+10 more)

### Community 63 - "rust-client/protocol.rs"
Cohesion: 0.10
Nodes (17): auth_denied_reasons_distinguish_fatal_from_retryable(), config_request(), ConfigResponse, Config, NoConfig, device_fields(), disconnect_request(), MAX_STREAM_WORKERS (+9 more)

### Community 64 - ".push"
Cohesion: 0.12
Nodes (20): choose_group(), choose_srtp_profile(), ClientHello, decode_u16_list(), decode_use_srtp(), dtls12_client_hello_matches_boring_ssl_calls_wire_format(), dtls12_truncated_client_hello_is_rejected(), encode_u16_list() (+12 more)

### Community 65 - "jitsi_room.rs"
Cohesion: 0.06
Nodes (23): accept_answers_every_content_without_foreign_sources(), accept_contents(), BASE64, BATCH_BYTES, CHANNEL_WAIT, CLIENT_HELLO, DOCKER_DOMAIN, FEATURES (+15 more)

### Community 66 - "direct_relay.rs"
Cohesion: 0.12
Nodes (20): build_tcp_packet(), build_udp_packet(), craft_tcp_packet_into(), craft_udp_packet_into(), DIRECT_RELAY_INBOUND_CAPACITY, dns_query_slots(), ip_checksum(), MAX_CONCURRENT_DNS_QUERIES (+12 more)

### Community 67 - "ShutdownCoordinator"
Cohesion: 0.13
Nodes (12): NEXT_INCARNATION_ID, send_writer_bytes(), shutdown_never_uses_a_dropped_control_stream(), shutdown_uses_the_most_recently_active_control_stream_once(), ShutdownControlStream, ShutdownCoordinator, ShutdownRegistration, ShutdownState (+4 more)

### Community 68 - "DeviceQuotaRegistry"
Cohesion: 0.13
Nodes (13): acquire_tcp_quota(), acquire_udp_quota(), adaptive_device_limit(), AdmissionError, DeviceLimit, GlobalLimit, Inactive, AdmissionState (+5 more)

### Community 69 - "Загрузка MRTT через GitHub Desktop и сборка APK в отдельной ветке"
Cohesion: 0.13
Nodes (13): Возможные ошибки загрузки, Загрузка MRTT через GitHub Desktop и сборка APK в отдельной ветке, Что именно собирает и чего не делает workflow, Шаг 1. Распаковать новый ZIP, Шаг 2. Клонировать существующий приватный репозиторий, Шаг 3. Создать отдельную ветку ДО копирования, Шаг 4. Поместить исходники в клонированный репозиторий, Шаг 5. Коммит и отправка ветки на GitHub (+5 more)

### Community 70 - "rust-server/packet.rs"
Cohesion: 0.10
Nodes (13): PACKET_CAPACITY, packet_pool_discards_excess_idle_storage(), packet_pool_is_bounded_and_recovers_buffers(), PACKET_POOL_RETAINED_MAX, PacketBuf, PacketPool, PacketPoolSnapshot, socket_addr_to_storage() (+5 more)

### Community 71 - "TurnCredentials"
Cohesion: 0.13
Nodes (17): TurnCredentials, allocation_mismatch_retries_once_then_rotates_and_resets_after_allocate(), AllocationMismatch, Replaced, RetryCurrent, Rotated, CachedCredentials, credential_cache_never_reuses_a_credential_after_nine_allocate_attempts() (+9 more)

### Community 72 - "clients_get"
Cohesion: 0.16
Nodes (22): BoundDevice, is_expired(), PasswordEntry, api_links_get(), client_get(), ClientInfo, ClientListQuery, clients_get() (+14 more)

### Community 73 - "Dispatcher"
Cohesion: 0.11
Nodes (5): Dispatcher, spawn_critical(), Termination, Fatal, TunLost

### Community 74 - "jitsi_relay.rs"
Cohesion: 0.11
Nodes (13): Config, CONFIG, configure(), DATAGRAM_MAX, Flow, IDLE_FORGET, REJOIN_MAX, REJOIN_MIN (+5 more)

### Community 75 - "PhysicalNetworkTracker"
Cohesion: 0.13
Nodes (10): PhysicalNetworkPauseReason, AIRPLANE_MODE, OFFLINE, PhysicalNetworkTracker, PhysicalNetworkTransition, AVAILABLE, CHANGED, NONE (+2 more)

### Community 76 - "NetworkCapture"
Cohesion: 0.08
Nodes (19): IceServer, Credential, Urls, Username, NetworkCapture, ApiServer, CapturedAt, ConversationId (+11 more)

### Community 77 - "TokioIo"
Cohesion: 0.12
Nodes (7): ControlBuffer, recvmsg_preserves_udp_payload_and_peer_address(), RxOutcome, Batch, Drained, TokioIo, write_ipv4_pktinfo_control()

### Community 78 - "ConnectionStartBlocker"
Cohesion: 0.10
Nodes (16): ConnectionStartBlocker, Hashes, Link, NoProfile, Password, Port, Server, StoredSecret (+8 more)

### Community 79 - "HotSession"
Cohesion: 0.10
Nodes (22): derive_wrap_key(), aes_ctr(), build_srtp_iv(), client_getconf_fixture_authenticates_with_matching_password_only(), client_getconf_fixture_rejects_every_single_bit_wire_mutation(), Credential, duration_ticks(), HotSession (+14 more)

### Community 80 - "firefox/mod.rs"
Cohesion: 0.10
Nodes (18): BrowserLeaksResponse, build_firefox_settings(), build_headers(), build_http2_settings(), build_user_agent(), FIREFOX140_AKAMAI_HASH, FIREFOX140_AKAMAI_TEXT, FIREFOX140_JA4 (+10 more)

### Community 81 - "TunnelService.kt"
Cohesion: 0.06
Nodes (5): CaptchaCancelReceiver, ManlCaptchaWebViewManager, DomainRoutingUpdate, BroadcastReceiver, BroadcastReceiver

### Community 82 - "DtlsServerHandshake"
Cohesion: 0.13
Nodes (12): a_forged_server_flight_is_rejected(), a_pinned_fingerprint_that_does_not_match_is_rejected(), complete_handshake(), dtls12_client_hello_rebuild_keeps_identical_format(), DtlsServerHandshake, extract_client_hello_random_matches(), fill_random_bytes(), random_array() (+4 more)

### Community 83 - "ImpersonateOS"
Cohesion: 0.15
Nodes (26): selected_profiles_keep_tls_and_http_identity_aligned(), chrome_pool(), chrome_profiles_produce_diverse_versions_and_os_combinations(), default_fingerprint_is_chrome(), edge_pool(), edge_profiles_produce_diverse_combinations(), firefox_pool(), firefox_profiles_produce_diverse_combinations() (+18 more)

### Community 85 - "T"
Cohesion: 0.15
Nodes (16): T, advance(), channel(), notification_wakes_a_rearmed_empty_receiver(), preserves_fifo_and_capacity(), rearm_race_keeps_the_already_queued_value_visible(), Ring, Ring<T> (+8 more)

### Community 86 - "DeployResultPolicyTest"
Cohesion: 0.16
Nodes (9): deployFailureDetail(), deployFailureMessage(), deployMode(), DeployOutputLine, friendlyDeployError(), isSuccessfulDeployResult(), parseDeployOutputLine(), serverArchitectureForMachine() (+1 more)

### Community 88 - "proxy_route.rs"
Cohesion: 0.09
Nodes (20): ACTIVE_RUNTIME, COMMAND_TIMEOUT, config(), iptables_spec_tokens(), LEGACY_NAT_COMMENT, LEGACY_POLICY_PRIORITY, LEGACY_POLICY_TABLE, LEGACY_QUIC_COMMENT (+12 more)

### Community 89 - "handle_tcp_session"
Cohesion: 0.11
Nodes (12): Association, create_association(), handle_tcp_session(), now_ms(), relay_direction(), session_expired(), spawn_refill(), TcpEvictionSlot (+4 more)

### Community 91 - "request_configuration"
Cohesion: 0.09
Nodes (28): is_config_response(), is_control_response(), is_panel_restart_notice(), ActiveConnection, apply_workers_limit(), build_registration_payload(), cipher_pair(), handle_writer_command() (+20 more)

### Community 92 - "BufferClassPool"
Cohesion: 0.10
Nodes (8): BufferClassPool, opening_packets_have_a_fixed_global_ceiling(), OpeningBufferLease, OpeningBufferPool, OpeningDatagram, OpeningQueue, proxy_buffers_are_released_when_unused(), ProxyCapacity

### Community 93 - "setup_nat_and_firewall"
Cohesion: 0.28
Nodes (9): detect_wan_interface(), fw_add_forward(), fw_add_input_tcp(), fw_add_input_udp(), fw_add_masquerade(), fw_add_mss_clamping(), ipt_add_or_ensure(), is_ignored_wan_interface() (+1 more)

### Community 94 - "performDeploy"
Cohesion: 0.20
Nodes (6): DeployManager, DeploySSHClient, performDeploy(), performUninstall(), publishDeployOutput(), rootCommand()

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
Cohesion: 0.11
Nodes (4): MrttShapes, MrttSizes, measureSubnetRtt(), probeStunRtt()

### Community 102 - ".updateLog"
Cohesion: 0.11
Nodes (7): TunVpnService, VpnFailureKind, PermissionRevoked, ServiceDestroyed, TunEstablishFailed, TunFdDeliveryFailed, VpnRecoveryPolicy

### Community 104 - "VkHashValidationStatus"
Cohesion: 0.15
Nodes (6): VkHashValidationCodec, VkHashValidationStatus, Invalid, Valid, VkHashValidator, VkHashValidatorTest

### Community 105 - "linux_workers_hold_per_stream_rate_through_server_dataplane"
Cohesion: 0.16
Nodes (13): e2e_env_u64(), e2e_process_cpu_seconds(), e2e_thread_cpu_seconds(), e2e_tunnel_ip(), E2eTunResult, E2eTunRuntime, LimitedRelayMeter, LimitedRelayRuntime (+5 more)

### Community 106 - "Participant"
Cohesion: 0.18
Nodes (5): pump_back(), join(), Participant, peers_in(), RoomSender

### Community 107 - "netstack-smoltcp/src/lib.rs"
Cohesion: 0.11
Nodes (3): BoxFuture, BoxFuture<'a, T>, BoxFuture<'_, T>

### Community 108 - "log_info"
Cohesion: 0.13
Nodes (22): check_root(), detect_firewall(), detect_os(), do_status(), docker_build_candidate_image(), install_network_helper(), log_info(), main() (+14 more)

### Community 109 - "DeployOperations.kt"
Cohesion: 0.09
Nodes (17): CascadeHost, CreatedExit, createSSHClient(), deployAssetLabel(), deployEnvironmentValue(), DeployOutputLevel, ERR, LOG (+9 more)

### Community 110 - "stack.rs"
Cohesion: 0.09
Nodes (6): Sink, IpFilters, channel_closed_err(), Stack, StackBuilder, tcp_window_defaults_to_historical_and_threads_overrides()

### Community 111 - "Impersonate"
Cohesion: 0.04
Nodes (58): base_chrome_headers(), BrowserLeaksResponse, build_chrome_settings(), build_http2_settings(), build_sec_ch_ua(), build_user_agent(), CHROME146_AKAMAI_HASH, CHROME146_AKAMAI_TEXT (+50 more)

### Community 112 - "Test"
Cohesion: 0.22
Nodes (7): normalizeVkModeSelection(), shouldConfirmAutoJsMode(), shouldReplaceUnavailableVkHash(), vkAuthModeForHashMode(), vkHashModeForAuthMode(), VkModeSelection, VkModePolicyTest

### Community 113 - "TunnelDropdownSetting.kt"
Cohesion: 0.13
Nodes (7): MrttSegmentedControl(), sanitizeSshPassword(), ServerEditDialog(), VkHashModeControls(), CompactDropdownSetting(), ModeIndicator, ModeProgressBar()

### Community 115 - "ordering"
Cohesion: 0.17
Nodes (13): parse_stream_alive(), parse_stream_command(), parse_stream_repair(), StreamRepairCommand, command(), duplicate_repair_sequence_is_ignored(), invalid_worker_ids_are_ignored(), ready_or_alive_sequence_prevents_credential_escalation() (+5 more)

### Community 116 - "relay_responses"
Cohesion: 0.13
Nodes (15): allowed_client(), bind_sockets(), bind_tcp_listener(), calc_checksum(), next_udp_datagram_len(), parse_orig_dst(), parses_original_destination_from_control_message(), RawReplySocket (+7 more)

### Community 117 - "CaptchaSession"
Cohesion: 0.20
Nodes (9): CaptchaSession, insert_header(), parses_captcha_error_numbers(), slider_check(), slider_request(), slider_saved_profile(), solve_slider(), string_value() (+1 more)

### Community 118 - "command_output"
Cohesion: 0.18
Nodes (18): activate_tproxy(), add_tproxy_rules(), add_tproxy_shared_rules(), cleanup_all_nat_exemptions(), cleanup_legacy_proxy_policy(), cleanup_legacy_quic_rule(), cleanup_mark_rules(), cleanup_nat_exemption() (+10 more)

### Community 119 - "log_warn"
Cohesion: 0.14
Nodes (24): assert_peer_port_is_available(), cleanup_legacy_proxy_interfaces(), cleanup_mrtt_netfilter_rules(), clear_runtime_config_preserving_database(), delete_marked_rules(), do_uninstall(), docker_container_for_pid(), docker_container_runs_mrtt() (+16 more)

### Community 120 - "Test"
Cohesion: 0.22
Nodes (3): dispatchTunnelStop(), effectiveWifiAutoPauseConnection(), WifiAutoPausePolicyTest

### Community 121 - "super"
Cohesion: 0.14
Nodes (7): FEMALE, LAST, MALE, plan(), plan_touches_only_the_difference(), RULE_PREF, rules()

### Community 123 - "DirectRelay"
Cohesion: 0.16
Nodes (9): DirectRelay, is_local_or_private_ip(), resolve_direct_domain(), run_full_preresolve_task(), test_direct_relay_active_tcp_cleanup_purge(), test_direct_relay_bypasses_icmp_cleanly(), test_direct_relay_malformed_packets_rejected_safely(), test_run_full_preresolve_task_preserves_dynamic_ips() (+1 more)

### Community 124 - "UplinkState"
Cohesion: 0.14
Nodes (13): adaptive_rate_adjusts_at_most_once_per_interval(), adaptive_rate_backs_off_on_congestion_and_recovers(), adaptive_token_refill_uses_per_stream_rate(), bulk_udp_with_small_packets_loses_the_pin(), FlowKey, V4, V6, FlowState (+5 more)

### Community 125 - "start_tproxy_child_with_retry"
Cohesion: 0.23
Nodes (12): read_tproxy_child_error(), remove_tproxy_status_file(), spawn_tproxy_child(), start_tproxy_child_with_retry(), stop_tproxy_child(), tproxy_child_startup_error_is_compact_and_cleaned_with_status(), tproxy_error_path(), tproxy_start_error_is_retryable() (+4 more)

### Community 126 - "setup_letsencrypt_ip_tls"
Cohesion: 0.19
Nodes (16): adopt_letsencrypt_certificate(), certificate_is_mrtt_letsencrypt(), certificate_is_mrtt_rcgen(), detect_public_ipv4(), disable_letsencrypt_renewal_timer(), generate_rcgen_fallback_certificate(), install_letsencrypt_renewal_timer(), is_public_ipv4() (+8 more)

### Community 127 - "UpdateLaunchGuard.kt"
Cohesion: 0.24
Nodes (3): shouldDiscardStaleSavedState(), UpdateLaunchGuard, UpdateLaunchGuardTest

### Community 128 - "join_as"
Cohesion: 0.22
Nodes (11): answer_iq(), attr(), bridge_socket_url(), connect_tcp(), deliver(), elements(), join_as(), open_bridge_socket() (+3 more)

### Community 129 - "lock_password_epoch"
Cohesion: 0.13
Nodes (16): concurrent_session_admission_accepts_each_new_session_key(), CREDENTIALS_SNAPSHOT, current_password_epoch(), disconnect_request_authorized(), EPOCHS_SNAPSHOT, lock_existing_password_epoch(), lock_password_epoch(), PasswordEpochDecision (+8 more)

### Community 130 - "opera/mod.rs"
Cohesion: 0.16
Nodes (14): base_opera_headers(), BrowserLeaksResponse, build_http2_settings(), build_opera_settings(), build_sec_ch_ua(), build_user_agent(), new_opera_emulator(), OPERA129_AKAMAI_HASH (+6 more)

### Community 131 - "stream_control_payload"
Cohesion: 0.16
Nodes (9): stream_control_payload(), stream_control_payload_caps_untrusted_worker_list_at_u8_limit(), stream_control_payload_is_compact_and_deterministic(), stream_control_payload_represents_all_supported_workers(), stream_repair_round_deduplicates_and_escalates_by_sequence(), stream_repair_round_emits_alive_after_recovery(), StreamInventory, StreamRepairRound (+1 more)

### Community 132 - "ServerApi"
Cohesion: 0.13
Nodes (4): ServerApi, ServerApiException, ServerNotRunningException, ServerApiResponseTest

### Community 133 - "ProxyRoute"
Cohesion: 0.28
Nodes (4): POLICY_LOCK, ProxyRoute, request_tproxy_status(), TproxyStatsSnapshot

### Community 134 - "Formatter"
Cohesion: 0.25
Nodes (3): BotChallenge, RateLimit, ShowTypeMismatch

### Community 135 - "resolveStoredSecret"
Cohesion: 0.23
Nodes (7): resolveStoredSecret(), StoredSecret, StoredSecretState, Missing, Readable, Unreadable, StoredSecretStateTest

### Community 137 - "FiftyStreamRig"
Cohesion: 0.33
Nodes (17): assert_fifty_stream_healthy(), collect_fifty_stream_report(), dispatch_one(), FiftyStreamReport, FiftyStreamRig, print_fifty_stream_report(), pump_bursts(), pump_constant() (+9 more)

### Community 138 - "ControlEvent"
Cohesion: 0.13
Nodes (14): client_fixture_creates_hot_session_and_control_event_through_engine(), client_fixture_with_wrong_server_key_cannot_create_a_hot_session(), ControlEvent, IngressBeforeTunnel, IoCounters, Payload, SessionClosed, SessionCreated (+6 more)

### Community 139 - "VkAuthWebViewManager.kt"
Cohesion: 0.14
Nodes (5): VkAuthDialog(), VkAuthPhase, LOGIN, TOKEN, VkAuthWebViewManager

### Community 141 - "resolveTunnelPasswordStorage"
Cohesion: 0.25
Nodes (3): resolveTunnelPasswordStorage(), shouldResetUnreadableTunnelPassword(), TunnelPasswordStorageTest

### Community 142 - "TunnelStartupProgress"
Cohesion: 0.09
Nodes (8): NetworkPauseGate, TunnelStartupProgress, TunnelStartupStage, READY, WAITING_FOR_CREDENTIALS, WAITING_FOR_TURN_OR_PEER, NetworkPauseGateTest, TunnelStartupProgressTest

### Community 144 - "socks_command"
Cohesion: 0.38
Nodes (6): append_socks_address(), parses_socks5_udp_ipv4_response(), read_socks_address(), socks_command(), socks_udp_response(), verify_socks5_udp_associate()

### Community 145 - "worker_loop"
Cohesion: 0.10
Nodes (14): config_flight_is_globally_exclusive_and_released_by_drop(), ConfigFlightGuard, CredentialLease, delivered_config_prevents_new_flight(), GroupContext, GroupCredentials, HashAvailability, pause_gate_blocks_new_work_until_resume() (+6 more)

### Community 146 - "build_linux.sh"
Cohesion: 0.13
Nodes (15): AR_aarch64_unknown_linux_musl, AR_armv7_unknown_linux_musleabihf, AR_x86_64_unknown_linux_musl, build_variant(), CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER, CARGO_TARGET_ARMV7_UNKNOWN_LINUX_MUSLEABIHF_LINKER, CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER, CC_aarch64_unknown_linux_musl (+7 more)

### Community 147 - ".startAutoCalls"
Cohesion: 0.24
Nodes (4): ActiveCall, PendingLog, StartResult, VkAutoCallsManager

### Community 148 - "AppUpdate.kt"
Cohesion: 0.24
Nodes (15): applyNoCacheHeaders(), AppReleaseInfo, fetchGitHubApi(), fetchHttpText(), fetchLatestReleaseInfo(), isLegacyRelease(), isNewerVersion(), normalizeVersionTag() (+7 more)

### Community 150 - "MrttMaxToken"
Cohesion: 0.16
Nodes (3): MrttMaxToken, JsonOpts, JsonOpts

### Community 151 - ".send_client_flight"
Cohesion: 0.19
Nodes (12): wrap_key_material(), compute_verify_data(), derive_cipher_key(), derive_dtls_srtp_deterministic(), derive_dtls_srtp_keys(), derive_extended_master_secret(), derive_master_secret(), derive_record_keys() (+4 more)

### Community 152 - ".connect"
Cohesion: 0.33
Nodes (4): ensure_linux_platform(), port_is_listening(), spawn_rule_watchdog(), validate_config()

### Community 153 - "TcpSocketState"
Cohesion: 0.40
Nodes (5): TcpSocketState, Close, Closed, Closing, Normal

### Community 154 - ".accept"
Cohesion: 0.21
Nodes (6): read_diagnostic_request(), ReplayState, run_dpi_server(), run_syscalls_server(), serve_syscalls_client(), SyscallsClient

### Community 156 - "CaptchaSolver"
Cohesion: 0.24
Nodes (5): CaptchaSolver, device_info(), VkCaptchaError, load_saved_profile(), SavedProfile

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

### Community 161 - "ProfileRepository"
Cohesion: 0.22
Nodes (6): ConnectionProfile, decodeSubscription(), encodeSubscription(), ProfileRepository, Subscription, SubscriptionCodecTest

### Community 162 - "cached_system_stats"
Cohesion: 0.19
Nodes (11): cached_system_stats(), proc_kib(), process_memory(), process_memory_reads_linux_status_fields(), process_memory_rollup(), process_memory_rollup_reads_attribution_fields(), ProcessMemory, ProcessMemoryRollup (+3 more)

### Community 163 - "DtlsCertificate"
Cohesion: 0.16
Nodes (7): civil_from_days(), days_from_civil(), dtls_rng(), DtlsCertificate, public_point_bytes(), random_u64(), utc_today()

### Community 164 - "EphemeralSecret"
Cohesion: 0.29
Nodes (4): EphemeralSecret, P256, X25519, verify_p256_signature()

### Community 166 - "PacketSink"
Cohesion: 0.22
Nodes (3): PacketSink, TunTxSlot, UdpTxSlot

### Community 167 - "parseServerRoutingRule"
Cohesion: 0.35
Nodes (3): isRoutingPackageName(), parseServerRoutingRule(), ServerRoutingRuleTest

### Community 168 - "die"
Cohesion: 0.14
Nodes (27): archive_mrtt_docker_state(), check_kernel_compatibility(), cleanup_deploy_uploads(), default_exit_code(), diagnose_systemd_failure(), die(), ensure_mrtt_directory(), ensure_tun_device() (+19 more)

### Community 170 - "cascade.sh"
Cohesion: 0.83
Nodes (3): ensure_wg(), cascade.sh script, stop_link()

### Community 172 - "arc"
Cohesion: 0.24
Nodes (3): HEARTBEAT_TICKS, Stats, TICK

### Community 173 - "WifiAutoPausePolicy.kt"
Cohesion: 0.22
Nodes (7): completeWifiAutoPause(), runWifiAutoPauseTransition(), tunnelPowerIsOn(), WifiAutoPauseAction, NONE, PAUSE, RESUME

### Community 174 - "Test"
Cohesion: 0.26
Nodes (3): TunnelLogPolicy, withoutLogStickers(), TunnelLogPolicyTest

### Community 176 - "ServerArchitecture"
Cohesion: 0.50
Nodes (4): ServerArchitecture, AMD64, ARM64, ARMV7

### Community 178 - "jsonobject"
Cohesion: 0.23
Nodes (3): MaxAuthCredentials, MaxAuthPage, MaxAuthPageTest

### Community 185 - "ExtractorResult"
Cohesion: 0.20
Nodes (9): ExtractorResult, AccessToken, ApiServer, Calls, ExternalUserId, ExtractedAtUtc, SessionKey, SessionSecretKey (+1 more)

### Community 187 - "ProtocolCommand"
Cohesion: 0.10
Nodes (19): DPI_BROADCAST, DPI_RING, DpiFrame, GLOBAL_IO_COUNTERS, ProtocolCommand, ActivateTunnel, BroadcastPlain, CompactMemory (+11 more)

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
Cohesion: 0.29
Nodes (7): test_dispatch_large_packet_direct_relay_bypass(), test_dispatch_malformed_packets_safety(), test_select_cascade_waterfall_cursor(), test_select_cursor_bounds_after_scale_down(), test_try_follow_stream_respects_deficit_window(), uplink_cascade_pacing_pins_spaced_ticks_to_worker_zero(), uplink_cascade_waterfall_fills_worker_zero_then_overflows()

### Community 193 - "log_step"
Cohesion: 0.36
Nodes (9): certbot_supports_ip_certificates(), ensure_docker(), ensure_letsencrypt_client(), install_prerequisites(), log_step(), migrate_legacy_install(), pkg_install(), pkg_install_with_refresh() (+1 more)

### Community 194 - "VkApiResult"
Cohesion: 0.27
Nodes (7): ApiError, Failed, Ok, StartedCall, VkApi, VkApiResult, runVkCallAttempts()

### Community 195 - "CallPayload"
Cohesion: 0.20
Nodes (10): CallPayload, ConversationId, Endpoint, ExtractedAtUtc, GroupId, SignalingToken, Stun, Turn (+2 more)

### Community 196 - "InfoTab.kt"
Cohesion: 0.05
Nodes (15): MrttSpacing, ExpandableSectionCard(), InfoHeroCard(), ProjectLinkRow(), WideActionTile(), buildSupportReport(), InfoTab(), openUrlInBrowser() (+7 more)

### Community 197 - "MRTT / MAX (экспериментальная интеграция)"
Cohesion: 0.22
Nodes (8): GitHub Actions: тестовое APK для приватного репозитория, MRTT / MAX (экспериментальная интеграция), Настройка MAX и проверка, Проверки и прозрачность, Протокольные ограничения и диагностика, Сборка исходного кода (Windows), Только новая схема ссылок, Что именно реализовано

### Community 198 - "wrap.rs"
Cohesion: 0.12
Nodes (8): concurrent_shared_native_calls_are_stable(), derive_rustcrypto(), deterministic_and_password_bound(), wrap_equivalence_case!, WRAP_INFO, WRAP_KEY_LEN, WRAP_SALT, WRAP_SALT_BYTES

### Community 199 - ".onCreate"
Cohesion: 0.16
Nodes (3): shouldRequestBatteryOptimizationExemption(), MainActivity, BatteryOptimizationPromptPolicyTest

### Community 200 - "native_client_provenance.ps1"
Cohesion: 0.46
Nodes (7): Assert-Elf(), Assert-RegularFile(), Assert-RustArtifact(), Get-BuildInputHash(), Get-CargoPackage(), Get-Sha256(), Test-BytePattern()

### Community 207 - "BufferClass"
Cohesion: 0.50
Nodes (4): BufferClass, Jumbo, Normal, Tcp

### Community 208 - "server_asset_provenance.ps1"
Cohesion: 0.57
Nodes (6): Assert-RegularFile(), Assert-ServerBinary(), Get-BuildInputHash(), Get-CargoPackage(), Get-Sha256(), Get-WireProtocolRevision()

### Community 209 - "result"
Cohesion: 0.13
Nodes (5): run(), SrtpMode, Auto, Dtls, Wrap

### Community 213 - "CsqttrMaxToken.csproj"
Cohesion: 0.33
Nodes (5): net8.0, Microsoft.Playwright (1.47.0), System.CommandLine (2.0.0-beta4.22272.1), System.Text.Json (8.0.5), Microsoft.NET.Sdk

### Community 214 - "test_max_webview_theme.cjs"
Cohesion: 0.15
Nodes (8): assert, fs, path, root, sandbox, source, storage, vm

### Community 215 - "TunWriteState"
Cohesion: 0.33
Nodes (6): TunWriteState, Backoff, Complete, Dropped, Wait, Yield

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
Cohesion: 0.05
Nodes (51): ClientDevice, objects(), ProxyProfile, ProxyState, RoutingRuleData, ServerClient, ServerControl, ServerExit (+43 more)

### Community 228 - "VpsServer"
Cohesion: 0.26
Nodes (5): ServerRepository, VpsServer, runDeployJob(), startDeploy(), startUninstall()

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

### Community 247 - "DnsDomainMap"
Cohesion: 0.16
Nodes (11): DnsDomainMap, evaluate_route(), ip_verdict(), IpRule, Cidr, Range, Single, RouteVerdict (+3 more)

### Community 249 - "parseMrttLink"
Cohesion: 0.11
Nodes (18): ConnectionSource, resolveConnectionSource(), decodeBase64Safe(), decodeQueryComponent(), decryptMrttPayload(), encryptMrttPayload(), format(), formatMrttLink() (+10 more)

### Community 251 - "StreamDriverEvent"
Cohesion: 0.33
Nodes (5): StreamDriverEvent, Frame, Timer, Wake, WriteFailure

### Community 252 - "flush_limited_relay_queue"
Cohesion: 0.26
Nodes (6): enqueue_limited_relay_packet(), flush_limited_relay_queue(), next_limited_relay_deadline(), QueuedRelayPacket, RelayFault, StreamRateLimiter

### Community 259 - "Изменения в этом архиве"
Cohesion: 0.50
Nodes (3): Границы готовности, Изменения в этом архиве, Поправки сборки и тестирования 22 октября 2026

### Community 269 - "getconf_credential_access"
Cohesion: 0.38
Nodes (7): CredentialAccess, Main, Slot, getconf_credential_access(), resolve_routing_rule_access(), resolve_worker_limit_access(), test_two_stage_handshake_getworkers_parsing_and_limits()

## Knowledge Gaps
- **329 isolated node(s):** `DEBIAN_FRONTEND`, `TERM`, `SCRIPT_VERSION`, `LOG_FILE`, `PEER_PORT` (+324 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 1842 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **68 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SettingsStore` connect `SettingsStore` to `TunnelService`, `MainActivity.kt`, `SettingsTab.kt`, `LogLine.kt`, `TunnelLifecycleState`, `resolveTunnelPasswordStorage`, `ExceptionsTab.kt`, `.startAutoCalls`, `TunnelManager`, `ProfileRepository`, `SettingsStore.kt`, `InfoTab.kt`, `TurnSubnetRegistry`, `Preferences`, `SecureStringStore.kt`, `.normalize`, `TunnelAuthSnapshot`, `SubscriptionsSection.kt`, `VpsServer`, `InfoTab.kt`, `.updateLog`, `VkHashValidationStatus`, `Test`, `MaxAuthLifecycleTest.kt`, `TunnelQuickActions.kt`, `parseMrttLink`?**
  _High betweenness centrality (0.089) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `SettingsStore` (e.g. with `.pendingBindingSurvivesBackgroundWithoutReloadingTheWebView()` and `.onCreate()`) actually correct?**
  _`SettingsStore` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `DEBIAN_FRONTEND`, `TERM`, `SCRIPT_VERSION` to the rest of the system?**
  _329 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `rust-server/protocol.rs` be split into smaller, more focused modules?**
  _Cohesion score 0.02303445650104702 - nodes in this community are weakly interconnected._
- **Why does `TunnelManager` connect `TunnelManager` to `TunnelService`, `SubscriptionsSection.kt`, `SettingsStore`, `MainActivity.kt`, `.updateLog`, `InfoTab.kt`, `InfoTab.kt`, `TunnelLifecycleState`, `DeployOperations.kt`, `TunnelStartupProgress`, `TunnelManager.kt`, `TunnelService.kt`, `ExceptionsTab.kt`, `WorkerRecoveryPolicy`, `performDeploy`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `TunnelManager` (e.g. with `NetworkPauseGate` and `TunnelLifecycleState`) actually correct?**
  _`TunnelManager` has 3 INFERRED edges - model-reasoned connections that need verification._
- **Should `TunnelService` be split into smaller, more focused modules?**
  _Cohesion score 0.07056962025316456 - nodes in this community are weakly interconnected._