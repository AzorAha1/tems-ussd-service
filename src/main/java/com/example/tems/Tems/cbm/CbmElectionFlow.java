package com.example.tems.Tems.cbm;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * City Boy Movement "Election Results Collection" module on *7447#, built one phase at a time. It is reached from the
 * CBM main menu (option 7).
 *
 * PHASE 1 (this version): the Election Results menu, and "Authorised Access Registration" - a polling-unit supervisor
 *          registers with name, City Boy Registration ID, state, LGA, ward and polling unit code. The registration is
 *          saved as PENDING; CBM authorises it by changing the status in the database. "Send Result" and
 *          "Report Incident" already check that the caller is an AUTHORISED supervisor, but their forms are not
 *          built yet, so an authorised supervisor sees "not open yet".
 * PHASE 2: Send Result.   PHASE 3: Report Incident.
 *
 * This class keeps no state of its own. The user's position and answers live in the caller's session (the controller's
 * Redis session, per phone number), and supervisors are saved through the Store, so the class has no dependency on
 * Spring or the database and can be tested on its own.
 *
 * Every screen stays under about 170 visible characters, because a USSD screen is cut off at roughly 160 to 182
 * characters depending on the network.
 */
public final class CbmElectionFlow {
    private static final Logger LOG = Logger.getLogger(CbmElectionFlow.class.getName());

    /** The caller's per-phone session store. Setting a null value removes the key. */
    public interface Session {
        String get(String key);
        void set(String key, String value);
        /** The caller's phone number in local format (07012345678). */
        String phone();
    }

    /** Where supervisors are saved and looked up. Implemented by CbmElectionSupervisorService. */
    public interface Store {
        /** Saves a new supervisor registration and returns its reference (for example CBM-SV1001). */
        String save(Supervisor supervisor);
        /** The supervisor registered from this phone number, or null. */
        Supervisor findByPhone(String phone);
        /** Asks CBM whether this City Boy Registration ID exists. Never throws for "not found". */
        MemberCheck checkMember(String memberId);
    }

    /** CBM's answer about a registration ID. {@code status} is CBM's own member status text, if it gave one. */
    public record MemberCheck(boolean confirmed, String status) {}

    /** A supervisor registration as the flow sees it. */
    public record Supervisor(String phone, String fullName, String memberId, String memberCheck, String memberStatus,
                             String state, String lga, String ward, String puCode,
                             String reference, String status, LocalDateTime createdAt) {}

    /** Returned by handle() when the user backs out of the election menu. The caller then shows the CBM main menu. */
    public static final String BACK_TO_CBM = "@CBM_MENU";

    /** Supervisor status, as stored. CBM changes it in the database: PENDING -> AUTHORISED or REJECTED. */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_AUTHORISED = "AUTHORISED";
    public static final String STATUS_REJECTED = "REJECTED";

    /** Whether CBM could confirm the registration ID when the supervisor registered. */
    public static final String CHECK_CONFIRMED = "CONFIRMED";
    public static final String CHECK_NOT_CONFIRMED = "NOT_CONFIRMED";

    public static final String FLOW_KEY = "cbmElection";
    static final String STEP_KEY = "cbmEsStep";
    static final String CHECK_KEY = "cbmEsCheck";
    static final String MEMBER_STATUS_KEY = "cbmEsMStatus";
    /** Answers to the registration questions, by position. */
    static final String[] ANSWER_KEYS = {"cbmEsA0", "cbmEsA1", "cbmEsA2", "cbmEsA3", "cbmEsA4", "cbmEsA5"};
    /** Every key this flow uses, so the caller can register them for session extension and reset. */
    public static final String[] SESSION_KEYS = {FLOW_KEY, STEP_KEY, CHECK_KEY, MEMBER_STATUS_KEY,
        "cbmEsA0", "cbmEsA1", "cbmEsA2", "cbmEsA3", "cbmEsA4", "cbmEsA5"};

    private static final String S_MENU = "menu";
    private static final String S_INFO = "info";
    private static final String S_REGISTER = "register";
    private static final String S_ID_CHECK = "id_check";
    private static final String S_CONFIRM = "confirm";

    private static final String UNAVAILABLE = "Service unavailable. Please try again later.";

    // ---- the registration questions, in the order they are asked ----

