import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  BandView,
  ChangeSalaryRequest,
  DirectoryQuery,
  Employee,
  EmployeePage,
  SalaryRevision,
} from '../../features/employees/employee.model';
import { DirectoryFilterOptions } from './filter-options.model';

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
      // An empty filter is an absent parameter, not `?country=`: a blank value fails the API's
      // own validation and comes back 400, so a cleared dropdown must leave the parameter out.
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

  /**
   * The approved range for a role, and where this salary sits in it. Read-only: nothing acts on
   * it. Takes the role rather than the employee, because a band belongs to a role - the server
   * keeps the two modules apart and this call reflects that.
   */
  band(employee: Employee): Observable<BandView> {
    // POST for a read: the salary is part of the question, and a query string reaches the access
    // log, the browser history and the Referer of everything the page loads next.
    return this.http.post<BandView>('/api/v1/bands/position', {
      jobTitle: employee.jobTitle,
      level: employee.level,
      country: employee.country,
      salary: employee.salary.amount,
      currency: employee.salary.currency,
    });
  }

  revisions(id: string): Observable<SalaryRevision[]> {
    return this.http.get<SalaryRevision[]>(`/api/v1/employees/${id}/salary-revisions`);
  }

  /** The only way pay changes. Who made the change comes from the token, never from here. */
  changeSalary(id: string, change: ChangeSalaryRequest): Observable<Employee> {
    return this.http.put<Employee>(`/api/v1/employees/${id}/salary`, change);
  }
}
