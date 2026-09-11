import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EmployeePage } from '../../features/employees/employee.model';

/**
 * The only place the employee endpoint is named. Components reach the directory through the
 * feature facade, never through HttpClient, so a change of URL or page contract lands here.
 */
@Injectable({ providedIn: 'root' })
export class EmployeeApiService {
  private readonly http = inject(HttpClient);

  page(page: number, size: number): Observable<EmployeePage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<EmployeePage>('/api/v1/employees', { params });
  }
}
