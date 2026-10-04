package com.example.cloans.domain.enums;

/** 归还核验：单页核对结论 */
public enum PageCheckStatus {
    OK("完好"),
    MISSING("缺页"),
    DAMAGED("损坏");

    public final String label;
    PageCheckStatus(String label) { this.label = label; }

    public boolean hasIssue() { return this != OK; }
}
