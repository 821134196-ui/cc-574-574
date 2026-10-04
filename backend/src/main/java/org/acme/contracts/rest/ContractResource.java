package org.acme.contracts.rest;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.contracts.model.Contract;
import org.acme.contracts.model.Employee;
import org.acme.contracts.model.Enums;
import org.acme.contracts.repo.ContractRepository;
import org.acme.contracts.security.CurrentUser;
import org.acme.contracts.service.ApiException;
import org.acme.contracts.service.AuditService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/contracts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContractResource {

    @Inject
    ContractRepository repo;

    @Inject
    CurrentUser currentUser;

    @Inject
    AuditService audit;

    @ConfigProperty(name = "contracts.scan.dir")
    String scanDir;

    public record ContractInput(String code, String title, Enums.Secrecy secrecy, String boxLocation,
                                Integer pages, String stampedPages) {
    }

    @GET
    public List<Contract> list() {
        return repo.listAll();
    }

    @GET
    @Path("/{id}")
    public Contract get(@PathParam("id") Long id) {
        Contract c = repo.findById(id);
        if (c == null) {
            throw ApiException.notFound("合同不存在: " + id);
        }
        return c;
    }

    @POST
    @Transactional
    public Contract create(ContractInput in) {
        currentUser.requireRole(Enums.Role.ADMIN);
        if (in.code() == null || in.code().isBlank() || in.title() == null || in.title().isBlank()) {
            throw ApiException.badRequest("合同编号和名称必填");
        }
        if (in.pages() == null || in.pages() <= 0) {
            throw ApiException.badRequest("原件总页数必须为正整数");
        }
        if (repo.findByCode(in.code()) != null) {
            throw ApiException.conflict("合同编号已存在: " + in.code());
        }
        Contract c = new Contract();
        c.code = in.code().trim();
        c.title = in.title().trim();
        c.secrecy = in.secrecy() == null ? Enums.Secrecy.INTERNAL : in.secrecy();
        c.boxLocation = in.boxLocation() == null ? "" : in.boxLocation().trim();
        c.pages = in.pages();
        c.stampedPages = in.stampedPages() == null ? "" : in.stampedPages().trim();
        c.persist();
        audit.append("CONTRACT_CREATE", currentUser.requireAny(), null, c.id, null,
                "{\"code\":\"" + c.code + "\",\"secrecy\":\"" + c.secrecy + "\",\"pages\":" + c.pages + "}");
        return c;
    }

    /** 修改密级：提升密级后在途借阅单的重核验标记在续借/核验时触发。 */
    @PUT
    @Path("/{id}/secrecy")
    @Transactional
    public Contract changeSecrecy(@PathParam("id") Long id, Map<String, String> body) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        Contract c = repo.findById(id);
        if (c == null) {
            throw ApiException.notFound("合同不存在: " + id);
        }
        Enums.Secrecy newSecrecy;
        try {
            newSecrecy = Enums.Secrecy.valueOf(body.get("secrecy"));
        } catch (Exception e) {
            throw ApiException.badRequest("非法密级: " + body.get("secrecy"));
        }
        Enums.Secrecy old = c.secrecy;
        c.secrecy = newSecrecy;
        audit.append("CONTRACT_SECRECY_CHANGE", admin, null, c.id, null,
                "{\"old\":\"" + old + "\",\"new\":\"" + newSecrecy + "\"}");
        return c;
    }

    /** 简单上传：JSON body {"fileName":"x.pdf","contentBase64":"..."}，存到本地目录。 */
    @POST
    @Path("/{id}/scan-content")
    @Transactional
    public Map<String, String> uploadScanContent(@PathParam("id") Long id, Map<String, String> body) {
        Employee admin = currentUser.requireRole(Enums.Role.ADMIN);
        Contract c = repo.findById(id);
        if (c == null) {
            throw ApiException.notFound("合同不存在: " + id);
        }
        String fileName = body.get("fileName");
        String b64 = body.get("contentBase64");
        if (fileName == null || fileName.isBlank() || b64 == null) {
            throw ApiException.badRequest("fileName 与 contentBase64 必填");
        }
        String safe = UUID.randomUUID() + "-" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        java.nio.file.Path dir = java.nio.file.Path.of(scanDir);
        try {
            Files.createDirectories(dir);
            java.nio.file.Path target = dir.resolve(safe);
            Files.write(target, java.util.Base64.getDecoder().decode(b64));
        } catch (IOException e) {
            throw ApiException.badRequest("扫描件保存失败: " + e.getMessage());
        }
        c.scanFile = safe;
        audit.append("SCAN_UPLOAD", admin, null, c.id, null, "{\"file\":\"" + safe + "\"}");
        return Map.of("scanFile", safe);
    }

    /** 下载扫描件：仅 scanAuthorized 且密级权限满足的岗位可查看。 */
    @GET
    @Path("/{id}/scan")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadScan(@PathParam("id") Long id) {
        Employee viewer = currentUser.requireAny();
        Contract c = repo.findById(id);
        if (c == null) {
            throw ApiException.notFound("合同不存在: " + id);
        }
        if (!viewer.scanAuthorized) {
            audit.append("SCAN_DENIED_NO_AUTH", viewer, null, c.id, viewer.id, "{}");
            throw ApiException.forbidden("当前岗位未获授权查看合同扫描件");
        }
        if (viewer.clearance.level() < c.secrecy.level()) {
            audit.append("SCAN_DENIED_SECRECY", viewer, null, c.id, viewer.id, "{}");
            throw ApiException.forbidden("密级不足，无法查看该扫描件");
        }
        if (c.scanFile == null) {
            throw ApiException.notFound("该合同暂无扫描件");
        }
        java.nio.file.Path file = java.nio.file.Path.of(scanDir).resolve(c.scanFile).normalize();
        if (!file.startsWith(java.nio.file.Path.of(scanDir).normalize()) || !Files.exists(file)) {
            throw ApiException.notFound("扫描件文件丢失");
        }
        audit.append("SCAN_VIEW", viewer, null, c.id, viewer.id, "{\"file\":\"" + c.scanFile + "\"}");
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw ApiException.badRequest("读取扫描件失败");
        }
        return Response.ok(bytes)
                .header("Content-Disposition", "inline; filename=\"" + c.scanFile + "\"")
                .build();
    }
}
