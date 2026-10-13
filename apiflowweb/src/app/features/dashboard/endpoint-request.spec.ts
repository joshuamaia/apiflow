import { EndpointInfo, SpanDto } from '../../core/models';
import { bodyTemplate, buildUrl, defaultDelayTarget, delayOptions, delayTargets } from './endpoint-request';

const endpoint = (overrides: Partial<EndpointInfo>): EndpointInfo => ({
  method: 'GET',
  path: '/api/products/{id}',
  pathVariables: ['id'],
  queryParams: [],
  hasBody: false,
  bodyExample: null,
  handler: 'ProductController.get',
  instrumented: true,
  entryStep: 'ProductController.get',
  ...overrides,
});

describe('endpoint-request', () => {
  it('substitutes and encodes path variables', () => {
    expect(buildUrl(endpoint({}), { id: '1' }, {})).toBe('/api/products/1');
    expect(buildUrl(endpoint({}), { id: 'a b/c' }, {})).toBe('/api/products/a%20b%2Fc');
  });

  it('supports variables with regex constraints', () => {
    expect(buildUrl(endpoint({ path: '/api/orders/{id:\\d+}' }), { id: '7' }, {})).toBe('/api/orders/7');
  });

  it('adds non-empty query parameters only', () => {
    const e = endpoint({ path: '/api/orders', pathVariables: [] });
    expect(buildUrl(e, {}, { limit: '20', status: ' ' })).toBe('/api/orders?limit=20');
    expect(buildUrl(e, {}, { q: 'a&b' })).toBe('/api/orders?q=a%26b');
  });

  it('pretty prints the body example only when the endpoint has a body', () => {
    expect(bodyTemplate(endpoint({ hasBody: true, bodyExample: { items: [] } }))).toBe('{\n  "items": []\n}');
    expect(bodyTemplate(endpoint({}))).toBe('');
  });
});

describe('delayTargets', () => {
  it('lists unique Component.operation steps in execution order', () => {
    const span = (component: string, operation: string, sequence: number) =>
      ({ component, operation, sequence }) as SpanDto;
    expect(
      delayTargets([
        span('ProductRepository', 'findById', 1),
        span('ProductController', 'get', 0),
        span('ProductRepository', 'findById', 2),
        span('PostgreSQL', 'SELECT products by id', 3),
      ]),
    ).toEqual(['ProductController.get', 'ProductRepository.findById', 'PostgreSQL.SELECT products by id']);
  });
});

describe('delay options', () => {
  const ref = (component: string, operation: string, sequence: number) =>
    ({ component, operation, sequence }) as SpanDto;

  it('uses the steps of the reference execution when available', () => {
    const options = delayOptions(endpoint({}), [ref('ProductController', 'get', 0), ref('ProductService', 'getProduct', 1)]);
    expect(options).toEqual(['ProductController.get', 'ProductService.getProduct']);
    expect(defaultDelayTarget(options)).toBe('ProductService.getProduct');
  });

  it('falls back to the handler step for endpoints that never ran', () => {
    const options = delayOptions(endpoint({}), null);
    expect(options).toEqual(['ProductController.get']);
    expect(defaultDelayTarget(options)).toBe('ProductController.get');
  });

  it('offers nothing for uninstrumented handlers without history', () => {
    expect(delayOptions(endpoint({ instrumented: false, entryStep: null }), null)).toEqual([]);
    expect(defaultDelayTarget([])).toBe('');
  });
});
