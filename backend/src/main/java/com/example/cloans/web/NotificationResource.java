package com.example.cloans.web;

import com.example.cloans.domain.Notification;
import com.example.cloans.domain.enums.NotificationType;
import com.example.cloans.service.D;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject Identity identity;
    @Inject EntityManager em;

    @GET @Path("notifications")
    public Object list(@QueryParam("unread") Boolean unread) {
        var me = identity.current();
        String jpql = "select n from Notification n where n.user=:me";
        if (Boolean.TRUE.equals(unread)) jpql += " and n.read=false";
        jpql += " order by n.id desc";
        List<Notification> list = em.createQuery(jpql, Notification.class).setParameter("me", me)
                .setMaxResults(100).getResultList();
        long unreadCount = em.createQuery("select count(n) from Notification n where n.user=:me and n.read=false",
                Long.class).setParameter("me", me).getSingleResult();
        return D.of("unread", unreadCount, "items", list.stream().map(Views::notification).toList());
    }

    @POST @Path("notifications/read-all")
    @Transactional
    public Object readAll() {
        var me = identity.current();
        int n = em.createQuery("update Notification n set n.read=true where n.user=:me and n.read=false")
                .setParameter("me", me).executeUpdate();
        return D.of("updated", n);
    }

    /** 提醒类型字典（本地模拟说明） */
    @GET @Path("notification-channels")
    public Object channels() {
        identity.current();
        return D.of("provider", "LOCAL_MOCK",
                "description", "提醒中心为本地模拟：临期/逾期/门禁/权限/异常提醒均写入本系统通知表，不外发。",
                "types", List.of(NotificationType.values()).stream().map(t -> D.of("name", t.name(), "label", t.label)).toList());
    }
}
