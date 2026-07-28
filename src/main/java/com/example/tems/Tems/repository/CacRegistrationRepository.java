package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.CacRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CacRegistrationRepository extends JpaRepository<CacRegistration, Long> {
    Optional<CacRegistration> findByPhoneNumber(String phoneNumber);
    Optional<CacRegistration> findByReferenceId(String referenceId);
}