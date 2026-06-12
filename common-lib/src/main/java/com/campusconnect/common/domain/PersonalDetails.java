package com.campusconnect.common.domain;

/**
 * Embedded personal sub-document of a {@link StudentProfile} (Story 3.1). Only {@code fullName} and
 * {@code phone} are required for profile completion; the rest are optional and the richer set is
 * fleshed out with the Epic 9 form rather than speculatively now.
 */
public class PersonalDetails {

    private String fullName;
    private String phone;
    private String gender;
    private String dateOfBirth;
    private String address;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
