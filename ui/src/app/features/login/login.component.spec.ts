import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthApiService } from '../../core/auth/auth-api.service';
import { SessionService } from '../../core/auth/session.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let element: HTMLElement;
  let auth: jasmine.SpyObj<AuthApiService>;
  let router: jasmine.SpyObj<Router>;

  const render = async (queryParams: Record<string, string> = {}) => {
    auth = jasmine.createSpyObj<AuthApiService>('AuthApiService', ['login']);
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: AuthApiService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(LoginComponent);
    element = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  };

  it('asks for an email and a password', async () => {
    await render();

    const labels = Array.from(element.querySelectorAll('mat-label')).map((l) => l.textContent?.trim());
    expect(element.querySelector('h1')?.textContent).toContain('Sign in');
    expect(labels).toContain('Email');
    expect(labels).toContain('Password');
  });

  it('says an expired session expired, rather than showing nothing', async () => {
    await render({ expired: 'true' });

    // The alternative is a blank screen that reads as "there is nothing here", which is a
    // statement about the data rather than about the session.
    expect(element.querySelector('[role="status"]')?.textContent).toContain('session expired');
  });

  it('starts a session and goes to the directory when the credentials are good', async () => {
    await render();
    auth.login.and.returnValue(
      of({ token: 'a-token', expiresAt: '2099-01-01T00:00:00Z', role: 'HR_MANAGER' as const }),
    );

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(TestBed.inject(SessionService).isSignedIn()).toBeTrue();
    expect(router.navigate).toHaveBeenCalledWith(['/employees']);
  });

  it('gives one message for a wrong password and an unknown address alike', async () => {
    await render();
    auth.login.and.returnValue(throwError(() => new Error('401')));

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('do not match a user');
  });
});
