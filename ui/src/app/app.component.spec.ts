import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
    }).compileComponents();
  });

  it('renders the application name in the toolbar', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const toolbar = fixture.nativeElement as HTMLElement;
    expect(toolbar.querySelector('mat-toolbar')?.textContent).toContain('Salary Management');
  });
});