    private enum Kind { NAME, MEMBER_ID, STATE, LGA, WARD, PU_CODE }

    private record Step(String field, Kind kind, String prompt) {}

    private static final List<Step> STEPS = List.of(
        new Step("name", Kind.NAME, "Enter your full name:"),
        new Step("memberId", Kind.MEMBER_ID, "Enter your City Boy Registration ID:"),
        new Step("state", Kind.STATE, "Enter your state of voting (e.g. Kano):"),
        new Step("lga", Kind.LGA, "Enter your LGA:"),
        new Step("ward", Kind.WARD, "Enter your Ward:"),
        new Step("pu", Kind.PU_CODE, "Enter your Polling Unit Code\n(8 or 9 digits, e.g. 010203004):"));

    private static final int ID_STEP = 1;
    private static final int STATE_STEP = 2;
    /** The sample code printed on the screen. People copy it, so it is never accepted as an answer. */
    private static final String EXAMPLE_PU = "01/02/03/004";

    // ---- Nigerian states, so the state is stored in one consistent spelling ----

    private static final String[] STATE_NAMES = {
        "Abia", "Adamawa", "Akwa Ibom", "Anambra", "Bauchi", "Bayelsa", "Benue", "Borno", "Cross River", "Delta",
        "Ebonyi", "Edo", "Ekiti", "Enugu", "Gombe", "Imo", "Jigawa", "Kaduna", "Kano", "Katsina", "Kebbi", "Kogi",
        "Kwara", "Lagos", "Nasarawa", "Niger", "Ogun", "Ondo", "Osun", "Oyo", "Plateau", "Rivers", "Sokoto",
        "Taraba", "Yobe", "Zamfara", "FCT Abuja"
    };
    private static final Map<String, String> STATES = new HashMap<>();
    static {
        for (String name : STATE_NAMES) STATES.put(lettersOnly(name), name);
        STATES.put("fct", "FCT Abuja");
        STATES.put("abuja", "FCT Abuja");
        STATES.put("federalcapitalterritory", "FCT Abuja");
        STATES.put("nassarawa", "Nasarawa");
        STATES.put("abujafct", "FCT Abuja");
    }

    private final Supplier<Store> storeSupplier;

    /**
     * @param storeSupplier gives the Store when it is needed (the controller's service is injected after the flow is
     *                      constructed, so it is read lazily). It may return null; the flow then says "try again later".
     */
    public CbmElectionFlow(Supplier<Store> storeSupplier) {
        this.storeSupplier = storeSupplier;
    }

    /** Entry from the CBM main menu: always starts at the election menu with a clean state. */
    public String start(Session s) {
        clear(s);
        s.set(FLOW_KEY, S_MENU);
        return menu(null);
    }

    public void clear(Session s) {
        for (String key : SESSION_KEYS) s.set(key, null);
    }

    /** Every keypress after the entry. Returns "CON ...", "END ..." or BACK_TO_CBM. */
    public String handle(Session s, String input) {
        String choice = input == null ? "" : input.trim();
        String state = s.get(FLOW_KEY);
        if (state == null) return start(s);
        switch (state) {
            case S_MENU:     return handleMenu(s, choice);
            case S_INFO:     // a one-screen message: any key returns to the election menu
                s.set(FLOW_KEY, S_MENU);
                return menu(null);
            case S_REGISTER: return handleRegister(s, choice);
            case S_ID_CHECK: return handleIdCheck(s, choice);
            case S_CONFIRM:  return handleConfirm(s, choice);
            default:         return start(s);   // unknown state: do not trap the user
        }
    }

    // ---- the election menu ----

    private String menu(String notice) {
        return "CON " + (notice == null ? "" : notice + "\n")
            + "ELECTION RESULTS COLLECTION\n\n"
            + "1. Authorised Access Registration\n"
            + "2. Send Result\n"
            + "3. Report Incident\n"
            + "4. Back";
    }

    private String handleMenu(Session s, String choice) {
        switch (choice) {
            case "1": return startRegistration(s);
            case "2": return gate(s, "Send Result");
            case "3": return gate(s, "Report Incident");
            case "4":
            case "0":
                clear(s);
                return BACK_TO_CBM;
            default:
                return menu("Invalid choice.");
        }
    }

