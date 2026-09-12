import { Money } from '../employees/employee.model';

/**
 * The four KPI cards as the API returns them, from one query.
 *
 * <p>Amounts are strings for the reason `MoneyPipe` explains, and they are already normalised to
 * `reportingCurrency` by the server — this application converts nothing. `ratesAsOf` is the date
 * whose exchange rates produced these figures, shown rather than assumed so the number on screen
 * is one somebody can reproduce.
 */
export interface PayrollSummary {
  readonly headcount: number;
  readonly totalSpend: Money;
  readonly averageSalary: Money;
  readonly medianSalary: Money;
  readonly reportingCurrency: string;
  readonly ratesAsOf: string;
}

/** What the dashboard is looking at. Every field optional: the unfiltered view sets none of them. */
export interface DashboardQuery {
  readonly country?: string;
  readonly department?: string;
  readonly jobTitle?: string;
  readonly level?: string;
  readonly currency?: string;
}

/** What a breakdown groups by. Lower case, as the API documents and returns it. */
export type BreakdownDimension = 'department' | 'country' | 'level';

export interface BreakdownGroup {
  readonly name: string;
  readonly headcount: number;
  readonly totalSpend: Money;
  readonly averageSalary: Money;
}

/**
 * Spend per group, largest first.
 *
 * <p>`groups` is empty when nobody matches the filters - there is no group to name. That is a
 * different answer from the KPI cards, which are always four cards and so read zero.
 */
export interface PayrollBreakdown {
  readonly groupedBy: BreakdownDimension;
  readonly groups: readonly BreakdownGroup[];
  readonly reportingCurrency: string;
  readonly ratesAsOf: string;
}

/** What a distribution groups by. Narrower than a breakdown's: a country is not a peer group. */
export type DistributionDimension = 'jobTitle' | 'department';

export interface DistributionGroup {
  readonly name: string;
  readonly headcount: number;
  readonly lowest: Money;
  readonly p25: Money;
  readonly median: Money;
  readonly p75: Money;
  readonly p90: Money;
  readonly highest: Money;
}

/** Quartiles and range per group, widest spread first. Every figure is an amount somebody earns. */
export interface SalaryDistribution {
  readonly groupedBy: DistributionDimension;
  readonly groups: readonly DistributionGroup[];
  readonly reportingCurrency: string;
  readonly ratesAsOf: string;
}
