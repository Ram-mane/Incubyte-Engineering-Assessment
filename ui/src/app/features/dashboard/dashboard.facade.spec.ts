import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DashboardFacade } from './dashboard.facade';
import { DashboardQuery, PayrollSummary } from './dashboard.model';

const summary = (headcount: number, total: string): PayrollSummary => ({
  headcount,
  totalSpend: { amount: total, currency: 'USD' },
  averageSalary: { amount: '123265.10', currency: 'USD' },
  medianSalary: { amount: '113930.00', currency: 'USD' },
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
});

describe('DashboardFacade', () => {
  let api: jasmine.SpyObj<DashboardApiService>;
  let employees: jasmine.SpyObj<EmployeeApiService>;
  let facade: DashboardFacade;
  let asked: DashboardQuery[];

  const answering = (...summaries: PayrollSummary[]) => {
    let call = 0;
    api.summary.and.callFake((query: DashboardQuery) => {
      asked.push(query);
      return of(summaries[Math.min(call++, summaries.length - 1)]);
    });
  };

  beforeEach(() => {
    asked = [];
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', ['summary']);
    employees = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['filterOptions']);
    employees.filterOptions.and.returnValue(
      of({ countries: ['DE', 'IN'], departments: ['Engineering'], jobTitles: ['Software Engineer'], levels: ['SENIOR'] }),
    );
    TestBed.configureTestingModule({
      providers: [
        DashboardFacade,
        { provide: DashboardApiService, useValue: api },
        { provide: EmployeeApiService, useValue: employees },
      ],
    });
    facade = TestBed.inject(DashboardFacade);
  });

  it('fills all four cards from a single request', () => {
    answering(summary(9842, '1213179157.20'));

    facade.load({});

    // Four cards from four round trips is a visible stutter on every filter change, and four
    // queries can disagree with each other besides.
    expect(api.summary).toHaveBeenCalledTimes(1);
    expect(facade.summary()?.headcount).toBe(9842);
    expect(facade.summary()?.totalSpend.amount).toBe('1213179157.20');
  });

  it('moves every card together when the filters change', () => {
    answering(summary(9842, '1213179157.20'), summary(412, '50112900.00'));

    facade.load({});
    facade.load({ country: 'DE' });

    expect(asked[1].country).toBe('DE');
    expect(facade.summary()?.headcount).toBe(412);
    expect(facade.summary()?.totalSpend.amount).toBe('50112900.00');
  });

  it('reports a filter nobody matches as zero rather than as nothing', () => {
    answering({
      headcount: 0,
      totalSpend: { amount: '0.00', currency: 'USD' },
      averageSalary: { amount: '0.00', currency: 'USD' },
      medianSalary: { amount: '0.00', currency: 'USD' },
      reportingCurrency: 'USD',
      ratesAsOf: '2026-09-01',
    });

    facade.load({ country: 'DE', department: 'Legal' });

    // Zero people is an answer. It is not a failure, and it is not an absent summary.
    expect(facade.summary()?.headcount).toBe(0);
    expect(facade.hasFailed()).toBeFalse();
    expect(facade.isEmpty()).toBeTrue();
  });

  it('says so when the summary could not be loaded', () => {
    api.summary.and.returnValue(throwError(() => new Error('offline')));

    facade.load({});

    expect(facade.hasFailed()).toBeTrue();
    expect(facade.isLoading()).toBeFalse();
  });

  it('drops a stale summary rather than showing figures from the last query', () => {
    answering(summary(9842, '1213179157.20'));
    facade.load({});
    api.summary.and.returnValue(throwError(() => new Error('offline')));

    facade.load({ country: 'DE' });

    // Leaving the old numbers up under a new filter is a dashboard that lies about what it is
    // showing, which is worse than a dashboard that says it failed.
    expect(facade.summary()).toBeNull();
    expect(facade.hasFailed()).toBeTrue();
  });

  it('offers the filter values the directory actually contains', () => {
    answering(summary(9842, '1213179157.20'));

    facade.loadFilterOptions();

    expect(facade.filterOptions().countries).toEqual(['DE', 'IN']);
    expect(facade.filterOptions().levels).toEqual(['SENIOR']);
  });
});
