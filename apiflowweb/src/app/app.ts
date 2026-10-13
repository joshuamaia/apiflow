import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule, MatIconRegistry } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FlowStreamService } from './core/flow-stream.service';
import { MetricsStore } from './core/metrics.store';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  exact: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatTooltipModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly stream = inject(FlowStreamService);
  protected readonly metrics = inject(MetricsStore);

  protected readonly nav: NavItem[] = [
    { path: '/', label: 'Flow', icon: 'hub', exact: true },
    { path: '/traces', label: 'Traces', icon: 'timeline', exact: false },
    { path: '/components', label: 'Components', icon: 'widgets', exact: false },
  ];

  constructor() {
    inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined');
  }
}
