import { Component, DestroyRef, ElementRef, inject, input, model, signal } from '@angular/core';
import { SegmentItem } from '../ui.models';

/** Process-wide counter so each instance's tab/panel ids are unique (multiple controls on one page). */
let nextId = 0;

/**
 * Segmented sections (Story 9.1; accessibility + responsive pass Story 9.7) — the four-section drive grouping
 * (Eligible / Applied / Not Eligible / Closed) as labeled, count-badged segments. Two-way `activeKey`; the
 * parent renders the active panel via the projected `<ng-content>` (switching on `activeKey`).
 *
 * - Desktop (≥768px): a WAI-ARIA **tablist** — roving tabindex (only the active tab is tabbable),
 *   ArrowLeft/Right/Home/End move + activate, and the projected panel is the `tabpanel`.
 * - Mobile (<768px): a stacked **accordion** of disclosure buttons (`aria-expanded`/`aria-controls`); the
 *   active section is expanded and renders the projected panel inline beneath its header.
 *
 * `<ng-content>` appears once per mutually-exclusive branch, so the projected panel is instantiated exactly once.
 */
@Component({
  selector: 'app-segmented-sections',
  standalone: true,
  template: `
    @if (mobile()) {
      <div class="accordion">
        @for (s of sections(); track s.key) {
          <button
            type="button"
            class="acc-btn"
            [id]="tabId(s.key)"
            [attr.aria-expanded]="s.key === activeKey()"
            [attr.aria-controls]="panelId"
            (click)="activeKey.set(s.key)"
          >
            <span class="acc-caret" aria-hidden="true">{{ s.key === activeKey() ? '▾' : '▸' }}</span>
            <span class="seg-label">{{ s.label }}</span>
            <span class="seg-count">{{ s.count }}</span>
          </button>
        }
      </div>
    } @else {
      <div class="segments" role="tablist" (keydown)="onKeydown($event)">
        @for (s of sections(); track s.key) {
          <button
            type="button"
            role="tab"
            class="segment"
            [id]="tabId(s.key)"
            [class.segment--active]="s.key === activeKey()"
            [attr.aria-selected]="s.key === activeKey()"
            [attr.aria-controls]="panelId"
            [attr.tabindex]="s.key === activeKey() ? 0 : -1"
            (click)="activeKey.set(s.key)"
          >
            <span class="seg-label">{{ s.label }}</span>
            <span class="seg-count">{{ s.count }}</span>
          </button>
        }
      </div>
    }
    <!-- Single projected panel — shared by both modes so the content never relocates on a breakpoint change. -->
    <div class="panel" [attr.role]="mobile() ? 'region' : 'tabpanel'" [id]="panelId" [attr.aria-labelledby]="tabId(activeKey())">
      <ng-content />
    </div>
  `,
  styles: [
    `
      .segments {
        display: flex;
        gap: var(--cc-space-1);
        border-bottom: 1px solid var(--cc-color-border);
        margin-bottom: var(--cc-space-4);
      }
      .segment {
        display: inline-flex;
        align-items: center;
        gap: var(--cc-space-2);
        min-height: 40px;
        padding: var(--cc-space-2) var(--cc-space-4);
        border: none;
        background: transparent;
        border-bottom: 2px solid transparent;
        cursor: pointer;
        font: var(--cc-text-body-medium);
        color: var(--cc-color-text-secondary);
      }
      .segment:hover {
        color: var(--cc-color-text);
      }
      .segment--active {
        color: var(--cc-color-primary);
        border-bottom-color: var(--cc-color-primary);
      }
      .seg-count {
        min-width: 20px;
        padding: 0 6px;
        border-radius: var(--cc-radius-full);
        font: var(--cc-text-caption);
        background: var(--cc-color-surface);
        color: var(--cc-color-text-secondary);
        text-align: center;
      }
      .segment--active .seg-count {
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-primary);
      }
      /* ── accordion (<768px) ── */
      .accordion {
        display: flex;
        flex-direction: column;
        margin-bottom: var(--cc-space-4);
      }
      .acc-btn {
        display: flex;
        align-items: center;
        gap: var(--cc-space-2);
        width: 100%;
        min-height: 44px;
        padding: var(--cc-space-3) var(--cc-space-2);
        border: none;
        border-bottom: 1px solid var(--cc-color-border);
        background: transparent;
        cursor: pointer;
        font: var(--cc-text-body-medium);
        color: var(--cc-color-text);
        text-align: left;
      }
      .acc-caret {
        width: 1em;
        color: var(--cc-color-text-secondary);
      }
      .acc-btn .seg-label {
        flex: 1;
      }
    `,
  ],
})
export class SegmentedSections {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly sections = input<SegmentItem[]>([]);
  readonly activeKey = model<string>('');

  /** True under 768px — drives the accordion vs tablist branch. */
  readonly mobile = signal(false);

  private readonly uid = nextId++;
  readonly panelId = `seg-panel-${this.uid}`;
  tabId(key: string): string {
    return `seg-tab-${this.uid}-${key}`;
  }

  constructor() {
    const mq = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(max-width: 767px)') : null;
    if (mq) {
      this.mobile.set(mq.matches);
      const onChange = (e: MediaQueryListEvent) => this.mobile.set(e.matches);
      mq.addEventListener('change', onChange);
      inject(DestroyRef).onDestroy(() => mq.removeEventListener('change', onChange));
    }
  }

  /** Arrow/Home/End roving navigation across the tabs (desktop tablist). */
  onKeydown(event: KeyboardEvent): void {
    const keys = this.sections().map((s) => s.key);
    if (keys.length === 0) {
      return;
    }
    const current = keys.indexOf(this.activeKey());
    let next = current;
    switch (event.key) {
      case 'ArrowRight':
        next = (current + 1) % keys.length;
        break;
      case 'ArrowLeft':
        next = (current - 1 + keys.length) % keys.length;
        break;
      case 'Home':
        next = 0;
        break;
      case 'End':
        next = keys.length - 1;
        break;
      default:
        return;
    }
    event.preventDefault();
    this.activeKey.set(keys[next]);
    queueMicrotask(() => {
      const host = this.host.nativeElement as HTMLElement;
      host.querySelector<HTMLElement>(`[id="${this.tabId(keys[next])}"]`)?.focus();
    });
  }
}
