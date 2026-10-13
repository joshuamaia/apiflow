import { SpanDto, TraceDetail, TraceSummary } from '../../../core/models';
import { isoToEpochMs } from '../../../shared/time';

/** One step of the execution timeline. `at` is the real offset (ms) from the start of the request. */
export type TimelineEntry =
  | { kind: 'start'; span: SpanDto; at: number }
  | { kind: 'end'; span: SpanDto; at: number }
  | { kind: 'trace-end'; trace: TraceSummary; at: number };

export function entryKey(entry: TimelineEntry): string {
  return entry.kind === 'trace-end' ? 'trace-end' : `${entry.kind}:${entry.span.spanId}`;
}

export function offsetMs(iso: string | null | undefined, origin: number): number {
  const epoch = isoToEpochMs(iso);
  return epoch == null ? 0 : Math.max(0, epoch - origin);
}

/** The running version of a span, as it looked when it started. */
export function asRunning(span: SpanDto): SpanDto {
  return {
    ...span,
    status: 'RUNNING',
    finishedAt: null,
    durationMs: null,
    slow: false,
    errorType: null,
    errorMessage: null,
  };
}

/**
 * Rebuilds the ordered start/end events of a persisted trace from its captured timestamps.
 * Ties are broken so that the order is always causally valid (a child starts after and ends before
 * its parent).
 */
export function timelineFromTrace(detail: TraceDetail): TimelineEntry[] {
  const origin = isoToEpochMs(detail.trace.startedAt) ?? 0;
  const entries: TimelineEntry[] = [];
  for (const span of detail.spans) {
    entries.push({ kind: 'start', span: asRunning(span), at: offsetMs(span.startedAt, origin) });
    if (span.status !== 'RUNNING') {
      entries.push({ kind: 'end', span, at: offsetMs(span.finishedAt ?? span.startedAt, origin) });
    }
  }
  entries.sort(compareEntries);
  if (detail.trace.status !== 'RUNNING') {
    const lastAt = entries.length ? entries[entries.length - 1].at : 0;
    entries.push({
      kind: 'trace-end',
      trace: detail.trace,
      at: Math.max(lastAt, offsetMs(detail.trace.finishedAt, origin)),
    });
  }
  return entries;
}

function compareEntries(a: TimelineEntry, b: TimelineEntry): number {
  if (a.at !== b.at) return a.at - b.at;
  if (a.kind === 'trace-end' || b.kind === 'trace-end') return a.kind === 'trace-end' ? 1 : -1;
  if (a.span.spanId === b.span.spanId) return a.kind === 'start' ? -1 : 1;
  if (a.kind !== b.kind) return a.kind === 'end' ? -1 : 1;
  return a.kind === 'start' ? a.span.sequence - b.span.sequence : b.span.sequence - a.span.sequence;
}

/** Applies the first `count` entries, producing the spans as they were at that moment. */
export function spansAt(timeline: readonly TimelineEntry[], count: number): SpanDto[] {
  const spans = new Map<string, SpanDto>();
  for (let i = 0; i < Math.min(count, timeline.length); i++) {
    const entry = timeline[i];
    if (entry.kind !== 'trace-end') {
      spans.set(entry.span.spanId, entry.span);
    }
  }
  return [...spans.values()];
}
