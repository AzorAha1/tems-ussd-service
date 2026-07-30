package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.CbmSupportGroupRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CbmSupportGroupRegistrationRepository extends JpaRepository<CbmSupportGroupRegistration, Long> {
}