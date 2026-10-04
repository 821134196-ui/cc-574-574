package com.example.cloans.service;

import com.example.cloans.domain.Contract;
import com.example.cloans.domain.Post;
import com.example.cloans.domain.User;
import com.example.cloans.domain.enums.Role;
import com.example.cloans.domain.enums.SecurityLevel;
import com.example.cloans.web.dto.Requests;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;

/** 合同台账与扫描件（本地目录存储，按岗位授权查看） */
@ApplicationScoped
public class ContractService {

    @Inject EntityManager em;
    @Inject AuditService audit;

    @ConfigProperty(name = "app.scans.dir") String scansDir;

    @Transactional
    public Contract create(User actor, Requests.ContractRequest req) {
        if (actor.role != Role.ADMIN && actor.role != Role.ARCHIVIST)
            throw ApiException.forbidden("仅档案员/管理人员可建档");
        if (req.code() == null || req.code().isBlank()) throw ApiException.badRequest("合同编号必填");
        if (req.title() == null || req.title().isBlank()) throw ApiException.badRequest("合同名称必填");
        Long dup = em.createQuery("select count(c) from Contract c where c.code=:x", Long.class)
                .setParameter("x", req.code().trim()).getSingleResult();
        if (dup > 0) throw ApiException.conflict("合同编号已存在");
        SecurityLevel level = parseLevel(req.securityLevel());
        if (req.totalPages() == null || req.totalPages() < 1) throw ApiException.badRequest("总页数至少 1");
        Contract c = new Contract();
        c.code = req.code().trim();
        c.title = req.title().trim();
        c.counterparty = req.counterparty() == null ? "" : req.counterparty().trim();
        c.securityLevel = level;
        c.boxLocation = req.boxLocation() == null ? "" : req.boxLocation().trim();
        c.totalPages = req.totalPages();
        c.sealPages = req.sealPages() == null ? "" : req.sealPages().trim();
        if (req.scanGrantedPostId() != null && req.scanGrantedPostId() > 0)
            c.scanGrantedPost = em.find(Post.class, req.scanGrantedPostId());
        em.persist(c);
        audit.record(actor.name, "建立合同档案", "Contract", c.id, null,
                c.code + "《" + c.title + "》盒位 " + c.boxLocation + "，" + c.totalPages +
                        "页，盖章页 " + c.sealPages + "，密级 " + level.label);
        return c;
    }

    /** 保存扫描件到本地目录（仅档案员/管理员） */
    @Transactional
    public Contract uploadScan(User actor, Long contractId, String filename, byte[] data) {
        if (actor.role != Role.ADMIN && actor.role != Role.ARCHIVIST)
            throw ApiException.forbidden("仅档案员/管理人员可上传扫描件");
        Contract c = must(contractId);
        String safe = filename == null ? "scan.dat" : Paths.get(filename).getFileName().toString();
        String ext = "";
        int dot = safe.lastIndexOf('.');
        if (dot >= 0) ext = safe.substring(dot);
        String stored = "contract-" + c.id + "-" + System.currentTimeMillis() + ext;
        try {
            Files.createDirectories(Paths.get(scansDir));
            Path target = Paths.get(scansDir, stored);
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(500, "IO_ERROR", "扫描件保存失败: " + e.getMessage());
        }
        String old = c.scanFile;
        c.scanFile = stored;
        audit.record(actor.name, "上传扫描件", "Contract", c.id, null,
                "文件 " + safe + " -> " + stored + (old == null ? "" : "，替换旧文件 " + old));
        if (old != null && !old.equals(stored)) {
            try { Files.deleteIfExists(Paths.get(scansDir, old)); } catch (IOException ignored) {}
        }
        return c;
    }

    /** 设置可查看扫描件的授权岗位 */
    @Transactional
    public Contract grantScan(User actor, Long contractId, Long postId) {
        if (actor.role != Role.ADMIN && actor.role != Role.ARCHIVIST)
            throw ApiException.forbidden("仅档案员/管理人员可授权");
        Contract c = must(contractId);
        Post old = c.scanGrantedPost;
        if (postId == null || postId == 0L) {
            c.scanGrantedPost = null;
            audit.record(actor.name, "取消扫描件授权", "Contract", c.id, null,
                    "原授权岗位：" + (old == null ? "无" : old.name));
        } else {
            Post p = em.find(Post.class, postId);
            if (p == null) throw ApiException.notFound("岗位不存在");
            c.scanGrantedPost = p;
            audit.record(actor.name, "扫描件岗位授权", "Contract", c.id, null,
                    "仅岗位「" + p.name + "」可查看扫描件（原：" + (old == null ? "无" : old.name) + "）");
        }
        return c;
    }

    /** 扫描件查看鉴权：仅获授权岗位可看（按当前岗位判定，不是按个人） */
    @Transactional
    public Map.Entry<String, byte[]> readScan(User viewer, Long contractId) {
        Contract c = must(contractId);
        if (c.scanFile == null) throw ApiException.notFound("该合同暂无扫描件");
        Post granted = c.scanGrantedPost;
        if (granted == null || !viewer.post.id.equals(granted.id))
            throw ApiException.forbidden("您当前岗位「" + viewer.post.name + "」未获授权查看该扫描件");
        try {
            byte[] data = Files.readAllBytes(Paths.get(scansDir, c.scanFile));
            return Map.entry(c.scanFile, data);
        } catch (IOException e) {
            throw new ApiException(500, "IO_ERROR", "扫描件读取失败");
        }
    }

    public boolean canSeeScan(User viewer, Contract c) {
        return c.scanFile != null && c.scanGrantedPost != null && viewer.post.id.equals(c.scanGrantedPost.id);
    }

    public Contract must(Long id) {
        Contract c = em.find(Contract.class, id);
        if (c == null) throw ApiException.notFound("合同不存在");
        return c;
    }

    public static SecurityLevel parseLevel(String s) {
        if (s == null || s.isBlank()) return SecurityLevel.INTERNAL;
        try { return SecurityLevel.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw ApiException.badRequest("未知密级: " + s); }
    }
}
