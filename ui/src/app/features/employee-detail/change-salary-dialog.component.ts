import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { ChangeSalaryRequest, Employee } from '../employees/employee.model';

/** The reasons the domain accepts. Four, because the seed and the log only ever produce four. */
export const CHANGE_REASONS = ['MERIT', 'PROMOTION', 'MARKET_ADJUSTMENT', 'CORRECTION'] as const;

@Component({
  selector: 'app-change-salary-dialog',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './change-salary-dialog.component.html',
})
export class ChangeSalaryDialogComponent {
  protected readonly employee = inject<Employee>(MAT_DIALOG_DATA);
  private readonly dialog = inject(MatDialogRef<ChangeSalaryDialogComponent, ChangeSalaryRequest>);

  protected readonly reasons = CHANGE_REASONS;
  protected readonly amount = signal(this.employee.salary.amount);
  protected readonly reason = signal<string>('MERIT');
  protected readonly note = signal('');

  protected submit(): void {
    this.dialog.close({
      amount: this.amount(),
      // Always the employee's own currency: this system stores local currency and converts
      // nothing, so offering a currency picker would offer a mistake.
      currency: this.employee.salary.currency,
      reason: this.reason(),
      note: this.note(),
    });
  }

  protected cancel(): void {
    this.dialog.close();
  }
}
