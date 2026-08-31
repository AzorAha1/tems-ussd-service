package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.NinRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface NinRecordRepository extends JpaRepository<NinRecord, Long> {
    Optional<NinRecord> findByNin(String nin);
}