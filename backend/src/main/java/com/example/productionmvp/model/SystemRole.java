package com.example.productionmvp.model;

public enum SystemRole {
    WORKER,
    DISPATCHER,
    MANAGER,
    SUPPLIER,
    ADMIN,
    // Not a person - a shop-floor display account. Deliberately granted zero @PreAuthorize
    // access anywhere in the system except the handful of endpoints tv.html actually calls
    // (/api/dashboard, /api/outsource/active, /api/events/stream - all already open to any
    // authenticated role), so a token minted for this role can't do anything beyond reading
    // the dashboard even if someone lifts it off the display. See index.html's TV login flow.
    TV
}
