package com.example.cloans.web;

import com.example.cloans.domain.PermissionRecheck;
import com.example.cloans.service.ContractService;
import com.example.cloans.service.PermissionService;
import com.example.cloans.web.dto.Requests;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class PermissionResource {

    @Inject Identity identity;
    @Inject EntityManager em;
    @Inject PermissionService permissions;

    /** 员工调岗 -> 在册借阅生成重新核验 */
    @POST @Path("admin/transfer")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object transfer(Requests.TransferRequest req) {
        return Views.user(permissions.transfer(identity.current(), req.userId(), req.postId()));
    }

    /** 合同密级调整（可同时调整扫描件授权岗位） -> 在册借阅重新核验 */
    @POST @Path("admin/contract-level")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object level(Requests.LevelChangeRequest req) {
        return Views.contract(permissions.changeLevel(identity.current(), req.contractId(),
                ContractService.parseLevel(req.securityLevel()), req.scanGrantedPostId()),
                identity.current(), null, false);
    }

    @GET @Path("rechecks")
    public Object list(@QueryParam("status") String status) {
        identity.current();
        String jpql = "select r from PermissionRecheck r where 1=1";
        if (status != null && !status.isBlank()) jpql += " and r.status=:s";
        jpql += " order by r.id desc";
        var q = em.createQuery(jpql, PermissionRecheck.class);
        if (status != null && !status.isBlank()) {
            try { q.setParameter("s", com.example.cloans.domain.enums.RecheckStatus.valueOf(status.trim().toUpperCase())); }
            catch (IllegalArgumentException e) { throw com.example.cloans.service.ApiException.badRequest("未知状态: " + status); }
        }
        return q.getResultList().stream().map(Views::recheck).toList();
    }

    /** 管理人员作出重检结论；不通过则中止借阅、催还 */
    @POST @Path("rechecks/{id}/decision")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object decide(@PathParam("id") long id, Requests.RecheckDecisionRequest req) {
        boolean pass = req != null && Boolean.TRUE.equals(req.pass());
        return Views.recheck(permissions.decideRecheck(identity.current(), id, pass,
                req == null ? null : req.note()));
    }
}
