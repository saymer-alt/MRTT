package com.mrtt.client.ui

import android.content.Context
import com.mrtt.client.MrttConstants
import com.mrtt.client.DeployManager
import com.mrtt.client.SshHostKeyStore
import com.mrtt.client.TunnelManager
import net.schmizz.sshj.SSHClient as SshjClient
import net.schmizz.sshj.xfer.FileSystemFile
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.Security
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val CMD_TIMEOUT = MrttConstants.Timeouts.DEPLOY_CMD_TIMEOUT_MS
private const val SFTP_UPLOAD_ATTEMPTS = 2
private const val UPLOAD_RECONNECT_ATTEMPTS = 1

internal fun sanitizeSshPassword(value: String): String =
    value.replace("\r", "").replace("\n", "")

internal fun isSuccessfulDeployResult(exitStatus: Int, output: String): Boolean =
    exitStatus == 0 && output.lineSequence().any { it.trim() == "MRTT_DEPLOY_OK" }

private fun deployFailureDetail(output: String): String? =
    output.lineSequence()
        .map(String::trim)
        .lastOrNull { it.startsWith("MRTT_DEPLOY_ERROR|") }
        ?.split("|", limit = 3)
        ?.getOrNull(2)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.take(360)

internal fun deployFailureMessage(exitStatus: Int, output: String): String {
    val detail = deployFailureDetail(output)
    fun withDetail(message: String): String =
        if (detail == null) message else "$message Причина: $detail"

    return when {
        output.lineSequence().any { it.trim() == "MRTT_DEPLOY_ROLLED_BACK" } || exitStatus == 30 ->
            withDetail("Новый релиз не прошёл проверку; предыдущая установка восстановлена")
        output.lineSequence().any { it.trim() == "MRTT_DEPLOY_ROLLBACK_FAILED" } || exitStatus == 31 ->
            withDetail("Новый релиз не запустился, а автоматический rollback завершился с ошибкой — требуется проверка VPS")
        exitStatus == 20 ->
            withDetail("Кандидат не прошёл предзапусковую проверку; работающий сервер не останавливался")
        exitStatus == 2 ->
            withDetail("Установщик отклонил параметры развёртывания")
        else ->
            detail ?: "Установщик завершился с кодом $exitStatus; подробности сохранены в errors.log"
    }
}

internal const val HOST_KEY_CHANGED = "SSH-ключ сервера изменился: так бывает после переустановки VPS. Логин и пароль тут ни при чём."

internal fun friendlyDeployError(message: String?): String {
    val text = message.orEmpty()
    return when {
        text.contains("during identification", ignoreCase = true) ->
            "VPS закрыл соединение до начала SSH: сервер временно не пускает ваш IP. " +
                "Так работает защита OpenSSH 9.8+ (PerSourcePenalties) после неудачных или оборванных входов, " +
                "или fail2ban. Подождите 10-15 минут или подключитесь через другую сеть (Wi-Fi вместо мобильной)"
        text.contains("Auth fail (public key)", ignoreCase = true) ||
            text.contains("Auth fail (key parse)", ignoreCase = true) ->
            "SSH: Auth fail — ключ не принят сервером. Проверьте: 1) приватный ключ вставлен целиком (-----BEGIN...-----END), 2) публичный ключ добавлен в authorized_keys на VPS, 3) логин верный"
        text.contains("Auth fail", ignoreCase = true) ->
            "SSH-аутентификация отклонена: проверьте логин, пароль и разрешение PasswordAuthentication на VPS"
        text.contains("authentication", ignoreCase = true) ->
            "SSH-аутентификация отклонена: проверьте логин, пароль и разрешение PasswordAuthentication на VPS"
        text.contains("SFTP/SCP upload failed", ignoreCase = true) ->
            "Загрузка на VPS оборвалась даже после автоматического переподключения SSH; проверьте стабильность Wi-Fi и подробную причину в errors.log"
        text.contains("reject HostKey", ignoreCase = true) ||
            text.contains("HostKey", ignoreCase = true) ||
            text.contains("host key", ignoreCase = true) ->
            HOST_KEY_CHANGED
        text.contains("connect timed out", ignoreCase = true) ->
            "VPS не ответил на подключение по SSH: проверьте IP, порт SSH и фаервол"
        text.contains("settlement", ignoreCase = true) ->
            "SSH: приложение и сервер не договорились о шифровании ($text)".take(240)
        text.contains("timeout", ignoreCase = true) ||
            text.contains("timed out", ignoreCase = true) ->
            "Истекло время ожидания SSH/SFTP"
        text.contains("UnknownHost", ignoreCase = true) ||
            text.contains("No route to host", ignoreCase = true) ->
            "VPS недоступен по указанному адресу"
        text.contains("session is down", ignoreCase = true) ||
            text.contains("connection is closed", ignoreCase = true) ->
            "SSH-соединение было разорвано"
        text.contains("Connection refused", ignoreCase = true) ->
            "SSH: соединение отклонено — проверьте порт и доступность VPS"
        text.isBlank() -> "Неизвестная ошибка установки"
        else -> text.take(240)
    }
}

