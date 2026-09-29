package com.delivery.auth.application.api;

/** Framework-free public registration input. */
public record RegisterCommand(String email, String password, String role) {
}
