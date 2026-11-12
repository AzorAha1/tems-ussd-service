package com.example.tems.Tems.nde;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * NDE (National Directorate of Employment) journey on *7447#, built one phase at a time.
 *
 * PHASE 1: reach NDE from "Search Organizations" by typing NDE, pick a language (only English works for now) and see
 *          the NDE main menu. Navigation: 0 back, 00 main menu, 000 exit.
 * PHASE 2 (this version): "1 Register / Verify" works: job-seeker, employer and skills-training registration, and
 *          verification of a registration by its reference number. Registrations are saved through a Store.
 *          The other nine services still show a "coming soon" screen.
 *
 * This class keeps no state of its own. The user's position and answers live in the caller's session (the controller's
 * Redis session, per phone number), and registrations are saved through the Store, so the class has no dependency on
 * Spring or the database and can be tested on its own.
 *
 * Every screen stays under about 170 visible characters, because a USSD screen is cut off at roughly 160 to 182
 * characters depending on the network.
 */
public final class NdeFlow {
    private static final Logger LOG = Logger.getLogger(NdeFlow.class.getName());

    /** The caller's per-phone session store. Setting a null value removes the key. */
    public interface Session {
        String get(String key);
        void set(String key, String value);
        /** The caller's phone number in local format (07012345678). */
        String phone();
    }

    /** Where registrations are saved. Implemented by NdeRegistrationService. */
    public interface Store {
        /** Saves a new registration and returns its reference (for example NDE1001). */
        String save(Registration registration);
        /** The registration with this reference, only if it was made from this phone number; otherwise null. */
        Registration findForPhone(String reference, String phone);
    }

    /** A registration as the flow sees it. Fields that do not apply to a type are null. */
    public record Registration(String type, String phone, String name, String state, String lga,
                               String qualification, String employmentCategory, String contactPhone,
                               String businessSector, String preferredTrade,
                               String reference, String status, LocalDateTime createdAt) {}

    /** Returned by handle() when the user backs out of NDE. The caller then shows the TEMS welcome menu. */
    public static final String TEMS_MENU = "@TEMS_MENU";

    public static final String JOB_SEEKER = "JOB_SEEKER";
    public static final String EMPLOYER = "EMPLOYER";
    public static final String TRAINING = "TRAINING";

    public static final String FLOW_KEY = "ndeFlow";
    public static final String LANG_KEY = "ndeLang";
    static final String REG_TYPE_KEY = "ndeRegType";
    static final String REG_STEP_KEY = "ndeRegStep";
    /** Answers to the registration questions, by position. */
    static final String[] ANSWER_KEYS = {"ndeA0", "ndeA1", "ndeA2", "ndeA3", "ndeA4"};
    /** Every key this flow uses, so the caller can register them for session extension. */
    public static final String[] SESSION_KEYS = {FLOW_KEY, LANG_KEY, REG_TYPE_KEY, REG_STEP_KEY,
        "ndeA0", "ndeA1", "ndeA2", "ndeA3", "ndeA4"};

    private static final String S_LANGUAGE = "language";
    private static final String S_MAIN = "main_menu";
    private static final String S_SOON = "coming_soon";
    private static final String S_REG_MENU = "reg_menu";
    private static final String S_REGISTER = "register";
    private static final String S_VERIFY_REF = "verify_ref";
    private static final String S_VERIFY_RESULT = "verify_result";

    /** Titles of the ten services, in the order of the NDE main menu. */
    private static final String[] SERVICES = {
        "Register / Verify", "Job Matching", "Request", "Report", "Guidelines & Procedures",
        "Skills & Training", "Rural Employment", "Enterprise Support", "Check Status / Follow-up",
        "More Information"
    };

    // ---- the registration questions, in the order they are asked ----

    private enum Kind { NAME, ORG, STATE, LGA, PHONE, CHOICE }

    private record Step(String field, Kind kind, String prompt, String[] options) {}

    private static Step step(String field, Kind kind, String prompt, String... options) {
        return new Step(field, kind, prompt, options);
    }

