package com.example.tems.Tems.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.tems.Tems.model.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    // find organization by name
    List<Organization> findByNameContainingIgnoreCase(String name);

    // search by initals or partial search of organisation names
    
    @Query("SELECT org FROM Organization org " +
        "WHERE LOWER(org.name) LIKE LOWER(CONCAT('%', :name, '%')) " +
        "   OR LOWER(org.initials) LIKE LOWER(CONCAT('%', :name, '%')) " +
        "GROUP BY org.id " +
        "ORDER BY " +
        "  MIN(CASE WHEN LOWER(org.initials) = LOWER(:name) THEN 1 " +
        "           WHEN LOWER(org.name) = LOWER(:name) THEN 2 " +
        "           ELSE 3 END), " +
        "  MIN(org.name) ASC")
    Page<Organization> searchByNameOrInitialsContainingIgnoreCase(@Param("name") String name, Pageable nextPage);
}
