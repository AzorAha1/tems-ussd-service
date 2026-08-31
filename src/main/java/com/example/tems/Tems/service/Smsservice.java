package com.example.tems.Tems.service;

import com.example.tems.Tems.client.SmsSendRequest;
import com.example.tems.Tems.client.SmsServiceClient;
import com.example.tems.Tems.model.CacRegistration;
import com.example.tems.Tems.model.FfsRegistration;
import com.example.tems.Tems.model.NinRecord;

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
    public void sendFfsRegistrationSms(String sessionId, String phone, FfsRegistration reg) {
        String message = "TEMS: Your FFS registration is COMPLETE. Ref: "
            + reg.getReferenceId() + ". Dial *7447# to check status.";

        SmsSendRequest req = new SmsSendRequest(
            phone,
            message,
            sessionId,
            reg.getReferenceId(),
            "FFS"
        );

        client.sendSms(req);
    }
    public void sendCacVerificationSms(String sessionId, String phone, CacRegistration reg) {
        String message = "TEMS: Your CAC registration for " + reg.getBusinessName()
            + " has been VERIFIED. Ref: " + reg.getReferenceId() + ".";

        SmsSendRequest req = new SmsSendRequest(
            phone,
            message,
            sessionId,
            reg.getReferenceId(),
            "CAC_VERIFY"
        );

        client.sendSms(req);
    }
    public void sendCacNinValidationSms(String sessionId, String phone, NinRecord rec) {
            String message = "TEMS: NIN Verified.\n"
                + "Name: " + rec.getFirstName() + " " + (rec.getMiddleName() != null ? rec.getMiddleName() + " " : "") + rec.getLastName() + "\n"
                + "DOB: " + (rec.getDateOfBirth() != null ? rec.getDateOfBirth().toString() : "N/A") + "\n"
                + "Gender: " + rec.getGender() + "\n"
                + "State: " + rec.getStateOfOrigin() + "\n"
                + "LGA: " + rec.getLga();

            SmsSendRequest req = new SmsSendRequest(
                phone,
                message,
                sessionId,
                "NIN-" + rec.getNin(),
                "CAC_NIN_VALIDATE"
            );

            client.sendSms(req);
        }
}