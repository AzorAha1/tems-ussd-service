package com.example.tems.Tems.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fhis_enrollment")
public class FhisEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Common fields
    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "enrollment_type")
    private String enrollmentType;

    @Column(name = "current_step")
    private String currentStep;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Personal Data
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

    @Column(name = "sex")
    private String sex;

    @Column(name = "blood_group")
    private String bloodGroup;

    // Professional Data
    @Column(name = "nin_number")
    private String ninNumber;

    @Column(name = "organization_name")
    private String organizationName;

    @Column(name = "designation")
    private String designation;

    @Column(name = "occupation")
    private String occupation;

    @Column(name = "present_station")
    private String presentStation;

    @Column(name = "rank")
    private String rank;

    @Column(name = "pf_number")
    private String pfNumber;

    @Column(name = "sda_name")
    private String sdaName;

    // Social Data
    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "telephone_number")
    private String telephoneNumber;

    @Column(name = "residential_address", columnDefinition = "TEXT")
    private String residentialAddress;

    @Column(name = "email")
    private String email;

    // Dependants
    @Column(name = "number_of_children")
    private Integer numberOfChildren;

    // Healthcare Provider (Relation)
    @ManyToOne
    @JoinColumn(name = "hospital_id", referencedColumnName = "id")
    private Hospital hospital;

    // Constructors
    public FhisEnrollment() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEnrollmentType() { return enrollmentType; }
    public void setEnrollmentType(String enrollmentType) { this.enrollmentType = enrollmentType; }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { this.currentStep = currentStep; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFhisNo() { return fhisNo; }
    public void setFhisNo(String fhisNo) { this.fhisNo = fhisNo; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public String getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(String bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getNinNumber() { return ninNumber; }
    public void setNinNumber(String ninNumber) { this.ninNumber = ninNumber; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public String getPresentStation() { return presentStation; }
    public void setPresentStation(String presentStation) { this.presentStation = presentStation; }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    public String getPfNumber() { return pfNumber; }
    public void setPfNumber(String pfNumber) { this.pfNumber = pfNumber; }

    public String getSdaName() { return sdaName; }
    public void setSdaName(String sdaName) { this.sdaName = sdaName; }

    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }

    public String getTelephoneNumber() { return telephoneNumber; }
    public void setTelephoneNumber(String telephoneNumber) { this.telephoneNumber = telephoneNumber; }

    public String getResidentialAddress() { return residentialAddress; }
    public void setResidentialAddress(String residentialAddress) { this.residentialAddress = residentialAddress; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getNumberOfChildren() { return numberOfChildren; }
    public void setNumberOfChildren(Integer numberOfChildren) { this.numberOfChildren = numberOfChildren; }

    public Hospital getHospital() { return hospital; }
    public void setHospital(Hospital hospital) { this.hospital = hospital; }

    @Override
    public String toString() {
        return "FhisEnrollment{" +
                "id=" + id +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", enrollmentType='" + enrollmentType + '\'' +
                ", currentStep='" + currentStep + '\'' +
                ", fhisNo='" + fhisNo + '\'' +
                ", firstName='" + firstName + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", numberOfChildren=" + numberOfChildren +
                ", hospital=" + (hospital != null ? hospital.getName() : "null") +
                '}';
    }
}