    private String info(Session s, String text) {
        s.set(FLOW_KEY, S_INFO);
        return "CON " + text + "\n\n0. Back";
    }

    // ---- who may use Send Result and Report Incident ----

    private record Lookup(boolean available, Supervisor supervisor) {}

    private Lookup lookup(Session s) {
        try {
            Store store = storeSupplier == null ? null : storeSupplier.get();
            if (store == null) return new Lookup(false, null);
            return new Lookup(true, store.findByPhone(s.phone()));
        } catch (RuntimeException e) {
            LOG.warning("cbm_election lookup_failed cause=" + e.getClass().getSimpleName());
            return new Lookup(false, null);
        }
    }

    /** Only an AUTHORISED supervisor gets past here. Their forms come in later phases. */
    private String gate(Session s, String label) {
        Lookup found = lookup(s);
        if (!found.available()) return info(s, UNAVAILABLE);
        Supervisor supervisor = found.supervisor();
        if (supervisor == null) {
            return info(s, label + " is for authorised supervisors.\nRegister first under Authorised Access Registration.");
        }
        String status = supervisor.status() == null ? "" : supervisor.status();
        switch (status) {
            case STATUS_AUTHORISED:
                return info(s, "Your access is authorised.\n" + label + " is not open yet.");
            case STATUS_REJECTED:
                return info(s, label + " is for authorised supervisors.\nYour access was not authorised. Contact CBM.");
            default:
                return info(s, label + " is for authorised supervisors.\nYour access is pending authorisation.\nRef: "
                    + supervisor.reference());
        }
    }

    // ---- Authorised Access Registration ----

    private String startRegistration(Session s) {
        Lookup found = lookup(s);
        if (!found.available()) return info(s, UNAVAILABLE);
        Supervisor existing = found.supervisor();
        if (existing != null) {   // one registration per phone number: show it instead of making a second
            return info(s, "You are already registered.\nRef: " + existing.reference() + "\nStatus: "
                + statusLabel(existing.status()) + "\n\nTo change your details, contact CBM.");
        }
        clearRegistration(s);
        s.set(FLOW_KEY, S_REGISTER);
        s.set(STEP_KEY, "0");
        return prompt(0, null);
    }

    private String handleRegister(Session s, String choice) {
        int index = parseStep(s.get(STEP_KEY));
        if (index < 0 || index >= STEPS.size()) {   // lost or damaged state: do not trap the user
            clearRegistration(s);
            s.set(FLOW_KEY, S_MENU);
            return menu(null);
        }

        if (choice.equals("0")) {   // back one question; from the first question, back to the election menu
            if (index == 0) {
                clearRegistration(s);
                s.set(FLOW_KEY, S_MENU);
                return menu(null);
            }
            s.set(STEP_KEY, String.valueOf(index - 1));
            return prompt(index - 1, null);
        }

        Step current = STEPS.get(index);
        String value = validate(current.kind(), choice);
        if (value == null) return prompt(index, invalidNotice(current.kind()));
        if (current.kind() == Kind.PU_CODE) {
            String problem = puProblem(s.get(ANSWER_KEYS[STATE_STEP]), value);
            if (problem != null) return prompt(index, problem);
        }

        s.set(ANSWER_KEYS[index], value);
        if (current.kind() == Kind.MEMBER_ID) return checkMemberId(s, value);
        return advance(s, index);
    }

    /** Moves to the next question, or to the confirm screen after the last one. */
    private String advance(Session s, int index) {
        if (index + 1 < STEPS.size()) {
            s.set(STEP_KEY, String.valueOf(index + 1));
            return prompt(index + 1, null);
        }
        s.set(FLOW_KEY, S_CONFIRM);
        return confirmScreen(s, null);
    }

    /**
     * Asks CBM about the ID as soon as it is typed. If CBM cannot confirm it (wrong ID, or CBM is unreachable - the two
     * look the same from here) the supervisor chooses: type it again, or continue and let CBM check it later.
     */
    private String checkMemberId(Session s, String memberId) {
        MemberCheck check = null;
        try {
            Store store = storeSupplier == null ? null : storeSupplier.get();
            if (store != null) check = store.checkMember(memberId);
        } catch (RuntimeException e) {
            LOG.warning("cbm_election member_check_failed cause=" + e.getClass().getSimpleName());
        }
        if (check != null && check.confirmed()) {
            s.set(CHECK_KEY, CHECK_CONFIRMED);
            s.set(MEMBER_STATUS_KEY, check.status());
            return advance(s, ID_STEP);
        }
        s.set(CHECK_KEY, null);
        s.set(MEMBER_STATUS_KEY, null);
        s.set(FLOW_KEY, S_ID_CHECK);
        return idCheckScreen(null);
    }

