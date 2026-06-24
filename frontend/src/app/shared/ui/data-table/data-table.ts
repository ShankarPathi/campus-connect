import { Component, input, output, signal, TemplateRef } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { SortChange, TableColumn } from '../ui.models';

/**
 * Config-driven data table (Story 9.1) — applicant lists + reports. Sticky uppercase (caption) header, 48px
 * rows, hover, right-aligned actions via a per-row `rowActions` template, and a projected `[table-toolbar]`
 * filter/sort bar above. Clicking a `sortable` header toggles + emits `sortChange`; rendering the sort order is
 * the caller's job.
 */
@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [NgTemplateOutlet],
  template: `
    <div class="toolbar"><ng-content select="[table-toolbar]" /></div>
    <table class="table">
      <thead>
        <tr>
          @for (col of columns(); track col.key) {
            <th
              class="th"
              [class]="'th--' + (col.align || 'left')"
              [class.th--sortable]="col.sortable"
              [attr.role]="col.sortable ? 'button' : null"
              [attr.tabindex]="col.sortable ? 0 : null"
              [attr.aria-sort]="ariaSort(col)"
              (click)="onSort(col)"
              (keydown.enter)="onSort(col)"
              (keydown.space)="onSort(col); $event.preventDefault()"
            >
              {{ col.header }}
              @if (col.sortable && sortKey() === col.key) {
                <span class="sort">{{ sortDir() === 'asc' ? '▲' : '▼' }}</span>
              }
            </th>
          }
          @if (rowActions()) {
            <th class="th th--right"></th>
          }
        </tr>
      </thead>
      <tbody>
        @for (row of rows(); track $index) {
          <tr class="tr">
            @for (col of columns(); track col.key) {
              <td class="td" [class]="'td--' + (col.align || 'left')">{{ row[col.key] }}</td>
            }
            @if (rowActions()) {
              <td class="td td--right actions">
                <ng-container [ngTemplateOutlet]="rowActions()!" [ngTemplateOutletContext]="{ $implicit: row }" />
              </td>
            }
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: [
    `
      .table {
        width: 100%;
        border-collapse: collapse;
        background: var(--cc-color-surface-raised);
      }
      .th {
        position: sticky;
        top: 0;
        z-index: 1;
        background: var(--cc-portal-soft, var(--cc-color-surface));
        font: var(--cc-text-caption);
        font-weight: 700;
        letter-spacing: 0.05em;
        text-transform: uppercase;
        color: var(--cc-color-text-secondary);
        text-align: left;
        padding: var(--cc-space-3) var(--cc-space-4);
        border-bottom: 1px solid var(--cc-color-border);
      }
      .th--sortable {
        cursor: pointer;
        user-select: none;
      }
      .th--right,
      .td--right {
        text-align: right;
      }
      .th--center,
      .td--center {
        text-align: center;
      }
      .sort {
        margin-left: var(--cc-space-1);
        color: var(--cc-color-primary);
      }
      .tr {
        height: 52px;
        transition: background 0.1s ease;
      }
      .tr:hover {
        background: var(--cc-color-primary-subtle);
      }
      .td {
        padding: 0 var(--cc-space-4);
        font: var(--cc-text-body);
        color: var(--cc-color-text);
        border-bottom: 1px solid var(--cc-color-border);
      }
      .tr:last-child .td {
        border-bottom: none;
      }
    `,
  ],
})
export class DataTable {
  readonly columns = input<TableColumn[]>([]);
  readonly rows = input<Record<string, unknown>[]>([]);
  readonly rowActions = input<TemplateRef<{ $implicit: Record<string, unknown> }> | null>(null);
  readonly sortChange = output<SortChange>();

  readonly sortKey = signal<string | null>(null);
  readonly sortDir = signal<'asc' | 'desc'>('asc');

  onSort(col: TableColumn): void {
    if (!col.sortable) {
      return;
    }
    const dir: 'asc' | 'desc' = this.sortKey() === col.key && this.sortDir() === 'asc' ? 'desc' : 'asc';
    this.sortKey.set(col.key);
    this.sortDir.set(dir);
    this.sortChange.emit({ key: col.key, dir });
  }

  /** `aria-sort` for a column: the active direction, `none` for an idle sortable column, null otherwise. */
  ariaSort(col: TableColumn): 'ascending' | 'descending' | 'none' | null {
    if (!col.sortable) {
      return null;
    }
    if (this.sortKey() !== col.key) {
      return 'none';
    }
    return this.sortDir() === 'asc' ? 'ascending' : 'descending';
  }
}
