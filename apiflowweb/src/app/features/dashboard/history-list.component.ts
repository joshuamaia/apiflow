import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MetricsStore } from '../../core/metrics.store';
import { HistoryPoint } from '../../core/models';
import { formatDuration, formatRelative } from '../../shared/time';

@Component({
  selector: 'app-history-list',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <header class="head">
      <h2>Recent executions</h2>
      <a mat-button routerLink="/traces">View all<mat-icon iconPositionEnd>arrow_forward</mat-icon></a>
    </header>
    <ul class="list">
      @for (item of items(); track item.traceId) {
        <li>
          <button class="row" [class.row--current]="item.traceId === currentTraceId()" (click)="open.emit(item)"
                  matTooltip="Replay this execution" matTooltipPosition="left">
            <span class="status" [class.status--error]="item.status === 'ERROR'">
              <mat-icon>{{ item.status === 'ERROR' ? 'cancel' : 'check_circle' }}</mat-icon>{{ item.httpStatus }}
            </span>
            <span class="what">
              <span class="method">{{ item.httpMethod }}</span>
              <span class="endpoint">{{ item.endpoint }}</span>
              @if (item.scenario) { <span class="tag">{{ item.scenario }}</span> }
            </span>
            <span class="time">{{ duration(item.durationMs) }}</span>
            <span class="when">{{ relative(item.startedAt) }}</span>
          </button>
        </li>
      } @empty {
        <li class="empty">No executions yet — run a scenario.</li>
      }
    </ul>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 0; }
    .head { display: flex; align-items: center; justify-content: space-between; padding: 10px 8px 6px 16px; }
    h2 { margin: 0; font-size: 15px; font-weight: 600; }
    .list { list-style: none; margin: 0; padding: 0 8px 10px; overflow: auto; max-height: 300px; }
    .row {
      display: grid; grid-template-columns: 64px 1fr 72px 64px; align-items: center; gap: 8px; width: 100%;
      padding: 7px 8px; border: 1px solid transparent; border-radius: 8px; background: none; color: var(--text);
      font: 12.5px var(--font-body); text-align: left; cursor: pointer;
      &:hover { background: rgba(255,255,255,.04); }
      &--current { border-color: rgba(59,130,246,.45); background: rgba(59,130,246,.08); }
    }
    .status {
      display: inline-flex; align-items: center; gap: 4px; font: 600 12px var(--font-mono); color: #86efac;
      mat-icon { font-size: 16px; width: 16px; height: 16px; }
      &--error { color: #fca5a5; }
    }
    .what { display: flex; align-items: center; gap: 6px; min-width: 0; }
    .method { font: 700 10.5px var(--font-mono); color: #93c5fd; }
    .endpoint { font-family: var(--font-mono); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .tag { flex: none; padding: 0 6px; border-radius: 999px; font-size: 10.5px; color: var(--muted); border: 1px solid var(--border); }
    .time { font: 12px var(--font-mono); text-align: right; }
    .when { font-size: 11.5px; color: var(--muted); text-align: right; }
    .empty { padding: 24px; text-align: center; color: var(--muted); }
    @media (max-width: 640px) { .row { grid-template-columns: 56px 1fr 64px; } .when { display: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryListComponent {
  private readonly store = inject(MetricsStore);
  readonly currentTraceId = input<string | null>(null);
  readonly open = output<HistoryPoint>();

  protected readonly items = computed(() => [...this.store.history()].reverse().slice(0, 25));
  protected readonly duration = formatDuration;
  protected readonly relative = (iso: string) => formatRelative(iso);
}
