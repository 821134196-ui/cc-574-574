package org.acme.contracts.security;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/** 从 X-User-Id 请求头解析当前操作人。 */
@Provider
public class UserHeaderFilter implements ContainerRequestFilter {

    @Inject
    CurrentUser currentUser;

    @Override
    public void filter(ContainerRequestContext ctx) {
        currentUser.resolve(ctx.getHeaders());
    }
}
