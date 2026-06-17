import { applicantParams, driveCounts, driveStatusLabel, isDriveEditable, resultLabel } from './recruiter.mappers';
import { DriveResponse } from './recruiter.models';

function drive(status: DriveResponse['status']): DriveResponse {
  return { id: 'd', companyName: 'C', role: 'R', packageLpa: null, location: null, eligibility: { branches: null, minCgpa: null, backlogPolicy: null, batch: null }, openings: null, applyDeadline: null, status, rejectionReason: null };
}

describe('recruiter mappers', () => {
  it('labels statuses in plain language, never the enum', () => {
    expect(driveStatusLabel('PENDING_APPROVAL')).toBe('Pending approval');
    expect(driveStatusLabel('REJECTED_BY_ADMIN')).toBe('Changes requested');
    expect(resultLabel('PASS')).toBe('Passed');
  });

  it('marks a drive editable only when DRAFT or REJECTED_BY_ADMIN', () => {
    expect(isDriveEditable('DRAFT')).toBe(true);
    expect(isDriveEditable('REJECTED_BY_ADMIN')).toBe(true);
    expect(isDriveEditable('PUBLISHED')).toBe(false);
  });

  it('derives dashboard counts from the drive list', () => {
    const counts = driveCounts([drive('DRAFT'), drive('REJECTED_BY_ADMIN'), drive('PENDING_APPROVAL'), drive('PUBLISHED'), drive('ONGOING')]);
    expect(counts).toEqual({ drafts: 2, pending: 1, open: 2, total: 5 });
  });

  it('builds applicant params, omitting empty values and repeating status', () => {
    expect(applicantParams({ status: ['SHORTLISTED', 'APPLIED'], search: ' anj ', page: 0, pageSize: 20, sortDir: 'asc', sortBy: '' })).toEqual({
      status: ['SHORTLISTED', 'APPLIED'],
      search: 'anj',
      page: '0',
      pageSize: '20',
      sortDir: 'asc',
    });
    expect(applicantParams({})).toEqual({});
  });
});
