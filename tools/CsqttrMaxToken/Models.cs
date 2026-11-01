namespace CsqttrMaxToken;

/// <summary>
/// Результат extraction для одной группы.
/// </summary>
public sealed class CallPayload
{
    public string GroupId { get; init; } = "";
    public string? ConversationId { get; init; }
    public string? Endpoint { get; init; }
    public string? WtEndpoint { get; init; }
    public string? SignalingToken { get; init; }
    public IceServer? Turn { get; init; }
    public IceServer? Stun { get; init; }
    public DateTime ExtractedAtUtc { get; init; }
    public DateTime? TurnExpiresAtUtc { get; init; }
}

public sealed class IceServer
{
    public string[] Urls { get; init; } = Array.Empty<string>();
    public string? Username { get; init; }
    public string? Credential { get; init; }
}

public sealed class ExtractorResult
{
    public string? SessionKey { get; init; }
    public string? SessionSecretKey { get; init; }
    public string? Uid { get; init; }
    public string? ExternalUserId { get; init; }
    public string? ApiServer { get; init; }
    public string? AccessToken { get; init; }
    public DateTime ExtractedAtUtc { get; init; }
    public List<CallPayload> Calls { get; init; } = new();
}
