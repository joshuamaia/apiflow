import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { HistoryPoint } from '../core/models';
import { formatDuration, formatRelative } from './time';

const W = 320;
const H = 72;
const PAD_X = 6;
const PAD_Y = 8;

interface Plotted {
  point: HistoryPoint;
  x: number;
  y: number;
}

/**
 * Latency of the latest executions (one series, so no legend). Failed executions get a distinct ✕
 * marker in addition to the status color, so the state never depends on color alone.
 */
@Component({
  selector: 'app-sparkline',
  template: `
    <div class="wrap" (mouseleave)="hover.set(null)">
      <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" preserveAspectRatio="none" role="img"
           [attr.aria-label]="'Latency of the last ' + points().length + ' executions'">
        <line class="base" [attr.x1]="0" [attr.x2]="width" [attr.y1]="height - padY" [attr.y2]="height - padY" />
        @if (plotted().length > 1) {
          <path class="area" [attr.d]="area()" />
          <path class="line" [attr.d]="line()" />
        }
        @for (p of plotted(); track p.point.traceId) {
          @if (p.point.status === 'ERROR') {
            <path class="mark mark--error" [attr.d]="cross(p)" />
          }
          <rect class="hit" [attr.x]="p.x - step() / 2" y="0" [attr.width]="step()" [attr.height]="height"
                (mouseenter)="hover.set(p)" (click)="pick.emit(p.point)" />
        }
        @if (hover(); as h) {
          <line class="cursor" [attr.x1]="h.x" [attr.x2]="h.x" y1="0" [attr.y2]="height" />
          <circle class="dot" [class.dot--error]="h.point.status === 'ERROR'" [attr.cx]="h.x" [attr.cy]="h.y" r="4" />
        }
      </svg>
      @if (hover(); as h) {
        <div class="tip" [style.left.%]="(h.x / width) * 100">
          <b>{{ duration(h.point.durationMs) }}</b>
          <span>{{ h.point.httpMethod }} {{ h.point.endpoint }} · {{ h.point.status === 'ERROR' ? '✕ error' : '✓ ok' }} {{ h.point.httpStatus }}</span>
          <span class="muted">{{ relative(h.point.startedAt) }}{{ h.point.scenario ? ' · ' + h.point.scenario : '' }} · click to open</span>
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .wrap { position: relative; }
    svg { display: block; width: 100%; height: 72px; overflow: visible; cursor: pointer; }
    .base { stroke: var(--border); stroke-width: 1; }
    .area { fill: rgba(59, 130, 246, 0.14); }
    .line { fill: none; stroke: var(--c-running); stroke-width: 2; vector-effect: non-scaling-stroke; stroke-linejoin: round; }
    .mark--error { stroke: var(--c-error); stroke-width: 2; vector-effect: non-scaling-stroke; stroke-linecap: round; }
    .hit { fill: transparent; }
    .cursor { stroke: var(--muted); stroke-width: 1; stroke-dasharray: 2 3; vector-effect: non-scaling-stroke; }
    .dot { fill: var(--c-running); stroke: var(--panel); stroke-width: 2; vector-effect: non-scaling-stroke; }
    .dot--error { fill: var(--c-error); }
    .tip {
      position: absolute; bottom: calc(100% + 6px); transform: translateX(-50%); z-index: 2;
      display: flex; flex-direction: column; gap: 2px; padding: 6px 9px; white-space: nowrap;
      font-size: 11.5px; color: var(--text); background: #0b0f14; border: 1px solid var(--border);
      border-radius: 8px; pointer-events: none; box-shadow: 0 6px 24px rgba(0,0,0,.45);
    }
    .tip b { font-size: 13px; font-variant-numeric: tabular-nums; }
    .muted { color: var(--muted); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SparklineComponent {
  readonly points = input.required<HistoryPoint[]>();
  readonly pick = output<HistoryPoint>();

  protected readonly width = W;
  protected readonly height = H;
  protected readonly padY = PAD_Y;
  protected readonly hover = signal<Plotted | null>(null);
  protected readonly duration = formatDuration;
  protected readonly relative = (iso: string) => formatRelative(iso);

  protected readonly step = computed(() => (W - PAD_X * 2) / Math.max(1, this.points().length - 1));

  protected readonly plotted = computed<Plotted[]>(() => {
    const points = this.points().filter((p) => p.durationMs != null);
    const max = Math.max(1, ...points.map((p) => p.durationMs!));
    const step = (W - PAD_X * 2) / Math.max(1, points.length - 1);
    return points.map((point, i) => ({
      point,
      x: points.length === 1 ? W / 2 : PAD_X + i * step,
      y: H - PAD_Y - (point.durationMs! / max) * (H - PAD_Y * 2),
    }));
  });

  protected readonly line = computed(() =>
    this.plotted().map((p, i) => `${i ? 'L' : 'M'} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join(' '),
  );

  protected readonly area = computed(() => {
    const plotted = this.plotted();
    if (plotted.length < 2) return '';
    const base = H - PAD_Y;
    return `${this.line()} L ${plotted.at(-1)!.x} ${base} L ${plotted[0].x} ${base} Z`;
  });

  protected cross(p: Plotted): string {
    const s = 4;
    return `M ${p.x - s} ${p.y - s} L ${p.x + s} ${p.y + s} M ${p.x + s} ${p.y - s} L ${p.x - s} ${p.y + s}`;
  }
}
