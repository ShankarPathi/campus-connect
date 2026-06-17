import { TestBed } from '@angular/core/testing';
import { DataTable } from './data-table';
import { SortChange, TableColumn } from '../ui.models';

describe('DataTable', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DataTable] }).compileComponents();
  });

  it('renders headers + rows and emits sortChange on a sortable header, toggling direction', async () => {
    const columns: TableColumn[] = [
      { key: 'name', header: 'Name', sortable: true },
      { key: 'cgpa', header: 'CGPA', align: 'right', sortable: true },
    ];
    const rows = [
      { name: 'Asha', cgpa: 8.1 },
      { name: 'Ravi', cgpa: 7.4 },
    ];
    const fixture = TestBed.createComponent(DataTable);
    fixture.componentRef.setInput('columns', columns);
    fixture.componentRef.setInput('rows', rows);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('thead .th').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('tbody .tr').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Asha');

    const emitted: SortChange[] = [];
    fixture.componentInstance.sortChange.subscribe((e) => emitted.push(e));

    const nameHeader = fixture.nativeElement.querySelectorAll('thead .th')[0] as HTMLElement;
    // a11y: sortable headers are keyboard-operable + expose aria-sort
    expect(nameHeader.getAttribute('tabindex')).toBe('0');
    expect(nameHeader.getAttribute('role')).toBe('button');
    expect(nameHeader.getAttribute('aria-sort')).toBe('none');

    nameHeader.click();
    await fixture.whenStable();
    expect(nameHeader.getAttribute('aria-sort')).toBe('ascending');
    nameHeader.click();
    await fixture.whenStable();
    expect(nameHeader.getAttribute('aria-sort')).toBe('descending');

    expect(emitted).toEqual([
      { key: 'name', dir: 'asc' },
      { key: 'name', dir: 'desc' },
    ]);
  });
});
