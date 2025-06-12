package com.example.tems.Tems.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.tems.Tems.model.Organization;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    // find organization by name
    Optional<Organization> findByNameIgnoreCase(String name);
    
}
