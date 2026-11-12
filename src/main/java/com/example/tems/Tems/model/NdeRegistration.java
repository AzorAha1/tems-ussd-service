package com.example.tems.Tems.model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One NDE registration submitted over USSD: a job seeker, an employer, or a skills-training request.
 * Which columns are filled depends on the type (for example contact_phone and business_sector only for employers).
 */
@Entity
@Table(name = "nde_registration", indexes = {
    @Index(name = "idx_nde_registration_phone", columnList = "phone_number")
})
public class NdeRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JOB_SEEKER, EMPLOYER or TRAINING. */
    @Column(name = "registration_type", nullable = false)
    private String registrationType;

    /** The number the registration was made from (local format, 07012345678). Used to protect lookups. */
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    /** For example NDE1001, NDE-EMP1002, NDE-ST1003. */
    @Column(name = "reference", unique = true)
    private String reference;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "organization_name")
    private String organizationName;

    @Column(name = "state")
    private String state;

    @Column(name = "lga")
    private String lga;

    @Column(name = "qualification")
    private String qualification;

    @Column(name = "employment_category")
    private String employmentCategory;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "business_sector")
    private String businessSector;

    @Column(name = "preferred_trade")
    private String preferredTrade;

    /** Submitted, Awaiting verification, ... */
    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public NdeRegistration() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistrationType() { return registrationType; }
    public void setRegistrationType(String registrationType) { this.registrationType = registrationType; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLga() { return lga; }
    public void setLga(String lga) { this.lga = lga; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public String getEmploymentCategory() { return employmentCategory; }
    public void setEmploymentCategory(String employmentCategory) { this.employmentCategory = employmentCategory; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getBusinessSector() { return businessSector; }
    public void setBusinessSector(String businessSector) { this.businessSector = businessSector; }
    public String getPreferredTrade() { return preferredTrade; }
    public void setPreferredTrade(String preferredTrade) { this.preferredTrade = preferredTrade; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}