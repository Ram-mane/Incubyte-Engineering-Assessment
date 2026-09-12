import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DashboardComponent } from './dashboard.component';
import {
  BreakdownDimension,
  DashboardQuery,
  DistributionDimension,
  PayrollBreakdown,
  PayrollSummary,
  SalaryDistribution,
} from './dashboard.model';

const populated: PayrollSummary = {
  headcount: 9_842,
  totalSpend: { amount: '1213179157.20', currency: 'USD' },
  averageSalary: { amount: '123265.10', currency: 'USD' },
  medianSalary: { amount: '113930.00', currency: 'USD' },
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
};

const germany: PayrollSummary = {
  headcount: 1_700,
  totalSpend: { amount: '229327134.00', currency: 'USD' },
  averageSalary: { amount: '134898.31', currency: 'USD' },
  medianSalary: { amount: '128643.00', currency: 'USD' },
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
};

const nobody: PayrollSummary = {
  headcount: 0,
  totalSpend: { amount: '0.00', currency: 'USD' },
  averageSalary: { amount: '0.00', currency: 'USD' },
  medianSalary: { amount: '0.00', currency: 'USD' },
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
};

/** The figure on the card named by that heading — the card carries the name, not the figure. */
const valueUnder = (element: HTMLElement, headingId: string): string =>
  element.querySelector(`article[aria-labelledby="${headingId}"] p`)?.textContent?.trim() ?? '';

const byDepartment: PayrollBreakdown = {
  groupedBy: 'department',
  groups: [
    {
      name: 'Engineering',
      headcount: 4_200,
      totalSpend: { amount: '620000000.00', currency: 'USD' },
      averageSalary: { amount: '147619.05', currency: 'USD' },
    },
    {
      name: 'Finance',
      headcount: 900,
      totalSpend: { amount: '110000000.00', currency: 'USD' },
      averageSalary: { amount: '122222.22', currency: 'USD' },
    },
  ],
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
};

const noGroups: PayrollBreakdown = { ...byDepartment, groups: [] };

const money = (amount: string) => ({ amount, currency: 'USD' });

