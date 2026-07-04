/**
 * help-chat widget v2 — embeddable AI help chat. Framework-agnostic.
 *
 * Ways to use it (pick one):
 *
 * 1. Plain HTML — one script tag, auto-initializes:
 *      <script src="https://your-cdn/chat.v2.js"
 *              data-app-key="demo"
 *              data-api-url="https://your-backend:8090"></script>
 *
 * 2. Declarative element — Angular / Vue / React 19+ templates:
 *      <help-chat app-key="demo" api-url="https://your-backend:8090"></help-chat>
 *    (load this file once via script tag or `import './chat.js'`)
 *
 * 3. Programmatic API — any SPA:
 *      HelpChat.init({ appKey: 'demo', apiUrl: 'https://...', user: {...} });
 *      HelpChat.open(); HelpChat.send('How do I reset my password?');
 *      HelpChat.on('helpchat:message', e => console.log(e.detail));
 *      HelpChat.destroy();
 *
 * Element attributes:
 *   app-key   (required)  application key registered in the backend
 *   api-url   (required)  backend base URL
 *   mode      "bubble" (default) | "fullscreen" (mobile WebView)
 *   position  "right" (default) | "left"
 *
 * Events (bubble up from the element, composed):
 *   helpchat:ready    detail: appConfig      config loaded, widget usable
 *   helpchat:open / helpchat:close
 *   helpchat:message  detail: {role, text}   a message was added
 *   helpchat:error    detail: {message}
 *
 * No dependencies. Shadow DOM, so host app CSS never conflicts.
 */
