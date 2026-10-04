package com.example.cloans.domain.enums;

/** 异常单状态：缺页/损坏后必须走完异常处理，不能直接签为已还 */
public enum AnomalyStatus {
    OPEN("待处理"),
    IN_PROGRESS("处理中"),
    RESOLVED("已处理");

    public final String label;
    AnomalyStatus(String label) { this.label = label; }
}
