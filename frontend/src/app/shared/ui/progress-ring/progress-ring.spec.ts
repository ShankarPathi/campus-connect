import { TestBed } from '@angular/core/testing';
import { ProgressRing } from './progress-ring';

describe('ProgressRing', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ProgressRing] }).compileComponents();
  });

  it('renders the clamped percent + an aria-label', async () => {
    const fixture = TestBed.createComponent(ProgressRing);
    fixture.componentRef.setInput('percent', 72);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.value').textContent.trim()).toBe('72%');
    expect(fixture.nativeElement.querySelector('.ring').getAttribute('aria-label')).toBe('72% complete');
  });

  it('clamps out-of-range values to 0–100', async () => {
    const fixture = TestBed.createComponent(ProgressRing);
    fixture.componentRef.setInput('percent', 140);
    await fixture.whenStable();
    expect(fixture.componentInstance.clamped()).toBe(100);
    expect(fixture.componentInstance.offset()).toBe(0); // full ring at 100%

    fixture.componentRef.setInput('percent', -10);
    await fixture.whenStable();
    expect(fixture.componentInstance.clamped()).toBe(0);
  });
});
