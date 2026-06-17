import { Component, computed, input } from '@angular/core';

const RADIUS = 20;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/**
 * Progress ring (Story 9.1) — profile-completion %. Indigo (primary) arc over a border track, the integer %
 * centered. Accessible via role="img" + aria-label. `percent` is clamped to 0–100.
 */
@Component({
  selector: 'app-progress-ring',
  standalone: true,
  template: `
    <div class="ring" role="img" [attr.aria-label]="clamped() + '% complete'">
      <svg viewBox="0 0 48 48" width="48" height="48">
        <circle class="track" cx="24" cy="24" r="20" fill="none" stroke-width="4"></circle>
        <circle
          class="arc"
          cx="24"
          cy="24"
          r="20"
          fill="none"
          stroke-width="4"
          stroke-linecap="round"
          transform="rotate(-90 24 24)"
          [attr.stroke-dasharray]="circumference"
          [attr.stroke-dashoffset]="offset()"
        ></circle>
      </svg>
      <span class="value">{{ clamped() }}%</span>
    </div>
  `,
  styles: [
    `
      .ring {
        position: relative;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 48px;
        height: 48px;
      }
      .track {
        stroke: var(--cc-color-border);
      }
      .arc {
        stroke: var(--cc-color-primary);
        transition: stroke-dashoffset 0.3s ease;
      }
      .value {
        position: absolute;
        font: var(--cc-text-body-medium); /* 14px — fits the 48px ring; AC's display/h2 assumed a larger ring */
        color: var(--cc-color-text);
      }
    `,
  ],
})
export class ProgressRing {
  readonly percent = input(0);
  readonly circumference = CIRCUMFERENCE;
  readonly clamped = computed(() => Math.max(0, Math.min(100, Math.round(this.percent()))));
  readonly offset = computed(() => CIRCUMFERENCE * (1 - this.clamped() / 100));
}
