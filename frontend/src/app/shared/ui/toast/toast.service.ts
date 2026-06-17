import { Injectable, signal } from '@angular/core';

export interface ToastItem {
  id: number;
  text: string;
  variant: 'success' | 'error';
}

/**
 * Transient toast notifications (Story 9.4). A single `providedIn:'root'` queue rendered once by the
 * `Toast` host in the app shell. `success`/`error` enqueue an auto-dismissing message; the host
 * announces them via an ARIA live region.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<ToastItem[]>([]);

  /** Default visible duration (ms) before auto-dismiss. */
  readonly durationMs = 4000;

  success(text: string): void {
    this.push(text, 'success');
  }

  error(text: string): void {
    this.push(text, 'error');
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(text: string, variant: 'success' | 'error'): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list, { id, text, variant }]);
    setTimeout(() => this.dismiss(id), this.durationMs);
  }
}
