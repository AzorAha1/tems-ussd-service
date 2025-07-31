package com.example.tems.Tems.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.tems.Tems.model.FormalFhisEnrollment;

public interface FormalFhisEnrollmentRepository extends JpaRepository<FormalFhisEnrollment, Long> {
    // // find by phone number
    Optional<FormalFhisEnrollment> findByPhoneNumber(String phoneNumber);

    
}
