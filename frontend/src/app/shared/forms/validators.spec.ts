import { FormControl } from '@angular/forms';
import { futureDateTime, positiveNumber } from './validators';

describe('form validators (Story 9.7)', () => {
  describe('futureDateTime', () => {
    it('passes for empty (required owns emptiness)', () => {
      expect(futureDateTime(new FormControl(''))).toBeNull();
    });
    it('flags an unparseable value', () => {
      expect(futureDateTime(new FormControl('not-a-date'))).toEqual({ datetime: true });
    });
    it('flags a past datetime', () => {
      expect(futureDateTime(new FormControl('2000-01-01T00:00:00Z'))).toEqual({ future: true });
    });
    it('passes for a future datetime', () => {
      expect(futureDateTime(new FormControl('2999-01-01T00:00:00Z'))).toBeNull();
    });
  });

  describe('positiveNumber', () => {
    it('passes for empty', () => {
      expect(positiveNumber(new FormControl(''))).toBeNull();
    });
    it('flags a non-number', () => {
      expect(positiveNumber(new FormControl('abc'))).toEqual({ number: true });
    });
    it('flags zero and negatives', () => {
      expect(positiveNumber(new FormControl('0'))).toEqual({ positive: true });
      expect(positiveNumber(new FormControl('-5'))).toEqual({ positive: true });
    });
    it('passes for a positive number', () => {
      expect(positiveNumber(new FormControl('12.5'))).toBeNull();
    });
  });
});
