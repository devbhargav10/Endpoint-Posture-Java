# Windows worker deployment package

This package installs the standalone Windows worker. It is separate from the backend service configured by `endpoint-posture.xml` and `Install-PostureService.ps1`. The worker uses service `EndpointPostureWorker` and the dedicated local identity `COMPUTERNAME\svc-posture-worker`; it must not change or reuse backend service `EndpointPostureEngine` / `svc-posture`.

No account, policy, ACL, service, enrollment, or endpoint collection is performed by bundle preparation. Administrative steps below are explicit operator actions. No service is installed or started by the staging command.

## Build-host bundle creation

On a trusted build host with Java 21 and Maven, build and verify the worker artifact from the reviewed source revision:

```powershell
Push-Location worker-runtime
mvn -B clean verify "-Duser.timezone=UTC"
Pop-Location
```

Obtain the organization-approved x64 WinSW v2.12.0 binary through the approved software supply chain. Do not download it from this script or commit it. Independently record its approved SHA-256 digest. Create a new, empty bundle destination; the script refuses an existing destination:

```powershell
.\deploy\Prepare-PostureWorkerDeployment.ps1 `
  -RepositoryRoot 'C:\src\Endpoint-Posture-Java' `
  -BundleDirectory 'C:\staging\EndpointPostureWorker-bundle' `
  -WinSWPath 'C:\approved\WinSW.NET461.exe' `
  -ExpectedWinSWSha256 '<64-hex independently approved digest>'
```

The self-contained bundle contains the shaded JAR, the four scripts allowlisted by `PowerShellExecutor`, the worker WinSW XML template, the credential bootstrap and staging scripts, WinSW, and a manifest of file hashes. The manifest detects accidental corruption; it is not a signature and does not authenticate the JAR or scripts. Transfer the bundle through the organization’s trusted artifact channel. Keep source revision/build provenance with the artifact. The separately approved WinSW digest is checked again on the target host.

## Target-host staging

Copy the complete bundle directory to a new staging location on the worker host. A Git checkout is not required. Run the staging helper from inside that verified bundle in an elevated PowerShell session because it creates directories under Program Files and ProgramData. Use an HTTPS backend origin, or loopback HTTP only for a local disposable test:

```powershell
$bundle = 'C:\staging\EndpointPostureWorker-bundle'
& "$bundle\Prepare-PostureWorkerDeployment.ps1" `
  -BundleDirectory $bundle `
  -BackendUrl 'https://<disposable-backend-host>' `
  -ExpectedWinSWSha256 '<same 64-hex independently approved digest>'
```

Staging accepts only the required bundle files and verifies the manifest, each file digest, WinSW product version and independently supplied WinSW digest. It requires the standard non-existing paths `%ProgramFiles%\EndpointPostureWorker` and `%ProgramData%\EndpointPostureWorker`; it refuses to overwrite either. It writes a configured XML with absolute paths, `EP_POWERSHELL_EXECUTION_ENABLED=false`, and creates the empty data/log directories. It does not modify ACLs, create an account, enroll a worker, install a service, or start collection. A failed copy can leave a partial install tree for explicit operator inspection; do not delete it blindly.

## Identity, permissions, and ACLs

The selected identity is the local account `COMPUTERNAME\svc-posture-worker`, separate from the backend identity. It needs **Log on as a service**, Read/Execute on the worker installation, and Modify on the private data and log directories. It must not be a local administrator. Add endpoint-specific CIM/WinRM access only after the collector’s needs are reviewed. Domain policy can override local rights.

From an elevated PowerShell session, provision the account:

```powershell
.\deploy\Provision-WorkerServiceAccount.ps1
```

The script requests a password securely and does not automatically remove an account if later service-logon-right configuration fails. A `PARTIAL PROVISIONING` message means the account may exist without the right. Inspect it with `Get-LocalUser -Name svc-posture-worker`; inspect local policy and controlling domain GPO. Grant only the missing **Log on as a service** right through the approved policy channel, or have an administrator retire the account only after confirming it is unused. Do not rerun account creation or grant broader privileges as a workaround.

Review the path checks first. `-ValidateOnly` checks the canonical layout and reparse-point constraints without requiring elevation or changing ACLs. The ACL script is deliberately limited to canonical worker directories under Program Files and ProgramData, rejects volume roots, aliases/reparse points and overlapping trees, and replaces inherited/unlisted permissions within those trees:

```powershell
.\deploy\Configure-WorkerServiceAcl.ps1 `
  -ServiceAccount "$env:COMPUTERNAME\svc-posture-worker" `
  -ValidateOnly
```

Then review the proposed ACL operations. `-WhatIf` is a real PowerShell `ShouldProcess` preview and makes no ACL changes:

```powershell
.\deploy\Configure-WorkerServiceAcl.ps1 `
  -ServiceAccount "$env:COMPUTERNAME\svc-posture-worker" `
  -WhatIf
