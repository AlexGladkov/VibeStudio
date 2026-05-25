# Remote Control — Спецификация

**Дата:** 2026-05-14
**Платформа:** macOS 14+ (сервер), любой мобильный браузер (клиент)
**Статус:** Draft

---

## Концепция

Встроенный HTTP/WebSocket сервер в VibeStudio, позволяющий управлять терминальными сессиями с мобильного устройства через браузер. Основной use case — мониторинг и управление Claude Code и другими CLI-агентами в поездках.

---

## Архитектура

### Серверная часть (macOS, встроена в VibeStudio)

```
┌─────────────────────────────────────────────┐
│  VibeStudio App                              │
│                                              │
│  ┌──────────────┐    ┌───────────────────┐  │
│  │ RemoteServer  │────│ TerminalService   │  │
│  │ (NIOHTTPSvr)  │    │ (существующий)    │  │
│  │               │    └───────────────────┘  │
│  │  HTTP  ──→ Static Web UI (xterm.js)      │
│  │  WS    ←→ Terminal I/O relay             │
│  │  REST  ──→ Projects/sessions list        │
│  └──────────────┘                            │
│       │                                      │
│  ┌────┴─────────┐                            │
│  │ BonjourAdvert │  (_vibestudio._tcp)       │
│  └──────────────┘                            │
└─────────────────────────────────────────────┘
```

**Ключевые компоненты:**

1. **`RemoteControlServer`** — HTTP + WebSocket сервер на SwiftNIO
2. **`RemoteAuthService`** — генерация PIN, валидация, rate limiting
3. **`RemoteSessionBridge`** — мост между WebSocket и `TerminalService.sendInput`/`scrollbackContent`
4. **`BonjourAdvertiser`** — публикация сервиса через `NetService` (`_vibestudio._tcp`)
5. **`RemoteControlPreferences`** — настройки (порт, автостарт, вкл/выкл)

### Клиентская часть (Web UI)

Статический HTML/JS/CSS, отдаётся встроенным HTTP-сервером. Основа — xterm.js для рендера терминала.

```
┌──────────────────────────────────┐
│  Mobile Browser                   │
│                                   │
│  ┌─────────────────────────────┐ │
│  │ Project/Session Picker      │ │
│  │ [proj-a ▼] [session-1 ▼]   │ │
│  ├─────────────────────────────┤ │
│  │                             │ │
│  │     xterm.js terminal       │ │
│  │                             │ │
│  ├─────────────────────────────┤ │
│  │ [Esc][Tab][Ctrl][↑][↓][←][→]│ │
│  │      Special Keys Bar       │ │
│  └─────────────────────────────┘ │
└──────────────────────────────────┘
```

---

## Протокол

### REST API

| Endpoint | Method | Описание |
|----------|--------|----------|
| `GET /` | — | Web UI (index.html) |
| `POST /api/auth/pin` | POST | Отправить PIN → получить session token |
| `GET /api/projects` | GET | Список проектов + сессий |
| `GET /api/status` | GET | Статус сервера (connected clients, uptime) |
| `POST /api/disconnect` | POST | Принудительное отключение (с мака) |

### WebSocket `/api/terminal/:sessionId`

Двунаправленный канал:

**Client → Server:**
```json
{"type": "input", "data": "ls -la\n"}
{"type": "resize", "cols": 80, "rows": 24}
{"type": "ping"}
```

**Server → Client:**
```json
{"type": "output", "data": "\x1b[32muser@host\x1b[0m:~$ "}
{"type": "session_ended", "exitCode": 0}
{"type": "pong"}
```

---

## Авторизация

### Поток

1. Пользователь открывает Web UI → видит форму ввода PIN
2. VibeStudio на маке показывает 6-значный PIN в Settings / toolbar popover
3. Пользователь вводит PIN на телефоне
4. `POST /api/auth/pin` → сервер проверяет → при успехе отдаёт JWT-like session token
5. Все последующие запросы включают token в заголовке `Authorization: Bearer <token>`
6. PIN одноразовый — после использования генерируется новый

### Rate Limiting

- 3 неверных попытки → блокировка IP на 5 минут
- После блокировки — генерируется новый PIN
- Лог всех попыток аутентификации (os_log)

### PIN-генерация

- Криптографически безопасный random (`SecRandomCopyBytes`)
- 6 цифр (000000–999999)
- Показывается в Settings pane + toolbar popover (при наведении на индикатор)
- Обновляется: при старте сервера, после использования, после блокировки

---

## Настройки (Settings pane)

Новая секция **"Remote Control"** в `GeneralSettingsPane`:

| Параметр | Тип | Default | Описание |
|----------|-----|---------|----------|
| `remoteControlEnabled` | Bool | `true` | Включить/выключить сервер |
| `remoteControlPort` | Int | `7842` | Порт HTTP/WS сервера |
| `currentPin` | String | (generated) | Текущий PIN (read-only, кнопка "Regenerate") |
| `connectedDevices` | [String] | [] | Список подключённых устройств (IP + User-Agent) |

---

## Toolbar интеграция

### Индикатор подключённых устройств

В `ToolbarView` — бейдж рядом с settings button:

- **0 подключений:** иконка скрыта
- **≥1 подключение:** иконка 📱 с числом подключённых устройств
- **Клик по иконке:** popover с PIN-кодом + список подключённых устройств + кнопка disconnect

---

## Bonjour Discovery

