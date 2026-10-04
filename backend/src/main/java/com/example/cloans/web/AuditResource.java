package com.example.cloans.web;

import com.example.cloans.domain.AuditLog;
import com.example.cloans.service.D;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** 顺序审计：只可按时间顺序查询，不可修改 */
@Path("/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject Identity identity;
    @Inject EntityManager em;

    @GET
    public Object list(@QueryParam("loanId") Long loanId,
                       @QueryParam("entityType") String entityType,
                       @QueryParam("limit") @DefaultValue("200") int limit) {
        var me = identity.current();
        String jpql = "select a from AuditLog a where 1=1";
        if (loanId != null) jpql += " and (a.entityId=:loan and a.entityType in ('Loan','Anomaly'))";
        if (entityType != null && !entityType.isBlank()) jpql += " and a.entityType=:et";
        jpql += " order by a.id desc";
        var q = em.createQuery(jpql, AuditLog.class);
        if (loanId != null) q.setParameter("loan", loanId);
        if (entityType != null && !entityType.isBlank()) q.setParameter("et", entityType.trim());
        q.setMaxResults(Math.min(limit, 1000));
        return q.getResultList().stream().map(Views::audit).toList();
    }

    /** 按借阅单顺序追查整条交接/核验链路 */
    @GET @Path("trace/{loanId}")
    public Object trace(@PathParam("loanId") long loanId) {
        identity.current();
        // Loan 自身日志按 entityId 命中；异常/重检/续借日志在 detail 中带“借阅单#id”
        var logs = em.createQuery(
                "select a from AuditLog a where (a.entityType='Loan' and a.entityId=:id) " +
                        "or a.detail like :kw order by a.id", AuditLog.class)
                .setParameter("id", loanId).setParameter("kw", "%#" + loanId + "%").getResultList();
        return D.of("loanId", loanId, "trace", logs.stream().map(Views::audit).toList());
    }
}
