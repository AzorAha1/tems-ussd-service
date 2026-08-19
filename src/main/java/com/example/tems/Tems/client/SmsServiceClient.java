package com.example.tems.Tems.client;

public interface SmsServiceClient {
    SmsSendResponse sendSms(SmsSendRequest req);
}