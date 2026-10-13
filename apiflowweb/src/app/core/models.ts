/** DTOs exposed by the backend visualization API (`/api/flow/**`) and demo endpoints. */

export type ComponentType = 'CONTROLLER' | 'USE_CASE' | 'SERVICE' | 'REPOSITORY' | 'DATABASE';
export type SpanStatus = 'RUNNING' | 'COMPLETED' | 'ERROR';
export type TraceStatus = 'RUNNING' | 'SUCCESS' | 'ERROR';
export type FlowEventType =
  | 'TRACE_STARTED'
  | 'SPAN_STARTED'
  | 'SPAN_COMPLETED'
  | 'SPAN_FAILED'
  | 'TRACE_COMPLETED'
  | 'TRACE_PERSISTED';

export interface SpanDto {
  traceId: string;
  spanId: string;
  parentSpanId: string | null;
  component: string;
  operation: string;
  type: ComponentType;
  sequence: number;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  status: SpanStatus;
  slow: boolean;
  errorType: string | null;
  errorMessage: string | null;
  artificialDelayMs: number | null;
  tags: Record<string, string>;
}

export interface TraceSummary {
  traceId: string;
  httpMethod: string;
  endpoint: string;
  httpStatus: number | null;
  scenario: string | null;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  status: TraceStatus;
  spanCount: number;
  errorMessage: string | null;
  truncated: boolean;
}

export interface TraceDetail {
  trace: TraceSummary;
  spans: SpanDto[];
  live: boolean;
  slowThresholdMs: number;
}

export interface SpanDetail {
  span: SpanDto;
  ancestors: SpanDto[];
  children: SpanDto[];
  baselineMedianMs: number | null;
  deltaFromBaselineMs: number | null;
}

export interface Blueprint {
  referenceTraceId: string;
  httpMethod: string;
  endpoint: string;
  spans: SpanDto[];
}

export interface Metrics {
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  avgLatencyMs: number;
  maxLatencyMs: number;
  p95LatencyMs: number;
  totalSpans: number;
  inFlight: number;
  slowThresholdMs: number;
}

export interface ComponentMetrics {
  component: string;
  operation: string;
  type: ComponentType;
  executions: number;
  errors: number;
  avgMs: number;
  medianMs: number;
  maxMs: number;
}

export interface HistoryPoint {
  traceId: string;
  startedAt: string;
  durationMs: number | null;
  status: TraceStatus;
  httpStatus: number | null;
  httpMethod: string;
  endpoint: string;
  scenario: string | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface FlowEvent {
  sequence: number;
  type: FlowEventType;
  traceId: string;
  timestamp: string;
  trace: TraceSummary | null;
  span: SpanDto | null;
}

export interface Product {
  id: number;
  name: string;
  price: number;
  stock: number;
}

export interface ScenarioInfo {
  id: string;
  description: string;
}

export interface ScenarioResult {
  scenario: string;
  description: string;
  traceId: string | null;
  httpStatus: number;
  clientDurationMs: number;
  requestBody: unknown;
  artificialDelayMs: number | null;
}

export interface TraceFilter {
  status?: TraceStatus | null;
  method?: string | null;
  endpoint?: string | null;
  scenario?: string | null;
  minDurationMs?: number | null;
  page: number;
  size: number;
}

export interface OrderLine {
  productId: number;
  quantity: number;
}

export interface QueryParamInfo {
  name: string;
  required: boolean;
  defaultValue: string | null;
}

/** A tracked HTTP endpoint discovered from the backend's Spring MVC mappings. */
export interface EndpointInfo {
  method: string;
  path: string;
  pathVariables: string[];
  queryParams: QueryParamInfo[];
  hasBody: boolean;
  bodyExample: unknown;
  handler: string;
  instrumented: boolean;
  /** `Component.operation` of the handler step as shown in the graph (when instrumented). */
  entryStep: string | null;
}

export interface SendResult {
  traceId: string | null;
  httpStatus: number;
  durationMs: number;
  responsePreview: string;
}

/** Artificial latency injected in one step, for demonstrations only. */
export interface DemoDelay {
  ms: number;
  /** `Component` or `Component.operation` of the step to slow down. */
  target: string;
}