internal enum class DeployOutputLevel { LOG, OK, ERR }

internal data class DeployOutputLine(
    val message: String,
    val level: DeployOutputLevel,
)

private val ANSI_ESCAPE = Regex("\u001B\\[[;\\d]*m")
private val DEPLOY_SYMBOLS = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]")
private val DEPLOY_STATUS_PREFIX = Regex("^\\[(?:OK|ERR|LOG|WARN|✓|!|✗|►)]\\s*", RegexOption.IGNORE_CASE)

internal fun parseDeployOutputLine(rawLine: String): DeployOutputLine? {
    val raw = rawLine.replace(ANSI_ESCAPE, "").trim()
    if (
        raw.isEmpty() ||
            raw.startsWith("MRTT_PROGRESS|") ||
            raw.startsWith("MRTT_DEPLOY_ERROR|") ||
            raw == "MRTT_DEPLOY_OK" ||
            raw == "MRTT_DEPLOY_ROLLED_BACK" ||
            raw == "MRTT_DEPLOY_ROLLBACK_FAILED"
    ) return null

    val warning = raw.startsWith("[WARN]", ignoreCase = true) || raw.startsWith("[!]") || raw.startsWith("⚠")
    val explicitError = raw.startsWith("[ERR]", ignoreCase = true) || raw.startsWith("[✗]") || raw.startsWith("✗")
    val success = raw.startsWith("[OK]", ignoreCase = true) || raw.startsWith("[✓]") || raw.startsWith("✓")
    val lower = raw.lowercase()
    val looksLikeError = explicitError || (!warning && (
        lower.contains("error:") ||
            lower.contains("failed") ||
            lower.contains("fatal") ||
            lower.contains("не запуст") ||
            lower.contains("ошибка")
        ))

    var clean = raw
        .replace(DEPLOY_STATUS_PREFIX, "")
        .replace(DEPLOY_SYMBOLS, "")
        .trim { it.isWhitespace() || it in "═║╔╗╚╝─━" }
        .replace(Regex("\\s+"), " ")
        .trim()
    if (clean.isEmpty() || clean.all { it == '=' || it == '-' || it == '_' }) return null
    if (warning && !clean.startsWith("Предупреждение:", ignoreCase = true)) {
        clean = "Предупреждение: $clean"
    }

    val level = when {
        looksLikeError -> DeployOutputLevel.ERR
        success -> DeployOutputLevel.OK
        else -> DeployOutputLevel.LOG
    }
    return DeployOutputLine(clean.take(500), level)
}

private fun publishDeployOutput(deploy: DeployRun, rawLine: String) {
    val line = parseDeployOutputLine(rawLine) ?: return
    when (line.level) {
        DeployOutputLevel.LOG -> deploy.info(line.message)
        DeployOutputLevel.OK -> deploy.success(line.message)
        DeployOutputLevel.ERR -> deploy.error(line.message)
    }
}

internal enum class ServerArchitecture(
    val assetName: String,
    val displayName: String,
) {
    AMD64("mrtt-linux-amd64", "amd64"),
    ARM64("mrtt-linux-arm64", "ARM64"),
    ARMV7("mrtt-linux-armv7", "ARM32"),
}

internal fun serverArchitectureForMachine(output: String): ServerArchitecture {
    val machine = output.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() }
        ?.lowercase(Locale.ROOT)
        ?: throw IOException("VPS не вернул архитектуру через uname -m")
    return when (machine) {
        "x86_64", "amd64" -> ServerArchitecture.AMD64
        "aarch64", "arm64" -> ServerArchitecture.ARM64
        "armv7l", "armv7", "armhf" -> ServerArchitecture.ARMV7
        else -> throw IOException(
            "Архитектура VPS $machine пока не поддерживается. Поддерживаются: x86_64, aarch64, armv7l",
        )
    }
}

private fun deployAssetLabel(fileName: String): String = when (fileName) {
    "deploy.sh" -> "скрипт установки"
    "mrtt-linux-amd64" -> "сервер MRTT (amd64)"
    "mrtt-linux-arm64" -> "сервер MRTT (ARM64)"
    "mrtt-linux-armv7" -> "сервер MRTT (ARM32)"
    "mrtt.env" -> "настройки веб-панели"
    "mrtt-deploy.json" -> "настройки доступа"
    "hev-socks5-tunnel" -> "C-движок туннеля"
    else -> fileName
}

