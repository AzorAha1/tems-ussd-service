package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.CbmElectionSupervisor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CbmElectionSupervisorRepository extends JpaRepository<CbmElectionSupervisor, Long> {
    Optional<CbmElectionSupervisor> findByPhoneNumber(String phoneNumber);
}
