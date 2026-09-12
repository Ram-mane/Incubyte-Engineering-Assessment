import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable } from 'rxjs';

import { MoneyPipe } from '../../shared/money.pipe';
import { BandView, ChangeSalaryRequest, Employee } from '../employees/employee.model';

/** Somebody else changed this pay first: the screen behind the dialog now holds the truth. */
const CONFLICT = 409;

/** The session ended. The interceptor is already navigating; this dialog just gets out of the way. */
const UNAUTHORISED = 401;

/** The reasons the domain accepts. Four, because the seed and the log only ever produce four. */
export const CHANGE_REASONS = ['MERIT', 'PROMOTION', 'MARKET_ADJUSTMENT', 'CORRECTION'] as const;

/**
 * What the dialog needs, and what it does with it.
 *
 * <p>`save` rather than a result the caller acts on: a dialog that closes and hands back a request
 * cannot show what the server said about it. The manager would lose both the reason and everything
 * they typed.
 */
export interface ChangeSalaryDialogData {
  readonly employee: Employee;
  readonly band: BandView | null;
  readonly save: (request: ChangeSalaryRequest) => Observable<unknown>;
}

@Component({
  selector: 'app-change-salary-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MoneyPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-salary-dialog.component.html',
  styleUrl: './change-salary-dialog.component.scss',
})
export class ChangeSalaryDialogComponent {
  private readonly data = inject<ChangeSalaryDialogData>(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialogRef<ChangeSalaryDialogComponent>);

  protected readonly employee = this.data.employee;
  /** A signal so the template can bind it once with `as` rather than optional-chaining it four times. */
  protected readonly bandRange = signal(this.data.band);
  protected readonly reasons = CHANGE_REASONS;

  protected readonly amount = signal(this.employee.salary.amount);
  protected readonly reason = signal<string>('MERIT');
  protected readonly note = signal('');
  protected readonly saving = signal(false);
  /** Starts empty on every open, because the dialog is built fresh each time it is opened. */
  protected readonly refusal = signal<string | null>(null);
  private readonly touched = signal(false);

  /**
   * A salary is a positive decimal, checked as text.
   *
   * <p>Deliberately not `type="number"`. Angular binds that through `NumberValueAccessor`, which
   * runs the value through `parseFloat` - money through a double, which this codebase forbids on
   * the wire and has no business doing in a form either. It also broke typing: at the keystroke
   * `.` the browser reports an empty value for `1500000.`, the model went to '' and the binding
   * wrote that back, so the field cleared itself mid-entry. Measured, not theorised.
   *
   * <p>`inputmode="decimal"` still gives a phone the number pad, which was the only real benefit.
   */
  protected readonly amountProblem = computed(() => {
    const typed = this.amount().trim();
    if (!typed) {
      return 'Enter the new annual salary.';
    }
    if (!/^\d{1,15}(\.\d{1,2})?$/.test(typed)) {
      return 'Enter an amount in digits, with up to two decimal places.';
    }
    if (Number(typed) <= 0) {
      return 'A salary must be greater than zero.';
    }
    return null;
  });

  /**
   * The local check is shown only once the manager has typed something, so opening a dialog does
   * not greet them with an error about a field they have not touched.
   */
  protected readonly showProblem = computed(() => (this.touched() ? this.amountProblem() : null));

  /** What to show under the field: the server's refusal if it gave one, else the local check. */
  protected readonly problem = computed(() => this.refusal() ?? this.showProblem());

  protected readonly cannotSave = computed(() => this.amountProblem() !== null || this.saving());

  /**
   * A number input hands back a number, or null when what was typed is not one. Kept as the string
   * the API expects - money crosses the wire as text, and parsing it twice is how a figure loses
   * its last digits.
   */
  protected setAmount(typed: string | null | undefined): void {
    this.amount.set(typed ?? '');
    this.touched.set(true);
    // A stale refusal under a field the manager has just corrected is worse than no message: it
    // describes an amount that is no longer in the box.
    this.refusal.set(null);
  }

  protected submit(): void {
    if (this.cannotSave()) {
      return;
    }
    this.saving.set(true);
    this.refusal.set(null);
    this.data
      .save({
        amount: this.amount().trim(),
        // Always the employee's own currency: this system stores local currency and converts
        // nothing, so offering a currency picker would offer a mistake.
        currency: this.employee.salary.currency,
        reason: this.reason(),
        note: this.note(),
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.dialog.close(true);
        },
        error: (failure: { status?: number; error?: { detail?: string } }) => {
          this.saving.set(false);
          if (failure?.status === UNAUTHORISED) {
            // The interceptor is already sending them to sign in. "The change was refused" over a
            // sign-in screen would be a message about the wrong thing.
            this.dialog.close();
            return;
          }
          if (failure?.status === CONFLICT) {
            // Somebody else moved this pay. The facade has re-read it, and the figure they need to
            // decide against is on the screen behind this dialog - so get out of its way.
            this.dialog.close();
            return;
          }
          // Stay open. The manager keeps what they typed and can see why it was refused.
          this.refusal.set(failure?.error?.detail ?? 'The change was refused.');
        },
        complete: () => this.saving.set(false),
      });
  }

  protected cancel(): void {
    this.dialog.close();
  }
}