private fun readableFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f МБ", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f КБ", bytes / 1024.0)
    else -> "$bytes Б"
}

/** Одна установка или удаление: ход отдельно для сервера, в журнале каждая строка подписана его адресом. */
internal class DeployRun(private val id: String, private val host: String) {
    fun progress(value: Float, step: String) = DeployManager.updateProgress(id, value, step)
    fun session(session: java.io.Closeable?) = DeployManager.setSession(id, session)
    fun stop(result: String) = DeployManager.stopDeploy(id, result)
    fun info(message: String) = TunnelManager.addDeployInfoLog("$host: $message")
    fun success(message: String) = TunnelManager.addDeploySuccessLog("$host: $message")
    fun warning(message: String) = TunnelManager.addDeployWarningLog("$host: $message")
    fun error(message: String) = TunnelManager.addDeployErrorLog("$host: $message")
}

private data class SSHExecResult(val output: String, val exitStatus: Int)

private class DeploySSHClient(
    private var ssh: SshjClient,
    private val sudoPass: String,
    private val deploy: DeployRun,
    private val reconnect: ((String) -> SshjClient)? = null,
) {

    private fun reconnectFor(stage: String): Boolean {
        val factory = reconnect ?: return false
        return try {
            runCatching { ssh.disconnect() }
            deploy.info("SSH-соединение восстановление: $stage")
            ssh = factory(stage)
            deploy.session(ssh)
            deploy.success("SSH-соединение восстановлено")
            true
        } catch (error: Exception) {
            DeployManager.writeError(
                "SSH reconnect failed during $stage (${error.javaClass.simpleName}): ${error.message}",
            )
            false
        }
    }

    private fun ensureConnected(stage: String): Boolean =
        ssh.isConnected || reconnectFor(stage)

    fun exec(command: String, timeout: Long = CMD_TIMEOUT): String =
        execResult(command, timeout).output

    fun execResult(command: String, timeout: Long = CMD_TIMEOUT): SSHExecResult {
        if (!ensureConnected("команда")) {
            DeployManager.writeError("SSH exec: клиент отключён перед командой: ${command.take(80)}")
            return SSHExecResult("error: session is down", -1)
        }

        val result = StringBuilder()
        val progressRegex = Regex("^MRTT_PROGRESS\\|(\\d+\\.?\\d*)\\|(.+)$")
        val cmdAdjusted = if (command.contains("sudo") && !command.contains("sudo -S")) {
            command.replace("sudo ", "sudo -S ")
        } else command

        val sshSession = ssh.startSession()
        return try {
            val remoteCmd = sshSession.exec(cmdAdjusted)

            if (cmdAdjusted.contains("sudo -S")) {
                runCatching {
                    remoteCmd.outputStream.write("$sudoPass\n".toByteArray())
                    remoteCmd.outputStream.flush()
                }
            }

            val stdoutDone = CountDownLatch(1)
            val stderrDone = CountDownLatch(1)

            val stdoutThread = Thread({
                try {
                    remoteCmd.inputStream.bufferedReader().forEachLine { line ->
                        val clean = line.replace(ANSI_ESCAPE, "")
                        val match = progressRegex.find(clean.trim())
                        when {
                            match != null -> {
                                val p = match.groupValues[1].toFloatOrNull() ?: 0f
                                val step = match.groupValues[2].trim()
                                deploy.progress(p, step)
                                deploy.info("Этап: $step")
                            }
                            !clean.contains("MRTT_PROGRESS") -> {
                                synchronized(result) { result.appendLine(clean) }
                                publishDeployOutput(deploy, clean)
                                val parsed = parseDeployOutputLine(clean)
                                if (parsed?.level == DeployOutputLevel.ERR) {
                                    DeployManager.writeError("REMOTE: $clean")
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                stdoutDone.countDown()
            }, "deploy-stdout")
            stdoutThread.isDaemon = true
            stdoutThread.start()

            val stderrThread = Thread({
                try {
                    remoteCmd.errorStream.bufferedReader().forEachLine { line ->
                        if (!line.contains("password for")) {
                            val clean = line.replace(ANSI_ESCAPE, "")
                            if (clean.isNotBlank() && !isBenignStderr(clean)) {
                                synchronized(result) { result.appendLine(clean) }
                                DeployManager.writeError("STDERR: $clean")
                                val display = parseDeployOutputLine(clean)?.message ?: clean.trim().take(500)
                                deploy.error("Сервер: $display")
                            }
                        }
                    }
                } catch (_: Exception) {}
                stderrDone.countDown()
            }, "deploy-stderr")
            stderrThread.isDaemon = true
            stderrThread.start()

            val finished = stdoutDone.await(timeout, TimeUnit.MILLISECONDS)
            stderrDone.await(2, TimeUnit.SECONDS)

            if (!finished) {
                DeployManager.writeError("SSH timeout (${timeout / 1000}s): ${command.take(80)}")
                return SSHExecResult("error: timeout", -1)
            }

            remoteCmd.join(3, TimeUnit.SECONDS)
            SSHExecResult(result.toString(), remoteCmd.exitStatus ?: -1)
        } catch (e: Exception) {
            DeployManager.writeError("SSH exec error: ${e.message} | cmd: ${command.take(80)}")
            deploy.error("SSH exec error: ${e.message}")
            SSHExecResult("error: ${e.message}", -1)
        } finally {
            runCatching { sshSession.close() }
        }
    }

    fun upload(localFile: File, remotePath: String) {
        uploadInternal(localFile, remotePath, UPLOAD_RECONNECT_ATTEMPTS)
    }

    private fun uploadInternal(localFile: File, remotePath: String, reconnectsLeft: Int) {
        if (!localFile.isFile || !localFile.canRead()) {
            throw IOException("Local deploy file is unavailable: ${localFile.name}")
        }
        if (!ensureConnected("загрузка ${deployAssetLabel(localFile.name)}")) {
            throw IOException("SSH client disconnected before upload: ${localFile.name}")
        }

        val label = deployAssetLabel(localFile.name)
        deploy.info("Загрузка: $label (${readableFileSize(localFile.length())})")
        var sftpSuccess = false
        var lastError = "SFTP channel did not accept the file"
        repeat(SFTP_UPLOAD_ATTEMPTS) { attempt ->
            try {
                ssh.newSFTPClient().use { sftp ->
                    sftp.put(FileSystemFile(localFile), remotePath)
                    val remoteSize = sftp.lstat(remotePath).size
                    if (remoteSize == localFile.length()) {
                        deploy.success("Загружено: $label")
                        sftpSuccess = true
                        return
                    }
                }
            } catch (e: Exception) {
                lastError = "SFTP: ${e.message ?: e.javaClass.simpleName}"
                DeployManager.writeError(
                    "SFTP upload attempt ${attempt + 1}/$SFTP_UPLOAD_ATTEMPTS failed: " +
                        "${e.message} | file: ${localFile.name}"
                )
                if (attempt + 1 < SFTP_UPLOAD_ATTEMPTS && ssh.isConnected) {
                    Thread.sleep(500)
                }
            }
        }

        if (!sftpSuccess && ssh.isConnected) {
            try {
                DeployManager.writeError("Falling back to SCP for ${localFile.name}...")
                ssh.newSCPFileTransfer().upload(FileSystemFile(localFile), remotePath)
                deploy.success("Загружено (SCP): $label")
                return
            } catch (e: Exception) {
                lastError = "SCP: ${e.message ?: e.javaClass.simpleName}"
                DeployManager.writeError("SCP upload failed: ${e.message} | file: ${localFile.name}")
            }
        }

        if (ssh.isConnected) {
            try {
                DeployManager.writeError("Falling back to chunked stream upload for ${localFile.name}...")
                val tempRemote = "$remotePath.tmp"
                execResult(rootCommand("rm -f '$tempRemote'", sudoPass), 30000L)
                localFile.inputStream().buffered(64 * 1024).use { inputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalUploaded = 0L
                    val totalLength = localFile.length()
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                        val base64 = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                        val cmd = "printf '%s' '$base64' | base64 -d >> '$tempRemote'"
                        val res = execResult(rootCommand(cmd, sudoPass), 60000L)
                        if (res.exitStatus != 0) {
                            throw IOException("Chunk upload failed at offset $totalUploaded")
                        }
                        totalUploaded += bytesRead
                        val percent = (totalUploaded.toFloat() / totalLength.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        deploy.progress(percent * 0.5f, "Загрузка: $label (${(percent * 100).toInt()}%)")
                    }
                }
                val moveRes = execResult(rootCommand("mv -f '$tempRemote' '$remotePath' && chmod 0755 '$remotePath'", sudoPass), 30000L)
                if (moveRes.exitStatus == 0) {
                    deploy.success("Загружено (Stream): $label")
                    return
                }
            } catch (e: Exception) {
                lastError = "stream: ${e.message ?: e.javaClass.simpleName}"
                DeployManager.writeError("Stream upload failed: ${e.message} | file: ${localFile.name}")
            }
        }

        if (reconnectsLeft > 0 && reconnectFor("повторная загрузка $label")) {
            uploadInternal(localFile, remotePath, reconnectsLeft - 1)
            return
        }

        val message = "SFTP/SCP upload failed for ${localFile.name}: $lastError"
        deploy.error("Не удалось загрузить $label: $lastError")
        throw IOException(message)
    }
}

private val bcInitialized = AtomicBoolean(false)

private fun ensureBouncyCastle() {
    if (bcInitialized.compareAndSet(false, true)) {
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}

internal fun createSSHClient(
    context: Context,
    host: String,
    user: String,
    pass: String,
    port: Int = 22,
    privateKey: String = "",
    keyPassphrase: String = "",
): SshjClient {
    require(host.isNotBlank()) { "SSH host is empty" }
    require(user.isNotBlank()) { "SSH login is empty" }
    val keysMode = privateKey.isNotBlank()
    require(keysMode || pass.isNotEmpty()) { "SSH password is empty" }
    require(port in 1..65535) { "Invalid SSH port: $port" }

    ensureBouncyCastle()
    // Ответ сервера проверяется: при обрыве связи установка падает с ошибкой примерно за минуту, а не висит.
    val ssh = SshjClient(net.schmizz.sshj.DefaultConfig().apply { keepAliveProvider = net.schmizz.keepalive.KeepAliveProvider.KEEP_ALIVE })
    ssh.connectTimeout = 30000
    ssh.timeout = 300000
    ssh.addHostKeyVerifier(SshHostKeyStore.TofuVerifier(context))
    // Поток keepalive запускается при подключении, и только если интервал уже задан.
    ssh.connection.keepAlive.keepAliveInterval = 10

    try {
        ssh.connect(host, port)
    } catch (e: Exception) {
        DeployManager.writeError("SSH connect failed (${e.javaClass.simpleName}): ${e.message}")
        throw e
    }

    if (keysMode) {
        try {
            DeployManager.writeError("SSH key mode: key=${privateKey.length}b, passphrase=${keyPassphrase.isNotBlank()}")
            val passwordFinder = if (keyPassphrase.isNotBlank()) {
                PasswordUtils.createOneOff(keyPassphrase.toCharArray())
            } else {
                null
            }
            val keyProvider = ssh.loadKeys(privateKey, null, passwordFinder)
            ssh.authPublickey(user, keyProvider)
        } catch (e: UserAuthException) {
            DeployManager.writeError("SSH pubkey auth failed: ${e.message}")
            throw IOException("Auth fail (public key): ${e.message}", e)
        } catch (e: IOException) {
            DeployManager.writeError("SSH key parse error: ${e.message}")
            throw IOException("Auth fail (key parse): ${e.message}", e)
        }
    } else {
        try {
            ssh.authPassword(user, pass)
        } catch (passwordError: UserAuthException) {
            try {
                ssh.auth(
                    user,
                    listOf(
                        AuthKeyboardInteractive(
                            PasswordResponseProvider(
                                PasswordUtils.createOneOff(pass.toCharArray()),
                            ),
                        ),
                    ),
                )
            } catch (keyboardError: UserAuthException) {
                DeployManager.writeError(
                    "SSH password auth failed: password=${passwordError.message}; " +
                        "keyboard-interactive=${keyboardError.message}",
                )
                throw IOException("Auth fail (password): ${keyboardError.message}", keyboardError)
            }
        }
    }

    return ssh
}

private fun shellQuote(value: String): String {
    return "'" + value.replace("'", "'\"'\"'") + "'"
}

private fun systemdEnvironmentValue(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n', '\r' -> append(' ')
            else -> append(ch)
        }
    }
    append('"')
}

private fun dockerEnvironmentValue(value: String): String =
    value.replace('\n', ' ').replace('\r', ' ')

internal fun deployEnvironmentValue(value: String, installInDocker: Boolean): String =
    if (installInDocker) dockerEnvironmentValue(value) else systemdEnvironmentValue(value)

internal fun deployMode(installInDocker: Boolean): String =
    if (installInDocker) "docker" else "systemd"

private fun rootCommand(command: String, password: String? = null): String {
    val quoted = shellQuote(command)
    val sudoCmd = if (!password.isNullOrEmpty()) {
        val passQuoted = shellQuote(password)
        "printf '%s\\n' $passQuoted | sudo -S -p '' bash -c $quoted"
    } else {
        "sudo -S -p '' bash -c $quoted"
    }
    return "if [ \"\$(id -u)\" = \"0\" ]; then bash -c $quoted; " +
        "elif command -v sudo >/dev/null 2>&1; then $sudoCmd; " +
        "else echo 'error: root privileges required and sudo not found'; exit 1; fi"
}

private val BENIGN_STDERR = Regex(
    "debconf|dpkg-preconfigure|TERM is not set|falling back to frontend|" +
    "unable to re-open stdin|controlling tty|unable to initialize frontend|" +
    "^Warning:|frontend:\\s*(Readline|Teletype|Dialog)|\\[sudo\\] password for",
    RegexOption.IGNORE_CASE
)

private fun isBenignStderr(line: String): Boolean = BENIGN_STDERR.containsMatchIn(line)

internal suspend fun performDeploy(
    context: Context,
    host: String, user: String, pass: String, port: Int,
    mainPass: String,
    peerPort: Int, webPort: Int,
    deploy: DeployRun,
    privateKey: String = "",
    keyPassphrase: String = "",
    installInDocker: Boolean = false,
): Boolean = withContext(Dispatchers.IO) {
    var ssh: SshjClient? = null
    var tempDir: File? = null
    try {
        TunnelManager.beginDeployLog("Начало установки на $host:$port")
        deploy.progress(0.02f, "Подключение...")

        deploy.info("SSH/SFTP используют текущий системный маршрут")
        deploy.info(
            "Подключение к $host:$port по SSH, вход как $user по ${if (privateKey.isNotBlank()) "ключу" else "паролю"}",
        )
        val initialSsh = createSSHClient(context, host, user, pass, port, privateKey, keyPassphrase)
        ssh = initialSsh
        deploy.info("Сервер SSH: ${initialSsh.transport.serverVersion}")
        deploy.session(initialSsh)
        val sshClient = DeploySSHClient(initialSsh, pass, deploy) { stage ->
            deploy.info("Повторное SSH-подключение: $stage")
            createSSHClient(context, host, user, pass, port, privateKey, keyPassphrase).also {
                ssh = it
            }
        }
        deploy.success("Вход на VPS выполнен")

        val architectureResult = sshClient.execResult("uname -m", timeout = 10000L)
        if (architectureResult.exitStatus != 0) {
            throw IOException("Не удалось определить архитектуру VPS: ${architectureResult.output.trim().take(160)}")
        }
        val serverArchitecture = serverArchitectureForMachine(architectureResult.output)
        deploy.info("Архитектура VPS: ${serverArchitecture.displayName}")

        deploy.progress(0.05f, "Подготовка файлов...")
        deploy.info("Подготовка файлов установки")

        val workingDir = File(context.cacheDir, "deploy-${System.nanoTime()}")
        tempDir = workingDir
        if (!workingDir.mkdirs() && !workingDir.isDirectory) {
            throw IOException("Не удалось создать временный каталог установки")
        }
        fun extractAsset(assetName: String): File {
            val target = File(workingDir, assetName)
            context.assets.open(assetName).use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            if (!target.isFile || target.length() == 0L) {
                throw IOException("Файл $assetName отсутствует или пуст в assets")
            }
            return target
        }

        val scriptFile = extractAsset("deploy.sh")
        val serverFile = extractAsset(serverArchitecture.assetName)
        val environmentFile = File(workingDir, "mrtt.env").apply {
            writeText(
                "MRTT_PUBLIC_IP=${deployEnvironmentValue(host.trim(), installInDocker)}\n"
            )
        }
        val overridesFile = File(workingDir, "mrtt-deploy.json").apply {
            writeText(
                JSONObject()
                    .put("main_password", mainPass)
                    .toString()
            )
        }
        deploy.success("Файлы установки подготовлены")

        deploy.progress(0.06f, "Подготовка сервера...")
        sshClient.upload(scriptFile, "/tmp/deploy.sh")

        val deployEnvironment =
            "env MRTT_PEER_PORT=$peerPort MRTT_SSH_PORT=$port MRTT_WEB_PORT=$webPort MRTT_PUBLIC_IP=${host.trim()} " +
                "MRTT_DEPLOY_MODE=${deployMode(installInDocker)}"
        deploy.progress(0.10f, "Загрузка нового сервера...")
        sshClient.upload(serverFile, "/tmp/.mrtt-upload-server")
        sshClient.upload(environmentFile, "/tmp/.mrtt-upload-web.env")
        sshClient.upload(overridesFile, "/tmp/.mrtt-upload-overrides.json")

        deploy.progress(0.18f, "Установка нового сервера...")
        deploy.info("Запуск установщика на VPS")
        val deployResult = sshClient.execResult(
            rootCommand("$deployEnvironment bash /tmp/deploy.sh install", pass),
            timeout = CMD_TIMEOUT
        )
        val output = deployResult.output

        if (isSuccessfulDeployResult(deployResult.exitStatus, output)) {
            deploy.success(
                if (installInDocker) {
                    "Установка завершена · Docker-контейнер MRTT активен"
                } else {
                    "Установка завершена · служба MRTT активна"
                }
            )
            deploy.stop("success")
            return@withContext true
        } else {
            DeployManager.writeError(
                "Deploy failed: exit=${deployResult.exitStatus}, success marker=${output.contains("MRTT_DEPLOY_OK")}" +
                    "\n${output.takeLast(1200)}"
            )
            val failureMessage = deployFailureMessage(deployResult.exitStatus, output)
            deploy.error(failureMessage)
            deploy.stop(failureMessage)
            return@withContext false
        }
    } catch (e: CancellationException) {
        DeployManager.writeError("Deploy cancelled")
        deploy.warning("установка отменена пользователем")
        deploy.stop("Установка отменена")
        throw e
    } catch (e: Exception) {
        val friendly = friendlyDeployError(e.message)
        DeployManager.writeError(
            "Deploy error (${e.javaClass.simpleName}): ${e.message}\n" +
                e.stackTraceToString().take(1200)
        )
        deploy.error(friendly)
        if (friendly != e.message) deploy.info("Техническая причина: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}")
        deploy.stop(friendly)
        return@withContext false
    } finally {
        try { ssh?.disconnect() } catch (_: Exception) {}
        deploy.session(null)
        runCatching { tempDir?.deleteRecursively() }
    }
}

internal suspend fun performUninstall(
    context: Context,
    host: String, user: String, pass: String, port: Int,
    peerPort: Int,
    deploy: DeployRun,
    privateKey: String = "",
    keyPassphrase: String = "",
): Boolean = withContext(Dispatchers.IO) {
    var ssh: SshjClient? = null
    var uninstallScript: File? = null
    try {
        TunnelManager.beginDeployLog("Начало удаления с $host:$port")
        deploy.progress(0.05f, "Подключение...")
        deploy.info("SSH/SFTP используют текущий системный маршрут")
        deploy.info("Подключение к VPS по SSH")
        ssh = createSSHClient(context, host, user, pass, port, privateKey, keyPassphrase)
        deploy.session(ssh)
        val sshClient = DeploySSHClient(ssh, pass, deploy)
        deploy.success("SSH-соединение установлено")

        deploy.progress(0.15f, "Подготовка штатного удаления...")
        deploy.info("Загрузка штатного установщика MRTT")
        uninstallScript = File(context.cacheDir, "mrtt-uninstall-${System.nanoTime()}.sh")
        context.assets.open("deploy.sh").use { input ->
            FileOutputStream(uninstallScript).use { output -> input.copyTo(output) }
        }
        if (!uninstallScript.isFile || uninstallScript.length() == 0L) {
            throw IOException("Не удалось подготовить штатный установщик MRTT")
        }
        sshClient.upload(uninstallScript, "/tmp/deploy.sh")

        deploy.progress(0.30f, "Удаление через deploy.sh...")
        deploy.info("Запуск серверного удаления")
        sshClient.exec(
            rootCommand("env MRTT_PEER_PORT=$peerPort MRTT_SSH_PORT=$port bash /tmp/deploy.sh uninstall", pass),
            timeout = 30000L,
        )
        // Клиенты каскада принадлежат MRTT и уходят вместе с ним.
        // shortcut: их правила iptables остаются до перезагрузки, но без интерфейса ни на что не действуют.
        sshClient.exec(
            rootCommand(
                "for u in /etc/systemd/system/mrtt-exit-*.service; do [ -e \"\$u\" ] || continue; " +
                    "systemctl disable --now \"\$(basename \"\$u\")\" >/dev/null 2>&1; rm -f \"\$u\"; done; " +
                    "rm -rf /etc/mrtt-exit /usr/local/lib/mrtt-exit-client; systemctl daemon-reload",
                pass,
            ),
            timeout = 30000L,
        )

        deploy.progress(1.0f, "Готово!")
        deploy.success("Удаление MRTT завершено")
        deploy.stop("success")
        return@withContext true

    } catch (e: CancellationException) {
        DeployManager.writeError("Uninstall cancelled")
        deploy.warning("удаление отменено пользователем")
        deploy.stop("Удаление отменено")
        throw e
    } catch (e: Exception) {
        val friendly = friendlyDeployError(e.message)
        DeployManager.writeError("Uninstall error (${e.javaClass.simpleName}): ${e.message}")
        deploy.error(friendly)
        if (friendly != e.message) deploy.info("Техническая причина: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}")
        deploy.stop(friendly)
        return@withContext false
    } finally {
        try { ssh?.disconnect() } catch (_: Exception) {}
        deploy.session(null)
        uninstallScript?.delete()
    }
}

/** Выход каскада на сервере [from]: что сохранить в его настройках. */
internal data class CreatedExit(val id: String, val table: Int, val remotePassword: String)

private class CascadeHost(private val context: Context, private val server: com.mrtt.client.servers.VpsServer) : java.io.Closeable {
    private val script = context.assets.open("cascade.sh").use { it.readBytes().toString(Charsets.UTF_8) }
    private val ssh = createSSHClient(context, server.host, server.sshUser, server.sshPassword, server.sshPort, server.sshPrivateKey, server.sshKeyPassphrase)
    private val client = DeploySSHClient(ssh, server.sshPassword, DeployRun("", server.host))

    /** Строка ответа с маркером [marker], разбитая на слова. */
    fun run(marker: String, vararg args: String): List<String> {
        val quoted = args.joinToString(" ") { shellQuote(it) }
        val result = client.execResult(rootCommand("set -- $quoted\n$script", server.sshPassword), 180_000L)
        val line = result.output.lines().firstOrNull { it.startsWith(marker) }
        if (result.exitStatus != 0 || line == null) {
            val reason = result.output.lines().lastOrNull { it.startsWith("error:") }?.removePrefix("error:")?.trim()
            throw IOException("${server.title}: ${reason ?: "каскад не настроился"}")
        }
        return line.split(' ')
    }

    /** Кладёт на сервер клиент каскада под его архитектуру. */
    fun uploadExitClient() {
        val machine = client.exec("uname -m", 30_000L).trim()
        val asset = when (machine) {
            "x86_64", "amd64" -> "mrtt-exit-client-amd64"
            "aarch64", "arm64" -> "mrtt-exit-client-arm64"
            else -> throw IOException("${server.title}: каскад не поддерживает архитектуру $machine")
        }
        val local = File(context.cacheDir, "$asset-${System.nanoTime()}")
        try {
            context.assets.open(asset).use { input -> FileOutputStream(local).use { input.copyTo(it) } }
            // SFTP не даёт права на запуск и не пишет в системную папку без root; install ещё и заменит работающий файл.
            client.upload(local, "/tmp/.mrtt-upload-exit-client")
            val installed = client.execResult(
                rootCommand("install -m 0755 /tmp/.mrtt-upload-exit-client /usr/local/lib/mrtt-exit-client; status=\$?; rm -f /tmp/.mrtt-upload-exit-client; exit \$status", server.sshPassword),
                30_000L,
            )
            if (installed.exitStatus != 0) throw IOException("${server.title}: клиент каскада не установился")
        } finally {
            local.delete()
        }
    }

    override fun close() {
        runCatching { ssh.disconnect() }
    }
}

/** Подключает [from] к [to] как обычный конфиг и проверяет, что адрес выдан; при ошибке за собой всё убирает. */
internal suspend fun setupExit(
    context: Context,
    from: com.mrtt.client.servers.VpsServer,
    to: com.mrtt.client.servers.VpsServer,
): CreatedExit {
    val id = java.util.UUID.randomUUID().toString().replace("-", "").take(8)
    val name = "Каскад $id"
    val remote = com.mrtt.client.servers.ServerControl(context, to)
    remote.createClient(name, "", 0, 0, 0, "", false, "", false, "")
    val password = remote.clients().first { it.name == name }.password
    try {
        return withContext(Dispatchers.IO) {
            CascadeHost(context, from).use { client ->
                try {
                    client.uploadExitClient()
                    val up = client.run("CASCADE_CLIENT", "client-up", id, to.host, to.peerPort.toString(), password)
                    client.run("CASCADE_OK", "client-check", id)
                    CreatedExit(id, up[1].toInt(), password)
                } catch (error: Exception) {
                    runCatching { client.run("CASCADE_OK", "client-down", id) }
                    throw error
                }
            }
        }
    } catch (error: Exception) {
        runCatching { remote.deleteClient(password) }
        throw error
    }
}

/** Убирает клиент и конфиг на выходе; недоступный или уже удалённый выход не мешает убрать его здесь. */
internal suspend fun removeExit(
    context: Context,
    from: com.mrtt.client.servers.VpsServer,
    to: com.mrtt.client.servers.VpsServer?,
    id: String,
    remotePassword: String,
) {
    withContext(Dispatchers.IO) {
        CascadeHost(context, from).use { it.run("CASCADE_OK", "client-down", id) }
    }
    if (to != null && remotePassword.isNotEmpty()) {
        runCatching { com.mrtt.client.servers.ServerControl(context, to).deleteClient(remotePassword) }
    }
}
