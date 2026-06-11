/**
 * VibeStudio Remote Control — Web Client
 *
 * Vanilla JS SPA. No frameworks, no bundler.
 * Target: iOS Safari 15+, Chrome for Android.
 *
 * Architecture:
 *   PinInput           — 6-digit PIN entry with auto-advance and paste
 *   VibeStudioClient   — REST API wrapper (auth, projects, scrollback)
 *   ReconnectingWS     — WebSocket with exponential backoff
 *   TerminalManager    — xterm.js lifecycle, font sizing, theme
 *   SpecialKeysBar     — touch-optimised special key buttons
 *   ThemeManager       — dynamic CSS custom property updates
 *   App                — orchestrator (init, screen transitions, state)
 */

'use strict';

// ---------------------------------------------------------------------------
// 1. PinInput
// ---------------------------------------------------------------------------

class PinInput {
  /**
   * @param {HTMLElement} container  — element containing .pin-digit inputs
   * @param {HTMLElement} errorEl    — element for error messages
   * @param {HTMLElement} blockedEl  — lockout overlay
   * @param {HTMLElement} countdownEl — countdown timer span
   */
  constructor(container, errorEl, blockedEl, countdownEl) {
    /** @type {HTMLInputElement[]} */
    this.digits = Array.from(container.querySelectorAll('.pin-digit'));
    this.errorEl = errorEl;
    this.blockedEl = blockedEl;
    this.countdownEl = countdownEl;

    /** @type {((pin: string) => void) | null} */
    this.onComplete = null;

    this._countdownTimer = null;
    this._shakeTimeout = null;

    this._bindEvents();
  }

  // --- Public API ---

  /** Reset all fields and focus the first one. */
  reset() {
    this.digits.forEach(function (d) {
      d.value = '';
      d.disabled = false;
      d.classList.remove('pin-digit--error');
    });
    this.errorEl.textContent = '';
    this.digits[0].focus();
  }

  /** Show error state: shake animation + message, auto-clear. */
  setError(message) {
    this.errorEl.textContent = message || '';
    this.digits.forEach(function (d) {
      d.classList.add('pin-digit--error');
    });

    clearTimeout(this._shakeTimeout);
    this._shakeTimeout = setTimeout(function () {
      this.digits.forEach(function (d) {
        d.value = '';
        d.classList.remove('pin-digit--error');
      });
      this.digits[0].focus();
    }.bind(this), 600);
  }

  /** Enter lockout state with countdown. */
  setBlocked(seconds) {
    this.digits.forEach(function (d) {
      d.disabled = true;
    });
    this.blockedEl.hidden = false;
    this._startCountdown(seconds);
  }

  // --- Private ---

  _bindEvents() {
    const self = this;

    this.digits.forEach(function (digit, idx) {
      digit.addEventListener('input', function (e) {
        self._onInput(e, idx);
      });
      digit.addEventListener('keydown', function (e) {
        self._onKeyDown(e, idx);
      });
      digit.addEventListener('paste', function (e) {
        self._onPaste(e);
      });
      // Prevent non-digit characters on mobile
      digit.addEventListener('beforeinput', function (e) {
        if (e.data && !/^\d$/.test(e.data) && e.inputType !== 'insertFromPaste') {
          e.preventDefault();
        }
      });
    });
  }

  _onInput(_e, idx) {
    const value = this.digits[idx].value;
    // Keep only last digit (handles some Android IME quirks)
    if (value.length > 1) {
      this.digits[idx].value = value.slice(-1);
    }
    // Non-digit guard
    if (!/^\d$/.test(this.digits[idx].value)) {
      this.digits[idx].value = '';
      return;
    }
    // Auto-advance
    if (idx < 5) {
      this.digits[idx + 1].focus();
    } else {
      this._tryComplete();
    }
  }

  _onKeyDown(e, idx) {
    if (e.key === 'Backspace') {
      if (this.digits[idx].value === '' && idx > 0) {
        this.digits[idx - 1].value = '';
        this.digits[idx - 1].focus();
      } else {
        this.digits[idx].value = '';
      }
      e.preventDefault();
    } else if (e.key === 'ArrowLeft' && idx > 0) {
      this.digits[idx - 1].focus();
    } else if (e.key === 'ArrowRight' && idx < 5) {
      this.digits[idx + 1].focus();
    }
  }

  _onPaste(e) {
    e.preventDefault();
    const text = (e.clipboardData || window.clipboardData).getData('text').trim();
    const digits = text.replace(/\D/g, '').slice(0, 6);
    if (digits.length === 0) return;

    for (let i = 0; i < digits.length && i < 6; i++) {
      this.digits[i].value = digits[i];
    }
    if (digits.length >= 6) {
      this.digits[5].focus();
      this._tryComplete();
    } else {
      this.digits[Math.min(digits.length, 5)].focus();
    }
  }

  _tryComplete() {
    const pin = this.digits.map(function (d) { return d.value; }).join('');
    if (pin.length === 6 && /^\d{6}$/.test(pin) && this.onComplete) {
      this.onComplete(pin);
    }
  }

  _startCountdown(totalSeconds) {
    const self = this;
    let remaining = totalSeconds;
    self._updateCountdownDisplay(remaining);

    clearInterval(self._countdownTimer);
    self._countdownTimer = setInterval(function () {
      remaining--;
      if (remaining <= 0) {
        clearInterval(self._countdownTimer);
        self.blockedEl.hidden = true;
        self.digits.forEach(function (d) { d.disabled = false; });
        self.reset();
      } else {
        self._updateCountdownDisplay(remaining);
      }
    }, 1000);
  }

  _updateCountdownDisplay(seconds) {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    this.countdownEl.textContent = m + ':' + (s < 10 ? '0' : '') + s;
  }
}

// ---------------------------------------------------------------------------
// 2. VibeStudioClient (REST API)
// ---------------------------------------------------------------------------

class VibeStudioClient {
  constructor() {
    this._baseUrl = '';  // same origin
    // Migrate any legacy sessionStorage tokens to localStorage so users
    // already authenticated in the current tab don't get bounced to PIN.
    this._migrateLegacyStorage();
  }

  /** @private */
  _migrateLegacyStorage() {
    const keys = ['vs_token', 'vs_device_id', 'vs_token_expires'];
    keys.forEach((k) => {
      const v = sessionStorage.getItem(k);
      if (v && !localStorage.getItem(k)) {
        localStorage.setItem(k, v);
      }
      sessionStorage.removeItem(k);
    });
  }

  /** @returns {boolean} */
  hasToken() {
    return !!localStorage.getItem('vs_token');
  }

  /** @returns {string|null} */
  getToken() {
    return localStorage.getItem('vs_token');
  }

  /** @returns {string|null} */
  getDeviceId() {
    return localStorage.getItem('vs_device_id');
  }

  /** @returns {Date|null} */
  getTokenExpiry() {
    const v = localStorage.getItem('vs_token_expires');
    if (!v) return null;
    const t = new Date(v);
    return isNaN(t.getTime()) ? null : t;
  }

  /** @returns {boolean} */
  isTokenExpired() {
    const exp = this.getTokenExpiry();
    return exp ? Date.now() >= exp.getTime() : false;
  }

  /** Seconds until expiry. -Infinity if no token. */
  secondsUntilExpiry() {
    const exp = this.getTokenExpiry();
    return exp ? Math.floor((exp.getTime() - Date.now()) / 1000) : -Infinity;
  }

  clearToken() {
    localStorage.removeItem('vs_token');
    localStorage.removeItem('vs_device_id');
    localStorage.removeItem('vs_token_expires');
  }

  /**
   * Exchange PIN for auth token.
   * @param {string} pin — 6-digit PIN
   * @returns {Promise<{ok: boolean, token?: string, error?: object}>}
   */
  async authenticate(pin) {
    const resp = await fetch(this._baseUrl + '/api/v1/auth/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin: pin })
    });

    const data = await resp.json();

