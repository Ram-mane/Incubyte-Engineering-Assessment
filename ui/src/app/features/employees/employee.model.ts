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
  readonly changedByEmail: string;
  readonly changedAt: string;
  readonly note?: string;
}

export interface ChangeSalaryRequest {
  readonly amount: string;
  readonly currency: string;
  readonly reason: string;
  readonly note?: string;
}

/**
 * The approved range for an employee's role, and where their pay sits in it.
 *
 * <p>`defined` is false when the org has no band for that role, level and country. That is an
 * ordinary state of the data, and the screen says so rather than hiding the section.
 *
 * <p>Nothing in this application acts on `position`. It is displayed - see the change-pay dialog,
 * which shows the band and lets the manager set whatever figure they decide on.
 */
export interface BandView {
  readonly defined: boolean;
  readonly min?: Money | null;
  readonly mid?: Money | null;
  readonly max?: Money | null;
  readonly compaRatio?: string | null;
  readonly position?: 'BELOW_MIN' | 'LOW' | 'WITHIN' | 'HIGH' | 'ABOVE_MAX' | null;
}
