# help-chat — lightweight, reusable help chat

One backend + one embeddable widget that any of your applications (web or mobile)
can reuse. Each app gets its own theme, welcome message, and knowledge base via
a single `appKey` config entry.

**No AI required.** By default the backend answers straight from each app's
help-docs file — no external services, no API keys, zero cost. AI answers
(Claude) are an optional engine you can switch on later with one config value.

```
Web app      → <script> tag (Web Component) ─┐
Mobile app   → WebView (fullscreen mode)     ├→ Spring Boot chat-service → answer engine
Any other UI → same REST/SSE API             ─┘        │
                                          docs (default, no AI)  or  claude (optional)
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
├── widget/
│   ├── chat.js              embeddable widget (no dependencies, Shadow DOM)
│   ├── demo.html            test page — a fake "host application"
│   └── integrations/
│       ├── README.md                    how to plug into every host type
│       ├── react/HelpChatWidget.jsx     drop-in React component
│       └── angular/help-chat.service.ts drop-in Angular service
└── scripts/
    ├── start-dev.ps1 / .sh  start backend + widget demo together
    ├── new-app.ps1          scaffold a new application (docs + config snippet)
    └── db/
        ├── schema.sql                   MySQL tables (apps, messages, feedback)
        └── dynamodb-create-tables.sh    DynamoDB tables + 24h TTL (AWS)
```

## Run it (5 minutes)

Prerequisites: Java 17+ and Maven. That's all — no API keys needed.

**1. Start the backend**

```powershell
cd backend
mvn spring-boot:run
```

Backend runs at http://localhost:8090. Quick check:
http://localhost:8090/chat/config/demo should return JSON.

**2. Open the widget demo**

```powershell
cd widget
python -m http.server 3000     # or any static server
```

Open http://localhost:3000/demo.html, click the teal bubble, and ask
"How do I reset my password?" — the answer comes from the matching section
of `backend/src/main/resources/docs/demo.md`.

## Answer engines (helpchat.provider)

| Engine | Config | What it does |
|---|---|---|
| `docs` (default) | nothing to set | Matches the question against the app's help-docs sections and replies with the best ones. FAQ-style, free, fully offline. |
| `claude` (optional) | `HELPCHAT_PROVIDER=claude` + `ANTHROPIC_API_KEY=sk-ant-...` | AI-generated conversational answers grounded in the same docs. |

Your own engine: implement `AnswerProvider` (one method) — e.g. call your
existing FAQ service or database — and select it in `ChatService`. The widget
and API don't change.

## Storage backends (helpchat.storage)

Every deployment can use its own database — pick one with a single env var;
nothing else changes:

| Backend | Config | Best for |
|---|---|---|
| `memory` (default) | nothing to set | dev/test, small sites. History lost on restart. Apps registered in `InMemoryAppConfigStore`. |
| `jdbc` | `HELPCHAT_STORAGE=jdbc`, `HELPCHAT_DB_URL=jdbc:mysql://host/helpchat` (or `jdbc:postgresql://...`), `HELPCHAT_DB_USER`, `HELPCHAT_DB_PASSWORD` | clients with MySQL / PostgreSQL / MariaDB. Run `scripts/db/schema.sql` once. Onboard apps by INSERTing a row — no rebuild, no restart. |
| `dynamodb` | `HELPCHAT_STORAGE=dynamodb` + standard AWS credentials/region | AWS deployments. Run `scripts/db/dynamodb-create-tables.sh` once. Native TTL cleans up history automatically. |

With `jdbc`/`dynamodb`, chat history survives restarts and app onboarding is a
database row instead of a code change. Help-docs files can also live **outside
the jar**: drop `myapp.md` into the folder set by `HELPCHAT_DOCS_DIR`
(default `./docs` next to the jar) — edit answers anytime without rebuilding.

## Integration — clean steps

Adding help chat to any application is always the same 3 phases:

```
PHASE A (once)         PHASE B (per application)        PHASE C (in the app)
run the backend   →    register appKey + write docs  →  embed the widget
```

### Phase A — run the backend (once, shared by all apps)

1. `cd backend`
2. `mvn spring-boot:run`
3. Verify: open http://localhost:8090/chat/config/demo — you should see JSON.

In production, deploy this service once (EC2/ECS/etc.) and point every app at
its URL, e.g. `https://chat.yourdomain.com`.

### Phase B — register your application (per app, backend side)

1. Open `backend/src/main/java/com/helpchat/store/AppConfigStore.java` and add
   one entry: `appKey`, app name, theme color, welcome message, suggested
   questions, system prompt, docs file name.
2. Create `backend/src/main/resources/docs/<appkey>.md`. Each `## Heading`
   section is one answer — write headings the way users would ask:

   ```markdown
   ## How do I reset my password?
   Click "Forgot password" on the sign-in page...

   ## What are the pricing plans?
   We offer three plans...
   ```

3. Restart the backend. Verify: http://localhost:8090/chat/config/<appkey>

### Phase C — embed the widget (per app, frontend side)

Pick the ONE that matches your app. In every case you only need two values:
your `appKey` (from Phase B) and the backend URL (from Phase A).

#### C1. Plain HTML / static website

1. Copy `widget/chat.js` into your site (or serve it from a CDN).
2. Add one line before `</body>`:

   ```html
   <script src="chat.js" data-app-key="myapp" data-api-url="https://chat.yourdomain.com"></script>
   ```

Done — the bubble appears bottom-right.

#### C2. Angular

