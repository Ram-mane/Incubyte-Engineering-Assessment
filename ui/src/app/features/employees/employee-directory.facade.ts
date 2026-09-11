import { Injectable, computed, inject, signal } from '@angular/core';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DirectoryFilterOptions, DirectoryQuery, Employee } from './employee.model';

export const DEFAULT_PAGE_SIZE = 50;

/**
 * What the directory screen knows: which page it is on, who is on it, and whether it is waiting.
 *
 * <p>Paging is by cursor, so there is no page number to jump to - only "the page after this one".
 * Going back is a client-side stack of the cursors already visited, because the server has no
 * notion of a previous page and inventing one would mean running the sort backwards.
 */
@Injectable()
export class EmployeeDirectoryFacade {
  private readonly api = inject(EmployeeApiService);

  private readonly employeesOnPage = signal<readonly Employee[]>([]);
  private readonly nextCursor = signal<string | undefined>(undefined);
  /** Cursors for the pages before this one. Its length is how deep into the directory we are. */
  private readonly previousCursors = signal<readonly (string | undefined)[]>([]);
  private readonly currentCursor = signal<string | undefined>(undefined);
  private readonly matching = signal(0);
  private readonly loading = signal(false);
  private readonly failed = signal(false);
  private readonly query = signal<DirectoryQuery>({});
  private readonly options = signal<DirectoryFilterOptions>({
    countries: [],
    departments: [],
    jobTitles: [],
    levels: [],
  });

  readonly employees = this.employeesOnPage.asReadonly();
  readonly total = this.matching.asReadonly();
  readonly isLoading = this.loading.asReadonly();
  readonly hasFailed = this.failed.asReadonly();
  readonly filters = this.query.asReadonly();
  readonly filterOptions = this.options.asReadonly();

  readonly hasNextPage = computed(() => this.nextCursor() !== undefined);
  readonly hasPreviousPage = computed(() => this.previousCursors().length > 0);
  readonly pageNumber = computed(() => this.previousCursors().length + 1);
  readonly firstOnPage = computed(() =>
    this.total() === 0 ? 0 : this.previousCursors().length * DEFAULT_PAGE_SIZE + 1,
  );
  readonly lastOnPage = computed(() => this.firstOnPage() + this.employees().length - 1);

  loadFilterOptions(): void {
    this.api.filterOptions().subscribe({ next: (options) => this.options.set(options) });
  }

  /** A new search or filter starts again from the beginning: the old cursor named another list. */
  search(query: DirectoryQuery): void {
    this.query.set(query);
    this.previousCursors.set([]);
    this.currentCursor.set(undefined);
    this.fetch(undefined);
  }

  nextPage(): void {
    const cursor = this.nextCursor();
    if (!cursor) {
      return;
    }
    this.previousCursors.update((stack) => [...stack, this.currentCursor()]);
    this.fetch(cursor);
  }

  previousPage(): void {
    const stack = [...this.previousCursors()];
    const cursor = stack.pop();
    this.previousCursors.set(stack);
    this.fetch(cursor);
  }

  private fetch(cursor: string | undefined): void {
    this.loading.set(true);
    this.failed.set(false);
    this.currentCursor.set(cursor);
    this.api.page({ ...this.query(), cursor, limit: DEFAULT_PAGE_SIZE }).subscribe({
      next: (page) => {
        this.employeesOnPage.set(page.items);
        this.nextCursor.set(page.nextCursor);
        // Counted on the first page only, so keep the last number we were told rather than
        // showing "of 0" from page two onwards.
        if (page.totalApprox !== undefined) {
          this.matching.set(page.totalApprox);
        }
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
