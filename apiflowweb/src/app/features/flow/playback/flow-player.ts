import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { FlowEvent, SpanDto, TraceDetail, TraceSummary } from '../../../core/models';
import { isoToEpochMs } from '../../../shared/time';
import { FlowGraph, FlowPhase, buildGraph } from '../graph/graph-builder';
import { TimelineEntry, asRunning, entryKey, offsetMs, spansAt, timelineFromTrace } from './timeline';

export type PlayerMode = 'idle' | 'live' | 'replay';

/** Minimum on-screen time between two steps, so sub-millisecond steps remain visible. */
export const MIN_STEP_MS = 220;
export const SPEEDS = [0.25, 0.5, 1, 2, 4] as const;

/**
 * Drives what the flow graph shows.
 *
 * Both modes play the same thing – a timeline of real span start/end events captured by the backend:
 * - **live**: entries are appended as SSE events arrive for the followed trace;
 * - **replay**: entries are rebuilt from the timestamps persisted for a finished trace.
 *
 * Steps are shown after their real time gap divided by the speed, never closer than {@link MIN_STEP_MS}
 * (scaled by speed). Only the animation is paced; displayed durations are always the measured ones.
 */
@Injectable()
export class FlowPlayer {
  readonly mode = signal<PlayerMode>('idle');
  readonly trace = signal<TraceSummary | null>(null);
  readonly timeline = signal<TimelineEntry[]>([]);
  readonly cursor = signal(0);
  readonly playing = signal(false);
  readonly speed = signal<number>(1);
  readonly reference = signal<{ traceId: string; spans: SpanDto[] } | null>(null);
  readonly selectedSpanId = signal<string | null>(null);
  /** Structural key of a selected step that was not executed (no span id). */
  readonly selectedKey = signal<string | null>(null);

  /** Spans as they are at the current cursor position. */
  readonly spans = computed(() => spansAt(this.timeline(), this.cursor()));

  /** Trace summary once the cursor passed the end of the request. */
  readonly finalTrace = computed<TraceSummary | null>(() => {
    const timeline = this.timeline();
    for (let i = Math.min(this.cursor(), timeline.length) - 1; i >= 0; i--) {
      const entry = timeline[i];
      if (entry.kind === 'trace-end') return entry.trace;
    }
    return null;
  });

  readonly phase = computed<FlowPhase>(() => {
    const final = this.finalTrace();
    if (!final) return 'running';
    return final.status === 'ERROR' ? 'error' : 'success';
  });

  readonly graph = computed<FlowGraph>(() => buildGraph(this.spans(), this.reference()?.spans ?? null, this.phase()));

  /** Span touched by the last applied step (highlighted in the graph). */
  readonly activeSpanId = computed<string | null>(() => {
    const index = this.cursor() - 1;
    const entry = this.timeline()[index];
    return entry && entry.kind !== 'trace-end' ? entry.span.spanId : null;
  });

  readonly atEnd = computed(() => this.cursor() >= this.timeline().length);
  readonly complete = computed(() => this.timeline().some((e) => e.kind === 'trace-end'));

