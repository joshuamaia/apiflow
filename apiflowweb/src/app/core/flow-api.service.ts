import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import {
  Blueprint,
  ComponentMetrics,
  DemoDelay,
  EndpointInfo,
  HistoryPoint,
  Metrics,
  OrderLine,
  Page,
  Product,
  ScenarioInfo,
  ScenarioResult,
  SendResult,
  SpanDetail,
  TraceDetail,
  TraceFilter,
  TraceSummary,
} from './models';

/** Relative URLs only: Nginx (production) and the dev-server proxy forward `/api` to the backend. */
const FLOW = '/api/flow';

export interface OrderSubmission {
  traceId: string | null;
  httpStatus: number;
}

@Injectable({ providedIn: 'root' })
export class FlowApiService {
  private readonly http = inject(HttpClient);

  metrics(): Observable<Metrics> {
    return this.http.get<Metrics>(`${FLOW}/metrics`);
  }

  componentMetrics(): Observable<ComponentMetrics[]> {
    return this.http.get<ComponentMetrics[]>(`${FLOW}/metrics/components`);
  }

  history(limit = 60): Observable<HistoryPoint[]> {
    return this.http.get<HistoryPoint[]>(`${FLOW}/history`, { params: { limit } });
  }

  traces(filter: TraceFilter): Observable<Page<TraceSummary>> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.method) params = params.set('method', filter.method);
    if (filter.endpoint) params = params.set('endpoint', filter.endpoint);
    if (filter.scenario) params = params.set('scenario', filter.scenario);
    if (filter.minDurationMs != null) params = params.set('minDurationMs', filter.minDurationMs);
    return this.http.get<Page<TraceSummary>>(`${FLOW}/traces`, { params });
  }

  liveTraces(): Observable<TraceSummary[]> {
    return this.http.get<TraceSummary[]>(`${FLOW}/traces/live`);
  }

  trace(traceId: string): Observable<TraceDetail> {
    return this.http.get<TraceDetail>(`${FLOW}/traces/${encodeURIComponent(traceId)}`);
  }

  span(traceId: string, spanId: string): Observable<SpanDetail> {
    return this.http.get<SpanDetail>(
      `${FLOW}/traces/${encodeURIComponent(traceId)}/spans/${encodeURIComponent(spanId)}`,
    );
  }

  /** Emits `null` when the endpoint has no successful execution yet (HTTP 404). */
  blueprint(method: string, endpoint: string): Observable<Blueprint | null> {
    return this.http
      .get<Blueprint>(`${FLOW}/blueprint`, { params: { method, endpoint } })
      .pipe(catchError(() => of(null)));
  }

  clearTraces(): Observable<{ deletedTraces: number }> {
    return this.http.delete<{ deletedTraces: number }>(`${FLOW}/traces`);
  }

  /** Tracked endpoints of the backend, discovered from its Spring MVC mappings. */
  endpoints(): Observable<EndpointInfo[]> {
    return this.http.get<EndpointInfo[]>(`${FLOW}/endpoints`);
  }

  /**
   * Sends an arbitrary request to one of the tracked endpoints. Any HTTP status (including 4xx/5xx) is
   * returned as a result; only network failures error.
   */
  send(method: string, url: string, body: unknown | null, delay?: DemoDelay): Observable<SendResult> {
    let headers = new HttpHeaders({ 'X-Flow-Scenario': 'custom' });
    if (delay && delay.ms > 0 && delay.target) {
      headers = headers.set('X-Demo-Delay-Ms', String(delay.ms)).set('X-Demo-Delay-Target', delay.target);
    }
    const started = performance.now();
    const toResult = (status: number, responseHeaders: HttpHeaders | undefined, text: unknown): SendResult => ({
      traceId: responseHeaders?.get('X-Flow-Trace-Id') ?? null,
      httpStatus: status,
      durationMs: performance.now() - started,
      responsePreview: preview(text),
    });
    return this.http
      .request(method, url, { body: body ?? undefined, headers, observe: 'response', responseType: 'text' })
      .pipe(
        map((response) => toResult(response.status, response.headers, response.body)),
        catchError((error: HttpErrorResponse) => {
          if (error.status > 0) return of(toResult(error.status, error.headers, error.error));
          throw error;
        }),
      );
  }

  scenarios(): Observable<ScenarioInfo[]> {
    return this.http.get<ScenarioInfo[]>('/api/demo/scenarios');
  }

  runScenario(id: string, delayMs: number): Observable<ScenarioResult> {
    return this.http.post<ScenarioResult>(`/api/demo/scenarios/${id}`, null, { params: { delayMs } });
  }

  products(): Observable<Product[]> {
    return this.http.get<Product[]>('/api/products');
  }

  /** Sends a real order request. Business errors (4xx) are returned as results, not thrown. */
  createOrder(lines: OrderLine[], delayMs: number | null): Observable<OrderSubmission> {
    let headers = new HttpHeaders({ 'X-Flow-Scenario': 'custom' });
    if (delayMs && delayMs > 0) headers = headers.set('X-Demo-Delay-Ms', String(delayMs));
    return new Observable<OrderSubmission>((subscriber) => {
      const sub = this.http
        .post('/api/orders', { items: lines }, { headers, observe: 'response' })
        .subscribe({
          next: (response: HttpResponse<unknown>) => {
            subscriber.next({ traceId: response.headers.get('X-Flow-Trace-Id'), httpStatus: response.status });
            subscriber.complete();
          },
          error: (error: { status?: number; headers?: HttpHeaders }) => {
            if (error.status && error.status >= 400 && error.status < 500) {
              subscriber.next({ traceId: error.headers?.get('X-Flow-Trace-Id') ?? null, httpStatus: error.status });
              subscriber.complete();
            } else {
              subscriber.error(error);
            }
          },
        });
      return () => sub.unsubscribe();
    });
  }
}

const PREVIEW_LIMIT = 1200;

/** Pretty-prints JSON responses and truncates long bodies for display. */
function preview(body: unknown): string {
  if (body == null || body === '') return '';
  const text = String(body);
  let pretty = text;
  try {
    pretty = JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    // not JSON: keep as is
  }
  return pretty.length > PREVIEW_LIMIT ? `${pretty.slice(0, PREVIEW_LIMIT)}\n…` : pretty;
}
