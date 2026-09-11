import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { ChangeSalaryRequest, Employee, SalaryRevision } from '../employees/employee.model';

/** One employee and their pay history, which are always read and shown together. */
@Injectable()
export class EmployeeDetailFacade {
  private readonly api = inject(EmployeeApiService);

  private readonly person = signal<Employee | null>(null);
  private readonly history = signal<readonly SalaryRevision[]>([]);
  private readonly loading = signal(false);
  private readonly failed = signal(false);
  private readonly changeFailed = signal<string | null>(null);

  readonly employee = this.person.asReadonly();
  readonly revisions = this.history.asReadonly();
  readonly isLoading = this.loading.asReadonly();
  readonly hasFailed = this.failed.asReadonly();
  readonly changeError = this.changeFailed.asReadonly();

  load(id: string): void {
    this.loading.set(true);
    this.failed.set(false);
    forkJoin({ employee: this.api.byId(id), revisions: this.api.revisions(id) }).subscribe({
      next: ({ employee, revisions }) => {
        this.person.set(employee);
        this.history.set(revisions);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  changeSalary(id: string, change: ChangeSalaryRequest, onDone: () => void): void {
    this.changeFailed.set(null);
    this.api.changeSalary(id, change).subscribe({
      next: () => {
        // Re-read rather than patch in place: the new row in the log is written by the server and
        // showing a locally invented one would be showing something nobody recorded.
        this.load(id);
        onDone();
      },
      error: (failure: { error?: { detail?: string } }) => {
        this.changeFailed.set(failure.error?.detail ?? 'The change was refused.');
      },
    });
  }
}
