import { Injectable, computed, inject, signal } from '@angular/core';

import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DirectoryFilterOptions } from '../../core/api/filter-options.model';
import { DashboardQuery, PayrollSummary } from './dashboard.model';

/**
 * What the dashboard screen knows: what it is looking at, and the one answer that came back.
 *
 * <p>The four cards are one signal, not four, because they are one query. Holding them separately
 * would make it possible to show a median from one filter beside a headcount from another, which
 * is a defect the shape of this facade rules out rather than guards against.
 *
 * <p>The filter values come from the directory's endpoint, through `core` rather than through the
 * directory feature. The dashboard filters the same people by the same vocabulary, and a second
 * endpoint returning the same distinct countries would be a second thing to keep in step.
 */
@Injectable()
export class DashboardFacade {
  private readonly api = inject(DashboardApiService);
  private readonly employees = inject(EmployeeApiService);

  private readonly current = signal<PayrollSummary | null>(null);
  private readonly loading = signal(false);
  private readonly failed = signal(false);
  private readonly query = signal<DashboardQuery>({});
  /** Which request the screen is currently showing. Answers to older ones are dropped. */
  private inFlight = 0;
  private readonly options = signal<DirectoryFilterOptions>({
    countries: [],
    departments: [],
    jobTitles: [],
    levels: [],
  });

  readonly summary = this.current.asReadonly();
  readonly isLoading = this.loading.asReadonly();
  readonly hasFailed = this.failed.asReadonly();
  readonly filters = this.query.asReadonly();
  readonly filterOptions = this.options.asReadonly();

  /** Nobody matched — an answer, and a different thing from a summary that did not arrive. */
  readonly isEmpty = computed(() => this.current()?.headcount === 0);

  loadFilterOptions(): void {
    this.employees.filterOptions().subscribe({ next: (options) => this.options.set(options) });
  }

  load(query: DashboardQuery): void {
    // Two filter changes in quick succession can come back in either order, and the slower answer
    // is the older question. Painting it would put one filter's figures under another filter's
    // controls, which is a dashboard that lies about what it is showing.
    const request = ++this.inFlight;
    this.query.set(query);
    this.loading.set(true);
    this.failed.set(false);
    this.api.summary(query).subscribe({
      next: (summary) => {
        if (request !== this.inFlight) {
          return;
        }
        this.current.set(summary);
        this.loading.set(false);
      },
      error: () => {
        if (request !== this.inFlight) {
          return;
        }
        // Leaving the previous figures on screen under a new filter would be a dashboard
        // describing a question nobody asked.
        this.current.set(null);
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }
}
