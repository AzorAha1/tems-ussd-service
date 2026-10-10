package com.example.tems.Tems.ussd;

import java.util.Map;
import java.util.Set;

/** Initial-entry resolution only. Dialogue input must never be passed here mid-session. */
@org.springframework.stereotype.Component
public class UssdRouteResolver {
    public enum Route { TEMS, TMF, CBM, UNKNOWN }
    // private static final Map<String, Route> EXTENSIONS = Map.of("10", Route.TMF, "27", Route.CBM, "1", Route.CBM);
    private static final Map<String, Route> EXTENSIONS =
    Map.of("10", Route.TMF, "27", Route.CBM, "100", Route.TEMS,
        // City Boy Movement sub-codes *7447*1# to *7447*5#
        "1", Route.CBM, "2", Route.CBM, "3", Route.CBM, "4", Route.CBM, "5", Route.CBM);

    // MTN passes digits typed after *7447*100 through unchanged (production logs: 7447*100*2, 7447*100*6).
    // These suffixes open City Boy Movement: *7447*100*2#, *7447*100*20#, *7447*100*27#.
    // Both long and short forms are listed in case the network shortens the suffix to its first digit.
    // Any other suffix after 100 stays on the normal TEMS menu.
    private static final Set<String> CBM_AFTER_100 = Set.of("1", "2", "7", "27");

    public Route resolve(UssdInboundRequest request, boolean cumulative, boolean startEvidence) {
        String service = normalize(request.serviceCode());
        String input = normalize(request.input(cumulative));
        // An explicit extended service code is authoritative over input/text.
        if (!service.isEmpty() && !service.equals("7447")) return dialRoute(service);
        if (input.startsWith("7447*") || input.equals("744710") || input.equals("744727")) return dialRoute(input);
        if (input.equals("7447") || input.isEmpty()) return Route.TEMS;
        if (startEvidence) {
            String extension = cumulative ? input.split("\\*", -1)[0] : input;
            return EXTENSIONS.getOrDefault(extension, Route.UNKNOWN);
        }
        // Legacy bare 10/27 can start a session only when the caller has no active state.
        if (input.equals("10")) return Route.TMF;
        if (input.equals("27")) return Route.CBM;
        return Route.UNKNOWN;
    }

    private Route dialRoute(String value) {
        if (value.equals("744710")) return Route.TMF;
        if (value.equals("744727")) return Route.CBM;
        String[] parts = value.split("\\*", -1);
        if (parts.length >= 3 && parts[0].equals("7447") && parts[1].equals("100")) {
            return CBM_AFTER_100.contains(parts[2]) ? Route.CBM : Route.TEMS;
        }
        if (parts.length >= 2 && parts[0].equals("7447")) return EXTENSIONS.getOrDefault(parts[1], Route.UNKNOWN);
        return Route.UNKNOWN;
    }

    public String reply(UssdInboundRequest request, boolean cumulative) {
        String input = request.input(cumulative);
        if (!cumulative) return input;
        String[] parts = normalize(input).split("\\*", -1);
        return parts[parts.length - 1];
    }

    private String normalize(String value) {
        String result = value.trim();
        if (result.startsWith("*")) result = result.substring(1);
        if (result.endsWith("#")) result = result.substring(0, result.length() - 1);
        return result;
    }
}