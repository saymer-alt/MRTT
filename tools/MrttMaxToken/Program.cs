using System.CommandLine;
using MrttMaxToken;

// ─────────────────────────────────────────────────────────────────────────────
//  MRTT MAX Token v6
//
//  Извлекает TURN/STUN-креденшелы MAX.
//
//  Поведение браузера:
//    - по умолчанию headless
//    - если нужно залогиниться — браузер переоткрывается headed,
//      ждём логина, затем снова переоткрываем в headless
//
//  Флаги:
//    --session-path      Папка persistent-профиля Chromium (обязательно)
//    --group_ids         ID групп MAX через запятую (обязательно)
//    --headless          Форсировать старт в headless (по умолчанию: true)
//    --no-headless       Форсировать старт в headed (без авто-переключения)
//    --output            Путь к call.json
//    --timeout-min       Максимум минут ожидания логина
//    --pause-sec         Пауза между группами
//    --wait-ms           Сколько ждать после 2-го клика (мс)
//    --debug             Сохранять ВСЕ /fb.do запросы в debug-fbdo.jsonl
//    --debug-dir         Куда сохранять debug (по умолчанию рядом с call.json)
// ─────────────────────────────────────────────────────────────────────────────

// ── Playwright: install browsers before anything else ────────────────────────
Console.WriteLine("→ Устанавливаю/проверяю браузеры Playwright...");
var installExitCode = Microsoft.Playwright.Program.Main(new[] { "install" });
if (installExitCode != 0)
{
    Console.Error.WriteLine($"✗ Playwright install завершился с кодом {installExitCode}");
    Environment.Exit(installExitCode);
}
Console.WriteLine("  ✓ браузеры готовы");
Console.WriteLine();

var sessionPathOpt = new Option<string>(
    name: "--session-path",
    description: "Папка persistent-профиля Chromium.")
{ IsRequired = true };

var groupIdsOpt = new Option<string>(
    name: "--group_ids",
    description: "ID групп MAX через запятую.")
{ IsRequired = true };

var headlessOpt = new Option<bool>(
    name: "--headless",
    description: "Форсировать старт в headless (по умолчанию: true).",
    getDefaultValue: () => true);

var noHeadlessOpt = new Option<bool>(
    name: "--no-headless",
    description: "Форсировать старт в headed. Авто-переключение отключается.",
    getDefaultValue: () => false);

var outputOpt = new Option<string?>(
    name: "--output",
    description: "Путь к call.json.");

var timeoutOpt = new Option<int>(
    name: "--timeout-min",
    description: "Максимум минут ожидания логина.",
    getDefaultValue: () => 15);

var pauseOpt = new Option<int>(
    name: "--pause-sec",
    description: "Пауза между группами (сек).",
    getDefaultValue: () => 8);

var waitOpt = new Option<int>(
    name: "--wait-ms",
    description: "Сколько ждать после 2-го клика (мс).",
    getDefaultValue: () => 4000);

var debugOpt = new Option<bool>(
    name: "--debug",
    description: "Сохранять все запросы/ответы к /fb.do в отдельный файл.",
    getDefaultValue: () => false);

var debugDirOpt = new Option<string?>(
    name: "--debug-dir",
    description: "Каталог для debug-файлов (по умолчанию рядом с call.json).");

var rootCommand = new RootCommand("MRTT MAX Token — TURN extraction via UI + network capture")
{
    sessionPathOpt, groupIdsOpt, headlessOpt, noHeadlessOpt, outputOpt,
    timeoutOpt, pauseOpt, waitOpt, debugOpt, debugDirOpt,
};

rootCommand.SetHandler(async (ctx) =>
{
    var sessionPath = ctx.ParseResult.GetValueForOption(sessionPathOpt)!;
    var groupIds    = ctx.ParseResult.GetValueForOption(groupIdsOpt)!;
    var headless    = ctx.ParseResult.GetValueForOption(headlessOpt);
    var noHeadless  = ctx.ParseResult.GetValueForOption(noHeadlessOpt);
    var output      = ctx.ParseResult.GetValueForOption(outputOpt);
    var timeoutMin  = ctx.ParseResult.GetValueForOption(timeoutOpt);
    var pauseSec    = ctx.ParseResult.GetValueForOption(pauseOpt);
    var waitMs      = ctx.ParseResult.GetValueForOption(waitOpt);
    var debug       = ctx.ParseResult.GetValueForOption(debugOpt);
    var debugDir    = ctx.ParseResult.GetValueForOption(debugDirOpt);

    // --no-headless имеет приоритет над --headless
    if (noHeadless) headless = false;

    var opts = new ExtractorOptions
    {
        SessionPath = Path.GetFullPath(sessionPath),
        GroupIds = groupIds
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .ToList(),
        Headless = headless,
        AllowHeadedFallback = !noHeadless,       // при --no-headless авто-переключение отключено
        OutputPath = output is null
            ? Path.Combine(AppContext.BaseDirectory, "call.json")
            : Path.GetFullPath(output),
        LoginTimeout = TimeSpan.FromMinutes(timeoutMin),
        PauseBetweenGroups = TimeSpan.FromSeconds(pauseSec),
        PostClickWait = TimeSpan.FromMilliseconds(waitMs),
        Debug = debug,
        DebugDir = debugDir is null
            ? null
            : Path.GetFullPath(debugDir),
    };

    var exitCode = await Extractor.RunAsync(opts);
    Environment.Exit(exitCode);
});

return await rootCommand.InvokeAsync(args);
