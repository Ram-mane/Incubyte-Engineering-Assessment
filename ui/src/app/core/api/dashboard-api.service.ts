import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DashboardQuery, PayrollSummary } from '../../features/dashboard/dashboard.model';

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
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      // A cleared dropdown is an absent parameter, not `?country=`: the API reads a blank string
      // as a filter matching nobody.
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(name, value);
      }
    }
    return this.http.get<PayrollSummary>('/api/v1/dashboard/summary', { params });
  }
}