    private static final List<Step> JOB_SEEKER_STEPS = List.of(
        step("name", Kind.NAME, "Enter your full name:"),
        step("state", Kind.STATE, "Enter your state (e.g. Kano):"),
        step("lga", Kind.LGA, "Enter your LGA:"),
        step("qualification", Kind.CHOICE, "Select Qualification:",
            "No Formal Education", "Primary", "Secondary", "NCE / OND", "HND / Degree", "Other"),
        step("category", Kind.CHOICE, "Select Employment Category:",
            "Skilled / Technical", "Administrative", "Agriculture", "General Labour", "Other"));

    private static final List<Step> EMPLOYER_STEPS = List.of(
        step("name", Kind.ORG, "Enter business / organization name:"),
        step("state", Kind.STATE, "Enter the state:"),
        step("lga", Kind.LGA, "Enter the LGA:"),
        step("contact", Kind.PHONE, "Enter contact phone number:"),
        step("sector", Kind.CHOICE, "Select Business Sector:",
            "Trading", "Agriculture", "Manufacturing", "Services", "Other"));

    private static final List<Step> TRAINING_STEPS = List.of(
        step("name", Kind.NAME, "Enter your full name:"),
        step("state", Kind.STATE, "Enter your state (e.g. Kano):"),
        step("lga", Kind.LGA, "Enter your LGA:"),
        step("trade", Kind.CHOICE, "Select Preferred Trade:",
            "Tailoring", "Welding", "Electrical Installation", "Catering", "Other"));

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

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final Supplier<Store> storeSupplier;

    /**
     * @param storeSupplier gives the Store when it is needed (the controller's service is injected after the flow is
     *                      constructed, so it is read lazily). It may return null; registration then says "try again later".
     */
    public NdeFlow(Supplier<Store> storeSupplier) {
        this.storeSupplier = storeSupplier;
    }

    /** What a user may type into "Search Organizations" to reach NDE. */
    public static boolean isSearchTerm(String term) {
        if (term == null) return false;
        String n = lettersOnly(term);
        return n.equals("nde") || n.equals("ndeservices") || n.equals("ndeemploymentconnect")
            || n.equals("nationaldirectorateofemployment");
    }

    /** Entry from the search: always starts at the language screen with a clean NDE state. */
    public String start(Session s) {
        clear(s);
        s.set(FLOW_KEY, S_LANGUAGE);
        return languageMenu(null);
    }

    public void clear(Session s) {
        for (String key : SESSION_KEYS) s.set(key, null);
    }

    /** Every keypress after the entry. Returns "CON ...", "END ..." or TEMS_MENU. */
    public String handle(Session s, String input) {
        String choice = input == null ? "" : input.trim();
        String state = s.get(FLOW_KEY);
        if (state == null) return start(s);

        // Navigation that works at every level.
        if (choice.equals("000")) {
            clear(s);
            return "END Thank you for using NDE Services.";
        }
        if (choice.equals("00")) {
            return showMain(s);
        }

        switch (state) {
            case S_LANGUAGE:      return handleLanguage(s, choice);
            case S_MAIN:          return handleMain(s, choice);
            case S_SOON:          return handleSoon(s, choice);
            case S_REG_MENU:      return handleRegMenu(s, choice);
            case S_REGISTER:      return handleRegister(s, choice);
            case S_VERIFY_REF:    return handleVerifyRef(s, choice);
            case S_VERIFY_RESULT: return handleVerifyResult(s, choice);
            default:              return start(s);   // unknown state: start over rather than trap the user
        }
    }

    // ---- language ----

    private String handleLanguage(Session s, String choice) {
        switch (choice) {
            case "1":
                s.set(LANG_KEY, "EN");
                s.set(FLOW_KEY, S_MAIN);
                return mainMenu();
            case "2": return languageMenu("Hausa is not available yet.");
            case "3": return languageMenu("Yoruba is not available yet.");
            case "4": return languageMenu("Igbo is not available yet.");
            case "5": return languageMenu("Pidgin is not available yet.");
            case "0":
                clear(s);
                return TEMS_MENU;
            default:
                return languageMenu("Invalid choice.");
        }
    }

