package com.campusconnect.common.profile;

import com.campusconnect.common.domain.StudentProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileCompletionTest {

    /** A profile with every required field filled. */
    private static StudentProfile complete() {
        StudentProfile p = new StudentProfile();
        p.setRollNumber("21CS001");
        p.setBatch("2026");
        p.getPersonal().setFullName("Asha Rao");
        p.getPersonal().setPhone("9990001234");
        p.getAcademic().setBranch("CSE");
        p.getAcademic().setCgpa(8.1);
        p.getAcademic().setActiveBacklogs(0);
        p.getPlacement().setSkills(List.of("Java"));
        return p;
    }

    @Test
    void emptyProfile_is0() {
        assertThat(ProfileCompletion.percentOf(new StudentProfile())).isZero();
    }

    @Test
    void allRequiredFieldsFilled_is100() {
        assertThat(ProfileCompletion.percentOf(complete())).isEqualTo(100);
    }

    @Test
    void zeroBacklogsCountsAsFilled_not100MinusOne() {
        // activeBacklogs == 0 is a real value (not "unfilled") — a complete profile with 0 backlogs is 100
        StudentProfile p = complete();
        p.getAcademic().setActiveBacklogs(0);
        assertThat(ProfileCompletion.percentOf(p)).isEqualTo(100);
    }

    @Test
    void halfTheRequiredFields_is50() {
        // 4 of the 8 required fields filled → 50
        StudentProfile p = new StudentProfile();
        p.setRollNumber("21CS001");
        p.setBatch("2026");
        p.getPersonal().setFullName("Asha Rao");
        p.getPersonal().setPhone("9990001234");
        assertThat(ProfileCompletion.percentOf(p)).isEqualTo(50);
    }

    @Test
    void blankStringsAndEmptySkillsDoNotCount() {
        StudentProfile p = complete();
        p.getPersonal().setFullName("   ");      // blank → not filled
        p.getPlacement().setSkills(List.of());   // empty → not filled
        // 6 of 8 filled → 75
        assertThat(ProfileCompletion.percentOf(p)).isEqualTo(75);
    }

    @Test
    void optionalFieldsDoNotAffectCompletion() {
        StudentProfile p = complete();
        p.getPersonal().setAddress(null);
        p.getPersonal().setGender(null);
        p.getPlacement().setExpectedRole(null);
        assertThat(ProfileCompletion.percentOf(p)).isEqualTo(100); // optional absence doesn't lower it
    }

    @Test
    void isComplete_trueOnlyAt100() {
        assertThat(ProfileCompletion.isComplete(complete())).isTrue();
        assertThat(ProfileCompletion.isComplete(new StudentProfile())).isFalse();
    }
}
