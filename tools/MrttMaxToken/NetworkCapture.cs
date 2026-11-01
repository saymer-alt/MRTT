using System.Text.Json;
using System.Text.RegularExpressions;

namespace MrttMaxToken;

/// <summary>
/// Собирает данные из трафика MAX:
///   - session_key — приходит в ответе auth.anonymLogin
///   - также пробует вытащить session_key из request-body (fallback)
///   - conversationId — из ответа vchat.startConversation
///   - turn_server / stun_server — из ответа vchat.getConversationParams
/// </summary>
public sealed class NetworkCapture
{
    private readonly object _lock = new();

    public string? SessionKey { get; private set; }
    public string? SessionSecretKey { get; private set; }
    public string? Uid { get; private set; }
    public string? ExternalUserId { get; private set; }
    public string? ApiServer { get; private set; }

    public string? ConversationId { get; private set; }
    public string? SignalingToken { get; private set; }
    public string? Endpoint { get; private set; }
    public string? WtEndpoint { get; private set; }

    public IceServer? Turn { get; private set; }
    public IceServer? Stun { get; private set; }
    public DateTime? TurnExpiresAtUtc { get; private set; }

    public DateTime? CapturedAt { get; private set; }

    public bool IsReadyForFetch
    {
        get
        {
            lock (_lock)
                return !string.IsNullOrEmpty(SessionKey)
                    && !string.IsNullOrEmpty(ConversationId);
        }
    }

    /// <summary>
    /// Принудительно устанавливаем session_key (полученный активным запросом).
    /// </summary>
    public void OverrideSessionKey(string sk)
    {
        lock (_lock)
        {
            SessionKey = sk;
            CapturedAt ??= DateTime.UtcNow;
        }
    }

    /// <summary>
    /// Перехват request-body: если увидим session_key, сохраним (fallback).
    /// </summary>
    public void FeedRequest(string url, string? postData)
    {
        if (string.IsNullOrEmpty(postData)) return;
        if (!url.Contains("/fb.do")) return;

        var m = Regex.Match(postData, @"(?:^|&)session_key=([^&]+)");
        if (!m.Success) return;

        string val;
        try { val = Uri.UnescapeDataString(m.Groups[1].Value.Replace('+', ' ')); }
        catch { val = m.Groups[1].Value; }
        if (string.IsNullOrEmpty(val)) return;

        lock (_lock)
        {
            // Не перетираем более свежий session_key из ответа.
            if (SessionKey is null)
            {
                SessionKey = val;
                CapturedAt ??= DateTime.UtcNow;
            }
        }
    }

    public void FeedResponse(string url, string? body)
    {
        if (string.IsNullOrEmpty(body)) return;
        if (!url.Contains("/fb.do")) return;

        JsonDocument? doc = null;
        try { doc = JsonDocument.Parse(body); }
        catch
        {
            var idx = body.IndexOf('{');
            if (idx >= 0)
            {
                try { doc = JsonDocument.Parse(body.Substring(idx)); }
                catch { return; }
            }
            else return;
        }

        using (doc)
        {
            var root = doc.RootElement;
            var inner = Unwrap(root);

            lock (_lock)
            {
                if (SessionKey is null && TryGetString(inner, "session_key", out var sk))
                {
                    SessionKey = sk;
                    CapturedAt ??= DateTime.UtcNow;
                }
                if (SessionSecretKey is null && TryGetString(inner, "session_secret_key", out var ssk))
                    SessionSecretKey = ssk;
                if (Uid is null && TryGetString(inner, "uid", out var uid))
                    Uid = uid;
                if (ExternalUserId is null && TryGetString(inner, "external_user_id", out var euid))
                    ExternalUserId = euid;
                if (ApiServer is null && TryGetString(inner, "api_server", out var api))
                    ApiServer = api;

                if (ConversationId is null)
                {
                    if (TryGetString(inner, "id", out var id) && LooksLikeUuid(id))
                        ConversationId = id;
                    else if (TryGetString(inner, "conversationId", out var cid) && LooksLikeUuid(cid))
                        ConversationId = cid;
                }

                if (SignalingToken is null && TryGetString(inner, "token", out var tk))
                    SignalingToken = tk;

                if (Endpoint is null && TryGetString(inner, "endpoint", out var ep))
                    Endpoint = ep;

                if (WtEndpoint is null)
                {
                    if (TryGetString(inner, "wt_endpoint", out var wte)) WtEndpoint = wte;
                    else if (TryGetString(inner, "wtEndpoint", out var wte2)) WtEndpoint = wte2;
                }

                // ICE
                if (Turn is null)
                {
                    var t = ReadIce(inner, "turn_server");
                    if (t is not null)
                    {
                        Turn = t;
                        TurnExpiresAtUtc = ComputeTurnExpiry(t.Username);
                        Console.WriteLine($"  [capture] turn_server     = {string.Join(", ", t.Urls)}");
                    }
                }
                if (Stun is null)
                {
                    var s = ReadIce(inner, "stun_server");
                    if (s is not null) Stun = s;
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    private static JsonElement Unwrap(JsonElement root)
    {
        if (root.ValueKind != JsonValueKind.Object) return root;
        foreach (var key in new[] { "data", "result", "conversation" })
        {
            if (root.TryGetProperty(key, out var inner) &&
                inner.ValueKind == JsonValueKind.Object)
                return inner;
        }
        return root;
    }

    private static bool TryGetString(JsonElement el, string name, out string value)
    {
        value = "";
        if (el.ValueKind != JsonValueKind.Object) return false;
        if (!el.TryGetProperty(name, out var v)) return false;
        if (v.ValueKind != JsonValueKind.String) return false;
        value = v.GetString() ?? "";
        return value.Length > 0;
    }

    private static bool LooksLikeUuid(string s) =>
        s.Length == 36 && s.Count(c => c == '-') == 4;

    private static readonly Regex UsernameTsRegex =
        new(@"^(?<ts>\d{9,11}):", RegexOptions.Compiled);

    private static DateTime? ComputeTurnExpiry(string? username)
    {
        if (string.IsNullOrEmpty(username)) return null;
        var m = UsernameTsRegex.Match(username);
        if (!m.Success) return null;
        if (!long.TryParse(m.Groups["ts"].Value, out var ts)) return null;
        return DateTimeOffset.FromUnixTimeSeconds(ts).UtcDateTime;
    }

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
}
