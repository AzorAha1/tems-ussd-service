package com.example.tems.Tems.ussd;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parses entry metadata only. It never changes the incoming network identity. */
@Component
public final class UssdRouteParser {
    /** What MTN puts after the parent code: *7447*11*N# reaches us as 7447*11*N. The only place this value lives. */
    public static final String TRANSPORT_PREFIX = "11";

    public record Path(boolean valid, String baseCode, String transportPrefix,
                       String publicRoute, List<String> remainingSegments) {
        static Path invalid() { return new Path(false, "", "", "", List.of()); }
    }

    public Path parse(String raw) {
        if (raw == null) return Path.invalid();
        String value = raw.trim().replaceAll("^\\*|#$", "");
        // Bounds also prevent untrusted payloads from becoming unbounded routing data.
        if (value.length() > 128 || !value.matches("[0-9]{1,6}(\\*[0-9]{1,6})*")) return Path.invalid();
        // Historical flattened representations remain exact compatibility cases.
        if (value.equals("744710")) value = "7447*10";
        if (value.equals("744727")) value = "7447*27";
        List<String> segments = Arrays.asList(value.split("\\*"));
        int index = 1;
        String prefix = "";
        if (segments.size() > index && segments.get(index).equals(TRANSPORT_PREFIX)) {
            prefix = TRANSPORT_PREFIX;
            index++;
        }
        String route = segments.size() > index ? segments.get(index++) : "";
        return new Path(true, segments.get(0), prefix, route,
            List.copyOf(segments.subList(Math.min(index, segments.size()), segments.size())));
    }

    public Path initialPath(UssdInboundRequest request, boolean cumulative, boolean startEvidence) {
        Path service = parse(request.serviceCode());
        String input = request.input(cumulative).trim().replaceAll("^\\*|#$", "");
        if (!request.serviceCode().isEmpty() && (!service.valid() || !service.baseCode().equals("7447"))) {
            return service;
        }
        // A service with an actual child route is authoritative over dialogue/input fields.
        if (service.valid() && !service.publicRoute().isEmpty()) return service;
        // Some gateways supply the complete transport suffix without the parent shortcode.
        if (input.startsWith(TRANSPORT_PREFIX + "*")) return parse("7447*" + input);
        Path fullInput = parse(input);
        if (fullInput.valid() && fullInput.baseCode().equals("7447")) {
            if (service.valid() && !service.transportPrefix().isEmpty() && fullInput.transportPrefix().isEmpty()
                    && !fullInput.publicRoute().isEmpty()) {
                return parse("7447*" + service.transportPrefix() + "*"
                    + fullInput.publicRoute() + (fullInput.remainingSegments().isEmpty() ? "" : "*"
                    + String.join("*", fullInput.remainingSegments())));
            }
            return fullInput;
        }
        if (input.isEmpty()) return service.valid() ? service : parse("7447");
        if (startEvidence || input.equals("10") || input.equals("27")) {
            return parse("7447*" + (service.transportPrefix().isEmpty() ? "" : service.transportPrefix() + "*") + input);
        }
        return Path.invalid();
    }
}