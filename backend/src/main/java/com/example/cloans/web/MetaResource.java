package com.example.cloans.web;

import com.example.cloans.domain.Post;
import com.example.cloans.domain.User;
import com.example.cloans.domain.enums.Role;
import com.example.cloans.service.ApiException;
import com.example.cloans.service.D;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class MetaResource {

    @Inject Identity identity;
    @Inject EntityManager em;

    @GET @Path("me")
    public Object me() {
        User u = identity.current();
        return D.of("user", Views.user(u));
    }

    @GET @Path("posts")
    public Object posts() {
        identity.current();
        return em.createQuery("select p from Post p order by p.id", Post.class).getResultList()
                .stream().map(Views::post).toList();
    }

    @GET @Path("users")
    public Object users() {
        User me = identity.current();
        if (me.role != Role.ADMIN && me.role != Role.ARCHIVIST)
            throw ApiException.forbidden("仅管理人员/档案员可查看人员名册");
        return em.createQuery("select u from User u order by u.id", User.class).getResultList()
                .stream().map(Views::user).toList();
    }

    @GET @Path("enums")
    public Object enums() {
        identity.current();
        return D.of(
            "roles", List.of("EMPLOYEE", "ARCHIVIST", "ADMIN"),
            "securityLevels", List.of(
                D.of("name", "INTERNAL", "label", "内部", "level", 1),
                D.of("name", "CONFIDENTIAL", "label", "秘密", "level", 2),
                D.of("name", "SECRET", "label", "机密", "level", 3)));
    }
}
