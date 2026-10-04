package com.example.cloans.domain.enums;

public enum RecheckStatus {
    PENDING("待核验"),
    PASSED("权限通过"),
    REVOKED("权限不符，已中止借阅");

    public final String label;
    RecheckStatus(String label) { this.label = label; }
}
