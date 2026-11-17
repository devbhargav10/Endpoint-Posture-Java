# PR6 operational verification

## Scope and evidence boundary

This record separates implementation, automated component coverage, and Windows host acceptance. A source-code review, parser check, or component test does not prove an installed Windows service, service-account DPAPI continuity, live worker/backend delivery, or response-loss recovery.

### Historical test evidence (2026-11-11; recorded before PR6.7 package changes)

The operational-readiness branch report recorded these earlier results:

- Worker runtime `clean verify`: 107 tests, 0 failures, 0 errors, 1 skipped. The real DPAPI and real-agent Windows tests passed under the interactive verification identity. The skipped test was the non-Windows-only failure case.
- Backend Maven verification: 549 tests, 0 failures, 0 errors, 0 skipped, using PostgreSQL 16/Testcontainers.
- SQLite/outbox and dispatcher component tests included restart reconstruction and simulated lost-ack exact-byte replay.
- Deployment PowerShell/XML syntax checks were reported as passing at that time.

These are **historical, author-recorded results**, not test results from the current PR6.7 implementation run. They do not establish a real service identity, SCM restart, live worker/backend request, or real response drop after backend commit.

## Deployment package behavior

The worker deployment guide and scripts define a dedicated local `svc-posture-worker` identity and a separate `EndpointPostureWorker` WinSW service. The package is split between build host and target host:

- Build host: package the shaded JAR, four allowlisted collector scripts, WinSW template, credential bootstrap, approved external WinSW v2.12.0 binary, and a SHA-256 manifest.
- Target host: verify the transferred bundle and independent WinSW digest, stage standard install/data paths, and configure the service XML. Staging does not install/start a service, provision an account, apply ACLs, enroll, or collect.
- Administrative account, ACL, enrollment, service install/start/stop/remove are separate explicit steps.

The bundle manifest detects file corruption but is not signed. Artifact authenticity depends on the approved build and transfer channel; the external WinSW digest is independently checked on the target.

## Current implementation limitations

- No Windows service or service account is created by the repository code. The actual SCM identity, service-logon right, profile behavior and ACLs must be verified on the target host.
- DPAPI remains `CurrentUser`. The temporary credential bootstrap has no runtime enrollment-only shutdown operation; it waits for the protected state and SQLite database, then force-stops its short-lived JVM. It has no job handler and leaves PowerShell disabled, so it does not claim collection jobs. This is not proof of DPAPI success under SCM after restart.
- WinSW `Running` plus a live PID is process evidence only. Current worker runtime has no local health endpoint or successful-heartbeat log marker. Verify `lastSeenAt` through the ADMIN-only backend `GET /api/v1/admin/workers` and inspect sanitized logs.
- The PowerShell scripts use `posture_common_cred.xml` for remote CIM/WinRM authentication. The bundle intentionally contains no endpoint credential file or secret, and the current worker job contract has no protected credential channel for it. Keep `EP_POWERSHELL_EXECUTION_ENABLED=false` until a separate endpoint credential design is approved and tested.
- The current runtime does not expose a documented enrollment-only graceful shutdown. The bounded force-stop is limited to bootstrap while execution is disabled and no handler is installed; the genuine service restart remains required acceptance.
- Service removal retains state, outbox, WAL/SHM and logs. Backend key revocation, local account retirement and data/evidence retention are separate reviewed actions, not automated cleanup.

## Required Windows acceptance

Use a disposable worker and backend. Keep production collection disabled.

1. Verify the service SCM `StartName` is exactly the dedicated worker identity; confirm effective service-logon right and reviewed ACLs on install/data/log paths.
2. Enroll under that same identity and prove DPAPI decryption after a genuine SCM stop/start. Never display or copy state-file contents.
3. Confirm the worker is visible in the backend ADMIN worker inventory with a recent heartbeat. SCM status alone is insufficient.
4. Verify SQLite can create/open the database and sidecars under the service identity, then preserve a pending test result across an actual JVM/SCM restart and compare exact serialized request bytes/hash.
5. In a disposable environment, after separately approving the endpoint credential design, run a test-only job with explicit temporary execution opt-in. Verify one accepted backend receipt and a delivered outbox row.
6. Route the result endpoint through a test-only proxy: let the backend commit the first request, discard only that response, then forward the retry and return the matching duplicate receipt. Verify byte-identical requests, one effective receipt/evidence application, and delivered state.
7. Exercise invalid credentials, stale/conflicting attempts, malformed/mismatched acknowledgements, and transient failures. Confirm unresolved results remain retained and no evidence is silently deleted.
8. Record sanitized service identity, ACL, restart, heartbeat, receipt, outbox and log evidence.

## Acceptance status

**Not operationally verified.** Source and component behavior are present, and historical tests are recorded above. This PR6.7 implementation must be statically validated separately. No service/account/ACL operation or real endpoint collection is authorized by this package review. SCM restart, service identity, live backend delivery and response-drop recovery remain host-dependent acceptance blockers.
