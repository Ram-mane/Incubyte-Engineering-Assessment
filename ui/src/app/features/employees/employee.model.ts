/** The directory as the API returns it. Amounts are strings: see `MoneyPipe`. */
export interface Money {
  readonly amount: string;
  readonly currency: string;
}

export interface Employee {
  readonly id: string;
  readonly employeeNumber: string;
  readonly givenName: string;
  readonly familyName: string;
  readonly email: string;
  readonly country: string;
  readonly department: string;
  readonly jobTitle: string;
  readonly level: string;
  readonly salary: Money;
}

/**
 * A page of the directory. `nextCursor` is absent on the last page and `totalApprox` on every
 * page but the first — absent, not null, which is why both are optional here.
 */
export interface EmployeePage {
  readonly items: readonly Employee[];
  readonly nextCursor?: string;
  readonly totalApprox?: number;
}

export interface DirectoryFilterOptions {
  readonly countries: readonly string[];
  readonly departments: readonly string[];
  readonly jobTitles: readonly string[];
  readonly levels: readonly string[];
}

/** What the screen asks for. Every field optional: the unfiltered first page sets none of them. */
export interface DirectoryQuery {
  readonly q?: string;
  readonly country?: string;
  readonly department?: string;
  readonly jobTitle?: string;
  readonly level?: string;
  readonly cursor?: string;
  readonly limit?: number;
}

export interface SalaryRevision {
  readonly previousAmount: Money;
  readonly newAmount: Money;
  readonly reason: string;
  readonly changedBy: string;
  readonly changedAt: string;
  readonly note?: string;
}

export interface ChangeSalaryRequest {
  readonly amount: string;
  readonly currency: string;
  readonly reason: string;
  readonly note?: string;
}
