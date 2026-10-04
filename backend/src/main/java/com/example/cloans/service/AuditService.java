package com.example.cloans.service;

import com.example.cloans.domain.AuditLog;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** 顺序审计：所有交接与核验动作只追加、不修改 */
@ApplicationScoped
public class AuditService {

    @Inject
    EntityManager em;

    public void record(String actorName, String action, String entityType, Long entityId,
                       String stateChange, String detail) {
        em.persist(new AuditLog(actorName, action, entityType, entityId, stateChange, detail));
    }
}
