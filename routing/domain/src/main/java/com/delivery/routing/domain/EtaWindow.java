package com.delivery.routing.domain;

public record EtaWindow(int minMinutes, int maxMinutes, String source) {
    public EtaWindow {
        if (minMinutes < 0 || maxMinutes < minMinutes || source == null || source.isBlank()) {
            throw new IllegalArgumentException("ETA window is invalid");
        }
    }
}