1. Copy `widget/chat.js` → `src/assets/chat.js`.
2. Copy `widget/integrations/angular/help-chat.service.ts` → `src/app/help-chat.service.ts`.
3. In `AppComponent` (or wherever you want it to start):

   ```ts
   constructor(private helpChat: HelpChatService) {}

   ngOnInit() {
     this.helpChat.init({ appKey: 'myapp', apiUrl: 'https://chat.yourdomain.com' });
   }
   ```

Done. Optional: `this.helpChat.identify({ id: userId })`, `open()`, `messages$`.

#### C3. React

1. Copy `widget/chat.js` → `public/chat.js`.
2. Copy `widget/integrations/react/HelpChatWidget.jsx` → `src/HelpChatWidget.jsx`.
3. Render it once, e.g. in `App.jsx`:

   ```jsx
   <HelpChatWidget appKey="myapp" apiUrl="https://chat.yourdomain.com" />
   ```

Done. Optional props: `user`, `headers`, `onMessage`, `position`.

#### C4. Vue / Svelte / any other framework

1. Load `chat.js` (script tag or `import './chat.js'`).
2. Use the standard custom element anywhere in a template:

   ```html
   <help-chat app-key="myapp" api-url="https://chat.yourdomain.com"></help-chat>
   ```

(Vue: add `help-chat` to `compilerOptions.isCustomElement`.)

#### C5. Hybrid mobile (Android WebView / iOS WKWebView)

1. Host a tiny page (or bundle it in the app):

   ```html
   <script src="chat.js" data-app-key="myapp-mobile"
           data-api-url="https://chat.yourdomain.com"
           data-mode="fullscreen"></script>
   ```

2. Open that page in a WebView (enable JavaScript + DOM storage).
3. Optional: expose `window.HelpChatNative.close()` from the native bridge so
   the ✕ button closes the screen — Kotlin/Swift snippets in
   [widget/integrations/README.md](widget/integrations/README.md).

### Environment-specific config (dev/QA/prod)

Ship the identical `chat.js` everywhere; only a one-line config script differs:

```html
<script>window.HelpChatConfig = { appKey: 'myapp', apiUrl: 'https://chat.prod.yourdomain.com' };</script>
<script src="chat.js"></script>
```

### Programmatic control (any host)

```js
HelpChat.open();  HelpChat.toggle();  HelpChat.send('How do I reset my password?');
HelpChat.identify({ id: 'u1', name: 'Thiru' });   // attach user info to messages
HelpChat.setContext({ page: location.pathname }); // attach app context
HelpChat.on('helpchat:message', e => console.log(e.detail));
HelpChat.resetSession();  HelpChat.destroy();
```

Full details (events, auth headers, WebView bridges):
**[widget/integrations/README.md](widget/integrations/README.md)**

## Helper scripts

| Script | Purpose |
|---|---|
| `scripts\start-dev.ps1` (Windows) / `scripts/start-dev.sh` | Starts the backend and the widget demo together, opens the demo page. |
| `scripts\new-app.ps1 -AppKey myapp -AppName "My App"` | Phase B helper: creates `docs/myapp.md` from a template and prints the exact `AppConfigStore` entry to paste. |
| `scripts/db/schema.sql` | MySQL schema for when you move off the in-memory stores: `chat_apps`, `chat_messages` (with hourly 24h purge event), `chat_feedback`, plus the demo seed row. |
| `scripts/db/dynamodb-create-tables.sh [region]` | AWS alternative: creates DynamoDB `chat_apps` + `chat_sessions` (24h TTL via `expires_at`) and seeds the demo app. |
| `scripts/e2e-widget.js` | Full end-to-end test (18 checks) — real headless Chromium drives the widget against the running backend: config, theming, chips, streaming answers, JS API, events, session persistence. Run: start dev servers, then `npm i playwright-core && node scripts/e2e-widget.js`. |

The database scripts prepare the production storage; the backend currently
reads from the in-memory stores (`AppConfigStore`, `SessionStore`) — swap
those two classes to JDBC/DynamoDB when you're ready, the rest is unchanged.

## API reference

| Endpoint | Method | Purpose |
|---|---|---|
| `/chat/config/{appKey}` | GET | Widget bootstrap: theme, welcome, suggested questions |
| `/chat/message` | POST | Body `{appKey, sessionId, message}` → SSE stream of `delta` events, ending with `done` |

## Built-in protections (already on)

- **Rate limiting** — max 20 messages/minute per client (sessionId+IP);
  tune with `HELPCHAT_RATE_LIMIT`, 0 disables
- **Message length cap** (2000 chars) and request validation
- **Bounded worker pool** (64 concurrent replies) — a flood can't exhaust threads
- **Health endpoint** — `GET /chat/health` for load balancers / uptime monitors
- **Unanswered-question log** — every miss logs `UNANSWERED appKey=... question=...`;
  review it to learn what to add to your docs
- **Small-talk handling** — hi/hello/vanakkam/thanks get friendly replies
  instead of the fallback
- **Docs hot-reload** — external docs edits apply within 60 s, no restart
- **Safe rendering** — Shadow DOM isolation; links built as DOM nodes (no HTML injection)

## Production checklist (do before going live)

- [ ] Set `HELPCHAT_ALLOWED_ORIGINS` to your real domains (remove `*`)
- [ ] Serve over HTTPS (an https page cannot call an http backend — mixed content)
- [ ] Pick storage: `HELPCHAT_STORAGE=jdbc` or `dynamodb` so history and app
      registry survive restarts and multiple instances
- [ ] Serve `chat.js` from a CDN as versioned `chat.v2.js`
- [ ] Optional: replace `DocsRetriever` keyword matching with OpenSearch
      hybrid (BM25 + embeddings) if docs grow very large
- [ ] Optional: 👍/👎 feedback endpoint (schema table `chat_feedback` is ready)
