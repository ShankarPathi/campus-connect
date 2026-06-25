import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ReportsPage } from './reports';

const REPORT = {
  overall: { totalStudents: 100, placedStudents: 60, placementPercent: 60 },
  branchwise: [{ branch: 'CSE', totalStudents: 50, placedStudents: 35, placementPercent: 70 }],
  companywise: [{ company: 'TCS', placements: 20 }],
};

describe('ReportsPage', () => {
  let fixture: ComponentFixture<ReportsPage>;
  let page: ReportsPage;
  let mock: HttpTestingController;

  // The constructor fires `void this.load()`, so a GET is already pending; flush it then drain the microtask queue.
  async function settleLoad(body: Record<string, unknown> = REPORT): Promise<void> {
    mock.expectOne('/api/admin/reports/placements').flush(body);
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ReportsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportsPage);
    page = fixture.componentInstance;
  });

  afterEach(() => mock.verify());

  it('loads the report and renders the overall percent plus both tables', async () => {
    fixture.detectChanges();
    await settleLoad();

    expect(page.report()?.overall.placementPercent).toBe(60);
    expect(page.branches()).toEqual([{ branch: 'CSE', totalStudents: 50, placedStudents: 35, placementPercent: 70 }]);
    expect(page.companyRows()).toEqual([{ company: 'TCS', placements: 20 }]);

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('60% placed');
    expect(text).toContain('60 of 100');
    expect(text).toContain('CSE');
    expect(text).toContain('TCS');

    // Branch breakdown is rendered as progress bars; the CSE fill reflects its 70% placement.
    const fill = fixture.nativeElement.querySelector('.brow__fill') as HTMLElement;
    expect(fill.style.width).toBe('70%');

    // Only the company breakdown still uses the generic data-table now.
    const tables = fixture.nativeElement.querySelectorAll('app-data-table');
    expect(tables.length).toBe(1);
  });

  it('Export CSV requests text, builds a download, and revokes the object URL', async () => {
    fixture.detectChanges();
    await settleLoad();

    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    void page.exportCsv();
    await new Promise((r) => setTimeout(r));

    const req = mock.expectOne('/api/admin/reports/placements/export');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('text');
    req.flush('branch,placed\nCSE,35');
    await new Promise((r) => setTimeout(r));

    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    await new Promise((r) => setTimeout(r)); // revoke is deferred to a macrotask
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:x');

    createObjectURL.mockRestore();
    revokeObjectURL.mockRestore();
    click.mockRestore();
  });
});
