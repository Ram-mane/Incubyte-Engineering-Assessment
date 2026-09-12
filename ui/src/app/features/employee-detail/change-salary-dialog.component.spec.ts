import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, of, throwError } from 'rxjs';

import { BandView, ChangeSalaryRequest, Employee } from '../employees/employee.model';
import { ChangeSalaryDialogComponent, ChangeSalaryDialogData } from './change-salary-dialog.component';

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

const band: BandView = {
  defined: true,
  min: { amount: '1100000.00', currency: 'INR' },
  mid: { amount: '1300000.00', currency: 'INR' },
  max: { amount: '1560000.00', currency: 'INR' },
  compaRatio: '1.0615',
  position: 'WITHIN',
};

describe('ChangeSalaryDialogComponent', () => {
  let fixture: ComponentFixture<ChangeSalaryDialogComponent>;
  let element: HTMLElement;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ChangeSalaryDialogComponent>>;
  let saved: ChangeSalaryRequest[];

  const render = async (save: (request: ChangeSalaryRequest) => Observable<unknown>, showing = band) => {
    saved = [];
    dialogRef = jasmine.createSpyObj<MatDialogRef<ChangeSalaryDialogComponent>>('MatDialogRef', ['close']);
    const data: ChangeSalaryDialogData = {
      employee: alice,
      band: showing,
      save: (request) => {
        saved.push(request);
        return save(request);
      },
    };

    await TestBed.configureTestingModule({
      imports: [ChangeSalaryDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChangeSalaryDialogComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  };

  const amountInput = () => element.querySelector<HTMLInputElement>('input[name="amount"]')!;
  const saveButton = () => element.querySelector<HTMLButtonElement>('button[name="save"]')!;
  const typeAmount = (value: string) => {
    const input = amountInput();
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  it('keeps every character of a decimal amount as it is typed', async () => {
    await render(() => of({}));

    // type="number" binds through parseFloat: at the keystroke "." the browser reports an empty
    // value for "1500000.", the model went to '' and the binding wrote that back, clearing the
    // field mid-entry. Reproduced in a real browser before this was changed back to text.
    for (const partial of ['1', '15', '150', '1500', '15000', '150000', '1500000', '1500000.', '1500000.5']) {
      typeAmount(partial);
      expect(amountInput().value).toBe(partial);
    }

    saveButton().click();
    fixture.detectChanges();

    // And the string reaches the request unrounded - money never goes through a double.
    expect(saved[0].amount).toBe('1500000.5');
  });

  it('offers a phone the number pad without making the field a number', async () => {
    await render(() => of({}));

    expect(amountInput().type).toBe('text');
    expect(amountInput().inputMode).toBe('decimal');
  });

  it('refuses an amount with more precision than money has', async () => {
    await render(() => of({}));

    typeAmount('1500000.555');

    expect(saveButton().disabled).toBeTrue();
  });

  it('will not save an empty amount', async () => {
    await render(() => of({}));

    typeAmount('');

    expect(saveButton().disabled).toBeTrue();
  });

  it('will not save something that is not a number', async () => {
    await render(() => of({}));

    typeAmount('abc');

    // Reachable now that the field is text: a number input silently swallowed this.
    expect(saveButton().disabled).toBeTrue();
    expect(element.querySelector('#amount-problem')?.textContent).toContain('digits');
  });

  it('will not save a negative salary, or zero', async () => {
    await render(() => of({}));

    typeAmount('-1');
    expect(saveButton().disabled).toBeTrue();

    typeAmount('0');
    expect(saveButton().disabled).toBeTrue();
  });

  it('saves a positive amount and closes', async () => {
    await render(() => of({}));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();

    expect(saved[0].amount).toBe('1500000');
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('stays open when the server refuses, and keeps what was typed', async () => {
    await render(() => throwError(() => ({ status: 422, error: { detail: 'Salary must be positive.' } })));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();

    // Closing on a rejection throws away the manager's input and hides the reason. The whole
    // point of a refusal is that they get to correct it.
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(amountInput().value).toBe('1500000');
    // Everything they entered, not only the amount: losing the reason or the note would make them
    // redo work the refusal had nothing to do with.
    expect(element.querySelector<HTMLElement>('mat-select')?.textContent).toContain('MERIT');
  });

  it("shows the server's reason against the field it is about", async () => {
    await render(() => throwError(() => ({ status: 422, error: { detail: 'Salary must be positive.' } })));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();

    const refusal = element.querySelector('#amount-problem');
    expect(refusal?.textContent).toContain('Salary must be positive');
    expect(refusal?.getAttribute('role')).toBe('alert');
    // Bound to the field it is about, so a screen reader reaches it from the input.
    expect(amountInput().getAttribute('aria-describedby')).toBe('amount-problem');
  });

  it('starts with no error showing', async () => {
    await render(() => of({}));

    expect(element.querySelector('#amount-problem')).toBeNull();
  });

  it('shows the band for context and never blocks on it', async () => {
    await render(() => of({}));

    const context = element.querySelector('.dialog__band');
    expect(context?.textContent).toContain('1,100,000');
    expect(context?.textContent).toContain('1,560,000');

    // Six times the maximum. Enabled is not enough to prove nothing enforces: the click has to
    // happen and the request has to be issued, or a guard added inside submit() would pass this.
    typeAmount('9000000');
    expect(saveButton().disabled).toBeFalse();
    saveButton().click();
    fixture.detectChanges();

    expect(saved.length).toBe(1);
    expect(saved[0].amount).toBe('9000000');
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('will not send the same change twice when the button is double-clicked', async () => {
    await render(() => new Observable(() => undefined));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();
    saveButton().click();
    fixture.detectChanges();

    // The first click leaves it saving; the second must find the button disabled.
    expect(saved.length).toBe(1);
  });

  it('closes on a conflict so the figure now on record is visible behind it', async () => {
    await render(() => throwError(() => ({ status: 409, error: { detail: 'Reload employee and decide again.' } })));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();

    // The message tells them to reload and decide again. Staying open would put the dialog over
    // the very figure they are being told to look at.
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('gets out of the way when the session has expired', async () => {
    await render(() => throwError(() => ({ status: 401 })));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();

    // The interceptor is already navigating to sign-in. "The change was refused" over a sign-in
    // screen would be a message about the wrong thing.
    expect(dialogRef.close).toHaveBeenCalled();
    expect(element.querySelector('#amount-problem')).toBeNull();
  });

  it('drops a stale refusal as soon as the amount is corrected', async () => {
    await render(() => throwError(() => ({ status: 422, error: { detail: 'Salary must be positive.' } })));

    typeAmount('1500000');
    saveButton().click();
    fixture.detectChanges();
    expect(element.querySelector('#amount-problem')).not.toBeNull();

    typeAmount('1600000');

    // The message described an amount that is no longer in the box.
    expect(element.querySelector('#amount-problem')).toBeNull();
  });

  it('says when there is no band rather than leaving a blank space', async () => {
    await render(() => of({}), { defined: false });

    expect(element.querySelector('.dialog__band')?.textContent).toContain('No band defined');
  });
});
