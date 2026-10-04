package com.example.cloans.web;

import com.example.cloans.domain.Anomaly;
import com.example.cloans.service.ReturnService;
import com.example.cloans.web.dto.Requests;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ReturnResource {

    @Inject Identity identity;
    @Inject EntityManager em;
    @Inject ReturnService returns;

    /** 逐页核对提交：缺页/损坏 -> 异常，不能签已还；全部完好且无未闭环异常 -> 已还 */
    @POST @Path("return-check")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object check(Requests.PageCheckRequest req) {
        if (req == null || req.loanId() == null)
            throw com.example.cloans.service.ApiException.badRequest("请求体缺少 loanId 与逐页核对结果");
        return returns.check(identity.current(), req);
    }

    @GET @Path("anomalies")
    public Object list(@QueryParam("status") String status) {
        identity.current();
        String jpql = "select a from Anomaly a where 1=1";
        if (status != null && !status.isBlank()) jpql += " and a.status=:s";
        jpql += " order by a.id desc";
        var q = em.createQuery(jpql, Anomaly.class);
        if (status != null && !status.isBlank()) {
            try { q.setParameter("s", com.example.cloans.domain.enums.AnomalyStatus.valueOf(status.trim().toUpperCase())); }
            catch (IllegalArgumentException e) { throw com.example.cloans.service.ApiException.badRequest("未知状态: " + status); }
        }
        return q.getResultList().stream().map(Views::anomaly).toList();
    }

    /** 管理员闭环异常 */
    @POST @Path("anomalies/{id}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object resolve(@PathParam("id") long id, Requests.ResolveAnomalyRequest req) {
        return Views.anomaly(returns.resolveAnomaly(identity.current(), id,
                req == null ? null : req.resolution()));
    }
}
