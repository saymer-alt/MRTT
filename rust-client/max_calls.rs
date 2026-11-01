//! Транспорт MAX (web.max.ru / oneme).
//!
//! MAX едет на том же Calls-бэкенде, что и VK (`https://calls.okcdn.ru/fb.do`,
//! методы `auth.anonymLogin` / `vchat.*`). Отличия:
//!   1. application_key: `CNHIJPLGDIHBABABA` (у VK — `CGMMEJLGDIHBABABA`);
//!   2. call_token — через WS `wss://ws-api.oneme.ru/websocket` (INIT → LOGIN → 158);
//!   3. звонок создаётся внутри группы: `payload.chat_id`.
//!
//! Брокер и «создатель звонка» переиспользуются из `vk_js_calls` с бэкендом
//! `CallBackend::Max`.

use crate::profiles::Profile;
use crate::vk_js_calls::{
    ActiveCalls, ApiSession, Bootstrap, CallBackend, CredentialBroker, RequestSite,
    StartedCalls, check_api_error, post_form_with_origin, required_string, retry,
};
use anyhow::{Context, Result, bail};
use futures::{SinkExt, StreamExt};
use primp::Client;
use serde_json::{Value, json};
use std::{time::Duration, sync::Arc};
use tokio_tungstenite::{connect_async, tungstenite::{Message, client::IntoClientRequest}};
use uuid::Uuid;

pub const MAX_API_KEY: &str = "CNHIJPLGDIHBABABA";
pub const ONEME_WS_URL: &str = "wss://ws-api.oneme.ru/websocket";
pub const MAX_APP_VERSION: &str = "26.10.3";
pub const MAX_CALLS_API: &str = "https://calls.okcdn.ru/fb.do";
pub const MAX_ORIGIN: &str = "https://web.max.ru";

/// Открыть сессию Calls-бэкенда для MAX: WS-логин oneme → auth.anonymLogin.
pub(crate) async fn open_max_session(
    client: &Client,
    access_token: &str,
    device_id: &str,
) -> Result<ApiSession> {
    let call_token = fetch_call_token(access_token).await.context("MAX WS call token")?;
    let session_data = json!({
        "auth_token": call_token,
        "client_type": "SDK_JS",
        "client_version": "1.1",
        "device_id": device_id,
        "version": 3,
    })
    .to_string();
    let login = retry(3, || async {
        post_form_with_origin(
            client,
            MAX_CALLS_API,
            &[
                ("session_data", session_data.as_str()),
                ("method", "auth.anonymLogin"),
                ("format", "JSON"),
                ("application_key", MAX_API_KEY),
            ],
            RequestSite::Ok,
            MAX_ORIGIN,
        )
        .await
    })
    .await
    .context("MAX auth.anonymLogin")?;
    check_api_error(&login)?;
    // Веб-клиент MAX последовательно дёргает calls.okcdn.ru/fb.do,
    // игнорируя поле api_server из ответа — делаем так же.
    Ok(ApiSession {
        api_url: MAX_CALLS_API.to_owned(),
        session_key: required_string(&login, "session_key")?.to_owned(),
    })
}

/// Старт MAX-транспорта: создаём звонок в группе и удерживаем его
/// (аналог `vk_js_calls::start`, но без join-хеша).
pub async fn start(
    bootstrap: Bootstrap,
    group_ids: &[String],
    profile: &Profile,
) -> Result<StartedCalls> {
    if bootstrap.token.trim().is_empty() {
        bail!("MAX access token отсутствует");
    }
    let Some(group_id) = group_ids.first().filter(|id| !id.is_empty()) else {
        bail!("Не указан ID группы MAX (--max <group_id>)");
    };
    let client = Client::builder()
        .impersonate(profile.impersonate)
        .impersonate_os(profile.os)
        .timeout(Duration::from_secs(15))
        .connect_timeout(Duration::from_secs(8))
        .redirect(primp::redirect::Policy::limited(5))
        .dns_resolver(crate::dns::global())
        .build()
        .context("инициализация браузерного HTTP-клиента (MAX)")?;
    let access_token = bootstrap.token;
    let device_id = Uuid::new_v4().to_string();
    let creator_session = open_max_session(&client, &access_token, &device_id).await?;

    let info = post_form_with_origin(
        &client,
        &creator_session.api_url,
        &[
            ("method", "system.getInfo"),
            ("format", "JSON"),
            ("application_key", MAX_API_KEY),
            ("session_key", creator_session.session_key.as_str()),
        ],
        RequestSite::Ok,
        MAX_ORIGIN,
    )
    .await
    .context("MAX system.getInfo")?;
    check_api_error(&info)?;
    if info.get("serverTime").is_none() {
        bail!("MAX system.getInfo: нет serverTime");
    }
    crate::log_error!("[MAX] Сессия Calls подтверждена · группа {group_id}");

    let conversation_id = Uuid::new_v4().to_string();
    let payload = json!({ "is_video": false, "chat_id": group_id }).to_string();
    let call = retry(3, || async {
        post_form_with_origin(
            &client,
            &creator_session.api_url,
            &[
                ("conversationId", conversation_id.as_str()),
                ("isVideo", "false"),
                ("protocolVersion", "5"),
                ("payload", payload.as_str()),
                ("method", "vchat.startConversation"),
                ("format", "JSON"),
                ("application_key", MAX_API_KEY),
                ("session_key", creator_session.session_key.as_str()),
            ],
            RequestSite::Ok,
            MAX_ORIGIN,
        )
        .await
    })
    .await;
    // Как референс-экстрактор: startConversation у MAX часто отвечает VCHAT_DETAILED_ERROR,
    // но TURN выдаёт getConversationParams по локально сгенерированному conversationId —
    // поэтому ошибку старта не считаем фатальной и продолжаем со своим conversationId.
    let final_conversation_id = match call {
        Ok(value) => match check_api_error(&value) {
            Ok(()) => value
                .get("id")
                .or_else(|| value.get("conversationId"))
                .and_then(Value::as_str)
                .filter(|id| id.len() == 36)
                .map(str::to_owned)
                .unwrap_or_else(|| conversation_id.clone()),
            Err(error) => {
                // Штатный ответ MAX (обычно API 1114): пишем без текста ошибки, иначе UI красит строку как сбой.
                let code = error.to_string();
                let code = code.split(':').next().unwrap_or("API").trim().to_owned();
                crate::log_info!("[MAX] Звонок без startConversation ({code}) · TURN по локальному conversationId");
                conversation_id.clone()
            }
        },
        Err(error) => {
            crate::log_error!("[MAX] vchat.startConversation не ответил: {error:#} · продолжаю с локальным conversationId");
            conversation_id.clone()
        }
    };
    crate::log_error!("[MAX] Звонок создан: {final_conversation_id}");
    let credential_broker = Arc::new(CredentialBroker::from_parts(
        client.clone(),
        access_token.clone(),
        final_conversation_id.clone(),
        CallBackend::Max,
    ));
    let active = ActiveCalls::from_parts(
        client, creator_session.api_url, access_token, creator_session.session_key,
        vec![final_conversation_id], CallBackend::Max,
    );
    Ok(StartedCalls {
        hashes: vec!["max".to_owned()],
        active,
        credential_broker,
    })
}

