package com.example.tems.Tems.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

// FORMAL SECTOR
// PERSONAL DATA
// 	•	FHIS No
// 	•	Surname 
// 	•	First name
// 	•	middle name
// 	•	date of birth: generates a unique ()
// 	•	sex
// 	•	blood group
// PROFESSIONAL/CORPERATE DATA
// 	•	designation
// 	•	Occupation/profession
// 	•	Present station
// 	•	Rank
// 	•	PF number
// 	•	Name of SDA: secretariat/agency/dept.
// SOCIAL DATA
// 	•	marital status
// 	•	telephone number
// 	•	residential address
// 	•	email
// ENROLLEE’S DEPENDANTS DATA
//  (One spouse and four biological children)
// 	•	Spouse :first name, sex, blood group, date of Birth
// 	•	Child 1: first name, sex, blood group, date of Birth
// 	•	Child 3: first name, sex, blood group, date of Birth
// 	•	Child 4: first name, sex, blood group, date of Birth
// HEALTH CARE PROVIDER’S DATA
// 	•	Name of Hospital
// 	•	Hospital location
// 	•	Hospital code No (office use only)
@Entity
@Table(name = "formal_fhis_enrollment")
public class FormalFhisEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
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

    // Professional/Corporate Data (Stage 2)
    @Column(name = "designation")
    private String designation;
    @Column(name = "occupation")
    private String occupation; // Occupation/profession
    @Column(name = "present_station")
    private String presentStation; // Present station
    @Column(name = "rank")
    private String rank; // Rank
    @Column(name = "pf_number")
    private String pfNumber; // PF number
    @Column(name = "sda_name")
    private String sdaName; // Name of SDA: secretariat/agency/dept.

    // Social Data (Stage 3)
    @Column(name = "marital_status")
    private String maritalStatus;
    @Column(name = "telephone_number")
    private String telephoneNumber; // Telephone number
    @Column(name = "residential_address", columnDefinition = "TEXT")
    private String residentialAddress; // Residential address
    @Column(name = "email")
    private String email; // Email

    // Enrollee's Dependants Data (Stage 4)

    //start with spouse firstname, sex, blood group and date of birth
    @Column(name = "spouse_first_name")
    private String spouseFirstName; // Spouse's first name
    @Column(name = "spouse_sex")
    private String spouseSex;
    @Column(name = "spouse_blood_group")
    private String spouseBloodGroup; // Spouse's blood group
    @Column(name = "spouse_date_of_birth")
    private String spouseDateOfBirth; // Spouse's date of birth

    // Then add children data  child1 - child4 should have the same data as spouse
    @Column(name = "child1_first_name")
    private String child1FirstName; // Child 1's first name
    @Column(name = "child1_sex")
    private String child1Sex;
    @Column(name = "child1_blood_group")
    private String child1BloodGroup; // Child 1's blood group
    @Column(name = "child1_date_of_birth")
    private String child1DateOfBirth; // Child 1's date of birth

    @Column(name = "child2_first_name")
    private String child2FirstName; // Child 2's first name
    @Column(name = "child2_sex")
    private String child2Sex;
    @Column(name = "child2_blood_group")
    private String child2BloodGroup; // Child 2's blood group
    @Column(name = "child2_date_of_birth")
    private String child2DateOfBirth; // Child 2's date of birth

    @Column(name = "child3_first_name")
    private String child3FirstName; // Child 3's first name
    @Column(name = "child3_sex")
    private String child3Sex;
    @Column(name = "child3_blood_group")
    private String child3BloodGroup; // Child 3's blood group
    @Column(name = "child3_date_of_birth")
    private String child3DateOfBirth; // Child 3's date of birth

    @Column(name = "child4_first_name")
    private String child4FirstName; // Child 4's first name
    @Column(name = "child4_sex")
    private String child4Sex;
    @Column(name = "child4_blood_group")
    private String child4BloodGroup; // Child 4's blood group
    @Column(name = "child4_date_of_birth")
    private String child4DateOfBirth; // Child 4's date of birth

    // Health Care Provider's Data (Stage 5)
    @Column(name = "hospital_name")
    private String hospitalName; // Name of Hospital
    @Column(name = "hospital_location")
    private String hospitalLocation; // Hospital location
    @Column(name = "hospital_code_no")
    private String hospitalCodeNo; // Hospital code No (office use only)

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

    public String getSex() {
        return sex;
    }
    
    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }
    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }
    public String getDesignation() {
        return designation;
    }
    public void setDesignation(String designation) {
        this.designation = designation;
    }
    public String getOccupation() {
        return occupation;
    }
    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }
    public String getPresentStation() {
        return presentStation;
    }
    public void setPresentStation(String presentStation) {
        this.presentStation = presentStation;
    }
    public String getRank() {
        return rank;
    }
    public void setRank(String rank) {
        this.rank = rank;
    }
    public String getPfNumber() {
        return pfNumber;
    }
    public void setPfNumber(String pfNumber) {
        this.pfNumber = pfNumber;
    }
    public String getSdaName() {
        return sdaName;
    }
    public void setSdaName(String sdaName) {
        this.sdaName = sdaName;
    }
    public String getMaritalStatus() {
        return maritalStatus;
    }
    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }
    public String getTelephoneNumber() {
        return telephoneNumber;
    }
    public void setTelephoneNumber(String telephoneNumber) {
        this.telephoneNumber = telephoneNumber;
    }
    public String getResidentialAddress() {
        return residentialAddress;
    }
    public void setResidentialAddress(String residentialAddress) {
        this.residentialAddress = residentialAddress;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getSpouseFirstName() {
        return spouseFirstName;
    }
    public void setSpouseFirstName(String spouseFirstName) {
        this.spouseFirstName = spouseFirstName;
    }
    public String getSpouseSex() {
        return spouseSex;
    }
    public void setSpouseSex(String spouseSex) {
        this.spouseSex = spouseSex;
    }
    public String getSpouseBloodGroup() {
        return spouseBloodGroup;
    }
    public void setSpouseBloodGroup(String spouseBloodGroup) {
        this.spouseBloodGroup = spouseBloodGroup;
    }
    public String getSpouseDateOfBirth() {
        return spouseDateOfBirth;
    }
    public void setSpouseDateOfBirth(String spouseDateOfBirth) {
        this.spouseDateOfBirth = spouseDateOfBirth;
    }
    public String getChild1FirstName() {
        return child1FirstName;
    }
    public void setChild1FirstName(String child1FirstName) {
        this.child1FirstName = child1FirstName;
    }
    public String getChild1Sex() {
        return child1Sex;
    }
    public void setChild1Sex(String child1Sex) {
        this.child1Sex = child1Sex;
    }
    public String getChild1BloodGroup() {
        return child1BloodGroup;
    }
    public void setChild1BloodGroup(String child1BloodGroup) {
        this.child1BloodGroup = child1BloodGroup;
    }
    public String getChild1DateOfBirth() {
        return child1DateOfBirth;
    }
    public void setChild1DateOfBirth(String child1DateOfBirth) {
        this.child1DateOfBirth = child1DateOfBirth;
    }
    public String getChild2FirstName() {
        return child2FirstName;
    }
    public void setChild2FirstName(String child2FirstName) {
        this.child2FirstName = child2FirstName;
    }
    public String getChild2Sex() {
        return child2Sex;
    }

    public void setChild2Sex(String child2Sex) {
        this.child2Sex = child2Sex;
    }

    public String getChild2BloodGroup() {
        return child2BloodGroup;
    }
    public void setChild2BloodGroup(String child2BloodGroup) {
        this.child2BloodGroup = child2BloodGroup;
    }
    public String getChild2DateOfBirth() {
        return child2DateOfBirth;
    }
    public void setChild2DateOfBirth(String child2DateOfBirth) {
        this.child2DateOfBirth = child2DateOfBirth;
    }

    public String getChild3FirstName() {
        return child3FirstName;
    }
    public void setChild3FirstName(String child3FirstName) {
        this.child3FirstName = child3FirstName;
    }
    public String getChild3Sex() {
        return child3Sex;
    }
    public void setChild3Sex(String child3Sex) {
        this.child3Sex = child3Sex;
    }
    public String getChild3BloodGroup() {
        return child3BloodGroup;
    }
    public void setChild3BloodGroup(String child3BloodGroup) {
        this.child3BloodGroup = child3BloodGroup;
    }
    public String getChild3DateOfBirth() {
        return child3DateOfBirth;
    }
    public void setChild3DateOfBirth(String child3DateOfBirth) {
        this.child3DateOfBirth = child3DateOfBirth;
    }
    public String getChild4FirstName() {
        return child4FirstName;
    }
    public void setChild4FirstName(String child4FirstName) {
        this.child4FirstName = child4FirstName;
    }
    public String getChild4Sex() {
        return child4Sex;
    }
    public void setChild4Sex(String child4Sex) {
        this.child4Sex = child4Sex;
    }
    public String getChild4BloodGroup() {
        return child4BloodGroup;
    }
    public void setChild4BloodGroup(String child4BloodGroup) {
        this.child4BloodGroup = child4BloodGroup;
    }
    public String getChild4DateOfBirth() {
        return child4DateOfBirth;
    }
    public void setChild4DateOfBirth(String child4DateOfBirth) {
        this.child4DateOfBirth = child4DateOfBirth;
    }
    public String getHospitalName() {
        return hospitalName;
    }
    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }
    public String getHospitalLocation() {
        return hospitalLocation;
    }
    public void setHospitalLocation(String hospitalLocation) {
        this.hospitalLocation = hospitalLocation;
    }
    public String getHospitalCodeNo() {
        return hospitalCodeNo;
    }
    public void setHospitalCodeNo(String hospitalCodeNo) {
        this.hospitalCodeNo = hospitalCodeNo;
    }
    public FormalFhisEnrollment() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currentStep = "personal"; // Start with personal data step
    }
    public FormalFhisEnrollment(
        String phoneNumber, String enrollmentType, String currentStep, String fhisNo,
        String surname, String firstname, String middlename, String dateofBirth, String sex, String bloodGroup,
        String designation, String occupation, String presentStation, String rank, String pfNumber, String sdaName,
        String maritalStatus, String telephoneNumber, String residentialAddress, String email,
        String spouseFirstName, String spouseSex, String spouseBloodGroup, String spouseDateOfBirth,
        String child1FirstName, String child1Sex, String child1BloodGroup, String child1DateOfBirth,
        String child2FirstName, String child2Sex, String child2BloodGroup, String child2DateOfBirth,
        String child3FirstName, String child3Sex, String child3BloodGroup, String child3DateOfBirth,
        String child4FirstName, String child4Sex, String child4BloodGroup, String child4DateOfBirth,
        String hospitalName, String hospitalLocation, String hospitalCodeNo
    ) 
    {
        this.phoneNumber = phoneNumber;
        this.enrollmentType = enrollmentType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.currentStep = currentStep;
        this.fhisNo = fhisNo;
        this.surname = surname;
        this.firstName = firstname;
        this.middleName = middlename;
        this.dateOfBirth = dateofBirth;
        this.sex = sex;
        this.bloodGroup = bloodGroup;
        this.designation = designation;
        this.occupation = occupation;
        this.presentStation = presentStation;
        this.rank = rank;
        this.pfNumber = pfNumber;
        this.sdaName = sdaName;
        this.maritalStatus = maritalStatus;
        this.telephoneNumber = telephoneNumber;
        this.residentialAddress = residentialAddress;
        this.email = email;
        this.spouseFirstName = spouseFirstName;
        this.spouseSex = spouseSex;
        this.spouseBloodGroup = spouseBloodGroup;
        this.spouseDateOfBirth = spouseDateOfBirth;
        this.child1FirstName = child1FirstName;
        this.child1Sex = child1Sex;
        this.child1BloodGroup = child1BloodGroup;
        this.child1DateOfBirth = child1DateOfBirth;
        this.child2FirstName = child2FirstName;
        this.child2Sex = child2Sex;
        this.child2BloodGroup = child2BloodGroup;
        this.child2DateOfBirth = child2DateOfBirth;
        this.child3FirstName = child3FirstName;
        this.child3Sex = child3Sex;
        this.child3BloodGroup = child3BloodGroup;
        this.child3DateOfBirth = child3DateOfBirth;
        this.child4FirstName = child4FirstName;
        this.child4Sex = child4Sex;
        this.child4BloodGroup = child4BloodGroup;
        this.child4DateOfBirth = child4DateOfBirth;
        this.hospitalName = hospitalName;
        this.hospitalLocation = hospitalLocation;
        this.hospitalCodeNo = hospitalCodeNo;
    }
}

