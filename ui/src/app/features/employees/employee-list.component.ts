import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { MoneyPipe } from '../../shared/money.pipe';
import { EmployeeDirectoryFacade } from './employee-directory.facade';

/**
 * The directory: ten thousand people, fifty at a time, filtered and searched on the server.
 *
 * <p>Next and previous rather than page numbers, because the pagination is keyset: there is a
 * page after this one, not a page seventeen. A numbered pager would be a promise the query cannot
 * keep.
 */
@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    DecimalPipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MoneyPipe,
    RouterLink,
  ],
  providers: [EmployeeDirectoryFacade],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss',
})
export class EmployeeListComponent implements OnInit {
  protected readonly directory = inject(EmployeeDirectoryFacade);
  protected readonly columns = ['employeeNumber', 'name', 'department', 'jobTitle', 'level', 'country', 'salary'];

  protected readonly q = signal('');
  protected readonly country = signal('');
  protected readonly department = signal('');
  protected readonly jobTitle = signal('');
  protected readonly level = signal('');

  ngOnInit(): void {
    this.directory.loadFilterOptions();
    this.directory.search({});
  }

  protected apply(): void {
    this.directory.search({
      q: this.q(),
      country: this.country(),
      department: this.department(),
      jobTitle: this.jobTitle(),
      level: this.level(),
    });
  }

  /** Applies a filter the way the form does, for a test that needs one set. */
  applyFilterForTest(query: { country?: string }): void {
    this.country.set(query.country ?? '');
    this.apply();
  }

  protected clear(): void {
    this.q.set('');
    this.country.set('');
    this.department.set('');
    this.jobTitle.set('');
    this.level.set('');
    this.apply();
  }
}
