import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'employees' },
  {
    path: 'employees',
    title: 'Employee directory',
    loadComponent: () =>
      import('./features/employees/employee-list.component').then((m) => m.EmployeeListComponent),
  },
];
