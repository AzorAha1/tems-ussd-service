package com.example.tems.Tems.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "fhis_enrollment") // Explicitly specify table name
public class InformalFhisEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Common fields
    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;
    
    @Column(name = "enrollment_type")
    private String enrollmentType; // formal or informal
    
    @Column(name = "current_step")
    private String currentStep; // "personal", "social", "corporate", "completed"
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Personal Data (Stage 1)
    @Column(name = "fhis_no")
    private String fhisNo;
    
    @Column(name = "title")
    private String title;
    
    @Column(name = "surname")
    private String surname;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "middle_name")
    private String middleName;
    
    @Column(name = "date_of_birth")
    private String dateOfBirth;

    // Social Data (Stage 2) - nullable for now
    @Column(name = "marital_status")
    private String maritalStatus;
    
    @Column(name = "email")
    private String email;
    
    @Column(name = "blood_group")
    private String bloodGroup;
    
    @Column(name = "residential_address", columnDefinition = "TEXT")
    private String residentialAddress;
    
    @Column(name = "occupation")
    private String occupation;

    // Corporate Data (Stage 3) - nullable for now
    @Column(name = "nin_number")
    private String ninNumber;
    
    @Column(name = "telephone_number")
    private String telephoneNumber;
    
    @Column(name = "organization_name")
    private String organizationName;

    // Add this method for better debugging
    @PrePersist
    protected void onCreate() {
        System.out.println("=== PRE-PERSIST: About to save FhisEnrollment ===");
        System.out.println("Phone: " + phoneNumber);
        System.out.println("FHIS: " + fhisNo);
        System.out.println("Name: " + title + " " + firstName + " " + surname);
        System.out.println("Email: " + email);
        System.out.println("=== END PRE-PERSIST ===");
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        System.out.println("=== PRE-UPDATE: About to update FhisEnrollment ID: " + id + " ===");
        System.out.println("Phone: " + phoneNumber);
        System.out.println("FHIS: " + fhisNo);
        System.out.println("Name: " + title + " " + firstName + " " + surname);
        System.out.println("Email: " + email);
        System.out.println("Telephone: " + telephoneNumber);
        System.out.println("=== END PRE-UPDATE ===");
    }

    // Default constructor
    public InformalFhisEnrollment() {}

    // Full constructor (keep your existing one)
    public InformalFhisEnrollment(String phoneNumber, String enrollmentType, String currentStep, LocalDateTime createdAt, LocalDateTime updatedAt,
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

    // All your existing getters and setters (keeping them exactly the same)
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

    // Add toString method for debugging
    @Override
    public String toString() {
        return "FhisEnrollment{" +
                "id=" + id +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", enrollmentType='" + enrollmentType + '\'' +
                ", currentStep='" + currentStep + '\'' +
                ", fhisNo='" + fhisNo + '\'' +
                ", title='" + title + '\'' +
                ", firstName='" + firstName + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", telephoneNumber='" + telephoneNumber + '\'' +
                '}';
    }
}