(function (global) {
  'use strict';

  // Idempotent: safe if the script is loaded twice (SPA route changes, HMR).
  if (global.customElements && global.customElements.get('help-chat')) return;

  var DEFAULTS = {
    appKey: '',
    apiUrl: '',
    mode: 'bubble',       // 'bubble' | 'fullscreen'
    position: 'right',    // 'right' | 'left'
    user: null,           // {id, name, email, ...} sent with each message
    context: null,        // free-form object sent with each message
    headers: null         // object OR function returning object (e.g. auth token)
  };

  function newSessionId() {
    return 'S' + Date.now() + Math.random().toString(36).slice(2, 10);
  }

  /** Stable per-browser session id, one per appKey. */
  function getSessionId(appKey) {
    var key = 'helpchat_sid_' + appKey;
    try {
      var sid = localStorage.getItem(key);
      if (!sid) {
        sid = newSessionId();
        localStorage.setItem(key, sid);
      }
      return sid;
    } catch (_) {
      return newSessionId();
    }
  }

  class HelpChatElement extends HTMLElement {
    static get observedAttributes() { return ['app-key', 'api-url', 'mode', 'position']; }

    constructor() {
      super();
      this.attachShadow({ mode: 'open' });
      this._opts = Object.assign({}, DEFAULTS); // programmatic overrides
      this._config = null;                      // backend app config
      this._busy = false;
      this._abort = null;
      this._connected = false;
    }

    /** attributes win over programmatic opts, which win over defaults */
    get opts() {
      var o = Object.assign({}, this._opts);
      var attr = this.getAttribute.bind(this);
      if (attr('app-key'))  o.appKey  = attr('app-key');
      if (attr('api-url'))  o.apiUrl  = attr('api-url').replace(/\/$/, '');
      if (attr('mode'))     o.mode    = attr('mode');
      if (attr('position')) o.position = attr('position');
      return o;
    }

    configure(opts) {
      Object.assign(this._opts, opts || {});
      if (opts && opts.apiUrl) this._opts.apiUrl = String(opts.apiUrl).replace(/\/$/, '');
      if (this._connected) this._bootstrap();
      return this;
    }

    identify(user) { this._opts.user = user; }
    setContext(ctx) { this._opts.context = ctx; }

    connectedCallback() {
      this._connected = true;
      this._render();
      this._bootstrap();
    }

    disconnectedCallback() {
      this._connected = false;
      if (this._abort) this._abort.abort();
    }

    attributeChangedCallback(name, oldV, newV) {
      if (!this._connected || oldV === newV) return;
      if (name === 'app-key' || name === 'api-url') this._bootstrap();
      if (name === 'mode') this._applyMode();
    }

    // ---------- public element API ----------

    open() {
      this._panel().classList.add('open');
      this._emit('helpchat:open');
      var input = this.$('.hc-input');
      if (input) input.focus();
    }

    close() {
      this._panel().classList.remove('open');
      this._emit('helpchat:close');
      // Inside a native WebView the host may want to dismiss the screen.
      if (this.opts.mode === 'fullscreen' && global.HelpChatNative && global.HelpChatNative.close) {
        global.HelpChatNative.close();
      }
    }

    toggle() {
      this._panel().classList.contains('open') ? this.close() : this.open();
    }

    get isOpen() { return this._panel().classList.contains('open'); }

    resetSession() {
      try { localStorage.removeItem('helpchat_sid_' + this.opts.appKey); } catch (_) {}
      this._bootstrap();
    }

    // ---------- internals ----------

    $(sel) { return this.shadowRoot.querySelector(sel); }
    _panel() { return this.$('.hc-panel'); }

    _emit(name, detail) {
      this.dispatchEvent(new CustomEvent(name, { detail: detail, bubbles: true, composed: true }));
    }

    _bootstrap() {
      var o = this.opts;
      if (!o.appKey || !o.apiUrl) return; // wait until configured
      this._sessionId = getSessionId(o.appKey);
      this.$('.hc-msgs').innerHTML = '';
      this.$('.hc-chips').innerHTML = '';
      this._loadConfig();
      this._applyMode();
    }

    async _loadConfig() {
      var o = this.opts;
      try {
        var res = await fetch(o.apiUrl + '/chat/config/' + encodeURIComponent(o.appKey),
                              { headers: this._headers(false) });
        if (!res.ok) throw new Error('config ' + res.status);
        this._config = await res.json();
        this._applyConfig();
        this._emit('helpchat:ready', this._config);
      } catch (e) {
        console.error('[help-chat] failed to load config:', e);
        this._emit('helpchat:error', { message: 'config-load-failed' });
      }
    }

    _applyConfig() {
      var c = this._config;
      this.style.setProperty('--hc-primary', c.themeColor || '#0d7377');
      this.$('.hc-title').textContent = c.appName ? c.appName + ' Help' : 'Help';
      this._addMessage('assistant', c.welcomeMessage || 'Hi! How can I help?');
      var chips = this.$('.hc-chips');
      var self = this;
      (c.suggestedQuestions || []).forEach(function (q) {
        var b = document.createElement('button');
        b.className = 'hc-chip';
        b.textContent = q;
        b.onclick = function () { self.send(q); };
        chips.appendChild(b);
      });
    }

    _applyMode() {
      if (this.opts.mode === 'fullscreen') {
        this.setAttribute('data-fullscreen', '');
        this._panel().classList.add('open');
      } else {
        this.removeAttribute('data-fullscreen');
      }
    }

    _headers(json) {
      var h = json ? { 'Content-Type': 'application/json' } : {};
      var extra = this.opts.headers;
      if (typeof extra === 'function') extra = extra();
      return Object.assign(h, extra || {});
    }

    _render() {
      this.shadowRoot.innerHTML = '\n' +
        '<style>\n' +
        '  :host {\n' +
        '    --hc-primary: #0d7377;\n' +
        '    --hc-bg: #ffffff;\n' +
        '    --hc-ink: #1c2528;\n' +
        '    --hc-mut: #67757a;\n' +
        '    --hc-line: #e3e9ea;\n' +
        '    all: initial;\n' +
        '    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;\n' +
        '    position: fixed; z-index: 2147483000;\n' +
        '  }\n' +
        '  * { box-sizing: border-box; }\n' +
        '\n' +
        '  .hc-bubble {\n' +
        '    position: fixed; right: 20px; bottom: 20px;\n' +
        '    width: 56px; height: 56px; border-radius: 50%;\n' +
        '    background: var(--hc-primary); color: #fff; border: none;\n' +
        '    cursor: pointer; box-shadow: 0 6px 20px rgba(0,0,0,.22);\n' +
        '    display: flex; align-items: center; justify-content: center;\n' +
        '    transition: transform .15s ease;\n' +
        '  }\n' +
        '  .hc-bubble:hover { transform: scale(1.06); }\n' +
        '  .hc-bubble svg { width: 26px; height: 26px; fill: none; stroke: #fff; stroke-width: 2; }\n' +
        '\n' +
        '  .hc-panel {\n' +
        '    position: fixed; right: 20px; bottom: 88px;\n' +
        '    width: 372px; height: min(600px, calc(100vh - 110px));\n' +
        '    background: var(--hc-bg); border-radius: 16px;\n' +
        '    box-shadow: 0 12px 40px rgba(0,0,0,.24);\n' +
        '    display: none; flex-direction: column; overflow: hidden;\n' +
        '  }\n' +
        '  .hc-panel.open { display: flex; }\n' +
        '\n' +
        '  :host([position="left"]) .hc-bubble { left: 20px; right: auto; }\n' +
        '  :host([position="left"]) .hc-panel  { left: 20px; right: auto; }\n' +
        '\n' +
        '  .hc-head {\n' +
        '    background: var(--hc-primary); color: #fff;\n' +
        '    padding: 14px 16px; display: flex; align-items: center; gap: 10px;\n' +
        '  }\n' +
        '  .hc-title { font-size: 15px; font-weight: 600; flex: 1; }\n' +
        '  .hc-close {\n' +
        '    background: transparent; border: none; color: #fff;\n' +
        '    font-size: 20px; cursor: pointer; line-height: 1; padding: 4px;\n' +
        '  }\n' +
        '\n' +
        '  .hc-msgs {\n' +
        '    flex: 1; overflow-y: auto; padding: 16px;\n' +
        '    display: flex; flex-direction: column; gap: 10px;\n' +
        '  }\n' +
        '  .hc-msg {\n' +
        '    max-width: 84%; padding: 10px 13px; border-radius: 14px;\n' +
        '    font-size: 14px; line-height: 1.45; white-space: pre-wrap;\n' +
        '    word-wrap: break-word;\n' +
        '  }\n' +
        '  .hc-msg.user {\n' +
        '    align-self: flex-end; background: var(--hc-primary); color: #fff;\n' +
        '    border-bottom-right-radius: 4px;\n' +
        '  }\n' +
        '  .hc-msg.assistant {\n' +
        '    align-self: flex-start; background: #f1f5f5; color: var(--hc-ink);\n' +
        '    border-bottom-left-radius: 4px;\n' +
        '  }\n' +
        '  .hc-msg a {\n' +
        '    color: var(--hc-primary); text-decoration: underline;\n' +
        '    word-break: break-all;\n' +
        '  }\n' +
        '  .hc-msg.typing::after {\n' +
        '    content: \'\\25CF\\25CF\\25CF\'; letter-spacing: 3px; color: var(--hc-mut);\n' +
        '    animation: hcPulse 1.2s infinite;\n' +
        '  }\n' +
        '  @keyframes hcPulse { 0%,100% { opacity: .3 } 50% { opacity: 1 } }\n' +
        '  @media (prefers-reduced-motion: reduce) {\n' +
        '    .hc-msg.typing::after { animation: none; }\n' +
        '    .hc-bubble { transition: none; }\n' +
        '  }\n' +
        '\n' +
        '  .hc-chips { display: flex; flex-wrap: wrap; gap: 6px; padding: 0 16px 8px; }\n' +
        '  .hc-chip {\n' +
        '    border: 1px solid var(--hc-line); background: #fff; color: var(--hc-ink);\n' +
        '    border-radius: 999px; padding: 6px 12px; font-size: 12.5px; cursor: pointer;\n' +
        '  }\n' +
        '  .hc-chip:hover { border-color: var(--hc-primary); color: var(--hc-primary); }\n' +
        '\n' +
        '  .hc-inputrow {\n' +
        '    display: flex; gap: 8px; padding: 12px 14px;\n' +
        '    border-top: 1px solid var(--hc-line);\n' +
        '  }\n' +
        '  .hc-input {\n' +
        '    flex: 1; border: 1px solid var(--hc-line); border-radius: 10px;\n' +
        '    padding: 10px 12px; font-size: 14px; font-family: inherit;\n' +
        '    outline: none; resize: none; max-height: 96px; color: var(--hc-ink);\n' +
        '  }\n' +
        '  .hc-input:focus { border-color: var(--hc-primary); }\n' +
        '  .hc-send {\n' +
        '    background: var(--hc-primary); color: #fff; border: none;\n' +
        '    border-radius: 10px; width: 42px; cursor: pointer;\n' +
        '    display: flex; align-items: center; justify-content: center;\n' +
        '  }\n' +
        '  .hc-send:disabled { opacity: .5; cursor: default; }\n' +
        '  .hc-send svg { width: 18px; height: 18px; fill: #fff; }\n' +
        '\n' +
        '  /* Mobile: panel goes full screen on small viewports */\n' +
        '  @media (max-width: 480px) {\n' +
        '    .hc-panel {\n' +
        '      right: 0; bottom: 0; width: 100vw;\n' +
        '      height: 100dvh; border-radius: 0;\n' +
        '    }\n' +
        '  }\n' +
        '  :host([data-fullscreen]) .hc-panel {\n' +
        '    right: 0; left: 0; bottom: 0; width: 100vw; height: 100dvh; border-radius: 0;\n' +
        '  }\n' +
        '  :host([data-fullscreen]) .hc-bubble { display: none; }\n' +
        '</style>\n' +
        '\n' +
        '<button class="hc-bubble" aria-label="Open help chat">\n' +
        '  <svg viewBox="0 0 24 24"><path d="M21 12a8 8 0 0 1-8 8H5l-2 2V12a8 8 0 0 1 8-8h2a8 8 0 0 1 8 8z"\n' +
        '    stroke-linecap="round" stroke-linejoin="round"/></svg>\n' +
        '</button>\n' +
        '\n' +
        '<div class="hc-panel" role="dialog" aria-label="Help chat">\n' +
        '  <div class="hc-head">\n' +
        '    <span class="hc-title">Help</span>\n' +
        '    <button class="hc-close" aria-label="Close">✕</button>\n' +
        '  </div>\n' +
        '  <div class="hc-msgs"></div>\n' +
        '  <div class="hc-chips"></div>\n' +
        '  <div class="hc-inputrow">\n' +
        '    <textarea class="hc-input" rows="1" placeholder="Ask a question…"></textarea>\n' +
        '    <button class="hc-send" aria-label="Send">\n' +
        '      <svg viewBox="0 0 24 24"><path d="M2 21l21-9L2 3v7l15 2-15 2z"/></svg>\n' +
        '    </button>\n' +
        '  </div>\n' +
        '</div>\n';

      var self = this;
      this.$('.hc-bubble').onclick = function () { self.toggle(); };
      this.$('.hc-close').onclick = function () { self.close(); };
      this.$('.hc-send').onclick = function () { self.send(); };
      this.$('.hc-input').addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); self.send(); }
      });
    }

    _addMessage(role, text) {
      var div = document.createElement('div');
      div.className = 'hc-msg ' + role;
      div.textContent = text;
      if (role === 'assistant' && text) this._linkify(div);
      this.$('.hc-msgs').appendChild(div);
      this._scrollDown();
      if (text) this._emit('helpchat:message', { role: role, text: text });
      return div;
    }

    /**
     * Make URLs in an assistant message clickable (docs can link to guides,
     * forms, PDFs, downloads...). Builds DOM nodes — never injects HTML, so
     * message content can't break out of the bubble.
     */
    _linkify(el) {
      var text = el.textContent;
      if (!/https?:\/\//.test(text)) return;
      el.textContent = '';
      var re = /(https?:\/\/[^\s<>"')\]]+)/g;
      var last = 0, m;
      while ((m = re.exec(text)) !== null) {
        if (m.index > last) el.appendChild(document.createTextNode(text.slice(last, m.index)));
        var a = document.createElement('a');
        a.href = m[0];
        a.textContent = m[0];
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        el.appendChild(a);
        last = m.index + m[0].length;
      }
      if (last < text.length) el.appendChild(document.createTextNode(text.slice(last)));
    }

    _scrollDown() {
      var m = this.$('.hc-msgs');
      m.scrollTop = m.scrollHeight;
    }

    async send(text) {
      var input = this.$('.hc-input');
      var message = (text != null ? text : input.value).trim();
      if (!message || this._busy) return;
      var o = this.opts;
      if (!o.appKey || !o.apiUrl) return;

      input.value = '';
      this.$('.hc-chips').innerHTML = '';
      this._busy = true;
      this.$('.hc-send').disabled = true;

      this._addMessage('user', message);
      var reply = this._addMessage('assistant', '');
      reply.classList.add('typing');
      this._abort = new AbortController();

      try {
        var body = { appKey: o.appKey, sessionId: this._sessionId, message: message };
        if (o.user) body.user = o.user;
        if (o.context) body.context = o.context;

        var res = await fetch(o.apiUrl + '/chat/message', {
          method: 'POST',
          headers: this._headers(true),
          body: JSON.stringify(body),
          signal: this._abort.signal
        });
        if (!res.ok || !res.body) throw new Error('HTTP ' + res.status);

        var reader = res.body.getReader();
        var decoder = new TextDecoder();
        var buffer = '', eventName = '';

        while (true) {
          var chunk = await reader.read();
          if (chunk.done) break;
          buffer += decoder.decode(chunk.value, { stream: true });

          var idx;
          while ((idx = buffer.indexOf('\n')) >= 0) {
            var line = buffer.slice(0, idx).replace(/\r$/, '');
            buffer = buffer.slice(idx + 1);
            if (line.indexOf('event:') === 0) {
              eventName = line.slice(6).trim();
            } else if (line.indexOf('data:') === 0) {
              var data = line.slice(5).replace(/^ /, '');
              if (eventName === 'delta') {
                reply.classList.remove('typing');
                reply.textContent += data;
                this._scrollDown();
              } else if (eventName === 'error') {
                reply.classList.remove('typing');
                reply.textContent = data || 'Something went wrong. Please try again.';
                this._emit('helpchat:error', { message: reply.textContent });
              }
            }
          }
        }
        if (reply.classList.contains('typing')) {
          reply.classList.remove('typing');
          reply.textContent = 'No response received. Please try again.';
        } else if (reply.textContent) {
          this._linkify(reply);
          this._emit('helpchat:message', { role: 'assistant', text: reply.textContent });
        }
      } catch (e) {
        reply.classList.remove('typing');
        if (e.name !== 'AbortError') {
          reply.textContent = 'Could not reach the help service. Please try again.';
          this._emit('helpchat:error', { message: 'network' });
          console.error('[help-chat]', e);
        }
      } finally {
        this._busy = false;
        this._abort = null;
        this.$('.hc-send').disabled = false;
        input.focus();
      }
    }
  }

  customElements.define('help-chat', HelpChatElement);

  // ---------- global programmatic API (singleton convenience) ----------

  var HelpChat = {
    _el: null,

    /** Create (or reconfigure) the singleton widget. Returns the element. */
    init: function (opts) {
      if (!this._el || !this._el.isConnected) {
        this._el = document.querySelector('help-chat') || document.createElement('help-chat');
      }
      this._el.configure(opts);
      if (!this._el.isConnected) document.body.appendChild(this._el);
      return this._el;
    },

    get element() { return this._el; },

    open: function () { if (this._el) this._el.open(); },
    close: function () { if (this._el) this._el.close(); },
    toggle: function () { if (this._el) this._el.toggle(); },
    send: function (text) { if (this._el) this._el.send(text); },
    identify: function (user) { if (this._el) this._el.identify(user); },
    setContext: function (ctx) { if (this._el) this._el.setContext(ctx); },
    resetSession: function () { if (this._el) this._el.resetSession(); },

    /** Listen to helpchat:* events. Returns an unsubscribe function.
     *  'helpchat:ready' fires immediately if the config is already loaded,
     *  so late subscribers never miss it. */
    on: function (event, cb) {
      var target = this._el || document;
      target.addEventListener(event, cb);
      if (event === 'helpchat:ready' && this._el && this._el._config) {
        var detail = this._el._config;
        setTimeout(function () { cb({ type: 'helpchat:ready', detail: detail }); }, 0);
      }
      return function () { target.removeEventListener(event, cb); };
    },

    /** Remove the widget from the page entirely. */
    destroy: function () {
      if (this._el && this._el.isConnected) this._el.remove();
      this._el = null;
    }
  };

  global.HelpChat = HelpChat;

  // ---------- auto-init (backward compatible) ----------
  // Config sources, merged in this order (later wins):
  //   1. window.HelpChatConfig — set it in a tiny env-specific config script
  //      BEFORE loading chat.js, so this file never changes per environment:
  //        <script>window.HelpChatConfig = { appKey:'myapp', apiUrl:'https://...' };</script>
  //        <script src="chat.js"></script>
  //   2. data-* attributes on the <script> tag that loaded chat.js.
  // If neither provides an appKey (e.g. chat.js is bundled/imported by a
  // framework), nothing happens until the app calls HelpChat.init() or
  // renders <help-chat> itself.

  var script = document.currentScript;
  var cfg = Object.assign({}, global.HelpChatConfig || {});
  if (script && script.dataset) {
    if (script.dataset.appKey)   cfg.appKey   = script.dataset.appKey;
    if (script.dataset.apiUrl)   cfg.apiUrl   = script.dataset.apiUrl;
    if (script.dataset.mode)     cfg.mode     = script.dataset.mode;
    if (script.dataset.position) cfg.position = script.dataset.position;
  }
  if (cfg.appKey) {
    var boot = function () {
      cfg.apiUrl = cfg.apiUrl || 'http://localhost:8090';
      HelpChat.init(cfg);
      var el = HelpChat.element;
      if (cfg.mode) el.setAttribute('mode', cfg.mode);
      if (cfg.position) el.setAttribute('position', cfg.position);
    };
    if (document.body) boot();
    else document.addEventListener('DOMContentLoaded', boot);
  }
})(typeof window !== 'undefined' ? window : this);
