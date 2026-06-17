import { TestBed } from '@angular/core/testing';
import { Toast } from './toast';
import { ToastService } from './toast.service';

describe('ToastService + Toast', () => {
  let svc: ToastService;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ToastService] });
    svc = TestBed.inject(ToastService);
  });

  it('enqueues success/error and dismisses', () => {
    svc.success('Applied');
    svc.error('Failed');
    expect(svc.toasts().length).toBe(2);
    const first = svc.toasts()[0];
    svc.dismiss(first.id);
    expect(svc.toasts().length).toBe(1);
    expect(svc.toasts()[0].variant).toBe('error');
  });

  it('renders success in the polite region and errors in the assertive alert region (Story 9.7)', async () => {
    const fixture = TestBed.createComponent(Toast);
    fixture.detectChanges();
    svc.success('Saved');
    svc.error('Failed');
    fixture.detectChanges();
    await fixture.whenStable();

    const polite = fixture.nativeElement.querySelector('[aria-live="polite"]') as HTMLElement;
    const assertive = fixture.nativeElement.querySelector('[role="alert"][aria-live="assertive"]') as HTMLElement;
    expect(polite.textContent).toContain('Saved');
    expect(polite.textContent).not.toContain('Failed');
    expect(assertive.textContent).toContain('Failed');
  });
});
