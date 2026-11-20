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
 * A City Boy Movement polling-unit supervisor who registered over USSD (Election Results Collection, "Authorised
 * Access Registration"). One row per phone number. A new registration is PENDING; CBM authorises it by setting
 * status to AUTHORISED (or REJECTED) in the database.
 */
@Entity
@Table(name = "cbm_election_supervisor", indexes = {
    @Index(name = "idx_cbm_election_supervisor_member", columnList = "member_id")
})
public class CbmElectionSupervisor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The number the registration was made from (local format, 07012345678). One registration per number. */
    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    /** For example CBM-SV1001. */
    @Column(name = "reference", unique = true)
    private String reference;

    @Column(name = "full_name")
    private String fullName;

    /** The City Boy Registration ID exactly as the supervisor typed it. */
    @Column(name = "member_id")
    private String memberId;

    /** CONFIRMED if CBM's system knew the ID when the supervisor registered, otherwise NOT_CONFIRMED. */
    @Column(name = "member_check")
    private String memberCheck;

    /** CBM's own status text for the member, when CBM gave one. */
    @Column(name = "member_status")
    private String memberStatus;

    @Column(name = "state")
    private String state;

    @Column(name = "lga")
    private String lga;

    @Column(name = "ward")
    private String ward;

    /** Polling unit code in State/LGA/Ward/Unit form, for example 01/02/03/004. */
    @Column(name = "pu_code")
    private String puCode;

    /** PENDING, AUTHORISED or REJECTED. */
    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Set by whoever changes the status (for example when CBM authorises the supervisor). */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CbmElectionSupervisor() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }
    public String getMemberCheck() { return memberCheck; }
    public void setMemberCheck(String memberCheck) { this.memberCheck = memberCheck; }
    public String getMemberStatus() { return memberStatus; }
    public void setMemberStatus(String memberStatus) { this.memberStatus = memberStatus; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLga() { return lga; }
    public void setLga(String lga) { this.lga = lga; }
    public String getWard() { return ward; }
    public void setWard(String ward) { this.ward = ward; }
    public String getPuCode() { return puCode; }
    public void setPuCode(String puCode) { this.puCode = puCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
