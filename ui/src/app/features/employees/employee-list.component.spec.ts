import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { EmployeeListComponent } from './employee-list.component';
import { EmployeePage } from './employee.model';

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
  page: 0,
  size: 50,
  total: 10000,
};

describe('EmployeeListComponent', () => {
  let fixture: ComponentFixture<EmployeeListComponent>;
  let element: HTMLElement;

  beforeEach(async () => {
    const api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['page']);
    api.page.and.returnValue(of(page));

    await TestBed.configureTestingModule({
      imports: [EmployeeListComponent],
      providers: [{ provide: EmployeeApiService, useValue: api }],
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

    expect(count?.textContent).toContain('1–50 of 10,000');
  });

  it('offers a pager the whole org can be walked with', () => {
    const pager = element.querySelector('[aria-label="Select a page of employees"]');

    expect(pager).not.toBeNull();
    expect(pager?.textContent).toContain('10000');
  });
});
