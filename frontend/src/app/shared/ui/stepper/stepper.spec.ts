import { TestBed } from '@angular/core/testing';
import { Stepper } from './stepper';
import { StepItem } from '../ui.models';

describe('Stepper', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Stepper] }).compileComponents();
  });

  it('renders a node per step with its state class', async () => {
    const steps: StepItem[] = [
      { label: 'Round 1', state: 'done' },
      { label: 'Round 2', state: 'current' },
      { label: 'Round 3', state: 'upcoming' },
    ];
    const fixture = TestBed.createComponent(Stepper);
    fixture.componentRef.setInput('steps', steps);
    await fixture.whenStable();

    const items = fixture.nativeElement.querySelectorAll('.step');
    expect(items.length).toBe(3);
    expect(items[0].classList).toContain('step--done');
    expect(items[1].classList).toContain('step--current');
    expect(items[2].classList).toContain('step--upcoming');
    expect(items[0].querySelector('.node').textContent.trim()).toBe('✓'); // done shows a check
  });
});
