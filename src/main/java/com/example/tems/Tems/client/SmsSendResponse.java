package com.example.tems.Tems.client;

public record SmsSendResponse(
    boolean success,
    int statusCode,
    String message,
    String providerMessageId,
    String requestId
) {}