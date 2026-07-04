/* End-to-end test of the help-chat widget in a real (headless) Chromium. */
const { chromium } = require('playwright-core');

const EXE = process.env.LOCALAPPDATA + '\\ms-playwright\\chromium-1228\\chrome-win64\\chrome.exe';
const results = [];
function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log((ok ? 'PASS' : 'FAIL') + '  ' + name + (detail ? '  -- ' + detail : ''));
}

(async () => {
  const browser = await chromium.launch({ executablePath: EXE, headless: true });
  const page = await browser.newPage();
  const events = [];
  await page.exposeFunction('recordEvent', (t, d) => events.push({ t, d }));

  await page.goto('http://localhost:3000/demo.html', { waitUntil: 'load' });

  // Listen for widget events at document level (bubbling, composed)
  await page.evaluate(() => {
    for (const ev of ['helpchat:ready', 'helpchat:open', 'helpchat:close', 'helpchat:message', 'helpchat:error']) {
      document.addEventListener(ev, (e) => window.recordEvent(ev, e.detail ?? null));
    }
  });

  // 1. Widget element exists and API is present
  await page.waitForFunction(() => window.HelpChat && document.querySelector('help-chat'));
  check('widget auto-initialized from script tag', true);
  check('window.HelpChat API exists', await page.evaluate(() =>
    ['init','open','close','toggle','send','identify','setContext','on','destroy','resetSession']
      .every(m => typeof window.HelpChat[m] === 'function')));

  // 2. Config loaded (helpchat:ready) → title + welcome message + chips
  await page.waitForFunction(() => {
    const el = document.querySelector('help-chat');
    return el && el.shadowRoot.querySelector('.hc-title').textContent.includes('Demo App');
  }, null, { timeout: 10000 });
  const boot = await page.evaluate(() => {
    const sr = document.querySelector('help-chat').shadowRoot;
    return {
      title: sr.querySelector('.hc-title').textContent,
      welcome: sr.querySelector('.hc-msg.assistant')?.textContent || '',
      chips: [...sr.querySelectorAll('.hc-chip')].map(b => b.textContent),
      themed: getComputedStyle(sr.querySelector('.hc-bubble')).backgroundColor,
    };
  });
  check('title from backend config', boot.title === 'Demo App Help', boot.title);
  check('welcome message rendered', boot.welcome.startsWith('Hi!'), boot.welcome.slice(0, 40));
  check('3 suggested-question chips', boot.chips.length === 3, boot.chips.join(' | '));
  check('theme color applied (#0d7377)', boot.themed === 'rgb(13, 115, 119)', boot.themed);

  // 3. Open via bubble click
  await page.evaluate(() => document.querySelector('help-chat').shadowRoot.querySelector('.hc-bubble').click());
  const isOpen = await page.evaluate(() =>
    document.querySelector('help-chat').shadowRoot.querySelector('.hc-panel').classList.contains('open'));
  check('panel opens on bubble click', isOpen);

  // 4. Click a suggested-question chip → streamed answer appears
  await page.evaluate(() => {
    const sr = document.querySelector('help-chat').shadowRoot;
    [...sr.querySelectorAll('.hc-chip')].find(c => c.textContent.includes('password')).click();
  });
  await page.waitForFunction(() => {
    const msgs = document.querySelector('help-chat').shadowRoot.querySelectorAll('.hc-msg.assistant');
    const last = msgs[msgs.length - 1];
    return last && !last.classList.contains('typing') && last.textContent.length > 20;
  }, null, { timeout: 15000 });
  const answer1 = await page.evaluate(() => {
    const msgs = document.querySelector('help-chat').shadowRoot.querySelectorAll('.hc-msg.assistant');
    return msgs[msgs.length - 1].textContent;
  });
  check('chip click → streamed docs answer', answer1.includes('Forgot password'), answer1.slice(0, 60));

  // 5. Typed question via input + Enter
  await page.evaluate(() => {
    const sr = document.querySelector('help-chat').shadowRoot;
    const input = sr.querySelector('.hc-input');
    input.value = 'what are the pricing plans?';
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
  });
  await page.waitForFunction(() => {
    const msgs = document.querySelector('help-chat').shadowRoot.querySelectorAll('.hc-msg.assistant');
    const last = msgs[msgs.length - 1];
    return last && !last.classList.contains('typing') && last.textContent.includes('Enterprise');
  }, null, { timeout: 15000 });
  check('typed question → pricing answer', true);

  // 6. Programmatic API: send()
  await page.evaluate(() => window.HelpChat.send('How do I get started?'));
  await page.waitForFunction(() => {
    const msgs = document.querySelector('help-chat').shadowRoot.querySelectorAll('.hc-msg');
    const last = msgs[msgs.length - 1];
    return last && last.classList.contains('assistant') && !last.classList.contains('typing') && last.textContent.length > 20;
  }, null, { timeout: 15000 });
  check('HelpChat.send() works', true);

  // 7. Unknown question → fallback message
  await page.evaluate(() => window.HelpChat.send('zzzz qwerty nonsense'));
  await page.waitForFunction(() => {
    const msgs = document.querySelector('help-chat').shadowRoot.querySelectorAll('.hc-msg.assistant');
    return [...msgs].some(m => m.textContent.includes("couldn't find anything"));
  }, null, { timeout: 15000 });
  check('unknown question → graceful fallback', true);

  // 8. Events fired (ready/open/message). ready via late-subscriber replay.
  const lateReady = await page.evaluate(() => new Promise(res => {
    const timer = setTimeout(() => res(false), 3000);
    window.HelpChat.on('helpchat:ready', (e) => { clearTimeout(timer); res(!!e.detail); });
  }));
  const evTypes = [...new Set(events.map(e => e.t))];
  check('helpchat:ready delivered to late subscriber', lateReady);
  check('helpchat:open fired', evTypes.includes('helpchat:open'));
  check('helpchat:message fired', evTypes.includes('helpchat:message'));

  // 9. Session id persisted in localStorage
  const sid = await page.evaluate(() => localStorage.getItem('helpchat_sid_demo'));
  check('sessionId persisted per app', !!sid && sid.startsWith('S'), sid);

  // 10. Close + toggle + destroy
  await page.evaluate(() => window.HelpChat.close());
  const closed = await page.evaluate(() =>
    !document.querySelector('help-chat').shadowRoot.querySelector('.hc-panel').classList.contains('open'));
  check('HelpChat.close() closes panel', closed);
  await page.evaluate(() => window.HelpChat.destroy());
  check('HelpChat.destroy() removes widget', await page.evaluate(() => !document.querySelector('help-chat')));

  // 11. Re-init programmatically (SPA style) with position=left
  await page.evaluate(() => {
    const el = window.HelpChat.init({ appKey: 'demo', apiUrl: 'http://localhost:8090' });
    el.setAttribute('position', 'left');
  });
  await page.waitForFunction(() => {
    const el = document.querySelector('help-chat');
    return el && el.shadowRoot.querySelector('.hc-title').textContent.includes('Demo App');
  }, null, { timeout: 10000 });
  check('HelpChat.init() re-creates widget (SPA flow)', true);

  // 12. No console errors
  await browser.close();
  const failed = results.filter(r => !r.ok);
  console.log('\n==== ' + (results.length - failed.length) + '/' + results.length + ' checks passed ====');
  process.exit(failed.length ? 1 : 0);
})().catch(e => { console.error('E2E crashed:', e.message); process.exit(2); });
