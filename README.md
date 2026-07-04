# help-chat — lightweight, reusable AI help chat

One backend + one embeddable widget that any of your applications (web or mobile)
can reuse. Each app gets its own theme, welcome message, and knowledge base via
a single `appKey` config entry.

```
Web app      → <script> tag (Web Component) ─┐
Mobile app   → WebView (fullscreen mode)     ├→ Spring Boot chat-service → Claude API + docs RAG
Any other UI → same REST/SSE API             ─┘
```

## Folder structure

```
help-chat/
├── backend/                 Spring Boot 3 service (Java 17)
│   └── src/main/
│       ├── java/com/helpchat/
│       │   ├── controller/ChatController.java   REST + SSE endpoints
│       │   ├── service/ChatService.java         orchestration per turn
│       │   ├── service/ClaudeClient.java        Anthropic Messages API (streaming)
│       │   ├── service/DocsRetriever.java       simple keyword RAG (swap → OpenSearch)
│       │   ├── store/AppConfigStore.java        multi-app registry (swap → DynamoDB)
│       │   └── store/SessionStore.java          chat history, 24h TTL (swap → DynamoDB)
│       └── resources/
│           ├── application.yml
│           └── docs/demo.md                     sample help documentation
└── widget/
    ├── chat.js              embeddable widget (no dependencies, Shadow DOM)
    ├── demo.html            test page — a fake "host application"
    └── integrations/
        ├── README.md                    how to plug into every host type
        ├── react/HelpChatWidget.jsx     drop-in React component
        └── angular/help-chat.service.ts drop-in Angular service
```

## Run it (5 minutes)

Prerequisites: Java 17+, Maven, an Anthropic API key (console.anthropic.com).

**1. Start the backend**

```powershell
cd backend
$env:ANTHROPIC_API_KEY = "sk-ant-..."     # PowerShell (Windows)
mvn spring-boot:run
```

(Linux/macOS: `export ANTHROPIC_API_KEY=sk-ant-...`)

Backend runs at http://localhost:8090. Quick check:
http://localhost:8090/chat/config/demo should return JSON.

**2. Open the widget demo**

```powershell
cd widget
python -m http.server 3000     # or any static server
```

Open http://localhost:3000/demo.html, click the teal bubble, and ask
"How do I reset my password?" — the answer streams in from Claude using
the docs in `backend/src/main/resources/docs/demo.md`.

## Add the widget to YOUR app

Full per-framework guide (React, Angular, Vue, Android/iOS WebView, JS API,
events): **[widget/integrations/README.md](widget/integrations/README.md)**. Short version:

**Plain HTML** — one line before `</body>`:

```html
<script src="https://your-cdn/chat.v2.js"
        data-app-key="myapp"
        data-api-url="https://chat.yourdomain.com"></script>
```

**React** — copy `widget/integrations/react/HelpChatWidget.jsx`:

```jsx
<HelpChatWidget appKey="myapp" apiUrl="https://chat.yourdomain.com" />
```

**Angular** — copy `widget/integrations/angular/help-chat.service.ts`:

```ts
this.helpChat.init({ appKey: 'myapp', apiUrl: 'https://chat.yourdomain.com' });
```

**Any other framework** — `chat.js` is a standard custom element:

```html
<help-chat app-key="myapp" api-url="https://chat.yourdomain.com"></help-chat>
```

**Mobile app (hybrid/WebView)** — same widget, fullscreen mode:

```html
<script src="https://your-cdn/chat.v2.js"
        data-app-key="myapp-mobile"
        data-api-url="https://chat.yourdomain.com"
        data-mode="fullscreen"></script>
```

Optional: expose `window.HelpChatNative.close()` from your native bridge
(JavascriptInterface on Android / WKScriptMessageHandler on iOS) so the
widget's ✕ button can close the WebView screen — snippets in the
integrations guide.

**Programmatic control** (any host): `HelpChat.open() / send(text) /
identify(user) / setContext(ctx) / on('helpchat:message', cb) / destroy()`.

## Onboard a new application

1. Add an entry in `AppConfigStore.java` (appKey, name, theme color, welcome
   message, suggested questions, system prompt, docs file).
2. Add its help docs as `resources/docs/<appkey>.md` using `## Heading` sections.
3. Embed the script tag with that `data-app-key`. Done.

## API reference

| Endpoint | Method | Purpose |
|---|---|---|
| `/chat/config/{appKey}` | GET | Widget bootstrap: theme, welcome, suggested questions |
| `/chat/message` | POST | Body `{appKey, sessionId, message}` → SSE stream of `delta` events, ending with `done` |

## Production hardening checklist

- [ ] Replace `AppConfigStore` map with DynamoDB table `chat_apps`
- [ ] Replace `SessionStore` map with DynamoDB table `chat_sessions` (TTL attribute)
- [ ] Replace `DocsRetriever` keyword matching with OpenSearch hybrid (BM25 + embeddings), one index per appKey
- [ ] Set `helpchat.allowed-origins` to your real domains (remove `*`)
- [ ] Add rate limiting per sessionId/IP (e.g., bucket4j or API Gateway)
- [ ] Serve `chat.js` from CDN as versioned `chat.v1.js`
- [ ] Add 👍/👎 feedback logging and an "unanswered questions" log
