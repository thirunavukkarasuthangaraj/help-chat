# Plugging help-chat into any application

One widget (`chat.js`), five ways to plug it in. All of them talk to the same
Spring Boot backend; the only per-app difference is the `appKey`.

| Host | How | Files |
|---|---|---|
| Plain HTML / static site | script tag, auto-init | just `chat.js` |
| React (16.8+) | `<HelpChatWidget />` component | `react/HelpChatWidget.jsx` |
| Angular (12+) | `HelpChatService` or custom element | `angular/help-chat.service.ts` |
| Vue / Svelte / anything | custom element or `HelpChat` API | just `chat.js` |
| Hybrid mobile (WebView) | fullscreen mode + native bridge | just `chat.js` |

---

## 1. Plain HTML / static site

One line before `</body>`:

```html
<script src="https://your-cdn/chat.v2.js"
        data-app-key="myapp"
        data-api-url="https://chat.yourdomain.com"></script>
```

Optional: `data-mode="fullscreen"`, `data-position="left"`.

**Environment-specific config without touching chat.js** — define
`window.HelpChatConfig` before loading it (e.g. from a per-environment
`config.js` your deploy pipeline writes):

```html
<script>window.HelpChatConfig = { appKey: 'myapp', apiUrl: 'https://chat.prod.yourdomain.com' };</script>
<script src="https://your-cdn/chat.v2.js"></script>
```

Same idea works for dev/QA/prod: ship identical chat.js everywhere, only the
one-line config script differs.

## 2. React

Copy `react/HelpChatWidget.jsx` into your app, serve `chat.js` from
`public/` (or your CDN), then:

```jsx
<HelpChatWidget
  appKey="myapp"
  apiUrl="https://chat.yourdomain.com"
  user={{ id: user.id, name: user.name }}
  headers={() => ({ Authorization: `Bearer ${getToken()}` })}
  onMessage={(m) => analytics.track('help_chat', m)}
/>
```

React 19+ can skip the wrapper and render the custom element directly:

```jsx
import './chat.js';
<help-chat app-key="myapp" api-url="https://chat.yourdomain.com" />
```

## 3. Angular

Copy `angular/help-chat.service.ts` into your app and `chat.js` into
`src/assets/`. Then from any component (e.g. `AppComponent`):

```ts
constructor(private helpChat: HelpChatService) {}

ngOnInit() {
  this.helpChat.init({
    appKey: 'myapp',
    apiUrl: 'https://chat.yourdomain.com',
    user: { id: this.auth.userId },
    headers: () => ({ Authorization: 'Bearer ' + this.auth.token }),
  });
}
```

Declarative alternative — add `CUSTOM_ELEMENTS_SCHEMA` to your module or
standalone component, load `chat.js` via `angular.json` `"scripts"`, and put
this in a template:

```html
<help-chat app-key="myapp" api-url="https://chat.yourdomain.com"></help-chat>
```

## 4. Vue / Svelte / any other framework

`chat.js` defines a standard custom element, so:

```html
<help-chat app-key="myapp" api-url="https://chat.yourdomain.com"></help-chat>
```

(Vue: add `help-chat` to `compilerOptions.isCustomElement`.) Or purely with JS:

```js
import './chat.js';
window.HelpChat.init({ appKey: 'myapp', apiUrl: 'https://chat.yourdomain.com' });
```

## 5. Hybrid mobile (Android WebView / iOS WKWebView / Ionic / Capacitor)

Host a tiny page (or bundle it in the app):

```html
<script src="https://your-cdn/chat.v2.js"
        data-app-key="myapp-mobile"
        data-api-url="https://chat.yourdomain.com"
        data-mode="fullscreen"></script>
```

The widget opens full screen with no bubble. Its ✕ button calls
`window.HelpChatNative.close()` if your app provides it:

**Android (Kotlin):**

```kotlin
webView.settings.javaScriptEnabled = true
webView.settings.domStorageEnabled = true   // needed for the session id
webView.addJavascriptInterface(object {
    @JavascriptInterface
    fun close() = runOnUiThread { finish() }
}, "HelpChatNative")
webView.loadUrl("https://chat.yourdomain.com/mobile.html")
```

**iOS (Swift, WKWebView):**

```swift
// Inject a JS shim that forwards close() to a script message handler
let shim = "window.HelpChatNative = { close: () => window.webkit.messageHandlers.helpChatClose.postMessage('') };"
let userScript = WKUserScript(source: shim, injectionTime: .atDocumentStart, forMainFrameOnly: true)
config.userContentController.addUserScript(userScript)
config.userContentController.add(self, name: "helpChatClose")

func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
    if m.name == "helpChatClose" { dismiss(animated: true) }
}
```

**Ionic / Capacitor:** it's just a web app — use the React or Angular
integration above directly; no WebView bridge needed.

---

## JS API reference (`window.HelpChat`)

| Method | Purpose |
|---|---|
| `init(opts)` | create/reconfigure the widget; returns the element |
| `open()` / `close()` / `toggle()` | control the panel |
| `send(text)` | programmatically send a message |
| `identify(user)` | attach user info to every message payload |
| `setContext(ctx)` | attach app context (current page, plan, locale…) |
| `resetSession()` | new conversation (clears the stored session id) |
| `on(event, cb)` | listen to `helpchat:*` events; returns unsubscribe fn |
| `destroy()` | remove the widget entirely |

`init` options: `appKey`, `apiUrl`, `mode` (`bubble`/`fullscreen`),
`position` (`right`/`left`), `user`, `context`, `headers` (object or function
— use a function for tokens that refresh).

## Events

All bubble up from the `<help-chat>` element (composed, so they cross the
Shadow DOM):

| Event | `detail` |
|---|---|
| `helpchat:ready` | app config from the backend |
| `helpchat:open` / `helpchat:close` | — |
| `helpchat:message` | `{ role: 'user'\|'assistant', text }` |
| `helpchat:error` | `{ message }` |
