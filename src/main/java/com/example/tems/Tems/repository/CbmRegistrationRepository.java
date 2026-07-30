package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.CbmRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CbmRegistrationRepository extends JpaRepository<CbmRegistration, Long> {
    Optional<CbmRegistration> findByPhoneNumber(String phoneNumber);
    Optional<CbmRegistration> findByReferenceId(String referenceId);
}