    if (resp.ok) {
      this._storeAuth(data);
      return { ok: true, token: data.token };
    }

    return { ok: false, status: resp.status, error: data.error || data };
  }

  /** @private */
  _storeAuth(data) {
    if (data.token) localStorage.setItem('vs_token', data.token);
    if (data.device_id) localStorage.setItem('vs_device_id', data.device_id);
    if (data.expires_at) localStorage.setItem('vs_token_expires', data.expires_at);
  }

  /**
   * Refresh the current bearer token in place. Returns new expiry on success.
   * On failure (invalid/expired token, network error) returns null without
   * clearing storage — caller decides whether to drop the user to PIN.
   * @returns {Promise<{token: string, expires_at: string}|null>}
   */
  async refreshToken() {
    if (!this.hasToken()) return null;
    try {
      const resp = await fetch(this._baseUrl + '/api/v1/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + this.getToken()
        }
      });
      if (!resp.ok) return null;
      const data = await resp.json();
      this._storeAuth(data);
      return data;
    } catch (_e) {
      return null;
    }
  }

  /**
   * Check if current token is valid.
   * @returns {Promise<boolean>}
   */
  async validateToken() {
    if (!this.hasToken()) return false;

    try {
      const resp = await this._fetch('/api/v1/auth/validate');
      if (!resp.ok) {
        if (resp.status === 401) this.clearToken();
        return false;
      }
      return true;
    } catch (_e) {
      return false;
    }
  }

  /**
   * Fetch list of projects with sessions.
   * @returns {Promise<{projects: Array, active_project_id: string}>}
   */
  async getProjects() {
    const resp = await this._fetch('/api/v1/projects');
    if (!resp.ok) throw new ApiError(resp.status, await resp.json());
    return resp.json();
  }

  /**
   * Fetch server status (includes theme).
   * @returns {Promise<object>}
   */
  async getStatus() {
    const resp = await this._fetch('/api/v1/status');
    if (!resp.ok) throw new ApiError(resp.status, await resp.json());
    return resp.json();
  }

  /**
   * Fetch terminal scrollback.
   * @param {string} projectId
   * @param {string} sessionId
   * @param {number} [lines=500]
   * @returns {Promise<{content: string, total_lines: number}>}
   */
  async getScrollback(projectId, sessionId, lines) {
    let url = '/api/v1/projects/' + projectId + '/sessions/' + sessionId + '/scrollback';
    if (lines) url += '?lines=' + lines;
    const resp = await this._fetch(url);
    if (!resp.ok) throw new ApiError(resp.status, await resp.json());
    return resp.json();
  }

  /**
   * Activate a project.
   * @param {string} projectId
   * @returns {Promise<object>}
   */
  async activateProject(projectId) {
    const resp = await this._fetchJSON('POST', '/api/v1/projects/' + projectId + '/activate');
    return resp;
  }

  /**
   * Fetch recently opened projects (not currently in sidebar).
   * @returns {Promise<{projects: Array}>}
   */
  async getRecentProjects() {
    const resp = await this._fetch('/api/v1/projects/recent');
    if (!resp.ok) throw new ApiError(resp.status, await resp.json());
    return resp.json();
  }

  /**
   * Open a project by filesystem path (from recent history).
   * @param {string} path — absolute path on Mac
   * @returns {Promise<{ok: boolean, project_id: string}>}
   */
  async openProject(path) {
    return this._fetchJSON('POST', '/api/v1/projects/open', { path: path });
  }

  /**
   * Start an AI assistant for the active project.
   * @param {string} [assistant='claude']
   * @returns {Promise<object>}
   */
  async startAssistant(assistant) {
    return this._fetchJSON('POST', '/api/v1/assistant/start', { assistant: assistant || 'claude' });
  }

  /**
   * Stop the running AI assistant.
   * @returns {Promise<object>}
   */
  async stopAssistant() {
    return this._fetchJSON('POST', '/api/v1/assistant/stop');
  }

  /**
   * Upload an image to the host. Returns the local filesystem path on success,
   * or null on failure. The host writes the file to a temp directory and the
   * path can then be pasted into the active terminal so Claude/etc. picks it up.
   *
   * @param {Blob} blob — raw image data (image/png, image/jpeg, etc.)
   * @returns {Promise<string|null>}
   */
  async uploadImage(blob) {
    const token = this.getToken();
    if (!token) return null;
    const ct = blob.type || 'application/octet-stream';
    try {
      const resp = await fetch(this._baseUrl + '/api/v1/uploads/image', {
        method: 'POST',
        headers: {
          'Authorization': 'Bearer ' + token,
          'Content-Type': ct
        },
        body: blob
      });
      if (!resp.ok) return null;
      const data = await resp.json();
      return data.path || null;
    } catch (_e) {
      return null;
    }
  }

  /** @private */
  async _fetch(path) {
    const token = this.getToken();
    const headers = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;
    return fetch(this._baseUrl + path, { headers: headers });
  }

  /** @private */
  async _fetchJSON(method, path, body) {
    const token = this.getToken();
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const opts = { method: method, headers: headers };
    if (body) opts.body = JSON.stringify(body);
    const resp = await fetch(this._baseUrl + path, opts);
    return resp.json();
  }
}

class ApiError extends Error {
  constructor(status, body) {
    super((body && body.error && body.error.message) || 'API Error ' + status);
    this.status = status;
    this.body = body;
  }
}

// ---------------------------------------------------------------------------
// 3. ReconnectingWebSocket
// ---------------------------------------------------------------------------

class ReconnectingWS {
  /**
   * @param {string} url
   * @param {object} [opts]
   * @param {string[]} [opts.protocols]
   * @param {function} [opts.onOpen]
   * @param {function} [opts.onMessage]
   * @param {function} [opts.onClose]
   * @param {function} [opts.onStatusChange]
   */
  constructor(url, opts) {
    this.url = url;
    this.protocols = (opts && opts.protocols) || [];
    this.onOpen = (opts && opts.onOpen) || null;
    this.onMessage = (opts && opts.onMessage) || null;
    this.onClose = (opts && opts.onClose) || null;
    this.onStatusChange = (opts && opts.onStatusChange) || null;

    /** @type {WebSocket|null} */
    this.ws = null;
    this._attempt = 0;
    this._closed = false;
    this._pingInterval = null;
    this._reconnectTimeout = null;

    /** Close codes that must NOT trigger auto-reconnect. */
    this._fatalCodes = new Set([4000, 4001, 4002]);
  }

  /** Initiate connection. */
  connect() {
    this._closed = false;
    this._attempt = 0;
    this._doConnect();
  }

  /** Send data through the WebSocket. */
  send(data) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(data);
    }
  }

  /** Gracefully close without reconnect. */
  close() {
    this._closed = true;
    clearTimeout(this._reconnectTimeout);
    clearInterval(this._pingInterval);
    if (this.ws) {
      this.ws.close(1000);
      this.ws = null;
    }
  }

  /** @private */
  _doConnect() {
    if (this._closed) return;

    const self = this;
    if (this.onStatusChange) this.onStatusChange('connecting');

    try {
      this.ws = this.protocols.length
        ? new WebSocket(this.url, this.protocols)
        : new WebSocket(this.url);
      this.ws.binaryType = 'arraybuffer';
    } catch (e) {
      this._scheduleReconnect();
      return;
    }

    this.ws.onopen = function () {
      self._attempt = 0;
      self._startPing();
      if (self.onStatusChange) self.onStatusChange('connected');
      if (self.onOpen) self.onOpen();
    };

    this.ws.onmessage = function (evt) {
      if (self.onMessage) self.onMessage(evt);
    };

    this.ws.onclose = function (evt) {
      clearInterval(self._pingInterval);
      if (self.onStatusChange) self.onStatusChange('disconnected');

      if (self.onClose) self.onClose(evt.code, evt.reason);

      if (!self._closed && !self._fatalCodes.has(evt.code)) {
        self._scheduleReconnect();
      }
    };

    this.ws.onerror = function (e) {
      // onclose will fire after onerror — reconnect handled there
    };
  }

  /** @private */
  _scheduleReconnect() {
    if (this._closed) return;
    const delay = this._attempt === 0 ? 0 : Math.min(1000 * Math.pow(2, this._attempt - 1), 30000);
    this._attempt++;

    const self = this;
    clearTimeout(this._reconnectTimeout);
    this._reconnectTimeout = setTimeout(function () {
      self._doConnect();
    }, delay);
  }

  /** @private — ping every 30s */
  _startPing() {
    const self = this;
    clearInterval(this._pingInterval);
    this._pingInterval = setInterval(function () {
      self.send(JSON.stringify({ type: 'ping', ts: Date.now() }));
    }, 30000);
  }
}

