package com.delivery.web_bff.domain.session;

/** Browser secrets are returned only at creation time; persistence stores their protected forms. */
public record SessionMaterial(String rawSessionId, String rawCsrfToken, WebSession session) { }
