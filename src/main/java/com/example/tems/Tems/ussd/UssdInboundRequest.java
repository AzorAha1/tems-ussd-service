package com.example.tems.Tems.ussd;

public record UssdInboundRequest(String phoneNumber, String sessionId, String serviceCode,
                                 String userInput, String cumulativeText, SessionEvent event) {
    public enum SessionEvent { BEGIN, CONTINUE, END, UNKNOWN }
    public String input(boolean cumulative) {
        return cumulative ? (cumulativeText.isEmpty() ? userInput : cumulativeText)
            : (userInput.isEmpty() ? cumulativeText : userInput);
    }
    // Never expose request contents through incidental logging.
    @Override public String toString() { return "UssdInboundRequest[event=" + event + "]"; }
}
