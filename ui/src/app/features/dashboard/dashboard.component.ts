import { DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { MoneyPipe } from '../../shared/money.pipe';
import { DashboardFacade } from './dashboard.facade';
import { BreakdownDimension, DistributionDimension, DistributionGroup } from './dashboard.model';

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
    TitleCasePipe,
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
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

  protected readonly dimensions: readonly BreakdownDimension[] = ['department', 'country', 'level'];
  /** Role first: it is the peer group an HR manager compares within. Country is not one. */
  protected readonly spreadDimensions: readonly { value: DistributionDimension; label: string }[] = [
    { value: 'jobTitle', label: 'Role' },
    { value: 'department', label: 'Department' },
  ];

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

  /** The largest group's spend, so every bar can be drawn relative to it. */
  protected widthOf(spend: string): string {
    const groups = this.dashboard.breakdown()?.groups ?? [];
    const largest = Number(groups[0]?.totalSpend.amount ?? 0);
    if (!largest) {
      return '0%';
    }
    return `${Math.round((Number(spend) / largest) * 10000) / 100}%`;
  }

  /** Where a group's interquartile box starts, as a share of its own lowest-to-highest range. */
  protected iqrLeft(group: DistributionGroup): string {
    return `${share(group, group.p25)}%`;
  }

  protected iqrWidth(group: DistributionGroup): string {
    return `${round(share(group, group.p75) - share(group, group.p25))}%`;
  }

  protected clear(): void {
    this.country.set('');
    this.department.set('');
    this.jobTitle.set('');
    this.level.set('');
    this.apply();
  }
}

/** How far an amount sits along a group's own range, 0 at the lowest and 100 at the highest. */
function share(group: DistributionGroup, amount: { amount: string }): number {
  const lowest = Number(group.lowest.amount);
  const highest = Number(group.highest.amount);
  const span = highest - lowest;
  if (span <= 0) {
    // Everybody in the group is on the same salary: there is no spread to draw.
    return 0;
  }
  return round(((Number(amount.amount) - lowest) / span) * 100);
}

function round(percentage: number): number {
  return Math.round(percentage * 100) / 100;
}
