import { Injectable, computed, signal } from '@angular/core';

export interface Session {
  readonly token: string;
  readonly expiresAt: string;
  readonly role: 'HR_MANAGER' | 'HR_ANALYST';
}

const STORAGE_KEY = 'compensationiq.session';

/**
 * Who is signed in, for as long as their token is good for.
 *
 * <p>Kept in sessionStorage rather than localStorage: a token that authorises payroll changes
 * should not outlive the tab it was issued to. There is no refresh and no server-side revocation,
 * which is exactly why it is short-lived and why expiry is handled visibly rather than by letting
 * requests start failing.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly current = signal<Session | null>(this.restore());

  readonly session = this.current.asReadonly();
  readonly isSignedIn = computed(() => this.current() !== null);
  readonly canChangePay = computed(() => this.current()?.role === 'HR_MANAGER');
  readonly role = computed(() => this.current()?.role ?? null);

  start(session: Session): void {
    this.current.set(session);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // A browser refusing storage is a browser that signs in again on reload, not a broken app.
    }
  }

  end(): void {
    this.current.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing to clean up if it was never stored.
    }
  }

  private restore(): Session | null {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (!stored) {
        return null;
      }
      const session = JSON.parse(stored) as Session;
      // A token the browser kept past its expiry is not a session, and pretending otherwise
      // means the first request of the next visit fails for no reason the user can see.
      return new Date(session.expiresAt) > new Date() ? session : null;
    } catch {
      return null;
    }
  }
}
