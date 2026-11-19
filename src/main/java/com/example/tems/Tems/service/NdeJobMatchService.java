package com.example.tems.Tems.service;

import com.example.tems.Tems.model.NdeJobMatch;
import com.example.tems.Tems.nde.NdeFlow;
import com.example.tems.Tems.repository.NdeJobMatchRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves and looks up NDE job-matching requests for the USSD flow (NdeFlow). Reference numbers are built from the row's
 * id, so they are unique without any extra counter: NDE-JM1001, NDE-W1002, NDE-V1003 ...
 */
@Service
public class NdeJobMatchService implements NdeFlow.MatchStore {
    private final NdeJobMatchRepository repository;

    @Autowired
    public NdeJobMatchService(NdeJobMatchRepository repository) {
        this.repository = repository;
    }

    /** Saves the request and returns its reference. Both writes happen in one transaction. */
    @Override
    @Transactional
    public String save(NdeFlow.JobMatch m) {
        NdeJobMatch entity = new NdeJobMatch();
        entity.setMatchType(m.type());
        entity.setPhoneNumber(m.phone());
        entity.setEmployerReference(m.employerReference());
        entity.setCategory(m.category());
        entity.setState(m.state());
        entity.setSkill(m.skill());
        entity.setQuantity(m.quantity());
        entity.setStatus(m.status());
        entity.setCreatedAt(m.createdAt());

        NdeJobMatch saved = repository.save(entity);
        String reference = prefixFor(m.type()) + (1000 + saved.getId());
        saved.setReference(reference);
        repository.save(saved);
        return reference;
    }

    /** The request with this reference, only if it was made from this phone number. */
    @Override
    @Transactional(readOnly = true)
    public NdeFlow.JobMatch findForPhone(String reference, String phone) {
        Optional<NdeJobMatch> found = repository.findByReference(reference);
        if (found.isEmpty()) return null;
        NdeJobMatch e = found.get();
        if (phone == null || !phone.equals(e.getPhoneNumber())) return null; // never reveal another person's record
        return new NdeFlow.JobMatch(e.getMatchType(), e.getPhoneNumber(), e.getEmployerReference(), e.getCategory(),
            e.getState(), e.getSkill(), e.getQuantity(), e.getReference(), e.getStatus(), e.getCreatedAt());
    }

    static String prefixFor(String type) {
        switch (type) {
            case NdeFlow.FIND_EMPLOYMENT: return "NDE-JM";
            case NdeFlow.FIND_WORKERS:    return "NDE-W";
            case NdeFlow.VACANCY:         return "NDE-V";
            default: throw new IllegalArgumentException("Unknown NDE job-matching type");
        }
    }
}