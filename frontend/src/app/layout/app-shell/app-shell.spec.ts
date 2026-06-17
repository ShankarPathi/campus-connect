import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppShell } from './app-shell';

/**
 * Story 9.7 (AC3) — verifies the app-shell accessibility floor: a skip link as the first focusable element
 * pointing at a focusable <main>, the primary <nav> landmark, and the drawer toggle's aria wiring.
 */
describe('AppShell (accessibility floor)', () => {
  function setup() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders a skip link that targets a focusable <main id="main-content">', () => {
    const el = setup();
    const skip = el.querySelector<HTMLAnchorElement>('.skip-link')!;
    expect(skip).toBeTruthy();
    expect(skip.getAttribute('href')).toBe('#main-content');

    const main = el.querySelector<HTMLElement>('main#main-content')!;
    expect(main).toBeTruthy();
    expect(main.getAttribute('tabindex')).toBe('-1'); // focusable target for the skip link
  });

  it('exposes the primary navigation landmark', () => {
    const el = setup();
    expect(el.querySelector('nav[aria-label="Primary"]')).toBeTruthy();
  });

  it('wires the drawer toggle with aria-controls + aria-expanded reflecting the drawer state', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    const toggle = el.querySelector<HTMLButtonElement>('.hamburger')!;
    expect(toggle.getAttribute('aria-controls')).toBe('primary-sidebar');
    expect(el.querySelector('#primary-sidebar')).toBeTruthy();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    fixture.componentInstance.toggleDrawer();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
  });
});
