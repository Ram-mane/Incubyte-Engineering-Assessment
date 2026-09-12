import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PayrollSummary } from '../../features/dashboard/dashboard.model';
import { DashboardApiService } from './dashboard-api.service';

const summary: PayrollSummary = {
  headcount: 9_842,
  totalSpend: { amount: '1213179157.20', currency: 'USD' },
  averageSalary: { amount: '123265.10', currency: 'USD' },
  medianSalary: { amount: '113930.00', currency: 'USD' },
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
};

describe('DashboardApiService', () => {
  let api: DashboardApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DashboardApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DashboardApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads all four cards from one endpoint', () => {
    let received: PayrollSummary | undefined;

    api.summary({}).subscribe((s) => (received = s));
    http.expectOne('/api/v1/dashboard/summary').flush(summary);

    expect(received?.headcount).toBe(9842);
    expect(received?.medianSalary.amount).toBe('113930.00');
  });

  it('leaves a blank filter out of the query rather than sending it empty', () => {
    api.summary({ country: 'DE', department: '', jobTitle: '', level: '' }).subscribe();

    // The API reads `?department=` as a filter matching nobody, so a cleared dropdown must be an
    // absent parameter rather than a present blank one.
    const request = http.expectOne((r) => r.url === '/api/v1/dashboard/summary');

    expect(request.request.params.get('country')).toBe('DE');
    expect(request.request.params.has('department')).toBeFalse();
    expect(request.request.params.has('level')).toBeFalse();
    request.flush(summary);
  });
});
