import { EndpointInfo, SpanDto } from '../../core/models';

/**
 * Builds the request URL for a catalog endpoint: path variables are URL-encoded and substituted into
 * the route pattern; empty query values are omitted.
 */
export function buildUrl(
  endpoint: EndpointInfo,
  pathValues: Record<string, string>,
  queryValues: Record<string, string>,
): string {
  const path = endpoint.path.replace(/\{([^}:]+)(?::[^}]*)?}/g, (_, name: string) =>
    encodeURIComponent(pathValues[name] ?? ''),
  );
  const query = Object.entries(queryValues)
    .filter(([, value]) => value != null && String(value).trim() !== '')
    .map(([name, value]) => `${encodeURIComponent(name)}=${encodeURIComponent(String(value).trim())}`)
    .join('&');
  return query ? `${path}?${query}` : path;
}

/** Stable identity of a catalog entry. */
export function endpointKey(endpoint: EndpointInfo): string {
  return `${endpoint.method} ${endpoint.path}`;
}

/** Pretty JSON for the request body editor. */
export function bodyTemplate(endpoint: EndpointInfo): string {
  return endpoint.hasBody && endpoint.bodyExample != null ? JSON.stringify(endpoint.bodyExample, null, 2) : '';
}

/**
 * Steps that can receive an artificial delay, as `Component.operation`, in execution order and without
 * duplicates. Taken from a previous execution of the same endpoint.
 */
export function delayTargets(spans: readonly SpanDto[]): string[] {
  const ordered = [...spans].sort((a, b) => a.sequence - b.sequence);
  return [...new Set(ordered.map((span) => `${span.component}.${span.operation}`))];
}

/**
 * Steps offered for the delay: those of the last execution when the endpoint already ran, otherwise
 * just the handler step (known from the catalog even before the first call).
 */
export function delayOptions(endpoint: EndpointInfo, reference: readonly SpanDto[] | null): string[] {
  const fromReference = reference ? delayTargets(reference) : [];
  if (fromReference.length) return fromReference;
  return endpoint.entryStep ? [endpoint.entryStep] : [];
}

/**
 * Step pre-selected when a delay is entered: the first step below the controller (usually the use case
 * or service), or the controller itself when it is the only known step.
 */
export function defaultDelayTarget(options: readonly string[]): string {
  return options[1] ?? options[0] ?? '';
}
