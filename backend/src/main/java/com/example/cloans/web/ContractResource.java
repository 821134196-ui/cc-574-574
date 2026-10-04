package com.example.cloans.web;

import com.example.cloans.domain.Contract;
import com.example.cloans.domain.Loan;
import com.example.cloans.service.ContractService;
import com.example.cloans.web.dto.Requests;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/contracts")
@Produces(MediaType.APPLICATION_JSON)
public class ContractResource {

    @Inject Identity identity;
    @Inject EntityManager em;
    @Inject ContractService contracts;

    @GET
    public Object list(@QueryParam("q") String q) {
        var me = identity.current();
        String like = q == null ? "" : q.trim();
        List<Contract> all = em.createQuery(
                "select c from Contract c where :kw='' or c.code like :kw or c.title like :kw " +
                        "order by c.id", Contract.class)
                .setParameter("kw", "%" + like + "%").getResultList();
        return all.stream().map(c -> {
            List<Loan> active = em.createQuery(
                    "select l from Loan l where l.contract=:c and l.status in " +
                            "(com.example.cloans.domain.enums.LoanStatus.APPROVED," +
                            "com.example.cloans.domain.enums.LoanStatus.PICKED_UP," +
                            "com.example.cloans.domain.enums.LoanStatus.OVERDUE," +
                            "com.example.cloans.domain.enums.LoanStatus.REVOKED) order by l.id", Loan.class)
                    .setParameter("c", c).getResultList();
            Loan al = active.isEmpty() ? null : active.get(0);
            return Views.contract(c, me, al, contracts.canSeeScan(me, c));
        }).toList();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Object create(Requests.ContractRequest req) {
        return Views.contract(contracts.create(identity.current(), req), identity.current(), null, false);
    }

    /** 上传扫描件（multipart 简化：原始字节，文件名放 X-Filename） */
    @POST
    @Path("{id}/scan")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response uploadScan(@PathParam("id") long id,
                               @HeaderParam("X-Filename") String filename, byte[] data) {
        var c = contracts.uploadScan(identity.current(), id, filename, data);
        return Response.ok(Views.contract(c, identity.current(), null, false)).build();
    }

    /** 下载/查看扫描件：按岗位授权拦截 */
    @GET
    @Path("{id}/scan")
    public Response scan(@PathParam("id") long id) {
        var entry = contracts.readScan(identity.current(), id);
        String name = entry.getKey();
        String mime = guessMime(name);
        // inline 便于浏览器直接查看
        return Response.ok(entry.getValue(), mime)
                .header("Content-Disposition", "inline; filename=\"" + name + "\"")
                .build();
    }

    /** 设置扫描件授权岗位；postId=0 取消授权 */
    @POST
    @Path("{id}/scan-grant")
    @Consumes(MediaType.APPLICATION_JSON)
    public Object grant(@PathParam("id") long id, Requests.ScanGrantRequest req) {
        Long pid = req == null ? null : req.postId();
        return Views.contract(contracts.grantScan(identity.current(), id, pid), identity.current(), null, false);
    }

    private String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