Сервис публикуется как `_vibestudio._tcp` на локальном порту.

Мобильный клиент в Web UI может использовать mDNS-SD для автообнаружения в локальной сети.
Для tunnel-подключений — пользователь вручную вводит URL.

---

## Безопасность

### TLS

- Self-signed TLS-сертификат генерируется при первом запуске
- Хранится в `~/Library/Application Support/VibeStudio/remote-tls/`
- Браузер покажет предупреждение — пользователь принимает один раз
- Для tunnel (Cloudflare) — TLS обеспечивается tunnel'ом

### Защита от атак

- PIN rate limiting (описан выше)
- WebSocket heartbeat — отключение неактивных клиентов через 60 сек без pong
- Максимум 3 одновременных подключения
- Все данные передаются через TLS (в локалке — self-signed, через tunnel — tunnel TLS)

### Терминальные данные

- Передаётся только то, что видно в терминале (scrollback buffer)
- Нет доступа к файловой системе напрямую
- Нет доступа к git-операциям (только терминальный ввод/вывод)

---

## Web UI — детали

### Зависимости (bundled)

- **xterm.js** — терминальный эмулятор (MIT)
- **xterm-addon-fit** — автоподгон размера
- **xterm-addon-web-links** — кликабельные ссылки

### Спецклавиши (toolbar над клавиатурой)

Строка кнопок над мобильной клавиатурой:

```
[Esc] [Tab] [Ctrl] [↑] [↓] [←] [→] [|]
```

- **Ctrl** — модификатор: нажал Ctrl → следующий символ отправляется как Ctrl+X
- **Esc** — отправляет `\x1b`
- **Tab** — отправляет `\t`
- **Стрелки** — ANSI escape sequences

### Адаптивность

- Портретная ориентация: терминал на весь экран, picker сверху, клавиши снизу
- Ландшафтная: терминал шире, picker компактнее
- Шрифт: monospace, размер подбирается под ширину экрана

### Тема

- Автоматически синхронизируется с темой VibeStudio (dark/light)
- Цвета терминала передаются через WebSocket при подключении

---

## Точки интеграции с существующим кодом

### `ServiceContainer`

Новые сервисы:
- `RemoteControlServer` (concrete `@Observable` type, как `TerminalService`)
- `RemoteControlPreferences` (concrete `@Observable` type, как `GeneralPreferences`)

### `TerminalService`

Используемые методы (уже существуют):
- `sessionsByProject` — список всех сессий
- `sendInput(_:to:)` — отправка текста в PTY
- `scrollbackContent(for:)` — чтение буфера
- `sessionEvents` — подписка на события (activity, exit)
- `attachView(to:)` — не нужен (мы не рендерим NSView)

**Новое:** нужен способ получать real-time output из PTY без NSView.
Сейчас `scrollbackContent` возвращает весь буфер — для WebSocket нужен инкрементальный поток.
Потребуется новый callback или AsyncStream на `TaggedTerminalView.onRangeChanged`.

### `ProjectManaging`

- `projects` — список проектов для picker'а
- `activeProjectId` — текущий активный проект

### `ToolbarView` / `ToolbarViewModel`

- Добавить индикатор подключённых устройств
- Popover с PIN и списком устройств

### `GeneralSettingsPane`

- Добавить секцию "Remote Control"

---

## Зависимости (новые SPM-пакеты)

| Пакет | Назначение |
|-------|------------|
| `swift-nio` | HTTP/WebSocket сервер |
| `swift-nio-ssl` | TLS для HTTP-сервера |
| `swift-nio-extras` | HTTP/1.1 helpers |

**Размер:** ~2 MB в финальном бандле.

---

## Риски

1. **SwiftNIO + MainActor** — NIO работает в EventLoop (не main thread). Вызовы `TerminalService` (MainActor) требуют правильного dispatching. Риск race condition при высокой частоте терминального вывода.

2. **xterm.js на мобильных** — iOS Safari имеет особенности с virtual keyboard и fixed-position элементами. Потребуется тестирование UX.

3. **Self-signed TLS** — браузеры показывают страшное предупреждение. Может запутать пользователей. Документация обязательна.

4. **PTY output streaming** — сейчас `TerminalService` не предоставляет инкрементальный поток output. Нужна доработка `TaggedTerminalView` или нового bridge'а через `LocalProcessTerminalView.send(source:)`.

5. **Firewall macOS** — при первом запуске macOS покажет диалог "Accept incoming connections". Пользователь должен разрешить.

---

## Этапы реализации

### Phase 1: Server + Auth
- `RemoteControlServer` (SwiftNIO HTTP + WS)
- `RemoteAuthService` (PIN, rate limiting)
- `RemoteControlPreferences` (port, enabled)
- Self-signed TLS generation
- DI integration (`ServiceContainer`)

### Phase 2: Terminal Bridge
- `RemoteSessionBridge` — мост PTY ↔ WebSocket
- Инкрементальный PTY output streaming
- `sendInput` relay через WebSocket

### Phase 3: Web UI
- HTML/CSS/JS с xterm.js
- Project/session picker
- Special keys bar
- Responsive layout

### Phase 4: Bonjour + UX
- `BonjourAdvertiser`
- Toolbar индикатор подключённых устройств
- Settings pane секция "Remote Control"
- PIN display в popover

### Phase 5: Polish
- Тема синхронизация
- Reconnect logic в Web UI
- Документация tunnel setup (Cloudflare, Tailscale)
