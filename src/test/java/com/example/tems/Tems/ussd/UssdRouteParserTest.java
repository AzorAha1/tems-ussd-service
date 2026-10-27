package com.example.tems.Tems.ussd;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class UssdRouteParserTest {
    private final UssdRouteParser parser = new UssdRouteParser();
    private final UssdRequestNormalizer normalizer = new UssdRequestNormalizer();

    @ParameterizedTest
    @CsvSource(value = {"*7447#|''|''", "*7447*1#|''|1", "*7447*10#|''|10", "*7447*222#|''|222",
        "*7447*1001#|''|1001", "*7447*11*1#|11|1", "7447*11*1|11|1"}, delimiter = '|')
    void parsesExactSegments(String code, String prefix, String route) {
        var path = parser.parse(code);
        assertTrue(path.valid());
        assertEquals("7447", path.baseCode());
        assertEquals(prefix, path.transportPrefix());
        assertEquals(route, path.publicRoute());
        assertTrue(path.remainingSegments().isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {"*7447#|''|''|''", "*7447*1#|''|''|1", "*7447*11*1#|''|11|1",
        "*7447#|1|''|1", "*7447*11#|1|11|1"}, delimiter = '|')
    void classifiesCallbackRepresentations(String code, String input, String prefix, String route) {
        var inbound = normalizer.normalize(Map.of("serviceCode", new String[]{code},
            "ussdString", new String[]{input}, "sessionId", new String[]{"test-session"}), null, null, Map.of());
        var path = parser.initialPath(inbound, true, true);
        assertEquals(code, inbound.serviceCode());
        assertEquals(input, inbound.userInput());
        assertEquals(prefix, path.transportPrefix());
        assertEquals(route, path.publicRoute());
    }

    @Test void unknownRouteIsPreservedAndProductionResolverRejectsIt() {
        assertEquals("222", parser.parse("*7447*222#").publicRoute());
        var request = new UssdInboundRequest("", "s", "*7447*222#", "", "", UssdInboundRequest.SessionEvent.BEGIN);
        assertEquals(UssdRouteResolver.Route.UNKNOWN, new UssdRouteResolver().resolve(request, true, true));
    }

    @Test void rejectsMalformedAndDoesNotSearchArbitrarySegments() {
        for (String code : new String[]{"", "*7447**1#", "7447*abc", "**7447#", "7447#*1", "7447*1234567"}) {
            assertFalse(parser.parse(code).valid(), code);
        }
        var path = parser.parse("*7447*222*10#");
        assertEquals("222", path.publicRoute());
        assertEquals(java.util.List.of("10"), path.remainingSegments());
    }

    @Test void extendedServiceWinsAndBareReplyRequiresStartEvidence() {
        var request = new UssdInboundRequest("", "", "*7447*1#", "7447", "10", UssdInboundRequest.SessionEvent.UNKNOWN);
        assertEquals("1", parser.initialPath(request, true, true).publicRoute());
        request = new UssdInboundRequest("", "", "*7447#", "1", "", UssdInboundRequest.SessionEvent.UNKNOWN);
        assertFalse(parser.initialPath(request, true, false).valid());
    }

    @Test void preservesRawAliasEvidenceWithoutHeadersOrCredentials() {
        var request = normalizer.normalize(Map.of(),
            "{\"service_code\":\"*7447*1#\",\"input\":\"\",\"ussd_string\":\"1\",\"text\":\"\",\"message\":\"other\",\"apiKey\":\"SECRET\"}",
            "application/json", Map.of());
        assertEquals("1", request.userInput());
        assertEquals("", request.rawFields().get("input"));
        assertEquals("other", request.rawFields().get("message"));
        assertFalse(request.rawFields().containsKey("apiKey"));
        assertFalse(request.toString().contains("other"));
    }
}
