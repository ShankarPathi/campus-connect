import { accountStatusLabel, driveStatusLabel, placementStatusLabel, profileStatusLabel } from './admin.mappers';

describe('admin mappers', () => {
  it('labels every status in plain language, never the enum', () => {
    expect(profileStatusLabel('PENDING_APPROVAL')).toBe('Pending approval');
    expect(profileStatusLabel('REJECTED')).toBe('Changes requested');
    expect(accountStatusLabel('PENDING_VERIFICATION')).toBe('Awaiting verification');
    expect(driveStatusLabel('REJECTED_BY_ADMIN')).toBe('Changes requested');
    expect(placementStatusLabel('OFFICIALLY_PLACED')).toBe('Officially placed');
  });
});
