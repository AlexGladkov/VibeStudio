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
  }

  /** @returns {boolean} */
  hasToken() {
    return !!sessionStorage.getItem('vs_token');
  }

  /** @returns {string|null} */
  getToken() {
    return sessionStorage.getItem('vs_token');
  }

  /** @returns {string|null} */
  getDeviceId() {
    return sessionStorage.getItem('vs_device_id');
  }

  clearToken() {
    sessionStorage.removeItem('vs_token');
    sessionStorage.removeItem('vs_device_id');
    sessionStorage.removeItem('vs_token_expires');
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
      sessionStorage.setItem('vs_token', data.token);
      sessionStorage.setItem('vs_device_id', data.device_id);
      sessionStorage.setItem('vs_token_expires', data.expires_at);
      return { ok: true, token: data.token };
    }

    return { ok: false, status: resp.status, error: data.error || data };
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
   *  If viewport is too narrow, reduces font size until 80 cols fit.
   *  Horizontal scroll doesn't work on mobile (xterm.js captures touch
   *  events for vertical scrollback), so shrinking the font is the only
   *  reliable way to guarantee 80-col TUI rendering. */
  _fitWithMinCols() {
    const MIN_COLS = 80;
    const MIN_FONT = 6;
    const xtermEl = this.container.querySelector('.xterm');

    // Reset any forced width from previous attempts
    if (xtermEl) xtermEl.style.width = '';

    // Fit to container at current font size
    this.fitAddon.fit();

    if (this.term.cols >= MIN_COLS) return;

    // Measure current cell width to calculate required font size.
    // NOTE: Accesses xterm.js internal `_core._renderService` — no public API
    // for cell dimensions exists. May break on xterm.js major version updates.
    // Gracefully degrades: if internals change, columns stay at whatever fit() gives.
    const core = this.term._core;
    if (!core || !core._renderService) return;

    const cellWidth = core._renderService.dimensions.css.cell.width;
    if (cellWidth <= 0) return;

    const containerWidth = this.container.clientWidth;
    const neededCellWidth = containerWidth / MIN_COLS;
    const scaleFactor = neededCellWidth / cellWidth;
    let newFontSize = Math.floor(this.term.options.fontSize * scaleFactor);

    if (newFontSize < MIN_FONT) newFontSize = MIN_FONT;

    this.term.options.fontSize = newFontSize;
    this.fitAddon.fit();
  }

  /** Write scrollback content into the terminal. */
  writeScrollback(content) {
    if (content && this.term) {
      this.term.write(content);
    }
  }

  /**
   * Connect WebSocket to a terminal session.
   * @param {string} sessionId
   * @param {string} token
   */
  connect(sessionId, token) {
    const self = this;

    // SECURITY: Token NOT in URL (prevents leakage in logs/history/Referer).
    // Sent as first WS message after connection.
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = proto + '//' + location.host + '/api/v1/terminal/' + sessionId;
    // Close previous WS if any
    if (this.ws) {
      this.ws.close();
    }

    this.ws = new ReconnectingWS(wsUrl, {
      protocols: ['vibestudio.v1'],
      onOpen: function () {
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

    // Forward terminal input to WS
    this.term.onData(function (data) {
      self.ws.send(JSON.stringify({ type: 'input', data: data }));
    });
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
      this.term.write(new Uint8Array(evt.data));
      return;
    }

    // Blob fallback — some browsers deliver binary as Blob despite binaryType='arraybuffer'
    if (evt.data instanceof Blob) {
      const self = this;
      evt.data.arrayBuffer().then(function (ab) {
        self.term.write(new Uint8Array(ab));
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
// 6b. QuickActionsBar
// ---------------------------------------------------------------------------

/**
 * Manages the Quick-Actions bar (Re-run · Pause · Clear · Kill).
 *
 * - Only shown when the authenticated device is the session owner.
 * - Each button uses optimistic UI: disabled+spinner while awaiting control_ack.
 * - control_ack ok=false → restore button state + show toast.
 * - Kill: tap = SIGINT (soft), long-press ≥500ms = force-kill with confirm().
 * - Buttons disabled when WS is offline.
 * - Clear: client clears xterm only after receiving control_ack action=clear.
 */
class QuickActionsBar {
  /**
   * @param {HTMLElement} barEl
   * @param {TerminalManager} terminalManager
   */
  constructor(barEl, terminalManager) {
    this.bar = barEl;
    this.tm = terminalManager;

    this._pendingAction = null;
    this._pendingTimeout = null;
    this._killLongPressTimer = null;
    this._toastTimer = null;

    this._rerunBtn = barEl.querySelector('#action-rerun');
    this._pauseBtn = barEl.querySelector('#action-pause');
    this._clearBtn = barEl.querySelector('#action-clear');
    this._killBtn  = barEl.querySelector('#action-kill');
    this._spinner  = barEl.querySelector('#action-spinner');
    this._toast    = barEl.querySelector('#action-toast');

    /** @type {((data: string) => void)|null} WS send function injected by App */
    this.sendWS = null;

    this._bind();
  }

  // --- Public API ---

  show() { this.bar.hidden = false; }
  hide() { this.bar.hidden = true; }
  setOffline() { this._setAllDisabled(true); }
  setOnline()  { this._setAllDisabled(false); }

  /**
   * Handle an incoming control_ack from the server.
   * @param {{ type:string, action:string, ok:boolean, reason?:string, code?:string }} msg
   */
  handleControlAck(msg) {
    if (msg.action !== this._pendingAction) return;
    this._clearPending();

    if (!msg.ok) {
      this._showToast(this._friendlyReason(msg));
    } else if (msg.action === 'clear') {
      // DoD #6: client clears xterm only after server ack (single source of truth).
      this.tm.clear();
    }
  }

  // --- Private ---

  _bind() {
    var self = this;

    this._rerunBtn.addEventListener('click', function () {
      self._sendControl('rerun', { type: 'rerun' });
    });

    this._pauseBtn.addEventListener('click', function () {
      self._sendControl('pause', { type: 'pause' });
    });

    this._clearBtn.addEventListener('click', function () {
      self._sendControl('clear', { type: 'clear' });
    });

    // Kill — touch: tap vs long-press
    this._killBtn.addEventListener('touchstart', function (e) {
      e.preventDefault();
      self._killLongPressTimer = setTimeout(function () {
        self._killLongPressTimer = null;
        self._confirmForceKill();
      }, 500);
    }, { passive: false });

    this._killBtn.addEventListener('touchend', function (e) {
      e.preventDefault();
      if (self._killLongPressTimer !== null) {
        clearTimeout(self._killLongPressTimer);
        self._killLongPressTimer = null;
        self._sendControl('kill', { type: 'kill', force: false });
      }
    }, { passive: false });

    this._killBtn.addEventListener('touchcancel', function () {
      clearTimeout(self._killLongPressTimer);
      self._killLongPressTimer = null;
    });

    // Kill — mouse fallback (desktop)
    this._killBtn.addEventListener('mousedown', function (e) {
      if (e.button !== 0) return;
      self._killLongPressTimer = setTimeout(function () {
        self._killLongPressTimer = null;
        self._confirmForceKill();
      }, 500);
    });
    this._killBtn.addEventListener('mouseup', function () {
      if (self._killLongPressTimer !== null) {
        clearTimeout(self._killLongPressTimer);
        self._killLongPressTimer = null;
        self._sendControl('kill', { type: 'kill', force: false });
      }
    });
    this._killBtn.addEventListener('mouseleave', function () {
      clearTimeout(self._killLongPressTimer);
      self._killLongPressTimer = null;
    });
  }

  _confirmForceKill() {
    // eslint-disable-next-line no-alert
    var confirmed = window.confirm(
      'Force-kill will send SIGKILL to the process.\nThis cannot be undone. Continue?'
    );
    if (confirmed) {
      this._sendControl('kill', { type: 'kill', force: true });
    }
  }

  _sendControl(action, payload) {
    if (this._pendingAction) return;
    if (!this.sendWS) { this._showToast('Not connected.'); return; }

    this._pendingAction = action;
    this._setAllDisabled(true);
    if (this._spinner) this._spinner.hidden = false;

    this.sendWS(JSON.stringify(payload));

    var self = this;
    this._pendingTimeout = setTimeout(function () {
      self._clearPending();
      self._showToast('No response from server.');
    }, 2000);
  }

  _clearPending() {
    clearTimeout(this._pendingTimeout);
    this._pendingTimeout = null;
    this._pendingAction = null;
    if (this._spinner) this._spinner.hidden = true;
    this._setAllDisabled(false);
  }

  _setAllDisabled(disabled) {
    var btns = [this._rerunBtn, this._pauseBtn, this._clearBtn, this._killBtn];
    btns.forEach(function (btn) {
      if (!btn) return;
      btn.disabled = disabled;
      if (disabled) { btn.setAttribute('aria-busy', 'true'); }
      else           { btn.removeAttribute('aria-busy'); }
    });
  }

  _showToast(message) {
    if (!this._toast) return;
    clearTimeout(this._toastTimer);
    this._toast.textContent = message;
    this._toast.classList.add('action-toast--visible');
    var self = this;
    this._toastTimer = setTimeout(function () {
      self._toast.classList.remove('action-toast--visible');
      self._toast.textContent = '';
    }, 3000);
  }

  _friendlyReason(msg) {
    var map = {
      not_owner:         'Read-only: session owned by another device.',
      rate_limited:      'Too many requests. Wait a moment.',
      SESSION_NOT_FOUND: 'Session not found.',
      NO_AGENT_SESSION:  'Re-run is only available for agent sessions.',
      NO_STORED_COMMAND: 'Cannot re-run: no stored command for this session.',
      RERUN_FAILED:      'Agent relaunch failed.',
      CLEAR_FAILED:      'Terminal view not available for clearing.',
    };
    return map[msg.code || ''] || (msg.reason || 'Action failed.');
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
  /** @type {QuickActionsBar} */
  let actionsBar;

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

    // Prompt input bar
    const promptInput = document.getElementById('prompt-input');
    const sendBtn = document.getElementById('input-send-btn');

    function sendPromptInput() {
      const text = promptInput.value;
      if (!text) return;
      // Blur keyboard proxy to prevent echo from virtual keyboard
      keyboardProxy.blur();
      // Send text and Enter separately — Claude Code TUI treats combined
      // text+\r as a paste and doesn't submit. Splitting with a small
      // delay ensures the TUI processes Enter as a keypress.
      terminalMgr.sendInput(text);
      setTimeout(function () {
        terminalMgr.sendInput('\r');
      }, 50);
      promptInput.value = '';
    }

    sendBtn.addEventListener('click', function () {
      sendPromptInput();
    });

    promptInput.addEventListener('keydown', function (e) {
      if (e.key === 'Enter') {
        e.preventDefault();
        sendPromptInput();
      }
    });

    // Agent play/stop buttons
    const agentPicker = document.getElementById('agent-picker');
    const agentPlayBtn = document.getElementById('agent-play-btn');
    const agentStopBtn = document.getElementById('agent-stop-btn');
    let agentRunning = false;

    agentPlayBtn.addEventListener('click', function () {
      const agent = agentPicker.value;
      client.startAssistant(agent).then(function (resp) {
        if (resp.ok) {
          agentRunning = true;
          agentPlayBtn.hidden = true;
          agentStopBtn.hidden = false;
          agentPicker.disabled = true;
        }
      });
    });

    agentStopBtn.addEventListener('click', function () {
      client.stopAssistant().then(function (resp) {
        if (resp.ok) {
          agentRunning = false;
          agentPlayBtn.hidden = false;
          agentStopBtn.hidden = true;
          agentPicker.disabled = false;
        }
      });
    });

    // Pickers
    projectPicker.addEventListener('change', handleProjectChange);
    sessionPicker.addEventListener('change', handleSessionChange);

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
    if (actionsBar) actionsBar.hide();
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
      actionsBar = new QuickActionsBar(
        document.getElementById('actions-bar'),
        terminalMgr
      );
      // Wire WS send closure — uses the ReconnectingWS inside terminalMgr.
      actionsBar.sendWS = function (data) {
        if (terminalMgr.ws) terminalMgr.ws.send(data);
      };
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

  function populateSessionPicker(sessions) {
    sessionPicker.innerHTML = '';

    if (!sessions || sessions.length === 0) {
      sessionPicker.innerHTML = '<option value="">No sessions</option>';
      return;
    }

    sessions.forEach(function (s) {
      const opt = document.createElement('option');
      opt.value = s.id;
      let label = s.title || 'Session';
      if (s.is_agent) label += ' [agent]';
      if (s.state === 'exited') label += ' (exited)';
      opt.textContent = label;
      sessionPicker.appendChild(opt);
    });

    // Auto-select first session and connect
    sessionPicker.value = sessions[0].id;
    connectToSession(sessions[0].id);
  }

  async function connectToSession(sessionId) {
    if (currentSessionId === sessionId && terminalMgr.ws) return;
    currentSessionId = sessionId;

    // Clear and load scrollback
    terminalMgr.clear();
    updateConnectionStatus('connecting');

    try {
      const scrollback = await client.getScrollback(currentProjectId, sessionId);
      if (scrollback && scrollback.content) {
        terminalMgr.writeScrollback(scrollback.content);
      }
    } catch (e) {
      if (e.status === 401) {
        client.clearToken();
        showPinScreen();
        return;
      }
      // Non-critical — terminal will work without scrollback
    }

    // Open WebSocket
    const token = client.getToken();
    if (token) {
      terminalMgr.connect(sessionId, token);
    }

    // Show the actions bar — owner-only (DoD: observer path handled below)
    if (actionsBar) actionsBar.show();
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
      case 4000: // auth_expired
        client.clearToken();
        showPinScreen();
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
          // Update session picker without losing current selection
          const prevSession = currentSessionId;
          populateSessionPicker(msg.sessions);
          // Re-select if still available
          const still = msg.sessions.find(function (s) { return s.id === prevSession; });
          if (still) {
            sessionPicker.value = prevSession;
            currentSessionId = prevSession;
          }
        }
        break;

      case 'control_ack':
        // Route to QuickActionsBar for button state / xterm clear sync (DoD #6).
        if (actionsBar) actionsBar.handleControlAck(msg);
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
        const deviceId = client.getDeviceId();
        if (msg.device_id === deviceId) {
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
        if (actionsBar) actionsBar.setOnline();
        break;
      case 'connecting':
        statusLabel.textContent = 'Connecting...';
        if (actionsBar) actionsBar.setOffline();
        break;
      case 'disconnected':
        statusLabel.textContent = 'Disconnected';
        if (actionsBar) actionsBar.setOffline();
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
