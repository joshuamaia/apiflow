import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MetricsStore } from '../../core/metrics.store';
import { HistoryPoint } from '../../core/models';
import { SparklineComponent } from '../../shared/sparkline.component';
import { formatDuration } from '../../shared/time';

interface Card {
  label: string;
  value: string;
  icon: string;
  tone: 'neutral' | 'ok' | 'error' | 'info' | 'warn';
  hint: string;
}

@Component({
  selector: 'app-metric-cards',
  imports: [MatIconModule, MatTooltipModule, SparklineComponent],
  template: `
    <div class="grid">
      @for (card of cards(); track card.label) {
        <div class="card" [class]="'card--' + card.tone" [matTooltip]="card.hint">
          <div class="card__icon"><mat-icon>{{ card.icon }}</mat-icon></div>
          <div class="card__body">
            <span class="card__value">{{ card.value }}</span>
            <span class="card__label">{{ card.label }}</span>
          </div>
        </div>
      }
      <div class="card card--chart">
        <div class="chart-head">
          <span class="card__label">Latency · last {{ store.history().length }} executions</span>
          @if (store.metrics(); as m) {
            <span class="p95">p95 {{ duration(m.p95LatencyMs) }}</span>
          }
        </div>
        @if (store.history().length) {
          <app-sparkline [points]="store.history()" (pick)="open.emit($event)" />
        } @else {
          <div class="empty">No executions yet</div>
        }
      </div>
    </div>
  `,
  styleUrl: './metric-cards.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricCardsComponent {
  protected readonly store = inject(MetricsStore);
  protected readonly duration = formatDuration;
  readonly open = output<HistoryPoint>();

  protected readonly cards = computed<Card[]>(() => {
    const m = this.store.metrics();
    const value = (n: number | undefined) => (m ? (n ?? 0).toLocaleString() : '—');
    return [
      { label: 'Requests traced', value: value(m?.totalRequests), icon: 'stacks', tone: 'info',
        hint: 'Stored traces (subject to the retention limit)' },
      { label: 'Successful', value: value(m?.successfulRequests), icon: 'check_circle', tone: 'ok',
        hint: 'HTTP status below 400' },
      { label: 'Errors', value: value(m?.failedRequests), icon: 'cancel', tone: 'error',
        hint: 'HTTP status 400 or above' },
      { label: 'Avg latency', value: m ? formatDuration(m.avgLatencyMs) : '—', icon: 'timer', tone: 'neutral',
        hint: 'Mean server-side duration of stored traces' },
      { label: 'Max latency', value: m ? formatDuration(m.maxLatencyMs) : '—', icon: 'hourglass_top', tone: 'warn',
        hint: 'Slowest stored trace (demo delays included)' },
      { label: 'Steps executed', value: value(m?.totalSpans), icon: 'account_tree', tone: 'neutral',
        hint: 'Total spans captured across stored traces' },
    ];
  });
}