// ---------------------------------------------------------------------------
// 4. TerminalManager
// ---------------------------------------------------------------------------

class TerminalManager {
  constructor(containerEl) {
    this.container = containerEl;

    /** @type {Terminal|null} */
    this.term = null;
    /** @type {FitAddon|null} */
    this.fitAddon = null;
    /** @type {WebLinksAddon|null} */
    this.webLinksAddon = null;
    /** @type {ReconnectingWS|null} */
    this.ws = null;

    // Callbacks set by App
    /** @type {((code: number, reason: string) => void)|null} */
    this.onWsClose = null;
    /** @type {((msg: object) => void)|null} */
    this.onControlMessage = null;
    /** @type {((status: string) => void)|null} */
    this.onStatusChange = null;
  }

  /** Create and mount xterm.js terminal. */
  init() {
    const fontSize = this._calcFontSize();

    this.term = new window.Terminal({
      fontSize: fontSize,
      fontFamily: "'Menlo', 'Courier New', monospace",
      lineHeight: 1.0,
      scrollback: 5000,
      cursorBlink: true,
      cursorStyle: 'bar',
      allowProposedApi: true,
      convertEol: false,
      overviewRulerWidth: 0,
      theme: {
        background: getComputedStyle(document.documentElement).getPropertyValue('--term-background').trim() || '#1A1B1E',
        foreground: getComputedStyle(document.documentElement).getPropertyValue('--term-foreground').trim() || '#D4D4D8',
        cursor: getComputedStyle(document.documentElement).getPropertyValue('--term-cursor').trim() || '#D4D4D8',
        selectionBackground: getComputedStyle(document.documentElement).getPropertyValue('--term-selection').trim() || '#264F78'
      }
    });

    this.fitAddon = new window.FitAddon.FitAddon();
    this.term.loadAddon(this.fitAddon);

    this.webLinksAddon = new window.WebLinksAddon.WebLinksAddon();
    this.term.loadAddon(this.webLinksAddon);

    this.term.open(this.container);
    this._fitWithMinCols();
  }

  /** Fit terminal ensuring minimum 80 columns for TUI compatibility.
   *  Resolves the target font size ONCE (or reads user-saved override),
   *  then calls fit() — does NOT re-measure on every fit because the
   *  dynamic font recalculation caused visible "jitter" between cells
   *  when switching projects.
   */
  _fitWithMinCols() {
    const MIN_COLS = 80;
    const MIN_FONT = 6;

    // Apply persistent font size if the user has set one in Settings,
    // otherwise resolve once and freeze for the rest of the session.
    if (this._resolvedFontSize == null) {
      this._resolvedFontSize = this._resolveStableFontSize(MIN_COLS, MIN_FONT);
      if (this._resolvedFontSize && this.term.options.fontSize !== this._resolvedFontSize) {
        this.term.options.fontSize = this._resolvedFontSize;
      }
    }

    try {
      this.fitAddon.fit();
    } catch (_e) {
      // fit() may throw on zero-size containers; harmless.
    }
  }

  /** Resolve a stable font size once: either the user's saved value, or one
   *  derived from a single measurement so MIN_COLS columns fit. */
  _resolveStableFontSize(MIN_COLS, MIN_FONT) {
    const saved = parseInt(localStorage.getItem('vs_terminal_font_size'), 10);
    if (saved && saved >= MIN_FONT && saved <= 48) return saved;

    try {
      this.fitAddon.fit();
    } catch (_e) {
      return this.term.options.fontSize;
    }
    if (this.term.cols >= MIN_COLS) return this.term.options.fontSize;

    const core = this.term._core;
    if (!core || !core._renderService) return this.term.options.fontSize;
    const cellWidth = core._renderService.dimensions.css.cell.width;
    if (cellWidth <= 0) return this.term.options.fontSize;
    const neededCellWidth = this.container.clientWidth / MIN_COLS;
    const scale = neededCellWidth / cellWidth;
    return Math.max(MIN_FONT, Math.floor(this.term.options.fontSize * scale));
  }

  /** Public: user-driven font size override. */
  setFontSize(px) {
    if (typeof px !== 'number' || px < 6 || px > 48) return;
    this._resolvedFontSize = px;
    localStorage.setItem('vs_terminal_font_size', String(px));
    this.term.options.fontSize = px;
    try { this.fitAddon.fit(); } catch (_e) {}
  }

  /** Write scrollback content into the terminal. */
  writeScrollback(content) {
    if (content && this.term) {
      this.term.write(content);
    }
  }

  /**
   * Connect WebSocket to a terminal session. The new WebSocket is opened
   * BEFORE clearing the terminal. Live binary frames are buffered until the
   * server sends `auth_ok`, at which point the caller is expected to call
   * `applyScrollbackAndFlush()` to perform an atomic swap (clear + scrollback
   * + buffered frames + start live writes). This eliminates the empty-screen
   * gap that previously caused the visible "flicker" on project switch.
   *
   * @param {string} sessionId
   * @param {string} token
   * @param {(() => Promise<string>|string)} fetchScrollback
   *   Callback returning the scrollback content (string of ANSI bytes). Invoked
   *   after auth_ok so the HTTP fetch happens in parallel with WS handshake.
   */
  connect(sessionId, token, fetchScrollback) {
    const self = this;

    // SECURITY: Token NOT in URL (prevents leakage in logs/history/Referer).
    // Sent as first WS message after connection.
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = proto + '//' + location.host + '/ws/terminal/' + sessionId;
    // Close previous WS if any
    if (this.ws) {
      this.ws.close();
    }

    // Reset transition state
    this._authAcked = false;
    this._pendingBinary = [];
    this._currentSessionId = sessionId;
    this._fetchScrollback = fetchScrollback || null;
    this._inputBound = this._inputBound || false;

    this.ws = new ReconnectingWS(wsUrl, {
      protocols: ['vibestudio.v1'],
      onOpen: function () {
        self._authAcked = false;
        self._pendingBinary = [];
        // Send auth token as first message (not in URL query params).
        self.ws.send(JSON.stringify({ type: 'auth', token: token }));
        // Send initial resize after auth.
        if (self.term) {
          self.ws.send(JSON.stringify({
            type: 'resize',
            cols: self.term.cols,
            rows: self.term.rows
          }));
        }
      },
      onMessage: function (evt) {
        self._handleMessage(evt);
      },
      onClose: function (code, reason) {
        if (self.onWsClose) self.onWsClose(code, reason);
      },
      onStatusChange: function (status) {
        if (self.onStatusChange) self.onStatusChange(status);
      }
    });

    this.ws.connect();

    // Forward terminal input to WS (bound once for the lifetime of `this.term`)
    if (!this._inputBound && this.term) {
      this.term.onData(function (data) {
        if (self.ws) {
          self.ws.send(JSON.stringify({ type: 'input', data: data }));
        }
      });
      this._inputBound = true;
    }
  }

