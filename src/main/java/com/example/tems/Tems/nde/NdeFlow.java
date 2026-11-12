package com.example.tems.Tems.nde;

import java.util.Locale;

/**
 * NDE (National Directorate of Employment) journey on *7447#, built one step at a time.
 *
 * STEP 1 (this version): reach NDE from "Search Organizations" by typing NDE, pick a language (only English works
 * for now), see the NDE main menu, and navigate (0 back, 00 main menu, 000 exit). Each service on the main menu shows a
 * "coming soon" screen until its own step is built.
 *
 * This class keeps no state of its own. The user's position lives in the caller's session store (the controller's
 * Redis session, per phone number) under the two keys below, so it behaves like the other flows.
 *
 * Every screen is written to stay under about 170 visible characters, because a USSD screen is cut off at roughly
 * 160 to 182 characters depending on the network.
 */
public final class NdeFlow {

    /** The caller's per-phone session store. Setting a null value removes the key. */
    public interface Session {
        String get(String key);
        void set(String key, String value);
    }

    /** Returned by handle() when the user backs out of NDE. The caller then shows the TEMS welcome menu. */
    public static final String TEMS_MENU = "@TEMS_MENU";

    public static final String FLOW_KEY = "ndeFlow";
    public static final String LANG_KEY = "ndeLang";

    private static final String S_LANGUAGE = "language";
    private static final String S_MAIN = "main_menu";
    private static final String S_SOON = "coming_soon";

    /** Titles of the ten services, in the order of the NDE main menu. */
    private static final String[] SERVICES = {
        "Register / Verify", "Job Matching", "Request", "Report", "Guidelines & Procedures",
        "Skills & Training", "Rural Employment", "Enterprise Support", "Check Status / Follow-up",
        "More Information"
    };

    /** What a user may type into "Search Organizations" to reach NDE. */
    public static boolean isSearchTerm(String term) {
        if (term == null) return false;
        String n = term.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
        s.set(FLOW_KEY, null);
        s.set(LANG_KEY, null);
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
            case S_LANGUAGE: return handleLanguage(s, choice);
            case S_MAIN:     return handleMain(s, choice);
            case S_SOON:     return handleSoon(s, choice);
            default:         return start(s);   // unknown state: start over rather than trap the user
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
}