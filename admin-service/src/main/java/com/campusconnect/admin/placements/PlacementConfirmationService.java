package com.campusconnect.admin.placements;

import com.campusconnect.common.audit.AuditService;
import com.campusconnect.common.domain.AuditAction;
import com.campusconnect.common.domain.PlacementLifecycle;
import com.campusconnect.common.domain.PlacementRecord;
import com.campusconnect.common.domain.PlacementStatus;
import com.campusconnect.common.domain.NotificationType;
import com.campusconnect.common.events.DomainEvent;
import com.campusconnect.common.events.EventPublisher;
import com.campusconnect.common.events.NotificationRecipient;
import com.campusconnect.common.exception.ResourceNotFoundException;
import com.campusconnect.common.repository.PlacementRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The College Admin's two-step placement confirmation (Story 7.4, FR-25) — the official, audited event that
 * turns a student's accepted placement into a report-counted one, and the close of Epic 7.
 *
 * <p>Every operation is scoped to the calling admin's tenant via the tenant-aware {@link PlacementRecordRepository}
 * (a placement in another tenant is simply not found → 404 — the Story 3.3 admin isolation guard, no separate
 * ownership branch). Confirmation goes through the canonical {@link PlacementLifecycle}
 * ({@code PENDING_CONFIRMATION → OFFICIALLY_PLACED}, {@code @Version}-safe) and is audited. The student's
 * {@code isPlaced} flag was set at accept (Story 7.3) and is not touched here; reports count only
 * {@code OFFICIALLY_PLACED}.
 */
@Service
public class PlacementConfirmationService {

    private static final String ENTITY_TYPE = "PlacementRecord";

    private final PlacementRecordRepository placementRecordRepository;
    private final AuditService auditService;
    private final EventPublisher eventPublisher;

    public PlacementConfirmationService(PlacementRecordRepository placementRecordRepository,
                                        AuditService auditService, EventPublisher eventPublisher) {
        this.placementRecordRepository = placementRecordRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    /** The admin's tenant's placement records in the given status — the confirmation queue. */
    public List<PlacementResponse> listByStatus(PlacementStatus status) {
        return placementRecordRepository.findByStatus(status).stream()
                .map(PlacementResponse::of)
                .toList();
    }

    /** Confirm one PENDING_CONFIRMATION placement of the admin's tenant → OFFICIALLY_PLACED; audited. */
    public PlacementResponse confirm(String placementId) {
        PlacementRecord record = placementRecordRepository.findById(placementId)
                .orElseThrow(() -> new ResourceNotFoundException("Placement record not found"));

        PlacementStatus from = record.getStatus();
        PlacementLifecycle.requireTransition(from, PlacementStatus.OFFICIALLY_PLACED);
        record.setStatus(PlacementStatus.OFFICIALLY_PLACED);
        PlacementRecord saved = placementRecordRepository.save(record);

        auditService.record(AuditAction.PLACEMENT_CONFIRMED, ENTITY_TYPE, placementId,
                "status=" + from, "status=" + PlacementStatus.OFFICIALLY_PLACED);

        eventPublisher.publish(DomainEvent.of("PLACEMENT_CONFIRMED:" + placementId,
                NotificationType.PLACEMENT_CONFIRMED, new NotificationRecipient(saved.getStudentId(),
                        "Placement confirmed", "Your placement at " + saved.getCompany() + " is officially confirmed.")));

        return PlacementResponse.of(saved);
    }
}
