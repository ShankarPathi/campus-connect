import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EligibilityPolicyPage } from './eligibility';

const URL = '/api/admin/eligibility-policy';

describe('EligibilityPolicyPage', () => {
  let mock: HttpTestingController;
  let fixture: ComponentFixture<EligibilityPolicyPage>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    mock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EligibilityPolicyPage);
  });

  afterEach(() => mock.verify());

  /** Flush the constructor GET, then let the async load() microtasks settle and render. */
  async function settleLoad(): Promise<void> {
    const req = mock.expectOne(URL);
    expect(req.request.method).toBe('GET');
    req.flush({ minCgpaFloor: 6, placedStudentsMayApply: true, reapplyPackageThresholdLpa: 10 });
    await new Promise((r) => setTimeout(r));
    fixture.detectChanges();
  }

  it('loads the policy, seeds the form, and shows placedStudentsMayApply read-only', async () => {
    await settleLoad();

    expect(fixture.componentInstance.state()).toBe('ready');
    expect(fixture.componentInstance.form.getRawValue()).toEqual({
      minCgpaFloor: '6',
      reapplyPackageThresholdLpa: '10',
    });
    expect(fixture.componentInstance.placedMayApply()).toBe(true);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Placed students may apply:');
    expect(text).toContain('Yes');
  });

  it('Save PUTs the two editable fields, mapping an empty field to null', async () => {
    await settleLoad();

    const form = fixture.componentInstance.form;
    form.controls.minCgpaFloor.setValue('7.5');
    form.controls.reapplyPackageThresholdLpa.setValue('');

    void fixture.componentInstance.save();

    const req = mock.expectOne(URL);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ minCgpaFloor: 7.5, reapplyPackageThresholdLpa: null });
    req.flush({ minCgpaFloor: 7.5, placedStudentsMayApply: true, reapplyPackageThresholdLpa: null });
  });

  it('blocks the PUT when the CGPA floor is invalid', async () => {
    await settleLoad();

    fixture.componentInstance.form.controls.minCgpaFloor.setValue('15');

    void fixture.componentInstance.save();

    mock.expectNone(URL);
    expect(fixture.componentInstance.form.controls.minCgpaFloor.touched).toBe(true);
  });
});
