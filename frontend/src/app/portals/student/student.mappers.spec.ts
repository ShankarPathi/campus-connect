import {
  applicationSteps,
  applicationStatusLabel,
  canWithdraw,
  driveSections,
  eligibilityChecks,
  firstFailedReason,
} from './student.mappers';
import { StudentDrive } from './student.models';

function drive(group: StudentDrive['group'], failedCriteria: string[] | null = null): StudentDrive {
  return { id: 'd', companyName: 'C', role: 'R', packageLpa: 7, location: 'X', applyDeadline: null, status: 'PUBLISHED', group, failedCriteria };
}

describe('driveSections', () => {
  it('counts each group in IA order', () => {
    const sections = driveSections([drive('ELIGIBLE'), drive('ELIGIBLE'), drive('APPLIED'), drive('NOT_ELIGIBLE'), drive('CLOSED')]);
    expect(sections.map((s) => s.key)).toEqual(['ELIGIBLE', 'APPLIED', 'NOT_ELIGIBLE', 'CLOSED']);
    expect(sections.map((s) => s.count)).toEqual([2, 1, 1, 1]);
  });
});

describe('eligibilityChecks', () => {
  it('maps failedCriteria to danger rows for NOT_ELIGIBLE', () => {
    const checks = eligibilityChecks(drive('NOT_ELIGIBLE', ['CGPA 6.4 — needs 7.0', 'Branch not eligible']));
    expect(checks.length).toBe(2);
    expect(checks[0]).toEqual({ label: 'CGPA 6.4 — needs 7.0', passed: false, detail: 'CGPA 6.4 — needs 7.0' });
  });
  it('shows a single success row for ELIGIBLE', () => {
    const checks = eligibilityChecks(drive('ELIGIBLE'));
    expect(checks.length).toBe(1);
    expect(checks[0].passed).toBe(true);
  });
  it('returns no rows for APPLIED / CLOSED (the modal renders a status line instead)', () => {
    expect(eligibilityChecks(drive('APPLIED'))).toEqual([]);
    expect(eligibilityChecks(drive('CLOSED'))).toEqual([]);
  });
  it('firstFailedReason returns the first reason only for NOT_ELIGIBLE', () => {
    expect(firstFailedReason(drive('NOT_ELIGIBLE', ['a', 'b']))).toBe('a');
    expect(firstFailedReason(drive('ELIGIBLE'))).toBeNull();
  });
});

describe('applicationSteps', () => {
  it('marks done/current/upcoming from the lifecycle', () => {
    const steps = applicationSteps('SHORTLISTED');
    expect(steps.map((s) => s.state)).toEqual(['done', 'done', 'current', 'upcoming', 'upcoming', 'upcoming']);
  });
  it('stops the track for terminal REJECTED/WITHDRAWN (no current node)', () => {
    expect(applicationSteps('REJECTED').some((s) => s.state === 'current')).toBe(false);
    expect(applicationSteps('WITHDRAWN').every((s) => s.state === 'upcoming')).toBe(true);
  });
  it('completes the final node on OFFER_ACCEPTED', () => {
    const steps = applicationSteps('OFFER_ACCEPTED');
    expect(steps[5].state).toBe('done');
  });
  it('settles (no fake "current") the Offer node for OFFER_DECLINED / OFFER_EXPIRED', () => {
    for (const s of ['OFFER_DECLINED', 'OFFER_EXPIRED'] as const) {
      const steps = applicationSteps(s);
      expect(steps.some((x) => x.state === 'current')).toBe(false);
      expect(steps[5].state).toBe('done');
    }
  });
});

describe('labels + canWithdraw', () => {
  it('renders plain language, never the enum', () => {
    expect(applicationStatusLabel('OFFER_RELEASED')).toBe('Offer received');
    expect(applicationStatusLabel('UNDER_REVIEW')).toBe('Under review');
  });
  it('allows withdraw only pre-shortlist', () => {
    expect(canWithdraw('APPLIED')).toBe(true);
    expect(canWithdraw('UNDER_REVIEW')).toBe(true);
    expect(canWithdraw('SHORTLISTED')).toBe(false);
  });
});