  /** Internal: called from _handleMessage when `auth_ok` arrives. Triggers
   *  the atomic swap: clear → scrollback (chunked via rAF) → buffered
   *  binary → live. Large scrollback is split into 64KB rAF-paced chunks
   *  so the main thread is never blocked.
   */
  async _onAuthAck() {
    const self = this;
    let scrollback = '';
    if (this._fetchScrollback) {
      try {
        const result = await this._fetchScrollback();
        scrollback = (result || '');
      } catch (_e) {
        // Non-critical — terminal works without scrollback.
      }
    }

    if (!this.term) return;
    this.term.reset();

    // Chunked scrollback write — 64KB per rAF tick. xterm.write() is async,
    // so we use its completion callback to chain the next chunk on the
    // next animation frame; this keeps the UI responsive on slow devices.
    const CHUNK = 64 * 1024;
    let offset = 0;

    function writeChunk() {
      if (!self.term) return;
      if (offset >= scrollback.length) {
        flushPendingAndGoLive();
        return;
      }
      const slice = scrollback.slice(offset, offset + CHUNK);
      offset += CHUNK;
      self.term.write(slice, function () {
        requestAnimationFrame(writeChunk);
      });
    }

    function flushPendingAndGoLive() {
      const pending = self._pendingBinary || [];
      self._pendingBinary = null; // stop buffering — future frames go live
      for (let i = 0; i < pending.length; i++) {
        self.term.write(pending[i]);
      }
      self._authAcked = true;
      if (self.onScrollbackReady) self.onScrollbackReady();
    }

    if (scrollback) {
      requestAnimationFrame(writeChunk);
    } else {
      flushPendingAndGoLive();
    }
  }

  /** Disconnect WebSocket. */
  disconnect() {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  /** Resize terminal to fit container, enforcing minimum 80 columns. */
  fit() {
    if (this.fitAddon) {
      try {
        this._fitWithMinCols();
      } catch (_e) {
        // fit may throw if container has zero dimensions
      }
    }
  }

  /** Send resize message to server after fit. */
  sendResize() {
    if (this.term && this.ws) {
      this.ws.send(JSON.stringify({
        type: 'resize',
        cols: this.term.cols,
        rows: this.term.rows
      }));
    }
  }

  /** Send raw data string to the terminal PTY via WS. */
  sendInput(data) {
    if (this.ws) {
      this.ws.send(JSON.stringify({ type: 'input', data: data }));
    }
  }

  /**
   * Apply a theme to xterm.js.
   * @param {object} colors — TerminalColors from API
   */
  applyTheme(colors) {
    if (!this.term || !colors) return;

    const theme = {
      foreground: colors.foreground,
      background: colors.background,
      cursor: colors.cursor,
      selectionBackground: colors.selection
    };

    if (colors.ansi && colors.ansi.length === 16) {
      theme.black = colors.ansi[0];
      theme.red = colors.ansi[1];
      theme.green = colors.ansi[2];
      theme.yellow = colors.ansi[3];
      theme.blue = colors.ansi[4];
      theme.magenta = colors.ansi[5];
      theme.cyan = colors.ansi[6];
      theme.white = colors.ansi[7];
      theme.brightBlack = colors.ansi[8];
      theme.brightRed = colors.ansi[9];
      theme.brightGreen = colors.ansi[10];
      theme.brightYellow = colors.ansi[11];
      theme.brightBlue = colors.ansi[12];
      theme.brightMagenta = colors.ansi[13];
      theme.brightCyan = colors.ansi[14];
      theme.brightWhite = colors.ansi[15];
    }

    this.term.options.theme = theme;
  }

  /** Clear terminal buffer. */
  clear() {
    if (this.term) {
      this.term.clear();
      this.term.reset();
    }
  }

  // --- Private ---

  /** Calculate font size based on viewport width. */
  _calcFontSize() {
    const w = window.innerWidth;
    if (w <= 375) return 11;
    if (w <= 430) return 12;
    if (w <= 768) return 13;
    return 14;
  }

  /** Handle incoming WS message. */
  _handleMessage(evt) {
    // Binary frame = terminal output (raw PTY bytes with ANSI escapes)
    if (evt.data instanceof ArrayBuffer) {
      const bytes = new Uint8Array(evt.data);
      if (this._pendingBinary) {
        this._pendingBinary.push(bytes);
      } else if (this.term) {
        this.term.write(bytes);
      }
      return;
    }

    // Blob fallback — some browsers deliver binary as Blob despite binaryType='arraybuffer'
    if (evt.data instanceof Blob) {
      const self = this;
      evt.data.arrayBuffer().then(function (ab) {
        const bytes = new Uint8Array(ab);
        if (self._pendingBinary) {
          self._pendingBinary.push(bytes);
        } else if (self.term) {
          self.term.write(bytes);
        }
      });
      return;
    }

    // Text frame = control message
    let msg;
    try {
      msg = JSON.parse(evt.data);
    } catch (_e) {
      return;
    }

    // Intercept auth_ok internally — kicks off the atomic swap.
    if (msg && msg.type === 'auth_ok') {
      this._onAuthAck();
      return;
    }

    if (this.onControlMessage) {
      this.onControlMessage(msg);
    }
  }
}

// ---------------------------------------------------------------------------
// 5. SpecialKeysBar
// ---------------------------------------------------------------------------

class SpecialKeysBar {
  /**
   * @param {HTMLElement} barEl
   * @param {TerminalManager} terminalManager
   * @param {HTMLInputElement} keyboardProxy
   */
  constructor(barEl, terminalManager, keyboardProxy) {
    this.bar = barEl;
    this.tm = terminalManager;
    this.proxy = keyboardProxy;

    this._ctrlActive = false;
    this._repeatTimer = null;
    this._repeatInterval = null;

    this._bind();
  }

  // --- Private ---