    private String idCheckScreen(String notice) {
        return "CON " + (notice == null ? "" : notice + "\n")
            + "We could not confirm that ID right now.\n\n1. Re-enter ID\n2. Continue, CBM will check it\n0. Back";
    }

    private String handleIdCheck(Session s, String choice) {
        switch (choice) {
            case "1":
                s.set(FLOW_KEY, S_REGISTER);
                s.set(STEP_KEY, String.valueOf(ID_STEP));
                return prompt(ID_STEP, null);
            case "2":
                s.set(CHECK_KEY, CHECK_NOT_CONFIRMED);
                s.set(FLOW_KEY, S_REGISTER);
                return advance(s, ID_STEP);
            case "0":
                s.set(FLOW_KEY, S_REGISTER);
                s.set(STEP_KEY, String.valueOf(ID_STEP - 1));
                return prompt(ID_STEP - 1, null);
            default:
                return idCheckScreen("Invalid choice.");
        }
    }

    private String confirmScreen(Session s, String notice) {
        String header = notice == null ? "Confirm details:" : notice;
        return "CON " + header
            + "\n" + shorten(s.get(ANSWER_KEYS[0]), 22)
            + "\nID: " + shorten(s.get(ANSWER_KEYS[1]), 16)
            + "\n" + shorten(s.get(ANSWER_KEYS[2]), 11) + ", " + shorten(s.get(ANSWER_KEYS[3]), 16)
            + "\nWard: " + shorten(s.get(ANSWER_KEYS[4]), 16)
            + "\nPU: " + shorten(s.get(ANSWER_KEYS[5]), 14)
            + "\n\n1. Confirm\n2. Re-enter\n0. Back";
    }

    private String handleConfirm(Session s, String choice) {
        if (!allAnswered(s)) {   // lost or damaged state: do not trap the user
            clearRegistration(s);
            s.set(FLOW_KEY, S_MENU);
            return menu(null);
        }
        switch (choice) {
            case "1":
                return finishRegistration(s);
            case "2":   // start the questions again
                clearRegistration(s);
                s.set(FLOW_KEY, S_REGISTER);
                s.set(STEP_KEY, "0");
                return prompt(0, null);
            case "0":   // back to the last question
                s.set(FLOW_KEY, S_REGISTER);
                s.set(STEP_KEY, String.valueOf(STEPS.size() - 1));
                return prompt(STEPS.size() - 1, null);
            default:
                return confirmScreen(s, "Invalid choice.");
        }
    }

    private String finishRegistration(Session s) {
        String check = CHECK_CONFIRMED.equals(s.get(CHECK_KEY)) ? CHECK_CONFIRMED : CHECK_NOT_CONFIRMED;
        Supervisor draft = new Supervisor(s.phone(), s.get(ANSWER_KEYS[0]), s.get(ANSWER_KEYS[1]), check,
            s.get(MEMBER_STATUS_KEY), s.get(ANSWER_KEYS[2]), s.get(ANSWER_KEYS[3]), s.get(ANSWER_KEYS[4]),
            s.get(ANSWER_KEYS[5]), null, STATUS_PENDING, LocalDateTime.now());
        clear(s);   // the answers are copied into the draft; the session is finished either way

        String reference = save(draft);
        if (reference == null) {
            return "END We could not save your registration. Please try again later.";
        }
        return "END Registration received.\n\nRef: " + reference + "\nStatus: " + statusLabel(STATUS_PENDING)
            + "\n\nCBM will review and confirm your access.\n\nKeep your reference number.";
    }

    private String save(Supervisor draft) {
        try {
            Store store = storeSupplier == null ? null : storeSupplier.get();
            return store == null ? null : store.save(draft);
        } catch (RuntimeException e) {
            LOG.warning("cbm_election save_failed cause=" + e.getClass().getSimpleName());
            return null;
        }
    }

