import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { BandView, Employee } from '../employees/employee.model';
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
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', [
      'byId',
      'revisions',
      'changeSalary',
      'band',
    ]);
    api.band.and.returnValue(of({ defined: false }));
    api.byId.and.returnValue(of(alice));
    api.revisions.and.returnValue(of([]));
    api.revisions.calls.reset();
    TestBed.configureTestingModule({
      providers: [EmployeeDetailFacade, { provide: EmployeeApiService, useValue: api }],
    });
    facade = TestBed.inject(EmployeeDetailFacade);
  });

  it('stops showing the previous band verdict while the new one is being read', () => {
    // The state a manager is left in after a raise: the header already shows the new figure,
    // because the employee and the band are read in sequence rather than together. A verdict
    // that was true of the old salary sitting beside the new one is a claim about this pay
    // that nothing has checked - "SGD 9,000,000 / Within band" was on screen for four seconds.
    const within: BandView = {
      defined: true,
      min: { amount: '1000000.00', currency: 'INR' },
      mid: { amount: '1400000.00', currency: 'INR' },
      max: { amount: '1800000.00', currency: 'INR' },
      compaRatio: '0.99',
      position: 'WITHIN',
    };
    api.band.and.returnValue(of(within));
    facade.load(alice.id);
    expect(facade.band()?.position).toBe('WITHIN');

    const pending = new Subject<BandView>();
    api.band.and.returnValue(pending);
    api.byId.and.returnValue(of({ ...alice, salary: { amount: '9000000.00', currency: 'INR' } }));
    facade.load(alice.id);

    // Null with no failure is the one state the template renders as "Loading the approved
    // range" - so the panel says it is working rather than answering for the old salary.
    expect(facade.band()).toBeNull();
    expect(facade.bandFailed()).toBeFalse();

    pending.next({ ...within, position: 'ABOVE_MAX', compaRatio: '6.43' });
    expect(facade.band()?.position).toBe('ABOVE_MAX');
  });

  it('shows the pay now on record when somebody else changed it first', () => {
    // What the reload finds: the winner's figure, not the one this manager decided against.
    api.byId.and.returnValue(of({ ...alice, salary: { amount: '1450000.00', currency: 'INR' } }));
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
    // The point is not that a reload was requested, it is that the figure on screen changed. A
    // reload whose result is discarded leaves the manager reading the stale number under a message
    // telling them to reload.
    expect(facade.employee()?.salary.amount).toBe('1450000.00');
    expect(api.byId).toHaveBeenCalledWith(alice.id);
    // The winner's entry belongs in the log the manager is about to look at.
    expect(api.revisions).toHaveBeenCalledTimes(1);
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