  _bind() {
    const self = this;
    const buttons = this.bar.querySelectorAll('.key-btn');

    buttons.forEach(function (btn) {
      const key = btn.getAttribute('data-key');
      // Skip buttons without data-key (Play/Stop/agent-picker are handled by App)
      if (!key) return;

      // Use touchstart/touchend for arrows (long-press repeat)
      // and click for the rest.
      if (['up', 'down', 'left', 'right'].indexOf(key) >= 0) {
        btn.addEventListener('touchstart', function (e) {
          e.preventDefault();
          self._sendArrow(key);
          self._startRepeat(key);
        }, { passive: false });

        btn.addEventListener('touchend', function (e) {
          e.preventDefault();
          self._stopRepeat();
        }, { passive: false });

        btn.addEventListener('touchcancel', function () {
          self._stopRepeat();
        });

        // Fallback for non-touch
        btn.addEventListener('mousedown', function (e) {
          if (e.button !== 0) return;
          self._sendArrow(key);
          self._startRepeat(key);
        });
        btn.addEventListener('mouseup', function () { self._stopRepeat(); });
        btn.addEventListener('mouseleave', function () { self._stopRepeat(); });
      } else {
        // Touch-first for non-arrow keys
        btn.addEventListener('touchstart', function (e) {
          e.preventDefault();
          self._handleKey(key, btn);
        }, { passive: false });

        // Fallback click for desktop
        btn.addEventListener('click', function (e) {
          // Only fire if no touchstart fired
          if (e.sourceCapabilities && e.sourceCapabilities.firesTouchEvents) return;
          self._handleKey(key, btn);
        });
      }
    });

    // Keyboard proxy input events (for Kbd virtual keyboard)
    this.proxy.addEventListener('input', function () {
      // Skip if prompt-input is focused (avoid double-sending)
      const promptEl = document.getElementById('prompt-input');
      if (promptEl && document.activeElement === promptEl) {
        self.proxy.value = '';
        return;
      }
      const text = self.proxy.value;
      if (text) {
        // If ctrl is active, send as ctrl+char
        if (self._ctrlActive) {
          for (let i = 0; i < text.length; i++) {
            const code = text.charCodeAt(i);
            // a-z -> Ctrl+A-Z (1-26), A-Z also
            if (code >= 97 && code <= 122) {
              self.tm.sendInput(String.fromCharCode(code - 96));
            } else if (code >= 65 && code <= 90) {
              self.tm.sendInput(String.fromCharCode(code - 64));
            } else {
              self.tm.sendInput(text[i]);
            }
          }
          self._deactivateCtrl();
        } else {
          self.tm.sendInput(text);
        }
      }
      self.proxy.value = '';
    });

    this.proxy.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        if (self._ctrlActive) {
          // Ctrl+M
          self.tm.sendInput('\r');
          self._deactivateCtrl();
        } else {
          self.tm.sendInput('\r');
        }
      }
    });
  }

  _handleKey(key, btn) {
    switch (key) {
      case 'esc':
        if (this._ctrlActive) {
          // Ctrl+[ = Esc
          this.tm.sendInput('\x1b');
          this._deactivateCtrl();
        } else {
          this.tm.sendInput('\x1b');
        }
        break;

      case 'tab':
        if (this._ctrlActive) {
          // Ctrl+I = Tab
          this.tm.sendInput('\t');
          this._deactivateCtrl();
        } else {
          this.tm.sendInput('\t');
        }
        break;

      case 'ctrl':
        this._ctrlActive = !this._ctrlActive;
        btn.setAttribute('aria-pressed', this._ctrlActive ? 'true' : 'false');
        break;

      case 'pipe':
        if (this._ctrlActive) {
          // Ctrl+\ = SIGQUIT
          this.tm.sendInput('\x1c');
          this._deactivateCtrl();
        } else {
          this.tm.sendInput('|');
        }
        break;

      case 'kbd':
        this._toggleKeyboard();
        break;
    }
  }

  _sendArrow(dir) {
    let seq;
    switch (dir) {
      case 'up':    seq = '\x1b[A'; break;
      case 'down':  seq = '\x1b[B'; break;
      case 'right': seq = '\x1b[C'; break;
      case 'left':  seq = '\x1b[D'; break;
      default: return;
    }

    if (this._ctrlActive) {
      // Ctrl+Arrow: send modified sequence
      switch (dir) {
        case 'up':    seq = '\x1b[1;5A'; break;
        case 'down':  seq = '\x1b[1;5B'; break;
        case 'right': seq = '\x1b[1;5C'; break;
        case 'left':  seq = '\x1b[1;5D'; break;
      }
      this._deactivateCtrl();
    }

    this.tm.sendInput(seq);
  }

  _startRepeat(dir) {
    const self = this;
    clearTimeout(this._repeatTimer);
    clearInterval(this._repeatInterval);

    this._repeatTimer = setTimeout(function () {
      self._repeatInterval = setInterval(function () {
        self._sendArrow(dir);
      }, 80);
    }, 400);
  }

  _stopRepeat() {
    clearTimeout(this._repeatTimer);
    clearInterval(this._repeatInterval);
  }

  _deactivateCtrl() {
    this._ctrlActive = false;
    const ctrlBtn = this.bar.querySelector('[data-key="ctrl"]');
    if (ctrlBtn) ctrlBtn.setAttribute('aria-pressed', 'false');
  }

  _toggleKeyboard() {
    if (document.activeElement === this.proxy) {
      this.proxy.blur();
    } else {
      this.proxy.focus();
    }
  }
}

// ---------------------------------------------------------------------------
// 6. ThemeManager
// ---------------------------------------------------------------------------

class ThemeManager {
  /**
   * Apply terminal theme colors to CSS custom properties.
   * @param {object} themeData — theme_changed WS message or status.theme
   */
  static apply(themeData) {
    if (!themeData) return;

    const root = document.documentElement.style;
    const colors = themeData.terminal_colors;

    if (colors) {
      if (colors.foreground) root.setProperty('--term-foreground', colors.foreground);
      if (colors.background) root.setProperty('--term-background', colors.background);
      if (colors.cursor) root.setProperty('--term-cursor', colors.cursor);
      if (colors.selection) root.setProperty('--term-selection', colors.selection);
    }

    // If appearance info is included, swap the full UI palette
    if (themeData.appearance === 'light') {
      root.setProperty('--surface-base', '#FFFFFF');
      root.setProperty('--surface-raised', '#F5F5F7');
      root.setProperty('--surface-overlay', '#EBEBED');
      root.setProperty('--surface-input', '#F0F0F2');
      root.setProperty('--surface-tab-bar', '#EBEBED');
      root.setProperty('--text-primary', '#1D1D1F');
      root.setProperty('--text-secondary', '#6E6E73');
      root.setProperty('--text-muted', '#AEAEB2');
      root.setProperty('--border-default', '#D1D1D6');
      root.setProperty('--border-subtle', '#E5E5EA');
      root.setProperty('--border-focus', '#0066FF');
      root.setProperty('--accent-primary', '#0066FF');
      root.setProperty('--accent-primary-hover', '#0055EE');
      root.setProperty('--indicator-running', '#28843B');
      root.setProperty('--indicator-waiting', '#B59400');
      root.setProperty('--indicator-error', '#C42B2B');
    } else if (themeData.appearance === 'dark') {
      // Reset to dark defaults
      root.setProperty('--surface-base', '#1A1B1E');
      root.setProperty('--surface-raised', '#212225');
      root.setProperty('--surface-overlay', '#2A2B2F');
      root.setProperty('--surface-input', '#16171A');
      root.setProperty('--surface-tab-bar', '#17181B');
      root.setProperty('--text-primary', '#D4D4D8');
      root.setProperty('--text-secondary', '#8B8B93');
      root.setProperty('--text-muted', '#55565C');
      root.setProperty('--border-default', '#2E2F33');
      root.setProperty('--border-subtle', '#252629');
      root.setProperty('--border-focus', '#4A9EFF');
      root.setProperty('--accent-primary', '#4A9EFF');
      root.setProperty('--accent-primary-hover', '#5BABFF');
      root.setProperty('--indicator-running', '#3FB950');
      root.setProperty('--indicator-waiting', '#E2B93D');
      root.setProperty('--indicator-error', '#F85149');
    }
  }
}

// ---------------------------------------------------------------------------
// 7. App — Orchestrator
// ---------------------------------------------------------------------------

