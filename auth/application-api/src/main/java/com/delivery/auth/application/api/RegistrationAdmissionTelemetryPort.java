package com.delivery.auth.application.api;

public interface RegistrationAdmissionTelemetryPort {
    void record(boolean admitted, String mechanism);
}
