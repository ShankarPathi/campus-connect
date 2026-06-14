package com.campusconnect.common.eligibility;

import com.campusconnect.common.domain.AccountStatus;
import com.campusconnect.common.domain.Drive;
import com.campusconnect.common.domain.StudentProfile;

import java.time.Instant;

/**
 * The single pure input to {@link EligibilityEngine#check} (Story 5.1, Decision A) — everything the ten
 * rules need, supplied explicitly by the caller so the engine stays pure and deterministic.
 *
 * <p>The architecture's literal {@code check(StudentProfile, Drive, ResolvedPolicy)} cannot express
 * three of the rules: rule&nbsp;1 (account ACTIVE — {@code accountStatus} lives on the {@code User},
 * not the profile), rule&nbsp;4 (within deadline — needs a {@code now}; a pure function must not call
 * {@code Instant.now()}), and rule&nbsp;5 (no duplicate — a fact about the {@code applications}
 * collection). So the caller (5.4 apply / 5.3 panel) assembles this bundle: {@code accountStatus} from
 * the token's user, {@code now} from the system clock, {@code alreadyApplied} from the unique-index
 * lookup (the 5.4 idempotency commitment).
 *
 * @param accountStatus  the applying user's account status (rule 1)
 * @param profile        the student's placement profile (rules 2, 6, 7, 8, 9, 10)
 * @param drive          the target drive + its embedded eligibility criteria (rules 3, 4, 6, 7, 10)
 * @param resolvedPolicy the resolved policy thresholds (rules 8, 9, 10)
 * @param alreadyApplied whether this student already has an application to this drive (rule 5)
 * @param now            the evaluation instant — the clock, passed in (rule 4)
 */
public record EligibilityContext(
        AccountStatus accountStatus,
        StudentProfile profile,
        Drive drive,
        ResolvedPolicy resolvedPolicy,
        boolean alreadyApplied,
        Instant now) {
}
