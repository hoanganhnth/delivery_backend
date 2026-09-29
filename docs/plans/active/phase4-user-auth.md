# Phase 4: User, Auth, Web BFF, and Shipper Migration Plan

This document outlines the extraction and modernization of the `user-service`, `auth-service`, `web-bff`, and `shipper-service` to adhere to the modular platform architecture.

## 1. Current State
- `user-service` manages `User` profiles, `UserAddress`, block status, and outbox seeding.
- `auth-service` manages `AuthAccount`, JWT Tokens, `RefreshTokenRecord`, `AuthSession`, `IdentityRegistration`, and JWKS.
- Both services contain a mix of HTTP mapping, database transactions, domain logic, and outbox polling within their monolithic layers.
- BFFs (Web and Shipper) need to adopt standard identity client mechanisms.

## 2. Architecture Goal
Similar to Phase 1 (Restaurant), we will establish a Ports and Adapters architecture:
- `modules/user/user-domain`: Pure POJOs/Records, policies, status invariants.
- `modules/user/user-application-api`: Commands, Queries, Results, Use Cases, Ports.
- `modules/user/user-application`: Orchestration of use cases, transaction boundaries.
- `modules/user/user-infrastructure`: JPA Entities, Repositories, Kafka Adapters, Controllers, Cache.
- The same exact structure for `modules/auth/`.

## 3. Implementation Sequence (Slices)

### User Service Slices
- **Slice 1:** Extract `user-domain` and `user-application-api`. Define `User`, `UserAddress` models, `CreateUserCommand`, `UpdateProfileCommand`.
- **Slice 2:** Extract `user-application` use cases for profile management and block status.
- **Slice 3:** Extract `user-infrastructure` persistence adapters (JPA for `User`, `UserAddress`, `IdentityOutboxEvent`).
- **Slice 4:** Host migration: Move `UserController` and `UserAddressController` logic to delegate to the new Application Ports.

### Auth Service Slices
- **Slice 5:** Extract `auth-domain` (Models for Account, Session, Token validity rules, Registration policies).
- **Slice 6:** Extract `auth-application` (LoginUseCase, RegistrationUseCase, RefreshTokenUseCase, SocialLoginUseCase).
- **Slice 7:** Extract `auth-infrastructure` (JPA for `AuthAccount`, `RefreshTokenRecord`, `AuthSession`, Identity Inbox/Outbox).
- **Slice 8:** Host migration: Migrate `AuthController`, `JwksController`, and `PrincipalInternalController` to use Application Ports.

### BFF and Shipper Slices
- **Slice 9:** Migrate `web-bff` and `shipper-service` to fully adopt `auth-resource-server-starter` and `identity-client`.
- **Slice 10:** End-to-end integration proof (PostgreSQL-canonical auth/user check).

## 4. Constraints
- Do not modify database schemas or HTTP response structures.
- Use `git worktree` and commit incrementally per slice.
- Run `mvn clean verify` to ensure zero regression in tests.
