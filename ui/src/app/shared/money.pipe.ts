import { Pipe, PipeTransform } from '@angular/core';

import { Money } from '../features/employees/employee.model';

/**
 * Money, formatted in its own currency and never converted.
 *
 * <p>The amount arrives as a string because a JSON number is a double in a browser and a large
 * salary loses its last digits. It is parsed only here, for display, and nothing in this
 * application does arithmetic on it.
 */
@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(money: Money | null | undefined): string {
    if (!money) {
      return '';
    }
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: money.currency,
      maximumFractionDigits: 0,
    }).format(Number(money.amount));
  }
}