  private timer: ReturnType<typeof setTimeout> | null = null;
  private seen = new Set<string>();
  private origin = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clearTimer());
  }

  /** Shows a finished (or in-flight) trace. With `autoplay` the execution is replayed from the start. */
  load(detail: TraceDetail, autoplay: boolean): void {
    this.clearTimer();
    // "live" only while the request is still running; a finished one waiting for persistence is replayed
    const live = detail.live && detail.trace.status === 'RUNNING';
    const timeline = timelineFromTrace(detail);
    this.origin = isoToEpochMs(detail.trace.startedAt) ?? 0;
    this.seen = new Set(timeline.map(entryKey));
    this.mode.set(live ? 'live' : 'replay');
    this.trace.set(detail.trace);
    this.timeline.set(timeline);
    this.select(null);
    if (detail.trace.status === 'SUCCESS') {
      // a successful execution is its own best reference: upcoming steps are shown as waiting
      this.reference.set({ traceId: detail.trace.traceId, spans: detail.spans });
    } else if (this.reference()?.traceId === detail.trace.traceId) {
      this.reference.set(null);
    }
    if (autoplay || live) {
      this.cursor.set(live ? timeline.length : 0);
      this.playing.set(true);
      this.schedule();
    } else {
      this.cursor.set(timeline.length);
      this.playing.set(false);
    }
  }

  /** Starts following a request that has just entered the backend. */
  startLive(trace: TraceSummary): void {
    this.clearTimer();
    this.origin = isoToEpochMs(trace.startedAt) ?? Date.now();
    this.seen = new Set();
    this.mode.set('live');
    this.trace.set(trace);
    this.timeline.set([]);
    this.cursor.set(0);
    this.reference.set(null);
    this.select(null);
    this.playing.set(true);
  }

  /** Appends a live event of the followed trace. Events of other traces and duplicates are ignored. */
  push(event: FlowEvent): void {
    if (this.mode() !== 'live' || event.traceId !== this.trace()?.traceId) return;
    let entry: TimelineEntry | null = null;
    if (event.type === 'SPAN_STARTED' && event.span) {
      entry = { kind: 'start', span: asRunning(event.span), at: offsetMs(event.span.startedAt, this.origin) };
    } else if ((event.type === 'SPAN_COMPLETED' || event.type === 'SPAN_FAILED') && event.span) {
      entry = { kind: 'end', span: event.span, at: offsetMs(event.span.finishedAt, this.origin) };
    } else if (event.type === 'TRACE_COMPLETED' && event.trace) {
      this.trace.set(event.trace);
      const last = this.timeline().at(-1)?.at ?? 0;
      entry = { kind: 'trace-end', trace: event.trace, at: Math.max(last, offsetMs(event.trace.finishedAt, this.origin)) };
    }
    if (!entry || this.seen.has(entryKey(entry))) return;
    this.seen.add(entryKey(entry));
    this.timeline.update((timeline) => [...timeline, entry]);
    this.schedule();
  }

  setReference(traceId: string, spans: SpanDto[]): void {
    if (traceId !== this.trace()?.traceId) {
      this.reference.set({ traceId, spans });
    }
  }

  clearReference(): void {
    this.reference.set(null);
  }

  play(): void {
    if (this.mode() === 'idle') return;
    if (this.atEnd() && this.mode() === 'replay') {
      this.cursor.set(0);
    }
    this.playing.set(true);
    this.schedule();
  }

  pause(): void {
    this.playing.set(false);
    this.clearTimer();
  }

  restart(): void {
    if (this.mode() === 'live' && this.complete()) {
      this.mode.set('replay');
    }
    this.cursor.set(0);
    this.play();
  }

  /** Moves to a given number of applied steps (0 = nothing executed yet). */
  seek(count: number): void {
    this.pause();
    if (this.mode() === 'live' && this.complete()) this.mode.set('replay');
    this.cursor.set(Math.max(0, Math.min(count, this.timeline().length)));
  }

  step(delta: number): void {
    this.seek(this.cursor() + delta);
  }

  /** Jumps to the moment a span started (or ended, with `end`). */
  seekToSpan(spanId: string, end = true): void {
    const index = this.timeline().findIndex(
      (e) => e.kind === (end ? 'end' : 'start') && e.span.spanId === spanId,
    );
    if (index >= 0) this.seek(index + 1);
  }

  setSpeed(speed: number): void {
    this.speed.set(speed);
    if (this.playing()) {
      this.clearTimer();
      this.schedule();
    }
  }

  select(spanId: string | null, key: string | null = null): void {
    this.selectedSpanId.set(spanId);
    this.selectedKey.set(spanId ? null : key);
  }

  /** Real-time delay before applying the entry at `index`. */
  delayBefore(index: number): number {
    const timeline = this.timeline();
    if (index <= 0 || index >= timeline.length) return 0;
    const realGap = Math.max(0, timeline[index].at - timeline[index - 1].at);
    return Math.max(MIN_STEP_MS, realGap) / this.speed();
  }

  private schedule(): void {
    if (this.timer || !this.playing()) return;
    const index = this.cursor();
    if (index >= this.timeline().length) {
      if (this.mode() === 'replay' || this.complete()) this.playing.set(false);
      return;
    }
    this.timer = setTimeout(() => {
      this.timer = null;
      this.cursor.update((c) => Math.min(c + 1, this.timeline().length));
      this.schedule();
    }, this.delayBefore(index));
  }

  private clearTimer(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
  }
}
