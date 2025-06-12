package com.example.tems.Tems.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Organization {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @Column(length = 1000)
    public String contactAddress;

    @Column(length = 2000)
    public String description;

    public String contactTelephone;

    public Organization() {}

    public Organization(String name, String contactAddress, String description, String contactTelephone) {
        this.name = name;
        this.contactAddress = contactAddress;
        this.description = description;
        this.contactTelephone = contactTelephone;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getContactAddress() {
        return contactAddress;
    }
    public void setContactAddress(String contactAddress) {
        this.contactAddress = contactAddress;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getContactTelephone() {
        return contactTelephone;
    }
    public void setContactTelephone(String contactTelephone) {
        this.contactTelephone = contactTelephone;
    }
}
