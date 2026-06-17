import { TestBed } from '@angular/core/testing';
import { EligibilityPanel } from './eligibility-panel';
import { EligibilityCheck } from '../ui.models';

describe('EligibilityPanel', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [EligibilityPanel] }).compileComponents();
  });

  it('renders a row per check, marks failing rows, and always shows the reason', async () => {
    const checks: EligibilityCheck[] = [
      { label: 'CGPA', passed: false, detail: 'Your CGPA 6.4 — required 7.0' },
      { label: 'Branch', passed: true, detail: 'CSE is eligible' },
    ];
    const fixture = TestBed.createComponent(EligibilityPanel);
    fixture.componentRef.setInput('checks', checks);
    await fixture.whenStable();

    const rows = fixture.nativeElement.querySelectorAll('.row');
    expect(rows.length).toBe(2);
    expect(rows[0].classList).toContain('row--fail'); // the failing CGPA row
    expect(rows[1].classList).not.toContain('row--fail');
    // the reason/detail is rendered on every row (never a bare "not eligible")
    expect(fixture.nativeElement.textContent).toContain('Your CGPA 6.4 — required 7.0');
    expect(rows[0].querySelector('.icon').textContent.trim()).toBe('✕');
    expect(rows[1].querySelector('.icon').textContent.trim()).toBe('✓');
    // a11y: pass/fail is not colour/glyph-only — a visually-hidden label states it
    expect(rows[0].querySelector('.icon').getAttribute('aria-hidden')).toBe('true');
    expect(rows[0].querySelector('.cc-sr-only').textContent.trim()).toBe('Failed:');
    expect(rows[1].querySelector('.cc-sr-only').textContent.trim()).toBe('Passed:');
  });
});
