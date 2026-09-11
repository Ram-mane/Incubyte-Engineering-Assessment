import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { EmployeeListComponent } from './employee-list.component';
import { DirectoryQuery, EmployeePage } from './employee.model';

const page: EmployeePage = {
  items: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      employeeNumber: 'E00042',
      givenName: 'Alice',
      familyName: 'Kapoor',
      email: 'alice.kapoor@acme.test',
      country: 'IN',
      department: 'Engineering',
      jobTitle: 'Software Engineer',
      level: 'SENIOR',
      salary: { amount: '1200000.00', currency: 'INR' },
    },
  ],
  nextCursor: 'cursor-1',
  totalApprox: 10000,
};

describe('EmployeeListComponent', () => {
  let fixture: ComponentFixture<EmployeeListComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<EmployeeApiService>;
  let asked: DirectoryQuery[];

  beforeEach(async () => {
    asked = [];
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['page', 'filterOptions']);
    api.page.and.callFake((query: DirectoryQuery) => {
      asked.push(query);
      return of(page);
    });
    api.filterOptions.and.returnValue(
      of({
        countries: ['DE', 'IN'],
        departments: ['Engineering', 'Finance'],
        jobTitles: ['Accountant', 'Software Engineer'],
        levels: ['JUNIOR', 'SENIOR'],
      }),
    );

    await TestBed.configureTestingModule({
      imports: [EmployeeListComponent],
      // Rows link to the detail screen, so the component needs a router to render at all.
      providers: [{ provide: EmployeeApiService, useValue: api }, provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(EmployeeListComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });

  it('is titled for what it shows', () => {
    expect(element.querySelector('h1')?.textContent).toContain('Employee directory');
  });

  it('lists an employee as a row in a table', () => {
    const rows = element.querySelectorAll('table tbody tr');

    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Kapoor, Alice');
    expect(rows[0].textContent).toContain('Engineering');
  });

  it('shows a salary in the currency it is paid in', () => {
    const row = element.querySelector('table tbody tr');

    expect(row?.textContent).toContain('1,200,000');
    expect(row?.textContent).toMatch(/₹|INR/);
  });

  it('says how much of the org is on screen', () => {
    const count = element.querySelector('[aria-live="polite"]');

    expect(count?.textContent).toContain('1–1 of 10,000');
  });

  it('offers next and previous rather than page numbers', () => {
    const pager = element.querySelector('[aria-label="Directory pages"]');
    const buttons = Array.from(pager?.querySelectorAll('button') ?? []).map((b) => b.textContent?.trim());

    // Keyset paging has a page after this one, not a page seventeen. A numbered pager would be a
    // promise the query cannot keep.
    expect(buttons).toEqual(['Previous', 'Next']);
  });

  it('cannot go back from the first page', () => {
    const previous = element.querySelector<HTMLButtonElement>('[aria-label="Directory pages"] button');

    expect(previous?.disabled).toBeTrue();
  });

  it('offers the filters the directory actually contains', () => {
    const labels = Array.from(element.querySelectorAll('mat-label')).map((l) => l.textContent?.trim());

    expect(labels).toContain('Country');
    expect(labels).toContain('Department');
    expect(labels).toContain('Job title');
    expect(labels).toContain('Level');
    expect(labels).toContain('Search name or email');
  });

  it('sends the search term to the server rather than filtering in the browser', () => {
    const search = element.querySelector<HTMLInputElement>('input[name="q"]');
    search!.value = 'kapoor';
    search!.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    element.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    // Ten thousand people are never in the browser, so the filter cannot be applied there.
    expect(asked[asked.length - 1].q).toBe('kapoor');
  });
});
