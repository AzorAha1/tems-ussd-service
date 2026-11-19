package com.example.tems.Tems.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tems.Tems.model.NdeJobMatch;
import com.example.tems.Tems.nde.NdeFlow;
import com.example.tems.Tems.nde.NdeFlow.JobMatch;
import com.example.tems.Tems.repository.NdeJobMatchRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NdeJobMatchServiceTest {
    private static final String ME = "07072603735";
    private NdeJobMatchRepository repository;
    private NdeJobMatchService service;

    @BeforeEach void setup() {
        repository = mock(NdeJobMatchRepository.class);
        service = new NdeJobMatchService(repository);
        // The database gives a new row its id on the first save.
        when(repository.save(any(NdeJobMatch.class))).thenAnswer(invocation -> {
            NdeJobMatch entity = invocation.getArgument(0);
            if (entity.getId() == null) entity.setId(7L);
            return entity;
        });
    }

    private JobMatch draft(String type) {
        boolean vacancy = NdeFlow.VACANCY.equals(type);
        boolean workers = NdeFlow.FIND_WORKERS.equals(type);
        return new JobMatch(type, ME, vacancy ? "NDE-EMP1002" : null, "Skilled / Technical", "Kano",
            vacancy ? null : "Welding", (vacancy || workers) ? 5 : null, null,
            vacancy ? "Pending Review" : "Submitted", LocalDateTime.of(2026, 11, 19, 2, 30));
    }

    @Test void referenceIsBuiltFromTheRowIdAndWrittenBackInTheSameCall() {
        assertEquals("NDE-JM1007", service.save(draft(NdeFlow.FIND_EMPLOYMENT)));
        ArgumentCaptor<NdeJobMatch> captured = ArgumentCaptor.forClass(NdeJobMatch.class);
        verify(repository, times(2)).save(captured.capture());   // insert, then set the reference
        NdeJobMatch row = captured.getAllValues().get(1);
        assertEquals("NDE-JM1007", row.getReference());
        assertEquals("Welding", row.getSkill());
        assertEquals("Kano", row.getState());
        assertEquals("Skilled / Technical", row.getCategory());
        assertEquals(ME, row.getPhoneNumber());
        assertEquals("Submitted", row.getStatus());
        assertNull(row.getQuantity());
        assertNull(row.getEmployerReference());
    }

    @Test void eachTypeHasItsOwnPrefixAndKeepsItsOwnColumns() {
        assertEquals("NDE-W1007", service.save(draft(NdeFlow.FIND_WORKERS)));
        assertEquals("NDE-V1007", service.save(draft(NdeFlow.VACANCY)));
        ArgumentCaptor<NdeJobMatch> captured = ArgumentCaptor.forClass(NdeJobMatch.class);
        verify(repository, times(4)).save(captured.capture());
        NdeJobMatch workers = captured.getAllValues().get(1);
        assertEquals(Integer.valueOf(5), workers.getQuantity());
        assertEquals("Welding", workers.getSkill());
        NdeJobMatch vacancy = captured.getAllValues().get(3);
        assertEquals("NDE-EMP1002", vacancy.getEmployerReference());
        assertEquals(Integer.valueOf(5), vacancy.getQuantity());
        assertNull(vacancy.getSkill());
        assertEquals("Pending Review", vacancy.getStatus());
    }

    @Test void anUnknownTypeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.save(draft("SOMETHING_ELSE")));
    }

    @Test void onlyTheOwnerCanLookAtARequest() {
        NdeJobMatch row = new NdeJobMatch();
        row.setMatchType(NdeFlow.FIND_WORKERS);
        row.setPhoneNumber(ME);
        row.setReference("NDE-W1002");
        row.setCategory("Skilled / Technical");
        row.setState("Kano");
        row.setSkill("Welding");
        row.setQuantity(5);
        row.setStatus("Submitted");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 19, 2, 30));
        when(repository.findByReference("NDE-W1002")).thenReturn(Optional.of(row));

        JobMatch found = service.findForPhone("NDE-W1002", ME);
        assertNotNull(found);
        assertEquals("Welding", found.skill());
        assertEquals(Integer.valueOf(5), found.quantity());
        assertEquals("Submitted", found.status());
        assertNull(service.findForPhone("NDE-W1002", "08031112222"));   // someone else
        assertNull(service.findForPhone("NDE-W1002", null));
        assertNull(service.findForPhone("NDE-W9999", ME));              // unknown reference
    }
}
