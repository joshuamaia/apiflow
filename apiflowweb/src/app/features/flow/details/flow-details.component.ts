import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, of } from 'rxjs';
import { FlowApiService } from '../../../core/flow-api.service';
import { SpanDto } from '../../../core/models';
import { COMPONENT_TYPES } from '../../../shared/component-type';
import { formatClock, formatDuration, isoToEpochMs } from '../../../shared/time';
import { GraphNode, NodeState, nodeStateOf } from '../graph/graph-builder';
import { FlowPlayer } from '../playback/flow-player';

interface WaterfallRow {
  span: SpanDto;
  depth: number;
  state: NodeState;
  left: number;
  width: number;
}

/** Right-hand panel: trace overview with a waterfall when nothing is selected, span details otherwise. */
@Component({
  selector: 'app-flow-details',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './flow-details.component.html',
  styleUrl: './flow-details.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlowDetailsComponent {
  protected readonly player = inject(FlowPlayer);
  private readonly api = inject(FlowApiService);
  private readonly clipboard = inject(Clipboard);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly types = COMPONENT_TYPES;
  protected readonly duration = formatDuration;
  protected readonly clock = formatClock;
  protected readonly abs = Math.abs;

  /** Selected span, as currently known by the player (updates live). */
  protected readonly span = computed<SpanDto | null>(() => {
    const id = this.player.selectedSpanId();
    return id ? this.player.spans().find((s) => s.spanId === id) ?? null : null;
  });

  /** Selected step that was not executed in this request. */
  protected readonly ghost = computed<GraphNode | null>(() => {
    const key = this.player.selectedKey();
    return key ? this.player.graph().nodes.find((n) => n.key === key && !n.span) ?? null : null;
  });

  protected readonly state = computed<NodeState | null>(() => {
    const span = this.span();
    return span ? nodeStateOf(span) : null;
  });

  protected readonly ancestors = computed<SpanDto[]>(() => {
    const span = this.span();
    if (!span) return [];
    const byId = new Map(this.player.spans().map((s) => [s.spanId, s]));
    const chain: SpanDto[] = [];
    let parent = span.parentSpanId ? byId.get(span.parentSpanId) : undefined;
    while (parent && chain.length < 50) {
      chain.unshift(parent);
      parent = parent.parentSpanId ? byId.get(parent.parentSpanId) : undefined;
    }
    return chain;
  });

  protected readonly children = computed<SpanDto[]>(() => {
    const span = this.span();
    return span ? this.player.spans().filter((s) => s.parentSpanId === span.spanId) : [];
  });

  /** Server-side comparison with normal executions of the same operation (once the span finished). */
  protected readonly comparison = rxResource({
    params: () => {
      const span = this.span();
      return span && span.status !== 'RUNNING' ? { traceId: span.traceId, spanId: span.spanId } : undefined;
    },
    stream: ({ params }) => this.api.span(params.traceId, params.spanId).pipe(catchError(() => of(null))),
  });

  protected readonly trace = computed(() => this.player.finalTrace() ?? this.player.trace());

  protected readonly waterfall = computed<WaterfallRow[]>(() => {
    const spans = [...this.player.spans()].sort((a, b) => a.sequence - b.sequence);
    const trace = this.trace();
    const origin = isoToEpochMs(trace?.startedAt) ?? 0;
    const ends = spans.map((s) => (isoToEpochMs(s.finishedAt) ?? isoToEpochMs(s.startedAt) ?? origin) - origin);
    const total = Math.max(trace?.durationMs ?? 0, ...ends, 0.001);
    const depth = new Map<string, number>();
    return spans.map((span) => {
      const d = span.parentSpanId && depth.has(span.parentSpanId) ? depth.get(span.parentSpanId)! + 1 : 0;
      depth.set(span.spanId, d);
      const start = Math.max(0, (isoToEpochMs(span.startedAt) ?? origin) - origin);
      const width = span.durationMs != null ? (span.durationMs / total) * 100 : 1.5;
      return {
        span,
        depth: d,
        state: nodeStateOf(span),
        left: Math.min(98.5, (start / total) * 100),
        width: Math.max(0.8, Math.min(100, width)),
      };
    });
  });

  protected readonly slowest = computed(() => {
    const finished = this.player.spans().filter((s) => s.durationMs != null && s.type !== 'CONTROLLER' && s.type !== 'USE_CASE');
    return finished.reduce<SpanDto | null>((max, s) => (!max || s.durationMs! > max.durationMs! ? s : max), null);
  });

  /** The step where the failure originated: the last failed span to start. */
  protected readonly failure = computed(() => {
    const failed = this.player.spans().filter((s) => s.status === 'ERROR');
    return failed.length ? failed.reduce((a, b) => (b.sequence > a.sequence ? b : a)) : null;
  });

  protected readonly notExecuted = computed(() => this.player.graph().nodes.filter((n) => n.state === 'skipped').length);

  protected selectSpan(span: SpanDto): void {
    this.player.select(span.spanId);
  }

  protected jumpTo(span: SpanDto): void {
    this.player.select(span.spanId);
    this.player.seekToSpan(span.spanId, true);
  }

  protected copy(value: string | null): void {
    if (value && this.clipboard.copy(value)) {
      this.snackBar.open('Copied to clipboard', undefined, { duration: 1200 });
    }
  }

  protected shortType(errorType: string | null): string {
    return errorType ? errorType.substring(errorType.lastIndexOf('.') + 1) : '';
  }
}
