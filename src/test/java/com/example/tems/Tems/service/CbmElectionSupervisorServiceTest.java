package com.example.tems.Tems.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tems.Tems.cbm.CbmElectionFlow;
import com.example.tems.Tems.cbm.CbmElectionFlow.MemberCheck;
import com.example.tems.Tems.cbm.CbmElectionFlow.Supervisor;
import com.example.tems.Tems.client.CbmApiClient;
import com.example.tems.Tems.model.CbmElectionSupervisor;
import com.example.tems.Tems.repository.CbmElectionSupervisorRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CbmElectionSupervisorServiceTest {
    private static final String ME = "07072603735";
    private CbmElectionSupervisorRepository repository;
    private CbmApiClient cbmApiClient;
    private CbmElectionSupervisorService service;

    @BeforeEach void setup() {
        repository = mock(CbmElectionSupervisorRepository.class);
        cbmApiClient = mock(CbmApiClient.class);
        service = new CbmElectionSupervisorService(repository, cbmApiClient);
        // The database gives a new row its id on the first save.
        when(repository.save(any(CbmElectionSupervisor.class))).thenAnswer(invocation -> {
            CbmElectionSupervisor entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(7L);
            return entity;
        });
    }

    private Supervisor draft(String status) {
        return new Supervisor(ME, "Aminu Bello", "CBM-12345", "CONFIRMED", "active", "Kano", "Dala", "Gwale",
            "20/05/03/004", null, status, LocalDateTime.of(2026, 11, 20, 9, 30));
    }

    @Test void referenceIsBuiltFromTheRowIdAndWrittenBackInTheSameCall() {
        assertEquals("CBM-SV1007", service.save(draft(CbmElectionFlow.STATUS_PENDING)));
        ArgumentCaptor<CbmElectionSupervisor> captured = ArgumentCaptor.forClass(CbmElectionSupervisor.class);
        verify(repository, times(2)).save(captured.capture());   // insert, then set the reference
        CbmElectionSupervisor row = captured.getAllValues().get(1);
        assertEquals("CBM-SV1007", row.getReference());
        assertEquals(ME, row.getPhoneNumber());
        assertEquals("Aminu Bello", row.getFullName());
        assertEquals("CBM-12345", row.getMemberId());
        assertEquals("CONFIRMED", row.getMemberCheck());
        assertEquals("active", row.getMemberStatus());
        assertEquals("Kano", row.getState());
        assertEquals("Dala", row.getLga());
        assertEquals("Gwale", row.getWard());
        assertEquals("20/05/03/004", row.getPuCode());
        assertEquals("PENDING", row.getStatus());
        assertNotNull(row.getCreatedAt());
    }

    @Test void aRegistrationWithNoStatusIsSavedAsPending() {
        service.save(draft(null));
        ArgumentCaptor<CbmElectionSupervisor> captured = ArgumentCaptor.forClass(CbmElectionSupervisor.class);
        verify(repository, times(2)).save(captured.capture());
        assertEquals("PENDING", captured.getAllValues().get(1).getStatus());
    }

    @Test void aSupervisorCanBeFoundByPhoneNumber() {
        CbmElectionSupervisor row = new CbmElectionSupervisor();
        row.setPhoneNumber(ME);
        row.setReference("CBM-SV1002");
        row.setFullName("Aminu Bello");
        row.setMemberId("CBM-12345");
        row.setMemberCheck("NOT_CONFIRMED");
        row.setState("Kano");
        row.setLga("Dala");
        row.setWard("Gwale");
        row.setPuCode("20/05/03/004");
        row.setStatus("AUTHORISED");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 20, 9, 30));
        when(repository.findByPhoneNumber(ME)).thenReturn(Optional.of(row));
        when(repository.findByPhoneNumber("08031112222")).thenReturn(Optional.empty());

        Supervisor found = service.findByPhone(ME);
        assertNotNull(found);
        assertEquals("CBM-SV1002", found.reference());
        assertEquals("AUTHORISED", found.status());
        assertEquals("NOT_CONFIRMED", found.memberCheck());
        assertEquals("20/05/03/004", found.puCode());
        assertNull(service.findByPhone("08031112222"));
        assertNull(service.findByPhone(null));
        verify(repository, never()).findByPhoneNumber(null);
    }

    @Test void aMemberCbmKnowsIsConfirmedWithCbmsStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("member_id", "CBM-12345");
        data.put("status", "active");
        when(cbmApiClient.verifyMemberById("CBM-12345")).thenReturn(Optional.of(data));
        MemberCheck check = service.checkMember("CBM-12345");
        assertTrue(check.confirmed());
        assertEquals("active", check.status());
    }

    @Test void aMemberWithNoStatusFromCbmIsStillConfirmed() {
        Map<String, Object> data = new HashMap<>();
        data.put("member_id", "CBM-12345");
        when(cbmApiClient.verifyMemberById("CBM-12345")).thenReturn(Optional.of(data));
        MemberCheck check = service.checkMember("CBM-12345");
        assertTrue(check.confirmed());
        assertNull(check.status());
    }

    @Test void anIdCbmDoesNotKnowIsNotConfirmed() {
        when(cbmApiClient.verifyMemberById("CBM-00000")).thenReturn(Optional.empty());
        MemberCheck check = service.checkMember("CBM-00000");
        assertFalse(check.confirmed());
        assertNull(check.status());
    }

    @Test void aCbmLookupThatThrowsIsNotConfirmedAndDoesNotEscape() {
        when(cbmApiClient.verifyMemberById("CBM-12345")).thenThrow(new IllegalStateException("cbm down"));
        MemberCheck check = service.checkMember("CBM-12345");
        assertFalse(check.confirmed());
    }
}
