import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SegmentedSections } from './segmented-sections';
import { SegmentItem } from '../ui.models';

const SECTIONS: SegmentItem[] = [
  { key: 'eligible', label: 'Eligible', count: 4 },
  { key: 'applied', label: 'Applied', count: 2 },
  { key: 'closed', label: 'Closed', count: 7 },
];

describe('SegmentedSections', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SegmentedSections] }).compileComponents();
  });

  function makeDesktop() {
    const fixture = TestBed.createComponent(SegmentedSections);
    fixture.componentRef.setInput('sections', SECTIONS);
    fixture.componentRef.setInput('activeKey', 'eligible');
    fixture.componentInstance.mobile.set(false);
    return fixture;
  }

  it('renders count-badged tabs and switches the active key on click (desktop)', async () => {
    const fixture = makeDesktop();
    await fixture.whenStable();

    const buttons = fixture.nativeElement.querySelectorAll('.segment');
    expect(buttons.length).toBe(3);
    expect(buttons[0].classList).toContain('segment--active');
    expect(buttons[0].querySelector('.seg-count').textContent.trim()).toBe('4');

    buttons[2].click(); // "Closed"
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('closed');
    expect(fixture.nativeElement.querySelectorAll('.segment')[2].classList).toContain('segment--active');
  });

  it('wires the tablist/tabpanel ARIA pattern with a roving tabindex (desktop)', async () => {
    const fixture = makeDesktop();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[role="tablist"]')).toBeTruthy();
    const tabs = el.querySelectorAll<HTMLElement>('[role="tab"]');
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[0].getAttribute('tabindex')).toBe('0');
    expect(tabs[1].getAttribute('tabindex')).toBe('-1'); // only the active tab is tabbable

    const panel = el.querySelector<HTMLElement>('[role="tabpanel"]')!;
    expect(tabs[0].getAttribute('aria-controls')).toBe(panel.id);
    expect(panel.getAttribute('aria-labelledby')).toBe(tabs[0].id);
  });

  it('projects the parent panel into the tabpanel (desktop) and the open accordion section (mobile)', async () => {
    // Host that projects content — proves <ng-content> lands inside the ARIA panel, not as a loose sibling.
    @Component({
      standalone: true,
      imports: [SegmentedSections],
      template: `<app-segmented-sections [sections]="s" activeKey="eligible"><p class="projected">PANEL BODY</p></app-segmented-sections>`,
    })
    class Host {
      s = SECTIONS;
    }
    const fixture = TestBed.createComponent(Host);
    const seg = fixture.debugElement.children[0].componentInstance as SegmentedSections;

    seg.mobile.set(false);
    fixture.detectChanges();
    await fixture.whenStable();
    const panel = fixture.nativeElement.querySelector('[role="tabpanel"]') as HTMLElement;
    expect(panel.querySelector('.projected')?.textContent).toContain('PANEL BODY');

    seg.mobile.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    const accPanel = fixture.nativeElement.querySelector('[role="region"]') as HTMLElement;
    expect(accPanel.querySelector('.projected')?.textContent).toContain('PANEL BODY');
  });

  it('moves and activates the tab with ArrowRight / Home / End (desktop)', async () => {
    const fixture = makeDesktop();
    await fixture.whenStable();
    const tablist = fixture.nativeElement.querySelector('[role="tablist"]') as HTMLElement;

    tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('applied');

    tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('closed');

    tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('eligible');

    tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('closed'); // wraps
  });

  it('renders an accordion with disclosure buttons under 768px; only the active section is expanded', async () => {
    const fixture = TestBed.createComponent(SegmentedSections);
    fixture.componentRef.setInput('sections', SECTIONS);
    fixture.componentRef.setInput('activeKey', 'eligible');
    fixture.componentInstance.mobile.set(true);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('[role="tablist"]')).toBeNull();
    const headers = el.querySelectorAll<HTMLElement>('.acc-btn');
    expect(headers.length).toBe(3);
    expect(headers[0].getAttribute('aria-expanded')).toBe('true'); // eligible expanded by default
    expect(headers[1].getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelectorAll('[role="region"]').length).toBe(1); // single shared panel below the headers

    headers[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('applied');
    expect(fixture.nativeElement.querySelectorAll('.acc-btn')[1].getAttribute('aria-expanded')).toBe('true');
  });
});
