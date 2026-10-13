import { Graph, layout } from '@dagrejs/dagre';
import { ComponentType, SpanDto } from '../../../core/models';

/** Visual state of a node. */
export type NodeState = 'waiting' | 'running' | 'completed' | 'slow' | 'error' | 'skipped';
/** Visual state of a connection. */
export type EdgeState = 'pending' | 'active' | 'done' | 'error' | 'skipped';
/** Where the flow is: still executing, or finished with a final status. */
export type FlowPhase = 'running' | 'success' | 'error';

export interface GraphNode {
  /** Structural key (path from the root), stable between executions of the same endpoint. */
  key: string;
  parentKey: string | null;
  /** Span captured in this execution; `null` for steps known only from the reference execution. */
  span: SpanDto | null;
  component: string;
  operation: string;
  type: ComponentType;
  sequence: number;
  state: NodeState;
  depth: number;
  x: number;
  y: number;
}

export interface GraphEdge {
  key: string;
  from: string;
  to: string;
  state: EdgeState;
  path: string;
  labelX: number;
  labelY: number;
  /** Execution order of the target step (1-based), when executed. */
  order: number | null;
}

export interface FlowGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
  width: number;
  height: number;
  /** Changes only when the set of nodes changes (used to decide when to re-center). */
  signature: string;
}

export const NODE_WIDTH = 210;
export const NODE_HEIGHT = 80;
const RANK_SEP = 58;
const NODE_SEP = 18;
const MARGIN = 24;

export const EMPTY_GRAPH: FlowGraph = { nodes: [], edges: [], width: 0, height: 0, signature: '' };

/**
 * Assigns each span a structural key: the chain of `component.operation#occurrence` from the root.
 * Two executions of the same endpoint produce the same keys for the same steps, which lets us align a
 * running/failed execution with a reference (successful) execution.
 */
export function assignPathKeys(spans: readonly SpanDto[]): Map<string, string> {
  const ordered = [...spans].sort((a, b) => a.sequence - b.sequence);
  const keys = new Map<string, string>();
  const occurrences = new Map<string, number>();
  for (const span of ordered) {
    const parentKey = (span.parentSpanId && keys.get(span.parentSpanId)) || 'root';
    const base = `${parentKey}/${span.component}.${span.operation}`;
    const occurrence = occurrences.get(base) ?? 0;
    occurrences.set(base, occurrence + 1);
    keys.set(span.spanId, `${base}#${occurrence}`);
  }
  return keys;
}

export function nodeStateOf(span: SpanDto): NodeState {
  switch (span.status) {
    case 'RUNNING':
      return 'running';
    case 'ERROR':
      return 'error';
    default:
      return span.slow ? 'slow' : 'completed';
  }
}

function edgeStateOf(target: NodeState): EdgeState {
  switch (target) {
    case 'running':
      return 'active';
    case 'error':
      return 'error';
    case 'waiting':
      return 'pending';
    case 'skipped':
      return 'skipped';
    default:
      return 'done';
  }
}

/**
 * Builds the graph of an execution.
 *
 * @param spans     spans known so far (running or finished)
 * @param reference spans of the last successful execution of the same endpoint, if any. Steps present
 *                  there but not (yet) here are shown as `waiting` while the flow runs and as `skipped`
 *                  when it failed. They are dropped for successful flows.
 */
export function buildGraph(spans: readonly SpanDto[], reference: readonly SpanDto[] | null, phase: FlowPhase): FlowGraph {
  const nodes = new Map<string, GraphNode>();
  const actualKeys = assignPathKeys(spans);

  for (const span of [...spans].sort((a, b) => a.sequence - b.sequence)) {
    const key = actualKeys.get(span.spanId)!;
    const parentKey = span.parentSpanId ? actualKeys.get(span.parentSpanId) ?? null : null;
    nodes.set(key, {
      key,
      parentKey,
      span,
      component: span.component,
      operation: span.operation,
      type: span.type,
      sequence: span.sequence,
      state: nodeStateOf(span),
      depth: 0,
      x: 0,
      y: 0,
    });
  }

  if (reference && phase !== 'success') {
    const referenceKeys = assignPathKeys(reference);
    for (const span of [...reference].sort((a, b) => a.sequence - b.sequence)) {
      const key = referenceKeys.get(span.spanId)!;
      if (nodes.has(key)) continue;
      const parentKey = span.parentSpanId ? referenceKeys.get(span.parentSpanId) ?? null : null;
      if (parentKey && !nodes.has(parentKey)) continue;
      nodes.set(key, {
        key,
        parentKey,
        span: null,
        component: span.component,
        operation: span.operation,
        type: span.type,
        sequence: 10_000 + span.sequence,
        state: phase === 'running' ? 'waiting' : 'skipped',
        depth: 0,
        x: 0,
        y: 0,
      });
    }
  }

  return layoutGraph([...nodes.values()]);
}

function layoutGraph(nodes: GraphNode[]): FlowGraph {
  if (nodes.length === 0) return EMPTY_GRAPH;

  const byKey = new Map(nodes.map((n) => [n.key, n]));
  const g = new Graph();
  g.setGraph({ rankdir: 'LR', ranksep: RANK_SEP, nodesep: NODE_SEP, marginx: MARGIN, marginy: MARGIN });
  g.setDefaultEdgeLabel(() => ({}));

  const ordered = [...nodes].sort((a, b) => a.sequence - b.sequence);
  for (const node of ordered) {
    g.setNode(node.key, { width: NODE_WIDTH, height: NODE_HEIGHT });
  }
  for (const node of ordered) {
    if (node.parentKey && byKey.has(node.parentKey)) {
      g.setEdge(node.parentKey, node.key);
    }
  }
  layout(g);

  for (const node of ordered) {
    const position = g.node(node.key);
    node.x = position.x - NODE_WIDTH / 2;
    node.y = position.y - NODE_HEIGHT / 2;
    node.depth = depthOf(node, byKey);
  }

  const executed = ordered.filter((n) => n.span).map((n) => n.key);
  const edges: GraphEdge[] = [];
  for (const node of ordered) {
    const parent = node.parentKey ? byKey.get(node.parentKey) : undefined;
    if (!parent) continue;
    const x1 = parent.x + NODE_WIDTH;
    const y1 = parent.y + NODE_HEIGHT / 2;
    const x2 = node.x;
    const y2 = node.y + NODE_HEIGHT / 2;
    const bend = Math.max(36, (x2 - x1) * 0.5);
    const index = executed.indexOf(node.key);
    edges.push({
      key: `${parent.key}->${node.key}`,
      from: parent.key,
      to: node.key,
      state: edgeStateOf(node.state),
      path: `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`,
      labelX: (x1 + x2) / 2,
      labelY: (y1 + y2) / 2,
      order: index >= 0 ? index + 1 : null,
    });
  }

  const graph = g.graph();
  return {
    nodes: ordered,
    edges,
    width: graph.width ?? 0,
    height: graph.height ?? 0,
    signature: ordered.map((n) => n.key).sort().join('|'),
  };
}

function depthOf(node: GraphNode, byKey: Map<string, GraphNode>): number {
  let depth = 0;
  let current = node.parentKey ? byKey.get(node.parentKey) : undefined;
  while (current && depth < 100) {
    depth++;
    current = current.parentKey ? byKey.get(current.parentKey) : undefined;
  }
  return depth;
}
