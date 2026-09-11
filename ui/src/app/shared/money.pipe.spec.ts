import { MoneyPipe } from './money.pipe';

describe('MoneyPipe', () => {
  const pipe = new MoneyPipe();

  it('formats an amount in its own currency', () => {
    const formatted = pipe.transform({ amount: '1200000.00', currency: 'INR' });

    expect(formatted).toContain('1,200,000');
    expect(formatted).toMatch(/₹|INR/);
  });

  it('keeps every digit of a large salary', () => {
    // The amount is a string precisely so that nothing rounds it on the way to the screen.
    expect(pipe.transform({ amount: '9876543.00', currency: 'USD' })).toContain('9,876,543');
  });

  it('does not convert between currencies', () => {
    const euros = pipe.transform({ amount: '85000.00', currency: 'EUR' });

    expect(euros).toMatch(/€|EUR/);
    expect(euros).toContain('85,000');
  });

  it('shows nothing rather than NaN when there is no salary', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
