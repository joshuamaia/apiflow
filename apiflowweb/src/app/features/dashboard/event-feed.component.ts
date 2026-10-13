import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FlowStreamService } from '../../core/flow-stream.service';
import { FlowEvent } from '../../core/models';
import { formatClock, formatDuration } from '../../shared/time';

/** Raw view of the SSE stream: what the backend is emitting right now. */
@Component({
  selector: 'app-event-feed',
  imports: [MatIconModule, MatTooltipModule],
  template: `
    <header class="head">
      <h2>Live event stream</h2>
      <span class="state" [class]="'state--' + stream.state()">
        <span class="dot"></span>
        @switch (stream.state()) {
          @case ('open') { Connected }
          @case ('reconnecting') { Reconnecting… }
          @default { Connecting… }
        }
      </span>
    </header>
    <ol class="feed">
      @for (event of stream.feed(); track event.sequence) {
        <li class="event" [class]="'event--' + event.type">
          <span class="seq">#{{ event.sequence }}</span>
          <span class="type">{{ event.type }}</span>
          <span class="text">{{ describe(event) }}</span>
          <span class="trace" [matTooltip]="event.traceId">{{ event.traceId.slice(0, 8) }}</span>
        </li>
      } @empty {
        <li class="empty">Waiting for events… run a scenario to see them flow.</li>
      }
    </ol>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 0; }
    .head { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px 8px; }
    h2 { margin: 0; font-size: 15px; font-weight: 600; }
    .state { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--muted); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--c-waiting); }
    .state--open .dot { background: var(--c-completed); box-shadow: 0 0 8px var(--c-completed); }
    .state--reconnecting .dot { background: var(--c-slow); animation: blink 1s infinite; }
    @keyframes blink { 50% { opacity: .3; } }
    .feed { list-style: none; margin: 0; padding: 0 12px 12px; overflow: auto; max-height: 300px;
      font: 11.5px/1.5 var(--font-mono); }
    .event { display: grid; grid-template-columns: 48px 128px 1fr 64px; gap: 8px; padding: 3px 4px;
      border-bottom: 1px solid rgba(255,255,255,.03); animation: in 250ms ease; }
    .seq { color: var(--muted); }
    .type { color: var(--c-running); }
    .event--SPAN_COMPLETED .type, .event--TRACE_PERSISTED .type { color: var(--c-completed); }
    .event--SPAN_FAILED .type { color: var(--c-error); }
    .event--TRACE_STARTED .type, .event--TRACE_COMPLETED .type { color: #c4b5fd; }
    .text { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .trace { color: var(--muted); text-align: right; }
    .empty { padding: 24px; text-align: center; color: var(--muted); font-family: var(--font-body); }
    @keyframes in { from { opacity: 0; transform: translateX(-4px); } }
    @media (max-width: 640px) { .event { grid-template-columns: 40px 1fr 56px; } .type { display: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventFeedComponent {
  protected readonly stream = inject(FlowStreamService);

  protected describe(event: FlowEvent): string {
    if (event.span) {
      const time = event.span.durationMs != null ? ` · ${formatDuration(event.span.durationMs)}` : '';
      return `${event.span.component}.${event.span.operation}${time}`;
    }
    if (event.trace) {
      const status = event.trace.httpStatus ? ` → ${event.trace.httpStatus}` : '';
      return `${event.trace.httpMethod} ${event.trace.endpoint}${status} @ ${formatClock(event.timestamp)}`;
    }
    return '';
  }
}
