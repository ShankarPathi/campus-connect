package com.campusconnect.common.domain;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One student's instance of one interview round (architecture §5 {@code applicationRounds}; Story 6.3,
 * FR-20) — per-student-per-round. <b>Lean by design (Decision B):</b> it references the round definition by
 * {@code (driveId, roundOrder)} and carries only what is genuinely per-student — the {@code result}. The
 * round's {@code name}/{@code mode}/{@code schedule} are single-sourced on {@link Drive}'s {@code rounds[]}
 * (read by order), so a reschedule mutates one place and there is no per-student drift.
 *
 * <p>Created in {@code PENDING} when a shortlisted student is assigned to a round (round 1 in 6.3; later
 * rounds in 6.4 on a {@code PASS}). The unique {@code {tenantId, applicationId, roundOrder}} index makes the
 * assignment idempotent (one row per student per round); the {@code {tenantId, driveId, roundOrder}} index
 * backs the "who is in round N" query.
 */
@Document("applicationRounds")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_tenant_app_round",
                def = "{'tenantId': 1, 'applicationId': 1, 'roundOrder': 1}", unique = true),
        @CompoundIndex(name = "idx_tenant_drive_round",
                def = "{'tenantId': 1, 'driveId': 1, 'roundOrder': 1}")
})
public class ApplicationRound extends TenantAwareDocument {

    private String applicationId;
    private String driveId;
    private int roundOrder;
    private RoundResult result = RoundResult.PENDING;

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getDriveId() {
        return driveId;
    }

    public void setDriveId(String driveId) {
        this.driveId = driveId;
    }

    public int getRoundOrder() {
        return roundOrder;
    }

    public void setRoundOrder(int roundOrder) {
        this.roundOrder = roundOrder;
    }

    public RoundResult getResult() {
        return result;
    }

    public void setResult(RoundResult result) {
        this.result = result;
    }
}
