import { Injectable, computed, inject, signal } from '@angular/core';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { Employee } from './employee.model';

export const DEFAULT_PAGE_SIZE = 50;

/**
 * What the directory screen knows: which page it is on, who is on it, and whether it is waiting.
 *
 * <p>Ten thousand people are never in the browser at once - the server pages, and this holds one
 * page at a time. State is signals rather than a store: there is no cross-feature state here for
 * a store to coordinate (ADR-0008).
 */
@Injectable()
export class EmployeeDirectoryFacade {
  private readonly api = inject(EmployeeApiService);

  private readonly employeesOnPage = signal<readonly Employee[]>([]);
  private readonly currentPage = signal(0);
  private readonly currentSize = signal(DEFAULT_PAGE_SIZE);
  private readonly totalEmployees = signal(0);
  private readonly loading = signal(false);
  private readonly failed = signal(false);

  readonly employees = this.employeesOnPage.asReadonly();
  readonly page = this.currentPage.asReadonly();
  readonly size = this.currentSize.asReadonly();
  readonly total = this.totalEmployees.asReadonly();
  readonly isLoading = this.loading.asReadonly();
  readonly hasFailed = this.failed.asReadonly();

  readonly firstOnPage = computed(() => (this.total() === 0 ? 0 : this.page() * this.size() + 1));
  readonly lastOnPage = computed(() => Math.min((this.page() + 1) * this.size(), this.total()));

  load(page = this.currentPage(), size = this.currentSize()): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.page(page, size).subscribe({
      next: (result) => {
        this.employeesOnPage.set(result.items);
        this.currentPage.set(result.page);
        this.currentSize.set(result.size);
        this.totalEmployees.set(result.total);
        this.loading.set(false);
      },
      error: () => {
        // An empty table with no explanation reads as "nobody works here", which is a different
        // and much worse statement than "this did not load".
        this.employeesOnPage.set([]);
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }
}
