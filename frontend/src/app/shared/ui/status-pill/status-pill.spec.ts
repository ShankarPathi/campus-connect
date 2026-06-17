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
    expect(span.textContent?.trim()).toBe('Shortlisted');
    expect(span.classList).toContain('pill--info');
  });

  it('defaults to the neutral variant', async () => {
    const fixture = TestBed.createComponent(StatusPill);
    await fixture.whenStable();
    expect((fixture.nativeElement.querySelector('.pill') as HTMLElement).classList).toContain('pill--neutral');
  });
});
