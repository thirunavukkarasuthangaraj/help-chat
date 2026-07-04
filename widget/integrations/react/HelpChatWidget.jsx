/**
 * React wrapper for the help-chat widget. Works with React 16.8+ (hooks).
 *
 * Usage:
 *   import HelpChatWidget from './HelpChatWidget';
 *
 *   <HelpChatWidget
 *     appKey="myapp"
 *     apiUrl="https://chat.yourdomain.com"
 *     user={{ id: user.id, name: user.name }}
 *     onMessage={(m) => analytics.track('help_chat_message', m)}
 *   />
 *
 * Loads chat.js from `scriptUrl` on first mount (or set scriptUrl to null
 * and import chat.js yourself in index.js / main.jsx).
 */
import { useEffect, useRef } from 'react';

let scriptPromise = null;

function loadScript(src) {
  if (!src) return Promise.resolve();                 // already imported by the app
  if (window.customElements?.get('help-chat')) return Promise.resolve();
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = src;
      s.onload = resolve;
      s.onerror = reject;
      document.head.appendChild(s);
    });
  }
  return scriptPromise;
}

export default function HelpChatWidget({
  appKey,
  apiUrl,
  mode = 'bubble',          // 'bubble' | 'fullscreen'
  position = 'right',       // 'right' | 'left'
  user = null,
  context = null,
  headers = null,           // object or () => object, e.g. auth token
  scriptUrl = '/chat.js',   // where chat.js is served from; null if self-imported
  onReady,
  onOpen,
  onClose,
  onMessage,
  onError,
}) {
  const cleanupRef = useRef(null);

  useEffect(() => {
    let cancelled = false;

    loadScript(scriptUrl).then(() => {
      if (cancelled) return;
      const el = window.HelpChat.init({ appKey, apiUrl, mode, position, user, context, headers });
      el.setAttribute('mode', mode);
      el.setAttribute('position', position);

      const subs = [];
      const sub = (event, cb) => {
        if (!cb) return;
        const handler = (e) => cb(e.detail);
        el.addEventListener(event, handler);
        subs.push(() => el.removeEventListener(event, handler));
      };
      sub('helpchat:ready', onReady);
      sub('helpchat:open', onOpen);
      sub('helpchat:close', onClose);
      sub('helpchat:message', onMessage);
      sub('helpchat:error', onError);
      cleanupRef.current = () => subs.forEach((u) => u());
    }).catch((e) => console.error('[help-chat] failed to load widget script', e));

    return () => {
      cancelled = true;
      cleanupRef.current?.();
      window.HelpChat?.destroy();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [appKey, apiUrl, mode, position]);

  // Keep user/context fresh without re-creating the widget
  useEffect(() => { window.HelpChat?.identify(user); }, [user]);
  useEffect(() => { window.HelpChat?.setContext(context); }, [context]);

  return null; // widget renders itself into document.body via Shadow DOM
}
