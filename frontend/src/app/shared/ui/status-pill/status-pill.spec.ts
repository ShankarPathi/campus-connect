import { TestBed } from '@angular/core/testing';
import { StatusPill } from './status-pill';

describe('StatusPill', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [StatusPill] }).compileComponents();
  });

  it('renders the label and the variant class', async () => {
    const fixture = TestBed.createComponent(StatusPill);
    fixture.componentRef.setInput('label', 'Shortlisted');
    fixture.componentRef.setInput('variant', 'info');
    await fixture.whenStable();

    const span = fixture.nativeElement.querySelector('.pill') as HTMLElement;
    expect(span.textContent).toContain('Shortlisted');
    expect(span.classList).toContain('pill--info');
  });

  it('renders a decorative per-variant glyph alongside the label (color-never-alone)', async () => {
    const fixture = TestBed.createComponent(StatusPill);
    fixture.componentRef.setInput('label', 'Placed');
    fixture.componentRef.setInput('variant', 'success');
    await fixture.whenStable();

    const glyph = fixture.nativeElement.querySelector('.pill__glyph') as HTMLElement;
    expect(glyph.textContent?.trim()).toBe('✓');
    expect(glyph.getAttribute('aria-hidden')).toBe('true');
  });

  it('defaults to the neutral variant', async () => {
    const fixture = TestBed.createComponent(StatusPill);
    await fixture.whenStable();
    expect((fixture.nativeElement.querySelector('.pill') as HTMLElement).classList).toContain('pill--neutral');
  });
});
