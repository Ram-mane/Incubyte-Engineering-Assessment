import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { SessionService } from './session.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: SessionService;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    router = jasmine.createSpyObj<Router>('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(SessionService);
    session.start({ token: 'a-token', expiresAt: '2999-01-01T00:00:00Z', role: 'HR_MANAGER' });
  });

  afterEach(() => {
    backend.verify();
    session.end();
  });

  it('sends the token with a dashboard request', () => {
    http.get('/api/v1/dashboard/summary').subscribe({ error: () => undefined });

    const request = backend.expectOne('/api/v1/dashboard/summary');

    expect(request.request.headers.get('Authorization')).toBe('Bearer a-token');
    request.flush({});
  });

  it('turns an expired session on the dashboard into an explanation, not an empty dashboard', () => {
    http.get('/api/v1/dashboard/summary').subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/dashboard/summary')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    // Four cards reading zero is a statement about the company. "Your session expired" is a
    // statement about the request, which is the true one.
    expect(session.isSignedIn()).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: true } });
  });

  it('leaves a rejected sign-in on the sign-in screen', () => {
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/auth/login')
      .flush({}, { status: 401, statusText: 'Unauthorized' });

    // Redirecting to login from login would replace "those credentials are wrong" with nothing.
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
