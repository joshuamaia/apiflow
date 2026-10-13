import { orderTrace, span } from '../testing/spans';
import { assignPathKeys, buildGraph } from './graph-builder';

describe('graph-builder', () => {
  it('assigns stable structural keys, numbering repeated steps', () => {
    const spans = [
      span('c', null, 'Controller', 0, 0, 5),
      span('a', 'c', 'Service', 1, 1, 2),
      span('b', 'c', 'Service', 2, 2, 3),
    ];
    const keys = assignPathKeys(spans);
    expect(keys.get('c')).toBe('root/Controller.op#0');
    expect(keys.get('a')).toBe('root/Controller.op#0/Service.op#0');
    expect(keys.get('b')).toBe('root/Controller.op#0/Service.op#1');
  });

  it('builds one node per span with edges following the span hierarchy', () => {
    const graph = buildGraph(orderTrace().spans, null, 'success');

    expect(graph.nodes.map((n) => n.component)).toEqual([
      'OrderController', 'CreateOrderUseCase', 'ProductService', 'StockService', 'OrderRepository',
    ]);
    expect(graph.edges).toHaveLength(4);
    const useCase = graph.nodes.find((n) => n.component === 'CreateOrderUseCase')!;
    expect(graph.edges.filter((e) => e.from === useCase.key)).toHaveLength(3);
    expect(graph.edges.map((e) => e.order)).toEqual([2, 3, 4, 5]);
  });

  it('lays out nodes left to right by depth without overlaps', () => {
    const graph = buildGraph(orderTrace().spans, null, 'success');
    const controller = graph.nodes[0];
    const services = graph.nodes.slice(2);

    expect(controller.depth).toBe(0);
    expect(services.every((n) => n.depth === 2)).toBe(true);
    expect(services.every((n) => n.x > controller.x)).toBe(true);
    const ys = services.map((n) => n.y).sort((a, b) => a - b);
    for (let i = 1; i < ys.length; i++) expect(ys[i] - ys[i - 1]).toBeGreaterThanOrEqual(80);
  });

  it('maps span status to node colors, flagging slow spans', () => {
    const spans = [
      span('c', null, 'Controller', 0, 0, null),
      span('a', 'c', 'Fast', 1, 0, 1),
      span('b', 'c', 'Slow', 2, 1, 900, { slow: true }),
      span('e', 'c', 'Broken', 3, 900, 901, { status: 'ERROR' }),
    ];
    const states = buildGraph(spans, null, 'running').nodes.map((n) => n.state);
    expect(states).toEqual(['running', 'completed', 'slow', 'error']);
  });

  it('shows steps of the reference execution as waiting while running', () => {
    const reference = orderTrace().spans;
    const partial = reference.slice(0, 3);

    const graph = buildGraph(partial, reference, 'running');

    const waiting = graph.nodes.filter((n) => n.state === 'waiting').map((n) => n.component);
    expect(waiting).toEqual(['StockService', 'OrderRepository']);
    expect(graph.edges.filter((e) => e.state === 'pending')).toHaveLength(2);
  });

  it('marks steps that never ran as skipped when the request failed', () => {
    const reference = orderTrace().spans;
    const failed = [
      ...reference.slice(0, 3),
      span('s', 'u', 'StockService', 3, 1.1, 2.0, { status: 'ERROR' }),
    ];

    const graph = buildGraph(failed, reference, 'error');

    const skipped = graph.nodes.filter((n) => n.state === 'skipped');
    expect(skipped.map((n) => n.component)).toEqual(['OrderRepository']);
    expect(skipped[0].span).toBeNull();
  });

  it('drops unexecuted reference steps when the request succeeded', () => {
    const reference = orderTrace().spans;
    const graph = buildGraph(reference.slice(0, 3), reference, 'success');
    expect(graph.nodes).toHaveLength(3);
  });

  it('returns an empty graph without spans', () => {
    expect(buildGraph([], null, 'running').nodes).toEqual([]);
  });
});
