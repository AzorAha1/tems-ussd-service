package com.example.tems.Tems.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String phoneNumber;
    private String status; // Pending, active or expired
    private LocalDateTime createdat;
    private LocalDateTime expiresat;

    // getter and setters
    public Long getID() {
        return id;
    }
    public void setID(Long id) {
        this.id = id;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public LocalDateTime getCreatedat() {
        return createdat;
    }
    public void setCreatedat(LocalDateTime createdat) {
        this.createdat = createdat;
    }
    public LocalDateTime getExpiresat() {
        return expiresat;
    }
    public void setExpiresat(LocalDateTime expiresat) {
        this.expiresat = expiresat;
    }
    public Subscription() {}

    public Subscription(String phoneNumber, String status, LocalDateTime createdat, LocalDateTime expiresat) {
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.createdat = createdat;
        this.expiresat = expiresat;
    }
}
