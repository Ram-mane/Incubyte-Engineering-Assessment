import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  ChangeSalaryRequest,
  DirectoryFilterOptions,
  DirectoryQuery,
  Employee,
  EmployeePage,
  SalaryRevision,
} from '../../features/employees/employee.model';

/**
 * The only place the employee endpoints are named. Components reach the directory through the
 * feature facade, never through HttpClient, so a change of URL or page contract lands here.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeApiService {
  private readonly http = inject(HttpClient);

  page(query: DirectoryQuery): Observable<EmployeePage> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      // An empty filter is an absent parameter, not `?country=`: the API reads a blank string as
      // a filter matching nobody.
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(name, value);
      }
    }
    return this.http.get<EmployeePage>('/api/v1/employees', { params });
  }

  filterOptions(): Observable<DirectoryFilterOptions> {
    return this.http.get<DirectoryFilterOptions>('/api/v1/employees/filter-options');
  }

  byId(id: string): Observable<Employee> {
    return this.http.get<Employee>(`/api/v1/employees/${id}`);
  }

  revisions(id: string): Observable<SalaryRevision[]> {
    return this.http.get<SalaryRevision[]>(`/api/v1/employees/${id}/salary-revisions`);
  }

  /** The only way pay changes. Who made the change comes from the token, never from here. */
  changeSalary(id: string, change: ChangeSalaryRequest): Observable<Employee> {
    return this.http.put<Employee>(`/api/v1/employees/${id}/salary`, change);
  }
}
