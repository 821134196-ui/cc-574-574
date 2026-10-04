package org.acme.contracts;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.acme.contracts.model.Contract;
import org.acme.contracts.model.Employee;
import org.acme.contracts.model.Enums;
import org.acme.contracts.repo.ContractRepository;
import org.acme.contracts.repo.EmployeeRepository;
import org.acme.contracts.service.ApiException;
import org.acme.contracts.service.LoanService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键拦截场景：
 * 1) 并发借阅同一份原件 —— 只有一单成功，数据库部分唯一索引兜底；
 * 2) 逾期后续借 —— 硬拦截 400；
 * 3) 借阅人调岗 / 合同密级变化 —— 续借被拦，重核验失败继续拦，恢复权限并通过后才放行；
 * 4) 归还缺页 —— 进入异常处理，不得签为已还，管理员闭环后方可完成归还。
 * 另覆盖：管理员不能自审、扫描件授权查看、交接/审计链顺序追查。
 * 每个用例创建专用合同，避免相互占用。
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class LoanInterceptionTest {

    static final long ADMIN_ZHOU = 1, ADMIN_LI = 2, ARCHIVIST_WANG = 3;
    static final long EMP_ZHANG = 4, EMP_CHEN = 5, EMP_LIU = 6;

    @Inject
    LoanService loanService;

    @Inject
    EmployeeRepository employees;

    @Inject
    ContractRepository contracts;

    @Inject
    TestFixtures fixtures;

    private long newContract(String code, Enums.Secrecy secrecy, int pages) {
        return given().header("X-User-Id", ADMIN_ZHOU).contentType(ContentType.JSON)
                .body(Map.of("code", code, "title", "测试合同-" + code, "secrecy", secrecy.name(),
                        "boxLocation", "T-00-00", "pages", pages, "stampedPages", "1," + pages))
                .post("/api/contracts").then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    private long applyAndIssue(long borrowerId, long contractId, String handler) {
        long loanId = given()
                .header("X-User-Id", borrowerId)
                .contentType(ContentType.JSON)
                .body(Map.of("contractId", contractId, "purpose", "业务办理需要",
                        "dueDate", LocalDate.now().plusDays(7).toString()))
                .post("/api/loans").then().statusCode(200)
                .extract().jsonPath().getLong("id");
        given().header("X-User-Id", ADMIN_ZHOU).contentType(ContentType.JSON)
                .body(Map.of("approved", true))
                .post("/api/loans/" + loanId + "/approve").then().statusCode(200);
        given().header("X-User-Id", ARCHIVIST_WANG).contentType(ContentType.JSON)
                .body(Map.of("handler", handler))
                .post("/api/loans/" + loanId + "/issue").then().statusCode(200);
        return loanId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getLoan(long userId, long loanId) {
        return given().header("X-User-Id", userId)
                .get("/api/loans/" + loanId).then().statusCode(200)
                .extract().as(new TypeRef<Map<String, Object>>() {
                });
    }

    // ============ 1. 并发借阅：一份原件不能同时借给两人 ============

    @Test
    void t01_concurrentApply_onlyOneWins() throws Exception {
        long contractId = newContract("T-CONC", Enums.Secrecy.PUBLIC, 5);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final long borrowerId = (i % 2 == 0) ? EMP_ZHANG : EMP_CHEN;
            pool.submit(() -> {
                try {
                    start.await();
                    int code = given()
                            .header("X-User-Id", borrowerId)
                            .contentType(ContentType.JSON)
                            .body(Map.of("contractId", contractId, "purpose", "并发申请测试",
                                    "dueDate", LocalDate.now().plusDays(5).toString()))
                            .post("/api/loans").statusCode();
                    if (code == 200) {
                        ok.incrementAndGet();
                    } else if (code == 409) {
                        rejected.incrementAndGet();
                    } else {
                        other.incrementAndGet();
                    }
                } catch (Exception e) {
                    other.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(0, other.get(), "并发请求不应出现 409/200 以外的结果");
        assertEquals(1, ok.get(), "并发申请中只允许一单成功");
        assertEquals(threads - 1, rejected.get(), "其余并发申请必须收到 409 冲突");

        given().header("X-User-Id", EMP_LIU).contentType(ContentType.JSON)
                .body(Map.of("contractId", contractId, "purpose", "顺序再借",
                        "dueDate", LocalDate.now().plusDays(3).toString()))
                .post("/api/loans").then().statusCode(409);
    }

    // ============ 2. 逾期续借拦截 ============

    @Test
    void t02_overdueLoan_cannotRenew() {
        long contractId = newContract("T-OD", Enums.Secrecy.INTERNAL, 6);
        long loanId = applyAndIssue(EMP_ZHANG, contractId, "前台-孙七");

        // 到期前续借：进入待审批
        given().header("X-User-Id", EMP_ZHANG).contentType(ContentType.JSON)
                .body(Map.of("newDueDate", LocalDate.now().plusDays(14).toString(), "reason", "业务未办结"))
                .post("/api/loans/" + loanId + "/renewals").then().statusCode(200);

        // 到期日拨到昨天模拟逾期
        fixtures.setDueDate(loanId, LocalDate.now().minusDays(1));

        given().header("X-User-Id", EMP_ZHANG).contentType(ContentType.JSON)
                .body(Map.of("newDueDate", LocalDate.now().plusDays(14).toString(), "reason", "逾期还想续"))
                .post("/api/loans/" + loanId + "/renewals").then().statusCode(400);

        // 逾期后待审批续借也无法被批准
        long renewalId = given().header("X-User-Id", ADMIN_ZHOU)
                .get("/api/loans/" + loanId + "/renewals").then().statusCode(200)
                .extract().jsonPath().getLong("find { it.status == 'PENDING' }.id");
        given().header("X-User-Id", ADMIN_LI).contentType(ContentType.JSON)
                .body(Map.of("approved", true))
                .post("/api/loans/renewals/" + renewalId + "/decision").then().statusCode(400);

        // 到期日已过，借阅单在管理视图中被标记为逾期（定时任务禁用，状态仍为 ISSUED）
        Map<String, Object> loan = getLoan(ADMIN_ZHOU, loanId);
        assertTrue((Boolean) loan.get("overdue"));
    }

    // ============ 3. 密级变更 / 调岗后重新核验权限 ============

    @Test
    void t03_secrecyAndTransferChange_requiresReverify() {
        long contractId = newContract("T-SEC", Enums.Secrecy.CONFIDENTIAL, 10);
        long loanId = applyAndIssue(EMP_CHEN, contractId, "陈静本人");

        // 3.1 合同密级提升 SECRET -> 续借被拦
        fixtures.setContractSecrecy(contractId, Enums.Secrecy.SECRET);
        given().header("X-User-Id", EMP_CHEN).contentType(ContentType.JSON)
                .body(Map.of("newDueDate", LocalDate.now().plusDays(10).toString(), "reason", "密级变后续借"))
                .post("/api/loans/" + loanId + "/renewals").then().statusCode(409);

        // 3.2 借阅人密级不足 -> 重核验失败，继续拦截
        given().header("X-User-Id", ADMIN_ZHOU)
                .post("/api/loans/" + loanId + "/reverify").then().statusCode(409);

        // 3.3 调岗并提升密级到 SECRET -> 重核验通过
        fixtures.setEmployeeClearance(EMP_CHEN, Enums.Secrecy.SECRET);
        given().header("X-User-Id", ADMIN_ZHOU)
                .post("/api/loans/" + loanId + "/reverify").then().statusCode(200);

        // 3.4 重验通过后续借申请与审批放行，到期日更新
        long renewalId = given().header("X-User-Id", EMP_CHEN).contentType(ContentType.JSON)
                .body(Map.of("newDueDate", LocalDate.now().plusDays(10).toString(), "reason", "权限已重验"))
                .post("/api/loans/" + loanId + "/renewals").then().statusCode(200)
                .extract().jsonPath().getLong("id");
        given().header("X-User-Id", ADMIN_LI).contentType(ContentType.JSON)
                .body(Map.of("approved", true))
                .post("/api/loans/renewals/" + renewalId + "/decision").then().statusCode(200);

        Map<String, Object> loan = getLoan(ADMIN_ZHOU, loanId);
        assertEquals("OK", loan.get("reverify"));
        assertEquals(LocalDate.now().plusDays(10).toString(), loan.get("dueDate"));
        assertEquals(1, ((Number) loan.get("renewalCount")).intValue());
    }

    // ============ 4. 归还缺页/损坏：异常处理拦截 ============

    @Test
    void t04_returnWithMissingPage_blockedUntilAnomalyResolved() {
        long contractId = newContract("T-MISS", Enums.Secrecy.SECRET, 30);
        Contract c = contracts.findById(contractId);
        long loanId = applyAndIssue(EMP_CHEN, contractId, "机要员-周八");

        // 4.1 少 2 页且盖章页缺失 -> 409 + ANOMALY
        given().header("X-User-Id", ARCHIVIST_WANG).contentType(ContentType.JSON)
                .body(Map.of("handler", "机要员-周八", "pagesPresent", c.pages - 2,
                        "missingPages", "29,30", "missingAttachments", "",
                        "stampedPagesOk", false, "damageDesc", "", "note", "逐页核对"))
                .post("/api/loans/" + loanId + "/return").then().statusCode(409);

        assertEquals("ANOMALY", getLoan(ARCHIVIST_WANG, loanId).get("status"),
                "缺页必须转入异常处理，不能签为已还");

        List<Map<String, Object>> open = given().header("X-User-Id", ADMIN_ZHOU)
                .get("/api/loans/anomalies?status=OPEN").then().statusCode(200)
                .extract().jsonPath().getList("$");
        long anomalyId = open.stream()
                .filter(a -> ((Number) a.get("loanId")).longValue() == loanId)
                .findFirst().orElseThrow()
                .entrySet().stream().filter(e -> e.getKey().equals("id")).findFirst()
                .map(e -> ((Number) e.getValue()).longValue()).orElseThrow();

        // 4.2 异常未闭环前，再次核验通过也不能签为已还
        given().header("X-User-Id", ARCHIVIST_WANG).contentType(ContentType.JSON)
                .body(Map.of("handler", "机要员-周八", "pagesPresent", c.pages,
                        "missingPages", "", "missingAttachments", "",
                        "stampedPagesOk", true, "damageDesc", "", "note", "补验"))
                .post("/api/loans/" + loanId + "/return").then().statusCode(409);
        assertEquals("ANOMALY", getLoan(ARCHIVIST_WANG, loanId).get("status"));

        // 4.3 档案员无权闭环异常
        given().header("X-User-Id", ARCHIVIST_WANG).contentType(ContentType.JSON)
                .body(Map.of("resolution", "找回缺页", "completeReturn", true))
                .post("/api/loans/anomalies/" + anomalyId + "/resolve").then().statusCode(403);

        // 4.4 管理员闭环异常并确认完成归还
        given().header("X-User-Id", ADMIN_ZHOU).contentType(ContentType.JSON)
                .body(Map.of("resolution", "缺页已追回归档、盖章页补齐，通报借阅部门",
                        "completeReturn", true))
                .post("/api/loans/anomalies/" + anomalyId + "/resolve").then().statusCode(200);

        assertEquals("RETURNED", getLoan(ADMIN_ZHOU, loanId).get("status"),
                "异常闭环后才允许签为已还");

        // 归还后合同可被再次借阅（原件释放）；陈静已在 t03 调岗为 SECRET
        given().header("X-User-Id", EMP_CHEN).contentType(ContentType.JSON)
                .body(Map.of("contractId", contractId, "purpose", "闭环后再借",
                        "dueDate", LocalDate.now().plusDays(2).toString()))
                .post("/api/loans").then().statusCode(200);
    }

    // ============ 5. 管理员不能批准自己的借阅 ============

    @Test
    void t05_adminCannotApproveOwnLoan() {
        long contractId = newContract("T-SELF", Enums.Secrecy.INTERNAL, 4);
        long loanId = given().header("X-User-Id", ADMIN_ZHOU).contentType(ContentType.JSON)
                .body(Map.of("contractId", contractId, "purpose", "法务自查",
                        "dueDate", LocalDate.now().plusDays(5).toString()))
                .post("/api/loans").then().statusCode(200)
                .extract().jsonPath().getLong("id");

        given().header("X-User-Id", ADMIN_ZHOU).contentType(ContentType.JSON)
                .body(Map.of("approved", true))
                .post("/api/loans/" + loanId + "/approve").then().statusCode(403);
        given().header("X-User-Id", ADMIN_LI).contentType(ContentType.JSON)
                .body(Map.of("approved", true))
                .post("/api/loans/" + loanId + "/approve").then().statusCode(200);
    }

    // ============ 6. 扫描件仅授权岗位可看 ============

    @Test
    void t06_scanAccessControlled() {
        long contractId = newContract("T-SCAN", Enums.Secrecy.INTERNAL, 8);
        String b64 = Base64.getEncoder().encodeToString("%PDF-1.4 fake".getBytes());
        given().header("X-User-Id", ADMIN_ZHOU).contentType(ContentType.JSON)
                .body(Map.of("fileName", "t-scan.pdf", "contentBase64", b64))
                .post("/api/contracts/" + contractId + "/scan-content").then().statusCode(200);

        // 张伟未获扫描件授权 -> 403
        given().header("X-User-Id", EMP_ZHANG)
                .get("/api/contracts/" + contractId + "/scan").then().statusCode(403);
        // 刘洋密级/授权均不满足 -> 403
        given().header("X-User-Id", EMP_LIU)
                .get("/api/contracts/" + contractId + "/scan").then().statusCode(403);
        // 陈静在 t03 已调岗为 SECRET 且种子即有扫描授权 -> 可查看
        given().header("X-User-Id", EMP_CHEN)
                .get("/api/contracts/" + contractId + "/scan").then().statusCode(200);
    }

    // ============ 7. 交接/核验/审计记录可顺序追查 ============

    @Test
    void t07_handoverAndAuditChainIsOrdered() {
        long contractId = newContract("T-AUDIT", Enums.Secrecy.PUBLIC, 8);
        long loanId = applyAndIssue(EMP_LIU, contractId, "刘洋本人");
        given().header("X-User-Id", ARCHIVIST_WANG).contentType(ContentType.JSON)
                .body(Map.of("handler", "刘洋本人", "pagesPresent", 8,
                        "missingPages", "", "missingAttachments", "",
                        "stampedPagesOk", true, "damageDesc", "", "note", ""))
                .post("/api/loans/" + loanId + "/return").then().statusCode(200);

        List<Map<String, Object>> handovers = given().header("X-User-Id", ARCHIVIST_WANG)
                .get("/api/loans/" + loanId + "/handovers").then().statusCode(200)
                .extract().jsonPath().getList("$");
        assertEquals(2, handovers.size());
        assertEquals(1, ((Number) handovers.get(0).get("seq")).intValue());
        assertEquals(2, ((Number) handovers.get(1).get("seq")).intValue());
        assertEquals("ISSUE", handovers.get(0).get("kind"));
        assertEquals("RETURN", handovers.get(1).get("kind"));
        assertEquals(((Number) handovers.get(0).get("id")).longValue(),
                ((Number) handovers.get(1).get("prevId")).longValue(),
                "交接记录必须通过 prevId 顺序链接");

        List<Map<String, Object>> trail = given().header("X-User-Id", ADMIN_ZHOU)
                .get("/api/loans/audit/trail").then().statusCode(200)
                .extract().jsonPath().getList("$");
        long lastSeq = 0;
        Long lastId = null;
        for (Map<String, Object> row : trail) {
            long seq = ((Number) row.get("seq")).longValue();
            assertTrue(seq > lastSeq, "审计链 seq 必须全局递增");
            Object prev = row.get("prevId");
            assertEquals(lastId, prev == null ? null : ((Number) prev).longValue(),
                    "审计链 prevId 必须衔接上一条");
            lastSeq = seq;
            lastId = ((Number) row.get("id")).longValue();
        }

        given().header("X-User-Id", EMP_LIU)
                .get("/api/loans/audit/trail").then().statusCode(403);
    }
}
