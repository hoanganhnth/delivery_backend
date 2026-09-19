package com.delivery.web_bff.session;

public record SessionMaterial(String rawSessionId, String rawCsrfToken, WebSession session) { }
