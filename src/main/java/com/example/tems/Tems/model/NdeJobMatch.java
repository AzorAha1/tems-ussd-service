package com.example.tems.Tems.model;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One NDE job-matching request submitted over USSD: a job seeker looking for work, an employer looking for workers,
 * or a job vacancy registered against an employer reference. Which columns are filled depends on the type
 * (for example employer_reference only for vacancies, skill only for the other two).
 */
@Entity
@Table(name = "nde_job_match", indexes = {
    @Index(name = "idx_nde_job_match_phone", columnList = "phone_number")
})
public class NdeJobMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FIND_EMPLOYMENT, FIND_WORKERS or VACANCY. */
    @Column(name = "match_type", nullable = false)
    private String matchType;

    /** The number the request was made from (local format, 07012345678). Used to protect lookups. */
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    /** For example NDE-JM1001, NDE-W1002, NDE-V1003. */
    @Column(name = "reference", unique = true)
    private String reference;

    /** Vacancies only: the NDE-EMP... reference of the employer the vacancy belongs to. */
    @Column(name = "employer_reference")
    private String employerReference;

    @Column(name = "category")
    private String category;

    @Column(name = "state")
    private String state;

    @Column(name = "skill")
    private String skill;

    /** Workers needed (FIND_WORKERS) or number of vacancies (VACANCY). */
    @Column(name = "quantity")
    private Integer quantity;

    /** Submitted, Pending Review, ... */
    @Column(name = "status")
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public NdeJobMatch() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String matchType) { this.matchType = matchType; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getEmployerReference() { return employerReference; }
    public void setEmployerReference(String employerReference) { this.employerReference = employerReference; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}