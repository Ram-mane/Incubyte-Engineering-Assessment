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
