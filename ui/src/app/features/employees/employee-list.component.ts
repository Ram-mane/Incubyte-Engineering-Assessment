import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { MoneyPipe } from '../../shared/money.pipe';
import { DEFAULT_PAGE_SIZE, EmployeeDirectoryFacade } from './employee-directory.facade';

/**
 * The directory: ten thousand people, fifty at a time.
 *
 * <p>The page is fetched from the server rather than filtered in the browser, so the table holds
 * one page whatever the size of the org. Filters, search and sorting are 2.6; what this proves is
 * the whole path - schema, query, port, endpoint, screen - with real data in it.
 */
@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [DecimalPipe, MatTableModule, MatPaginatorModule, MatProgressBarModule, MoneyPipe],
  providers: [EmployeeDirectoryFacade],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss',
})
export class EmployeeListComponent implements OnInit {
  protected readonly directory = inject(EmployeeDirectoryFacade);
  protected readonly pageSizes = [25, DEFAULT_PAGE_SIZE, 100];
  protected readonly columns = ['employeeNumber', 'name', 'department', 'jobTitle', 'level', 'country', 'salary'];

  ngOnInit(): void {
    this.directory.load(0, DEFAULT_PAGE_SIZE);
  }

  protected onPage(event: PageEvent): void {
    this.directory.load(event.pageIndex, event.pageSize);
  }
}
