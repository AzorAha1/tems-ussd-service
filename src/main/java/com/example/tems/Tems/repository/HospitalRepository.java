package com.example.tems.Tems.repository;

import com.example.tems.Tems.model.Hospital;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, Long> {
    
    @Query("SELECT h FROM Hospital h WHERE h.isActive = true AND " +
           "(UPPER(h.name) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
           "UPPER(h.location) LIKE UPPER(CONCAT('%', :searchTerm, '%')) OR " +
           "UPPER(h.codeNo) LIKE UPPER(CONCAT('%', :searchTerm, '%')))")
    Page<Hospital> searchActiveHospitals(@Param("searchTerm") String searchTerm, Pageable pageable);
    
    @Query("SELECT h FROM Hospital h WHERE h.isActive = true")
    Page<Hospital> findAllActive(Pageable pageable);
    
    @Query("SELECT h FROM Hospital h WHERE h.isActive = true AND " +
           "UPPER(h.location) LIKE UPPER(CONCAT('%', :location, '%'))")
    Page<Hospital> findByLocationContainingIgnoreCase(@Param("location") String location, Pageable pageable);
}