const App = (function () {
  /** @type {VibeStudioClient} */
  let client;
  /** @type {PinInput} */
  let pinInput;
  /** @type {TerminalManager} */
  let terminalMgr;
  /** @type {SpecialKeysBar} */
  let keysBar;

  // DOM refs
  let pinScreen;
  let terminalScreen;
  let projectPicker;
  let sessionPicker;
  let statusDot;
  let statusLabel;
  let keyboardProxy;

  // State
  let currentProjectId = null;
  let currentSessionId = null;
  let projectsData = null;

  // ------ Init ------

  function init() {
    // DOM
    pinScreen = document.getElementById('pin-screen');
    terminalScreen = document.getElementById('terminal-screen');
    projectPicker = document.getElementById('project-picker');
    sessionPicker = document.getElementById('session-picker');
    statusDot = document.getElementById('status-dot');
    statusLabel = document.getElementById('status-label');
    keyboardProxy = document.getElementById('keyboard-proxy');

    // Client
    client = new VibeStudioClient();

    // PIN input
    pinInput = new PinInput(
      document.getElementById('pin-digits'),
      document.getElementById('pin-error'),
      document.getElementById('pin-blocked'),
      document.getElementById('pin-countdown')
    );
    pinInput.onComplete = handlePinComplete;

    // Terminal manager
    terminalMgr = new TerminalManager(document.getElementById('terminal-container'));
    terminalMgr.onWsClose = handleWsClose;
    terminalMgr.onControlMessage = handleControlMessage;
    terminalMgr.onStatusChange = updateConnectionStatus;
    terminalMgr.onScrollbackReady = hideTerminalOverlay;

    // Prompt input bar
    const promptInput = document.getElementById('prompt-input');
    const sendBtn = document.getElementById('input-send-btn');

    // ── Prompt History (persisted) ─────────────────────────────────────────
    const HISTORY_KEY = 'vs_prompt_history';
    const HISTORY_MAX = 50;
    let history = (function () {
      try {
        const raw = localStorage.getItem(HISTORY_KEY);
        return raw ? JSON.parse(raw) : [];
      } catch (_e) { return []; }
    })();
    let historyIdx = history.length; // pointer (length = "after the end" = fresh)
    let draftBeforeHistory = '';

    function pushHistory(text) {
      if (!text) return;
      // De-duplicate consecutive entries.
      if (history.length && history[history.length - 1] === text) {
        historyIdx = history.length;
        return;
      }
      history.push(text);
      while (history.length > HISTORY_MAX) history.shift();
      try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history)); } catch (_e) {}
      historyIdx = history.length;
    }

    // ── Auto-grow textarea ────────────────────────────────────────────────
    function autoGrow() {
      promptInput.style.height = 'auto';
      const maxH = Math.floor(window.innerHeight * 0.3);
      promptInput.style.height = Math.min(promptInput.scrollHeight, maxH) + 'px';
    }

    // ── Paste-chunking ────────────────────────────────────────────────────
    // Large pastes are split into 512-byte chunks with a small delay so the
    // server-side per-minute byte budget and the PTY's input buffer aren't
    // overrun, and so the TUI processes them in order.
    async function sendChunked(text) {
      const CHUNK = 512;
      for (let i = 0; i < text.length; i += CHUNK) {
        terminalMgr.sendInput(text.slice(i, i + CHUNK));
        if (text.length > CHUNK) {
          await new Promise(function (r) { setTimeout(r, 8); });
        }
      }
    }

    async function sendPromptInput() {
      const text = promptInput.value;
      const readyPaths = attachments
        .filter(function (a) { return a.status === 'ready' && a.path; })
        .map(function (a) { return a.path; });

      if (!text && readyPaths.length === 0) return;
      keyboardProxy.blur();

      // Compose payload: text + space-separated image paths so Claude Code /
      // similar CLIs see them as drag-dropped file references.
      let payload = text;
      if (readyPaths.length) {
        if (payload && !payload.endsWith(' ')) payload += ' ';
        payload += readyPaths.join(' ');
      }

      await sendChunked(payload + '\r');
      if (text) pushHistory(text);

      // Clear UI: text, textarea height, attachment thumbnails (& blob URLs).
      attachments.forEach(function (a) {
        try { URL.revokeObjectURL(a.url); } catch (_e) {}
      });
      attachments = [];
      renderAttachments();
      promptInput.value = '';
      autoGrow();
    }

    sendBtn.addEventListener('click', function () { sendPromptInput(); });

    promptInput.addEventListener('input', autoGrow);

    // Single-line "input"-like history & shortcuts on the textarea.
    promptInput.addEventListener('keydown', function (e) {
      // Enter (no Shift, no Alt) = send. Shift+Enter = newline. Cmd/Ctrl+Enter also = send.
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        sendPromptInput();
        return;
      }
      // Esc = clear input (don't propagate to xterm)
      if (e.key === 'Escape') {
        if (promptInput.value) {
          e.preventDefault();
          promptInput.value = '';
          autoGrow();
          historyIdx = history.length;
        }
        return;
      }
      // ↑ / ↓ history — only when the textarea is single-line (no newlines).
      const isSingleLine = promptInput.value.indexOf('\n') === -1;
      if (isSingleLine && e.key === 'ArrowUp' && history.length) {
        e.preventDefault();
        if (historyIdx === history.length) draftBeforeHistory = promptInput.value;
        if (historyIdx > 0) historyIdx--;
        promptInput.value = history[historyIdx];
        autoGrow();
        return;
      }
      if (isSingleLine && e.key === 'ArrowDown' && history.length) {
        e.preventDefault();
        if (historyIdx < history.length) historyIdx++;
        promptInput.value = historyIdx === history.length
          ? draftBeforeHistory
          : history[historyIdx];
        autoGrow();
        return;
      }
      // Ctrl+C → send SIGINT (\x03) to PTY, do NOT clear input.
      if (e.key === 'c' && (e.ctrlKey || e.metaKey)) {
        // Only intercept when no text is selected — otherwise let copy work.
        if (promptInput.selectionStart === promptInput.selectionEnd) {
          e.preventDefault();
          terminalMgr.sendInput('\x03');
        }
        return;
      }
    });

    // Paste handler — text + clipboard images.
    promptInput.addEventListener('paste', function (e) {
      const data = (e.clipboardData || window.clipboardData);
      if (!data) return;
      // Pasted images become attachments.
      const items = Array.from(data.items || []);
      const imageItems = items.filter(function (it) { return it.kind === 'file' && it.type.indexOf('image/') === 0; });
      if (imageItems.length) {
        e.preventDefault();
        imageItems.forEach(function (it) {
          const file = it.getAsFile();
          if (file) addAttachmentFile(file);
        });
        return;
      }
      const txt = data.getData('text');
      if (txt && txt.length > 4096) {
        // For very large pastes, bypass the textarea: send directly to PTY
        // in chunks so the UI doesn't lock up wrapping the text.
        e.preventDefault();
        sendChunked(txt);
      }
    });

    // Agent pill (inline play/stop in composer)
    const agentPicker = document.getElementById('agent-picker');
    const agentPlayBtn = document.getElementById('agent-play-btn');
    const agentStopBtn = document.getElementById('agent-stop-btn');
    const agentPill = document.getElementById('agent-pill');
    let agentRunning = false;

    function setAgentRunning(running) {
      agentRunning = running;
      agentPlayBtn.hidden = running;
      agentStopBtn.hidden = !running;
      agentPicker.disabled = running;
      agentPill.classList.toggle('is-running', running);
    }

    agentPlayBtn.addEventListener('click', function () {
      const agent = agentPicker.value;
      client.startAssistant(agent).then(function (resp) {
        if (resp.ok) setAgentRunning(true);
      });
    });

    agentStopBtn.addEventListener('click', function () {
      client.stopAssistant().then(function (resp) {
        if (resp.ok) setAgentRunning(false);
      });
    });

    // ── Attachments (images) ───────────────────────────────────────────────
    const attachmentsEl = document.getElementById('attachments');
    const attachBtn = document.getElementById('attach-btn');
    const attachInput = document.getElementById('attach-input');
    const composer = document.getElementById('composer');
    /** @type {Array<{id: string, name: string, status: string, path: string|null, url: string}>} */
    let attachments = [];

    function newAttachmentId() {
      return 'att-' + Math.random().toString(36).slice(2, 10);
    }

    function renderAttachments() {
      attachmentsEl.innerHTML = '';
      attachments.forEach(function (a) {
        const tile = document.createElement('div');
        tile.className = 'attachment' + (a.status === 'uploading' ? ' is-uploading' : '');
        tile.dataset.id = a.id;
        const img = document.createElement('img');
        img.src = a.url;
        img.alt = a.name;
        tile.appendChild(img);
        const rm = document.createElement('button');
        rm.className = 'attachment-remove';
        rm.type = 'button';
        rm.setAttribute('aria-label', 'Remove attachment');
        rm.textContent = '×';
        rm.addEventListener('click', function () {
          attachments = attachments.filter(function (x) { return x.id !== a.id; });
          try { URL.revokeObjectURL(a.url); } catch (_e) {}
          renderAttachments();
        });
        tile.appendChild(rm);
        attachmentsEl.appendChild(tile);
      });
    }

    async function addAttachmentFile(file) {
      if (!file || !file.type || file.type.indexOf('image/') !== 0) return;
      const id = newAttachmentId();
      const url = URL.createObjectURL(file);
      const entry = { id: id, name: file.name || 'image', status: 'uploading', path: null, url: url };
      attachments.push(entry);
      renderAttachments();

      const path = await client.uploadImage(file);
      const idx = attachments.findIndex(function (x) { return x.id === id; });
      if (idx === -1) return; // user removed it during upload
      if (path) {
        attachments[idx].path = path;
        attachments[idx].status = 'ready';
      } else {
        attachments.splice(idx, 1);
      }
      renderAttachments();
    }

    attachBtn.addEventListener('click', function () { attachInput.click(); });
    attachInput.addEventListener('change', function () {
      const files = Array.from(attachInput.files || []);
      files.forEach(addAttachmentFile);
      attachInput.value = '';
    });

    // Drag & drop onto composer
    ['dragenter', 'dragover'].forEach(function (ev) {
      composer.addEventListener(ev, function (e) {
        if (!e.dataTransfer || !Array.from(e.dataTransfer.types || []).includes('Files')) return;
        e.preventDefault();
        composer.classList.add('is-dragover');
      });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      composer.addEventListener(ev, function (e) {
        composer.classList.remove('is-dragover');
        if (ev === 'drop' && e.dataTransfer) {
          e.preventDefault();
          const files = Array.from(e.dataTransfer.files || []);
          files.forEach(addAttachmentFile);
        }
      });
    });

    // Pickers
    projectPicker.addEventListener('change', handleProjectChange);
    sessionPicker.addEventListener('change', handleSessionChange);

    // ── Swipe gestures on top-bar: ← / → switches sessions within the
    // current project. Vertical swipes are ignored so terminal scroll keeps
    // working. Threshold large enough to avoid accidental triggers.
    setupSwipeNav(document.querySelector('.top-bar'));

    // Viewport resize (iOS keyboard)
    setupViewportHandling();

    // Window resize
    window.addEventListener('resize', debounce(function () {
      if (terminalMgr.term) {
        terminalMgr.fit();
        terminalMgr.sendResize();
      }
    }, 150));

    // SECURITY: ?pin= auto-login removed (PIN leaked in server logs, proxy logs,
    // browser history despite replaceState). QR code should link to the app URL
    // without credentials; user enters PIN manually.

    // Pre-emptive token refresh — every 5 minutes, refresh if <30 min remain.
    setInterval(function () {
      if (!client.hasToken()) return;
      const remaining = client.secondsUntilExpiry();
      if (remaining > 0 && remaining < 30 * 60) {
        client.refreshToken();
      }
    }, 5 * 60 * 1000);

    // Also refresh when the tab regains focus after being backgrounded —
    // iOS Safari aggressively suspends, so a long idle window may have
    // elapsed since the last interval tick.
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible' && client.hasToken()) {
        const remaining = client.secondsUntilExpiry();
        if (remaining > 0 && remaining < 60 * 60) {
          client.refreshToken();
        }
      }
    });

    // Check existing token
    if (client.hasToken()) {
      client.validateToken().then(function (valid) {
        if (valid) {
          showTerminalScreen();
        } else {
          showPinScreen();
        }
      });
    } else {
      showPinScreen();
    }
  }

  // ------ Screen Transitions ------

  function showPinScreen() {
    pinScreen.hidden = false;
    terminalScreen.hidden = true;
    terminalMgr.disconnect();
    pinInput.reset();
  }

  function showTerminalScreen() {
    pinScreen.hidden = true;
    terminalScreen.hidden = false;

    if (!terminalMgr.term) {
      terminalMgr.init();
      keysBar = new SpecialKeysBar(
        document.getElementById('keys-bar'),
        terminalMgr,
        keyboardProxy
      );
    }

    // Fit after DOM is visible
    requestAnimationFrame(function () {
      terminalMgr.fit();
      loadProjectsAndConnect();
    });
  }

  // ------ PIN Handling ------

  async function handlePinComplete(pin) {
    const result = await client.authenticate(pin);

    if (result.ok) {
      showTerminalScreen();
      return;
    }

    const err = result.error || {};
    if (result.status === 429) {
      const seconds = (err.details && err.details.retry_after_seconds) || 300;
      pinInput.setBlocked(seconds);
    } else if (result.status === 401) {
      let msg = err.message || 'Incorrect PIN';
      if (err.details && typeof err.details.attempts_remaining === 'number') {
        msg = 'Incorrect PIN. ' + err.details.attempts_remaining + ' attempt' +
              (err.details.attempts_remaining !== 1 ? 's' : '') + ' remaining.';
      }
      pinInput.setError(msg);
    } else {
      pinInput.setError(err.message || 'Connection error. Try again.');
    }
  }

  // ------ Projects & Sessions ------

  async function loadProjectsAndConnect() {
    try {
      const data = await client.getProjects();
      projectsData = data;

      // Fetch recent projects (non-critical)
      let recentData = null;
      try {
        recentData = await client.getRecentProjects();
      } catch (_e) {
        // Non-critical — recent projects just won't show
      }

      populateProjectPicker(data.projects, data.active_project_id, recentData ? recentData.projects : []);

      // Also fetch theme from status
      try {
        const status = await client.getStatus();
        if (status && status.theme) {
          ThemeManager.apply(status.theme);
          terminalMgr.applyTheme(status.theme.terminal_colors);
        }
      } catch (_e) {
        // Non-critical
      }
    } catch (e) {
      if (e.status === 401) {
        client.clearToken();
        showPinScreen();
        return;
      }
      updateConnectionStatus('disconnected');
    }
  }

  function populateProjectPicker(projects, activeId, recentProjects) {
    projectPicker.innerHTML = '';

    if ((!projects || projects.length === 0) && (!recentProjects || recentProjects.length === 0)) {
      projectPicker.innerHTML = '<option value="">No projects</option>';
      sessionPicker.innerHTML = '<option value="">No sessions</option>';
      return;
    }

    // Open projects
    if (projects && projects.length > 0) {
      const openGroup = document.createElement('optgroup');
      openGroup.label = '\u041E\u0442\u043A\u0440\u044B\u0442\u044B\u0435';
      projects.forEach(function (p) {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = p.name;
        openGroup.appendChild(opt);
      });
      projectPicker.appendChild(openGroup);
    }

    // Recent projects (closed)
    if (recentProjects && recentProjects.length > 0) {
      const recentGroup = document.createElement('optgroup');
      recentGroup.label = '\u041D\u0435\u0434\u0430\u0432\u043D\u0438\u0435';
      recentProjects.forEach(function (p) {
        const opt = document.createElement('option');
        opt.value = 'recent:' + p.path;
        opt.textContent = p.name;
        recentGroup.appendChild(opt);
      });
      projectPicker.appendChild(recentGroup);
    }

    // Select active project or first
    if (projects && projects.length > 0) {
      const targetId = activeId || projects[0].id;
      projectPicker.value = targetId;
      selectProject(targetId);
    }
  }

  function selectProject(projectId) {
    currentProjectId = projectId;
    if (!projectsData) return;

    const project = projectsData.projects.find(function (p) { return p.id === projectId; });
    if (!project) return;

    populateSessionPicker(project.sessions);
  }

  function populateSessionPicker(sessions, preferId) {
    sessionPicker.innerHTML = '';

    if (!sessions || sessions.length === 0) {
      sessionPicker.innerHTML = '<option value="">No sessions</option>';
      return;
    }

    sessions.forEach(function (s) {
      const opt = document.createElement('option');
      opt.value = s.id;
      let label = s.title || 'Session';
      if (s.is_agent) label = '✨ ' + label + ' [agent]';
      if (s.state === 'exited') label += ' (exited)';
      opt.textContent = label;
      sessionPicker.appendChild(opt);
    });

    // Keep current selection if still present; otherwise pick first.
    const targetId = (preferId && sessions.some(function (s) { return s.id === preferId; }))
      ? preferId
      : sessions[0].id;
    sessionPicker.value = targetId;
    // Only (re)connect when target differs from current — prevents flicker
    // on every sessions_changed broadcast.
    if (currentSessionId !== targetId) {
      connectToSession(targetId);
    }
  }

  async function connectToSession(sessionId) {
    if (currentSessionId === sessionId && terminalMgr.ws) return;
    currentSessionId = sessionId;
    updateConnectionStatus('connecting');
    showTerminalOverlay('Loading session...');

    const token = client.getToken();
    if (!token) return;

    // Open WebSocket FIRST — binary frames are buffered by TerminalManager
    // until auth_ok arrives. Scrollback is fetched in parallel and applied
    // in a single rAF tick together with the buffered live data → no
    // visible empty-screen gap on project switch.
    const projectId = currentProjectId;
    terminalMgr.connect(sessionId, token, async function () {
      try {
        const scrollback = await client.getScrollback(projectId, sessionId);
        return (scrollback && scrollback.content) || '';
      } catch (e) {
        if (e.status === 401) {
          client.clearToken();
          showPinScreen();
        }
        return '';
      }
    });
  }

  function setupSwipeNav(el) {
    if (!el) return;
    const MIN_DX = 60;     // px horizontal travel
    const MAX_DY = 40;     // tolerated vertical drift
    const MAX_T = 600;     // ms — fast swipe only
    let sx = 0, sy = 0, st = 0, active = false;

    el.addEventListener('touchstart', function (e) {
      const t = e.touches[0];
      sx = t.clientX; sy = t.clientY; st = Date.now(); active = true;
    }, { passive: true });

    el.addEventListener('touchend', function (e) {
      if (!active) return;
      active = false;
      const t = (e.changedTouches && e.changedTouches[0]);
      if (!t) return;
      const dx = t.clientX - sx, dy = t.clientY - sy, dt = Date.now() - st;
      if (dt > MAX_T) return;
      if (Math.abs(dy) > MAX_DY) return;
      if (Math.abs(dx) < MIN_DX) return;
      switchSession(dx < 0 ? +1 : -1);
    }, { passive: true });
  }

  function switchSession(delta) {
    const opts = Array.from(sessionPicker.options).filter(function (o) { return o.value; });
    if (opts.length < 2) return;
    const idx = opts.findIndex(function (o) { return o.value === currentSessionId; });
    if (idx === -1) return;
    const next = (idx + delta + opts.length) % opts.length;
    const nextId = opts[next].value;
    sessionPicker.value = nextId;
    connectToSession(nextId);
  }

  function showTerminalOverlay(label) {
    const ov = document.getElementById('terminal-overlay');
    const lbl = document.getElementById('terminal-overlay-label');
    if (!ov) return;
    if (lbl && label) lbl.textContent = label;
    ov.hidden = false;
  }
  function hideTerminalOverlay() {
    const ov = document.getElementById('terminal-overlay');
    if (ov) ov.hidden = true;
  }

  async function handleProjectChange() {
    const newValue = projectPicker.value;
    if (!newValue) return;

    // Recent project — open it first, then reload
    if (newValue.indexOf('recent:') === 0) {
      const path = newValue.substring(7);
      try {
        updateConnectionStatus('connecting');
        const result = await client.openProject(path);
        terminalMgr.disconnect();
        // Reload full project list — the opened project is now in the open group
        await loadProjectsAndConnect();
      } catch (e) {
        updateConnectionStatus('disconnected');
      }
      return;
    }

    // Regular open project — activate
    if (newValue !== currentProjectId) {
      client.activateProject(newValue);
      terminalMgr.disconnect();
      selectProject(newValue);
    }
  }

  function handleSessionChange() {
    const newSessionId = sessionPicker.value;
    if (newSessionId && newSessionId !== currentSessionId) {
      terminalMgr.disconnect();
      connectToSession(newSessionId);
    }
  }

  // ------ WebSocket Messages ------

  function handleWsClose(code, _reason) {
    switch (code) {
      case 4000: // auth_expired — try silent refresh before dropping to PIN
        (async function () {
          const refreshed = await client.refreshToken();
          if (refreshed && currentSessionId) {
            // Reconnect WS with the new token — user never sees PIN.
            connectToSession(currentSessionId);
          } else {
            client.clearToken();
            showPinScreen();
          }
        })();
        break;
      case 4001: // disconnected_by_host
        updateConnectionStatus('disconnected');
        statusLabel.textContent = 'Disconnected by host';
        break;
      case 4002: // session_ended
        updateConnectionStatus('disconnected');
        statusLabel.textContent = 'Session ended';
        break;
      // Other codes: ReconnectingWS handles reconnect
    }
  }

  function handleControlMessage(msg) {
    switch (msg.type) {
      case 'session_state':
        if (msg.state === 'exited') {
          statusLabel.textContent = 'Exited (' + (msg.exit_code || 0) + ')';
        }
        break;

      case 'theme_changed':
        ThemeManager.apply(msg);
        terminalMgr.applyTheme(msg.terminal_colors);
        break;

      case 'sessions_changed':
        if (msg.project_id === currentProjectId) {
          // Pass current session as preferId — picker keeps selection if still
          // available, only switches to first when current session is gone.
          populateSessionPicker(msg.sessions, currentSessionId);
        }
        break;

      case 'pong':
        // Could compute RTT: Date.now() - msg.ts
        break;

      case 'error':
        if (msg.fatal) {
          updateConnectionStatus('disconnected');
          statusLabel.textContent = msg.message || 'Error';
        }
        break;

      case 'rate_limited':
        // Informational — input is being dropped server-side
        break;

      case 'device_disconnected':
        // Only react if the server-supplied deviceId matches ours AND we don't
        // already have a usable token. With sliding refresh + reconnect, the
        // server may notify us of an old session being detached; ignore those
        // when our token is still valid (we'll reconnect transparently).
        const deviceId = client.getDeviceId();
        if (msg.device_id === deviceId && client.isTokenExpired()) {
          client.clearToken();
          showPinScreen();
        }
        break;
    }
  }

  // ------ Connection Status ------

  function updateConnectionStatus(status) {
    statusDot.setAttribute('data-status', status);

    switch (status) {
      case 'connected':
        statusLabel.textContent = 'Connected';
        break;
      case 'connecting':
        statusLabel.textContent = 'Connecting...';
        break;
      case 'disconnected':
        statusLabel.textContent = 'Disconnected';
        break;
    }
  }

  // ------ iOS Keyboard Viewport ------

  function setupViewportHandling() {
    if (!window.visualViewport) return;

    const shell = document.querySelector('.app-shell');
    if (!shell) return;

    let fitTimer = null;

    // On Android Chrome, `interactive-widget=resizes-content` in the viewport
    // meta tag makes the layout viewport shrink when the keyboard opens, so
    // CSS `100dvh` is always correct.  The JS handler below is a fallback for
    // iOS Safari which ignores `interactive-widget`.
    const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent);

    window.visualViewport.addEventListener('resize', function () {
      if (isIOS) {
        // iOS Safari: layout viewport doesn't resize for keyboard, so
        // override the shell height with the visual viewport height.
        shell.style.height = window.visualViewport.height + 'px';
      }

      // Prevent any scroll offset the browser adds when focusing inputs.
      window.scrollTo(0, 0);

      // Debounce the expensive xterm.js relayout.
      clearTimeout(fitTimer);
      fitTimer = setTimeout(function () {
        terminalMgr.fit();
        terminalMgr.sendResize();
      }, 100);
    });

    window.visualViewport.addEventListener('scroll', function () {
      // Prevent scroll offset accumulation on iOS.
      window.scrollTo(0, 0);
    });
  }

  // ------ Utilities ------

  function debounce(fn, ms) {
    let timer;
    return function () {
      clearTimeout(timer);
      timer = setTimeout(fn, ms);
    };
  }

  // ------ Public ------

  return { init: init };
})();

// Boot
document.addEventListener('DOMContentLoaded', App.init);

// Register service worker for shell caching (instant boot on repeat visits).
if ('serviceWorker' in navigator) {
  window.addEventListener('load', function () {
    navigator.serviceWorker.register('/sw.js').catch(function (_e) {
      // SW registration failures are non-fatal — app works without it.
    });
  });
}
