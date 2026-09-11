/** The directory as the API returns it. Amounts are strings: see `MoneyPipe`. */
export interface Money {
  readonly amount: string;
  readonly currency: string;
}

export interface Employee {
  readonly id: string;
  readonly employeeNumber: string;
  readonly givenName: string;
  readonly familyName: string;
  readonly email: string;
  readonly country: string;
  readonly department: string;
  readonly jobTitle: string;
  readonly level: string;
  readonly salary: Money;
}

export interface EmployeePage {
  readonly items: readonly Employee[];
  readonly page: number;
  readonly size: number;
  readonly total: number;
}
