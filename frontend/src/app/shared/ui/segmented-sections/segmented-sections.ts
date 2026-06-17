import { Component, input, model } from '@angular/core';
import { SegmentItem } from '../ui.models';

/**
 * Segmented sections (Story 9.1) — the four-section drive grouping (Eligible / Applied / Not Eligible /
 * Closed) as labeled, count-badged segments. Two-way `activeKey`; the parent renders the active panel via the
 * projected `<ng-content>` (e.g. switching on `activeKey`). Desktop = segmented tabs.
 */
@Component({
  selector: 'app-segmented-sections',
  standalone: true,
  template: `
    <div class="segments" role="tablist">
      @for (s of sections(); track s.key) {
        <button
          type="button"
          role="tab"
          class="segment"
          [class.segment--active]="s.key === activeKey()"
          [attr.aria-selected]="s.key === activeKey()"
          (click)="activeKey.set(s.key)"
        >
          <span class="seg-label">{{ s.label }}</span>
          <span class="seg-count">{{ s.count }}</span>
        </button>
      }
    </div>
    <ng-content />
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
      }
      .segment--active .seg-count {
        background: var(--cc-color-primary-subtle);
        color: var(--cc-color-primary);
      }
    `,
  ],
})
export class SegmentedSections {
  readonly sections = input<SegmentItem[]>([]);
  readonly activeKey = model<string>('');
}