/// Получить call_token через WS-протокол oneme (INIT → LOGIN → 158).
async fn fetch_call_token(access_token: &str) -> Result<String> {
    let mut request = ONEME_WS_URL.into_client_request()
        .context("MAX WebSocket request")?;
    request.headers_mut().insert("Origin", "https://web.max.ru".parse()?);
    request.headers_mut().insert("User-Agent", "Mozilla/5.0 Chrome/144.0.0.0 Safari/537.36".parse()?);
    let (stream, _) = tokio::time::timeout(Duration::from_secs(10), connect_async(request))
        .await.context("MAX WS connect timeout")?
        .context("WS подключение ws-api.oneme.ru")?;
    let (mut sink, mut reader) = stream.split();

    let device_id = Uuid::new_v4().to_string();
    let init = json!({
        "ver": 11, "cmd": 0, "seq": 1, "opcode": 6,
        "payload": {
            "deviceId": device_id,
            "userAgent": {
                "deviceType": "WEB",
                "pushDeviceType": "WEBPUSH",
                "locale": "ru",
                "deviceLocale": "ru",
                "osVersion": "Windows",
                "deviceName": "Chrome",
                "headerUserAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36",
                "appVersion": MAX_APP_VERSION,
                "screen": "1920x1080 1.0x",
                "timezone": "Europe/Moscow",
            }
        }
    });
    sink.send(Message::Text(init.to_string().into()))
        .await
        .context("WS send INIT")?;

    let mut seq = 1u64;
    let deadline = tokio::time::Instant::now() + Duration::from_secs(20);
    loop {
        let msg = tokio::time::timeout_at(deadline, reader.next())
            .await
            .context("WS таймаут oneme")?
            .context("WS oneme закрыт")?
            .context("WS oneme ошибка потока")?;
        let text: &str = match &msg {
            Message::Text(t) => t.as_ref(),
            Message::Close(_) => bail!("WS oneme закрыт сервером до ответа 158"),
            _ => continue,
        };
        let m: Value = serde_json::from_str(text).context("WS oneme: некорректный JSON")?;
        let cmd = m.get("cmd").and_then(Value::as_i64).unwrap_or(-1);
        if cmd == 0 {
            continue;
        }
        if cmd == 3 {
            bail!("WS oneme ошибка: {:?}", m.get("payload"));
        }
        if cmd != 1 {
            continue;
        }
        match m.get("opcode").and_then(Value::as_i64).unwrap_or(-1) {
            6 => {
                seq += 1;
                let login = json!({
                    "ver": 11, "cmd": 0, "seq": seq, "opcode": 19,
                    "payload": {
                        "token": access_token,
                        "interactive": true,
                        "chatsCount": 0, "chatsSync": 0, "contactsSync": 0,
                        "presenceSync": 0, "draftsSync": 0,
                    }
                });
                sink.send(Message::Text(login.to_string().into()))
                    .await
                    .context("WS send LOGIN")?;
            }
            19 => {
                seq += 1;
                let req = json!({ "ver": 11, "cmd": 0, "seq": seq, "opcode": 158, "payload": {} });
                sink.send(Message::Text(req.to_string().into()))
                    .await
                    .context("WS send 158")?;
            }
            158 => {
                return m
                    .pointer("/payload/token")
                    .and_then(Value::as_str)
                    .map(str::to_owned)
                    .context("WS oneme 158: нет payload.token");
            }
            _ => {}
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn max_api_key_matches_extractor() {
        assert_eq!(MAX_API_KEY, "CNHIJPLGDIHBABABA");
        assert!(MAX_CALLS_API.ends_with("/fb.do"));
        assert!(ONEME_WS_URL.starts_with("wss://ws-api.oneme.ru"));
    }
}