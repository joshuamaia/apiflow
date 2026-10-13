import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormRecord, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, finalize, of, startWith } from 'rxjs';
import { FlowApiService } from '../../core/flow-api.service';
import { EndpointInfo, SendResult } from '../../core/models';
import { formatDuration } from '../../shared/time';
import { bodyTemplate, buildUrl, defaultDelayTarget, delayOptions, endpointKey } from './endpoint-request';
import { Execution } from './scenario-runner.component';

/** Rejects text that is not valid JSON. */
function jsonValidator(control: AbstractControl<string>): ValidationErrors | null {
  if (!control.value?.trim()) return null;
  try {
    JSON.parse(control.value);
    return null;
  } catch {
    return { json: true };
  }
}

/**
 * Sends a request to any tracked endpoint. The list comes from the backend's own Spring MVC mappings
 * (`GET /api/flow/endpoints`), so new endpoints appear here without changing the frontend.
 */
@Component({
  selector: 'app-endpoint-runner',
  imports: [
    ReactiveFormsModule, MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  templateUrl: './endpoint-runner.component.html',
  styleUrl: './endpoint-runner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EndpointRunnerComponent {
  private readonly api = inject(FlowApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly executed = output<Execution>();

  protected readonly endpoints = toSignal(this.api.endpoints().pipe(catchError(() => of([] as EndpointInfo[]))), {
    initialValue: null,
  });
  protected readonly sending = signal(false);
  protected readonly result = signal<SendResult | null>(null);
  protected readonly duration = formatDuration;
  protected readonly key = endpointKey;

  protected readonly endpoint = new FormControl<EndpointInfo | null>(null, Validators.required);
  protected readonly pathValues = new FormRecord<FormControl<string>>({});
  protected readonly queryValues = new FormRecord<FormControl<string>>({});
  protected readonly body = new FormControl('', { nonNullable: true, validators: jsonValidator });
  protected readonly delayMs = new FormControl(0, {
    nonNullable: true,
    validators: [Validators.min(0), Validators.max(5000)],
  });
  protected readonly delayTarget = new FormControl('', { nonNullable: true });

  /** Steps of the last execution of the selected endpoint, offered as delay targets. */
  protected readonly targets = signal<string[]>([]);
  private readonly targetText = toSignal(this.delayTarget.valueChanges, { initialValue: '' });
  protected readonly filteredTargets = computed(() => {
    const text = this.targetText().toLowerCase();
    return this.targets().filter((t) => t.toLowerCase().includes(text));
  });

  private readonly selected = toSignal(this.endpoint.valueChanges, { initialValue: null });
  private readonly pathState = toSignal(this.pathValues.valueChanges.pipe(startWith({})), { initialValue: {} });
  private readonly queryState = toSignal(this.queryValues.valueChanges.pipe(startWith({})), { initialValue: {} });

  /** URL that will be requested, updated as the user types. */
  protected readonly url = computed(() => {
    const endpoint = this.selected();
    this.pathState();
    this.queryState();
    return endpoint
      ? buildUrl(endpoint, this.pathValues.getRawValue(), this.queryValues.getRawValue())
      : '';
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.delayMs.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe((ms) => this.suggestTarget(ms));
    this.endpoint.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe((endpoint) => {
      this.result.set(null);
      Object.keys(this.pathValues.controls).forEach((name) => this.pathValues.removeControl(name, { emitEvent: false }));
      Object.keys(this.queryValues.controls).forEach((name) => this.queryValues.removeControl(name, { emitEvent: false }));
      if (!endpoint) return;
      for (const name of endpoint.pathVariables) {
        this.pathValues.addControl(name, new FormControl('1', { nonNullable: true, validators: Validators.required }),
          { emitEvent: false });
      }
      for (const param of endpoint.queryParams) {
        this.queryValues.addControl(param.name, new FormControl(param.defaultValue ?? '', {
          nonNullable: true,
          validators: param.required ? Validators.required : [],
        }), { emitEvent: false });
      }
      this.body.setValue(bodyTemplate(endpoint));
      this.delayMs.setValue(0, { emitEvent: false });
      this.delayTarget.setValue('');
      this.loadTargets(endpoint);
      this.pathValues.updateValueAndValidity();
      this.queryValues.updateValueAndValidity();
    });
  }

  private loadTargets(endpoint: EndpointInfo): void {
    this.targets.set(delayOptions(endpoint, null));
    this.api.blueprint(endpoint.method, endpoint.path).subscribe((blueprint) => {
      if (this.endpoint.value === endpoint) {
        this.targets.set(delayOptions(endpoint, blueprint?.spans ?? null));
      }
    });
  }

  /** Entering a delay without a step pre-selects a sensible one, so the request is ready to send. */
  private suggestTarget(delayMs: number): void {
    if (delayMs > 0 && !this.delayTarget.value.trim()) {
      const suggestion = defaultDelayTarget(this.targets());
      if (suggestion) this.delayTarget.setValue(suggestion);
    }
  }

  /** A delay needs a step to be applied to. */
  protected delayIncomplete(): boolean {
    return this.delayMs.value > 0 && !this.delayTarget.value.trim();
  }

  protected send(): void {
    const endpoint = this.endpoint.value;
    if (!endpoint || this.pathValues.invalid || this.queryValues.invalid || (endpoint.hasBody && this.body.invalid)
      || this.delayMs.invalid || this.delayIncomplete()) {
      this.endpoint.markAsTouched();
      this.pathValues.markAllAsTouched();
      this.queryValues.markAllAsTouched();
      this.body.markAsTouched();
      this.delayMs.markAsTouched();
      this.delayTarget.markAsTouched();
      return;
    }
    const body = endpoint.hasBody && this.body.value.trim() ? JSON.parse(this.body.value) : null;
    const delay = this.delayMs.value > 0 ? { ms: this.delayMs.value, target: this.delayTarget.value.trim() } : undefined;
    this.sending.set(true);
    this.api
      .send(endpoint.method, this.url(), body, delay)
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.executed.emit({ traceId: result.traceId, httpStatus: result.httpStatus });
          // the first execution of an endpoint makes its steps available as delay targets
          if (this.targets().length <= 1) setTimeout(() => this.loadTargets(endpoint), 800);
        },
        error: () => this.snackBar.open('Could not reach the backend', 'Dismiss', { duration: 4000 }),
      });
  }

  protected resetBody(): void {
    const endpoint = this.endpoint.value;
    if (endpoint) this.body.setValue(bodyTemplate(endpoint));
  }

  protected names(record: FormRecord<FormControl<string>>): string[] {
    return Object.keys(record.controls);
  }
}
