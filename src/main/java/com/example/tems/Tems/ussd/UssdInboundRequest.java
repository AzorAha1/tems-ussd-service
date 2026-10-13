package com.example.tems.Tems.ussd;

public record UssdInboundRequest(String phoneNumber, String sessionId, String serviceCode,
                                String userInput, String cumulativeText, SessionEvent event, String telco,
                                java.util.Map<String, String> rawFields) {
    public UssdInboundRequest {
        rawFields = java.util.Map.copyOf(rawFields);
    }
    public UssdInboundRequest(String phoneNumber, String sessionId, String serviceCode,
                              String userInput, String cumulativeText, SessionEvent event, String telco) {
        this(phoneNumber, sessionId, serviceCode, userInput, cumulativeText, event, telco, java.util.Map.of());
    }
    public enum SessionEvent { BEGIN, CONTINUE, END, UNKNOWN }
    /** Existing callers (and tests) that do not know the telco keep working. */
    public UssdInboundRequest(String phoneNumber, String sessionId, String serviceCode,
                              String userInput, String cumulativeText, SessionEvent event) {
        this(phoneNumber, sessionId, serviceCode, userInput, cumulativeText, event, "");
    }
    public String input(boolean cumulative) {
        return cumulative ? (cumulativeText.isEmpty() ? userInput : cumulativeText)
            : (userInput.isEmpty() ? cumulativeText : userInput);
    }
    // Never expose request contents through incidental logging.
    @Override public String toString() { return "UssdInboundRequest[event=" + event + "]"; }
}
