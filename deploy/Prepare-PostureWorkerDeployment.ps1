<#
.SYNOPSIS
Builds a self-contained worker bundle on a build host or stages that bundle on a worker host.
.DESCRIPTION
BuildBundle mode reads a completed shaded JAR and repository scripts/template, verifies an
approved external WinSW binary, then creates a checksummed bundle. Stage mode consumes only
that bundle and the independently supplied WinSW digest; it does not need a Git checkout.
Neither mode installs or starts a service, creates an account, or applies ACLs.
#>
[CmdletBinding(SupportsShouldProcess = $true, DefaultParameterSetName = 'Stage')]
param(
    [Parameter(Mandatory = $true, ParameterSetName = 'BuildBundle')]
    [string]$RepositoryRoot,
    [Parameter(Mandatory = $true, ParameterSetName = 'BuildBundle')]
    [Parameter(Mandatory = $true, ParameterSetName = 'Stage')]
    [string]$BundleDirectory,
    [Parameter(Mandatory = $true, ParameterSetName = 'BuildBundle')]
    [string]$WinSWPath,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Fa-f0-9]{64}$')]
    [string]$ExpectedWinSWSha256,
    [Parameter(Mandatory = $true, ParameterSetName = 'Stage')]
    [string]$BackendUrl,
    [Parameter(ParameterSetName = 'Stage')]
    [string]$InstallDirectory = "$env:ProgramFiles\EndpointPostureWorker",
    [Parameter(ParameterSetName = 'Stage')]
    [string]$DataDirectory = "$env:ProgramData\EndpointPostureWorker"
)

$ErrorActionPreference = 'Stop'
$approvedScripts = @(
    'posture_agent.ps1', 'hardware_health_agent.ps1',
    'diagnostic_agent.ps1', 'security_indicator_agent.ps1'
)
$bundleRoot = [IO.Path]::GetFullPath($BundleDirectory)
$manifestName = 'worker-bundle.manifest.json'

function Get-WorkerInputs([string]$Root) {
    $root = [IO.Path]::GetFullPath($Root)
    return @{
        Jar = Join-Path $root 'worker-runtime\target\worker-runtime-0.1.0-SNAPSHOT.jar'
        Scripts = Join-Path $root 'scripts'
        Xml = Join-Path $root 'deploy\EndpointPostureWorker.xml'
        Bootstrap = Join-Path $root 'deploy\Initialize-PostureWorkerCredentials.ps1'
        Prepare = Join-Path $root 'deploy\Prepare-PostureWorkerDeployment.ps1'
    }
}

function Assert-WinSW([string]$Path, [string]$Digest) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "WinSW binary is missing: $Path" }
    $version = [Diagnostics.FileVersionInfo]::GetVersionInfo($Path).ProductVersion
    if ($version -notmatch '^v?2\.12\.0(?:\.0)?(?:\+.*)?$') { throw "Expected approved WinSW v2.12.0 metadata; found '$version'." }
    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
    if ($actual -ne $Digest.ToUpperInvariant()) { throw 'WinSW SHA-256 does not match the independently approved digest.' }
    return @{ Version = $version; Sha256 = $actual }
}

