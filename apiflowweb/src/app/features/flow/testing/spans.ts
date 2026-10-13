import { SpanDto, TraceDetail, TraceSummary } from '../../../core/models';

const T0 = Date.parse('2026-01-01T10:00:00Z');

export function iso(offsetMs: number): string {
  const whole = Math.floor(offsetMs);
  const micros = Math.round((offsetMs - whole) * 1000);
  const base = new Date(T0 + whole).toISOString().replace('Z', '');
  return `${base}${String(micros).padStart(3, '0')}Z`;
}

export function span(
  id: string,
  parent: string | null,
  component: string,
  sequence: number,
  start: number,
  end: number | null,
  overrides: Partial<SpanDto> = {},
): SpanDto {
  return {
    traceId: 't1',
    spanId: id,
    parentSpanId: parent,
    component,
    operation: 'op',
    type: 'SERVICE',
    sequence,
    startedAt: iso(start),
    finishedAt: end == null ? null : iso(end),
    durationMs: end == null ? null : end - start,
    status: end == null ? 'RUNNING' : 'COMPLETED',
    slow: false,
    errorType: null,
    errorMessage: null,
    artificialDelayMs: null,
    tags: {},
    ...overrides,
  };
}

export function summary(status: TraceSummary['status'], durationMs: number | null): TraceSummary {
  return {
    traceId: 't1',
    httpMethod: 'POST',
    endpoint: '/api/orders',
    httpStatus: status === 'ERROR' ? 422 : status === 'SUCCESS' ? 201 : null,
    scenario: null,
    startedAt: iso(0),
    finishedAt: durationMs == null ? null : iso(durationMs),
    durationMs,
    status,
    spanCount: 0,
    errorMessage: null,
    truncated: false,
  };
}

/** Controller → UseCase → (Product, Stock) like a real order. */
export function orderTrace(): TraceDetail {
  const spans = [
    span('c', 'http', 'OrderController', 0, 0.1, 9.9, { type: 'CONTROLLER' }),
    span('u', 'c', 'CreateOrderUseCase', 1, 0.2, 9.8, { type: 'USE_CASE' }),
    span('p', 'u', 'ProductService', 2, 0.3, 1.0),
    span('s', 'u', 'StockService', 3, 1.1, 2.0),
    span('r', 'u', 'OrderRepository', 4, 2.1, 9.0, { type: 'REPOSITORY' }),
  ];
  return { trace: summary('SUCCESS', 10), spans, live: false, slowThresholdMs: 250 };
}
