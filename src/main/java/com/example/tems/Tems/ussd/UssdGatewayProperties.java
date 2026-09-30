package com.example.tems.Tems.ussd;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ussd.gateway")
public class UssdGatewayProperties {
    public enum InputMode { SINGLE, CUMULATIVE }
    public enum ResponseMode { LEGACY_ACCEPT, PLAIN, JSON }
    private InputMode inputMode = InputMode.SINGLE;
    private ResponseMode responseMode = ResponseMode.LEGACY_ACCEPT;
    private Map<String, UssdInboundRequest.SessionEvent> messageTypes = new HashMap<>();
    public InputMode getInputMode() { return inputMode; }
    public void setInputMode(InputMode value) { inputMode = value; }
    public ResponseMode getResponseMode() { return responseMode; }
    public void setResponseMode(ResponseMode value) { responseMode = value; }
    public Map<String, UssdInboundRequest.SessionEvent> getMessageTypes() { return messageTypes; }
    public void setMessageTypes(Map<String, UssdInboundRequest.SessionEvent> value) { messageTypes = value; }
    public boolean cumulative() { return inputMode == InputMode.CUMULATIVE; }
    public boolean plain(String accept) {
        return responseMode == ResponseMode.PLAIN || (responseMode == ResponseMode.LEGACY_ACCEPT
            && accept != null && accept.toLowerCase(java.util.Locale.ROOT).contains("text/plain"));
    }
}
