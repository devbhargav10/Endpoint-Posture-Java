# PR6.7 Worker Deployment Package — Handoff

## Task and boundaries

Implement the Windows worker deployment package from the existing operational-readiness worktree as a focused PR6.7 branch. Do not commit, push, merge, open a PR, install services, create accounts, alter machine policy/ACLs, enroll real workers, run endpoint collection, or provision infrastructure. Keep PR6.5/6.6 runtime behavior and the original main checkout untouched.

## Worktree and branch

- Main checkout: `C:\Users\devbh\Downloads\endpoint-posture-java\Endpoint-Posture-Java`
- Operational worktree: `C:\Users\devbh\Downloads\endpoint-posture-java\endpoint-posture-pr6-ops`
- Original operational branch: `codex/pr6-ops-acceptance` at `8b69dd15103d0e6730cf743fedbfc5000aaa9807`; left intact.
- New branch: `codex/pr6-7-worker-deployment`
- Branch base: `8f58b941925dc52687f14af10112a3b896e2b426`
- The new branch was created after confirming the operational worktree was clean, HEAD matched, and the main checkout was still at the requested base.
- The main checkout remains at `8f58b941925dc52687f14af10112a3b896e2b426` on `feature/terraform-foundation`. Its previously observed uncommitted frontend/Terraform files were not changed.

## Approved package carried into new branch

Only these source package files were restored from the operational branch before editing:

- `deploy/WORKER-DEPLOYMENT.md`
- `deploy/EndpointPostureWorker.xml`
- `deploy/Prepare-PostureWorkerDeployment.ps1`
- `deploy/Provision-WorkerServiceAccount.ps1`
- `deploy/Configure-WorkerServiceAcl.ps1`
- `deploy/Initialize-PostureWorkerCredentials.ps1`
- `deploy/Install-PostureWorkerService.ps1`
- `deploy/Start-PostureWorkerService.ps1`
- `deploy/Stop-PostureWorkerService.ps1`
- `deploy/Remove-PostureWorkerService.ps1`
- `docs/operations/PR6-OPERATIONAL-VERIFICATION.md`
- `worker-runtime/README.md`

The user then explicitly requested this handoff note; it is an additional documentation file in the operational worktree only.

## Completed implementation so far

1. Reworked `Prepare-PostureWorkerDeployment.ps1` into two parameter sets: build bundle on the build host from a repository/JAR plus externally approved WinSW, then stage on the target from the bundle only. The bundle includes the shaded JAR, exactly the four scripts allowlisted by `PowerShellExecutor`, WinSW XML, credential bootstrap and staging helper, WinSW binary, and a file-hash manifest. Target staging checks exact artifact lists, hashes, WinSW product version and independent WinSW SHA-256; it writes absolute service paths, leaves PowerShell execution disabled, and refuses existing standard install/data directories. It does not install/start a service or apply ACLs.
2. Reworked `Configure-WorkerServiceAcl.ps1`: independently canonicalizes paths, limits them to the standard worker install/data/log directories under Program Files/ProgramData, rejects volume roots, UNC/device paths, reparse points, aliases and overlap, distinguishes install Read/Execute from data/log Modify, and uses `ShouldProcess`. Added `-ValidateOnly` for no-change path checks and elevated-user bypass for genuine `-WhatIf` previews only.
3. Updated `Provision-WorkerServiceAccount.ps1` to report partial provisioning explicitly and retain the account if service-logon-right setup/verification fails. No automatic account deletion or privilege broadening was added.
4. Updated credential bootstrap to reject a pre-existing enrollment-token environment variable, restore prior non-token process environment, and wait for both DPAPI state and SQLite database before its bounded force-stop. Runtime has no enrollment-only graceful shutdown; docs state that limitation and why bootstrap runs with no handler and collection disabled.
5. Hardened install/start/stop/remove checks: installer checks service XML identity/paths and disabled execution, verifies final SCM identity/start mode/stopped state, and improves failed-install cleanup reporting. Start and stop scripts verify service identity and executable. Start confirms collection is disabled and warns that SCM/PID is not app readiness. Removal verifies the service identity/executable and retains state/outbox/logs.
6. Rewrote the worker deployment guide, worker runtime README and operational verification record. They distinguish build-host vs target-host actions, make endpoint collector credential limitations explicit, separate service removal/backend key revocation/account retirement/data retention, and preserve prior test counts as historical.

## Important confirmed runtime dependency

`worker-runtime/src/main/java/com/endpointposture/workerruntime/execution/JobSpecParser.java` passes remote targets to the allowlisted scripts. `scripts/posture_agent.ps1`, `hardware_health_agent.ps1`, and `diagnostic_agent.ps1` use `posture_common_cred.xml` for remote CIM/WinRM authentication. The bundle intentionally does not include this credential file, and current job specifications do not provide a protected endpoint credential channel. The docs therefore keep PowerShell execution disabled and state that remote collection is not ready until a separate credential design is approved. Do not solve this by embedding passwords in XML, env vars, or job arguments.

## Validation already performed

- PowerShell parser: all 8 changed `.ps1` files passed after the latest edits.
- WinSW XML parsed; service ID is `EndpointPostureWorker`, and XML default `EP_POWERSHELL_EXECUTION_ENABLED=false` was confirmed. An in-memory substitution check found no remaining `__PLACEHOLDER__` tokens.
- Static comparison confirmed the four bundled collector scripts match the `PowerShellExecutor` allowlist and the staged environment variables are present. Relative Markdown links in the three updated docs resolve.
- ACL disposable-temp test passed after finding/fixing an invalid `SecurityIdentifier` type name. `-ValidateOnly` succeeded, `-WhatIf` listed only the intended install/data/log roots, ACL SDDL snapshots were unchanged, and unsafe volume-root input was rejected. Temp fixture was removed. This was not a real deployment ACL change.
- A targeted Maven test attempt failed because Maven first could not create the default `C:\.m2\repository`. Retrying with a repository under `%TEMP%` failed because Maven Central network access was denied (`Permission denied: getsockopt`). The worker component tests have not run in this implementation session; they are optional because no worker Java/runtime source changed.

## Remaining work before reporting completion

1. Review `Prepare-PostureWorkerDeployment.ps1` carefully for parameter-set/artifact-count consistency, Java 21 validation, self-contained helper verification, and safe staging rollback. Confirm the bundle contains its own Stage helper, so no repository is needed on the target.
2. Inspect the complete diff and compare the untracked new files against the allowed scope.
3. Recheck `git diff --check`, status, diff summary, main checkout HEAD/status, and do not commit/push/merge/open a PR.
4. Final report should state that Windows service identity, DPAPI-after-SCM-restart, live backend delivery, and real response-loss replay remain unverified/host-dependent. Do not claim operational acceptance.

## Current expected stopping point

Leave the working tree uncommitted on `codex/pr6-7-worker-deployment`. Return branch/base, modified/added files, findings resolved, tests/commands and their true results, limitations, outstanding Windows acceptance, diff summary, and final Git status. Wait for human review.
