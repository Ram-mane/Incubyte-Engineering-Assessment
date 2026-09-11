import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { SessionService } from './session.service';

/**
 * Attaches the token, and turns a 401 into an explanation.
 *
 * <p>An expired session that manifests as an empty table is the worst version of this: the screen
 * says there are no employees, which is a statement about the company rather than about the
 * request. So a 401 ends the session and sends the person to sign in again, with a reason.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(SessionService);
  const router = inject(Router);
  const token = session.session()?.token;

  const authorised = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;

  return next(authorised).pipe(
    catchError((failure: HttpErrorResponse) => {
      const loggingIn = request.url.includes('/auth/login');
      if (failure.status === 401 && !loggingIn) {
        session.end();
        router.navigate(['/login'], { queryParams: { expired: true } });
      }
      return throwError(() => failure);
    }),
  );
};
