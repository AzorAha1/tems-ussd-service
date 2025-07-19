package com.example.tems.Tems.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity

public class FhisEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Common fields
    private String phoneNumber;
    private String enrollmentType; // formal or informal
    private String currentStep; // "personal", "social", "corporate", "completed"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
    // Personal Data (Stage 1)
    private String fhisNo;
    private String title;
    private String surname;
    private String firstName;
    private String middleName;
    private String dateOfBirth;

    // Social Data (Stage 2) - nullable for now
    private String maritalStatus;
    private String email;
    private String bloodGroup;
    private String residentialAddress;
    private String occupation;

    // Corporate Data (Stage 3) - nullable for now
    private String ninNumber;
    private String telephoneNumber;
    private String organizationName;

    public FhisEnrollment() {}

    public FhisEnrollment(String phoneNumber, String enrollmentType, String currentStep, LocalDateTime createdAt, LocalDateTime updatedAt,
                          String fhisNo, String title, String surname, String firstName, String middleName, String dateOfBirth,
                          String maritalStatus, String email, String bloodGroup, String residentialAddress, String occupation,
                          String ninNumber, String telephoneNumber, String organizationName) {
        this.phoneNumber = phoneNumber;
        this.enrollmentType = enrollmentType;
        this.currentStep = currentStep;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.fhisNo = fhisNo;
        this.title = title;
        this.surname = surname;
        this.firstName = firstName;
        this.middleName = middleName;
        this.dateOfBirth = dateOfBirth;
        this.maritalStatus = maritalStatus;
        this.email = email;
        this.bloodGroup = bloodGroup;
        this.residentialAddress = residentialAddress;
        this.occupation = occupation;
        this.ninNumber = ninNumber;
        this.telephoneNumber = telephoneNumber;
        this.organizationName = organizationName;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getEnrollmentType() {
        return enrollmentType;
    }
    public void setEnrollmentType(String enrollmentType) {
        this.enrollmentType = enrollmentType;
    }
    public String getCurrentStep() {
        return currentStep;
    }
    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    public String getFhisNo() {
        return fhisNo;
    }
    public void setFhisNo(String fhisNo) {
        this.fhisNo = fhisNo;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }
    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getMiddleName() {
        return middleName;
    }
    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }
    public String getDateOfBirth() {
        return dateOfBirth;
    }
    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
    public String getMaritalStatus() {
        return maritalStatus;
    }
    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getBloodGroup() {
        return bloodGroup;
    }
    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }
    public String getResidentialAddress() {
        return residentialAddress;
    }
    public void setResidentialAddress(String residentialAddress) {
        this.residentialAddress = residentialAddress;
    }
    public String getOccupation() {
        return occupation;
    }
    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }
    public String getNinNumber() {
        return ninNumber;
    }
    public void setNinNumber(String ninNumber) {
        this.ninNumber = ninNumber;
    }
    public String getTelephoneNumber() {
        return telephoneNumber;
    }
    public void setTelephoneNumber(String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }
    public String getOrganizationName() {
        return organizationName;
    }
    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }
}
