package com.example.tems.Tems.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "cbm_registrations")
public class CbmRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(unique = true)
    private String referenceId;

    private String firstName;
    private String lastName;
    private String email;
    private String vin;
    private String gender;
    private String orgName;
    private String supportType;
    private String spread;
    private String referral;

    private LocalDateTime createdAt;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

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