    private String languageMenu(String notice) {
        return "CON " + (notice == null ? "" : notice + "\n")
            + "WELCOME TO NDE SERVICES\n\nSelect Preferred Language:\n"
            + "1. English\n2. Hausa\n3. Yoruba\n4. Igbo\n5. Pidgin\n0. Back";
    }

    // ---- main menu ----

    /** 00 from anywhere: the main menu, or the language screen if no language has been chosen yet. */
    private String showMain(Session s) {
        clearRegistration(s);
        if (s.get(LANG_KEY) == null) {
            s.set(FLOW_KEY, S_LANGUAGE);
            return languageMenu(null);
        }
        s.set(FLOW_KEY, S_MAIN);
        return mainMenu();
    }

    private String handleMain(Session s, String choice) {
        if (choice.equals("0")) {
            s.set(FLOW_KEY, S_LANGUAGE);
            return languageMenu(null);
        }
        int index = serviceIndex(choice);
        if (index < 0) return invalidMainMenu();
        if (index == 0) {   // 1 Register / Verify
            s.set(FLOW_KEY, S_REG_MENU);
            return regMenu(null);
        }
        s.set(FLOW_KEY, S_SOON);
        return "CON " + SERVICES[index] + "\n\nThis service is coming soon.\n\n0. Back\n00. Main Menu\n000. Exit";
    }

    /** "1".."10" -> 0..9, anything else -> -1. */
    private int serviceIndex(String choice) {
        if (!choice.matches("[0-9]{1,2}")) return -1;
        int n = Integer.parseInt(choice);
        return (n >= 1 && n <= SERVICES.length) ? n - 1 : -1;
    }

    // Short labels on purpose: the full text of ten items does not fit on one USSD screen. The full names appear on
    // each service's own screens.
    private String mainMenu() {
        return "CON NDE SERVICES\n1.Register/Verify\n2.Job Matching\n3.Request\n4.Report\n5.Guidelines\n"
            + "6.Skills & Training\n7.Rural Employment\n8.Enterprise Support\n9.Check Status\n10.More Info";
    }

    /** The menu without its header, so "Invalid." still fits on the screen. */
    private String invalidMainMenu() {
        return "CON Invalid.\n1.Register/Verify\n2.Job Matching\n3.Request\n4.Report\n5.Guidelines\n"
            + "6.Skills & Training\n7.Rural Employment\n8.Enterprise Support\n9.Check Status\n10.More Info";
    }

    // ---- placeholder for services not built yet ----

    private String handleSoon(Session s, String choice) {
        if (choice.equals("0")) {
            s.set(FLOW_KEY, S_MAIN);
            return mainMenu();
        }
        return "CON This service is coming soon.\n\n0. Back\n00. Main Menu\n000. Exit";
    }

    // ---- Register / Verify menu ----

    private String regMenu(String notice) {
        return "CON " + (notice == null ? "" : notice + "\n")
            + "Register / Verify\n\n1. Register as Job Seeker\n2. Register as Employer\n"
            + "3. Skills Training Registration\n4. Verify Registration\n0. Back";
    }

    private String handleRegMenu(Session s, String choice) {
        switch (choice) {
            case "1": return beginRegistration(s, JOB_SEEKER);
            case "2": return beginRegistration(s, EMPLOYER);
            case "3": return beginRegistration(s, TRAINING);
            case "4":
                s.set(FLOW_KEY, S_VERIFY_REF);
                return verifyPrompt(null);
            case "0":
                s.set(FLOW_KEY, S_MAIN);
                return mainMenu();
            default:
                return regMenu("Invalid choice.");
        }
    }

    // ---- registration ----

    private String beginRegistration(Session s, String type) {
        clearRegistration(s);
        s.set(FLOW_KEY, S_REGISTER);
        s.set(REG_TYPE_KEY, type);
        s.set(REG_STEP_KEY, "0");
        return prompt(stepsFor(type).get(0), null);
    }

