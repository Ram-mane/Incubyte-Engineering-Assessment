import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthApiService } from '../../core/auth/auth-api.service';
import { SessionService } from '../../core/auth/session.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthApiService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly failed = signal(false);
  /** Set when the interceptor sent us here because a token ran out mid-session. */
  protected readonly expired = signal(
    inject(ActivatedRoute).snapshot.queryParamMap.get('expired') === 'true',
  );

  protected submit(): void {
    this.busy.set(true);
    this.failed.set(false);
    this.expired.set(false);
    this.auth.login(this.email(), this.password()).subscribe({
      next: (session) => {
        this.session.start(session);
        this.busy.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        // One message for a wrong password and an unknown address, matching the API: telling
        // them apart tells someone which addresses are real.
        this.failed.set(true);
        this.busy.set(false);
      },
    });
  }
}
