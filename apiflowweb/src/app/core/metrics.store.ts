import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, debounceTime, filter, forkJoin, merge, of, startWith, switchMap } from 'rxjs';
import { FlowApiService } from './flow-api.service';
import { FlowStreamService } from './flow-stream.service';
import { HistoryPoint, Metrics } from './models';

/**
 * Aggregated metrics and recent history, refreshed from the backend whenever a trace is persisted or
 * the event stream (re)connects. Values always come from the backend; nothing is computed locally.
 */
@Injectable({ providedIn: 'root' })
export class MetricsStore {
  private readonly api = inject(FlowApiService);
  private readonly stream = inject(FlowStreamService);
  private readonly manual = new Subject<void>();

  readonly metrics = signal<Metrics | null>(null);
  readonly history = signal<HistoryPoint[]>([]);
  readonly loadError = signal(false);

  constructor() {
    merge(
      this.manual,
      this.stream.connected$,
      this.stream.events$.pipe(filter((e) => e.type === 'TRACE_PERSISTED')),
    )
      .pipe(
        startWith(undefined),
        debounceTime(200),
        switchMap(() =>
          forkJoin({ metrics: this.api.metrics(), history: this.api.history(60) }).pipe(catchError(() => of(null))),
        ),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((result) => {
        this.loadError.set(result === null);
        if (result) {
          this.metrics.set(result.metrics);
          this.history.set(result.history);
        }
      });
  }

  refresh(): void {
    this.manual.next();
  }
}
