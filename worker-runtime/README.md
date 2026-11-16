# Windows worker runtime

`worker-runtime` is a standalone Java 21 Windows process. It enrolls against the backend, stores its worker key using Windows DPAPI `CurrentUser`, heartbeats, and claims work only when the PowerShell handler is explicitly enabled. Completed results are committed to the SQLite outbox before handoff succeeds.

## Build and local run

From this directory:

```powershell
mvn -B clean verify "-Duser.timezone=UTC"
$env:EP_BACKEND_URL = 'https://<backend-host>'
$env:EP_ENROLLMENT_TOKEN = '<single-use enrollment token>'
java -jar target\worker-runtime-0.1.0-SNAPSHOT.jar
```

For local development only, the worker permits HTTP on loopback. Non-loopback origins require HTTPS. Enrollment tokens are single-use. The key is saved in DPAPI-protected form, never as plaintext in the credential state file or SQLite outbox.

## Runtime configuration

| Variable | Default | Meaning |
|---|---|---|
| `EP_BACKEND_URL` | Required | Backend origin only. Must match the enrolled origin; non-loopback requires HTTPS. |
| `EP_ENROLLMENT_TOKEN` | None | Single-use token read from the process environment only during enrollment. |
| `EP_WORKER_STATE_FILE` | `%ProgramData%\EndpointPostureWorker\state.json` on Windows | DPAPI-protected identity and worker key. |
| `EP_WORKER_DATA_DIRECTORY` | Parent directory of the state file | Persistent SQLite outbox directory; database name is `worker-results.sqlite`. |
| `EP_POWERSHELL_EXECUTION_ENABLED` | `false` | Explicit opt-in for collection. Keep disabled until endpoint permissions, contracts and credential provisioning are approved. |
| `EP_SCRIPTS_DIRECTORY` | `scripts` under the working directory | Collector scripts. |
| `EP_POWERSHELL_EXE` | `powershell.exe` | PowerShell executable. |
| `EP_JOB_LEASE_SAFETY_MARGIN_SECONDS` | `15` | Time reserved before the claimed-job lease deadline. |
| `EP_POWERSHELL_MAX_OUTPUT_BYTES` | `8388608` | Maximum captured output bytes per stream. |
| `EP_HTTP_CONNECT_TIMEOUT_SECONDS` | `10` | Backend connection timeout. |
| `EP_HTTP_REQUEST_TIMEOUT_SECONDS` | `30` | Backend request timeout. |
| `EP_RESULT_DELIVERY_GRACE_MINUTES` | `15` | Result delivery lease-renewal grace. |
| `EP_RESULT_MAX_CUMULATIVE_LEASE_EXTENSION_SECONDS` | `900` | Cumulative lease-extension ceiling. |

## Windows service deployment

Follow [the worker deployment guide](../deploy/WORKER-DEPLOYMENT.md). Build hosts create a self-contained, checksummed deployment bundle; target hosts stage that bundle without requiring a Git checkout. The selected Windows service identity is a dedicated local `svc-posture-worker`, distinct from backend `svc-posture`. The identity that enrolls must be the same identity that runs the service, because DPAPI uses `CurrentUser`.

The WinSW template is `deploy/EndpointPostureWorker.xml`. It starts in Manual mode with collection disabled. The installer verifies identity, configures Automatic startup, and leaves the service stopped. Start/stop/remove use SCM and preserve the evidence store. SCM Running plus a live PID proves process status only; the backend worker inventory must show a recent heartbeat before treating the application as available.

For this deployment, do not use the legacy `Configure-WorkerDataDirectory.ps1`; it sets a machine-wide environment variable and grants Full Control. The deployment XML sets the worker-specific data path and the ACL script applies the reviewed narrower permissions.

## Collector credential limitation

The allowlisted legacy PowerShell scripts use `posture_common_cred.xml` for remote CIM/WinRM authentication. The deployment bundle does not contain that file or endpoint credentials, and current worker job specifications do not supply a protected credential channel. PowerShell execution must remain disabled until a separate least-privilege endpoint credential provisioning design is reviewed and tested. Do not put credentials in job arguments, environment configuration, or service XML.

## Durable result delivery and recovery

- SQLite uses ordered schema migration, WAL, `synchronous=FULL`, a busy timeout, and foreign-key enforcement on persistent local storage.
- A result is handed off only after its immutable serialized request is committed. The outbox stores claim identity, hashes bytes, and checks integrity on load.
- The dispatcher retries the exact stored request bytes, validates backend acknowledgements, and retains unresolved/permanent results.
- DPAPI-protected state is separate from SQLite. Do not copy it across users or machines.
- Stop the service and verify the JVM exits before copying or backing up the DB and any WAL/SHM sidecars. Never delete unresolved outbox data to recover space.
- The credential bootstrap starts a temporary worker JVM with execution disabled, waits for the DPAPI state and SQLite store, then force-stops it. The runtime has no enrollment-only graceful-stop mode; this does not prove SCM restart recovery.
- Service deployment, service-account DPAPI continuity, live backend delivery, and response-loss recovery require host-level acceptance. Automated component tests do not establish Windows service acceptance.
