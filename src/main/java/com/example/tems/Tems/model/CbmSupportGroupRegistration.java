package com.example.tems.Tems.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "cbm_support_group_registrations")
public class CbmSupportGroupRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phoneNumber;
    private String referenceId;
    private String orgName;
    private String supportType;
    private String spread;
    private String referral;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getSupportType() { return supportType; }
    public void setSupportType(String supportType) { this.supportType = supportType; }
    public String getSpread() { return spread; }
    public void setSpread(String spread) { this.spread = spread; }
    public String getReferral() { return referral; }
    public void setReferral(String referral) { this.referral = referral; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}