if ($PSCmdlet.ParameterSetName -eq 'BuildBundle') {
    $inputs = Get-WorkerInputs $RepositoryRoot
    foreach ($path in @($inputs.Jar, $inputs.Xml, $inputs.Bootstrap, $inputs.Prepare)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required bundle input is missing: $path" }
    }
    if ((Get-Item -LiteralPath $inputs.Jar).Length -lt 1) { throw 'Worker JAR is empty.' }
    if (-not (Test-Path -LiteralPath $inputs.Scripts -PathType Container)) { throw "Scripts directory is missing: $($inputs.Scripts)" }
    foreach ($scriptName in $approvedScripts) {
        $scriptPath = Join-Path $inputs.Scripts $scriptName
        if (-not (Test-Path -LiteralPath $scriptPath -PathType Leaf)) { throw "Required worker script is missing: $scriptPath" }
    }
    $winsw = Assert-WinSW ([IO.Path]::GetFullPath($WinSWPath)) $ExpectedWinSWSha256
    if (Test-Path -LiteralPath $bundleRoot) { throw "Bundle output already exists; refusing to overwrite: $bundleRoot" }
    if ([IO.Path]::GetPathRoot($bundleRoot).TrimEnd('\') -eq $bundleRoot.TrimEnd('\')) { throw 'Bundle output cannot be a volume root.' }
    if (-not $PSCmdlet.ShouldProcess($bundleRoot, 'Create checksummed worker deployment bundle')) { return }

    New-Item -ItemType Directory -Path $bundleRoot -ErrorAction Stop | Out-Null
    try {
        New-Item -ItemType Directory -Path (Join-Path $bundleRoot 'scripts') | Out-Null
        Copy-Item -LiteralPath $inputs.Jar -Destination (Join-Path $bundleRoot 'worker-runtime.jar')
        foreach ($scriptName in $approvedScripts) { Copy-Item -LiteralPath (Join-Path $inputs.Scripts $scriptName) -Destination (Join-Path $bundleRoot "scripts\$scriptName") }
        Copy-Item -LiteralPath $inputs.Xml -Destination (Join-Path $bundleRoot 'EndpointPostureWorker.xml')
        Copy-Item -LiteralPath $inputs.Bootstrap -Destination (Join-Path $bundleRoot 'Initialize-PostureWorkerCredentials.ps1')
        Copy-Item -LiteralPath $inputs.Prepare -Destination (Join-Path $bundleRoot 'Prepare-PostureWorkerDeployment.ps1')
        Copy-Item -LiteralPath $WinSWPath -Destination (Join-Path $bundleRoot 'WinSW.NET461.exe')
        $files = @('worker-runtime.jar') + @($approvedScripts | ForEach-Object { "scripts\$_" }) + @('EndpointPostureWorker.xml','Initialize-PostureWorkerCredentials.ps1','Prepare-PostureWorkerDeployment.ps1','WinSW.NET461.exe')
        $entries = foreach ($relative in $files) {
            $file = Join-Path $bundleRoot $relative
            [pscustomobject]@{ path = $relative.Replace('\','/'); sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash }
        }
        [pscustomobject]@{ format = 1; winswProductVersion = $winsw.Version; files = @($entries) } |
            ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $bundleRoot $manifestName) -Encoding UTF8
    } catch {
        # Remove only the bundle directory created by this invocation; never touch pre-existing paths.
        Remove-Item -LiteralPath $bundleRoot -Recurse -Force -ErrorAction SilentlyContinue
        throw
    }
    Write-Host "Created worker bundle at $bundleRoot. Transfer this directory unchanged to the worker host."
    return
}

# Target-host stage mode consumes only the transferred bundle.
$InstallDirectory = [IO.Path]::GetFullPath($InstallDirectory)
$DataDirectory = [IO.Path]::GetFullPath($DataDirectory)
$LogDirectory = Join-Path $DataDirectory 'logs'
$StateFile = Join-Path $DataDirectory 'state.json'
$uri = [Uri]$BackendUrl
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http','https') -or
    -not [string]::IsNullOrEmpty($uri.UserInfo) -or $uri.Query -or $uri.Fragment -or
    ($uri.AbsolutePath -notin @('', '/')) -or
    ($uri.Scheme -eq 'http' -and $uri.Host -notin @('localhost','127.0.0.1','::1'))) {
    throw 'BackendUrl must be an origin only; use HTTPS except for loopback tests. Credentials, paths, queries and fragments are not allowed.'
}
if (Test-Path -LiteralPath $InstallDirectory) { throw "Install directory already exists; refusing to overwrite: $InstallDirectory" }
if (Test-Path -LiteralPath $DataDirectory) { throw "Data directory already exists; inspect and preserve it before staging: $DataDirectory" }
if ([string]::IsNullOrWhiteSpace($env:ProgramFiles) -or [string]::IsNullOrWhiteSpace($env:ProgramData)) { throw 'ProgramFiles and ProgramData must be defined on the worker host.' }
$expectedInstall = [IO.Path]::GetFullPath((Join-Path $env:ProgramFiles 'EndpointPostureWorker'))
$expectedData = [IO.Path]::GetFullPath((Join-Path $env:ProgramData 'EndpointPostureWorker'))
if (-not $InstallDirectory.Equals($expectedInstall,[StringComparison]::OrdinalIgnoreCase)) { throw "Install path must be exactly $expectedInstall." }
if (-not $DataDirectory.Equals($expectedData,[StringComparison]::OrdinalIgnoreCase)) { throw "Data path must be exactly $expectedData." }
if ($InstallDirectory.StartsWith($DataDirectory.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase) -or
    $DataDirectory.StartsWith($InstallDirectory.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Install and data directories must be disjoint.'
}

$manifestPath = Join-Path $bundleRoot $manifestName
if (-not (Test-Path -LiteralPath $bundleRoot -PathType Container)) { throw "Bundle directory is missing: $bundleRoot" }
if ((Get-Item -LiteralPath $bundleRoot -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Bundle directory cannot be a reparse point.' }
if ($bundleRoot.TrimEnd('\') -eq [IO.Path]::GetPathRoot($bundleRoot).TrimEnd('\')) { throw 'Bundle directory cannot be a volume root.' }
if ($bundleRoot.StartsWith($InstallDirectory.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase) -or
    $InstallDirectory.StartsWith($bundleRoot.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase) -or
    $bundleRoot.StartsWith($DataDirectory.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase) -or
    $DataDirectory.StartsWith($bundleRoot.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Bundle, install and data paths must be disjoint.' }
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "Bundle manifest is missing: $manifestPath" }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if ($manifest.format -ne 1 -or $manifest.files.Count -ne 9) { throw 'Unsupported or incomplete worker bundle manifest.' }
$expectedFiles = @('worker-runtime.jar') + @($approvedScripts | ForEach-Object { "scripts/$_" }) + @('EndpointPostureWorker.xml','Initialize-PostureWorkerCredentials.ps1','Prepare-PostureWorkerDeployment.ps1','WinSW.NET461.exe')
if ((@($manifest.files.path | Sort-Object) -join '|') -ne (@($expectedFiles | Sort-Object) -join '|')) { throw 'Bundle manifest contains missing, duplicate or unexpected artifacts.' }
$expectedTopLevel = @('worker-runtime.jar','scripts','EndpointPostureWorker.xml','Initialize-PostureWorkerCredentials.ps1','Prepare-PostureWorkerDeployment.ps1','WinSW.NET461.exe',$manifestName) | Sort-Object
$actualTopLevel = @(Get-ChildItem -LiteralPath $bundleRoot -Force | ForEach-Object Name | Sort-Object)
if (($actualTopLevel -join '|') -ne ($expectedTopLevel -join '|')) { throw 'Bundle contains missing or unexpected top-level files.' }
$bundledPreparePath = [IO.Path]::GetFullPath((Join-Path $bundleRoot 'Prepare-PostureWorkerDeployment.ps1'))
if (-not [IO.Path]::GetFullPath($PSCommandPath).Equals($bundledPreparePath,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Run Stage mode from the Prepare-PostureWorkerDeployment.ps1 file inside the verified bundle.'
}
foreach ($entry in $manifest.files) {
    $relative = $entry.path.Replace('/',[IO.Path]::DirectorySeparatorChar)
    $file = [IO.Path]::GetFullPath((Join-Path $bundleRoot $relative))
    if (-not $file.StartsWith($bundleRoot.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Invalid or missing bundle artifact: $($entry.path)" }
    if ((Get-Item -LiteralPath $file -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Bundle artifact cannot be a reparse point: $($entry.path)" }
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Bundle artifact digest mismatch: $($entry.path)" }
}
$scriptDirectory = Join-Path $bundleRoot 'scripts'
if (-not (Test-Path -LiteralPath $scriptDirectory -PathType Container)) { throw 'Bundle scripts directory is missing.' }
if ((Get-Item -LiteralPath $scriptDirectory -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Bundle scripts directory cannot be a reparse point.' }
$actualScriptNames = @(Get-ChildItem -LiteralPath $scriptDirectory -Force | ForEach-Object Name | Sort-Object)
if (($actualScriptNames -join '|') -ne (@($approvedScripts | Sort-Object) -join '|')) { throw 'Bundle scripts directory contains missing or unexpected files.' }
$bundleWinSW = Join-Path $bundleRoot 'WinSW.NET461.exe'
$winsw = Assert-WinSW $bundleWinSW $ExpectedWinSWSha256
if ($manifest.winswProductVersion -ne $winsw.Version) { throw 'WinSW version does not match the bundle manifest.' }
$template = Join-Path $bundleRoot 'EndpointPostureWorker.xml'
$bootstrap = Join-Path $bundleRoot 'Initialize-PostureWorkerCredentials.ps1'
$xmlCheck = [Xml.XmlDocument]::new(); $xmlCheck.Load($template)
if (-not $xmlCheck.SelectSingleNode('/service/env[@name="EP_BACKEND_URL"]') -or
    -not $xmlCheck.SelectSingleNode('/service/logpath')) { throw 'Worker WinSW XML is missing required configuration nodes.' }
if (-not $PSCmdlet.ShouldProcess($InstallDirectory, 'Stage verified worker bundle and create empty persistent data/log directories')) { return }

$java = Get-Command java.exe -ErrorAction Stop
$javaPath = [IO.Path]::GetFullPath($java.Source)
$javaVersion = (& $javaPath -version 2>&1 | Select-Object -First 1)
if ($javaVersion -notmatch '"21(?:\.|\")') { throw "Java 21 is required on the target host; found '$javaVersion'." }
$powershell = Get-Command powershell.exe -ErrorAction Stop
$powershellPath = [IO.Path]::GetFullPath($powershell.Source)
New-Item -ItemType Directory -Path $InstallDirectory -ErrorAction Stop | Out-Null
try {
    New-Item -ItemType Directory -Path $DataDirectory -ErrorAction Stop | Out-Null
    New-Item -ItemType Directory -Path $LogDirectory -ErrorAction Stop | Out-Null
    Copy-Item -LiteralPath (Join-Path $bundleRoot 'worker-runtime.jar') -Destination (Join-Path $InstallDirectory 'worker-runtime.jar')
    New-Item -ItemType Directory -Path (Join-Path $InstallDirectory 'scripts') | Out-Null
    foreach ($scriptName in $approvedScripts) { Copy-Item -LiteralPath (Join-Path $bundleRoot "scripts\$scriptName") -Destination (Join-Path $InstallDirectory "scripts\$scriptName") }
    Copy-Item -LiteralPath $bootstrap -Destination (Join-Path $InstallDirectory 'Initialize-PostureWorkerCredentials.ps1')
    Copy-Item -LiteralPath $bundleWinSW -Destination (Join-Path $InstallDirectory 'EndpointPostureWorker.exe')
    $xml = [Xml.XmlDocument]::new(); $xml.PreserveWhitespace = $true; $xml.Load($template)
    $xml.SelectSingleNode('/service/executable').InnerText = $javaPath
    $xml.SelectSingleNode('/service/env[@name="EP_BACKEND_URL"]').SetAttribute('value',$uri.GetLeftPart([UriPartial]::Authority))
    $xml.SelectSingleNode('/service/env[@name="EP_WORKER_STATE_FILE"]').SetAttribute('value',(Join-Path $DataDirectory 'state.json'))
    $xml.SelectSingleNode('/service/env[@name="EP_WORKER_DATA_DIRECTORY"]').SetAttribute('value',$DataDirectory)
    $xml.SelectSingleNode('/service/env[@name="EP_SCRIPTS_DIRECTORY"]').SetAttribute('value',(Join-Path $InstallDirectory 'scripts'))
    $xml.SelectSingleNode('/service/env[@name="EP_POWERSHELL_EXE"]').SetAttribute('value',$powershellPath)
    $xml.SelectSingleNode('/service/workingdirectory').InnerText = $InstallDirectory
    $xml.SelectSingleNode('/service/logpath').InnerText = $LogDirectory
    if ($xml.OuterXml -match '__[A-Z0-9_]+__' -or $xml.SelectSingleNode('/service/env[@name="EP_POWERSHELL_EXECUTION_ENABLED"]').GetAttribute('value') -ne 'false') {
        throw 'Staged WinSW XML contains unresolved placeholders or enables PowerShell execution.'
    }
    $xml.Save((Join-Path $InstallDirectory 'EndpointPostureWorker.xml'))
    Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $InstallDirectory $manifestName)
} catch {
    # Roll back only newly created, empty deployment paths; preserve evidence if anything appeared.
    foreach ($path in @($InstallDirectory,$DataDirectory)) {
        if (Test-Path -LiteralPath $path) {
            $children = @(Get-ChildItem -LiteralPath $path -Force -ErrorAction SilentlyContinue)
            if (-not $children.Count) { Remove-Item -LiteralPath $path -Force -ErrorAction SilentlyContinue }
        }
    }
    throw
}
Write-Host "Staged verified bundle at $InstallDirectory. No service, account, ACL, enrollment, or collection was started."
Write-Host "Next, review paths, provision the service identity, then apply ACLs with -WhatIf before the explicit ACL run."
