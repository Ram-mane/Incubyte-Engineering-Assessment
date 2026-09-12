import { Pipe, PipeTransform } from '@angular/core';

/** Segments that are initialisms, and stay shouted when everything around them stops. */
const ACRONYMS = new Set(['HR']);

/**
 * A wire constant, written the way a person reads it: MARKET_ADJUSTMENT becomes "Market
 * adjustment".
 *
 * <p>Display only. The value sent to the server, stored in `salary_revision.reason` and carried
 * in the token is the constant itself - this changes what the screen says, never what is
 * recorded. A value this pipe has not been taught still comes out as prose, so a reason added
 * on the server does not reach the screen shouting.
 */
@Pipe({ name: 'enumLabel', standalone: true })
export class EnumLabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value) {
      return '';
    }
    const words = value
      .split('_')
      .map((word) => (ACRONYMS.has(word.toUpperCase()) ? word.toUpperCase() : word.toLowerCase()));
    const [first, ...rest] = words;
    const opening = ACRONYMS.has(first) ? first : first.charAt(0).toUpperCase() + first.slice(1);
    return [opening, ...rest].join(' ');
  }
}
