import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecruiterRounds } from './rounds';

describe('RecruiterRounds', () => {
  let fixture: ComponentFixture<RecruiterRounds>;
  let mock: HttpTestingController;
  const URL = '/api/recruiter/drives/d1/rounds';

  // Note: the define-form template binds `[formGroupName]="$index"` directly under `[formGroup]`,
  // which the reactive-forms directive cannot resolve in an isolated TestBed render — so we drive
  // and assert through the component's public model (signals + roundsArray) rather than the DOM.
  async function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    fixture = TestBed.createComponent(RecruiterRounds);
    mock = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('driveId', 'd1');
    fixture.detectChanges();
    await new Promise((r) => setTimeout(r)); // queueMicrotask load
    mock.expectOne((r) => r.method === 'GET' && r.url === URL).flush({ rounds: [] });
    await new Promise((r) => setTimeout(r));
  }
  afterEach(() => mock.verify());

  it('reaches the ready state with the empty define form ready', async () => {
    await setup();
    expect(fixture.componentInstance.state()).toBe('ready');
    expect(fixture.componentInstance.rounds().length).toBe(0);
    expect(fixture.componentInstance.roundsArray.length).toBe(1);
  });

  it('define() PUTs the filled round and stores the response', async () => {
    await setup();
    const cmp = fixture.componentInstance;
    cmp.roundsArray.at(0).patchValue({ name: 'Tech', schedule: '2099-07-01T10:00:00Z', venueOrLink: 'Zoom' });
    cmp.define();
    const put = mock.expectOne((r) => r.method === 'PUT' && r.url === URL);
    expect(put.request.body.rounds[0].name).toBe('Tech');
    put.flush({ rounds: [{ roundOrder: 1, name: 'Tech', mode: 'ONLINE', schedule: '2099-07-01T10:00:00Z', venueOrLink: 'Zoom', assignedCount: 3 }] });
    await new Promise((r) => setTimeout(r));
    expect(cmp.rounds().length).toBe(1);
  });
});
