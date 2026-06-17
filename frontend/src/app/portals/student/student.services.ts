import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  NotificationList,
  OfferDetail,
  OfferSummary,
  ResumeView,
  StudentApplication,
  StudentDrive,
  StudentProfile,
  StudentProfileRequest,
  UnreadCount,
} from './student.models';

const BASE = `${environment.apiBase}/student`;

/** Profile + resume (Epic 3). Responses arrive already unwrapped by the 9.2 interceptor. */
@Injectable({ providedIn: 'root' })
export class ProfileService {
  private readonly http = inject(HttpClient);

  getProfile(): Promise<StudentProfile> {
    return firstValueFrom(this.http.get<StudentProfile>(`${BASE}/profile`));
  }
  saveProfile(body: StudentProfileRequest): Promise<StudentProfile> {
    return firstValueFrom(this.http.put<StudentProfile>(`${BASE}/profile`, body));
  }
  submitProfile(): Promise<StudentProfile> {
    return firstValueFrom(this.http.post<StudentProfile>(`${BASE}/profile/submit`, {}));
  }
  getResume(): Promise<ResumeView> {
    return firstValueFrom(this.http.get<ResumeView>(`${BASE}/resume`));
  }
  uploadResume(file: File): Promise<ResumeView> {
    const form = new FormData();
    form.append('file', file);
    return firstValueFrom(this.http.post<ResumeView>(`${BASE}/resume`, form));
  }
}

/** Drives discovery + apply (Epic 5). */
@Injectable({ providedIn: 'root' })
export class DriveService {
  private readonly http = inject(HttpClient);

  listDrives(): Promise<StudentDrive[]> {
    return firstValueFrom(this.http.get<StudentDrive[]>(`${BASE}/drives`));
  }
  apply(driveId: string): Promise<StudentApplication> {
    return firstValueFrom(this.http.post<StudentApplication>(`${BASE}/drives/${driveId}/apply`, {}));
  }
}

/** My applications + withdraw (Epic 5). */
@Injectable({ providedIn: 'root' })
export class ApplicationsService {
  private readonly http = inject(HttpClient);

  listApplications(): Promise<StudentApplication[]> {
    return firstValueFrom(this.http.get<StudentApplication[]>(`${BASE}/applications`));
  }
  withdraw(applicationId: string): Promise<StudentApplication> {
    return firstValueFrom(this.http.post<StudentApplication>(`${BASE}/applications/${applicationId}/withdraw`, {}));
  }
}

/** Offers list/detail/accept/decline (Epic 7). */
@Injectable({ providedIn: 'root' })
export class OffersService {
  private readonly http = inject(HttpClient);

  listOffers(): Promise<OfferSummary[]> {
    return firstValueFrom(this.http.get<OfferSummary[]>(`${BASE}/offers`));
  }
  getOffer(id: string): Promise<OfferDetail> {
    return firstValueFrom(this.http.get<OfferDetail>(`${BASE}/offers/${id}`));
  }
  accept(id: string): Promise<OfferDetail> {
    return firstValueFrom(this.http.post<OfferDetail>(`${BASE}/offers/${id}/accept`, {}));
  }
  decline(id: string): Promise<OfferDetail> {
    return firstValueFrom(this.http.post<OfferDetail>(`${BASE}/offers/${id}/decline`, {}));
  }
}

/**
 * Notifications (Story 8.3) + the shared unread-count signal the topbar bell binds to. `refreshUnread`
 * is called on load and after any mark-read so the badge stays current.
 */
@Injectable({ providedIn: 'root' })
export class StudentNotificationsService {
  private readonly http = inject(HttpClient);
  readonly unreadCount = signal(0);

  list(page = 0, size = 20): Promise<NotificationList> {
    return firstValueFrom(
      this.http.get<NotificationList>(`${BASE}/notifications`, { params: { page, size } }),
    );
  }
  async refreshUnread(): Promise<number> {
    const res = await firstValueFrom(this.http.get<UnreadCount>(`${BASE}/notifications/unread-count`));
    this.unreadCount.set(res.unreadCount);
    return res.unreadCount;
  }
  async markRead(id: string): Promise<number> {
    const res = await firstValueFrom(this.http.post<UnreadCount>(`${BASE}/notifications/${id}/read`, {}));
    this.unreadCount.set(res.unreadCount);
    return res.unreadCount;
  }
  async markAll(): Promise<number> {
    const res = await firstValueFrom(this.http.post<UnreadCount>(`${BASE}/notifications/read-all`, {}));
    this.unreadCount.set(res.unreadCount);
    return res.unreadCount;
  }
}
