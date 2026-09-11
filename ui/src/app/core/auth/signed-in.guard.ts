import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionService } from './session.service';

/** Nothing but the login screen renders without a session. */
export const signedInGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  return session.isSignedIn() ? true : router.createUrlTree(['/login']);
};
