import { statusToVariant } from './status.util';

describe('statusToVariant', () => {
  it('maps success statuses', () => {
    for (const s of ['ELIGIBLE', 'SELECTED', 'PLACED', 'OFFICIALLY_PLACED', 'PASS']) {
      expect(statusToVariant(s)).toBe('success');
    }
  });

  it('maps warning statuses', () => {
    for (const s of ['UNDER_REVIEW', 'PENDING', 'PENDING_APPROVAL']) {
      expect(statusToVariant(s)).toBe('warning');
    }
  });

  it('maps danger statuses', () => {
    for (const s of ['NOT_ELIGIBLE', 'REJECTED', 'OFFER_EXPIRED', 'FAILED']) {
      expect(statusToVariant(s)).toBe('danger');
    }
  });

  it('maps info statuses (incl. positive/in-progress milestones, not amber)', () => {
    for (const s of ['APPLIED', 'SHORTLISTED', 'INTERVIEWING', 'OFFER_RELEASED', 'ONGOING']) {
      expect(statusToVariant(s)).toBe('info');
    }
  });

  it('maps end-of-lifecycle / draft drive states to neutral by decision', () => {
    for (const s of ['CLOSED', 'COMPLETED', 'DRAFT']) {
      expect(statusToVariant(s)).toBe('neutral');
    }
  });

  it('is case- and separator-insensitive', () => {
    expect(statusToVariant('under review')).toBe('warning');
    expect(statusToVariant('not-eligible')).toBe('danger');
    expect(statusToVariant('  Selected  ')).toBe('success');
  });

  it('falls back to neutral for unknown / empty', () => {
    expect(statusToVariant('SOMETHING_NEW')).toBe('neutral');
    expect(statusToVariant('')).toBe('neutral');
    expect(statusToVariant(null)).toBe('neutral');
    expect(statusToVariant(undefined)).toBe('neutral');
  });
});
