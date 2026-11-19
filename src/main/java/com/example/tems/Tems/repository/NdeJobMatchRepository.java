package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.NdeJobMatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NdeJobMatchRepository extends JpaRepository<NdeJobMatch, Long> {
    Optional<NdeJobMatch> findByReference(String reference);
}