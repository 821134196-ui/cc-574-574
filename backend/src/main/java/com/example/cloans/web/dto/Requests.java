package com.example.cloans.web.dto;

import java.util.List;

/** 接口请求体 */
public final class Requests {
    private Requests() {}

    public record ApplyRequest(Long contractId, String purpose, Integer days) {}

    public record RejectRequest(String reason) {}

    public record RenewRequest(Integer days) {}

    public record DecisionRequest(Boolean approve, String note) {}

    public record PageResult(int pageNo, String status, String note) {}

    public record PageCheckRequest(Long loanId, List<PageResult> pages) {}

    public record ResolveAnomalyRequest(String resolution) {}

    public record TransferRequest(Long userId, Long postId) {}

    public record LevelChangeRequest(Long contractId, String securityLevel, Long scanGrantedPostId) {}

    public record RecheckDecisionRequest(Boolean pass, String note) {}

    public record ScanGrantRequest(Long postId) {}

    public record ContractRequest(String code, String title, String counterparty,
                                  String securityLevel, String boxLocation,
                                  Integer totalPages, String sealPages, Long scanGrantedPostId) {}
}
