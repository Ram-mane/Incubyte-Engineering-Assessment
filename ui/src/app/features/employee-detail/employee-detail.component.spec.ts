import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { SessionService } from '../../core/auth/session.service';
import { BandView, Employee, SalaryRevision } from '../employees/employee.model';
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
    changedByEmail: 'hr.manager@acme.example',
    changedAt: '2026-09-11T09:15:30Z',
    note: 'Annual merit review',
  },
];

const band: BandView = {
  defined: true,
  min: { amount: '1100000.00', currency: 'INR' },
  mid: { amount: '1300000.00', currency: 'INR' },
  max: { amount: '1560000.00', currency: 'INR' },
  compaRatio: '1.0615',
  position: 'WITHIN',
};

describe('EmployeeDetailComponent', () => {
  let fixture: ComponentFixture<EmployeeDetailComponent>;
  let element: HTMLElement;
  let api: jasmine.SpyObj<EmployeeApiService>;

  const render = async (role: 'HR_MANAGER' | 'HR_ANALYST', showing: BandView | 'failed' = band) => {
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', [
      'byId',
      'revisions',
      'changeSalary',
      'band',
    ]);
    api.band.and.returnValue(showing === 'failed' ? throwError(() => new Error('offline')) : of(showing));
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

  it('says who made each change, by name rather than by id', async () => {
    await render('HR_MANAGER');

    const row = element.querySelector('table tbody tr');

    // "Who raised this salary" is not a question a UUID answers. Rendering the email is also
    // what makes the JPA read touch the association - see docs/evidence/09-jpa-read-path.txt.
    expect(row?.textContent).toContain('hr.manager@acme.example');
    expect(row?.textContent).not.toContain('22222222-2222');
  });

  it('shows the approved range for this role and where the person sits in it', async () => {
    await render('HR_MANAGER');
    const section = element.querySelector('[aria-labelledby="band-heading"]');

    expect(section?.textContent).toContain('1,100,000');
    expect(section?.textContent).toContain('1,300,000');
    expect(section?.textContent).toContain('1,560,000');
    expect(section?.textContent).toContain('Within band');
  });

  it('says plainly when the org has no band for this role', async () => {
    await render('HR_MANAGER', { defined: false });
    const section = element.querySelector('[aria-labelledby="band-heading"]');

    // Hiding the section would leave a reader unable to tell "no band" from "not loaded".
    expect(section?.textContent).toContain('No band defined for this role and level');
  });

  it('reports a salary above the maximum without warning about it', async () => {
    await render('HR_MANAGER', { ...band, compaRatio: '3.8462', position: 'ABOVE_MAX' });
    const section = element.querySelector('[aria-labelledby="band-heading"]');

    // Displayed, never enforced: it states the position and offers no alarm, no confirmation and
    // nothing disabled. The change-pay button is exactly as available as it was.
    expect(section?.textContent).toContain('Above band maximum');
    expect(section?.querySelector('[role="alert"]')).toBeNull();
    const changePay = Array.from(element.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Change pay',
    );
    expect(changePay).withContext('the change-pay button must still be there').toBeTruthy();
    expect(changePay!.disabled).toBeFalse();
  });

  it('does not claim there is no band when the band request failed', async () => {
    await render('HR_MANAGER', 'failed');
    const section = element.querySelector('[aria-labelledby="band-heading"]');

    // "No band defined" is a claim about the organisation. A failed request does not support it.
    expect(section?.textContent).toContain('could not be loaded');
    expect(section?.textContent).not.toContain('No band defined');
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
