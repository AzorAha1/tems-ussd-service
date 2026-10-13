package com.example.tems.Tems.ussd;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class UssdGatewayPropertiesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(UssdGatewayProperties.class)
    static class PropertiesConfiguration {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test void probeAndDiagnosticsAreDisabledWithoutConfiguration() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var properties = context.getBean(UssdGatewayProperties.class);
            assertFalse(properties.isAliasTestEnabled());
            assertFalse(properties.isDiagnosticsEnabled());
            assertTrue(properties.getAliasTestNumbers().isEmpty());
        });
    }

    @Test void deploymentPlaceholdersBindTheExplicitTestSettings() {
        runner.withPropertyValues("USSD_ALIAS_TEST_ENABLED=true", "USSD_DIAGNOSTICS_ENABLED=true",
            "USSD_ALIAS_TEST_NUMBERS=08000000000",
            "ussd.gateway.alias-test-enabled=${USSD_ALIAS_TEST_ENABLED:false}",
            "ussd.gateway.diagnostics-enabled=${USSD_DIAGNOSTICS_ENABLED:false}",
            "ussd.gateway.alias-test-numbers=${USSD_ALIAS_TEST_NUMBERS:}")
            .run(context -> {
                assertNull(context.getStartupFailure());
                var properties = context.getBean(UssdGatewayProperties.class);
                assertTrue(properties.isAliasTestEnabled());
                assertTrue(properties.isDiagnosticsEnabled());
                assertEquals(java.util.Set.of("08000000000"), properties.getAliasTestNumbers());
            });
    }
}
