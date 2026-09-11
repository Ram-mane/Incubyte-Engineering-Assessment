import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../core/auth/session.service';
import { MoneyPipe } from '../../shared/money.pipe';
import { ChangeSalaryRequest } from '../employees/employee.model';
import { ChangeSalaryDialogComponent } from './change-salary-dialog.component';
import { EmployeeDetailFacade } from './employee-detail.facade';

/**
 * One employee, their current pay, and every change anyone has ever made to it.
 *
 * <p>The log is the point of the screen. A salary on its own is a number; a salary with the
 * history of how it got there is something an HR manager can answer a question about.
 */
@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatProgressBarModule,
    MatTableModule,
    MoneyPipe,
    RouterLink,
  ],
  providers: [EmployeeDetailFacade],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-detail.component.html',
  styleUrl: './employee-detail.component.scss',
})
export class EmployeeDetailComponent implements OnInit {
  readonly id = input.required<string>();

  protected readonly detail = inject(EmployeeDetailFacade);
  protected readonly session = inject(SessionService);
  private readonly dialogs = inject(MatDialog);

  protected readonly columns = ['changedAt', 'previousAmount', 'newAmount', 'reason', 'note'];

  ngOnInit(): void {
    this.detail.load(this.id());
  }

  protected changePay(): void {
    const employee = this.detail.employee();
    if (!employee) {
      return;
    }
    this.dialogs
      .open(ChangeSalaryDialogComponent, { data: employee, width: '32rem' })
      .afterClosed()
      .subscribe((change?: ChangeSalaryRequest) => {
        if (change) {
          this.detail.changeSalary(this.id(), change, () => undefined);
        }
      });
  }
}
