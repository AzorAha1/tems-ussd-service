package com.example.tems.Tems.cbm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tems.Tems.cbm.CbmElectionFlow.MemberCheck;
import com.example.tems.Tems.cbm.CbmElectionFlow.Supervisor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CbmElectionFlowTest {
    private static final String ME = "07072603735";

    /** In-memory store that behaves like the real service: reference = CBM-SV + (1000 + id); one row per phone. */
    static class MemoryStore implements CbmElectionFlow.Store {
        final List<Supervisor> rows = new ArrayList<>();
        final List<String> checked = new ArrayList<>();
        boolean fail = false;            // the database is down
        boolean checkThrows = false;     // CBM's lookup blows up
        boolean idKnown = true;          // CBM knows the ID
        String memberStatus = "active";
        int offset = 0;                  // pushes the numbers up so a test can check the longest possible reference

        @Override public String save(Supervisor s) {
            if (fail) throw new IllegalStateException("database down");
            String reference = "CBM-SV" + (1000 + offset + rows.size() + 1);
            rows.add(new Supervisor(s.phone(), s.fullName(), s.memberId(), s.memberCheck(), s.memberStatus(), s.state(),
                s.lga(), s.ward(), s.puCode(), reference, s.status(), s.createdAt()));
            return reference;
        }
        @Override public Supervisor findByPhone(String phone) {
            if (fail) throw new IllegalStateException("database down");
            return rows.stream().filter(r -> r.phone().equals(phone)).findFirst().orElse(null);
        }
        @Override public MemberCheck checkMember(String memberId) {
            if (checkThrows) throw new IllegalStateException("cbm down");
            checked.add(memberId);
            return new MemberCheck(idKnown, idKnown ? memberStatus : null);
        }
        void seed(String status) {
            rows.add(new Supervisor(ME, "Aminu Bello", "CBM-12345", "CONFIRMED", "active", "Kano", "Dala", "Gwale",
                "20/05/03/004", "CBM-SV1001", status, LocalDateTime.of(2026, 11, 20, 9, 0)));
        }
    }

    private final Map<String, String> store = new HashMap<>();
    private MemoryStore db;
    private CbmElectionFlow flow;
    private final CbmElectionFlow.Session session = new CbmElectionFlow.Session() {
        @Override public String get(String key) { return store.get(key); }
        @Override public void set(String key, String value) {
            if (value == null) store.remove(key); else store.put(key, value);
        }
        @Override public String phone() { return ME; }
    };

    @BeforeEach void setup() {
        store.clear();
        db = new MemoryStore();
        flow = new CbmElectionFlow(() -> db);
    }

    /** Sends each input in turn and returns the reply to the last one. */
    private String send(String... inputs) {
        String reply = null;
        for (String input : inputs) reply = flow.handle(session, input);
        return reply;
    }

    private static int visibleLength(String reply) {
        return reply.substring(4).length();   // without the "CON " / "END " prefix
    }

    private static final String[] FULL_REGISTRATION = {"Aminu Bello", "CBM-12345", "kano state", "Dala", "Gwale", "010203004"};

    private String registerUpToConfirm() {
        flow.start(session);
        return send("1", "Aminu Bello", "CBM-12345", "kano state", "Dala", "Gwale", "010203004");
    }

    // ---- the menu ----

    @Test void startShowsTheElectionMenu() {
        String reply = flow.start(session);
        assertTrue(reply.startsWith("CON ELECTION RESULTS COLLECTION"));
        assertTrue(reply.contains("1. Authorised Access Registration"));
        assertTrue(reply.contains("2. Send Result"));
        assertTrue(reply.contains("3. Report Incident"));
        assertTrue(reply.endsWith("4. Back"));
    }

    @Test void backFromTheMenuReturnsToCbmAndClearsTheElectionState() {
        flow.start(session);
        assertEquals(CbmElectionFlow.BACK_TO_CBM, send("4"));
        assertNull(store.get(CbmElectionFlow.FLOW_KEY));
        flow.start(session);
        assertEquals(CbmElectionFlow.BACK_TO_CBM, send("0"));
    }

    @Test void anInvalidMenuChoiceStaysOnTheMenu() {
        flow.start(session);
        String reply = send("9");
        assertTrue(reply.startsWith("CON Invalid choice."));
        assertTrue(reply.contains("1. Authorised Access Registration"));
        assertEquals(CbmElectionFlow.BACK_TO_CBM, send("4"));
    }

    @Test void lostStateStartsAtTheMenu() {
        String reply = flow.handle(session, "1");   // nothing started yet
        assertTrue(reply.contains("ELECTION RESULTS COLLECTION"));
        store.put(CbmElectionFlow.FLOW_KEY, "something_old");
        assertTrue(flow.handle(session, "1").contains("ELECTION RESULTS COLLECTION"));
    }

    // ---- who may use Send Result and Report Incident ----

    @Test void sendResultAndReportIncidentNeedARegisteredSupervisor() {
        flow.start(session);
        String reply = send("2");
        assertTrue(reply.contains("Send Result is for authorised supervisors."));
        assertTrue(reply.contains("Register first under Authorised Access Registration."));
        assertTrue(send("5").contains("ELECTION RESULTS COLLECTION"));   // any key returns to the menu
        reply = send("3");
        assertTrue(reply.contains("Report Incident is for authorised supervisors."));
    }

    @Test void aPendingSupervisorIsToldTheirAccessIsPending() {
        db.seed(CbmElectionFlow.STATUS_PENDING);
        flow.start(session);
        String reply = send("2");
        assertTrue(reply.contains("pending authorisation"));
        assertTrue(reply.contains("Ref: CBM-SV1001"));
    }

    @Test void aRejectedSupervisorIsToldToContactCbm() {
        db.seed(CbmElectionFlow.STATUS_REJECTED);
        flow.start(session);
        assertTrue(send("3").contains("not authorised. Contact CBM."));
    }

    @Test void anAuthorisedSupervisorGetsPastTheGateButTheFormsAreNotBuiltYet() {
        db.seed(CbmElectionFlow.STATUS_AUTHORISED);
        flow.start(session);
        String reply = send("2");
        assertTrue(reply.contains("Your access is authorised."));
        assertTrue(reply.contains("Send Result is not open yet."));
        assertTrue(send("0").contains("ELECTION RESULTS COLLECTION"));
        assertTrue(send("3").contains("Report Incident is not open yet."));
    }

    @Test void whenTheDatabaseIsDownTheGateSaysTryAgainLater() {
        db.fail = true;
        flow.start(session);
        assertTrue(send("2").contains("Service unavailable. Please try again later."));
        assertTrue(send("1").contains("ELECTION RESULTS COLLECTION"));
        assertTrue(send("1").contains("Service unavailable."));
    }

    // ---- registration ----

    @Test void registrationHappyPathSavesAPendingSupervisor() {
        String confirm = registerUpToConfirm();
        assertTrue(confirm.startsWith("CON Confirm details:"));
        assertTrue(confirm.contains("Aminu Bello"));
        assertTrue(confirm.contains("ID: CBM-12345"));
        assertTrue(confirm.contains("Kano, Dala"));
        assertTrue(confirm.contains("Ward: Gwale"));
        assertTrue(confirm.contains("PU: 01/02/03/004"));

        String done = send("1");
        assertTrue(done.startsWith("END Registration received."));
        assertTrue(done.contains("Ref: CBM-SV1001"));
        assertTrue(done.contains("Status: Pending authorisation"));

        assertEquals(1, db.rows.size());
        Supervisor saved = db.rows.get(0);
        assertEquals(ME, saved.phone());
        assertEquals("Aminu Bello", saved.fullName());
        assertEquals("CBM-12345", saved.memberId());
        assertEquals("CONFIRMED", saved.memberCheck());
        assertEquals("active", saved.memberStatus());
        assertEquals("Kano", saved.state());
        assertEquals("Dala", saved.lga());
        assertEquals("Gwale", saved.ward());
        assertEquals("01/02/03/004", saved.puCode());
        assertEquals("PENDING", saved.status());
        assertNotNull(saved.createdAt());
        assertTrue(store.isEmpty(), "the session is cleared when the registration is finished");
    }

    @Test void theIdIsSentToCbmExactlyAsTyped() {
        flow.start(session);
        send("1", "Aminu Bello", "cbm/2026/Ab-77");
        assertEquals(Arrays.asList("cbm/2026/Ab-77"), db.checked);
    }

    @Test void answersWithExtraSpacesAreCleaned() {
        flow.start(session);
        send("1", "  Aminu    Bello ", "CBM-12345", "  Akwa   Ibom ", " Ikot  Ekpene", "  Ward 5 ", "01 02 03 004", "1");
        Supervisor saved = db.rows.get(0);
        assertEquals("Aminu Bello", saved.fullName());
        assertEquals("Akwa Ibom", saved.state());
        assertEquals("Ikot Ekpene", saved.lga());
        assertEquals("Ward 5", saved.ward());
        assertEquals("01/02/03/004", saved.puCode());
    }

    @Test void anIdCbmCannotConfirmOffersReEnterOrContinue() {
        db.idKnown = false;
        flow.start(session);
        String reply = send("1", "Aminu Bello", "CBM-99999");
        assertTrue(reply.contains("We could not confirm that ID right now."));
        assertTrue(reply.contains("1. Re-enter ID"));
        assertTrue(reply.contains("2. Continue, CBM will check it"));

        reply = send("1");   // type the ID again
        assertTrue(reply.contains("Enter your City Boy Registration ID:"));
        db.idKnown = true;
        reply = send("CBM-12345");
        assertTrue(reply.contains("Enter your state of voting"));
        send("Kano", "Dala", "Gwale", "010203004", "1");
        assertEquals("CONFIRMED", db.rows.get(0).memberCheck());
        assertEquals("CBM-12345", db.rows.get(0).memberId());
    }

    @Test void continuingWithoutAConfirmedIdIsRecordedAsNotConfirmed() {
        db.idKnown = false;
        flow.start(session);
        send("1", "Aminu Bello", "CBM-99999");
        String reply = send("2");
        assertTrue(reply.contains("Enter your state of voting"));
        send("Kano", "Dala", "Gwale", "010203004");
        String done = send("1");
        assertTrue(done.startsWith("END Registration received."));
        Supervisor saved = db.rows.get(0);
        assertEquals("NOT_CONFIRMED", saved.memberCheck());
        assertNull(saved.memberStatus());
        assertEquals("CBM-99999", saved.memberId());
        assertEquals("PENDING", saved.status());
    }

    @Test void aCbmLookupThatBlowsUpIsTreatedAsNotConfirmed() {
        db.checkThrows = true;
        flow.start(session);
        String reply = send("1", "Aminu Bello", "CBM-12345");
        assertTrue(reply.contains("We could not confirm that ID right now."));
        assertTrue(send("5").contains("Invalid choice."));
        assertTrue(send("2").contains("Enter your state of voting"));
    }

    @Test void zeroOnTheIdDecisionScreenGoesBackToTheName() {
        db.idKnown = false;
        flow.start(session);
        send("1", "Aminu Bello", "CBM-99999");
        assertTrue(send("0").contains("Enter your full name:"));
    }

    @Test void aSupervisorAlreadyRegisteredSeesTheirReferenceInsteadOfRegisteringTwice() {
        db.seed(CbmElectionFlow.STATUS_AUTHORISED);
        flow.start(session);
        String reply = send("1");
        assertTrue(reply.contains("You are already registered."));
        assertTrue(reply.contains("Ref: CBM-SV1001"));
        assertTrue(reply.contains("Status: Authorised"));
        assertTrue(reply.contains("contact CBM"));
        assertTrue(send("0").contains("ELECTION RESULTS COLLECTION"));
        assertEquals(1, db.rows.size());
    }

    @Test void badAnswersAreRejectedAndTheQuestionIsAskedAgain() {
        flow.start(session);
        assertTrue(send("1", "1234").contains("Please enter a valid name."));
        assertTrue(send("Aminu Bello").contains("Enter your City Boy Registration ID:"));
        assertTrue(send("AB&C=1").contains("Invalid ID. Check it and try again."));
        assertTrue(send("ab").contains("Invalid ID."));                 // too short
        assertTrue(send("a b c d").contains("Invalid ID."));            // spaces are not part of an ID
        assertTrue(send("CBM-12345").contains("Enter your state of voting"));
        assertTrue(send("Atlantis").contains("State not recognised."));
        assertTrue(send("Kano").contains("Enter your LGA:"));
        assertTrue(send("12345").contains("Please enter a valid LGA."));
        assertTrue(send("Dala").contains("Enter your Ward:"));
        assertTrue(send("!!").contains("Please enter a valid ward."));
        assertTrue(send("Gwale").contains("Enter your Polling Unit Code"));
        for (String bad : new String[] {"123", "0102030", "0102030405", "ABCDEFGHI", "01/02/03/AB4", "01*02#03"}) {
            String reply = send(bad);
            assertTrue(reply.contains("Enter 8 or 9 digits, e.g. 010203004."), bad);
        }
        assertEquals(0, db.rows.size());
    }

    @Test void statesAreRecognisedInTheWaysPeopleTypeThem() {
        for (String typed : new String[] {"FCT", "abuja", "Federal Capital Territory", "FCT Abuja"}) {
            store.clear();
            flow.start(session);
            send("1", "Aminu Bello", "CBM-12345", typed, "Garki", "Ward 1", "150101001", "1");
            assertEquals("FCT Abuja", db.rows.get(db.rows.size() - 1).state(), typed);
            db.rows.clear();
        }
        store.clear();
        flow.start(session);
        send("1", "Aminu Bello", "CBM-12345", "Nassarawa State", "Keffi", "Ward 1", "260101001", "1");
        assertEquals("Nasarawa", db.rows.get(0).state());
    }

    @Test void pollingUnitCodesAreAcceptedInTheFormsPeopleTypeThem() {
        assertEquals("01/02/03/004", CbmElectionFlow.normalizePollingUnit("010203004"));
        assertEquals("01/02/03/004", CbmElectionFlow.normalizePollingUnit("01/02/03/004"));
        assertEquals("01/02/03/004", CbmElectionFlow.normalizePollingUnit("01-02-03-004"));
        assertEquals("01/02/03/004", CbmElectionFlow.normalizePollingUnit("01 02 03 004"));
        assertEquals("01/02/03/04", CbmElectionFlow.normalizePollingUnit("01020304"));
        assertNull(CbmElectionFlow.normalizePollingUnit("1/2/3/4"));
        assertNull(CbmElectionFlow.normalizePollingUnit("0102030"));
        assertNull(CbmElectionFlow.normalizePollingUnit("0102030405"));
        assertNull(CbmElectionFlow.normalizePollingUnit("01a203004"));
    }

    // ---- going back ----

    @Test void zeroGoesBackOneQuestionAndFromTheFirstQuestionToTheMenu() {
        flow.start(session);
        send("1", "Aminu Bello", "CBM-12345", "Kano");
        assertTrue(send("0").contains("Enter your state of voting"));      // from the LGA question
        assertTrue(send("0").contains("Enter your City Boy Registration ID:"));
        assertTrue(send("0").contains("Enter your full name:"));
        assertTrue(send("0").contains("ELECTION RESULTS COLLECTION"));
        assertTrue(send("1").contains("Enter your full name:"), "a new registration starts from the first question");
    }

    @Test void backOnTheConfirmScreenReturnsToThePollingUnitQuestionAndKeepsTheEarlierAnswers() {
        registerUpToConfirm();
        assertTrue(send("0").contains("Enter your Polling Unit Code"));
        String confirm = send("020304005");
        assertTrue(confirm.contains("Aminu Bello"));
        assertTrue(confirm.contains("PU: 02/03/04/005"));
    }

    @Test void reEnterOnTheConfirmScreenStartsAgainFromTheName() {
        registerUpToConfirm();
        assertTrue(send("2").contains("Enter your full name:"));
        assertNull(store.get("cbmEsA0"));
        assertNull(store.get("cbmEsA5"));
        send("Musa Danjuma", "CBM-77777", "Lagos", "Ikeja", "Alausa", "250101001");
        send("1");
        assertEquals("Musa Danjuma", db.rows.get(0).fullName());
        assertEquals("25/01/01/001", db.rows.get(0).puCode());
    }

    @Test void anInvalidChoiceOnTheConfirmScreenStaysOnIt() {
        registerUpToConfirm();
        String reply = send("7");
        assertTrue(reply.startsWith("CON Invalid choice."));
        assertTrue(reply.contains("1. Confirm"));
        assertTrue(reply.contains("PU: 01/02/03/004"));
        assertEquals(0, db.rows.size());
        assertTrue(send("1").startsWith("END Registration received."));
    }

    @Test void damagedConfirmStateDoesNotTrapTheUser() {
        registerUpToConfirm();
        store.remove("cbmEsA2");   // one answer is gone
        assertTrue(send("1").contains("ELECTION RESULTS COLLECTION"));
        assertEquals(0, db.rows.size());
    }

    // ---- failures ----

    @Test void ifTheSaveFailsTheUserIsToldAndTheSessionIsCleared() {
        registerUpToConfirm();
        db.fail = true;
        // the lookup before registration already passed, so only the save fails here
        String reply = send("1");
        assertEquals("END We could not save your registration. Please try again later.", reply);
        assertTrue(store.isEmpty());
        assertEquals(0, db.rows.size());
    }

    @Test void withNoStoreAtAllRegistrationSaysTryAgainLater() {
        flow = new CbmElectionFlow(() -> null);
        flow.start(session);
        assertTrue(send("1").contains("Service unavailable."));
        assertTrue(send("0").contains("ELECTION RESULTS COLLECTION"));
        assertTrue(send("2").contains("Service unavailable."));
    }

    // ---- the USSD screen limit and the session keys ----

    @Test void everyScreenOnTheLongestPathFitsTheUssdLimit() {
        db.offset = 9000;   // five-digit id, so the longest possible reference: CBM-SV10001
        String name = "Abcdefghij Abcdefghij Abcdefghij Abcdefghij Abcdefghij";    // 54 characters
        String id = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123";                               // 30 characters
        String lga = "Abcdefghi Abcdefghi Abcdefghi Abcdefghi";                     // 39 characters
        String ward = "Abcdefghi Abcdefghi Abcdefghi Abcdefghi";                    // 39 characters
        List<String> screens = new ArrayList<>();
        screens.add(flow.start(session));
        screens.add(send("9"));                                   // invalid choice on the menu
        screens.add(send("1"));
        screens.add(send("1234"));                                // invalid name, with its notice
        screens.add(send(name));
        screens.add(send("A&B"));                                 // invalid id, with its notice
        db.idKnown = false;
        screens.add(send(id));                                    // could-not-confirm screen
        screens.add(send("8"));                                   // ... with its notice
        screens.add(send("2"));
        screens.add(send("Atlantis"));                            // invalid state, with its notice
        screens.add(send("Cross River"));
        screens.add(send(lga));
        screens.add(send(ward));
        screens.add(send("123"));                                 // polling unit prompt plus its longest notice
        screens.add(send("010203004"));                           // confirm screen with the longest answers
        screens.add(send("7"));                                   // ... and with the invalid-choice notice
        screens.add(send("1"));                                   // done
        for (String screen : screens) {
            assertTrue(visibleLength(screen) <= 170, visibleLength(screen) + " chars: " + screen);
        }
        db.rows.clear();
        flow.start(session);
        assertTrue(visibleLength(send("2")) <= 170);              // gate screens
        db.seed(CbmElectionFlow.STATUS_PENDING);
        assertTrue(visibleLength(send("0", "3")) <= 170);
        assertTrue(visibleLength(send("0", "1")) <= 170);         // already-registered screen
    }

    @Test void everySessionKeyTheFlowUsesIsInTheDocumentedList() {
        Set<String> allowed = new HashSet<>(Arrays.asList(CbmElectionFlow.SESSION_KEYS));
        db.idKnown = false;
        flow.start(session);
        send("1", "Aminu Bello", "CBM-12345", "2", "Kano", "Dala", "Gwale");
        assertFalse(store.isEmpty());
        for (String key : store.keySet()) assertTrue(allowed.contains(key), "undocumented session key: " + key);
        assertTrue(store.containsKey("cbmEsA5") == false);   // the last answer is not typed yet
        send("010203004");
        for (String key : store.keySet()) assertTrue(allowed.contains(key), "undocumented session key: " + key);
        assertTrue(store.containsKey("cbmEsA5"));
        flow.clear(session);
        assertTrue(store.isEmpty());
    }
}
