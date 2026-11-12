package com.example.tems.Tems.service;

import com.example.tems.Tems.model.NdeRegistration;
import com.example.tems.Tems.nde.NdeFlow;
import com.example.tems.Tems.repository.NdeRegistrationRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves and looks up NDE registrations for the USSD flow (NdeFlow). Reference numbers are built from the row's id,
 * so they are unique without any extra counter: NDE1001, NDE-EMP1002, NDE-ST1003 ...
 */
@Service
public class NdeRegistrationService implements NdeFlow.Store {
    private final NdeRegistrationRepository repository;

    @Autowired
    public NdeRegistrationService(NdeRegistrationRepository repository) {
        this.repository = repository;
    }

    /** Saves the registration and returns its reference. Both writes happen in one transaction. */
    @Override
    @Transactional
    public String save(NdeFlow.Registration r) {
        NdeRegistration entity = new NdeRegistration();
        entity.setRegistrationType(r.type());
        entity.setPhoneNumber(r.phone());
        if (NdeFlow.EMPLOYER.equals(r.type())) {
            entity.setOrganizationName(r.name());
        } else {
            entity.setFullName(r.name());
        }
        entity.setState(r.state());
        entity.setLga(r.lga());
        entity.setQualification(r.qualification());
        entity.setEmploymentCategory(r.employmentCategory());
        entity.setContactPhone(r.contactPhone());
        entity.setBusinessSector(r.businessSector());
        entity.setPreferredTrade(r.preferredTrade());
        entity.setStatus(r.status());
        entity.setCreatedAt(r.createdAt());

        NdeRegistration saved = repository.save(entity);
        String reference = prefixFor(r.type()) + (1000 + saved.getId());
        saved.setReference(reference);
        repository.save(saved);
        return reference;
    }

    /** The registration with this reference, only if it was made from this phone number. */
    @Override
    @Transactional(readOnly = true)
    public NdeFlow.Registration findForPhone(String reference, String phone) {
        Optional<NdeRegistration> found = repository.findByReference(reference);
        if (found.isEmpty()) return null;
        NdeRegistration e = found.get();
        if (phone == null || !phone.equals(e.getPhoneNumber())) return null; // never reveal another person's record
        String name = NdeFlow.EMPLOYER.equals(e.getRegistrationType()) ? e.getOrganizationName() : e.getFullName();
        return new NdeFlow.Registration(e.getRegistrationType(), e.getPhoneNumber(), name, e.getState(), e.getLga(),
            e.getQualification(), e.getEmploymentCategory(), e.getContactPhone(), e.getBusinessSector(),
            e.getPreferredTrade(), e.getReference(), e.getStatus(), e.getCreatedAt());
    }

    static String prefixFor(String type) {
        switch (type) {
            case NdeFlow.JOB_SEEKER: return "NDE";
            case NdeFlow.EMPLOYER:   return "NDE-EMP";
            case NdeFlow.TRAINING:   return "NDE-ST";
            default: throw new IllegalArgumentException("Unknown NDE registration type");
        }
    }
}