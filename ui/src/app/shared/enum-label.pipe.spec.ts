import { EnumLabelPipe } from './enum-label.pipe';

describe('EnumLabelPipe', () => {
  const pipe = new EnumLabelPipe();

  it('writes a wire constant the way a person reads it', () => {
    expect(pipe.transform('MERIT')).toBe('Merit');
    expect(pipe.transform('MARKET_ADJUSTMENT')).toBe('Market adjustment');
  });

  it('keeps an acronym an acronym', () => {
    // Sentence case applied naively makes the HR team "Hr", which is worse than shouting.
    expect(pipe.transform('HR_MANAGER')).toBe('HR manager');
    expect(pipe.transform('HR_ANALYST')).toBe('HR analyst');
  });

  it('still reads as English for a value it has never seen', () => {
    // A new reason added on the server must not reach the screen as SHOUTED_UNDERSCORES just
    // because this pipe was not updated with it.
    expect(pipe.transform('RETENTION_AWARD')).toBe('Retention award');
  });

  it('leaves text that is already prose alone', () => {
    expect(pipe.transform('Merit')).toBe('Merit');
    expect(pipe.transform(null)).toBe('');
  });
});