const byRole: SalaryDistribution = {
  groupedBy: 'jobTitle',
  groups: [
    {
      name: 'Software Engineer',
      headcount: 1_240,
      lowest: money('62000.00'),
      p25: money('98000.00'),
      median: money('124000.00'),
      p75: money('151000.00'),
      p90: money('178000.00'),
      highest: money('240000.00'),
    },
  ],
  reportingCurrency: 'USD',
  ratesAsOf: '2026-09-01',
};

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<DashboardApiService>;
  let employees: jasmine.SpyObj<EmployeeApiService>;
  let asked: DashboardQuery[];
  let grouped: BreakdownDimension[];

  const showing = (summary: PayrollSummary) => {
    api.summary.and.callFake((query: DashboardQuery) => {
      asked.push(query);
      return of(summary);
    });
  };

  /** Opens the select with the given label and clicks one of its options, as a person would. */
  const choose = async (label: string, value: string) => {
    const field = Array.from(element.querySelectorAll('mat-form-field')).find(
      (f) => f.querySelector('mat-label')?.textContent?.trim() === label,
    );
    field!.querySelector<HTMLElement>('[role="combobox"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    const option = Array.from(
      document.querySelectorAll<HTMLElement>('mat-option'),
    ).find((o) => o.textContent?.trim() === value);
    option!.click();
    fixture.detectChanges();
    await fixture.whenStable();
  };

  const submitFilters = () => {
    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };

  const render = async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: DashboardApiService, useValue: api },
        { provide: EmployeeApiService, useValue: employees },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  };

  beforeEach(() => {
    asked = [];
    grouped = [];
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', [
      'summary',
      'breakdown',
      'distribution',
    ]);
    api.breakdown.and.callFake((dimension: BreakdownDimension) => {
      grouped.push(dimension);
      return of(dimension === 'department' ? byDepartment : { ...byDepartment, groupedBy: dimension });
    });
    api.distribution.and.returnValue(of(byRole));
    employees = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['filterOptions']);
    employees.filterOptions.and.returnValue(
      of({
        countries: ['DE', 'IN'],
        departments: ['Engineering', 'Finance'],
        jobTitles: ['Accountant', 'Software Engineer'],
        levels: ['JUNIOR', 'SENIOR'],
      }),
    );
    showing(populated);
  });

  it('is titled for what it shows', async () => {
    await render();

    expect(element.querySelector('h1')?.textContent).toContain('Compensation dashboard');
  });

  it('names each of the four cards with a heading', async () => {
    await render();
    // Scoped to the cards: the screen has other headings, and "every h2 on the page" would break
    // every time a section is added rather than when a card changes.
    const headings = Array.from(element.querySelectorAll('article h2')).map((h) => h.textContent?.trim());

    expect(headings).toEqual([
      'Total payroll spend',
      'Active headcount',
      'Average salary',
      'Median salary',
    ]);
  });

  it('shows the payroll figures in the reporting currency', async () => {
    await render();

    expect(valueUnder(element, 'kpi-total-spend')).toContain('1,213,179,157');
    expect(valueUnder(element, 'kpi-total-spend')).toMatch(/\$|USD/);
    expect(valueUnder(element, 'kpi-average-salary')).toContain('123,265');
    expect(valueUnder(element, 'kpi-median-salary')).toContain('113,930');
  });

  it('shows the headcount as a count, not as money', async () => {
    await render();

    expect(valueUnder(element, 'kpi-headcount')).toBe('9,842');
  });

  it('says no conversion was needed rather than printing an empty date', async () => {
    showing({ ...populated, ratesAsOf: undefined });
    await render();

    // The server omits the date when nothing needed converting - everybody matched was already
    // paid in the reporting currency. "at exchange rates of" followed by nothing is worse than
    // saying so.
    expect(element.querySelector('.dashboard__basis')?.textContent).toContain('No conversion was needed');
    expect(element.querySelector('.dashboard__basis')?.textContent).not.toContain('exchange rates of');
  });

  it('says which day the exchange rates are from', async () => {
    await render();

    // A total normalised through "whatever rate was current" is a number nobody can reproduce, so
    // the day has to be on screen — not just the year.
    expect(element.textContent).toContain('Sep 1, 2026');
  });

  it('offers the four filters the dashboard supports', async () => {
    await render();
    const labels = Array.from(element.querySelectorAll('mat-label')).map((l) => l.textContent?.trim());

    expect(labels).toEqual(['Country', 'Department', 'Job title', 'Level']);
  });

  it('recomputes every card from one request when a country is chosen', async () => {
    await render();
    showing(germany);

    await choose('Country', 'DE');
    submitFilters();

    // One request, not one per card: the four figures on screen are four columns of one answer.
    expect(asked.length).toBe(2);
    expect(asked[1].country).toBe('DE');
    expect(valueUnder(element, 'kpi-headcount')).toBe('1,700');
    expect(valueUnder(element, 'kpi-total-spend')).toContain('229,327,134');
    expect(valueUnder(element, 'kpi-average-salary')).toContain('134,898');
    expect(valueUnder(element, 'kpi-median-salary')).toContain('128,643');
  });

  it('clears back to the whole company', async () => {
    await render();
    await choose('Country', 'DE');
    submitFilters();

    element.querySelector<HTMLButtonElement>('button[name="clear"]')!.click();
    fixture.detectChanges();

    expect(asked[asked.length - 1].country).toBe('');
  });

  it('renders a filter nobody matches as zero rather than as a broken card', async () => {
    showing(nobody);
    await render();

    // All four, exactly: a card left blank or showing the previous filter's figure is the defect
    // this is guarding, and "contains a zero" would pass for $1,000,000.
    expect(valueUnder(element, 'kpi-headcount')).toBe('0');
    expect(valueUnder(element, 'kpi-total-spend')).toBe('$0');
    expect(valueUnder(element, 'kpi-average-salary')).toBe('$0');
    expect(valueUnder(element, 'kpi-median-salary')).toBe('$0');
    expect(element.querySelector('[role="alert"]')).toBeNull();
    expect(element.textContent).toContain('No employees match these filters');
  });

  it('says so when the dashboard could not be loaded', async () => {
    api.summary.and.returnValue(throwError(() => new Error('offline')));
    await render();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('could not be loaded');
  });
});

