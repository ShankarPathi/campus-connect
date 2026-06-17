import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Topbar } from './topbar/topbar';
import { AppShell } from './app-shell/app-shell';
import { AuthStore } from '../core/auth/auth.store';
import { AuthService } from '../core/auth/auth.service';
import { StudentNotificationsService } from '../portals/student/student.services';

// The topbar bell binds to the student notifications unread signal; stub it (no HTTP in these layout tests).
const notificationsStub = { unreadCount: signal(0), refreshUnread: () => Promise.resolve(0) };

function jwt(claims: object): string {
  const b64 = (o: object) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.sig`;
}

describe('Topbar', () => {
  const logout = vi.fn();
  let store: AuthStore;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [Topbar],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { logout } },
        { provide: StudentNotificationsService, useValue: notificationsStub },
      ],
    }).compileComponents();
    store = TestBed.inject(AuthStore);
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT', tenantId: 'vignan' }), 'student');
  });

  it('shows the tenant name + role and logs out via the service', async () => {
    const fixture = TestBed.createComponent(Topbar);
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[data-test="tenant-name"]').textContent.trim()).toBe('vignan');
    expect(fixture.nativeElement.querySelector('.role-tag').textContent.trim()).toBe('STUDENT');

    (fixture.nativeElement.querySelector('.logout') as HTMLButtonElement).click();
    expect(logout).toHaveBeenCalled();
  });
});

describe('AppShell', () => {
  let store: AuthStore;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { logout: vi.fn() } },
        { provide: StudentNotificationsService, useValue: notificationsStub },
      ],
    }).compileComponents();
    store = TestBed.inject(AuthStore);
  });

  it('builds role-scoped nav and toggles the responsive drawer', async () => {
    store.setSession(jwt({ sub: 'u1', role: 'STUDENT', tenantId: 't1' }), 'student');
    const fixture = TestBed.createComponent(AppShell);
    await fixture.whenStable();

    const links = fixture.nativeElement.querySelectorAll('.nav-link');
    expect(links.length).toBe(6); // student nav: Dashboard · Drives · My Applications · Profile · Offers · Notifications
    expect(fixture.componentInstance.drawerOpen()).toBe(false);
    fixture.componentInstance.toggleDrawer();
    expect(fixture.componentInstance.drawerOpen()).toBe(true);
  });
});