    private String handleRegister(Session s, String choice) {
        String type = s.get(REG_TYPE_KEY);
        int index = parseStep(s.get(REG_STEP_KEY));
        List<Step> steps = type == null ? null : stepsFor(type);
        if (steps == null || index < 0 || index >= steps.size()) {   // lost or damaged state: do not trap the user
            clearRegistration(s);
            s.set(FLOW_KEY, S_REG_MENU);
            return regMenu(null);
        }

        if (choice.equals("0")) {   // back one question; from the first question, back to the Register / Verify menu
            if (index == 0) {
                clearRegistration(s);
                s.set(FLOW_KEY, S_REG_MENU);
                return regMenu(null);
            }
            s.set(REG_STEP_KEY, String.valueOf(index - 1));
            return prompt(steps.get(index - 1), null);
        }

        Step current = steps.get(index);
        String value = validate(current, choice);
        if (value == null) return prompt(current, invalidNotice(current.kind()));

        s.set(ANSWER_KEYS[index], value);
        if (index + 1 < steps.size()) {
            s.set(REG_STEP_KEY, String.valueOf(index + 1));
            return prompt(steps.get(index + 1), null);
        }
        return finishRegistration(s, type, steps);
    }

    private String finishRegistration(Session s, String type, List<Step> steps) {
        Registration draft = new Registration(type, s.phone(),
            answer(s, steps, "name"), answer(s, steps, "state"), answer(s, steps, "lga"),
            answer(s, steps, "qualification"), answer(s, steps, "category"), answer(s, steps, "contact"),
            answer(s, steps, "sector"), answer(s, steps, "trade"),
            null, statusFor(type), LocalDateTime.now());
        clear(s);   // the answers are copied into the draft; the session is finished either way

        String reference = save(draft);
        if (reference == null) {
            return "END We could not save your registration. Please try again later.";
        }
        switch (type) {
            case JOB_SEEKER:
                return "END NDE Registration Request Received.\n\nName: " + shorten(draft.name(), 24)
                    + "\nCategory: " + draft.employmentCategory() + "\nReference: " + reference
                    + "\n\nKeep your reference number for future enquiries.";
            case EMPLOYER:
                return "END Employer Registration Request Received.\n\nReference: " + reference
                    + "\n\nYour registration is awaiting verification.";
            default:
                return "END Skills Training Registration Received.\n\nTrade: " + draft.preferredTrade()
                    + "\nReference: " + reference + "\n\nKeep your reference number for follow-up.";
        }
    }

    private String save(Registration draft) {
        try {
            Store store = storeSupplier == null ? null : storeSupplier.get();
            return store == null ? null : store.save(draft);
        } catch (RuntimeException e) {
            LOG.warning("nde_registration save_failed type=" + draft.type() + " cause=" + e.getClass().getSimpleName());
            return null;
        }
    }

    private static List<Step> stepsFor(String type) {
        switch (type) {
            case JOB_SEEKER: return JOB_SEEKER_STEPS;
            case EMPLOYER:   return EMPLOYER_STEPS;
            case TRAINING:   return TRAINING_STEPS;
            default:         return null;
        }
    }

    private static String statusFor(String type) {
        return EMPLOYER.equals(type) ? "Awaiting verification" : "Submitted";
    }