describe('DashboardComponent breakdown', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<DashboardApiService>;
  let employees: jasmine.SpyObj<EmployeeApiService>;
  let grouped: BreakdownDimension[];

  const render = async (breakdown: PayrollBreakdown = byDepartment) => {
    grouped = [];
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', [
      'summary',
      'breakdown',
      'distribution',
    ]);
    api.summary.and.returnValue(of(populated));
    api.distribution.and.returnValue(of(byRole));
    api.breakdown.and.callFake((dimension: BreakdownDimension) => {
      grouped.push(dimension);
      return of(breakdown);
    });
    employees = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['filterOptions']);
    employees.filterOptions.and.returnValue(
      of({ countries: ['DE'], departments: ['Engineering'], jobTitles: ['Software Engineer'], levels: ['SENIOR'] }),
    );

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: DashboardApiService, useValue: api },
        { provide: EmployeeApiService, useValue: employees },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  };

  const rows = () => Array.from(element.querySelectorAll('table.dashboard__breakdown tbody tr'));

  it('names the section for the question it answers', async () => {
    await render();

    expect(element.querySelector('h2#breakdown-heading')?.textContent).toContain('Where the money goes');
  });

  it('lists one row per group, largest first as the server ordered them', async () => {
    await render();

    expect(rows().length).toBe(2);
    expect(rows()[0].textContent).toContain('Engineering');
    expect(rows()[0].textContent).toContain('4,200');
    expect(rows()[0].textContent).toContain('620,000,000');
    expect(rows()[1].textContent).toContain('Finance');
  });

  it('groups by department until asked otherwise', async () => {
    await render();

    expect(grouped).toEqual(['department']);
  });

  it('re-asks the server when the grouping changes rather than regrouping in the browser', async () => {
    await render();

    // Find the toggle by the label a person reads, then click whatever element it renders.
    const toggle = Array.from(element.querySelectorAll('mat-button-toggle')).find(
      (t) => t.textContent?.trim() === 'Country',
    );
    toggle!.querySelector<HTMLElement>('button')!.click();
    fixture.detectChanges();

    // The browser holds the groups of one dimension, never the rows they were computed from.
    expect(grouped).toEqual(['department', 'country']);
  });

  it('says there is nothing to break down rather than drawing an empty table', async () => {
    await render(noGroups);

    expect(rows().length).toBe(0);
    expect(element.textContent).toContain('No groups to show');
  });

  it('draws each bar in proportion to the largest group', async () => {
    await render();

    const bars = Array.from(element.querySelectorAll<HTMLElement>('.dashboard__bar'));

    // 110,000,000 of 620,000,000 is 17.7%. The bar is decoration - the figure beside it is the
    // number - so it is hidden from assistive technology rather than labelled twice.
    expect(bars[0].style.width).toBe('100%');
    expect(bars[1].style.width).toBe('17.74%');
    expect(bars[0].getAttribute('aria-hidden')).toBe('true');
  });
});

describe('DashboardComponent distribution', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<DashboardApiService>;
  let distributed: DistributionDimension[];

  const render = async (distribution: SalaryDistribution = byRole) => {
    distributed = [];
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', [
      'summary',
      'breakdown',
      'distribution',
    ]);
    api.summary.and.returnValue(of(populated));
    api.breakdown.and.returnValue(of(byDepartment));
    api.distribution.and.callFake((dimension: DistributionDimension) => {
      distributed.push(dimension);
      return of(distribution);
    });
    const employees = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['filterOptions']);
    employees.filterOptions.and.returnValue(
      of({ countries: ['DE'], departments: ['Engineering'], jobTitles: ['Software Engineer'], levels: ['SENIOR'] }),
    );

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: DashboardApiService, useValue: api },
        { provide: EmployeeApiService, useValue: employees },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  };

  const row = () => element.querySelector('table.dashboard__distribution tbody tr');

  it('names the section for the question it answers', async () => {
    await render();

    expect(element.querySelector('h2#distribution-heading')?.textContent).toContain('How pay is spread');
  });

  it('shows the quartiles and the range of a role', async () => {
    await render();

    expect(row()?.textContent).toContain('Software Engineer');
    expect(row()?.textContent).toContain('98,000');
    expect(row()?.textContent).toContain('124,000');
    expect(row()?.textContent).toContain('151,000');
    expect(row()?.textContent).toContain('178,000');
  });

  it('groups by role until asked otherwise, because a role is the peer group', async () => {
    await render();

    expect(distributed).toEqual(['jobTitle']);
  });

  it('offers department but never country, which is not a peer group', async () => {
    await render();

    const options = Array.from(
      element.querySelectorAll('[aria-label="Group the distribution by"] mat-button-toggle'),
    ).map((t) => t.textContent?.trim());

    expect(options).toEqual(['Role', 'Department']);
  });

  it('draws the interquartile range as a bar positioned within the group range', async () => {
    await render();

    const box = element.querySelector<HTMLElement>('.dashboard__iqr');

    // 62,000 to 240,000 is the full width; p25 98,000 starts at 20.22% and p75 151,000 ends at
    // 50.0%, so the box is 29.78% wide.
    expect(box?.style.left).toBe('20.22%');
    expect(box?.style.width).toBe('29.78%');
    // Decoration - the figures are in the row beside it. Asserted as "hidden from assistive
    // technology" rather than "carries the attribute", because hiding the track hides the bar
    // inside it and either shape is correct.
    expect(box?.closest('[aria-hidden="true"]')).not.toBeNull();
  });

  it('says there is nothing to distribute rather than drawing an empty table', async () => {
    await render({ ...byRole, groups: [] });

    expect(element.textContent).toContain('No groups to show');
  });
});
