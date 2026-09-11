import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { EmployeeDirectoryFacade } from './employee-directory.facade';
import { EmployeePage } from './employee.model';

const aPage = (page: number, size: number): EmployeePage => ({
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
  page,
  size,
  total: 10000,
});

describe('EmployeeDirectoryFacade', () => {
  let api: jasmine.SpyObj<EmployeeApiService>;
  let facade: EmployeeDirectoryFacade;

  beforeEach(() => {
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['page']);
    TestBed.configureTestingModule({
      providers: [EmployeeDirectoryFacade, { provide: EmployeeApiService, useValue: api }],
    });
    facade = TestBed.inject(EmployeeDirectoryFacade);
  });

  it('holds one page of employees, not the whole org', () => {
    api.page.and.returnValue(of(aPage(0, 50)));

    facade.load(0, 50);

    expect(facade.employees().length).toBe(1);
    expect(facade.total()).toBe(10000);
  });

  it('reports which slice of the org is on screen', () => {
    api.page.and.returnValue(of(aPage(3, 50)));

    facade.load(3, 50);

    expect(facade.firstOnPage()).toBe(151);
    expect(facade.lastOnPage()).toBe(200);
  });

  it('says the last page ends at the last employee rather than a round number', () => {
    api.page.and.returnValue(of({ ...aPage(199, 50), total: 10000 }));

    facade.load(199, 50);

    expect(facade.lastOnPage()).toBe(10000);
  });

  it('says so when the directory could not be loaded', () => {
    api.page.and.returnValue(throwError(() => new Error('offline')));

    facade.load(0, 50);

    // An empty table with no explanation reads as "nobody works here".
    expect(facade.hasFailed()).toBeTrue();
    expect(facade.isLoading()).toBeFalse();
    expect(facade.employees()).toEqual([]);
  });
});
