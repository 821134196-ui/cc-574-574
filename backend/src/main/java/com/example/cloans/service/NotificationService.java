package com.example.cloans.service;

import com.example.cloans.domain.Notification;
import com.example.cloans.domain.User;
import com.example.cloans.domain.enums.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/** 本地模拟提醒中心（真实系统此处对接短信/邮件/IM 网关） */
@ApplicationScoped
public class NotificationService {

    @Inject
    EntityManager em;

    public void notify(User to, NotificationType type, String title, String message, Long refLoanId) {
        em.persist(new Notification(to, type, title, message, refLoanId));
    }

    public boolean exists(NotificationType type, Long refLoanId, Long userId) {
        Long n = em.createQuery(
                "select count(n) from Notification n where n.type=:t and n.refLoanId=:l " +
                        "and (n.user.id=:u or :u is null)", Long.class)
                .setParameter("t", type).setParameter("l", refLoanId).setParameter("u", userId)
                .getSingleResult();
        return n > 0;
    }
}
