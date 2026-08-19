package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.CacRegistration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CacRegistrationRepository extends JpaRepository<CacRegistration, Long> {
    Optional<CacRegistration> findByPhoneNumber(String phoneNumber);
    Optional<CacRegistration> findByReferenceId(String referenceId);
    Optional<CacRegistration> findByRcNumber(String rcNumber);
    Page<CacRegistration> findByBusinessNameContainingIgnoreCase(String businessName, Pageable pageable);
    List<CacRegistration> findAllByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}