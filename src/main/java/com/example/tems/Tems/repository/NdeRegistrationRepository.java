package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.NdeRegistration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NdeRegistrationRepository extends JpaRepository<NdeRegistration, Long> {
    Optional<NdeRegistration> findByReference(String reference);
}