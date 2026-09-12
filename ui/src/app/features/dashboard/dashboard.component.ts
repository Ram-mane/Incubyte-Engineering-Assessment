import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { MoneyPipe } from '../../shared/money.pipe';
import { DashboardFacade } from './dashboard.facade';

/**
 * The four KPI cards and the filters that move them.
 *
 * <p>Every filter change is one request returning all four figures, so the cards always agree with
 * each other and the screen never stutters through four separate answers.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatSelectModule,
    MoneyPipe,
  ],
  providers: [DashboardFacade],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  protected readonly dashboard = inject(DashboardFacade);

  protected readonly country = signal('');
  protected readonly department = signal('');
  protected readonly jobTitle = signal('');
  protected readonly level = signal('');

  ngOnInit(): void {
    this.dashboard.loadFilterOptions();
    this.dashboard.load({});
  }

  protected apply(): void {
    this.dashboard.load({
      country: this.country(),
      department: this.department(),
      jobTitle: this.jobTitle(),
      level: this.level(),
    });
  }

  protected clear(): void {
    this.country.set('');
    this.department.set('');
    this.jobTitle.set('');
    this.level.set('');
    this.apply();
  }
}
