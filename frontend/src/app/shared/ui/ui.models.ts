/** Shared UI model types (Story 9.1). */

/** Semantic status variant — maps to the design-token status colors. */
export type StatusVariant = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/** One row in the eligibility panel — always carries its reason (`detail`). */
export interface EligibilityCheck {
  label: string;
  passed: boolean;
  detail: string;
}

/** One node in the stepper. */
export interface StepItem {
  label: string;
  state: 'done' | 'current' | 'upcoming';
}

/** One segment in the segmented-sections control. */
export interface SegmentItem {
  key: string;
  label: string;
  count: number;
}

/** One column in the data table. */
export interface TableColumn {
  key: string;
  header: string;
  align?: 'left' | 'right' | 'center';
  sortable?: boolean;
}

/** Sort change emitted by a sortable table header. */
export interface SortChange {
  key: string;
  dir: 'asc' | 'desc';
}
