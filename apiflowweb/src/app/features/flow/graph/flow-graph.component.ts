import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterRenderEffect,
  computed,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { select } from 'd3-selection';
import 'd3-transition';
import { ZoomBehavior, zoom, zoomIdentity } from 'd3-zoom';
import { COMPONENT_TYPES } from '../../../shared/component-type';
import { formatDuration } from '../../../shared/time';
import { FlowGraph, GraphNode, NODE_HEIGHT, NODE_WIDTH, NodeState } from './graph-builder';

const STATE_LABELS: Record<NodeState, string> = {
  waiting: 'Waiting',
  running: 'Running',
  completed: 'Completed',
  slow: 'Slow',
  error: 'Error',
  skipped: 'Not executed',
};

/**
 * SVG rendering of the execution graph. Layout comes from dagre (see graph-builder); this component
 * handles zoom/pan (d3-zoom), automatic centering, selection, tooltips and path highlighting.
 */
@Component({
  selector: 'app-flow-graph',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './flow-graph.component.html',
  styleUrl: './flow-graph.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FlowGraphComponent {
  readonly graph = input.required<FlowGraph>();
  readonly selectedSpanId = input<string | null>(null);
  readonly activeSpanId = input<string | null>(null);
  readonly selectedKey = input<string | null>(null);
  readonly nodeSelected = output<GraphNode>();

  protected readonly nodeWidth = NODE_WIDTH;
  protected readonly nodeHeight = NODE_HEIGHT;
  protected readonly types = COMPONENT_TYPES;
  protected readonly stateLabels = STATE_LABELS;
  protected readonly duration = formatDuration;
  protected readonly zoomPercent = signal(100);

  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');
  private readonly viewport = viewChild.required<ElementRef<SVGGElement>>('viewport');
  private zoomBehavior: ZoomBehavior<SVGSVGElement, unknown> | null = null;
  private fittedSignature = '';
  private userMoved = false;

  /** Key of the selected node: by span id when executed, otherwise the structural key. */
  protected readonly selected = computed(() => {
    const spanId = this.selectedSpanId();
    const key = this.selectedKey();
    return this.graph().nodes.find((n) => (spanId && n.span?.spanId === spanId) || (!spanId && key === n.key))?.key ?? null;
  });

  /** Selected node and all its ancestors: the path the request took to reach it. */
  protected readonly highlighted = computed(() => {
    const key = this.selected();
    const result = new Set<string>();
    if (!key) return result;
    const byKey = new Map(this.graph().nodes.map((n) => [n.key, n]));
    let current = byKey.get(key);
    while (current && !result.has(current.key)) {
      result.add(current.key);
      current = current.parentKey ? byKey.get(current.parentKey) : undefined;
    }
    return result;
  });

  protected readonly activeKey = computed(() => {
    const spanId = this.activeSpanId();
    return spanId ? this.graph().nodes.find((n) => n.span?.spanId === spanId)?.key ?? null : null;
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    afterRenderEffect(() => {
      const graph = this.graph();
      untracked(() => {
        this.ensureZoom();
        if (graph.signature && graph.signature !== this.fittedSignature) {
          this.fittedSignature = graph.signature;
          this.userMoved = false;
          this.fit(true);
        }
      });
    });

    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    if (typeof ResizeObserver !== 'undefined') {
      const observer = new ResizeObserver(() => {
        if (!this.userMoved) this.fit(false);
      });
      observer.observe(host);
      destroyRef.onDestroy(() => observer.disconnect());
    }
  }

  protected select(node: GraphNode): void {
    this.nodeSelected.emit(node);
  }

  protected tooltip(node: GraphNode): string {
    const lines = [
      `${node.component}.${node.operation}()`,
      `${this.types[node.type].label} · ${this.stateLabels[node.state]}`,
    ];
    if (node.span?.durationMs != null) lines.push(`Duration: ${formatDuration(node.span.durationMs)}`);
    if (node.span?.artificialDelayMs) lines.push(`Includes ${node.span.artificialDelayMs} ms artificial demo delay`);
    if (node.span?.errorMessage) lines.push(`Error: ${node.span.errorMessage}`);
    if (!node.span) {
      lines.push(node.state === 'skipped'
        ? 'Not executed: the request failed before reaching this step'
        : 'Expected step (from the last successful execution)');
    }
    return lines.join('\n');
  }

  zoomBy(factor: number): void {
    if (!this.zoomBehavior) return;
    this.userMoved = true;
    select(this.svg().nativeElement).transition().duration(250).call(this.zoomBehavior.scaleBy, factor);
  }

  fit(animate = true): void {
    const zoomBehavior = this.zoomBehavior;
    const graph = this.graph();
    if (!zoomBehavior || !graph.width) return;
    const svg = this.svg().nativeElement;
    const { width, height } = svg.getBoundingClientRect();
    if (!width || !height) return;
    const padding = 32;
    const scale = Math.min((width - padding) / graph.width, (height - padding) / graph.height, 1.1);
    const k = Math.max(0.2, scale);
    const transform = zoomIdentity
      .translate((width - graph.width * k) / 2, (height - graph.height * k) / 2)
      .scale(k);
    const selection = select(svg);
    if (animate) {
      selection.transition().duration(650).call(zoomBehavior.transform, transform);
    } else {
      selection.call(zoomBehavior.transform, transform);
    }
  }

  recenter(): void {
    this.userMoved = false;
    this.fit(true);
  }

  private ensureZoom(): void {
    if (this.zoomBehavior) return;
    const viewport = this.viewport().nativeElement;
    this.zoomBehavior = zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.2, 2.5])
      .on('zoom', (event) => {
        viewport.setAttribute('transform', event.transform.toString());
        this.zoomPercent.set(Math.round(event.transform.k * 100));
        if (event.sourceEvent) this.userMoved = true;
      });
    select(this.svg().nativeElement).call(this.zoomBehavior).on('dblclick.zoom', null);
  }
}
