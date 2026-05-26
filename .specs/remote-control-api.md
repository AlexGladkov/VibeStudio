# Remote Control API Specification

**Version:** 1.0.0
**Date:** 2026-05-14
**Base URL:** `https://<host>:<port>/api/v1`
**Transport:** HTTPS (self-signed TLS) + WSS
**Content-Type:** `application/json; charset=utf-8`

---

## Table of Contents

1. [API Versioning Strategy](#1-api-versioning-strategy)
2. [Authentication Flow](#2-authentication-flow)
3. [Common Headers](#3-common-headers)
4. [Error Format](#4-error-format)
5. [Rate Limiting](#5-rate-limiting)
6. [CORS Policy](#6-cors-policy)
7. [REST Endpoints](#7-rest-endpoints)
8. [WebSocket Protocol](#8-websocket-protocol)
9. [Data Models](#9-data-models)
10. [Security Considerations](#10-security-considerations)

---

## 1. API Versioning Strategy

### Approach: URI Path Versioning

All REST endpoints are prefixed with `/api/v1/`. WebSocket endpoints follow the
same convention: `/api/v1/terminal/{sessionId}`.

**Rationale:** URI versioning is the simplest and most explicit strategy for an
embedded server with a single bundled client. Unlike header-based versioning, it
is trivially cacheable, debuggable in browser DevTools, and unambiguous.

### Version Lifecycle

| Phase        | Duration       | Behavior                                      |
|--------------|----------------|-----------------------------------------------|
| Active       | Current        | Full support, new features                    |
| Deprecated   | +2 minor       | `Sunset` header, warnings in response body    |
| Removed      | +1 minor after | 410 Gone with migration instructions          |

### Breaking Change Policy

A new major version (`v2`) is introduced only when:
- Resource structure changes incompatibly
- Authentication mechanism changes
- WebSocket message format changes

Non-breaking additions (new fields, new optional parameters, new endpoints) are
added to the current version without bumping.

### Headers

```
API-Version: 1.0.0
Sunset: Wed, 01 Jan 2028 00:00:00 GMT   (only on deprecated versions)
Deprecation: true                         (only on deprecated versions)
```

---

## 2. Authentication Flow

### PIN-to-Token Exchange

```
Mobile Browser                          VibeStudio Server
     |                                        |
     |  1. GET /                               |
     |--------------------------------------->|
     |  200 OK (login page HTML)              |
     |<---------------------------------------|
     |                                        |
     |  2. POST /api/v1/auth/token            |
     |     {"pin": "482917"}                  |
     |--------------------------------------->|
     |                                        |
     |  [Server validates PIN]                |
     |  [PIN is consumed -- new PIN generated]|
     |                                        |
     |  3a. 200 OK                            |
     |      {"token": "<jwt>",                |
     |       "expires_at": "...",             |
     |       "device_id": "d-abc123"}         |
     |<---------------------------------------|
     |                                        |
     |  -- OR on failure --                   |
     |                                        |
     |  3b. 401 Unauthorized                  |
     |      {"error": {...}}                  |
     |<---------------------------------------|
     |                                        |
     |  4. GET /api/v1/projects               |
     |     Authorization: Bearer <jwt>        |
     |--------------------------------------->|
     |  200 OK                                |
     |<---------------------------------------|
     |                                        |
     |  5. WSS /api/v1/terminal/{sessionId}   |
     |     ?token=<jwt>                       |
     |  (Sec-WebSocket-Protocol: vibestudio)  |
     |<======================================>|
```

### Token Format (JWT-like)

The token is a compact, signed string. It is NOT a full JWT because there is no
need for interoperability with external systems. The server is the sole issuer
and validator.

**Token payload (internal):**

```json
{
  "did": "d-abc123",           // device ID (generated on auth)
  "iat": 1715644800,          // issued at (Unix timestamp)
  "exp": 1715731200,          // expires at (24h from issuance)
  "ip": "192.168.1.42"        // bound to client IP
}
```

**Signing:** HMAC-SHA256 with a per-launch random secret (32 bytes from
`SecRandomCopyBytes`). The secret lives only in memory and is never persisted --
all tokens are invalidated on app restart.

**Token lifetime:** 4 hours. No refresh mechanism -- user re-authenticates with
a new PIN after expiry. This is intentional: the threat model assumes a local
network where re-authentication is trivial.

### Token Validation Rules

1. Signature must be valid (HMAC-SHA256)
2. `exp` must be in the future
3. `ip` must match the requesting client IP (prevents token theft across devices)
4. `did` must not be in the revocation set (populated by `POST /api/v1/devices/{deviceId}/disconnect`)

---

## 3. Common Headers

### Request Headers

| Header            | Required | Description                                |
|-------------------|----------|--------------------------------------------|
| `Authorization`   | Yes*     | `Bearer <token>` (not required for auth endpoints and static files) |
| `Content-Type`    | Yes**    | `application/json` (for POST/PUT/PATCH)    |
| `Accept`          | No       | `application/json` (assumed default)       |
| `X-Request-Id`    | No       | Client-generated UUID for tracing          |

### Response Headers (always present)

| Header                    | Description                                      |
|---------------------------|--------------------------------------------------|
| `Content-Type`            | `application/json; charset=utf-8`                |
| `X-Request-Id`            | Echoed from request or server-generated UUID     |
| `API-Version`             | `1.0.0`                                          |
| `X-RateLimit-Limit`       | Max requests per window                          |
| `X-RateLimit-Remaining`   | Remaining requests in current window             |
| `X-RateLimit-Reset`       | Unix timestamp when the window resets            |
| `Cache-Control`           | `no-store` (all API responses)                   |
| `X-Content-Type-Options`  | `nosniff`                                        |
| `X-Frame-Options`         | `DENY`                                           |
| `Strict-Transport-Security` | `max-age=31536000` (HSTS)                     |

---

## 4. Error Format

All errors follow a single consistent structure. HTTP status code is always
accompanied by a machine-readable `code` and a human-readable `message`.

### Error Response Body

```json
{
  "error": {
    "code": "AUTH_PIN_INVALID",
    "message": "The PIN you entered is incorrect. 2 attempts remaining before lockout.",
    "details": {
      "attempts_remaining": 2,
      "lockout_duration_seconds": 300
    },
    "request_id": "550e8400-e29b-41d4-a716-446655440000",
    "docs_url": "https://vibestudio.dev/docs/errors#AUTH_PIN_INVALID"
  }
}
```

### Error Codes Catalog

| HTTP Status | Code                          | When                                              |
|-------------|-------------------------------|---------------------------------------------------|
| 400         | `INVALID_REQUEST`             | Malformed JSON, missing required fields           |
| 400         | `INVALID_PARAMETER`           | Parameter fails validation (e.g. bad UUID format) |
| 401         | `AUTH_REQUIRED`               | No `Authorization` header                         |
| 401         | `AUTH_TOKEN_EXPIRED`          | Token `exp` is in the past                        |
| 401         | `AUTH_TOKEN_INVALID`          | Signature mismatch or malformed token             |
| 401         | `AUTH_PIN_INVALID`            | Wrong PIN                                         |
| 403         | `AUTH_IP_MISMATCH`            | Token IP does not match request IP                |
| 403         | `AUTH_DEVICE_REVOKED`         | Device was disconnected by the user               |
| 404         | `PROJECT_NOT_FOUND`           | Unknown project ID                                |
| 404         | `SESSION_NOT_FOUND`           | Unknown session ID                                |
| 409         | `SESSION_ALREADY_ATTACHED`    | Another remote client is attached to this session |
| 429         | `RATE_LIMITED`                | Too many requests                                 |
| 429         | `AUTH_LOCKOUT`                | Too many failed PIN attempts, IP blocked          |
| 500         | `INTERNAL_ERROR`              | Unhandled server error                            |
| 503         | `SERVER_SHUTTING_DOWN`        | VibeStudio is quitting                            |

### Retry Guidance

The `Retry-After` header is included with 429 and 503 responses:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 300
X-RateLimit-Reset: 1715645100
```

---

## 5. Rate Limiting

### Strategy: Sliding Window by IP

| Scope               | Limit                | Window   | Notes                          |
|----------------------|----------------------|----------|--------------------------------|
| `POST /auth/token`   | 3 attempts           | 5 min    | After 3 failures: IP lockout  |
| Authenticated REST   | 60 requests          | 1 min    | Per device (by `did`)         |
| WebSocket messages   | 120 messages         | 1 min    | Per connection (input only)   |

### Headers (on every response)

```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 47
X-RateLimit-Reset: 1715645100
```

### Lockout Behavior

When auth rate limit is hit:
1. IP is blocked for 5 minutes
2. Current PIN is invalidated and a new one is generated
3. All existing tokens from that IP are revoked
4. Event logged via `os_log` at `.error` level

---

## 6. CORS Policy

Required for tunnel scenarios where the browser origin differs from the server
origin (e.g. `https://random-slug.trycloudflare.com` accessing
`https://192.168.1.100:7842`).

### Response Headers

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS
Access-Control-Allow-Headers: Authorization, Content-Type, X-Request-Id
Access-Control-Max-Age: 86400
Access-Control-Expose-Headers: X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, X-Request-Id, API-Version, Retry-After
```

**Why `Allow-Origin: *`:** The server is only reachable on the local network or
through an authenticated tunnel. The PIN + token mechanism is the real access
control -- CORS origin restrictions add no security value here and would break
tunnel scenarios where the origin is unpredictable.

**Credentials:** `Access-Control-Allow-Credentials` is NOT set. Tokens are
passed via `Authorization` header, not cookies. This avoids CSRF entirely.

### Preflight

All `OPTIONS` requests return 204 No Content with the CORS headers above. No
authentication required for preflight.

---

## 7. REST Endpoints

### 7.1 Health Check

```
GET /api/v1/health
```

**Authentication:** None

**Response 200:**

```json
{
  "status": "healthy",
  "version": "0.0.8",
  "api_version": "1.0.0",
  "uptime_seconds": 3600,
  "connected_devices": 1,
  "max_devices": 10,
  "tls": "self-signed"
}
```

**Purpose:** Monitoring, readiness probes, client connectivity check before
showing the login screen. Also used by Bonjour discovery clients to verify the
service is alive.

---

### 7.2 Authentication

#### 7.2.1 Exchange PIN for Token

```
POST /api/v1/auth/token
```

**Authentication:** None

**Request Body:**

```json
{
  "pin": "482917"
}
```

| Field  | Type   | Required | Validation                      |
|--------|--------|----------|---------------------------------|
| `pin`  | string | Yes      | Exactly 6 digits (`/^\d{6}$/`) |

**Response 200 (success):**

```json
{
  "token": "vs1.dC1hYmMxMjM.MTcxNTY0NDgwMA.aGVsbG8gd29ybGQ",
  "expires_at": "2026-05-15T12:00:00Z",
  "device_id": "d-abc123"
}
```

**Response 401 (wrong PIN):**

```json
{
  "error": {
    "code": "AUTH_PIN_INVALID",
    "message": "Incorrect PIN. 2 attempts remaining.",
    "details": {
      "attempts_remaining": 2,
      "lockout_duration_seconds": 300
    }
  }
}
```

**Response 429 (locked out):**

```json
{
  "error": {
    "code": "AUTH_LOCKOUT",
    "message": "Too many failed attempts. Try again in 5 minutes.",
    "details": {
      "retry_after_seconds": 300
    }
  }
}
```

Headers: `Retry-After: 300`

**Response 503 (max devices reached):**

```json
{
  "error": {
    "code": "MAX_DEVICES_REACHED",
    "message": "Maximum of 10 devices already connected. Disconnect a device first.",
    "details": {
      "max_devices": 10,
      "connected_devices": [
        {"device_id": "d-abc123", "ip": "192.168.1.42", "connected_since": "2026-05-14T10:00:00Z"}
      ]
    }
  }
}
```

**Side effects:**
- PIN is consumed and regenerated on success
- Failed attempt counter incremented on failure
- New PIN generated on lockout

---

#### 7.2.2 Validate Token

```
GET /api/v1/auth/validate
```

**Authentication:** Required

**Response 200:**

```json
{
  "valid": true,
  "device_id": "d-abc123",
  "expires_at": "2026-05-15T12:00:00Z"
}
```

**Response 401:** (any auth error code)

**Purpose:** Client checks token validity on page load / app resume without
making a full API call.

---

### 7.3 Projects & Sessions

#### 7.3.1 List Projects with Sessions

```
GET /api/v1/projects
```

**Authentication:** Required

**Response 200:**

```json
{
  "projects": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "my-app",
      "path": "/Users/dev/projects/my-app",
      "color": "#FF6B35",
      "is_active": true,
      "git": {
        "branch": "main",
        "ahead": 2,
        "behind": 0
      },
      "sessions": [
        {
          "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
          "title": "-zsh",
          "state": "has_activity",
          "is_agent": false,
          "has_remote_attachment": false
        },
        {
          "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
          "title": "Claude Code",
          "state": "running",
          "is_agent": true,
          "has_remote_attachment": true,
          "attached_device_id": "d-xyz789"
        }
      ]
    }
  ],
  "active_project_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Notes:**
- `state` enum: `"running"` | `"has_activity"` | `"exited"`
- `has_remote_attachment`: whether another remote client is watching this session
- `git` is null if the project directory is not a git repository

---

#### 7.3.2 Get Single Project

```
GET /api/v1/projects/{projectId}
```

**Authentication:** Required

**Response 200:** Same structure as a single item from the `projects` array above.

**Response 404:** `PROJECT_NOT_FOUND`

---

#### 7.3.3 Get Session Detail

```
GET /api/v1/projects/{projectId}/sessions/{sessionId}
```

**Authentication:** Required

**Response 200:**

```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "-zsh",
  "state": "running",
  "is_agent": false,
  "has_remote_attachment": false,
  "terminal": {
    "cols": 120,
    "rows": 36,
    "scrollback_lines": 847
  }
}
```

---

#### 7.3.4 Get Session Scrollback

```
GET /api/v1/projects/{projectId}/sessions/{sessionId}/scrollback
```

**Authentication:** Required

**Query Parameters:**

| Param   | Type | Default | Description                          |
|---------|------|---------|--------------------------------------|
| `lines` | int  | 500     | Max lines to return (1-10000)        |
| `offset`| int  | 0       | Lines to skip from the bottom        |

**Response 200:**

```json
{
  "content": "$ git status\nOn branch main\n...",
  "total_lines": 847,
  "returned_lines": 500
}
```

**Purpose:** Initial terminal buffer load before WebSocket attachment. The client
fetches scrollback first, renders it in xterm.js, then opens the WebSocket for
live streaming.

---

### 7.4 Device Management

#### 7.4.1 List Connected Devices

```
GET /api/v1/devices
```

**Authentication:** Required

**Response 200:**

```json
{
  "devices": [
    {
      "device_id": "d-abc123",
      "ip": "192.168.1.42",
      "user_agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X)",
      "connected_since": "2026-05-14T10:00:00Z",
      "last_activity": "2026-05-14T11:30:00Z",
      "attached_sessions": ["7c9e6679-7425-40de-944b-e07fc1f90ae7"],
      "is_self": true
    }
  ],
  "max_devices": 10
}
```

---

#### 7.4.2 Disconnect a Device

```
DELETE /api/v1/devices/{deviceId}
```

**Authentication:** Required

**Response 204:** No Content

**Response 404:** Device not found

**Side effects:**
- Device's token is added to the revocation set
- All WebSocket connections from that device are closed with code `4001`
- `device_disconnected` event broadcast to remaining connections

**Note:** A device can only disconnect **itself** (the `deviceId` must match the
requesting device's ID). Returns 403 `FORBIDDEN` if attempting to disconnect
another device. The Settings pane on macOS disconnects devices directly via
the `RemoteControlServer.disconnect()` method (not through this API).

---

### 7.5 Server Status

```
GET /api/v1/status
```

**Authentication:** Required

**Response 200:**

```json
{
  "server": {
    "version": "0.0.8",
    "api_version": "1.0.0",
    "uptime_seconds": 7200,
    "port": 7842,
    "tls": "self-signed",
    "bonjour_published": true
  },
  "connections": {
    "connected_devices": 2,
    "max_devices": 10,
    "active_websockets": 3
  },
  "theme": {
    "appearance": "dark",
    "terminal_colors": {
      "foreground": "#D4D4D4",
      "background": "#1E1E1E",
      "cursor": "#AEAFAD",
      "selection": "#264F78",
      "ansi": [
        "#000000", "#CD3131", "#0DBC79", "#E5E510",
        "#2472C8", "#BC3FBC", "#11A8CD", "#E5E5E5",
        "#666666", "#F14C4C", "#23D18B", "#F5F543",
        "#3B8EEA", "#D670D6", "#29B8DB", "#FFFFFF"
      ]
    }
  }
}
```

**Purpose:** The `theme` section is used by the Web UI to configure xterm.js
colors on initial load and after reconnection.

---

## 8. WebSocket Protocol

### Connection

```
WSS /api/v1/terminal/{sessionId}
```

**Subprotocol:** `Sec-WebSocket-Protocol: vibestudio.v1`

**Authentication:** The WebSocket upgrade is unauthenticated. The client must
send an `auth` message as the **first frame** after connection. The server
rejects connections that do not authenticate within 10 seconds (close code 4000).

```json
// Client -> Server (first frame)
{ "type": "auth", "token": "<bearer-token>" }

// Server -> Client (on success)
{ "type": "auth_ok", "session_id": "<uuid>" }

// Server -> Client (on failure)
{ "type": "auth_error", "message": "Invalid or expired token" }
// followed by close code 4000
```

**Why first-frame auth (not query param):** Tokens in URL query strings leak
into server logs, browser history, and Referer headers. First-frame auth keeps
the token out of URLs entirely.

**Connection limits:**
- 1 WebSocket per session per device (attempting a second returns 409)
- 3 WebSocket connections total per device
- Connection is rejected with HTTP 401/403/409 during the upgrade handshake

### Frame Format

All frames are **text** (JSON) except for terminal output which uses **binary**
frames for efficiency. This hybrid approach minimizes encoding overhead for the
high-volume terminal output path while keeping control messages human-readable.

### Message Types: Client -> Server

#### 8.1 `input` -- Terminal Input

**Frame type:** Text

```json
{
  "type": "input",
  "data": "ls -la\n"
}
```

| Field  | Type   | Description                                |
|--------|--------|--------------------------------------------|
| `data` | string | UTF-8 text to send to the PTY. May contain escape sequences (e.g. `\x1b` for Esc, `\t` for Tab). |

**Rate limit:** 120 messages/minute. Excess messages are silently dropped with a
`rate_limited` warning sent back.

---

#### 8.2 `resize` -- Terminal Resize

**Frame type:** Text

```json
{
  "type": "resize",
  "cols": 80,
  "rows": 24
}
```

| Field  | Type | Validation       | Description          |
|--------|------|------------------|----------------------|
| `cols` | int  | 10-500           | Terminal columns     |
| `rows` | int  | 4-200            | Terminal rows        |

**Behavior:** The server calls `TerminalService.resize(session:to:)` and
forwards the resize to the PTY via `TIOCSWINSZ`. This affects the local
terminal view as well -- the remote client's viewport becomes authoritative.

**Important:** When the remote client disconnects, the terminal size reverts to
the local view's dimensions.

---

#### 8.3 `ping` -- Keepalive Ping

**Frame type:** Text

```json
{
  "type": "ping",
  "ts": 1715644800000
}
```

**Response:** Server sends `pong` with the same `ts` for RTT measurement.

**Interval:** Client should send a ping every 30 seconds. Server disconnects
after 60 seconds without any message (ping or input).

---

#### 8.4 `detach` -- Graceful Detach

**Frame type:** Text

```json
{
  "type": "detach"
}
```

**Behavior:** The server cleanly detaches the remote client from the session
without closing the WebSocket connection. The client can then attach to a
different session by opening a new WebSocket.

---

### Message Types: Server -> Client

#### 8.5 `output` -- Terminal Output

**Frame type:** Binary

The binary frame contains raw terminal output bytes (UTF-8 with ANSI escape
sequences). No JSON wrapping -- xterm.js can consume the ArrayBuffer directly.

**Rationale:** Terminal output is the highest-volume message type. JSON-encoding
ANSI escape sequences would roughly double the payload size and require
encode/decode overhead on both ends. Binary frames eliminate this.

---

#### 8.6 `session_state` -- Session State Change

**Frame type:** Text

```json
{
  "type": "session_state",
  "session_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "state": "exited",
  "exit_code": 0
}
```

| Field       | Type    | Description                                    |
|-------------|---------|------------------------------------------------|
| `state`     | string  | `"running"` / `"has_activity"` / `"exited"`   |
| `exit_code` | int?    | Present only when `state` is `"exited"`        |

---

#### 8.7 `pong` -- Keepalive Response

**Frame type:** Text

```json
{
  "type": "pong",
  "ts": 1715644800000,
  "server_ts": 1715644800042
}
```

---

#### 8.8 `theme_changed` -- Theme Update

**Frame type:** Text

```json
{
  "type": "theme_changed",
  "appearance": "light",
  "terminal_colors": {
    "foreground": "#383A42",
    "background": "#FAFAFA",
    "cursor": "#526FFF",
    "selection": "#E5E5E6",
    "ansi": ["...16 colors..."]
  }
}
```

**Purpose:** Pushed when the user changes the VibeStudio theme on macOS. The
Web UI updates xterm.js colors in real time.

---

#### 8.9 `sessions_changed` -- Session List Update

**Frame type:** Text

```json
{
  "type": "sessions_changed",
  "project_id": "550e8400-e29b-41d4-a716-446655440000",
  "sessions": [
    {
      "id": "7c9e6679-...",
      "title": "-zsh",
      "state": "running",
      "is_agent": false
    }
  ]
}
```

**Purpose:** Pushed when sessions are created, destroyed, or change state in any
project. Allows the session picker in the Web UI to update without polling.

---

#### 8.10 `device_disconnected` -- Peer Disconnected

**Frame type:** Text

```json
{
  "type": "device_disconnected",
  "device_id": "d-xyz789",
  "reason": "kicked_by_host"
}
```

---

#### 8.11 `error` -- Protocol Error

**Frame type:** Text

```json
{
  "type": "error",
  "code": "SESSION_NOT_FOUND",
  "message": "The terminal session no longer exists.",
  "fatal": true
}
```

| Field   | Type | Description                                              |
|---------|------|----------------------------------------------------------|
| `fatal` | bool | If true, the server will close the WebSocket after this. |

---

#### 8.12 `rate_limited` -- Rate Limit Warning

**Frame type:** Text

```json
{
  "type": "rate_limited",
  "message": "Input rate limit exceeded. Messages are being dropped.",
  "retry_after_ms": 500
}
```

---

### WebSocket Close Codes

| Code  | Meaning                    | Who initiates | Client action              |
|-------|----------------------------|---------------|----------------------------|
| 1000  | Normal closure             | Either        | Clean disconnect           |
| 1001  | Going away (server shutdown)| Server       | Show "server stopped"      |
| 4000  | Authentication failed      | Server        | Redirect to PIN entry      |
| 4001  | Device revoked             | Server        | Show "disconnected by host"|
| 4002  | Session ended              | Server        | Show exit code, offer pick |
| 4003  | Replaced by new connection | Server        | Show "connected elsewhere" |
| 4004  | Idle timeout               | Server        | Auto-reconnect             |
| 4005  | Rate limit exceeded        | Server        | Back off and retry         |

---

### Reconnection Protocol

The Web UI implements exponential backoff reconnection:

```
Attempt 1: immediate
Attempt 2: 1s delay
Attempt 3: 2s delay
Attempt 4: 4s delay
Attempt 5: 8s delay
...max: 30s delay
```

**On reconnect:**
1. Validate token: `GET /api/v1/auth/validate`
2. If token expired: redirect to PIN entry
3. If valid: fetch scrollback `GET /api/v1/projects/{pid}/sessions/{sid}/scrollback`
4. Re-render terminal buffer from scrollback
5. Open new WebSocket connection

**Idempotency:** Reconnection is safe because:
- The server does not buffer output for disconnected clients
- The client fetches full scrollback on reconnect to restore visual state
- No messages are guaranteed to be delivered exactly once

---

## 9. Data Models

### REST API Models (JSON)

#### Project (response)

```typescript
interface Project {
  id: string;               // UUID
  name: string;
  path: string;             // absolute filesystem path
  color: string | null;     // hex color e.g. "#FF6B35"
  is_active: boolean;
  git: GitInfo | null;
  sessions: SessionSummary[];
}

interface GitInfo {
  branch: string;
  ahead: number;
  behind: number;
}

interface SessionSummary {
  id: string;               // UUID
  title: string;
  state: "running" | "has_activity" | "exited";
  is_agent: boolean;
  has_remote_attachment: boolean;
  attached_device_id?: string;
}
```

#### Device (response)

```typescript
interface Device {
  device_id: string;        // "d-" prefixed
  ip: string;
  user_agent: string;
  connected_since: string;  // ISO 8601
  last_activity: string;    // ISO 8601
  attached_sessions: string[];  // session UUIDs
  is_self: boolean;
}
```

#### TerminalColors (embedded)

```typescript
interface TerminalColors {
  foreground: string;       // hex
  background: string;       // hex
  cursor: string;           // hex
  selection: string;        // hex
  ansi: string[];           // 16 ANSI colors, hex
}
```

### WebSocket Message Envelope

```typescript
// Client -> Server
type ClientMessage =
  | { type: "input"; data: string }
  | { type: "resize"; cols: number; rows: number }
  | { type: "ping"; ts: number }
  | { type: "detach" };

// Server -> Client (text frames only; output is binary)
type ServerMessage =
  | { type: "session_state"; session_id: string; state: string; exit_code?: number }
  | { type: "pong"; ts: number; server_ts: number }
  | { type: "theme_changed"; appearance: string; terminal_colors: TerminalColors }
  | { type: "sessions_changed"; project_id: string; sessions: SessionSummary[] }
  | { type: "device_disconnected"; device_id: string; reason: string }
  | { type: "error"; code: string; message: string; fatal: boolean }
  | { type: "rate_limited"; message: string; retry_after_ms: number };
```

### Mapping to Swift Domain Models

| REST/WS field       | Swift model             | Notes                              |
|----------------------|-------------------------|------------------------------------|
| `Project.id`         | `Project.id: UUID`      | Direct mapping                     |
| `Project.name`       | `Project.name: String`  | Direct mapping                     |
| `Project.path`       | `Project.path: URL`     | Serialized as string               |
| `Project.color`      | `Project.color: String?`| Direct mapping                     |
| `Project.is_active`  | `ProjectManaging.activeProjectId == project.id` | Computed   |
| `GitInfo.branch`     | `GitStatus.branch`      | Direct mapping                     |
| `GitInfo.ahead`      | `GitStatus.aheadCount`  | Direct mapping                     |
| `GitInfo.behind`     | `GitStatus.behindCount` | Direct mapping                     |
| `SessionSummary.id`  | `TerminalSession.id`    | Direct mapping                     |
| `SessionSummary.state` | `TerminalSession.state` | Enum serialized as snake_case string |
| `SessionSummary.is_agent` | `TerminalSession.isAgentSession` | Direct mapping          |

---

## 10. Security Considerations

### Threat Model

The server runs on a developer's local machine and is reachable on the LAN or
through an authenticated tunnel. The primary threats are:

1. **Unauthorized LAN access** -- mitigated by PIN + token + TLS
2. **Token theft** -- mitigated by IP binding and 24h expiry
3. **Brute-force PIN** -- mitigated by rate limiting (3 attempts / 5 min lockout)
4. **Session hijacking via WebSocket** -- mitigated by token validation on upgrade
5. **Cross-site WebSocket hijacking** -- mitigated by first-frame token auth (not cookie-based)
6. **Denial of service** -- mitigated by connection limits (3 devices, rate limiting)

### What This API Does NOT Expose

- No filesystem access (read or write)
- No git operations (commit, push, pull)
- No project management (create, delete, rename)
- No settings modification
- Only terminal I/O relay -- the remote client can type exactly what the user
  could type at the physical keyboard

### TLS Requirements

- All HTTP and WebSocket traffic MUST use TLS (HTTPS / WSS)
- Plain HTTP on port 7842 returns 301 redirect to HTTPS (if the client
  accidentally uses `http://`)
- Certificate: self-signed, generated via `Security.framework`
  (`SecKeyCreateRandomKey` + `SecCertificateCreateWithData`)
- Stored: `~/Library/Application Support/VibeStudio/remote-tls/`
- Regenerated if: certificate file is missing, corrupted, or expired

### Input Sanitization

- PIN: validated as exactly 6 ASCII digits before comparison
- Session/project IDs: validated as UUID format before database lookup
- WebSocket `input.data`: passed through to PTY as-is (the PTY is the
  sanitization boundary -- it processes escape sequences natively)
- `resize` cols/rows: clamped to valid ranges (cols: 10-500, rows: 4-200)

---

## Appendix A: Static File Serving

The following paths serve the bundled Web UI and are NOT part of the versioned
API:

| Path              | Content-Type   | Description                 |
|-------------------|----------------|-----------------------------|
| `GET /`           | `text/html`    | Login / main SPA page       |
| `GET /app.js`     | `text/javascript` | Bundled JS (xterm.js + app) |
| `GET /app.css`    | `text/css`     | Styles                      |
| `GET /favicon.ico`| `image/x-icon` | App icon                    |

Static files are served with:
- `Cache-Control: public, max-age=31536000, immutable` (content-hashed filenames)
- No authentication required (the HTML is the login page)

---

## Appendix B: Full Endpoint Summary

| Method  | Path                                                    | Auth  | Description                    |
|---------|---------------------------------------------------------|-------|--------------------------------|
| GET     | `/api/v1/health`                                        | No    | Health/readiness check         |
| POST    | `/api/v1/auth/token`                                    | No    | PIN -> token exchange          |
| GET     | `/api/v1/auth/validate`                                 | Yes   | Token validity check           |
| GET     | `/api/v1/projects`                                      | Yes   | List projects + sessions       |
| GET     | `/api/v1/projects/{projectId}`                          | Yes   | Single project detail          |
| GET     | `/api/v1/projects/{projectId}/sessions/{sessionId}`     | Yes   | Session detail                 |
| GET     | `/api/v1/projects/{projectId}/sessions/{sessionId}/scrollback` | Yes | Terminal scrollback buffer |
| GET     | `/api/v1/devices`                                       | Yes   | Connected devices list         |
| DELETE  | `/api/v1/devices/{deviceId}`                            | Yes   | Disconnect a device            |
| GET     | `/api/v1/status`                                        | Yes   | Server status + theme          |
| GET     | `/api/v1/projects/recent`                               | Yes   | Recently opened projects       |
| POST    | `/api/v1/projects/open`                                 | Yes   | Open project by path           |
| POST    | `/api/v1/projects/{projectId}/activate`                 | Yes   | Activate (switch to) project   |
| POST    | `/api/v1/assistant/start`                               | Yes   | Start AI assistant in session  |
| POST    | `/api/v1/assistant/stop`                                | Yes   | Stop AI assistant in session   |
| WSS     | `/api/v1/terminal/{sessionId}`                          | First-frame | Bidirectional terminal I/O |
| OPTIONS | `/api/v1/*`                                             | No    | CORS preflight                 |

---

## Appendix C: Idempotency Guarantees

| Method  | Idempotent | Safe | Notes                                        |
|---------|------------|------|----------------------------------------------|
| GET     | Yes        | Yes  | All GET endpoints are read-only              |
| POST    | No         | No   | `auth/token` has side effects (PIN consumed) |
| DELETE  | Yes        | No   | Deleting already-disconnected device -> 204  |
| WSS     | N/A        | N/A  | Stateful bidirectional channel               |

---

## Appendix D: Future Considerations (v2)

These are explicitly OUT OF SCOPE for v1 but documented for forward planning:

1. **File browsing endpoint** (`GET /api/v2/projects/{id}/files`) -- read-only
   file tree for mobile review
2. **Git status endpoint** (`GET /api/v2/projects/{id}/git`) -- real-time git
   info without terminal
3. **Multi-session view** -- attach to multiple sessions simultaneously via
   multiplexed WebSocket
4. **Push notifications** -- notify mobile device when a long-running process
   completes
5. **OAuth2 PKCE flow** -- for public-facing tunnel scenarios where PIN is
   insufficient
6. **Token refresh** -- long-lived sessions with rotating refresh tokens
7. **Audit log endpoint** -- `GET /api/v2/audit` for reviewing all remote
   access history
