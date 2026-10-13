import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { FlowEvent } from './models';

export type StreamState = 'connecting' | 'open' | 'reconnecting';

const STREAM_URL = '/api/flow/stream';
const MAX_FEED = 80;
const MAX_BACKOFF_MS = 15_000;

/**
 * Single Server-Sent Events connection to the backend.
 *
 * - The browser reconnects automatically and resumes with `Last-Event-ID`.
 * - When the browser gives up (connection closed), we reconnect with exponential backoff, passing the
 *   last received id as a query parameter.
 * - Every (re)connection emits on {@link connected$} so consumers can re-fetch the consolidated state.
 */
@Injectable({ providedIn: 'root' })
export class FlowStreamService {
  private readonly eventsSubject = new Subject<FlowEvent>();
  private readonly connectedSubject = new Subject<{ replayed: number; gap: boolean }>();
  private source: EventSource | null = null;
  private lastEventId: number | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private attempts = 0;

  /** Connection state, for the status indicator. */
  readonly state = signal<StreamState>('connecting');
  /** Most recent events (newest first) for the live event feed. */
  readonly feed = signal<FlowEvent[]>([]);

  readonly events$: Observable<FlowEvent> = this.eventsSubject.asObservable();
  readonly connected$ = this.connectedSubject.asObservable();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.close());
    if (typeof EventSource !== 'undefined') {
      this.open();
    }
  }

  private open(): void {
    this.close();
    const url = this.lastEventId == null ? STREAM_URL : `${STREAM_URL}?lastEventId=${this.lastEventId}`;
    const source = new EventSource(url);
    this.source = source;

    source.addEventListener('connected', (message) => {
      this.attempts = 0;
      this.state.set('open');
      const info = safeParse<{ replayed: number; gap: boolean }>((message as MessageEvent).data);
      this.connectedSubject.next(info ?? { replayed: 0, gap: false });
    });

    source.onmessage = (message: MessageEvent<string>) => {
      const event = safeParse<FlowEvent>(message.data);
      if (!event) return;
      this.lastEventId = event.sequence;
      this.feed.update((feed) => [event, ...feed].slice(0, MAX_FEED));
      this.eventsSubject.next(event);
    };

    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) {
        this.scheduleReconnect();
      } else {
        this.state.set('reconnecting');
      }
    };
  }

  private scheduleReconnect(): void {
    this.state.set('reconnecting');
    if (this.retryTimer) return;
    const delay = Math.min(MAX_BACKOFF_MS, 1000 * 2 ** this.attempts++);
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.open();
    }, delay);
  }

  private close(): void {
    this.source?.close();
    this.source = null;
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}

function safeParse<T>(data: string): T | null {
  try {
    return JSON.parse(data) as T;
  } catch {
    return null;
  }
}
