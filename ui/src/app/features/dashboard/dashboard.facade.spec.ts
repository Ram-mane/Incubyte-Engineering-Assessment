import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DashboardFacade } from './dashboard.facade';
import { DashboardQuery, PayrollBreakdown, PayrollSummary } from './dashboard.model';

const summary = (headcount: number, total: string): PayrollSummary => ({
  headcount,
  totalSpend: { amount: total, currency: 'USD' },
  averageSalary: { amount: '123265.10', currency: 'USD' },
  medianSalary: { amount: '113930.00', currency: 'USD' },
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
});

const breakdown = (...names: string[]): PayrollBreakdown => ({
  groupedBy: 'department',
  groups: names.map((name, index) => ({
    name,
    headcount: 10 - index,
    totalSpend: { amount: `${1000 - index * 100}.00`, currency: 'USD' },
    averageSalary: { amount: '100.00', currency: 'USD' },
  })),
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
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', [
      'summary',
      'breakdown',
      'distribution',
    ]);
    api.breakdown.and.returnValue(of(breakdown('Engineering', 'Finance')));
    api.distribution.and.returnValue(
      of({ groupedBy: 'jobTitle' as const, groups: [], reportingCurrency: 'USD', ratesAsOf: '2026-09-01' }),
    );
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

  it('ignores an answer to a filter that is no longer on screen', () => {
    const de = new Subject<PayrollSummary>();
    const everyone = new Subject<PayrollSummary>();
    const queue = [de, everyone];
    api.summary.and.callFake(() => queue.shift()!.asObservable());

    facade.load({ country: 'DE' });
    facade.load({});
    everyone.next(summary(9842, '1213179157.20'));
    de.next(summary(412, '50112900.00'));

    // The slow DE answer lands last. Painting it would put 412 people under a filter bar reading
    // "Any country", and the person reading it has no way to know which question it answers.
    expect(facade.summary()?.headcount).toBe(9842);
  });

  it('does not let a stale failure erase figures that did load', () => {
    const de = new Subject<PayrollSummary>();
    const everyone = new Subject<PayrollSummary>();
    const queue = [de, everyone];
    api.summary.and.callFake(() => queue.shift()!.asObservable());

    facade.load({ country: 'DE' });
    facade.load({});
    everyone.next(summary(9842, '1213179157.20'));
    de.error(new Error('offline'));

    expect(facade.summary()?.headcount).toBe(9842);
    expect(facade.hasFailed()).toBeFalse();
  });

  it('asks the same filters of the cards and of the breakdown', () => {
    answering(summary(9842, '1213179157.20'));

    facade.load({ country: 'DE' });

    expect(api.summary).toHaveBeenCalledTimes(1);
    expect(api.breakdown).toHaveBeenCalledTimes(1);
    expect(api.breakdown.calls.mostRecent().args[1].country).toBe('DE');
    expect(facade.breakdown()?.groups.length).toBe(2);
  });

  it('re-asks only the breakdown when the grouping changes', () => {
    answering(summary(9842, '1213179157.20'));
    facade.load({});

    facade.groupBy('country');

    // The cards do not depend on the grouping. Re-fetching them would be a second round trip for
    // an answer already on screen, and a visible flicker on a control that should feel instant.
    expect(api.summary).toHaveBeenCalledTimes(1);
    expect(api.breakdown).toHaveBeenCalledTimes(2);
    expect(api.breakdown.calls.mostRecent().args[0]).toBe('country');
  });

  it('keeps the grouping when the filters change', () => {
    answering(summary(9842, '1213179157.20'));
    facade.load({});
    facade.groupBy('level');

    facade.load({ country: 'DE' });

    // Changing a filter is not a request to look at a different dimension.
    expect(api.breakdown.calls.mostRecent().args[0]).toBe('level');
  });

  it('offers the filter values the directory actually contains', () => {
    answering(summary(9842, '1213179157.20'));

    facade.loadFilterOptions();

    expect(facade.filterOptions().countries).toEqual(['DE', 'IN']);
    expect(facade.filterOptions().levels).toEqual(['SENIOR']);
  });
});
