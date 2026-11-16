# Backend release readiness

**Audit date:** 2026-11-16
**Branch under verification:** `codex/application-integration`
**Base:** `main` at `71b77a4df639d37796da3f53db74d8e463b87198`
**State:** Local backend, worker, frontend, and live frontend/backend API checks passed. This branch has not yet been pushed; PR checks and production acceptance remain outstanding.

## Implementation reviewed

The application baseline includes Spring Boot API and Flyway schema management, JWT login and RBAC, bounded collection APIs, worker enrollment and revocable credentials, heartbeat and lifecycle state, job leases and fencing, active-job deduplication, crash recovery, durable result receipts/replay, Cisco ISE TLS verification and hardened XML parsing, ISE action auditing/reconciliation/recovery, and a Linux profile that does not run the Windows PowerShell worker.

The ISE TLS/XML protections introduced in the PR #17 security work are present in `main`; `main` also has later durable ISE action handling. PR #17 was not blindly merged. Remote PowerShell endpoint collection remains disabled until secure endpoint credential delivery is implemented and tested.

## Verification results

Backend command, run from `backend/`:

```text
mvn -B clean verify "-Duser.timezone=UTC"
```

- Exit code 0; `BUILD SUCCESS`; 4m41s.
- 84 Surefire XML reports; 595 tests; 0 failures, 0 errors, 0 skipped.
- Docker/Testcontainers PostgreSQL 16.15 ran successfully.
- Flyway validated and exercised 29 migrations through V32, including worker authentication/receipts, recovery, active-job deduplication, and durable ISE operations.
- Spring Boot executable JAR was packaged at `backend/target/endpoint-posture-java-0.1.0-SNAPSHOT.jar`.
- JPA and Hikari contexts shut down cleanly. The earlier forced Surefire shutdown issue was fixed by closing database-backed Spring test contexts after each class. This run had no forced-fork termination. I also removed the duplicate Surefire timezone `argLine` and the ineffective Hikari idle-timeout setting for the fixed-size pool; the final run did not emit either warning.

Windows worker command, run from `worker-runtime/`:

```text
mvn -B clean verify "-Duser.timezone=UTC"
```

- Exit code 0; `BUILD SUCCESS`; 48.9s.
- 107 tests; 0 failures, 0 errors, 1 skip. `DpapiSecretProtectorTest` skips its non-Windows branch; Windows DPAPI and PowerShell execution tests ran on this Windows host.
- Shaded worker runtime JAR packaged. Maven Shade still reports expected duplicate metadata/module-info resource warnings; no test/build failure resulted.
- Fixed an asynchronous test race by waiting for the worker handler to leave its busy state after result handoff; runtime behavior was unchanged.

Frontend commands, run from `frontend/`:

- `npm run lint` — exit 0.
- `npm run typecheck` — exit 0.
- `npm run test` — 6 files and 30 tests passed.
- `npm run build` — exit 0; Next.js production build generated 19 routes.
- Added `npm run test:integration`, which uses the real frontend API client against the packaged backend: login/token, dashboard summary, paged endpoints, and admin users. Local live run passed 1/1 test against disposable PostgreSQL and backend.

The CI backend job now starts the packaged application against its PostgreSQL service and runs this API integration check. GitHub Actions for this new branch have not run yet. The frontend CI job uses Node 22; local frontend checks passed with the installed Node runtime, which emitted a Node engine warning for one dependency.

## Operational and security limits

- Production must supply unique JWT, admin, database, and (if enabled) ISE credentials through an approved secret store. Configure the deployment TLS certificate/trust chain and review database migrations before release.
- ISE TLS certificate validation is mandatory; disabling verification fails startup. A configured truststore is supported, with JVM default trust otherwise. Secure XML parsing rejects DTDs and external entities. No live ISE system, production CA chain, hostname, or deployed ISE version was tested.
- Worker/IP rate buckets are local to each backend replica. A production gateway still needs a shared ingress ceiling and correct client-IP trust configuration. Confirm request-size limits at the deployed proxy.
- Backend-to-Windows worker enrollment, heartbeat, lease renewal/fencing, receipt replay, revocation, and recovery still need acceptance against the selected Linux deployment. Windows service installation/upgrade and DPAPI persistence across a real SCM restart remain host-specific acceptance.
- Remote PowerShell collection must remain disabled until protected endpoint credential delivery, rotation, audit, and isolation are implemented and accepted. Do not put endpoint credentials in job arguments or service XML.
- No paid AWS infrastructure was provisioned. Terraform remains subject to extraction into its own repository. Production networking, workload IAM, state bootstrap, database backup/restore, monitoring, alerting, and incident procedures require environment-specific review.

## Release gates

1. Push the verified branch and confirm required GitHub Actions checks on its pull request.
2. Test the deployed ISE certificate chain/hostname with approved trust material and the target ISE version.
3. Perform authenticated Windows-worker to Linux-backend acceptance for enrollment, heartbeat, lease/fencing, retry, revocation, and crash recovery.
4. Complete and test secure endpoint credential delivery before enabling remote PowerShell collection.
5. Approve deployment secret provisioning, gateway ingress/client-IP policy, state bootstrap, network egress, database backup/restore, and monitoring.
