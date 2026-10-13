import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormArray, FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, finalize, of } from 'rxjs';
import { FlowApiService } from '../../core/flow-api.service';
import { Product } from '../../core/models';
import { EndpointRunnerComponent } from './endpoint-runner.component';

export interface Execution {
  traceId: string | null;
  httpStatus: number;
}

type LineForm = FormGroup<{ productId: FormControl<number | null>; quantity: FormControl<number> }>;

const SCENARIOS = [
  { id: 'success', title: 'Successful order', icon: 'check_circle', tone: 'ok',
    text: 'Two products in stock → 201 Created' },
  { id: 'stock-error', title: 'Insufficient stock', icon: 'error', tone: 'error',
    text: 'Second line exceeds the stock → 422' },
  { id: 'slow', title: 'Slow operation', icon: 'hourglass_top', tone: 'warn',
    text: 'Artificial delay in StockService (demo)' },
] as const;

@Component({
  selector: 'app-scenario-runner',
  imports: [
    ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, MatSliderModule, MatTooltipModule, EndpointRunnerComponent,
  ],
  templateUrl: './scenario-runner.component.html',
  styleUrl: './scenario-runner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScenarioRunnerComponent {
  private readonly api = inject(FlowApiService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly snackBar = inject(MatSnackBar);

  readonly executed = output<Execution>();

  protected readonly scenarios = SCENARIOS;
  protected readonly tab = signal<'scenarios' | 'custom' | 'endpoint'>('scenarios');
  protected readonly running = signal<string | null>(null);
  protected readonly delayMs = signal(1500);
  protected readonly products = toSignal(this.api.products().pipe(catchError(() => of([] as Product[]))), {
    initialValue: [] as Product[],
  });

  protected readonly form = this.fb.group({
    lines: this.fb.array<LineForm>([this.newLine(1, 1)]),
    delayMs: this.fb.control(0, [Validators.min(0), Validators.max(5000)]),
  });

  get lines(): FormArray<LineForm> {
    return this.form.controls.lines;
  }

  protected run(id: string): void {
    this.running.set(id);
    this.api
      .runScenario(id, this.delayMs())
      .pipe(finalize(() => this.running.set(null)))
      .subscribe({
        next: (result) => this.executed.emit({ traceId: result.traceId, httpStatus: result.httpStatus }),
        error: () => this.snackBar.open('Could not reach the backend', 'Dismiss', { duration: 4000 }),
      });
  }

  protected addLine(): void {
    if (this.lines.length < 10) this.lines.push(this.newLine(null, 1));
  }

  protected removeLine(index: number): void {
    if (this.lines.length > 1) this.lines.removeAt(index);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const lines = value.lines.map((l) => ({ productId: l.productId!, quantity: l.quantity }));
    this.running.set('custom');
    this.api
      .createOrder(lines, value.delayMs)
      .pipe(finalize(() => this.running.set(null)))
      .subscribe({
        next: (result) => this.executed.emit(result),
        error: () => this.snackBar.open('Request failed (server error)', 'Dismiss', { duration: 4000 }),
      });
  }

  protected formatDelay = (value: number): string => (value >= 1000 ? `${value / 1000}s` : `${value}ms`);

  private newLine(productId: number | null, quantity: number): LineForm {
    return this.fb.group({
      productId: new FormControl<number | null>(productId, Validators.required),
      quantity: this.fb.control(quantity, [Validators.required, Validators.min(1), Validators.max(10000)]),
    });
  }
}
