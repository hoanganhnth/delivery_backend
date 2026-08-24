# Execution Plan: Production Readiness Remediation

Date: 2026-08-01

## Status

Completed (no-runtime validation, 2026-08-01)

## Outcome

The current polyrepo worktree can pass its required local CI gates without a
false-positive secret scan or a host-specific Mockito failure; the in-progress
Auth/User customer-registration handoff is isolated, validated, and committed
without overwriting Task 21 checkout work. Runtime checkout rehearsal and
external production credentials remain explicit follow-up checkpoints rather
than implicit local changes.

## Context

- `docs/WORKFLOW.md` and root `AGENTS.md` govern this cross-repo work.
- Audit on 2026-08-01 found that `backend_delivery/scripts/verify-secrets.sh`
  matches its own PEM-detection literal, so the CI secret step cannot pass.
- The local JDK 17 test reactor fails in `observability-starter` while Mockito
  tries to dynamically attach Byte Buddy; source compilation still succeeds.
- `backend_delivery` has in-progress Auth/User registration and Task 21 changes;
  `delivery_app` has matching registration changes. They must not be bundled
  indiscriminately.
- Task 21 authority and runtime scope remain in
  `docs/plans/active/task-21-voucher-flashsale-checkout.md`; that plan owns
  checkout feature flags and any container/retained-volume rehearsal.

## Scope

In scope:

- Correct the secret scanner without weakening detection.
- Make observability tests portable across the supported JDK 17 developer
  environment and CI.
- Classify and validate the currently dirty registration handoff across Backend
  and Flutter, preserving unrelated checkout changes.
- Record and run appropriate static, focused, and cross-repo gates.

Out of scope:

- Enabling voucher/flash-sale checkout or running its persistent Compose
  rehearsal without explicit approval.
- Supplying real email, FCM/APNs, mobile signing, cloud, or payment credentials.
- Choosing a log aggregation vendor, payment provider, mobile package identity,
  or production backup platform without product/operator authority.

## Approach

1. Snapshot the dirty worktrees and map files to registration versus Task 21.
2. Repair the secret-scanner self-match and prove it retains all intended
   source/config scanning boundaries.
3. Replace the host-specific Mockito attachment requirement with a portable
   test configuration or test seam, without reducing production coverage.
4. Validate the registration handoff through focused Backend/Flutter tests and
   then run the relevant repository and polyrepo gates.
5. Commit only explicitly classified files in coherent repository-local commits.
6. Stop before any checkout runtime rehearsal or credential-backed production
   integration and report the exact required authority.

## Risks And Recovery

- Dirty worktree changes may belong to another task.
  - Stage explicit paths only; never reset, checkout, or use `git add -A`.
- Excluding too much from the secret scan could conceal a credential.
  - Exclude only the scanner's own source literal and preserve scans for all
    other tracked runtime files.
- Replacing Mockito inline could make a test less representative.
  - Keep the same assertions and use test fakes/subclass mocking only where
    behavior is equivalent; prove all observability test cases still run.
- Checkout runtime rehearsal writes retained local volumes.
  - Leave it to its existing Task 21 plan and require explicit approval.

## Progress

- [x] Read root/backend workflow and snapshot current worktree state.
- [x] Classify current dirty changes at a high level: Auth/User registration,
  Gateway rate-limit/route alignment, Task 21 checkout, and generated Flutter
  registration artifacts.
- [x] Repair and validate secret scanner.
- [x] Repair and validate Mockito/JDK test portability.
- [x] Run focused registration validation and separate coherent commits.
- [x] Run full repository/polyrepo gates after the worktree is coherent.
- [x] Record explicit runtime/credential follow-up blockers.

## Decisions

- 2026-08-01: Task 21 remains isolated because its persistent Compose rehearsal
  has an explicit approval boundary and feature flags must stay off.
- 2026-08-01: No real credential or release-signing material will be generated
  or committed by this remediation work.
- 2026-08-01: The user explicitly keeps Docker/VM, emulator/device and
  credential-backed provider execution outside acceptance. Static, in-process
  and localhost-only proof may validate contracts, but must not be reported as
  real infrastructure/provider proof.

## Validation

- Focused proof:
  - `bash -n scripts/verify-secrets.sh` and `bash scripts/verify-secrets.sh`
    passed after scanner repair.
  - An isolated temporary Git fixture with a tracked PEM header was rejected;
    the same fixture passed after removing that tracked file. This proves the
    self-exclusion does not disable the PEM-content rule.
  - `mvn -q -pl observability-starter clean test` passed after adding the
    repository-standard Mockito subclass mock maker.
  - `mvn -q -pl auth-service,user-service,api-gateway -am test` produced
    current zero-error Surefire reports for observability, Auth, User, and
    Gateway on the local JDK 17 host.
  - `mvn -q -pl observability-starter clean test` after Mockito repair.
  - Auth/User/Gateway focused tests and Flutter Auth tests after handoff review.
- Repository-required checks:
  - Backend baseline, API inventory, Compose configuration, and package checks.
  - Flutter analyze/test; Web and Shipper verification when cross-repo gate runs.
  - Root `scripts/verify-mvp-polyrepo-contract.sh`.
- Runtime proof:
  - No destructive runtime proof is included here. Task 21 and credential-backed
    provider smoke tests retain their own approval gates.

Completed evidence:

- Coherent commits: Backend `36f539c` (two-step customer registration),
  `b3e0efc` (Task 21 runtime parity) and `37d3c01` (hermetic Shipper/Saga test
  contexts); Customer App `8f061f3` (registration handoff).
- Focused Backend tests passed for Auth/User/Gateway, Shipper and Saga. The
  broad no-runtime reactor regenerated 250 current Surefire reports across the
  modules with zero `<failure>`/`<error>` records. Its three management-port
  probe tests were run separately as localhost-only tests and passed.
- Backend `verify-build-baseline.sh`, `verify-compose-config.sh`,
  `verify-secrets.sh` and `verify-http-api-inventory.sh` passed. Root
  `verify-mvp-polyrepo-contract.sh` passed.
- Web `npm run verify` passed (39 tests/action contracts/build); Shipper
  `npm run verify` passed (31 suites/117 tests); Flutter `fvm flutter analyze`
  and `fvm flutter test` passed (217 tests). No emulator/device was used.

## Result

The remediation is complete for the approved no-runtime scope. It leaves only
explicitly deferred evidence: Docker/Testcontainers PostgreSQL/Kafka race or
rehearsal where a task requires it, live email/FCM/payment-provider proof, and
real client device sanity. Those require a separate approval and must not be
inferred from this result.
