import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Flow · API Flow Visualizer',
    loadComponent: () => import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'traces',
    title: 'Traces · API Flow Visualizer',
    loadComponent: () => import('./features/traces/traces-page.component').then((m) => m.TracesPageComponent),
  },
  {
    path: 'components',
    title: 'Components · API Flow Visualizer',
    loadComponent: () =>
      import('./features/components/components-page.component').then((m) => m.ComponentsPageComponent),
  },
  { path: '**', redirectTo: '' },
];
