import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FlowPlayer, MIN_STEP_MS, SPEEDS } from './flow-player';

@Component({
  selector: 'app-playback-controls',
  imports: [MatButtonModule, MatButtonToggleModule, MatIconModule, MatSliderModule, MatTooltipModule],
  template: `
    <div class="controls">
      @switch (player.mode()) {
        @case ('live') {
          <span class="badge badge--live" matTooltip="Events streamed in real time from the backend over SSE">
            <span class="badge__dot"></span>LIVE
          </span>
        }
        @case ('replay') {
          <span class="badge badge--replay" matTooltip="Visual reproduction of a finished execution using its persisted timestamps">
            <mat-icon>history</mat-icon>REPLAY
          </span>
        }
        @default {
          <span class="badge">IDLE</span>
        }
      }

      <div class="buttons">
        <button mat-icon-button (click)="player.restart()" [disabled]="!hasTimeline()" matTooltip="Restart" aria-label="Restart">
          <mat-icon>replay</mat-icon>
        </button>
        <button mat-icon-button (click)="player.step(-1)" [disabled]="player.cursor() === 0" matTooltip="Previous step" aria-label="Previous step">
          <mat-icon>skip_previous</mat-icon>
        </button>
        @if (player.playing()) {
          <button mat-flat-button class="play" (click)="player.pause()" aria-label="Pause"><mat-icon>pause</mat-icon></button>
        } @else {
          <button mat-flat-button class="play" (click)="player.play()" [disabled]="!hasTimeline()" aria-label="Play"><mat-icon>play_arrow</mat-icon></button>
        }
        <button mat-icon-button (click)="player.step(1)" [disabled]="player.atEnd()" matTooltip="Next step" aria-label="Next step">
          <mat-icon>skip_next</mat-icon>
        </button>
      </div>

      <mat-slider class="scrubber" [min]="0" [max]="player.timeline().length" [step]="1" [disabled]="!hasTimeline()"
                  discrete [displayWith]="stepLabel">
        <input matSliderThumb [value]="player.cursor()" (valueChange)="player.seek($event)" aria-label="Execution step" />
      </mat-slider>

      <mat-button-toggle-group class="speeds" hideSingleSelectionIndicator [value]="player.speed()"
                               (change)="player.setSpeed($event.value)" aria-label="Playback speed">
        @for (speed of speeds; track speed) {
          <mat-button-toggle [value]="speed">{{ speed }}×</mat-button-toggle>
        }
      </mat-button-toggle-group>
    </div>
    <div class="caption">
      <span class="caption__step">Step {{ player.cursor() }}/{{ player.timeline().length }}</span>
      <span class="caption__text">{{ caption() }}</span>
    </div>
  `,
  styleUrl: './playback-controls.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaybackControlsComponent {
  protected readonly player = inject(FlowPlayer);
  protected readonly speeds = SPEEDS;
  protected readonly hasTimeline = computed(() => this.player.timeline().length > 0);

  protected readonly caption = computed(() => {
    const timeline = this.player.timeline();
    const entry = timeline[this.player.cursor() - 1];
    const mode = this.player.mode();
    const pacing =
      mode === 'live'
        ? `Live stream · animation paced ≥ ${MIN_STEP_MS} ms per step; durations are the measured ones`
        : `Replay of persisted timestamps at ${this.player.speed()}× · minimum ${MIN_STEP_MS} ms between steps`;
    if (!entry) return mode === 'idle' ? 'Run a scenario or pick an execution from the history' : pacing;
    switch (entry.kind) {
      case 'start':
        return `${entry.span.component}.${entry.span.operation}() started · ${pacing}`;
      case 'end':
        return `${entry.span.component}.${entry.span.operation}() ${entry.span.status === 'ERROR' ? 'failed' : 'completed'} · ${pacing}`;
      default:
        return `Request finished with HTTP ${entry.trace.httpStatus} · ${pacing}`;
    }
  });

  protected readonly stepLabel = (value: number): string => `${value}`;
}
