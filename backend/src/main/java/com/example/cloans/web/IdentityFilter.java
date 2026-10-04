package com.example.cloans.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/** 从请求头提取 X-User-Id（开发态模拟登录） */
@Provider
public class IdentityFilter implements ContainerRequestFilter {

    @Inject
    IdentityHolder holder;

    @Override
    public void filter(ContainerRequestContext ctx) {
        holder.userIdHeader = ctx.getHeaderString("X-User-Id");
    }
}
