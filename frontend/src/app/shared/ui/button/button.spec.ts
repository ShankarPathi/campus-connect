import { TestBed } from '@angular/core/testing';
import { Button } from './button';

describe('Button', () => {
  it('renders projected content, the variant class, and the type', async () => {
    const fixture = TestBed.createComponent(Button);
    fixture.componentRef.setInput('variant', 'secondary');
    fixture.componentRef.setInput('type', 'submit');
    await fixture.whenStable();

    const btn = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(btn.classList).toContain('btn--secondary');
    expect(btn.getAttribute('type')).toBe('submit');
  });

  it('is enabled with no spinner by default', async () => {
    const fixture = TestBed.createComponent(Button);
    await fixture.whenStable();
    const btn = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(btn.disabled).toBe(false);
    expect(fixture.nativeElement.querySelector('.btn__spinner')).toBeNull();
  });

  it('disables and shows a spinner while loading', async () => {
    const fixture = TestBed.createComponent(Button);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    await fixture.whenStable();

    const btn = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(btn.disabled).toBe(true);
    expect(btn.getAttribute('aria-busy')).toBe('true');
    expect(fixture.nativeElement.querySelector('.btn__spinner')).not.toBeNull();
  });
});
