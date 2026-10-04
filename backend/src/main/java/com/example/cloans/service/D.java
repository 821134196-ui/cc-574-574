package com.example.cloans.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 轻量有序 Map 构造器，用于接口返回 JSON */
public final class D {
    private D() {}

    public static Map<String, Object> of(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("kv must be pairs");
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
