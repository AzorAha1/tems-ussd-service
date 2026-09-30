package com.example.tems.Tems.ussd;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.tems.Tems.ussd.UssdInboundRequest.SessionEvent.BEGIN;
import static com.example.tems.Tems.ussd.UssdInboundRequest.SessionEvent.CONTINUE;
import static com.example.tems.Tems.ussd.UssdRouteResolver.Route.*;

class UssdRoutingTest {
    private final UssdRequestNormalizer normalizer = new UssdRequestNormalizer();
    private final UssdRouteResolver resolver = new UssdRouteResolver();

    @ParameterizedTest
    @CsvSource({"*7447#,TEMS", "*7447*10#,TMF", "*7447*27#,CBM", "*7447*1#,CBM",
        "744710,TMF", "744727,CBM", "*7447*222#,UNKNOWN", "*9999*10#,UNKNOWN"})
    void resolvesExactInitialService(String code, UssdRouteResolver.Route expected) {
        assertEquals(expected, resolver.resolve(new UssdInboundRequest("", "", code, "7447", "10", BEGIN), false, true));
    }

    @ParameterizedTest
    @CsvSource({"10,TMF", "27,CBM", "1,CBM", "222,UNKNOWN"})
    void resolvesSuffixWithStartEvidence(String suffix, UssdRouteResolver.Route expected) {
        assertEquals(expected, resolver.resolve(new UssdInboundRequest("", "", "*7447#", suffix, "", BEGIN), false, true));
    }

    @Test void keepsFirstExtensionAndRequiresEvidenceForBareOne() {
        assertEquals(CBM, resolver.resolve(new UssdInboundRequest("", "", "", "7447*27*10", "", BEGIN), true, true));
        assertEquals(UNKNOWN, resolver.resolve(new UssdInboundRequest("", "", "7447", "1", "", UssdInboundRequest.SessionEvent.UNKNOWN), false, false));
        assertEquals(UNKNOWN, resolver.resolve(new UssdInboundRequest("", "", "", "9999*10", "", BEGIN), false, true));
    }

    @Test void preservesIndependentFieldsAndIgnoresBlankAliases() {
        var r = normalizer.normalize(Map.of("input", new String[]{" "}, "msisdn", new String[]{"08000000000"}),
            "{\"serviceCode\":\"*7447*10#\",\"input\":\"7447\",\"text\":\"10\"}", "application/json", Map.of());
        assertEquals("08000000000", r.phoneNumber());
        assertEquals("7447", r.userInput());
        assertEquals("10", r.cumulativeText());
        assertEquals(TMF, resolver.resolve(r, false, true));
    }

    @Test void queryAliasesWinOverBodyAndEventRequiresExplicitMapping() {
        var params = Map.of("msisdn", new String[]{"08000000000"}, "ussdString", new String[]{"27"},
            "shortCode", new String[]{"7447"}, "messageType", new String[]{"0"});
        var r = normalizer.normalize(params, "{\"input\":\"10\"}", "application/json", Map.of());
        assertEquals("27", r.userInput());
        assertEquals(UssdInboundRequest.SessionEvent.UNKNOWN, r.event());
        assertEquals(BEGIN, normalizer.normalize(params, null, null, Map.of("0", BEGIN)).event());
    }

    @Test void decodesFormsAndRejectsMalformedPayloads() {
        var r = normalizer.normalize(Map.of(), "msisdn=%2B2348000000000&shortCode=%2A7447%2A27%23&ussdString=",
            "application/x-www-form-urlencoded", Map.of());
        assertEquals("+2348000000000", r.phoneNumber());
        assertEquals("*7447*27#", r.serviceCode());
        for (String raw : new String[]{"{broken", "[]", "{\"input\":{}}"}) {
            assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(Map.of(), raw, "application/json", Map.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(Map.of(), "input=%ZZ", null, Map.of()));
    }

    @Test void splitsDialogueOnlyWhenConfigured() {
        var r = new UssdInboundRequest("", "", "", "a*b", "10*1", CONTINUE);
        assertEquals("a*b", resolver.reply(r, false));
        assertEquals("1", resolver.reply(r, true));
    }

    @Test void explicitResponseModeOverridesAccept() {
        var config = new UssdGatewayProperties();
        assertFalse(config.plain(null));
        assertTrue(config.plain("text/plain"));
        config.setResponseMode(UssdGatewayProperties.ResponseMode.PLAIN);
        assertTrue(config.plain("application/json"));
        config.setResponseMode(UssdGatewayProperties.ResponseMode.JSON);
        assertFalse(config.plain("text/plain"));
    }
}
