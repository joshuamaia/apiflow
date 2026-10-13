import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FlowApiService } from '../../core/flow-api.service';
import { FlowStreamService } from '../../core/flow-stream.service';
import { MetricsStore } from '../../core/metrics.store';
import { FlowEvent, HistoryPoint } from '../../core/models';
import { formatDuration } from '../../shared/time';
import { FlowDetailsComponent } from '../flow/details/flow-details.component';
import { FlowGraphComponent } from '../flow/graph/flow-graph.component';
import { GraphNode } from '../flow/graph/graph-builder';
import { FlowPlayer } from '../flow/playback/flow-player';
import { PlaybackControlsComponent } from '../flow/playback/playback-controls.component';
import { EventFeedComponent } from './event-feed.component';
import { HistoryListComponent } from './history-list.component';
import { MetricCardsComponent } from './metric-cards.component';
import { Execution, ScenarioRunnerComponent } from './scenario-runner.component';

@Component({
  selector: 'app-dashboard',
  imports: [
    MatIconModule, MatSlideToggleModule, MatTooltipModule, MetricCardsComponent, ScenarioRunnerComponent,
    FlowGraphComponent, PlaybackControlsComponent, FlowDetailsComponent, HistoryListComponent, EventFeedComponent,
  ],
  providers: [FlowPlayer],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  protected readonly player = inject(FlowPlayer);
  private readonly api = inject(FlowApiService);
  private readonly stream = inject(FlowStreamService);
  private readonly store = inject(MetricsStore);
  private readonly snackBar = inject(MatSnackBar);

  /** `?trace=<id>` opens and replays a given execution (bound by the router). */
  readonly trace = input<string | undefined>();

  protected readonly followLive = signal(true);
  protected readonly duration = formatDuration;
  private openedInitial = false;

  protected readonly banner = computed(() => {
    const final = this.player.finalTrace();
    if (!final) return null;
    const failed = this.player.spans().filter((s) => s.status === 'ERROR');
    const origin = failed.length ? failed.reduce((a, b) => (b.sequence > a.sequence ? b : a)) : null;
    return {
      ok: final.status !== 'ERROR',
      text: final.status !== 'ERROR'
        ? `Completed · HTTP ${final.httpStatus} · ${formatDuration(final.durationMs)}`
        : `Failed${origin ? ` at ${origin.component}` : ''} · HTTP ${final.httpStatus} · ${formatDuration(final.durationMs)}`,
    };
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.stream.events$.pipe(takeUntilDestroyed(destroyRef)).subscribe((event) => this.onEvent(event));
    this.stream.connected$.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => this.resync());

    effect(() => {
      const traceId = this.trace();
      if (traceId) {
        untracked(() => {
          this.openedInitial = true;
          this.openTrace(traceId, true);
        });
      }
    });

    // Without an explicit trace, show the latest stored execution once history is available.
    effect(() => {
      const history = this.store.history();
      untracked(() => {
        if (!this.openedInitial && history.length && this.player.mode() === 'idle') {
          this.openedInitial = true;
          this.openTrace(history[history.length - 1].traceId, false);
        }
      });
    });
  }

  protected onNodeSelected(node: GraphNode): void {
    this.player.select(node.span?.spanId ?? null, node.key);
  }

  protected openHistory(point: HistoryPoint): void {
    this.openTrace(point.traceId, true);
  }

  protected onExecuted(execution: Execution): void {
    if (!execution.traceId) return;
    // When following live, the SSE stream already switched to this request.
    if (this.player.trace()?.traceId !== execution.traceId) {
      this.openTrace(execution.traceId, true);
    }
  }

  private onEvent(event: FlowEvent): void {
    if (event.type === 'TRACE_STARTED' && event.trace && this.followLive()) {
      this.openedInitial = true;
      this.player.startLive(event.trace);
      this.loadReference(event.trace.httpMethod, event.trace.endpoint);
      return;
    }
    this.player.push(event);
    if (event.type === 'TRACE_COMPLETED' && event.trace && event.traceId === this.player.trace()?.traceId
      && event.trace.status === 'ERROR' && !this.player.reference()) {
      this.loadReference(event.trace.httpMethod, event.trace.endpoint);
    }
  }

  /** After a reconnection, rebuild the followed trace from its consolidated server-side state. */
  private resync(): void {
    const current = this.player.trace();
    if (this.player.mode() === 'live' && current && !this.player.complete()) {
      this.api.trace(current.traceId).subscribe({
        next: (detail) => this.player.load(detail, false),
        error: () => undefined,
      });
    }
  }

  private openTrace(traceId: string, autoplay: boolean): void {
    this.api.trace(traceId).subscribe({
      next: (detail) => {
        this.player.load(detail, autoplay);
        if (detail.trace.status !== 'SUCCESS') {
          this.player.clearReference();
          this.loadReference(detail.trace.httpMethod, detail.trace.endpoint);
        }
      },
      error: () => this.snackBar.open('Trace not found (it may have been removed by retention)', 'Dismiss', { duration: 4000 }),
    });
  }

  private loadReference(method: string, endpoint: string): void {
    const traceId = this.player.trace()?.traceId;
    this.api.blueprint(method, endpoint).subscribe((blueprint) => {
      if (blueprint && this.player.trace()?.traceId === traceId) {
        this.player.setReference(blueprint.referenceTraceId, blueprint.spans);
      }
    });
  }
}
