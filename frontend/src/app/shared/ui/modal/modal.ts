import { Component, ElementRef, effect, inject, input, model, output } from '@angular/core';

/**
 * Modal dialog (Story 9.4) — scrim + centered panel with `role="dialog"`/`aria-modal`, a labelled
 * title, Esc-to-close, scrim-click close, and a focus trap that restores focus on close. Projected
 * `[header]`, default body, and `[footer]` slots. Token-styled, no hard-coded hex.
 */
@Component({
  selector: 'app-modal',
  standalone: true,
  template: `
    @if (open()) {
      <div class="scrim" (click)="onScrim($event)">
        <div
          #panel
          class="panel"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          [attr.aria-label]="title()"
          (keydown)="onKeydown($event)"
        >
          <div class="panel__head">
            <h2 class="cc-h2">{{ title() }}</h2>
            <button class="panel__close" type="button" aria-label="Close" (click)="close()">×</button>
          </div>
          <div class="panel__body"><ng-content /></div>
          <div class="panel__foot"><ng-content select="[footer]" /></div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .scrim {
        position: fixed;
        inset: 0;
        background: rgba(17, 24, 39, 0.5);
        display: flex;
        align-items: flex-start;
        justify-content: center;
        padding: var(--cc-space-12) var(--cc-space-4);
        z-index: 900;
        overflow-y: auto;
      }
      .panel {
        width: 100%;
        max-width: 560px;
        background: var(--cc-color-surface-raised);
        border-radius: var(--cc-radius-lg);
        box-shadow: var(--cc-shadow-lg);
      }
      .panel__head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--cc-space-5) var(--cc-space-6);
        border-bottom: 1px solid var(--cc-color-border);
      }
      .panel__head h2 {
        margin: 0;
      }
      .panel__close {
        border: none;
        background: transparent;
        cursor: pointer;
        font-size: 22px;
        line-height: 1;
        color: var(--cc-color-text-muted);
      }
      .panel__body {
        padding: var(--cc-space-6);
      }
      .panel__foot:not(:empty) {
        display: flex;
        justify-content: flex-end;
        gap: var(--cc-space-3);
        padding: var(--cc-space-4) var(--cc-space-6);
        border-top: 1px solid var(--cc-color-border);
      }
    `,
  ],
})
export class Modal {
  private readonly host = inject(ElementRef<HTMLElement>);
  private previouslyFocused: HTMLElement | null = null;

  readonly open = model(false);
  readonly title = input('');
  /** Emits when the user requests a close (Esc / scrim / × button); parent owns the `open` state. */
  readonly closed = output<void>();

  constructor() {
    effect(() => {
      if (this.open()) {
        this.previouslyFocused = document.activeElement as HTMLElement | null;
        queueMicrotask(() => this.focusFirst());
      } else {
        this.previouslyFocused?.focus?.();
      }
    });
  }

  close(): void {
    this.open.set(false);
    this.closed.emit();
  }

  protected onScrim(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('scrim')) {
      this.close();
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }
    if (event.key === 'Tab') {
      this.trapFocus(event);
    }
  }

  private focusables(): HTMLElement[] {
    return Array.from(
      (this.host.nativeElement as HTMLElement).querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])',
      ),
    ) as HTMLElement[];
  }

  private focusFirst(): void {
    // Focus the first focusable child, or the panel itself (tabindex=-1) so Esc/Tab still work in an
    // otherwise-empty dialog (e.g. a read-only detail modal with no buttons).
    const first = this.focusables()[0];
    if (first) {
      first.focus();
    } else {
      (this.host.nativeElement as HTMLElement).querySelector<HTMLElement>('.panel')?.focus();
    }
  }

  private trapFocus(event: KeyboardEvent): void {
    const items = this.focusables();
    if (!items.length) {
      return;
    }
    const first = items[0];
    const last = items[items.length - 1];
    const active = document.activeElement as HTMLElement;
    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }
}
