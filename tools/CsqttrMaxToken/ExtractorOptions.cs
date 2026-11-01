namespace CsqttrMaxToken;

public sealed class ExtractorOptions
{
    public required string SessionPath { get; init; }
    public required string OutputPath { get; init; }
    public required IReadOnlyList<string> GroupIds { get; init; }

    /// <summary>
    /// Стартовый режим браузера. По умолчанию true (headless).
    /// Если <see cref="AllowHeadedFallback"/> = true и требуется логин —
    /// браузер переоткрывается в headed, а после логина снова в headless.
    /// </summary>
    public bool Headless { get; init; } = true;

    /// <summary>
    /// Разрешить авто-переключение headless → headed → headless,
    /// если токена нет и требуется ручной логин.
    /// </summary>
    public bool AllowHeadedFallback { get; init; } = true;

    public TimeSpan LoginTimeout { get; init; } = TimeSpan.FromMinutes(15);
    public TimeSpan LoginPollInterval { get; init; } = TimeSpan.FromMilliseconds(500);
    public TimeSpan PauseBetweenGroups { get; init; } = TimeSpan.FromSeconds(8);
    public TimeSpan PostClickWait { get; init; } = TimeSpan.FromMilliseconds(4000);

    public bool Debug { get; init; }
    public string? DebugDir { get; init; }
}
