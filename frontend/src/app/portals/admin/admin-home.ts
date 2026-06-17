import { Component } from '@angular/core';

/** Admin portal placeholder (Story 9.2) — real screens land in a later Epic-9 story. */
@Component({
  selector: 'app-admin-home',
  standalone: true,
  template: `<div class="stub"><h2 class="cc-h2">Admin portal</h2><p class="cc-body">Screens arrive in a later Story 9.x.</p></div>`,
  styles: [
    `.stub { padding: var(--cc-space-6); background: var(--cc-color-surface-raised); border: 1px solid var(--cc-color-border); border-radius: var(--cc-radius-lg); box-shadow: var(--cc-shadow-sm); }`,
  ],
})
export class AdminHome {}
