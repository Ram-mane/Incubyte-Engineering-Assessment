import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { Employee } from '../employees/employee.model';
import { EmployeeDetailFacade } from './employee-detail.facade';

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

describe('EmployeeDetailFacade', () => {
  let api: jasmine.SpyObj<EmployeeApiService>;
  let facade: EmployeeDetailFacade;

  beforeEach(() => {
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['byId', 'revisions', 'changeSalary']);
    api.byId.and.returnValue(of(alice));
    api.revisions.and.returnValue(of([]));
    TestBed.configureTestingModule({
      providers: [EmployeeDetailFacade, { provide: EmployeeApiService, useValue: api }],
    });
    facade = TestBed.inject(EmployeeDetailFacade);
  });

  it('shows the pay now on record when somebody else changed it first', () => {
    api.changeSalary.and.returnValue(
      throwError(() => ({
        status: 409,
        error: {
          detail:
            "This employee's pay changed while you were deciding, so the change was not applied. " +
            'Reload employee 1111 and decide again against the salary now on record.',
        },
      })),
    );

    facade.changeSalary(alice.id, { amount: '1500000.00', currency: 'INR', reason: 'MERIT' }, () => undefined);

    expect(facade.changeError()).toContain('Reload');
    // Re-read, because the figure on screen is now the stale one this manager decided against.
    expect(api.byId).toHaveBeenCalled();
  });

  it('does not reload after a refusal that leaves the salary where it was', () => {
    api.changeSalary.and.returnValue(
      throwError(() => ({ status: 422, error: { detail: 'Salary must be positive.' } })),
    );

    facade.changeSalary(alice.id, { amount: '-1', currency: 'INR', reason: 'MERIT' }, () => undefined);

    expect(facade.changeError()).toContain('positive');
    // Nothing moved, so there is nothing to re-read: reloading would only hide the message.
    expect(api.byId).not.toHaveBeenCalled();
  });
});
