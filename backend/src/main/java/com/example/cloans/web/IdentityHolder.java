package com.example.cloans.web;

import jakarta.enterprise.context.RequestScoped;

/** 保存当前 HTTP 请求携带的模拟身份头 */
@RequestScoped
public class IdentityHolder {
    public String userIdHeader;
}
