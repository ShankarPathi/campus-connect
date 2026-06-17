import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AccountStatus,
  AdminEditDriveCriteriaRequest,
  AdminEditProfileRequest,
  DashboardSnapshot,
  DriveStatus,
  EligibilityPolicy,
  PendingDrive,
  PendingProfile,
  PendingRecruiter,
  PlacementRecord,
  PlacementReport,
  PlacementStatus,
  ProfileApprovalStatus,
  UpdateEligibilityPolicyRequest,
} from './admin.models';

const BASE = `${environment.apiBase}/admin`;

/** Dashboard snapshot (Epic 8). Responses arrive already unwrapped by the 9.2 interceptor. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  snapshot(): Promise<DashboardSnapshot> {
    return firstValueFrom(this.http.get<DashboardSnapshot>(`${BASE}/dashboard`));
  }
}

/** Student-profile approvals (Epic 3). */
@Injectable({ providedIn: 'root' })
export class ProfileApprovalService {
  private readonly http = inject(HttpClient);
  list(status: ProfileApprovalStatus): Promise<PendingProfile[]> {
    return firstValueFrom(this.http.get<PendingProfile[]>(`${BASE}/profiles`, { params: { status } }));
  }
  approve(studentId: string): Promise<boolean> {
    return firstValueFrom(this.http.post<boolean>(`${BASE}/profiles/${studentId}/approve`, {}));
  }
  reject(studentId: string, reason: string): Promise<boolean> {
    return firstValueFrom(this.http.post<boolean>(`${BASE}/profiles/${studentId}/reject`, { reason }));
  }
  edit(studentId: string, body: AdminEditProfileRequest): Promise<boolean> {
    return firstValueFrom(this.http.patch<boolean>(`${BASE}/profiles/${studentId}`, body));
  }
  lock(): Promise<number> {
    return firstValueFrom(this.http.post<number>(`${BASE}/profiles/lock`, {}));
  }
  unlock(): Promise<number> {
    return firstValueFrom(this.http.post<number>(`${BASE}/profiles/unlock`, {}));
  }
}

/** Recruiter approvals (Epic 2). */
@Injectable({ providedIn: 'root' })
export class RecruiterApprovalService {
  private readonly http = inject(HttpClient);
  list(status: AccountStatus): Promise<PendingRecruiter[]> {
    return firstValueFrom(this.http.get<PendingRecruiter[]>(`${BASE}/recruiters`, { params: { status } }));
  }
  approve(userId: string): Promise<boolean> {
    return firstValueFrom(this.http.post<boolean>(`${BASE}/recruiters/${userId}/approve`, {}));
  }
  reject(userId: string, reason: string): Promise<boolean> {
    return firstValueFrom(this.http.post<boolean>(`${BASE}/recruiters/${userId}/reject`, { reason }));
  }
}

/** Drive approvals (Epic 4). */
@Injectable({ providedIn: 'root' })
export class DriveApprovalService {
  private readonly http = inject(HttpClient);
  list(status: DriveStatus): Promise<PendingDrive[]> {
    return firstValueFrom(this.http.get<PendingDrive[]>(`${BASE}/drives`, { params: { status } }));
  }
  approve(id: string): Promise<boolean> {
    return firstValueFrom(this.http.post<boolean>(`${BASE}/drives/${id}/approve`, {}));
  }
  reject(id: string, reason: string): Promise<boolean> {
    return firstValueFrom(this.http.post<boolean>(`${BASE}/drives/${id}/reject`, { reason }));
  }
  editCriteria(id: string, body: AdminEditDriveCriteriaRequest): Promise<boolean> {
    return firstValueFrom(this.http.patch<boolean>(`${BASE}/drives/${id}`, body));
  }
}

/** Placement confirmation queue (Epic 7, FR-25). */
@Injectable({ providedIn: 'root' })
export class PlacementService {
  private readonly http = inject(HttpClient);
  list(status: PlacementStatus): Promise<PlacementRecord[]> {
    return firstValueFrom(this.http.get<PlacementRecord[]>(`${BASE}/placements`, { params: { status } }));
  }
  confirm(placementId: string): Promise<PlacementRecord> {
    return firstValueFrom(this.http.post<PlacementRecord>(`${BASE}/placements/${placementId}/confirm`, {}));
  }
}

/** Tenant eligibility policy (Epic 5). */
@Injectable({ providedIn: 'root' })
export class EligibilityPolicyService {
  private readonly http = inject(HttpClient);
  get(): Promise<EligibilityPolicy> {
    return firstValueFrom(this.http.get<EligibilityPolicy>(`${BASE}/eligibility-policy`));
  }
  update(body: UpdateEligibilityPolicyRequest): Promise<EligibilityPolicy> {
    return firstValueFrom(this.http.put<EligibilityPolicy>(`${BASE}/eligibility-policy`, body));
  }
}

/** Placement reports + CSV export (Epic 8). The CSV is non-JSON — request it as text. */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);
  report(): Promise<PlacementReport> {
    return firstValueFrom(this.http.get<PlacementReport>(`${BASE}/reports/placements`));
  }
  exportCsv(): Promise<string> {
    return firstValueFrom(this.http.get(`${BASE}/reports/placements/export`, { responseType: 'text' }));
  }
}
