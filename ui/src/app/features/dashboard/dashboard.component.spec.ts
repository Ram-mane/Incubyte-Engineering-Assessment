import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { EmployeeApiService } from '../../core/api/employee-api.service';
import { DashboardComponent } from './dashboard.component';
import { DashboardQuery, PayrollSummary } from './dashboard.model';

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

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<DashboardApiService>;
  let employees: jasmine.SpyObj<EmployeeApiService>;
  let asked: DashboardQuery[];

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
    api = jasmine.createSpyObj<DashboardApiService>('DashboardApiService', ['summary']);
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
    const headings = Array.from(element.querySelectorAll('h2')).map((h) => h.textContent?.trim());

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
