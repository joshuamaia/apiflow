import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FlowApiService } from '../../core/flow-api.service';
import { MetricsStore } from '../../core/metrics.store';
import { COMPONENT_TYPES } from '../../shared/component-type';
import { formatDuration } from '../../shared/time';

/** Aggregated duration per component operation, computed by the backend over stored spans. */
@Component({
  selector: 'app-components-page',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <div class="page">
      <header class="title">
        <div>
          <h1>Components</h1>
          <p>Every instrumented operation, aggregated over the stored spans (square-root scale).
            <span class="key"><i class="key__median"></i>median</span><span class="key"><i class="key__max"></i>max</span></p>
        </div>
        <button mat-stroked-button (click)="stats.reload()"><mat-icon>refresh</mat-icon>Refresh</button>
      </header>
      <section class="panel">
        <div class="row row--head">
          <span>Component</span><span class="num">Runs</span><span class="num">Errors</span>
          <span class="num">Median</span><span class="num">Avg</span><span class="num">Max</span><span>Distribution</span>
        </div>
        @for (s of stats.value() ?? []; track s.component + s.operation) {
          <div class="row">
            <span class="name">
              <mat-icon>{{ types[s.type].icon }}</mat-icon>
              <span><b>{{ s.component }}</b><span class="op">{{ s.operation }}()</span></span>
            </span>
            <span class="num mono">{{ s.executions }}</span>
            <span class="num mono" [class.err]="s.errors > 0">{{ s.errors }}</span>
            <span class="num mono">{{ duration(s.medianMs) }}</span>
            <span class="num mono">{{ duration(s.avgMs) }}</span>
            <span class="num mono">{{ duration(s.maxMs) }}</span>
            <span class="bar" [matTooltip]="'median ' + duration(s.medianMs) + ' · max ' + duration(s.maxMs)">
              <span class="bar__max" [style.width.%]="scale(s.maxMs)"></span>
              <span class="bar__median" [style.width.%]="scale(s.medianMs)"></span>
            </span>
          </div>
        } @empty {
          <div class="empty">No spans stored yet.</div>
        }
      </section>
    </div>
  `,
  styles: `
    :host { display: block; }
    .page { display: flex; flex-direction: column; gap: 14px; padding: 20px; }
    .title { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap;
      h1 { margin: 0; font-size: 22px; font-weight: 650; } p { margin: 4px 0 0; color: var(--muted); } }
    .panel { border-radius: 16px; background: var(--panel); border: 1px solid var(--border); overflow: auto; }
    .row { display: grid; grid-template-columns: minmax(220px, 2fr) 60px 60px 84px 84px 84px minmax(160px, 2fr);
      gap: 12px; align-items: center; min-width: 860px; padding: 10px 16px; border-top: 1px solid var(--border); }
    .row--head { border-top: 0; font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: .04em; }
    .name { display: flex; align-items: center; gap: 10px; min-width: 0;
      mat-icon { color: var(--c-running); } b { display: block; font-weight: 600; }
      .op { font: 11.5px var(--font-mono); color: var(--muted); } }
    .num { text-align: right; } .mono { font: 12px var(--font-mono); } .err { color: #fca5a5; }
    .bar { position: relative; height: 10px; border-radius: 5px; background: rgba(255,255,255,.04); }
    .bar__max, .bar__median { position: absolute; left: 0; top: 0; bottom: 0; border-radius: 5px; }
    .bar__max { background: rgba(234, 179, 8, .35); }
    .bar__median { background: var(--c-running); border-right: 2px solid var(--panel); }
    .empty { padding: 32px; text-align: center; color: var(--muted); }
    .key { display: inline-flex; align-items: center; gap: 5px; margin-left: 12px; font-size: 12px;
      i { width: 12px; height: 8px; border-radius: 3px; } }
    .key__median { background: var(--c-running); } .key__max { background: rgba(234, 179, 8, .35); }
    @media (max-width: 640px) { .page { padding: 12px 16px; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComponentsPageComponent {
  private readonly api = inject(FlowApiService);
  private readonly metrics = inject(MetricsStore);
  protected readonly types = COMPONENT_TYPES;
  protected readonly duration = formatDuration;

  protected readonly stats = rxResource({
    params: () => this.metrics.metrics()?.totalSpans ?? 0,
    stream: () => this.api.componentMetrics(),
  });

  private readonly max = computed(() => Math.max(0.001, ...(this.stats.value() ?? []).map((s) => s.maxMs)));

  protected scale(ms: number): number {
    // square-root scale keeps sub-millisecond steps visible next to multi-second outliers
    return Math.max(1, (Math.sqrt(ms) / Math.sqrt(this.max())) * 100);
  }
}
