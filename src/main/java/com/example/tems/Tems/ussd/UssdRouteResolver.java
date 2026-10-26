package com.example.tems.Tems.ussd;

import java.util.Map;

/** Initial-entry resolution only. Dialogue input must never be passed here mid-session. */
@org.springframework.stereotype.Component
public class UssdRouteResolver {
    public enum Route { TEMS, TMF, CBM, UNKNOWN }
    private final UssdRouteParser parser = new UssdRouteParser();
    private static final Map<String, String> ROUTE_ALIASES = Map.of("10", "100*10", "11", "100*1");
    private static final Map<String, Route> CANONICAL_ROUTES = Map.ofEntries(
        Map.entry("7447", Route.TEMS), Map.entry("100", Route.TEMS),
        Map.entry("100*10", Route.TMF),
        Map.entry("1", Route.CBM), Map.entry("2", Route.CBM), Map.entry("3", Route.CBM),
        Map.entry("4", Route.CBM), Map.entry("5", Route.CBM), Map.entry("27", Route.CBM),
        Map.entry("100*1", Route.CBM), Map.entry("100*2", Route.CBM),
        Map.entry("100*7", Route.CBM), Map.entry("100*27", Route.CBM));

    public record Resolution(UssdRouteParser.Path path, String canonicalRoute, Route target) {
        @Override public String toString() { return "UssdResolution[target=" + target + "]"; }
    }

    public Resolution resolveEntry(UssdInboundRequest request, boolean cumulative, boolean startEvidence) {
        var path = parser.initialPath(request, cumulative, startEvidence);
        String canonical = canonicalize(path);
        Route target = CANONICAL_ROUTES.get(canonical);
        // Preserve the established TEMS fallback for unregistered numeric suffixes after 100.
        if (target == null) target = path.valid() && path.baseCode().equals("7447")
            && path.transportPrefix().equals("100") ? Route.TEMS : Route.UNKNOWN;
        return new Resolution(path, canonical, target);
    }

    public String canonicalize(UssdRouteParser.Path path) {
        if (!path.valid() || !path.baseCode().equals("7447")) return "";
        String entry = path.transportPrefix().isEmpty() ? path.publicRoute()
            : path.transportPrefix() + (path.publicRoute().isEmpty() ? "" : "*" + path.publicRoute());
        if (entry.isEmpty()) entry = "7447";
        return ROUTE_ALIASES.getOrDefault(entry, entry);
    }

    public Route resolve(UssdInboundRequest request, boolean cumulative, boolean startEvidence) {
        return resolveEntry(request, cumulative, startEvidence).target();
    }

    /** Only registered canonical identifiers can be emitted to diagnostics. */
    public static boolean isRegisteredCanonicalRoute(String route) {
        return CANONICAL_ROUTES.containsKey(route);
    }

    public String reply(UssdInboundRequest request, boolean cumulative) {
        String input = request.input(cumulative);
        if (!cumulative) return input;
        String[] parts = input.trim().replaceAll("^\\*|#$", "").split("\\*", -1);
        return parts[parts.length - 1];
    }
}
