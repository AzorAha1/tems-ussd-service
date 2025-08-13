package com.example.tems.Tems.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.tems.Tems.model.FhisEnrollment;

public interface FhisEnrollmentRepository extends JpaRepository<FhisEnrollment, Long> {
    // // find by phone number
    Optional<FhisEnrollment> findByPhoneNumber(String phoneNumber);

    
}
