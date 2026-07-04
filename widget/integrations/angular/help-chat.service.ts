/**
 * Angular wrapper for the help-chat widget. Works with Angular 12+
 * (module-based or standalone).
 *
 * Setup (one time):
 *   1. Copy chat.js into src/assets/ (or serve it from your CDN).
 *   2. Add it to angular.json "scripts": ["src/assets/chat.js"]
 *      — or leave it out and let this service lazy-load it.
 *
 * Usage:
 *   constructor(private helpChat: HelpChatService) {}
 *
 *   ngOnInit() {
 *     this.helpChat.init({
 *       appKey: 'myapp',
 *       apiUrl: 'https://chat.yourdomain.com',
 *       user: { id: this.auth.userId, name: this.auth.userName },
 *       headers: () => ({ Authorization: 'Bearer ' + this.auth.token }),
 *     });
 *     this.helpChat.messages$.subscribe(m => console.log('chat message', m));
 *   }
 *
 * Alternative (declarative): add CUSTOM_ELEMENTS_SCHEMA to your module and
 * put <help-chat app-key="myapp" api-url="..."></help-chat> in a template.
 */
import { Injectable, NgZone, OnDestroy } from '@angular/core';
import { Subject } from 'rxjs';

declare global {
  interface Window {
    HelpChat: any;
    customElements: CustomElementRegistry;
  }
}

export interface HelpChatOptions {
  appKey: string;
  apiUrl: string;
  mode?: 'bubble' | 'fullscreen';
  position?: 'right' | 'left';
  user?: Record<string, unknown> | null;
  context?: Record<string, unknown> | null;
  headers?: Record<string, string> | (() => Record<string, string>) | null;
  /** Where chat.js is served from. Omit if loaded via angular.json scripts. */
  scriptUrl?: string;
}

export interface HelpChatMessage { role: 'user' | 'assistant'; text: string; }

@Injectable({ providedIn: 'root' })
export class HelpChatService implements OnDestroy {

  readonly ready$ = new Subject<any>();
  readonly messages$ = new Subject<HelpChatMessage>();
  readonly errors$ = new Subject<{ message: string }>();

  private unsubscribers: Array<() => void> = [];
  private scriptPromise: Promise<void> | null = null;

  constructor(private zone: NgZone) {}

  async init(opts: HelpChatOptions): Promise<void> {
    await this.loadScript(opts.scriptUrl ?? 'assets/chat.js');

    // Run outside Angular so widget DOM events don't trigger change detection.
    this.zone.runOutsideAngular(() => {
      const el = window.HelpChat.init(opts);
      if (opts.mode) el.setAttribute('mode', opts.mode);
      if (opts.position) el.setAttribute('position', opts.position);

      const sub = (event: string, subject: Subject<any>) => {
        const handler = (e: Event) =>
          this.zone.run(() => subject.next((e as CustomEvent).detail));
        el.addEventListener(event, handler);
        this.unsubscribers.push(() => el.removeEventListener(event, handler));
      };
      sub('helpchat:ready', this.ready$);
      sub('helpchat:message', this.messages$);
      sub('helpchat:error', this.errors$);
    });
  }

  open(): void { window.HelpChat?.open(); }
  close(): void { window.HelpChat?.close(); }
  toggle(): void { window.HelpChat?.toggle(); }
  send(text: string): void { window.HelpChat?.send(text); }
  identify(user: Record<string, unknown>): void { window.HelpChat?.identify(user); }
  setContext(ctx: Record<string, unknown>): void { window.HelpChat?.setContext(ctx); }
  resetSession(): void { window.HelpChat?.resetSession(); }

  destroy(): void {
    this.unsubscribers.forEach(u => u());
    this.unsubscribers = [];
    window.HelpChat?.destroy();
  }

  ngOnDestroy(): void { this.destroy(); }

  private loadScript(src: string): Promise<void> {
    if (window.customElements?.get('help-chat')) return Promise.resolve();
    if (!this.scriptPromise) {
      this.scriptPromise = new Promise<void>((resolve, reject) => {
        const s = document.createElement('script');
        s.src = src;
        s.onload = () => resolve();
        s.onerror = () => reject(new Error('failed to load ' + src));
        document.head.appendChild(s);
      });
    }
    return this.scriptPromise;
  }
}
