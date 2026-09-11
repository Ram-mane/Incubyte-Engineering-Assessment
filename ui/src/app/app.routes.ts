import { Routes } from '@angular/router';

import { signedInGuard } from './core/auth/signed-in.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'employees' },
  {
    path: 'login',
    title: 'Sign in',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'employees',
    title: 'Employee directory',
    canActivate: [signedInGuard],
    loadComponent: () =>
      import('./features/employees/employee-list.component').then((m) => m.EmployeeListComponent),
  },
  {
    path: 'employees/:id',
    title: 'Employee',
    canActivate: [signedInGuard],
    // Route parameters bind straight to component inputs, so the component takes an id rather
    // than a router it has to ask.
    loadComponent: () =>
      import('./features/employee-detail/employee-detail.component').then((m) => m.EmployeeDetailComponent),
  },
];
