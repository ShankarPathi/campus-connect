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

  it('renders queued toasts in an aria-live region', async () => {
    const fixture = TestBed.createComponent(Toast);
    fixture.detectChanges();
    svc.success('Saved');
    fixture.detectChanges();
    await fixture.whenStable();
    const region = fixture.nativeElement.querySelector('[aria-live="polite"]') as HTMLElement;
    expect(region.textContent).toContain('Saved');
  });
});
