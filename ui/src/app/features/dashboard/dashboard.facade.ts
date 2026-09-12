import { Injectable, computed, inject, signal } from '@angular/core';

import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DirectoryFilterOptions } from '../../core/api/filter-options.model';
import {
  BreakdownDimension,
  DashboardQuery,
  DistributionDimension,
  PayrollBreakdown,
  PayrollSummary,
  SalaryDistribution,
} from './dashboard.model';

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
  private readonly groups = signal<PayrollBreakdown | null>(null);
  private readonly groupedBy = signal<BreakdownDimension>('department');
  private readonly spread = signal<SalaryDistribution | null>(null);
  // A role is the peer group an HR manager compares within, so that is where this starts.
  private readonly spreadBy = signal<DistributionDimension>('jobTitle');
  private readonly loading = signal(false);
  private readonly failed = signal(false);
  private readonly refusal = signal<string | null>(null);
  private readonly query = signal<DashboardQuery>({});
  /** Which request the screen is currently showing. Answers to older ones are dropped. */
  private inFlight = 0;
  private inFlightBreakdown = 0;
  private inFlightDistribution = 0;
  private readonly options = signal<DirectoryFilterOptions>({
    countries: [],
    departments: [],
    jobTitles: [],
    levels: [],
  });

  readonly summary = this.current.asReadonly();
  readonly breakdown = this.groups.asReadonly();
  readonly dimension = this.groupedBy.asReadonly();
  readonly distribution = this.spread.asReadonly();
  readonly distributionDimension = this.spreadBy.asReadonly();
  readonly isLoading = this.loading.asReadonly();
  readonly hasFailed = this.failed.asReadonly();

  /**
   * What to tell the person. A refused question and an unreachable server are different problems -
   * "the rate table has no rates to EUR" sends someone to fix the data, "could not be loaded"
   * sends them to refresh a page that will never load.
   */
  readonly failure = computed(
    () => this.refusal() ?? 'The dashboard could not be loaded. The server may be starting up.',
  );
  readonly filters = this.query.asReadonly();
  readonly filterOptions = this.options.asReadonly();

  /** Nobody matched — an answer, and a different thing from a summary that did not arrive. */
  readonly isEmpty = computed(() => this.current()?.headcount === 0);

  loadFilterOptions(): void {
    this.employees.filterOptions().subscribe({ next: (options) => this.options.set(options) });
  }

  /** Look at the same people grouped a different way. The cards do not depend on the grouping. */
  groupBy(dimension: BreakdownDimension): void {
    this.groupedBy.set(dimension);
    this.loadBreakdown(this.query());
  }

  /** Look at the spread within a different kind of peer group. The cards do not depend on it. */
  distributeBy(dimension: DistributionDimension): void {
    this.spreadBy.set(dimension);
    this.loadDistribution(this.query());
  }

  load(query: DashboardQuery): void {
    // Two filter changes in quick succession can come back in either order, and the slower answer
    // is the older question. Painting it would put one filter's figures under another filter's
    // controls, which is a dashboard that lies about what it is showing.
    const request = ++this.inFlight;
    this.query.set(query);
    this.loading.set(true);
    this.failed.set(false);
    this.refusal.set(null);
    this.loadBreakdown(query);
    this.loadDistribution(query);
    this.api.summary(query).subscribe({
      next: (summary) => {
        if (request !== this.inFlight) {
          return;
        }
        this.current.set(summary);
        this.loading.set(false);
      },
      error: (failure: { error?: { detail?: string } }) => {
        if (request !== this.inFlight) {
          return;
        }
        // Leaving the previous figures on screen under a new filter would be a dashboard
        // describing a question nobody asked.
        this.current.set(null);
        // The server's own words when it gave any: a refusal explains itself, an outage cannot.
        this.refusal.set(failure?.error?.detail ?? null);
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  private loadBreakdown(query: DashboardQuery): void {
    const request = ++this.inFlightBreakdown;
    this.api.breakdown(this.groupedBy(), query).subscribe({
      next: (breakdown) => {
        if (request === this.inFlightBreakdown) {
          this.groups.set(breakdown);
        }
      },
      error: () => {
        if (request === this.inFlightBreakdown) {
          // The cards carry the "could not be loaded" message for the screen; a breakdown that
          // failed shows nothing rather than the previous filter's groups.
          this.groups.set(null);
        }
      },
    });
  }

  private loadDistribution(query: DashboardQuery): void {
    const request = ++this.inFlightDistribution;
    this.api.distribution(this.spreadBy(), query).subscribe({
      next: (distribution) => {
        if (request === this.inFlightDistribution) {
          this.spread.set(distribution);
        }
      },
      error: () => {
        if (request === this.inFlightDistribution) {
          this.spread.set(null);
        }
      },
    });
  }
}
