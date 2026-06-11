package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.FfsRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FfsRegistrationRepository extends JpaRepository<FfsRegistration, Long> {
    Optional<FfsRegistration> findByPhoneNumber(String phoneNumber);
    Optional<FfsRegistration> findByReferenceId(String referenceId);
}