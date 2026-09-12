import { Injectable, inject, signal } from '@angular/core';
import { Observable, forkJoin, tap } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { BandView, ChangeSalaryRequest, Employee, SalaryRevision } from '../employees/employee.model';

/** The salary moved between the decision and the write: 409, and the screen is out of date. */
const CONFLICT = 409;

/** One employee and their pay history, which are always read and shown together. */
@Injectable()
export class EmployeeDetailFacade {
  private readonly api = inject(EmployeeApiService);

  private readonly person = signal<Employee | null>(null);
  private readonly history = signal<readonly SalaryRevision[]>([]);
  private readonly range = signal<BandView | null>(null);
  private readonly rangeFailed = signal(false);
  private readonly loading = signal(false);
  private readonly failed = signal(false);
  private readonly changeFailed = signal<string | null>(null);

  readonly employee = this.person.asReadonly();
  readonly revisions = this.history.asReadonly();
  /** The approved range. Null only while it is still loading or if the read failed. */
  readonly band = this.range.asReadonly();
  /** Distinct from "no band defined": one is an answer, the other is a missing answer. */
  readonly bandFailed = this.rangeFailed.asReadonly();
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
        // After the person, because a band is looked up by their role - and separately, so the
        // screen is usable whether or not this role has an approved range.
        this.rangeFailed.set(false);
        this.api.band(employee).subscribe({
          next: (band) => this.range.set(band),
          error: () => {
            // Not the same as "this role has no band". Saying so would be an affirmative claim
            // about the org made on the strength of a failed request.
            this.range.set(null);
            this.rangeFailed.set(true);
          },
        });
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  /**
   * Saves, and hands the caller the outcome rather than swallowing it.
   *
   * <p>The dialog subscribes to this so it can stay open on a refusal with the manager's input
   * intact. A success re-reads the employee here, because the new row in the log is written by the
   * server and showing a locally invented one would be showing something nobody recorded.
   */
  save(id: string, change: ChangeSalaryRequest): Observable<Employee> {
    this.changeFailed.set(null);
    return this.api.changeSalary(id, change).pipe(
      tap({
        next: () => this.load(id),
        error: (failure: { status?: number }) => {
          if (failure?.status === CONFLICT) {
            // Somebody else moved this pay. Re-read so the figure behind the dialog is the one
            // now on record rather than the stale one this manager decided against.
            this.load(id);
          }
        },
      }),
    );
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
      error: (failure: { status?: number; error?: { detail?: string } }) => {
        this.changeFailed.set(failure.error?.detail ?? 'The change was refused.');
        if (failure.status === CONFLICT) {
          // Somebody else moved this pay. The figure on screen is the one this manager decided
          // against, so re-read before they decide again - the message says to, and leaving the
          // stale number under it would be asking them to repeat the same mistake.
          this.load(id);
        }
      },
    });
  }
}