```

Review every path in the preview and current ACLs with `icacls`. Only after approval, repeat the command without `-WhatIf`, then inspect:

```powershell
icacls "$env:ProgramFiles\EndpointPostureWorker"
icacls "$env:ProgramData\EndpointPostureWorker"
icacls "$env:ProgramData\EndpointPostureWorker\logs"
```

If the target’s Program Files/ProgramData layout does not match those canonical directories, stop and review the ACL policy/script; do not bypass its path restrictions.

Do not run the older `worker-runtime/Configure-WorkerDataDirectory.ps1` for this package. That helper sets a machine-wide data-directory environment variable and grants Full Control on the data tree; this package configures the worker environment in its own service XML and uses the narrower reviewed ACL workflow instead. The legacy helper is not part of the target bundle.

## DPAPI enrollment and service installation

DPAPI is `CurrentUser`: the identity that enrolls and protects the worker key must be the exact identity used by the Windows service. Load the service identity’s user profile, then initialize credentials from a profile-enabled shell under that account. Use a single-use token issued for a disposable worker during acceptance. Never place a token on the command line or print the credential state file.

```powershell
runas /profile /user:"$env:COMPUTERNAME\svc-posture-worker" powershell.exe
Set-Location "$env:ProgramFiles\EndpointPostureWorker"
.\Initialize-PostureWorkerCredentials.ps1
```

The bootstrap runs the worker long enough to save protected credentials and initialize SQLite, with PowerShell execution disabled and no job handler, so it does not claim jobs. The current runtime has no bounded enrollment-only shutdown command. The bootstrap therefore uses a bounded wait and force-stops its temporary JVM after both the atomic DPAPI state file and SQLite database appear. This ends that temporary process, not a service process; it is not proof of DPAPI access after SCM restart. A disposable enrollment token may be spent even if local state writing fails; use the backend’s reviewed revoke/re-enrollment process rather than copying keys.

From an elevated shell, install the service. The installer validates the state record’s shape, wrapper digest/version, directory ACLs, and exact SCM identity. It configures Automatic startup but leaves the service **Stopped**:

```powershell
.\deploy\Install-PostureWorkerService.ps1 `
  -ExpectedWinSWSha256 '<64-hex approved digest>'
sc.exe qc EndpointPostureWorker
```

Confirm `SERVICE_START_NAME` is the dedicated local worker account. The backend service configuration is not changed by these worker scripts.

## Startup, process status, and application readiness

The start script validates the service identity and protected-state record, starts through SCM, and checks that SCM reports Running with a live process. That proves process status only. The current worker runtime exposes no local readiness probe or successful-heartbeat log marker. Verify a recent `lastSeenAt` for the named worker in the ADMIN-only backend endpoint `GET /api/v1/admin/workers`, and review sanitized service logs. Do not treat a live PID alone as a healthy worker.

PowerShell execution remains disabled by default. Do not enable it until the target identity, collector permissions, script contracts, endpoint credential model, and disposable acceptance job are reviewed. In particular, the legacy scripts use `posture_common_cred.xml` for remote CIM/WinRM credentials. This bundle deliberately does not include that file or any endpoint credential, and the worker JobSpec does not provide a protected credential channel for it. Remote endpoint collection is therefore **not ready to enable** until a separate approved secret-provisioning design is in place. Do not put endpoint credentials in job arguments or service XML.

## Stop, removal, revocation, and retention

Stop through SCM and verify the Java process exits before copying or backing up SQLite files:

```powershell
.\deploy\Stop-PostureWorkerService.ps1
Get-CimInstance Win32_Service -Filter "Name='EndpointPostureWorker'" |
  Select-Object Name, StartName, State, ProcessId
```

The stop script does not force-kill the service process. If it times out or the process remains alive, preserve the database/WAL/SHM files and investigate before any restart or backup.

Removal is intentionally limited to stopping and unregistering `EndpointPostureWorker`. It verifies the registered identity and executable correspond to this worker package, and retains credential state, SQLite outbox/WAL/SHM files, and logs. These are separate operator decisions:

1. **Service removal:** `Remove-PostureWorkerService.ps1` removes only the worker SCM registration.
2. **Backend credential revocation:** an authorized backend administrator revokes the worker through the authenticated admin workflow. The removal script does not do this.
3. **Local account retirement:** inspect service assignments and policy, then retire the local account separately if approved. The removal script does not delete it.
4. **Evidence/database retention:** preserve and back up unresolved outbox data securely; do not delete unresolved results. Retention or secure retirement requires an explicit data-retention decision and backup/handling review.

Credential rotation also requires coordinated backend revocation/re-enrollment and SCM password update under the same Windows identity. Do not copy `state.json` across machines or accounts. SQLite uses WAL and FULL synchronous commits; while stopped, capture the database and any sidecars together and protect backups.

## Acceptance limitations and required host evidence

Component tests for SQLite, dispatch, exact-byte retries, and DPAPI do not establish service acceptance. The following still require a disposable Windows host and backend:

- Confirm the actual SCM identity, effective service-logon right, ACLs, and the service user profile.
- Prove DPAPI `CurrentUser` decryption after a genuine SCM stop/start under that identity.
- Persist a pending result, stop/restart the JVM through SCM, and confirm exact stored request bytes/hash and recovery.
- Verify the worker heartbeat in the backend; then perform a test-only result delivery with a separately approved endpoint credential approach before enabling collection.
- Drop a response after the disposable backend commits a result, allow the identical retry, and verify one effective backend receipt and delivered outbox state.
- Exercise invalid credentials and stale/mismatched acknowledgements; confirm unresolved data remains retained.

Record sanitized identity, ACL, restart, heartbeat, receipt, outbox, and log evidence. Do not describe component tests as Windows service or live end-to-end acceptance.
