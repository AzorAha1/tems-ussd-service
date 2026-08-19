package com.example.tems.Tems.client;

public record SmsSendRequest(
    String phone,
    String message,
    String sessionId,
    String refId,
    String module
) {}
