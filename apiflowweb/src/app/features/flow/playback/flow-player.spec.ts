import { TestBed } from '@angular/core/testing';
import { FlowEvent } from '../../../core/models';
import { isoToEpochMs } from '../../../shared/time';
import { orderTrace, span, summary } from '../testing/spans';
import { FlowPlayer, MIN_STEP_MS } from './flow-player';
import { spansAt, timelineFromTrace } from './timeline';

describe('timeline', () => {
  it('parses microsecond timestamps', () => {
    const a = isoToEpochMs('2026-01-01T10:00:00.000100Z')!;
    const b = isoToEpochMs('2026-01-01T10:00:00.000350Z')!;
    expect(b - a).toBeCloseTo(0.25, 5);
  });

  it('rebuilds a causally ordered start/end timeline from persisted timestamps', () => {
    const timeline = timelineFromTrace(orderTrace());

    expect(timeline).toHaveLength(11);
    expect(timeline[0]).toMatchObject({ kind: 'start', span: { spanId: 'c', status: 'RUNNING' } });
    expect(timeline.at(-1)!.kind).toBe('trace-end');
    const order = timeline.filter((e) => e.kind !== 'trace-end').map((e) => `${e.kind}:${(e as { span: { spanId: string } }).span.spanId}`);
    expect(order).toEqual([
      'start:c', 'start:u', 'start:p', 'end:p', 'start:s', 'end:s', 'start:r', 'end:r', 'end:u', 'end:c',
    ]);
  });

  it('breaks timestamp ties so children end before their parents', () => {
    const detail = {
      ...orderTrace(),
      spans: [span('a', null, 'A', 0, 1, 5), span('b', 'a', 'B', 1, 1, 5)],
    };
    const kinds = timelineFromTrace(detail).map((e) => (e.kind === 'trace-end' ? 'end-trace' : `${e.kind}:${e.span.spanId}`));
    expect(kinds).toEqual(['start:a', 'start:b', 'end:b', 'end:a', 'end-trace']);
  });

  it('applies entries up to the cursor', () => {
    const timeline = timelineFromTrace(orderTrace());
    const atThree = spansAt(timeline, 4);
    expect(atThree.map((s) => `${s.spanId}:${s.status}`)).toEqual(['c:RUNNING', 'u:RUNNING', 'p:COMPLETED']);
  });
});

describe('FlowPlayer', () => {
  let player: FlowPlayer;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [FlowPlayer] });
    player = TestBed.inject(FlowPlayer);
  });

  afterEach(() => vi.useRealTimers());

  it('shows a loaded trace complete without autoplay', () => {
    player.load(orderTrace(), false);
    expect(player.mode()).toBe('replay');
    expect(player.atEnd()).toBe(true);
    expect(player.phase()).toBe('success');
    expect(player.graph().nodes).toHaveLength(5);
  });

  it('replays step by step, keeping a minimum gap between steps and honouring speed', () => {
    player.load(orderTrace(), true);
    expect(player.cursor()).toBe(0);

    vi.advanceTimersByTime(0);
    expect(player.cursor()).toBe(1);
    vi.advanceTimersByTime(MIN_STEP_MS - 1);
    expect(player.cursor()).toBe(1);
    vi.advanceTimersByTime(1);
    expect(player.cursor()).toBe(2);

    player.setSpeed(2);
    vi.advanceTimersByTime(MIN_STEP_MS / 2);
    expect(player.cursor()).toBe(3);

    vi.runAllTimers();
    expect(player.atEnd()).toBe(true);
    expect(player.playing()).toBe(false);
  });

  it('preserves real gaps longer than the minimum', () => {
    const detail = orderTrace();
    detail.spans = [span('a', null, 'Controller', 0, 0, 1002), span('s', 'a', 'StockService', 1, 1, 1001, { slow: true })];
    player.load(detail, true);
    const endStock = player.timeline().findIndex((e) => e.kind === 'end' && e.span.spanId === 's');
    expect(player.timeline()[endStock - 1]).toMatchObject({ kind: 'start', span: { spanId: 's' } });
    expect(player.delayBefore(endStock)).toBeCloseTo(1000, 0);
  });

  it('seeks to a selected step and pauses', () => {
    player.load(orderTrace(), true);
    player.seekToSpan('s', true);
    expect(player.playing()).toBe(false);
    expect(player.spans().find((s) => s.spanId === 's')!.status).toBe('COMPLETED');
    expect(player.spans().some((s) => s.spanId === 'r')).toBe(false);
  });

  it('follows live events of the current trace only and ignores duplicates', () => {
    player.startLive(summary('RUNNING', null));
    const started = (id: string, seq: number): FlowEvent => ({
      sequence: seq, type: 'SPAN_STARTED', traceId: 't1', timestamp: '', trace: null,
      span: span(id, null, 'C', 0, seq, null),
    });

    player.push(started('c', 1));
    player.push(started('c', 1));
    player.push({ ...started('x', 2), traceId: 'other' });
    player.push({ sequence: 3, type: 'TRACE_COMPLETED', traceId: 't1', timestamp: '', trace: summary('ERROR', 5), span: null });
    vi.runAllTimers();

    expect(player.timeline()).toHaveLength(2);
    expect(player.phase()).toBe('error');
    expect(player.mode()).toBe('live');
  });
});
