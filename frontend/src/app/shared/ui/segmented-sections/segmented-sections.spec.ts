import { TestBed } from '@angular/core/testing';
import { SegmentedSections } from './segmented-sections';
import { SegmentItem } from '../ui.models';

describe('SegmentedSections', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SegmentedSections] }).compileComponents();
  });

  it('renders count-badged segments and switches the active key on click', async () => {
    const sections: SegmentItem[] = [
      { key: 'eligible', label: 'Eligible', count: 4 },
      { key: 'applied', label: 'Applied', count: 2 },
      { key: 'closed', label: 'Closed', count: 7 },
    ];
    const fixture = TestBed.createComponent(SegmentedSections);
    fixture.componentRef.setInput('sections', sections);
    fixture.componentRef.setInput('activeKey', 'eligible');
    await fixture.whenStable();

    const buttons = fixture.nativeElement.querySelectorAll('.segment');
    expect(buttons.length).toBe(3);
    expect(buttons[0].classList).toContain('segment--active');
    expect(buttons[0].querySelector('.seg-count').textContent.trim()).toBe('4');

    buttons[2].click(); // click "Closed"
    await fixture.whenStable();
    expect(fixture.componentInstance.activeKey()).toBe('closed');
    expect(fixture.nativeElement.querySelectorAll('.segment')[2].classList).toContain('segment--active');
  });
});
