import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, catchError, debounceTime, filter, map, merge, of, startWith, switchMap, tap } from 'rxjs';
import { FlowApiService } from '../../core/flow-api.service';
import { FlowStreamService } from '../../core/flow-stream.service';
import { MetricsStore } from '../../core/metrics.store';
import { Page, TraceStatus, TraceSummary } from '../../core/models';
import { formatClock, formatDuration, formatRelative } from '../../shared/time';

@Component({
  selector: 'app-traces-page',
  imports: [
    ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatPaginatorModule,
    MatProgressBarModule, MatSelectModule, MatTableModule, MatTooltipModule,
  ],
  templateUrl: './traces-page.component.html',
  styleUrl: './traces-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TracesPageComponent {
  private readonly api = inject(FlowApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly metrics = inject(MetricsStore);
  private readonly reload$ = new Subject<void>();

  protected readonly columns = ['status', 'request', 'scenario', 'duration', 'spans', 'started', 'actions'];
  protected readonly page = signal<Page<TraceSummary> | null>(null);
  protected readonly loading = signal(false);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);
  protected readonly duration = formatDuration;
  protected readonly clock = formatClock;
  protected readonly relative = (iso: string) => formatRelative(iso);

  protected readonly filters = inject(NonNullableFormBuilder).group({
    status: [null as TraceStatus | null],
    method: [null as string | null],
    endpoint: [''],
    scenario: [null as string | null],
    minDurationMs: [null as number | null],
  });

  constructor() {
    const stream = inject(FlowStreamService);
    merge(
      this.filters.valueChanges.pipe(debounceTime(300), tap(() => this.pageIndex.set(0))),
      this.reload$,
      stream.events$.pipe(filter((e) => e.type === 'TRACE_PERSISTED' && this.pageIndex() === 0), debounceTime(500)),
    )
      .pipe(
        startWith(undefined),
        tap(() => this.loading.set(true)),
        switchMap(() => {
          const f = this.filters.getRawValue();
          return this.api
            .traces({ ...f, page: this.pageIndex(), size: this.pageSize() })
            .pipe(catchError(() => of(null)));
        }),
        map((page) => page),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((page) => {
        this.loading.set(false);
        if (page) this.page.set(page);
      });
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.reload$.next();
  }

  protected open(trace: TraceSummary): void {
    this.router.navigate(['/'], { queryParams: { trace: trace.traceId } });
  }

  protected resetFilters(): void {
    this.filters.reset();
  }

  protected clear(): void {
    this.snackBar
      .open('Delete all stored traces?', 'Delete', { duration: 6000 })
      .onAction()
      .pipe(switchMap(() => this.api.clearTraces()))
      .subscribe((result) => {
        this.snackBar.open(`${result.deletedTraces} traces deleted`, undefined, { duration: 2500 });
        this.reload$.next();
        this.metrics.refresh();
      });
  }
}
