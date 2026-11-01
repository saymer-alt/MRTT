# Изменения в этом архиве

- `rust-client/main.rs`: выбор MAX через `--max`, чтение безопасного bootstrap через stdin, совместимость с существующими VK-режимами.
- `rust-client/max_calls.rs`: MAX WS и OK Calls API; отдельная сессия звонка в выбранной группе. Источник протокола: `tools/CsqttrMaxToken`.
- `rust-client/vk_js_calls.rs`: общий брокер TURN параметров с явным выбором VK/MAX application_key и Origin.
- `rust-client/Cargo.toml`: зависимости websocket и futures.
- `app/src/main/java/com/csqtt/client` и `ui`: настройки MAX, защищённое хранение токена, восстановление подключений, запуск, обработка max-ссылок и fallback сети.
- `app/src/main/AndroidManifest.xml`: только ссылки csqttr://.
- `rust-server/web_panel.rs`: новые ссылки генерируются с `csqttr://`, старые ссылки **не** принимаются; серверный UDP-транспорт не менялся.
- `scripts/make_csqttr_link.py`: локальная генерация MAX/VK ссылок с AES-GCM (без токенов).
- `tools/CsqttrMaxToken`: основа экстрактора включена в исходный архив.
- `README_MAX_CSQTTR.md`: сборка, развёртывание, сценарии теста и не подтверждённые аспекты.

## Границы готовности

Это **исходники**, не собранный APK и не подтверждённый в MAX сетевой туннель. Rust/Cargo, NDK и полноценные тесты сборки в этой среде недоступны. Перед тестовой Android-сборкой выполните `cargo fmt --all` в `rust-client`; Cargo также обновит `Cargo.lock` при первом разрешении новых зависимостей. После сборки клиента обязательно обновите нативный provenance, прежде чем собирать APK. Не загружайте `call.json`, `max-profile` или приватные токены в репозиторий.

- `.github/workflows/build-test-apk.yml`: автоматическая сборка при `push` в ветку `max-test` через GitHub Actions (не подтверждена запуском).
- `app/build.gradle.kts`: debug-сборка с отдельным Android applicationId для установки рядом с оригиналом.
- `local.properties.example`, `.gitignore`: исключение локальных данных и SDK/подписи.

## Поправки сборки и тестирования 22 октября 2026

- В Android и серверной части `csqtt://` отклоняется, ссылка генерируется только как `csqttr://`.
- Разрешены отрицательные ID групп MAX при передаче в Rust CLI (Clap `allow_hyphen_values`).
- Согласована валидация формата MAX ID (`-?[0-9]+`) в Android и генераторе Python.
- `.github/workflows/build-test-apk.yml`: автоматическая сборка полного тестового APK из ветки `max-test` в GitHub Actions; установка JDK 17, SDK 37, NDK 28.2 и Rust 1.97.1.
- Отображаемое имя Android-приложения `MRTT`; debug-пакет с Android `applicationIdSuffix` `.maxdebug`, чтобы устанавливать тест рядом с оригинальной программой.
- `GITHUB_ACTIONS_GUIDE_RU.md`: пошаговая загрузка через GitHub Desktop в отдельную ветку `max-test`, автоматический запуск Actions и скачивание APK.
