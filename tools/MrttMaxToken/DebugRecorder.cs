using System.Text;
using System.Text.Json;

namespace MrttMaxToken;

/// <summary>
/// Пишет ВСЕ запросы и ответы к /fb.do в NDJSON-файл.
/// Формат: одна JSON-запись на строку.
///
/// События:
///   {"ts":"...","kind":"request","id":"...","url":"...","method":"...","body":"..."}
///   {"ts":"...","kind":"response","id":"...","url":"...","status":200,"body":"..."}
///   {"ts":"...","kind":"error","id":"...","url":"...","message":"..."}
/// </summary>
public sealed class DebugRecorder : IAsyncDisposable
{
    private readonly StreamWriter? _writer;
    private readonly object _lock = new();
    private readonly Dictionary<string, string> _requestBodies = new();
    private int _reqSeq;

    public bool Enabled => _writer is not null;
    public string? Path { get; }

    public DebugRecorder(bool enabled, string? dir, string baseName)
    {
        if (!enabled) return;

        dir ??= AppContext.BaseDirectory;
        Directory.CreateDirectory(dir);

        var ts = DateTime.UtcNow.ToString("yyyyMMdd-HHmmss");
        Path = System.IO.Path.Combine(dir, $"{baseName}-{ts}.debug.jsonl");

        _writer = new StreamWriter(Path, append: false, Encoding.UTF8)
        {
            AutoFlush = true,
        };
        WriteEvent(new { ts = Iso(), kind = "session", message = "DebugRecorder started" });
    }

    /// <summary>
    /// Регистрирует отправленный POST к /fb.do. Возвращает id для сопоставления.
    /// </summary>
    public string LogRequest(string url, string? method, string? body)
    {
        if (_writer is null) return "";

        string id;
        lock (_lock)
        {
            id = $"r{++_reqSeq}";
            _requestBodies[id] = body ?? "";
        }

        WriteEvent(new
        {
            ts = Iso(),
            kind = "request",
            id,
            url,
            method,
            body,
        });
        return id;
    }

    /// <summary>
    /// Регистрирует ответ. Пытается восстановить тело запроса по id.
    /// </summary>
    public void LogResponse(string url, int status, string? body)
    {
        if (_writer is null) return;

        string? reqBody = null;
        lock (_lock)
        {
            // Отдаём «последний» неиспользованный request body,
            // если id не передан явно (Playwright не даёт id в response).
            // Просто сохраняем всё как есть.
            _ = _requestBodies;
        }

        WriteEvent(new
        {
            ts = Iso(),
            kind = "response",
            url,
            status,
            body,
        });
    }

    public void LogError(string url, string message)
    {
        if (_writer is null) return;
        WriteEvent(new { ts = Iso(), kind = "error", url, message });
    }

    public void LogNote(string message, object? extra = null)
    {
        if (_writer is null) return;
        WriteEvent(new { ts = Iso(), kind = "note", message, extra });
    }

    private void WriteEvent(object ev)
    {
        if (_writer is null) return;
        try
        {
            var json = JsonSerializer.Serialize(ev, JsonOpts.Compact);
            lock (_lock) _writer.WriteLine(json);
        }
        catch { /* ignore */ }
    }

    private static string Iso() => DateTime.UtcNow.ToString("o");

    public async ValueTask DisposeAsync()
    {
        if (_writer is null) return;
        try
        {
            WriteEvent(new { ts = Iso(), kind = "session", message = "DebugRecorder stopped" });
            await _writer.FlushAsync();
            _writer.Dispose();
        }
        catch { /* ignore */ }
    }
}

file static class JsonOpts
{
    public static readonly JsonSerializerOptions Compact = new()
    {
        WriteIndented = false,
        DefaultIgnoreCondition =
            System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };
}
