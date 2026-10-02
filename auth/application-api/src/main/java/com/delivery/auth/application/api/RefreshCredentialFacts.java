package com.delivery.auth.application.api;

import com.delivery.auth.domain.model.AuthAccount;
import com.delivery.auth.domain.model.Session;
import com.delivery.auth.domain.model.RefreshCredentialState;

public record RefreshCredentialFacts(RefreshCredentialState state, Session session, AuthAccount account) {}
