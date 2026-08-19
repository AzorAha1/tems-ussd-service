package com.example.tems.Tems.model;


import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "cac_registration")
public class CacRegistration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "registration_type")
    private String registrationType;

    @Column(name = "reference_id", unique = true)
    private String referenceId;

    // adding business name
    @Column(name = "business_name")
    private String businessName;

    // adding rc_number
    @Column(name = "rc_number")
    private String rcNumber;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;
    @Column(name = "status")
    private String status;

    @Column(name = "state")
    private String state;

    @Column(name = "occupation")
    private String occupation;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public CacRegistration() {}

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getRcNumber() { return rcNumber; }
    public void setRcNumber(String rcNumber) { this.rcNumber = rcNumber; }

    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber; 
    }
     public String getRegistrationType() { return registrationType; }
    public void setRegistrationType(String registrationType) { this.registrationType = registrationType; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
