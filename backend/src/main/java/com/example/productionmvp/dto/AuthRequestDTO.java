package com.example.productionmvp.dto;

import java.util.UUID;

public class AuthRequestDTO {
    private UUID workerId;
    private String pin;
    private String qrBadgeCode;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getQrBadgeCode() { return qrBadgeCode; }
    public void setQrBadgeCode(String qrBadgeCode) { this.qrBadgeCode = qrBadgeCode; }
}
