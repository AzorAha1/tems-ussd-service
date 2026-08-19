package com.example.tems.Tems.service;

import com.example.tems.Tems.client.SmsSendRequest;
import com.example.tems.Tems.client.SmsServiceClient;
import com.example.tems.Tems.model.CacRegistration;
import org.springframework.stereotype.Service;

@Service
public class Smsservice {

    private final SmsServiceClient client;

    public Smsservice(SmsServiceClient client) {
        this.client = client;
    }

    public void sendCacRegistrationSms(String sessionId, String phone, CacRegistration reg) {
        String message = "TEMS: Your CAC registration for " + reg.getBusinessName()
            + " is PENDING. Ref: " + reg.getReferenceId()
            + ". Dial *7447# to check status.";

        SmsSendRequest req = new SmsSendRequest(
            phone,
            message,
            sessionId,
            "REG-" + reg.getId(),
            "CAC"
        );

        client.sendSms(req);
    }
}