package com.example.productionmvp.dto;

import java.util.UUID;

public class AuthRequestDTO {
    private UUID workerId;
    private String pin;
    private String qrBadgeCode;

    /**
     * Which sign-in screen the PIN was typed on: MANAGER, WORKER or TV.
     *
     * <p>The check used to happen in the browser, after the server had already issued a working
     * token — so a worker's PIN typed on the TV screen produced a real token that travelled over
     * the network before the page decided to discard it, and the rejection said so in as many
     * words. Between them that turned the sign-in screens into a way to test whether a PIN
     * exists at all. The server now applies the rule itself and answers exactly as it would for
     * a PIN that does not exist.
     */
    private String loginContext;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public String getLoginContext() { return loginContext; }
    public void setLoginContext(String loginContext) { this.loginContext = loginContext; }

    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }

    public String getQrBadgeCode() { return qrBadgeCode; }
    public void setQrBadgeCode(String qrBadgeCode) { this.qrBadgeCode = qrBadgeCode; }
}
