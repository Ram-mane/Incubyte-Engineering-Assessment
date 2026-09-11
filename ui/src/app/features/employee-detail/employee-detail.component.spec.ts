import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { SessionService } from '../../core/auth/session.service';
import { Employee, SalaryRevision } from '../employees/employee.model';
import { EmployeeDetailComponent } from './employee-detail.component';

const alice: Employee = {
  id: '11111111-1111-1111-1111-111111111111',
  employeeNumber: 'E00042',
  givenName: 'Alice',
  familyName: 'Kapoor',
  email: 'alice.kapoor@acme.test',
  country: 'IN',
  department: 'Engineering',
  jobTitle: 'Software Engineer',
  level: 'SENIOR',
  salary: { amount: '1380000.00', currency: 'INR' },
};

const history: SalaryRevision[] = [
  {
    previousAmount: { amount: '1200000.00', currency: 'INR' },
    newAmount: { amount: '1380000.00', currency: 'INR' },
    reason: 'MERIT',
    changedBy: '22222222-2222-2222-2222-222222222222',
    changedAt: '2026-09-11T09:15:30Z',
    note: 'Annual merit review',
  },
];

describe('EmployeeDetailComponent', () => {
  let fixture: ComponentFixture<EmployeeDetailComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<EmployeeApiService>;

  const render = async (role: 'HR_MANAGER' | 'HR_ANALYST') => {
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['byId', 'revisions', 'changeSalary']);
    api.byId.and.returnValue(of(alice));
    api.revisions.and.returnValue(of(history));

    await TestBed.configureTestingModule({
      imports: [EmployeeDetailComponent],
      providers: [
        { provide: EmployeeApiService, useValue: api },
        { provide: MatDialog, useValue: jasmine.createSpyObj<MatDialog>('MatDialog', ['open']) },
        provideRouter([]),
      ],
    }).compileComponents();

    TestBed.inject(SessionService).start({ token: 't', expiresAt: '2099-01-01T00:00:00Z', role });
    fixture = TestBed.createComponent(EmployeeDetailComponent);
    fixture.componentRef.setInput('id', alice.id);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  };

  it('names the person and what they are paid', async () => {
    await render('HR_MANAGER');

    expect(element.querySelector('h1')?.textContent).toContain('Alice Kapoor');
    expect(element.textContent).toContain('1,380,000');
  });

  it('shows the pay history with what it was and what it became', async () => {
    await render('HR_MANAGER');

    const rows = element.querySelectorAll('table tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('1,200,000');
    expect(rows[0].textContent).toContain('1,380,000');
    expect(rows[0].textContent).toContain('MERIT');
  });

  it('offers a manager the change-pay action', async () => {
    await render('HR_MANAGER');

    const actions = Array.from(element.querySelectorAll('button')).map((b) => b.textContent?.trim());
    expect(actions).toContain('Change pay');
  });

  it('does not offer an analyst a button the server would refuse', async () => {
    await render('HR_ANALYST');

    const actions = Array.from(element.querySelectorAll('button')).map((b) => b.textContent?.trim());
    // The server enforces this; the screen simply does not invite a 403.
    expect(actions).not.toContain('Change pay');
    expect(element.textContent).toContain('analysts cannot change pay');
  });
});
