import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { EmployeeApiService } from '../../core/api/employee-api.service';
import { EmployeeDirectoryFacade } from './employee-directory.facade';
import { DirectoryQuery, Employee, EmployeePage } from './employee.model';

const person = (familyName: string): Employee => ({
  id: '11111111-1111-1111-1111-111111111111',
  employeeNumber: 'E00042',
  givenName: 'Alice',
  familyName,
  email: 'alice.kapoor@acme.test',
  country: 'IN',
  department: 'Engineering',
  jobTitle: 'Software Engineer',
  level: 'SENIOR',
  salary: { amount: '1200000.00', currency: 'INR' },
});

describe('EmployeeDirectoryFacade', () => {
  let api: jasmine.SpyObj<EmployeeApiService>;
  let facade: EmployeeDirectoryFacade;
  let asked: DirectoryQuery[];

  const answering = (...pages: EmployeePage[]) => {
    let call = 0;
    api.page.and.callFake((query: DirectoryQuery) => {
      asked.push(query);
      return of(pages[Math.min(call++, pages.length - 1)]);
    });
  };

  beforeEach(() => {
    asked = [];
    api = jasmine.createSpyObj<EmployeeApiService>('EmployeeApiService', ['page', 'filterOptions']);
    api.filterOptions.and.returnValue(of({ countries: [], departments: [], jobTitles: [], levels: [] }));
    TestBed.configureTestingModule({
      providers: [EmployeeDirectoryFacade, { provide: EmployeeApiService, useValue: api }],
    });
    facade = TestBed.inject(EmployeeDirectoryFacade);
  });

  it('asks for the first page without a cursor', () => {
    answering({ items: [person('Kapoor')], nextCursor: 'cursor-1', totalApprox: 10000 });

    facade.search({});

    expect(asked[0].cursor).toBeUndefined();
    expect(facade.total()).toBe(10000);
  });

  it('follows the cursor the server gave it rather than counting pages', () => {
    answering({ items: [person('Kapoor')], nextCursor: 'cursor-1', totalApprox: 10000 }, { items: [person('Rao')], nextCursor: 'cursor-2' });

    facade.search({});
    facade.nextPage();

    expect(asked[1].cursor).toBe('cursor-1');
    expect(facade.employees()[0].familyName).toBe('Rao');
  });

  it('keeps the total it was told on the first page', () => {
    answering({ items: [person('Kapoor')], nextCursor: 'cursor-1', totalApprox: 10000 }, { items: [person('Rao')] });

    facade.search({});
    facade.nextPage();

    // The second page carries no totalApprox; showing "of 0" would be worse than showing the
    // number we already know.
    expect(facade.total()).toBe(10000);
  });

  it('walks back through the cursors it has already used', () => {
    answering({ items: [person('Kapoor')], nextCursor: 'cursor-1', totalApprox: 10000 }, { items: [person('Rao')], nextCursor: 'cursor-2' });

    facade.search({});
    facade.nextPage();
    facade.previousPage();

    expect(asked[2].cursor).toBeUndefined();
    expect(facade.hasPreviousPage()).toBeFalse();
  });

  it('offers no next page when the server sent no cursor', () => {
    answering({ items: [person('Kapoor')], totalApprox: 1 });

    facade.search({});

    expect(facade.hasNextPage()).toBeFalse();
  });

  it('starts again from the beginning when the filters change', () => {
    answering({ items: [person('Kapoor')], nextCursor: 'cursor-1', totalApprox: 10000 }, { items: [person('Rao')], nextCursor: 'cursor-2' });

    facade.search({});
    facade.nextPage();
    facade.search({ country: 'DE' });

    // A cursor names a place in one ordered list; carrying it into a different list would resume
    // at a person who may not be in it.
    expect(asked[2].cursor).toBeUndefined();
    expect(asked[2].country).toBe('DE');
    expect(facade.hasPreviousPage()).toBeFalse();
  });

  it('says which slice of the org is on screen', () => {
    answering({ items: [person('Kapoor')], nextCursor: 'cursor-1', totalApprox: 10000 });

    facade.search({});

    expect(facade.firstOnPage()).toBe(1);
    expect(facade.lastOnPage()).toBe(1);
    expect(facade.pageNumber()).toBe(1);
  });

  it('says so when the directory could not be loaded', () => {
    api.page.and.returnValue(throwError(() => new Error('offline')));

    facade.search({});

    expect(facade.hasFailed()).toBeTrue();
    expect(facade.isLoading()).toBeFalse();
    expect(facade.employees()).toEqual([]);
  });
});
