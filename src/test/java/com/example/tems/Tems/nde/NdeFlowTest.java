package com.example.tems.Tems.nde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.tems.Tems.nde.NdeFlow.JobMatch;
import com.example.tems.Tems.nde.NdeFlow.Registration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NdeFlowTest {
    private static final String ME = "07072603735";

    /** In-memory store that behaves like the real service: reference = prefix + (1000 + id); only the owner can look up. */
    static class MemoryStore implements NdeFlow.Store {
        final List<Registration> rows = new ArrayList<>();
        boolean fail = false;
        @Override public String save(Registration r) {
            if (fail) throw new IllegalStateException("database down");
            String prefix = r.type().equals("JOB_SEEKER") ? "NDE" : r.type().equals("EMPLOYER") ? "NDE-EMP" : "NDE-ST";
            String reference = prefix + (1000 + rows.size() + 1);
            rows.add(new Registration(r.type(), r.phone(), r.name(), r.state(), r.lga(), r.qualification(),
                r.employmentCategory(), r.contactPhone(), r.businessSector(), r.preferredTrade(), reference,
                r.status(), r.createdAt()));
            return reference;
        }
        @Override public Registration findForPhone(String reference, String phone) {
            if (fail) throw new IllegalStateException("database down");
            return rows.stream().filter(r -> r.reference().equals(reference) && r.phone().equals(phone)).findFirst().orElse(null);
        }
    }

    /** Same idea for job-matching requests: reference = prefix + (1000 + offset + count); only the owner can look up. */
    static class MemoryMatchStore implements NdeFlow.MatchStore {
        final List<JobMatch> rows = new ArrayList<>();
        boolean fail = false;
        int offset = 0;   // pushes the numbers up so a test can check the longest possible reference
        @Override public String save(JobMatch m) {
            if (fail) throw new IllegalStateException("database down");
            String prefix = m.type().equals("FIND_EMPLOYMENT") ? "NDE-JM" : m.type().equals("FIND_WORKERS") ? "NDE-W" : "NDE-V";
            String reference = prefix + (1000 + offset + rows.size() + 1);
            rows.add(new JobMatch(m.type(), m.phone(), m.employerReference(), m.category(), m.state(), m.skill(),
                m.quantity(), reference, m.status(), m.createdAt()));
            return reference;
        }
        @Override public JobMatch findForPhone(String reference, String phone) {
            if (fail) throw new IllegalStateException("database down");
            return rows.stream().filter(r -> r.reference().equals(reference) && r.phone().equals(phone)).findFirst().orElse(null);
        }
    }

    private final Map<String, String> store = new HashMap<>();
    private MemoryStore memory;
    private MemoryMatchStore matches;
    private NdeFlow flow;
    private NdeFlow.Session session;

    @BeforeEach void setup() {
        store.clear();
        memory = new MemoryStore();
        matches = new MemoryMatchStore();
        flow = new NdeFlow(() -> memory, () -> matches);
        session = sessionFor(ME);
        flow.start(session);
    }

    private NdeFlow.Session sessionFor(String phone) {
        return new NdeFlow.Session() {
            @Override public String get(String key) { return store.get(key); }
            @Override public void set(String key, String value) { if (value == null) store.remove(key); else store.put(key, value); }
            @Override public String phone() { return phone; }
        };
    }

    /** Sends each input in turn and returns the last screen. */
    private String go(String... inputs) {
        String screen = null;
        for (String input : inputs) screen = flow.handle(session, input);
        return screen;
    }

    private static String visible(String screen) { return screen.substring(4); }

    // ---- phase 1: language and main menu ----

    @Test void findsNdeFromTheSearchBox() {
        for (String term : new String[] {"NDE", "nde", " Nde ", "N.D.E", "nde services", "National Directorate of Employment"}) {
            assertTrue(NdeFlow.isSearchTerm(term), term);
        }
        for (String term : new String[] {"NABTEB", "CAC", "nd", "indeed", "tmf", ""}) assertFalse(NdeFlow.isSearchTerm(term), term);
        assertFalse(NdeFlow.isSearchTerm(null));
    }

    @Test void onlyEnglishIsAvailableAndOpensTheMainMenu() {
        assertTrue(go("2").contains("Hausa is not available yet."));
        assertTrue(go("5").contains("Pidgin is not available yet."));
        String menu = go("1");
        assertTrue(menu.startsWith("CON NDE SERVICES") && menu.contains("10.More Info"));
        assertEquals("EN", store.get("ndeLang"));
    }

    @Test void backFromTheLanguageScreenReturnsToTemsAndClearsState() {
        assertEquals(NdeFlow.TEMS_MENU, go("0"));
        assertTrue(store.isEmpty());
    }

    @Test void servicesThreeToTenAreStillComingSoon() {
        String[] titles = {"Request", "Report", "Guidelines & Procedures", "Skills & Training",
            "Rural Employment", "Enterprise Support", "Check Status / Follow-up", "More Information"};
        for (int i = 3; i <= 10; i++) {
            flow.start(session);
            String screen = go("1", String.valueOf(i));
            assertEquals(titles[i - 3], visible(screen).split("\n")[0]);
            assertTrue(screen.contains("coming soon"));
        }
    }

    // ---- phase 2: register ----

    @Test void jobSeekerRegistrationSavesCleanValuesAndShowsTheReference() {
        assertTrue(go("1", "1").contains("4. Verify Registration"));
        assertTrue(go("1").contains("Enter your full name:"));
        assertTrue(go("Musa Ibrahim").contains("Enter your state"));
        assertTrue(go("kano state").contains("Enter your LGA:"));
        assertTrue(go("Dala").contains("Select Qualification:"));
        assertTrue(go("5").contains("Select Employment Category:"));
        String done = go("1");

        assertTrue(done.startsWith("END NDE Registration Request Received."));
        assertTrue(done.contains("Name: Musa Ibrahim") && done.contains("Category: Skilled / Technical"));
        assertTrue(done.contains("Reference: NDE1001"));
        Registration saved = memory.rows.get(0);
        assertEquals("Kano", saved.state());                 // "kano state" is stored as "Kano"
        assertEquals("HND / Degree", saved.qualification());
        assertEquals(ME, saved.phone());
        assertEquals("Submitted", saved.status());
        assertTrue(store.isEmpty());                         // the session is cleared afterwards
    }

    @Test void employerRegistrationAcceptsInternationalPhoneFormat() {
        go("1", "1", "2", "Dala Welding Works", "FCT", "Gwagwalada");
        assertTrue(go("+2348031234567").contains("Select Business Sector:"));
        String done = go("2");
        assertTrue(done.contains("Reference: NDE-EMP1001") && done.contains("awaiting verification"));
        Registration saved = memory.rows.get(0);
        assertEquals("FCT Abuja", saved.state());
        assertEquals("08031234567", saved.contactPhone());
        assertEquals("Agriculture", saved.businessSector());
        assertEquals("Awaiting verification", saved.status());
    }

    @Test void skillsTrainingRegistration() {
        String done = go("1", "1", "3", "Amina Yusuf", "Kaduna", "Zaria", "3");
        assertTrue(done.contains("Trade: Electrical Installation") && done.contains("Reference: NDE-ST1001"));
    }

    @Test void badAnswersAreRejectedWithoutMovingOn() {
        go("1", "1", "1");
        for (String bad : new String[] {"Musa123", "A", "<b>", "Musa*Ibrahim"}) {
            assertTrue(go(bad).contains("Please enter a valid name."), bad);
            assertEquals("0", store.get("ndeRegStep"));
        }
        go("Ada Obi");
        assertTrue(go("Narnia").contains("State not recognised."));
        assertTrue(go("Akwa Ibom").contains("Enter your LGA:"));
        assertTrue(go("1234").contains("Please enter a valid LGA."));
        go("Ikot Ekpene");
        assertTrue(go("7").contains("Invalid choice."));
        flow.start(session);
        go("1", "1", "2", "Acme Ltd", "Lagos", "Ikeja");
        for (String bad : new String[] {"12345", "08423456789", "080123456789", "abc"}) {
            assertTrue(go(bad).contains("Use 11 digits"), bad);
        }
    }

    @Test void backGoesOneQuestionAndChangedAnswersAreWhatGetsSaved() {
        go("1", "1", "1", "Ada Obi", "Kano", "Dala");
        assertTrue(go("0").contains("Enter your LGA:"));
        go("Fagge", "3");
        go("2");
        assertEquals("Fagge", memory.rows.get(0).lga());
        flow.start(session);
        go("1", "1", "1");
        assertTrue(go("0").contains("Register / Verify"));   // back from the first question
        assertNull(store.get("ndeRegType"));
    }

    @Test void mainMenuAndExitWorkInTheMiddleOfARegistration() {
        go("1", "1", "1", "Ada Obi", "Kano");
        assertTrue(go("00").startsWith("CON NDE SERVICES"));
        assertNull(store.get("ndeA0"));                      // the half-finished answers are thrown away
        go("1", "2", "Acme Ltd");
        assertTrue(go("000").startsWith("END "));
        assertTrue(store.isEmpty());
    }

    @Test void aFailedSaveTellsTheUserAndNeverCrashes() {
        memory.fail = true;
        String screen = go("1", "1", "3", "Amina Yusuf", "Kaduna", "Zaria", "2");
        assertEquals("END We could not save your registration. Please try again later.", screen);
        assertTrue(store.isEmpty());
        NdeFlow withoutStore = new NdeFlow(() -> null);
        store.clear();
        withoutStore.start(session);
        String[] answers = {"1", "1", "3", "Amina Yusuf", "Kaduna", "Zaria", "2"};
        String last = null;
        for (String answer : answers) last = withoutStore.handle(session, answer);
        assertTrue(last.startsWith("END We could not save"));
    }

    @Test void aLostRegistrationStateReturnsToTheMenuInsteadOfTrappingTheUser() {
        go("1", "1", "1");
        store.remove("ndeRegType");
        assertTrue(go("Ada Obi").contains("Register / Verify"));
        go("1");
        store.put("ndeRegStep", "99");
        assertTrue(go("Ada Obi").contains("Register / Verify"));
    }

    // ---- phase 2: verify ----

    @Test void verifyShowsOnlyTheOwnersRegistration() {
        go("1", "1", "1", "Musa Ibrahim", "Kano", "Dala", "5", "1");           // NDE1001
        flow.start(session);
        go("1", "1", "2", "Acme Ltd", "Lagos", "Ikeja", "08031234567", "4");   // NDE-EMP1002
        flow.start(session);
        go("1", "1");
        assertTrue(go("4").contains("Enter your registration reference"));

        String own = go("NDE1001");
        assertTrue(own.startsWith("CON Reference: NDE1001") && own.contains("Type: Job Seeker"));
        assertTrue(own.contains("Status: Submitted") && own.contains("Date: "));
        String employer = go("1", "nde-emp1002");                                // lower case is fine
        assertTrue(employer.contains("Type: Employer") && employer.contains("Awaiting verification"));
        assertTrue(go("1", "NDE 1001").contains("Reference: NDE1001"));          // spaces are ignored
        assertTrue(go("1", "NDE9999").contains("Reference not found for this number."));
        assertTrue(go("1", "hello").contains("Invalid reference."));

        // Another phone number must not be able to read this registration.
        NdeFlow.Session stranger = sessionFor("08031112222");
        flow.start(stranger);
        flow.handle(stranger, "1");
        flow.handle(stranger, "1");
        flow.handle(stranger, "4");
        String denied = flow.handle(stranger, "NDE1001");
        assertTrue(denied.contains("Reference not found for this number."));
        assertFalse(denied.contains("Musa") || denied.contains("Job Seeker"));
    }

    @Test void verifyHandlesADatabaseErrorAndNavigation() {
        go("1", "1", "4");
        memory.fail = true;
        assertTrue(go("NDE1001").contains("Service unavailable"));
        assertTrue(go("7").contains("Invalid choice."));
        assertTrue(go("0").contains("Register / Verify"));
    }

    // ---- phase 3: job matching ----

    /** Registers an employer from ME (reference NDE-EMP1001) and returns to the language screen. */
    private void registerAnEmployer() {
        go("1", "1", "2", "Acme Ltd", "Lagos", "Ikeja", "08031234567", "4");
        assertEquals("NDE-EMP1001", memory.rows.get(0).reference());
        flow.start(session);
    }

    @Test void jobMatchingOpensTheEmploymentConnectMenu() {
        String menu = go("1", "2");
        assertTrue(menu.startsWith("CON NDE EMPLOYMENT CONNECT"));
        assertTrue(menu.contains("1. Find Employment") && menu.contains("2. Find Workers")
            && menu.contains("3. Register Job Vacancy") && menu.contains("4. Check Matching Status"));
        assertTrue(go("9").contains("Invalid choice."));
        assertTrue(go("0").startsWith("CON NDE SERVICES"));     // back to the NDE main menu
    }

    @Test void findEmploymentSavesCleanValuesAndShowsTheReference() {
        assertTrue(go("1", "2", "1").contains("Select Job Category:"));
        assertTrue(go("1").contains("Enter your state"));
        assertTrue(go("kano state").contains("Enter your skill"));
        String done = go("Welding");

        assertTrue(done.startsWith("END Employment Matching Request Received."));
        assertTrue(done.contains("Skill: Welding") && done.contains("Location: Kano"));
        assertTrue(done.contains("Reference: NDE-JM1001") && done.contains("You will be notified"));
        JobMatch saved = matches.rows.get(0);
        assertEquals("FIND_EMPLOYMENT", saved.type());
        assertEquals("Skilled / Technical", saved.category());
        assertEquals("Kano", saved.state());                    // "kano state" is stored as "Kano"
        assertEquals("Welding", saved.skill());
        assertEquals(ME, saved.phone());
        assertEquals("Submitted", saved.status());
        assertNull(saved.quantity());
        assertNull(saved.employerReference());
        assertTrue(store.isEmpty());                            // the session is cleared afterwards
    }

    @Test void findWorkersSavesTheNumberNeeded() {
        assertTrue(go("1", "2", "2").contains("Select Worker Category:"));
        assertTrue(go("1").contains("Enter the state"));
        assertTrue(go("Kano").contains("Enter the skill required"));
        assertTrue(go("Welding").contains("Enter number of workers needed:"));
        String done = go("5");

        assertTrue(done.startsWith("END Employer Request Received."));
        assertTrue(done.contains("Workers Required: 5 (Welding)") && done.contains("Location: Kano"));
        assertTrue(done.contains("Reference: NDE-W1001") && done.contains("reviewed for suitable matches"));
        JobMatch saved = matches.rows.get(0);
        assertEquals("FIND_WORKERS", saved.type());
        assertEquals(Integer.valueOf(5), saved.quantity());
        assertEquals("Submitted", saved.status());
    }

    @Test void numbersOutsideOneToAThousandAreRejectedAndLeadingZerosAreIgnored() {
        go("1", "2", "2", "1", "Kano", "Welding");
        for (String bad : new String[] {"1001", "abc", "-5", "5.5", "12345", "5 workers"}) {
            assertTrue(go(bad).contains("Enter a number from 1 to 1000."), bad);
            assertEquals("3", store.get("ndeRegStep"));
        }
        assertTrue(go("0").contains("Enter the skill required"));      // 0 goes back one question
        go("Welding");
        assertEquals("3", store.get("ndeRegStep"));
        assertTrue(go("007").startsWith("END Employer Request Received."));
        assertEquals(Integer.valueOf(7), matches.rows.get(0).quantity());
        flow.start(session);
        go("1", "2", "2", "1", "Kano", "Welding");
        assertTrue(go("1000").startsWith("END "));              // the top of the range is allowed
    }

    @Test void badSkillsAndStatesAreRejectedWithoutMovingOn() {
        go("1", "2", "1", "1");
        assertTrue(go("Narnia").contains("State not recognised."));
        assertTrue(go("Kano").contains("Enter your skill"));
        for (String bad : new String[] {"<b>", "W", "", "Weld*ing", "A".repeat(31), "123"}) {
            assertTrue(go(bad).contains("Please enter a valid skill."), bad);
            assertEquals("2", store.get("ndeRegStep"));
        }
        assertTrue(go("Phone repair").startsWith("END "));      // spaces inside a skill are fine
        assertEquals("Phone repair", matches.rows.get(0).skill());
        flow.start(session);
        assertTrue(go("1", "2", "1", "9").contains("Invalid choice."));   // category 9 does not exist
    }

    @Test void aVacancyNeedsAnEmployerRegisteredFromThisPhone() {
        registerAnEmployer();
        // an employer that belongs to somebody else, and a record with the wrong type
        memory.rows.add(new Registration("EMPLOYER", "08031112222", "Other Ltd", "Lagos", "Ikeja", null, null,
            "08031234567", "Trading", null, "NDE-EMP1002", "Awaiting verification", LocalDateTime.now()));
        memory.rows.add(new Registration("JOB_SEEKER", ME, "Musa", "Kano", "Dala", "Primary", "Agriculture", null,
            null, null, "NDE-EMP1003", "Submitted", LocalDateTime.now()));

        assertTrue(go("1", "2", "3").contains("Enter your employer reference"));
        assertTrue(go("hello").contains("Invalid reference."));
        assertTrue(go("NDE1001").contains("Invalid reference."));            // a job-seeker reference is not an employer
        String notMine = go("NDE-EMP1002");
        assertTrue(notMine.contains("Employer reference not found for this number. Register as Employer first."));
        assertFalse(notMine.contains("Other Ltd"));
        assertTrue(go("NDE-EMP1003").contains("not found for this number"));  // right prefix, wrong type
        assertEquals("0", store.get("ndeRegStep"));

        assertTrue(go("nde emp 1001").contains("Select Job Category:"));     // spaces and case do not matter
        assertTrue(go("3").contains("Enter number of vacancies:"));
        assertTrue(go("5").contains("Enter job location"));
        String done = go("kano");

        assertTrue(done.startsWith("END Vacancy Submission Received."));
        assertTrue(done.contains("Reference: NDE-V1001") && done.contains("Status: Pending Review."));
        assertTrue(done.contains("require NDE verification and approved data sources."));
        JobMatch saved = matches.rows.get(0);
        assertEquals("VACANCY", saved.type());
        assertEquals("NDE-EMP1001", saved.employerReference());
        assertEquals("Agriculture", saved.category());
        assertEquals(Integer.valueOf(5), saved.quantity());
        assertEquals("Kano", saved.state());
        assertNull(saved.skill());
        assertEquals("Pending Review", saved.status());
    }

    @Test void theEmployerCheckSurvivesAnOutageAndAMissingStore() {
        registerAnEmployer();
        go("1", "2", "3");
        memory.fail = true;
        assertTrue(go("NDE-EMP1001").contains("Service unavailable. Try again later."));
        assertEquals("0", store.get("ndeRegStep"));             // still on the same question
        memory.fail = false;
        assertTrue(go("NDE-EMP1001").contains("Select Job Category:"));

        NdeFlow noStore = new NdeFlow(() -> null, () -> matches);
        store.clear();
        noStore.start(session);
        String last = null;
        for (String input : new String[] {"1", "2", "3", "NDE-EMP1001"}) last = noStore.handle(session, input);
        assertTrue(last.contains("Service unavailable. Try again later."));
    }

    @Test void checkMatchingStatusShowsOnlyTheOwnersRequests() {
        registerAnEmployer();
        go("1", "2", "1", "1", "Kano", "Welding");                 // NDE-JM1001
        flow.start(session);
        go("1", "2", "2", "1", "Kano", "Welding", "5");            // NDE-W1002
        flow.start(session);
        go("1", "2", "3", "NDE-EMP1001", "3", "5", "Kano");        // NDE-V1003
        flow.start(session);
        go("1", "2");
        assertTrue(go("4").contains("Enter your request reference"));

        String employment = go("NDE-JM1001");
        assertTrue(employment.startsWith("CON Reference: NDE-JM1001") && employment.contains("Type: Find Employment"));
        assertTrue(employment.contains("Skill: Welding") && employment.contains("Location: Kano"));
        assertTrue(employment.contains("Status: Submitted") && employment.contains("Date: "));
        String workers = go("1", "nde-w1002");                       // lower case is fine
        assertTrue(workers.contains("Type: Find Workers") && workers.contains("Workers: 5 (Welding)"));
        String vacancy = go("1", "NDEV1003");                        // the hyphen is optional
        assertTrue(vacancy.contains("Reference: NDE-V1003") && vacancy.contains("Type: Job Vacancy"));
        assertTrue(vacancy.contains("Vacancies: 5 (Agriculture)") && vacancy.contains("Status: Pending Review"));
        assertTrue(go("1", "NDE-JM9999").contains("Reference not found for this number."));
        assertTrue(go("1", "hello").contains("Invalid reference."));
        assertTrue(go("1", "NDE1001").contains("Invalid reference."));        // a registration reference is not a request

        // Another phone number must not be able to read this request.
        NdeFlow.Session stranger = sessionFor("08031112222");
        flow.start(stranger);
        for (String input : new String[] {"1", "2", "4"}) flow.handle(stranger, input);
        String denied = flow.handle(stranger, "NDE-JM1001");
        assertTrue(denied.contains("Reference not found for this number."));
        assertFalse(denied.contains("Welding") || denied.contains("Find Employment"));
    }

    @Test void checkMatchingStatusHandlesNavigationAndAnOutage() {
        go("1", "2", "4");
        matches.fail = true;
        assertTrue(go("NDE-JM1001").contains("Service unavailable"));
        assertTrue(go("7").contains("Invalid choice."));
        assertTrue(go("1").contains("Enter your request reference"));
        assertTrue(go("0").contains("NDE EMPLOYMENT CONNECT"));               // back from the question
        go("4", "NDE-JM1001");
        assertTrue(go("0").contains("NDE EMPLOYMENT CONNECT"));               // back from the result
        NdeFlow withoutStore = new NdeFlow(() -> memory);                     // phase 2 constructor: no match store
        store.clear();
        withoutStore.start(session);
        String last = null;
        for (String input : new String[] {"1", "2", "4", "NDE-JM1001"}) last = withoutStore.handle(session, input);
        assertTrue(last.contains("Service unavailable"));
    }

    @Test void backGoesOneQuestionAndMainMenuAndExitWorkInsideJobMatching() {
        go("1", "2", "2", "1", "Kano", "Welding");
        assertTrue(go("0").contains("Enter the skill required"));
        assertTrue(go("0").contains("Enter the state"));
        go("Lagos", "Tailoring");
        assertTrue(go("8").startsWith("END "));
        assertEquals("Lagos", matches.rows.get(0).state());                   // the changed answer is what got saved
        assertEquals("Tailoring", matches.rows.get(0).skill());
        assertEquals(Integer.valueOf(8), matches.rows.get(0).quantity());

        flow.start(session);
        go("1", "2", "1");
        assertTrue(go("0").contains("NDE EMPLOYMENT CONNECT"));               // back from the first question
        assertNull(store.get("ndeRegType"));

        go("1", "1", "Kano");
        assertTrue(go("00").startsWith("CON NDE SERVICES"));
        assertNull(store.get("ndeA0"));                                       // half-finished answers are thrown away
        go("2", "2", "1", "Kano");
        assertTrue(go("000").startsWith("END "));
        assertTrue(store.isEmpty());
    }

    @Test void aFailedSaveOrAMissingStoreTellsTheUserAndNeverCrashes() {
        matches.fail = true;
        assertEquals("END We could not save your request. Please try again later.",
            go("1", "2", "1", "1", "Kano", "Welding"));
        assertTrue(store.isEmpty());
        NdeFlow phaseTwoOnly = new NdeFlow(() -> memory);
        phaseTwoOnly.start(session);
        String last = null;
        for (String input : new String[] {"1", "2", "1", "1", "Kano", "Welding"}) last = phaseTwoOnly.handle(session, input);
        assertTrue(last.startsWith("END We could not save your request."));
    }

    @Test void aLostFormStateReturnsToTheJobMatchingMenuInsteadOfTrappingTheUser() {
        go("1", "2", "1");
        store.remove("ndeRegType");
        assertTrue(go("Kano").contains("NDE EMPLOYMENT CONNECT"));
        go("1");
        store.put("ndeRegStep", "99");
        assertTrue(go("Kano").contains("NDE EMPLOYMENT CONNECT"));
    }

    @Test void phaseTwoStillWorksNextToPhaseThree() {
        go("1", "1", "1", "Musa Ibrahim", "Kano", "Dala", "5", "1");          // job seeker NDE1001
        assertEquals("NDE1001", memory.rows.get(0).reference());
        flow.start(session);
        go("1", "2", "1", "1", "Kano", "Welding");                            // NDE-JM1001
        flow.start(session);
        go("1", "1", "4");
        assertTrue(go("NDE1001").contains("Type: Job Seeker"));
        assertTrue(go("1", "NDE-JM1001").contains("Reference not found for this number."));   // different table
    }

    // ---- every screen has to fit on a USSD screen ----

    /** The visible length of a CON/END screen (0 for anything else); fails the test if it is over the limit. */
    private static int checkedLength(String screen, String[] journey) {
        if (!screen.startsWith("CON ") && !screen.startsWith("END ")) return 0;
        int length = visible(screen).length();
        assertTrue(length <= 170, "screen of " + length + " characters after " + String.join(",", journey) + ":\n" + screen);
        return length;
    }

    @Test void noScreenIsLongerThanAUssdScreenCanShow() {
        registerAnEmployer();
        matches.offset = 8_999_000;                              // seven-digit reference numbers, the longest case
        String longSkill = "Mechanical Engineering Repairs";    // 30 letters, the most a skill may be
        int longest = 0;
        String[][] journeys = {
            {"1", "1"}, {"1", "1", "1"}, {"1", "1", "1", "A".repeat(40), "Kano", "Dala", "5", "1"},
            {"1", "1", "2", "Acme Ltd", "Lagos", "Ikeja", "08031234567", "5"},
            {"1", "1", "3", "Amina Yusuf", "Kaduna", "Zaria", "3"},
            {"1", "1", "4", "NDE1001"}, {"1", "1", "4", "x"}, {"11"}, {"1", "11"},
            // job matching: every menu, prompt, notice and result, with the longest values
            {"2"}, {"1", "2"}, {"1", "2", "9"},
            {"1", "2", "1"}, {"1", "2", "1", "9"}, {"1", "2", "1", "1", "Narnia"}, {"1", "2", "1", "1", "Cross River", "1"},
            {"1", "2", "1", "1", "Cross River", longSkill},
            {"1", "2", "2", "1", "Cross River", longSkill, "abc"}, {"1", "2", "2", "1", "Cross River", longSkill, "1000"},
            {"1", "2", "3"}, {"1", "2", "3", "hello"}, {"1", "2", "3", "NDE-EMP9999"},
            {"1", "2", "3", "NDE-EMP1001", "1", "abc"}, {"1", "2", "3", "NDE-EMP1001", "1", "1000", "Cross River"},
            {"1", "2", "4"}, {"1", "2", "4", "hello"}, {"1", "2", "4", "NDE-JM9999999"}, {"1", "2", "4", "NDE-JM9999999", "7"},
        };
        for (String[] journey : journeys) {
            flow.start(session);
            for (String input : journey) {
                longest = Math.max(longest, checkedLength(flow.handle(session, input), journey));
            }
        }
        // Look up every request the journeys above saved (all three types, seven-digit references, longest values).
        assertEquals(3, matches.rows.stream().map(JobMatch::type).distinct().count());
        for (JobMatch saved : matches.rows) {
            String[] journey = {"1", "2", "4", saved.reference()};
            flow.start(session);
            String screen = null;
            for (String input : journey) screen = flow.handle(session, input);
            assertTrue(screen.contains("Reference: " + saved.reference()), screen);
            longest = Math.max(longest, checkedLength(screen, journey));
        }
        assertNotNull(memory);
        assertTrue(longest <= 170, "longest screen was " + longest + " characters");
    }
}