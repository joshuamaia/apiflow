/**
 * Parses an ISO-8601 instant keeping sub-millisecond precision (the backend sends microseconds),
 * returning epoch milliseconds as a fractional number.
 */
export function isoToEpochMs(iso: string | null | undefined): number | null {
  if (!iso) return null;
  const match = /^(.*T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$/.exec(iso);
  if (!match) {
    const parsed = Date.parse(iso);
    return Number.isNaN(parsed) ? null : parsed;
  }
  const [, base, fraction = '', zone = 'Z'] = match;
  const whole = Date.parse(`${base}${zone}`);
  if (Number.isNaN(whole)) return null;
  const fractionMs = fraction ? Number(`0.${fraction}`) * 1000 : 0;
  return whole + fractionMs;
}

/** Human friendly duration: µs below 1 ms, ms below 1 s, seconds above. */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) return '—';
  if (ms < 1) return `${Math.max(1, Math.round(ms * 1000))} µs`;
  if (ms < 10) return `${ms.toFixed(2)} ms`;
  if (ms < 1000) return `${ms.toFixed(1)} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

export function formatClock(iso: string | null | undefined): string {
  const epoch = isoToEpochMs(iso);
  if (epoch == null) return '—';
  const date = new Date(epoch);
  const micros = Math.round((epoch % 1000) * 1000) % 1_000_000;
  const time = date.toLocaleTimeString('en-GB', { hour12: false });
  return `${time}.${String(Math.floor(micros)).padStart(6, '0')}`;
}

export function formatRelative(iso: string | null | undefined, now = Date.now()): string {
  const epoch = isoToEpochMs(iso);
  if (epoch == null) return '—';
  const seconds = Math.max(0, Math.round((now - epoch) / 1000));
  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return new Date(epoch).toLocaleDateString();
}

export function shortId(id: string | null | undefined, length = 8): string {
  return id ? id.slice(0, length) : '—';
}
