package com.example.tems.Tems.model;

import java.sql.Date;

import jakarta.persistence.*;

@Entity
@Table(name = "hospital")
public class Hospital {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, name = "name")
    private String name;
    
    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "address", length = 1000)
    private String address;


    // maybe you still want to keep code for office use
    @Column(name = "code_no")
    private String codeNo;

    @Column(name = "services", length = 2000)
    private String services;

    @Column(name = "phone_number")
    private String phoneNumber;
    
    // Default constructor (required by JPA)
    public Hospital() {}
    
    // Constructor with parameters
    public Hospital(String name, String location, String codeNo, String address, String services, String phoneNumber) {
        this.name = name;
        this.location = location;
        this.codeNo = codeNo;
        this.address = address;
        this.isActive = true;
        this.phoneNumber = phoneNumber;
        this.services = services;
        
    }
    
    // Getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "is_active")
    private Boolean isActive = true;

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getFullAddress() {
        return (address != null && !address.isEmpty()) ? address + ", " + location : location;
    }

    public String getServices() { return services; }

    public void setServices(String services) { this.services = services; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getCodeNo() { return codeNo; }
    public void setCodeNo(String codeNo) { this.codeNo = codeNo; }

    public String getphoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    @Override
    public String toString() {
        return "Hospital{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", location='" + location + '\'' +
                ", codeNo='" + codeNo + '\'' +
                '}';
    }
}
