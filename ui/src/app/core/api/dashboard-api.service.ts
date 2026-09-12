import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  BreakdownDimension,
  DashboardQuery,
  DistributionDimension,
  PayrollBreakdown,
  PayrollSummary,
  SalaryDistribution,
} from '../../features/dashboard/dashboard.model';

/**
 * The only place the dashboard endpoints are named.
 *
 * <p>One call returns all four KPI cards. That is the server's contract rather than this
 * service's convenience: four cards fetched separately can disagree with each other, and a median
 * that does not belong to the headcount above it is worse than a slow dashboard.
 */
@Injectable({ providedIn: 'root' })
export class DashboardApiService {
  private readonly http = inject(HttpClient);

  summary(query: DashboardQuery): Observable<PayrollSummary> {
    return this.http.get<PayrollSummary>('/api/v1/dashboard/summary', { params: asParams(query) });
  }

  breakdown(dimension: BreakdownDimension, query: DashboardQuery): Observable<PayrollBreakdown> {
    return this.http.get<PayrollBreakdown>('/api/v1/dashboard/breakdown', {
      params: asParams(query).set('groupBy', dimension),
    });
  }

  distribution(dimension: DistributionDimension, query: DashboardQuery): Observable<SalaryDistribution> {
    return this.http.get<SalaryDistribution>('/api/v1/dashboard/distribution', {
      params: asParams(query).set('groupBy', dimension),
    });
  }
}

/**
 * A cleared dropdown is an absent parameter, not `?country=`: `new CountryCode("")` fails the value
 * object's own guard, so a blank one returns 400 rather than an empty dashboard.
 */
function asParams(query: DashboardQuery): HttpParams {
  let params = new HttpParams();
  for (const [name, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') {
      params = params.set(name, value);
    }
  }
  return params;
}