    private boolean allAnswered(Session s) {
        for (String key : ANSWER_KEYS) if (s.get(key) == null) return false;
        return true;
    }

    private void clearRegistration(Session s) {
        s.set(STEP_KEY, null);
        s.set(CHECK_KEY, null);
        s.set(MEMBER_STATUS_KEY, null);
        for (String key : ANSWER_KEYS) s.set(key, null);
    }

    private String prompt(int index, String notice) {
        return "CON " + (notice == null ? "" : notice + "\n") + STEPS.get(index).prompt() + "\n\n0. Back";
    }

    private static int parseStep(String value) {
        if (value == null) return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String invalidNotice(Kind kind) {
        switch (kind) {
            case NAME:      return "Please enter a valid name.";
            case MEMBER_ID: return "Invalid ID. Enter it as 123456 or CBM-2026-NG-123456.";
            case STATE:     return "State not recognised.";
            case LGA:       return "Please enter a valid LGA.";
            case WARD:      return "Please enter a valid ward.";
            default:        return "Enter 8 or 9 digits, e.g. 010203004.";
        }
    }

    /** The cleaned value to store, or null if the input is not acceptable for this question. */
    private static String validate(Kind kind, String raw) {
        String v = raw.trim().replaceAll("\\s+", " ");
        switch (kind) {
            case NAME:
                return v.matches("^[\\p{L}][\\p{L} .'\\-]{1,59}$") ? v : null;
            case MEMBER_ID: {   // a City Boy ID: 5 to 7 digits, or the full form CBM-2026-NG-123456 (kept in capitals)
                if (v.matches("^[0-9]{5,7}$")) return v;
                return v.matches("(?i)^CBM-[0-9]{4}-NG-[0-9]{5,7}$") ? v.toUpperCase(java.util.Locale.ROOT) : null;
            }
            case STATE: {     // accepts "Kano", "kano state", "FCT", "Abuja" ...
                String key = lettersOnly(v);
                String found = STATES.get(key);
                if (found == null && key.endsWith("state")) found = STATES.get(key.substring(0, key.length() - 5));
                return found;
            }
            case LGA:
                return v.matches("^[\\p{L}][\\p{L} .'\\-/()]{1,39}$") ? v : null;
            case WARD:
                return v.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N} .'\\-/()]{0,39}$") ? v : null;
            case PU_CODE:
                return normalizePollingUnit(v);
            default:
                return null;
        }
    }

    /**
     * INEC polling unit codes are State/LGA/Ward/Unit, for example 01/02/03/004, printed as an 8 or 9 digit number.
     * People cannot type slashes on a USSD pad, so the digits alone are accepted (separators too) and the code is
     * stored in the slash form.
     */
    static String normalizePollingUnit(String v) {
        if (!v.matches("^[0-9 /.\\-]+$")) return null;
        String digits = v.replaceAll("[^0-9]", "");
        if (!digits.matches("^[0-9]{8,9}$")) return null;
        return digits.substring(0, 2) + "/" + digits.substring(2, 4) + "/" + digits.substring(4, 6) + "/"
            + digits.substring(6);
    }

    /** Why a polling unit code cannot be right for this state, or null if it can be. */
    static String puProblem(String state, String code) {
        if (EXAMPLE_PU.equals(code)) return "That is only an example. Enter your own polling unit code.";
        int number = stateNumber(state);
        if (number > 0) {
            String prefix = String.format("%02d", number);
            if (!code.startsWith(prefix + "/")) return state + " codes start with " + prefix + ". Check and try again.";
        }
        return null;
    }

    /** INEC's number for a state: alphabetical, 1 for Abia to 36 for Zamfara, 37 for FCT. -1 if unknown. */
    static int stateNumber(String state) {
        for (int i = 0; i < STATE_NAMES.length; i++) {
            if (STATE_NAMES[i].equals(state)) return i + 1;
        }
        return -1;
    }

    /** Text shown to the supervisor for a stored status. */
    public static String statusLabel(String status) {
        if (STATUS_AUTHORISED.equals(status)) return "Authorised";
        if (STATUS_REJECTED.equals(status)) return "Not authorised";
        return "Pending authorisation";
    }

    private static String lettersOnly(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }
}
