using System.Text.Json;
using Microsoft.Playwright;

namespace CsqttrMaxToken;

/// <summary>
/// CSQTTR MAX Token v6
///
/// Жизненный цикл браузера:
///   Фаза A: headless (по умолчанию) — открываем web.max.ru, ждём инициализации SDK.
///   Если токена нет → закрываем, открываем headed в том же persistent-профиле,
///   ждём пока пользователь залогинится, затем закрываем headed и открываем
///   снова headless. Если токен есть сразу — headed-фаза пропускается.
///
/// Стратегия получения session_key:
///   1. Перехват ответов /fb.do — если пришёл session_key.
///   2. Активный запрос через WS (INIT→LOGIN→158) + HTTP auth.anonymLogin.
///   3. localStorage (fallback).
///
/// TURN получаем через vchat.startConversation + vchat.getConversationParams,
/// выполняя fetch из JS-контекста страницы (тот же origin/cookies/fingerprint).
/// </summary>
public static class Extractor
{
    private const string BaseUrl = "https://web.max.ru";
    private const string MaxApiKey = "CNHIJPLGDIHBABABA";

    public static async Task<int> RunAsync(ExtractorOptions opts)
    {
        Console.WriteLine("═══ CSQTTR MAX Token v6 ═══");
        Console.WriteLine($"  session-path : {opts.SessionPath}");
        Console.WriteLine($"  output       : {opts.OutputPath}");
        Console.WriteLine($"  groups       : {string.Join(", ", opts.GroupIds)}");
        Console.WriteLine($"  start-mode   : {(opts.Headless ? "headless" : "headed")}");
        Console.WriteLine($"  headed-fallback : {(opts.AllowHeadedFallback ? "on" : "off")}");
        Console.WriteLine($"  debug        : {opts.Debug}");
        Console.WriteLine();

        Directory.CreateDirectory(opts.SessionPath);

        await using var debug = new DebugRecorder(
            opts.Debug,
            opts.DebugDir ?? Path.GetDirectoryName(opts.OutputPath),
            Path.GetFileNameWithoutExtension(opts.OutputPath));

        if (debug.Enabled) Console.WriteLine($"  debug-file   : {debug.Path}");
        Console.WriteLine();

        using var playwright = await Playwright.CreateAsync();

        // ── Фаза A: старт (headless по умолчанию) ─────────────────────────
        // Открываем в стартовом режиме, проверяем наличие access_token.
        var (context, page, accessToken) = await LaunchAndCheckLoginAsync(
            playwright, opts, startHeaded: !opts.Headless, debug);

        if (accessToken is null)
        {
            if (!opts.AllowHeadedFallback)
            {
                Console.Error.WriteLine("  ✗ access_token не найден, headed-fallback отключён (--no-headless).");
                await context.CloseAsync();
                return 1;
            }

            Console.WriteLine("  ⓘ access_token не найден. Переоткрываю браузер в headed...");
            await context.CloseAsync();

            // ── Фаза B: headed — ждём логина ─────────────────────────────
            (context, page, accessToken) = await LaunchAndWaitForLoginAsync(playwright, opts, debug);

            if (accessToken is null)
            {
                Console.Error.WriteLine("  ✗ Логин не произошёл за отведённое время.");
                await context.CloseAsync();
                return 1;
            }

            Console.WriteLine($"  ✓ Залогинились. access_token: {Short(accessToken, 20)}");
            Console.WriteLine("  → Переоткрываю браузер в headless для боевого прогона...");
            await context.CloseAsync();

            // ── Фаза C: снова headless ───────────────────────────────────
            (context, page, accessToken) = await LaunchAndCheckLoginAsync(
                playwright, opts, startHeaded: false, debug);

            if (accessToken is null)
            {
                Console.Error.WriteLine("  ✗ После переоткрытия в headless access_token пропал. Проверь persistent-профиль.");
                await context.CloseAsync();
                return 1;
            }
        }
        else
        {
            Console.WriteLine($"  ✓ access_token уже есть: {Short(accessToken, 20)}");
        }

        // ── Перехват /fb.do (навешиваем на итоговую страницу) ────────────
        var capture = new NetworkCapture();
        AttachCapture(page, capture, debug);

        Console.WriteLine("  … пауза 3 сек на инициализацию SDK MAX");
        await Task.Delay(TimeSpan.FromSeconds(3));

        // ── session_key ──────────────────────────────────────────────────
        Console.WriteLine();
        Console.WriteLine("→ Поиск session_key (приоритет 1: перехват сети)");
        var caughtSession = capture.SessionKey;
        if (caughtSession is not null)
        {
            Console.WriteLine($"  ✓ Пойман из сети: {Short(caughtSession, 20)}");
        }
        else
        {
            Console.WriteLine("  ⓘ В сети session_key не пришёл (клиент уже авторизован).");
            Console.WriteLine("→ Поиск session_key (приоритет 2: активный запрос через WS+anonymLogin)");

            var fresh = await GetFreshSessionKeyAsync(page, debug);
            if (fresh is not null)
            {
                Console.WriteLine($"  ✓ Получен активным запросом: {Short(fresh, 20)}");
                capture.OverrideSessionKey(fresh);
            }
            else
            {
                Console.WriteLine("→ Поиск session_key (приоритет 3: localStorage)");
                var fromStorage = await ReadSessionKeyFromStorageAsync(page);
                if (fromStorage is not null)
                {
                    Console.WriteLine($"  ✓ Найден в localStorage: {Short(fromStorage, 20)}");
                    capture.OverrideSessionKey(fromStorage);
                }
                else
                {
                    Console.Error.WriteLine("  ✗ session_key не получен ни одним способом.");
                    await context.CloseAsync();
                    return 1;
                }
            }
        }

        // ── Работаем по группам ───────────────────────────────────────────
        var result = new ExtractorResult
        {
            SessionKey = capture.SessionKey,
            AccessToken = accessToken,
            ExtractedAtUtc = DateTime.UtcNow,
        };

        for (var i = 0; i < opts.GroupIds.Count; i++)
        {
            var groupId = opts.GroupIds[i];
            Console.WriteLine();
            Console.WriteLine($"── Группа [{i + 1}/{opts.GroupIds.Count}]: {groupId} ──");

            debug.LogNote($"=== processing group {groupId} ===");

            var payload = await ExtractIceForGroupAsync(page, capture, groupId, debug);
            result.Calls.Add(payload);

            if (payload.Turn is not null)
                Console.WriteLine($"  ✓ Группа {groupId}: TURN получен");
            else
                Console.WriteLine($"  ⚠ Группа {groupId}: TURN не получен");

            if (i < opts.GroupIds.Count - 1)
                await Task.Delay(opts.PauseBetweenGroups);
        }

        result = new ExtractorResult
        {
            SessionKey = capture.SessionKey,
            SessionSecretKey = capture.SessionSecretKey,
            Uid = capture.Uid,
            ExternalUserId = capture.ExternalUserId,
            ApiServer = capture.ApiServer,
            AccessToken = accessToken,
            ExtractedAtUtc = result.ExtractedAtUtc,
            Calls = result.Calls,
        };

        var json = JsonSerializer.Serialize(result, JsonOpts.Pretty);
        await File.WriteAllTextAsync(opts.OutputPath, json);

        Console.WriteLine();
        Console.WriteLine($"✓ Сохранено: {opts.OutputPath}");
        Console.WriteLine($"  session_key: {Short(result.SessionKey, 20)}");
        Console.WriteLine($"  групп:       {result.Calls.Count}");
        Console.WriteLine($"  с TURN:      {result.Calls.Count(c => c.Turn is not null)}");
        if (debug.Enabled) Console.WriteLine($"  debug-file:  {debug.Path}");

        await context.CloseAsync();
        return result.Calls.Any(c => c.Turn is not null) ? 0 : 1;
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Браузер: фабрика контекста и перехват /fb.do
    // ──────────────────────────────────────────────────────────────────────

    private static BrowserTypeLaunchPersistentContextOptions BuildLaunchOptions(bool headed)
    {
        return new BrowserTypeLaunchPersistentContextOptions
        {
            Headless = !headed,
            ViewportSize = ViewportSize.NoViewport,
            Args = new[]
            {
                "--disable-blink-features=AutomationControlled",
                "--no-default-browser-check",
                "--no-first-run",
                "--use-fake-ui-for-media-stream",
                "--use-fake-device-for-media-stream",
                "--autoplay-policy=no-user-gesture-required",
            },
        };
    }

    private static async Task<IBrowserContext> CreateContextAsync(
        IPlaywright playwright, ExtractorOptions opts, bool headed)
    {
        var context = await playwright.Chromium
            .LaunchPersistentContextAsync(opts.SessionPath, BuildLaunchOptions(headed));

        try
        {
            await context.GrantPermissionsAsync(
                new[] { "microphone", "camera" },
                new BrowserContextGrantPermissionsOptions { Origin = BaseUrl });
        }
        catch { /* ignore */ }

        await context.AddInitScriptAsync(@"
            Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
            Object.defineProperty(navigator, 'languages', { get: () => ['ru-RU','ru','en-US','en'] });
            window.chrome = window.chrome || { runtime: {} };
        ");

        return context;
    }

    /// <summary>
    /// Открывает контекст в указанном режиме, переходит на web.max.ru,
    /// ждёт инициализации SDK и читает access_token из localStorage.
    /// </summary>
    private static async Task<(IBrowserContext context, IPage page, string? accessToken)>
        LaunchAndCheckLoginAsync(IPlaywright playwright, ExtractorOptions opts, bool startHeaded, DebugRecorder debug)
    {
        Console.WriteLine($"→ Старт браузера ({(startHeaded ? "headed" : "headless")})...");
        var context = await CreateContextAsync(playwright, opts, startHeaded);

        var page = context.Pages.Count > 0 ? context.Pages[0] : await context.NewPageAsync();

        // Слушаем консоль для диагностики (запросы навесим позже, после логина)
        page.Console += (_, msg) =>
        {
            if (msg.Type is "error") Console.WriteLine($"[browser:error] {msg.Text}");
            try { debug.LogNote($"console[{msg.Type}]: {msg.Text}"); } catch { }
        };

        Console.WriteLine($"→ Открываю {BaseUrl} ...");
        await page.GotoAsync(BaseUrl, new PageGotoOptions
        {
            WaitUntil = WaitUntilState.DOMContentLoaded,
            Timeout = 60_000,
        });

        Console.WriteLine("  … пауза 4 сек на инициализацию SDK MAX");
        await Task.Delay(TimeSpan.FromSeconds(4));

        var token = await ReadAccessTokenAsync(page);
        return (context, page, token);
    }

    /// <summary>
    /// Открывает headed-браузер и ждёт, пока пользователь залогинится.
    /// Полинг localStorage до истечения <see cref="ExtractorOptions.LoginTimeout"/>.
    /// </summary>
    private static async Task<(IBrowserContext context, IPage page, string? accessToken)>
        LaunchAndWaitForLoginAsync(IPlaywright playwright, ExtractorOptions opts, DebugRecorder debug)
    {
        var context = await CreateContextAsync(playwright, opts, headed: true);
        var page = context.Pages.Count > 0 ? context.Pages[0] : await context.NewPageAsync();

        page.Console += (_, msg) =>
        {
            if (msg.Type is "error") Console.WriteLine($"[browser:error] {msg.Text}");
            try { debug.LogNote($"console[{msg.Type}]: {msg.Text}"); } catch { }
        };

        Console.WriteLine($"→ Открываю {BaseUrl} для ручного логина...");
        await page.GotoAsync(BaseUrl, new PageGotoOptions
        {
            WaitUntil = WaitUntilState.DOMContentLoaded,
            Timeout = 60_000,
        });

        Console.WriteLine($"  ⏳ Ждём логина (таймаут {(int)opts.LoginTimeout.TotalMinutes} мин).");
        Console.WriteLine("     Залогинься в открытом окне Chromium. Как только токен появится —");
        Console.WriteLine("     браузер будет закрыт и переоткрыт в headless автоматически.");

        var deadline = DateTime.UtcNow + opts.LoginTimeout;
        while (DateTime.UtcNow < deadline)
        {
            var token = await ReadAccessTokenAsync(page);
            if (token is not null) return (context, page, token);

            await Task.Delay(opts.LoginPollInterval);
        }

        return (context, page, null);
    }

    private static Task<string?> ReadAccessTokenAsync(IPage page)
        => page.EvaluateAsync<string?>(@"() => {
            try { return JSON.parse(localStorage.getItem('__oneme_auth') || '{}').token ?? null; }
            catch { return null; }
        }");

    private static void AttachCapture(IPage page, NetworkCapture capture, DebugRecorder debug)
    {
        page.Request += (_, req) =>
        {
            try
            {
                if (!req.Url.Contains("/fb.do")) return;
                debug.LogRequest(req.Url, req.Method, req.PostData);
                capture.FeedRequest(req.Url, req.PostData);
            }
            catch { /* ignore */ }
        };

        page.Response += async (_, resp) =>
        {
            try
            {
                if (!resp.Url.Contains("/fb.do")) return;
                var text = await resp.TextAsync();
                debug.LogResponse(resp.Url, resp.Status, text);
                capture.FeedResponse(resp.Url, text);
            }
            catch (Exception ex)
            {
                try { debug.LogError(resp.Url, ex.Message); } catch { }
            }
        };
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Приоритет 2: активный запрос session_key через WS + anonymLogin
    // ──────────────────────────────────────────────────────────────────────

    private static async Task<string?> GetFreshSessionKeyAsync(IPage page, DebugRecorder debug)
    {
        var apiKeyJson = JsonSerializer.Serialize(MaxApiKey);

        var js = $$"""
        async () => {
            const apiKey = {{apiKeyJson}};
            try {
                const raw = localStorage.getItem('__oneme_auth');
                if (!raw) return { _error: 'no __oneme_auth' };
                const accessToken = JSON.parse(raw).token;
                if (!accessToken) return { _error: 'no token' };

                // ─── WS: INIT → LOGIN → 158 ───
                const callToken = await new Promise((resolve, reject) => {
                    const ws = new WebSocket('wss://ws-api.oneme.ru/websocket');
                    let seq = 0;
                    let gotLogin = false;
                    const timeout = setTimeout(() => {
                        try { ws.close(); } catch {}
                        reject('WS timeout');
                    }, 20000);

                    const req = (op, payload) => {
                        seq++;
                        ws.send(JSON.stringify({ver: 11, cmd: 0, seq, opcode: op, payload}));
                    };

                    ws.onopen = () => req(6, {
                        deviceId: crypto.randomUUID(),
                        userAgent: {
                            deviceType: 'WEB', pushDeviceType: 'WEBPUSH',
                            locale: 'ru', deviceLocale: 'ru',
                            osVersion: 'Windows', deviceName: 'Chrome',
                            headerUserAgent: navigator.userAgent,
                            appVersion: '26.10.3',
                            screen: `${screen.width}x${screen.height} 1.0x`,
                            timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                        }
                    });

                    ws.onmessage = (ev) => {
                        let m; try { m = JSON.parse(ev.data); } catch { return; }
                        if (m.cmd === 0) return;
                        if (m.opcode === 6 && m.cmd === 1) {
                            req(19, {token: accessToken, interactive: true, chatsCount: 0, chatsSync: 0, contactsSync: 0, presenceSync: 0, draftsSync: 0});
                        } else if (m.opcode === 19 && m.cmd === 1) {
                            gotLogin = true;
                            req(158, {});
                        } else if (m.opcode === 158 && m.cmd === 1) {
                            clearTimeout(timeout);
                            try { ws.close(); } catch {}
                            resolve(m.payload.token);
                        } else if (m.cmd === 3) {
                            clearTimeout(timeout);
                            try { ws.close(); } catch {}
                            reject('WS error: ' + JSON.stringify(m.payload));
                        }
                    };
                    ws.onerror = () => { clearTimeout(timeout); reject('WS onerror'); };
                    ws.onclose = () => { if (!gotLogin) { clearTimeout(timeout); reject('WS closed before LOGIN'); } };
                });

                // ─── HTTP anonymLogin ───
                const r1 = await fetch('https://calls.okcdn.ru/fb.do', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: new URLSearchParams({
                        method: 'auth.anonymLogin',
                        format: 'JSON',
                        application_key: apiKey,
                        session_data: JSON.stringify({
                            auth_token: callToken,
                            client_type: 'SDK_JS',
                            client_version: '1.1',
                            device_id: crypto.randomUUID(),
                            version: 3,
                        }),
                    }).toString(),
                }).then(r => r.json());

                return {
                    session_key: r1.session_key,
                    session_secret_key: r1.session_secret_key,
                    uid: r1.uid,
                    external_user_id: r1.external_user_id,
                    api_server: r1.api_server,
                };
            } catch (e) {
                return { _error: String(e?.stack || e) };
            }
        }
        """;

        try
        {
            var res = await page.EvaluateAsync<JsonElement?>(js);
            if (res is null) return null;
            debug.LogNote("active session_key fetch", res.Value.GetRawText());

            if (res.Value.TryGetProperty("_error", out var err))
            {
                Console.WriteLine($"  ✗ активный запрос провалился: {err.GetString()}");
                return null;
            }

            if (res.Value.TryGetProperty("session_key", out var sk) && sk.ValueKind == JsonValueKind.String)
                return sk.GetString();

            return null;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"  ✗ EvaluateAsync (session_key) failed: {ex.Message}");
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Приоритет 3: session_key из localStorage
    // ──────────────────────────────────────────────────────────────────────

    private static async Task<string?> ReadSessionKeyFromStorageAsync(IPage page)
    {
        try
        {
            return await page.EvaluateAsync<string?>(@"() => {
                const candidates = ['__oneme_session_key', '__oneme_session', 'session_key', '__oneme_auth'];
                for (const k of candidates) {
                    const raw = localStorage.getItem(k);
                    if (!raw) continue;
                    try {
                        const obj = JSON.parse(raw);
                        if (obj?.session_key) return obj.session_key;
                    } catch {}
                }
                for (const k of Object.keys(localStorage)) {
                    if (!k.startsWith('_okcls_')) continue;
                    try {
                        const obj = JSON.parse(localStorage.getItem(k) || '');
                        if (obj?.session_key) return obj.session_key;
                    } catch {}
                }
                return null;
            }");
        }
        catch { return null; }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  TURN для одной группы
    // ──────────────────────────────────────────────────────────────────────

    private static async Task<CallPayload> ExtractIceForGroupAsync(
        IPage page, NetworkCapture capture, string groupId, DebugRecorder debug)
    {
        var sk = capture.SessionKey;
        if (string.IsNullOrEmpty(sk))
            return new CallPayload { GroupId = groupId };

        var gidJson = JsonSerializer.Serialize(groupId);
        var apiKeyJson = JsonSerializer.Serialize(MaxApiKey);
        var skJson = JsonSerializer.Serialize(sk);

        var js = $$"""
        async () => {
            const groupId = {{gidJson}};
            const apiKey = {{apiKeyJson}};
            const sessionKey = {{skJson}};

            try {
                const convId = crypto.randomUUID();
                const startResp = await fetch('https://calls.okcdn.ru/fb.do', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: new URLSearchParams({
                        method: 'vchat.startConversation',
                        format: 'JSON',
                        application_key: apiKey,
                        conversationId: convId,
                        isVideo: 'false',
                        protocolVersion: '5',
                        session_key: sessionKey,
                        payload: JSON.stringify({is_video: false, chat_id: groupId}),
                    }).toString(),
                }).then(r => r.json());

                let finalConvId = convId;
                if (startResp && typeof startResp === 'object') {
                    if (typeof startResp.id === 'string' && startResp.id.length === 36)
                        finalConvId = startResp.id;
                    else if (typeof startResp.conversationId === 'string' && startResp.conversationId.length === 36)
                        finalConvId = startResp.conversationId;
                }

                const ice = await fetch('https://calls.okcdn.ru/fb.do', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: new URLSearchParams({
                        method: 'vchat.getConversationParams',
                        format: 'JSON',
                        application_key: apiKey,
                        session_key: sessionKey,
                        conversationId: finalConvId,
                    }).toString(),
                }).then(r => r.json());

                return {
                    conversationId: finalConvId,
                    startResp: startResp,
                    ice: ice,
                };
            } catch (e) {
                return { _error: String(e?.stack || e) };
            }
        }
        """;

        JsonElement? root;
        try
        {
            root = await page.EvaluateAsync<JsonElement?>(js);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"  ✗ EvaluateAsync (ICE) failed: {ex.Message}");
            return new CallPayload { GroupId = groupId };
        }

        if (root is null) return new CallPayload { GroupId = groupId };
        debug.LogNote($"ICE result for {groupId}", root.Value.GetRawText());

        if (root.Value.TryGetProperty("_error", out var err))
        {
            Console.WriteLine($"  ✗ JS error: {err.GetString()}");
            return new CallPayload { GroupId = groupId };
        }

        var convId = TryGetStr(root.Value, "conversationId");
        string? endpoint = null, wtEndpoint = null, sigToken = null;
        IceServer? turn = null, stun = null;

        if (root.Value.TryGetProperty("ice", out var ice) && ice.ValueKind == JsonValueKind.Object)
        {
            turn = ReadIce(ice, "turn_server");
            stun = ReadIce(ice, "stun_server");
            endpoint = TryGetStr(ice, "endpoint");
            wtEndpoint = TryGetStr(ice, "wt_endpoint");
            sigToken = TryGetStr(ice, "token");

            if (turn is null)
            {
                Console.WriteLine($"  ℹ ICE keys: {string.Join(", ", ice.EnumerateObject().Select(p => p.Name))}");
            }
        }

        if (turn is not null)
        {
            Console.WriteLine($"  ✓ turn.urls: {string.Join(", ", turn.Urls)}");
            Console.WriteLine($"  ✓ turn.username: {Short(turn.Username, 32)}");
            Console.WriteLine($"  ✓ turn.credential: {Short(turn.Credential, 24)}");
        }

        return new CallPayload
        {
            GroupId = groupId,
            ConversationId = convId,
            Endpoint = endpoint,
            WtEndpoint = wtEndpoint,
            SignalingToken = sigToken,
            Turn = turn,
            Stun = stun,
            ExtractedAtUtc = DateTime.UtcNow,
            TurnExpiresAtUtc = ComputeTurnExpiry(turn?.Username),
        };
    }

    private static string? TryGetStr(JsonElement el, string name)
        => el.ValueKind == JsonValueKind.Object
           && el.TryGetProperty(name, out var v)
           && v.ValueKind == JsonValueKind.String
            ? v.GetString()
            : null;

    private static IceServer? ReadIce(JsonElement parent, string name)
    {
        if (parent.ValueKind != JsonValueKind.Object) return null;
        if (!parent.TryGetProperty(name, out var node)) return null;
        if (node.ValueKind == JsonValueKind.Null) return null;

        string? urlsRaw = null;
        if (node.ValueKind == JsonValueKind.Object)
        {
            if (node.TryGetProperty("urls", out var u))
            {
                urlsRaw = u.ValueKind == JsonValueKind.Array
                    ? string.Join(",", u.EnumerateArray().Select(x => x.GetString()))
                    : u.GetString();
            }
        }
        else if (node.ValueKind == JsonValueKind.String) urlsRaw = node.GetString();
        else if (node.ValueKind == JsonValueKind.Array)
            urlsRaw = string.Join(",", node.EnumerateArray().Select(x => x.GetString()));

        if (string.IsNullOrWhiteSpace(urlsRaw)) return null;

        var urls = urlsRaw.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

        string? user = null, cred = null;
        if (node.ValueKind == JsonValueKind.Object)
        {
            if (node.TryGetProperty("username", out var u2) && u2.ValueKind == JsonValueKind.String) user = u2.GetString();
            if (node.TryGetProperty("credential", out var c2) && c2.ValueKind == JsonValueKind.String) cred = c2.GetString();
        }

        return new IceServer { Urls = urls, Username = user, Credential = cred };
    }

    private static readonly System.Text.RegularExpressions.Regex UsernameTsRegex =
        new(@"^(?<ts>\d{9,11}):", System.Text.RegularExpressions.RegexOptions.Compiled);

    private static DateTime? ComputeTurnExpiry(string? username)
    {
        if (string.IsNullOrEmpty(username)) return null;
        var m = UsernameTsRegex.Match(username);
        if (!m.Success) return null;
        if (!long.TryParse(m.Groups["ts"].Value, out var ts)) return null;
        return DateTimeOffset.FromUnixTimeSeconds(ts).UtcDateTime;
    }

    private static string Short(string? s, int len)
        => string.IsNullOrEmpty(s) ? "—" : (s.Length > len ? s[..len] + "…" : s);
}

file static class JsonOpts
{
    public static readonly JsonSerializerOptions Pretty = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        DefaultIgnoreCondition =
            System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };
}
