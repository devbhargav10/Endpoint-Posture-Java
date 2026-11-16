<# Starts the provisioned worker service only when protected credentials are present. #>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$InstallDirectory = "$env:ProgramFiles\EndpointPostureWorker",
    [string]$DataDirectory = "$env:ProgramData\EndpointPostureWorker",
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_-]{2,31}$')][string]$ServiceAccount = 'svc-posture-worker',
    [int]$TimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$expectedIdentity = "$env:COMPUTERNAME\$ServiceAccount"
$acceptedServiceIdentities = @($expectedIdentity, ".\$ServiceAccount")
$service = Get-CimInstance Win32_Service -Filter "Name='EndpointPostureWorker'"
if (-not $service) { throw 'EndpointPostureWorker is not installed.' }
if ($service.StartName -notin $acceptedServiceIdentities) { throw "Service identity mismatch: expected $expectedIdentity; found $($service.StartName)." }
$wrapper = Join-Path ([IO.Path]::GetFullPath($InstallDirectory)) 'EndpointPostureWorker.exe'
if ([string]::IsNullOrWhiteSpace($service.PathName) -or $service.PathName.IndexOf($wrapper,[StringComparison]::OrdinalIgnoreCase) -lt 0) { throw 'Refusing to start a service whose executable does not match the worker installation.' }
$configPath = Join-Path ([IO.Path]::GetFullPath($InstallDirectory)) 'EndpointPostureWorker.xml'
if (-not (Test-Path -LiteralPath $configPath -PathType Leaf)) { throw "Worker service XML is missing: $configPath" }
$config = [Xml.XmlDocument]::new(); $config.Load($configPath)
if ($config.SelectSingleNode('/service/id').InnerText -ne 'EndpointPostureWorker' -or
    $config.SelectSingleNode('/service/env[@name="EP_POWERSHELL_EXECUTION_ENABLED"]').GetAttribute('value') -ne 'false') {
    throw 'Refusing to start: worker service identity or disabled-execution policy is invalid.'
}
$unsafeEnvironment = @($config.SelectNodes('/service/env') | Where-Object { $_.name -match '(?i)(PASSWORD|TOKEN|SECRET|API[_-]?KEY|CREDENTIAL)' })
if ($unsafeEnvironment.Count) { throw 'Refusing to start: credential-like values must not be configured in the service XML.' }
$dataPath = [IO.Path]::GetFullPath($DataDirectory)
if ([IO.Path]::GetFullPath($config.SelectSingleNode('/service/env[@name="EP_WORKER_DATA_DIRECTORY"]').GetAttribute('value')) -ne $dataPath) { throw 'Configured worker data directory differs from the requested persistent directory.' }
$expectedStatePath = Join-Path $dataPath 'state.json'
if ([IO.Path]::GetFullPath($config.SelectSingleNode('/service/env[@name="EP_WORKER_STATE_FILE"]').GetAttribute('value')) -ne $expectedStatePath) { throw 'Configured DPAPI state path differs from the persistent data directory.' }
$stateFile = Join-Path ([IO.Path]::GetFullPath($DataDirectory)) 'state.json'
if (-not (Test-Path -LiteralPath $stateFile)) { throw "DPAPI credential state is not initialized: $stateFile" }
$state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if ($state.format -ne 1 -or [string]::IsNullOrWhiteSpace($state.protectedApiKey)) { throw 'Credential state does not contain a protected worker key.' }
if ($TimeoutSeconds -lt 5 -or $TimeoutSeconds -gt 300) { throw 'TimeoutSeconds must be from 5 through 300.' }
if (-not $PSCmdlet.ShouldProcess('EndpointPostureWorker',"Start as $($service.StartName)")) { return }
if ($service.State -ne 'Running') { Start-Service -Name EndpointPostureWorker }
(Get-Service -Name EndpointPostureWorker).WaitForStatus('Running',[TimeSpan]::FromSeconds($TimeoutSeconds))
$service = Get-CimInstance Win32_Service -Filter "Name='EndpointPostureWorker'"
if (-not $service.ProcessId -or -not (Get-Process -Id $service.ProcessId -ErrorAction SilentlyContinue)) {
    throw 'SCM reported Running without a live worker process.'
}
Write-Host "EndpointPostureWorker is Running as $($service.StartName); process ID $($service.ProcessId); worker ID $($state.workerId)."
Write-Warning 'SCM Running state and a live PID prove only that the wrapper process exists. Confirm a recent worker heartbeat in the backend worker inventory and inspect startup logs before treating the worker as ready.'
