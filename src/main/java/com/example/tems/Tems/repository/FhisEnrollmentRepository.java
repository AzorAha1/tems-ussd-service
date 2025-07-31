package com.example.tems.Tems.repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.tems.Tems.model.InformalFhisEnrollment;

public interface FhisEnrollmentRepository extends JpaRepository<InformalFhisEnrollment, Long> {
	// find by phone number
    Optional<InformalFhisEnrollment> findByPhoneNumber(String phoneNumber);
}
