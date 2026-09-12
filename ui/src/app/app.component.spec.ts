import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AppComponent } from './app.component';
import { SessionService } from './core/auth/session.service';

describe('AppComponent', () => {
  let session: SessionService;

  const render = () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    }).compileComponents();
    session = TestBed.inject(SessionService);
  });

  afterEach(() => session.end());

  it('renders the application name in the toolbar', () => {
    expect(render().querySelector('mat-toolbar')?.textContent).toContain('Salary Management');
  });

  it('offers a signed-in person both the dashboard and the directory', () => {
    session.start({ token: 'a-token', expiresAt: '2999-01-01T00:00:00Z', role: 'HR_MANAGER' });

    const links = Array.from(
      render().querySelectorAll('[aria-label="Main"] a'),
    ).map((a) => a.textContent?.trim());

    expect(links).toEqual(['Dashboard', 'Directory']);
  });

  it('offers nothing to navigate to before anyone signs in', () => {
    // A nav bar on the sign-in screen advertises destinations the guard will refuse.
    expect(render().querySelector('[aria-label="Main"]')).toBeNull();
  });
});
