package com.mrtt.client.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeployScriptPreflightTest {
    private fun script(): String {
        val candidates = listOf(
            File("app/src/main/assets/deploy.sh"),
            File("src/main/assets/deploy.sh"),
        )
        return candidates.first(File::isFile).readText().replace("\r\n", "\n")
    }

    private fun deployOperations(): String {
        val candidates = listOf(
            File("app/src/main/java/com/mrtt/client/ui/DeployOperations.kt"),
            File("src/main/java/com/mrtt/client/ui/DeployOperations.kt"),
        )
        return candidates.first(File::isFile).readText().replace("\r\n", "\n")
    }

    @Test
    fun `preflight performs working kernel probes`() {
        val deploy = script()
        assertTrue(deploy.contains("ip tuntap add dev \"\$probe_iface\" mode tun"))
        assertTrue(deploy.contains("setup_nat_and_firewall"))
        assertTrue(deploy.contains("iptables -w \"\$XT_WAIT\" -t nat -C POSTROUTING"))
        assertTrue(deploy.contains("probe_tun_support"))
        assertTrue(deploy.contains("prepare_uploaded_release()"))
        assertTrue(deploy.contains("run_platform_preflight"))
    }

    @Test
    fun `installer accepts the supported server architectures`() {
        val deploy = script()
        assertTrue(deploy.contains("x86_64|amd64"))
        assertTrue(deploy.contains("aarch64|arm64"))
        assertTrue(deploy.contains("armv7l|armv7|armhf"))
        assertTrue(deploy.contains("Поддерживаются: x86_64, aarch64, armv7l"))
    }

    @Test
    fun `client uploads the release before one atomic runtime switch`() {
        val deploy = script()
        val installBranch = deploy.substringAfter("install|--install|-i)")
        val uploadCheck = installBranch.indexOf("prepare_uploaded_release")
        val cleanup = installBranch.indexOf("mrtt_cleanup")
        val binary = installBranch.indexOf("setup_mrtt_binary")
        val preflight = installBranch.indexOf("run_platform_preflight")
        val firewall = installBranch.indexOf("setup_nat_and_firewall")
        assertTrue(uploadCheck >= 0)
        assertTrue(cleanup >= 0)
        assertTrue(binary >= 0)
        assertTrue(binary > preflight)
        assertTrue(binary > firewall)
        assertTrue(installBranch.contains("run_timed \"preflight\" run_platform_preflight"))
        assertTrue(cleanup > uploadCheck)
        assertTrue(binary > cleanup)
        assertTrue(installBranch.contains("local total_started=\$SECONDS\n            detect_os\n            run_timed \"перенос старой установки\" migrate_legacy_install\n            run_timed \"зависимости\" install_prerequisites\n            require_runtime_tools"))

        val client = deployOperations()
        assertFalse(client.contains("bash /tmp/deploy.sh prepare"))
        assertTrue(client.indexOf("/tmp/.mrtt-upload-server") < client.indexOf("bash /tmp/deploy.sh install"))
    }

    @Test
    fun `activation uses direct runtime paths and has no staged candidate`() {
        val deploy = script()
        assertTrue(deploy.contains("readonly UPLOAD_BINARY=\"/tmp/.mrtt-upload-server\""))
        assertTrue(deploy.contains("install -m 0755 \"\$UPLOAD_BINARY\" /usr/local/bin/mrtt"))
        assertTrue(deploy.contains("install -m 0600 \"\$UPLOAD_ENV_FILE\" \"\$MRTT_ENV_FILE\""))
        assertTrue(deploy.contains("rm -f /usr/local/bin/mrtt"))
        assertTrue(deploy.contains("rm -rf /usr/local/lib/mrtt"))
        assertTrue(deploy.contains("clear_runtime_config_preserving_database()"))
        assertFalse(deploy.contains("mrtt.next.XXXXXX"))
        assertFalse(deploy.contains("mrtt-stage"))
        assertFalse(deploy.contains("verify_staged_release"))
        assertFalse(deploy.contains("MRTT_DEPLOY_READY_FOR_UPLOAD"))
        assertTrue(deploy.contains("probe_tun_support"))
    }

    @Test
    fun `reserved runtime directories recover from a failed previous install`() {
        val deploy = script()
        assertTrue(deploy.contains("ensure_mrtt_directory()"))
        assertTrue(deploy.contains("rm -rf -- \"\$path\" || die \"Не удалось очистить путь MRTT: \$path\""))
        assertTrue(deploy.contains("ensure_mrtt_directory \"\$MRTT_CONFIG_DIR\" 700"))
        assertTrue(deploy.contains("ensure_mrtt_directory /usr/local/lib/mrtt 755"))
        assertTrue(deploy.contains("setup_mrtt_environment() {\n    ensure_mrtt_directory \"\$MRTT_CONFIG_DIR\" 700"))
        assertTrue(deploy.contains("install -m 0600 \"\$UPLOAD_ENV_FILE\" \"\$MRTT_ENV_FILE\""))
    }

    @Test
    fun `redeploy removes old runtime config but preserves SQLite and its WAL files`() {
        val deploy = script()
        assertTrue(deploy.contains("readonly MRTT_DATABASE_FILE=\"mrtt.db\""))
        assertTrue(deploy.contains("readonly MRTT_DATABASE_WAL_FILE=\"mrtt.db-wal\""))
        assertTrue(deploy.contains("readonly MRTT_DATABASE_SHM_FILE=\"mrtt.db-shm\""))
        assertTrue(deploy.contains("clear_runtime_config_preserving_database"))
        assertTrue(deploy.contains("\"\$MRTT_DATABASE_FILE\"|\"\$MRTT_DATABASE_WAL_FILE\"|\"\$MRTT_DATABASE_SHM_FILE\"|\"\$MRTT_LEGACY_MIGRATION_JSON\"|\"\$MRTT_LEGACY_MIGRATION_IMPORTED_JSON\"|web_cert.pem|web_key.pem|letsencrypt-ip.env|runtime-archive) continue"))
        assertFalse(deploy.contains("find \"\$MRTT_CONFIG_DIR\" -mindepth 1 -maxdepth 1"))
    }

    @Test
    fun `docker uses minimal capabilities and repeats probes inside container`() {
        val deploy = script()
        assertTrue(deploy.contains("--cap-drop ALL"))
        assertTrue(deploy.contains("--cap-add NET_ADMIN"))
        assertTrue(deploy.contains("--cap-add NET_RAW"))
        assertTrue(deploy.contains("--device /dev/net/tun:/dev/net/tun"))
        assertTrue(deploy.contains("--security-opt seccomp=unconfined"))
        assertTrue(deploy.contains("probe_docker_runtime"))
        assertTrue(deploy.split("--cap-drop ALL").size >= 3)
        assertTrue(deploy.contains("COPY network-up.sh /usr/local/lib/mrtt/network-up.sh"))
        assertTrue(deploy.contains("exec /usr/local/bin/mrtt \\\"\$@\\\""))
        assertTrue(deploy.contains("write_network_helper \"\$context_dir/network-up.sh\""))
    }

    @Test
    fun `docker image build retries DNS with Yandex resolvers`() {
        val deploy = script()
        assertTrue(deploy.contains("DOCKER_BUILD_TIMEOUT_SECONDS"))
        assertTrue(deploy.contains("docker build --network host"))
        assertTrue(deploy.contains("MRTT_BUILD_DNS_PRIMARY:-"))
        assertTrue(deploy.contains("FROM alpine:3.21"))
        assertTrue(deploy.contains("apk add --no-cache --no-progress ca-certificates iproute2 iptables"))
        assertTrue(deploy.contains("MRTT_BUILD_DNS_PRIMARY=77.88.8.8"))
        assertTrue(deploy.contains("MRTT_BUILD_DNS_SECONDARY=77.88.8.1"))
        assertTrue(deploy.contains("Temporary failure resolving|Could not resolve|Name or service not known"))
    }

    @Test
    fun `deploy payload leaves DNS selection to the persisted server database`() {
        val deploy = script()
        val client = deployOperations()
        assertFalse(client.contains(".put(\"dns\""))
        assertFalse(client.contains("dnsValue"))
        assertFalse(deploy.contains("Не удалось сохранить DNS из существующей SQLite-конфигурации"))
        assertTrue(client.contains("\"mrtt-deploy.json\" -> \"настройки доступа\""))
    }

    @Test
    fun `management API is probed on loopback only`() {
        val deploy = script()
        assertTrue(deploy.contains("http://127.0.0.1:${'$'}{WEB_PORT}/api/stats"))
        assertFalse(deploy.contains("fw_add_input_tcp \"${'$'}WEB_PORT\""))
        assertFalse(deploy.contains("/api/login"))
    }

    @Test
    fun `configured network is verified after applying rules and after startup`() {
        val deploy = script()
        assertTrue(deploy.contains("verify_configured_network()"))
        assertTrue(deploy.split("verify_configured_network").size >= 4)
        assertTrue(deploy.contains("-j MASQUERADE >/dev/null 2>&1"))
        assertTrue(deploy.contains("-j TCPMSS --clamp-mss-to-pmtu 2>/dev/null"))
    }

    @Test
    fun `startup removes stale tunnel state and waits through the TUN initialization window`() {
        val deploy = script()
        assertTrue(deploy.contains("remove_mrtt_tun_interface()"))
        assertTrue(deploy.contains("timeout 2 ip link del \"\$MRTT_IFACE\""))
        assertTrue(deploy.contains("write_tun_recovery_helper /usr/local/lib/mrtt/tun-recover.sh"))
        assertTrue(deploy.contains("ExecStartPre=/usr/local/lib/mrtt/tun-recover.sh"))
        assertTrue(deploy.contains("release_owned_peer_port()"))
        assertTrue(deploy.contains("mrtt_process_is_owned()"))
        assertTrue(deploy.contains("""UDP/\${'$'}PEER_PORT runtime PID \${'$'}pid; terminating"""))
        assertTrue(deploy.contains("readonly START_STABILITY_SECONDS=\"\${MRTT_START_STABILITY_SECONDS:-4}\""))
        assertTrue(deploy.contains("StartLimitIntervalSec=60"))
        assertTrue(deploy.contains("RestartSec=3"))
        assertTrue(deploy.contains("COPY tun-recover.sh /usr/local/lib/mrtt/tun-recover.sh"))
        assertTrue(deploy.contains("/usr/local/lib/mrtt/tun-recover.sh; exec /usr/local/bin/mrtt"))
    }

    @Test
    fun `runtime mode switch removes MRTT paths and force releases the configured peer port`() {
        val deploy = script()
        val client = deployOperations()
        assertTrue(deploy.contains("docker_container_runs_mrtt()"))
        assertTrue(deploy.contains("docker top \"\$container\" -eo pid,args"))
        assertTrue(deploy.contains("docker-\${container_id}.scope"))
        assertTrue(deploy.contains("remove_all_mrtt_docker_containers"))
        assertTrue(deploy.contains("stop_all_running_mrtt_systemd_units"))
        assertTrue(deploy.contains("remove_all_mrtt_systemd_units"))
        assertTrue(deploy.contains("assert_peer_port_is_available"))
        assertTrue(deploy.contains("force_release_peer_port"))
        assertTrue(deploy.contains("cleanup_legacy_proxy_interfaces"))
        assertTrue(deploy.contains("docker_container_for_pid"))
        assertTrue(deploy.contains("docker rm -f \"\$container\""))
        assertTrue(deploy.contains("--label com.mrtt.managed=true"))
        assertTrue(deploy.contains("archive_mrtt_docker_state()"))
        assertTrue(deploy.contains("MRTT_RUNTIME_ARCHIVE_DIR"))
        assertTrue(deploy.contains("runtime-archive) continue"))
        assertTrue(deploy.contains("force_stop_mrtt_processes"))
        assertTrue(deploy.contains("all_mrtt_process_pids"))
        assertTrue(deploy.contains("mrtt_peer_port_is_owned"))
        assertTrue(deploy.contains("mrtt_process_owns_peer_port"))
        assertTrue(deploy.contains("readlink \"/proc/\$pid/exe\""))
        assertTrue(deploy.contains("--config-dir \${MRTT_CONFIG_DIR}"))
        assertTrue(deploy.contains("kill -KILL \"\$pid\""))
        assertTrue(deploy.contains("kill -TERM \"\$pid\""))
        assertTrue(deploy.contains("принудительное освобождение"))
        assertFalse(deploy.contains("unknown runtime; refusing to alter it"))
        assertFalse(deploy.contains("MRTT не будет их удалять"))
        assertFalse(deploy.contains("pkill -"))
        assertFalse(deploy.contains("pgrep -x mrtt"))
        assertFalse(deploy.contains("ipt_del_repeat"))
        assertFalse(deploy.contains("nft delete table ip mrtt"))
        assertFalse(deploy.contains("fw_add_input_tcp \"\$SSH_PORT\""))
        assertFalse(deploy.contains("--dport \"\$SSH_PORT\" -m comment --comment \"\$IPT_COMMENT\""))
        assertTrue(client.contains("mrtt-uninstall-"))
        assertFalse(client.contains("pkill -x mrtt"))
        assertFalse(client.contains("docker rm -f mrtt"))
        assertFalse(client.contains("nft delete table ip mrtt"))
    }

    @Test
    fun `runtime cleanup stops processes before deleting MRTT runtime paths`() {
        val deploy = script()
        val cleanup = deploy.substringAfter("mrtt_cleanup() {").substringBefore("setup_sysctl()")
        val forceStop = deploy.substringAfter("force_stop_mrtt_processes() {")
            .substringBefore("force_release_peer_port()")

        assertTrue(forceStop.contains("kill -KILL \"\$pid\""))
        assertTrue(cleanup.indexOf("force_stop_mrtt_processes") <
            cleanup.indexOf("rm -f /usr/local/bin/mrtt"))
        assertTrue(cleanup.contains("systemctl daemon-reload || die \"systemctl daemon-reload завершился ошибкой после удаления старого runtime\""))
    }

    @Test
    fun `systemd runtime discovery and shutdown tolerate expected nonzero states`() {
        val deploy = script()
        assertTrue(deploy.contains("if systemd_unit_runs_mrtt \"\$unit\"; then"))
        assertFalse(deploy.contains("systemd_unit_runs_mrtt \"\$unit\" &&"))
        assertTrue(deploy.contains("systemctl list-units --type=service --all --no-legend --plain 2>/dev/null || true"))
        assertTrue(deploy.contains("systemctl list-unit-files --type=service --no-legend --plain 2>/dev/null || true"))
        assertTrue(deploy.contains("if ! timeout 10 systemctl stop \"\$unit\" >/dev/null 2>&1; then"))
        assertTrue(deploy.contains("systemctl is-active --quiet \"\$unit\"; then\n            die \"Не удалось остановить systemd runtime MRTT: \$unit\""))
    }

    @Test
    fun `systemd service leaves resource limits to the VPS defaults`() {
        val deploy = script()
        assertFalse(deploy.contains("LimitNOFILE="))
        assertFalse(deploy.contains("LimitMEMLOCK="))
        assertFalse(deploy.contains("LimitNPROC="))
        assertFalse(deploy.contains("TasksMax="))
    }

    @Test
    fun `no web panel certificate is issued and no HTTP port is opened`() {
        val deploy = script()
        assertTrue(deploy.contains("disable_letsencrypt_renewal_timer"))
        assertFalse(deploy.contains("run_timed \"Let’s Encrypt\""))
        assertFalse(deploy.contains("fw_add_input_tcp \"${'$'}LE_HTTP_PORT\""))
    }

    @Test
    fun `certificate ownership keeps user TLS untouched and renews only MRTT certificates`() {
        val deploy = script()
        assertTrue(deploy.contains("certificate_is_mrtt_rcgen()"))
        assertTrue(deploy.contains("certificate_is_mrtt_letsencrypt()"))
        assertTrue(deploy.contains("web_certificate_is_mrtt_managed()"))
        assertTrue(deploy.contains("web_certificate_is_user_managed()"))
        assertTrue(deploy.contains("CN=rcgen self signed cert"))
        assertTrue(deploy.contains("O=Let's Encrypt"))
        assertTrue(deploy.contains("IP Address:\${saved_ip}"))
        assertTrue(deploy.contains("rm -f -- \"\$MRTT_LE_STATE_FILE\""))
        assertTrue(deploy.contains("disable_letsencrypt_renewal_timer"))
        assertTrue(deploy.contains("MRTT не изменяет его и отключает собственное продление"))
    }
}
