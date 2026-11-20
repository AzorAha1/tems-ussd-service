package com.example.tems.Tems.service;

import com.example.tems.Tems.cbm.CbmElectionFlow;
import com.example.tems.Tems.client.CbmApiClient;
import com.example.tems.Tems.model.CbmElectionSupervisor;
import com.example.tems.Tems.repository.CbmElectionSupervisorRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves and looks up CBM polling-unit supervisors for the USSD flow (CbmElectionFlow), and asks CBM whether a City Boy
 * Registration ID exists. Reference numbers are built from the row's id, so they are unique without any extra counter:
 * CBM-SV1001, CBM-SV1002 ...
 */
@Service
public class CbmElectionSupervisorService implements CbmElectionFlow.Store {
    private final CbmElectionSupervisorRepository repository;
    private final CbmApiClient cbmApiClient;

    @Autowired
    public CbmElectionSupervisorService(CbmElectionSupervisorRepository repository, CbmApiClient cbmApiClient) {
        this.repository = repository;
        this.cbmApiClient = cbmApiClient;
    }

    /** Saves the registration and returns its reference. Both writes happen in one transaction. */
    @Override
    @Transactional
    public String save(CbmElectionFlow.Supervisor s) {
        CbmElectionSupervisor entity = new CbmElectionSupervisor();
        entity.setPhoneNumber(s.phone());
        entity.setFullName(s.fullName());
        entity.setMemberId(s.memberId());
        entity.setMemberCheck(s.memberCheck());
        entity.setMemberStatus(s.memberStatus());
        entity.setState(s.state());
        entity.setLga(s.lga());
        entity.setWard(s.ward());
        entity.setPuCode(s.puCode());
        entity.setStatus(s.status() == null ? CbmElectionFlow.STATUS_PENDING : s.status());
        entity.setCreatedAt(s.createdAt());

        CbmElectionSupervisor saved = repository.save(entity);
        String reference = "CBM-SV" + (1000 + saved.getId());
        saved.setReference(reference);
        repository.save(saved);
        return reference;
    }

    /** The supervisor registered from this phone number, or null. */
    @Override
    @Transactional(readOnly = true)
    public CbmElectionFlow.Supervisor findByPhone(String phone) {
        if (phone == null) return null;
        return repository.findByPhoneNumber(phone).map(CbmElectionSupervisorService::toRecord).orElse(null);
    }

    /**
     * Asks CBM whether the ID exists. CBM's client returns "empty" both for an unknown ID and when CBM cannot be
     * reached, so "not confirmed" can mean either; the flow lets the supervisor continue and CBM check it later.
     */
    @Override
    public CbmElectionFlow.MemberCheck checkMember(String memberId) {
        try {
            Optional<Map<String, Object>> found = cbmApiClient.verifyMemberById(memberId);
            if (found == null || found.isEmpty()) return new CbmElectionFlow.MemberCheck(false, null);
            Object status = found.get().get("status");
            return new CbmElectionFlow.MemberCheck(true, status == null ? null : String.valueOf(status));
        } catch (RuntimeException e) {
            return new CbmElectionFlow.MemberCheck(false, null);
        }
    }

    private static CbmElectionFlow.Supervisor toRecord(CbmElectionSupervisor e) {
        return new CbmElectionFlow.Supervisor(e.getPhoneNumber(), e.getFullName(), e.getMemberId(), e.getMemberCheck(),
            e.getMemberStatus(), e.getState(), e.getLga(), e.getWard(), e.getPuCode(), e.getReference(),
            e.getStatus(), e.getCreatedAt());
    }
}
