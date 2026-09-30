package com.example.tems.Tems.ussd;

import java.util.Map;

/** Initial-entry resolution only. Dialogue input must never be passed here mid-session. */
@org.springframework.stereotype.Component
public class UssdRouteResolver {
    public enum Route { TEMS, TMF, CBM, UNKNOWN }
    // private static final Map<String, Route> EXTENSIONS = Map.of("10", Route.TMF, "27", Route.CBM, "1", Route.CBM);
    private static final Map<String, Route> EXTENSIONS =
    Map.of("10", Route.TMF, "27", Route.CBM, "1", Route.CBM, "100", Route.TEMS);

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