    private static String answer(Session s, List<Step> steps, String field) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).field().equals(field)) return s.get(ANSWER_KEYS[i]);
        }
        return null;
    }

    private static int parseStep(String value) {
        if (value == null) return -1;
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { return -1; }
    }

    private void clearRegistration(Session s) {
        s.set(REG_TYPE_KEY, null);
        s.set(REG_STEP_KEY, null);
        for (String key : ANSWER_KEYS) s.set(key, null);
    }

    private String prompt(Step step, String notice) {
        StringBuilder sb = new StringBuilder("CON ");
        if (notice != null) sb.append(notice).append('\n');
        sb.append(step.prompt());
        if (step.kind() == Kind.CHOICE) {
            for (int i = 0; i < step.options().length; i++) {
                sb.append('\n').append(i + 1).append(". ").append(step.options()[i]);
            }
            sb.append("\n0. Back");
        } else {
            sb.append("\n\n0. Back");
        }
        return sb.toString();
    }

    private static String invalidNotice(Kind kind) {
        switch (kind) {
            case NAME:   return "Please enter a valid name.";
            case ORG:    return "Please enter a valid name.";
            case STATE:  return "State not recognised.";
            case LGA:    return "Please enter a valid LGA.";
            case PHONE:  return "Use 11 digits, e.g. 08012345678.";
            default:     return "Invalid choice.";
        }
    }

    /** The cleaned value to store, or null if the input is not acceptable for this question. */
    private static String validate(Step step, String raw) {
        String v = raw.trim().replaceAll("\\s+", " ");
        switch (step.kind()) {
            case NAME:  return v.matches("^[\\p{L}][\\p{L} .'\\-]{1,39}$") ? v : null;
            case ORG:   return v.matches("^[\\p{L}\\p{N}][\\p{L}\\p{N} &.,'\\-/()]{1,59}$") ? v : null;
            case STATE: {   // accepts "Kano", "kano state", "FCT", "Abuja" ...
                String key = lettersOnly(v);
                String found = STATES.get(key);
                if (found == null && key.endsWith("state")) found = STATES.get(key.substring(0, key.length() - 5));
                return found;
            }
            case LGA:   return v.matches("^[\\p{L}][\\p{L} .'\\-/]{1,39}$") ? v : null;
            case PHONE: return normalizePhone(v);
            case CHOICE:
                if (!v.matches("[0-9]{1,2}")) return null;
                int n = Integer.parseInt(v);
                return (n >= 1 && n <= step.options().length) ? step.options()[n - 1] : null;
            default:    return null;
        }
    }

    /** 11 digits starting 07/08/09, or +234 / 234 followed by 10 digits, returned in local format. */
    private static String normalizePhone(String v) {
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.startsWith("234") && digits.length() == 13) digits = "0" + digits.substring(3);
        return digits.matches("^0[789][01][0-9]{8}$") ? digits : null;
    }

    // ---- verify a registration ----

    private String verifyPrompt(String notice) {
        return "CON " + (notice == null ? "" : notice + "\n")
            + "Enter your registration reference (e.g. NDE1001):\n\n0. Back";
    }

    private String handleVerifyRef(Session s, String choice) {
        if (choice.equals("0")) {
            s.set(FLOW_KEY, S_REG_MENU);
            return regMenu(null);
        }
        String reference = choice.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        if (!reference.matches("^NDE(-[A-Z]{2,4})?[0-9]{4,9}$")) {
            return verifyPrompt("Invalid reference.");
        }
        Registration found;
        try {
            Store store = storeSupplier == null ? null : storeSupplier.get();
            if (store == null) {
                s.set(FLOW_KEY, S_VERIFY_RESULT);
                return "CON Service unavailable. Please try again later.\n\n1. Verify another\n0. Back";
            }
            found = store.findForPhone(reference, s.phone());
        } catch (RuntimeException e) {
            LOG.warning("nde_registration lookup_failed cause=" + e.getClass().getSimpleName());
            s.set(FLOW_KEY, S_VERIFY_RESULT);
            return "CON Service unavailable. Please try again later.\n\n1. Verify another\n0. Back";
        }
        s.set(FLOW_KEY, S_VERIFY_RESULT);
        if (found == null) {
            return "CON Reference not found for this number.\n\n1. Verify another\n0. Back";
        }
        return "CON Reference: " + found.reference() + "\nType: " + typeLabel(found.type())
            + "\nStatus: " + found.status()
            + (found.createdAt() == null ? "" : "\nDate: " + found.createdAt().format(DATE))
            + "\n\n1. Verify another\n0. Back";
    }

    private String handleVerifyResult(Session s, String choice) {
        switch (choice) {
            case "1":
                s.set(FLOW_KEY, S_VERIFY_REF);
                return verifyPrompt(null);
            case "0":
                s.set(FLOW_KEY, S_REG_MENU);
                return regMenu(null);
            default:
                return "CON Invalid choice.\n\n1. Verify another\n0. Back";
        }
    }

    private static String typeLabel(String type) {
        switch (type) {
            case JOB_SEEKER: return "Job Seeker";
            case EMPLOYER:   return "Employer";
            case TRAINING:   return "Skills Training";
            default:         return type;
        }
    }

    // ---- small helpers ----

    private static String lettersOnly(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String shorten(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }
}