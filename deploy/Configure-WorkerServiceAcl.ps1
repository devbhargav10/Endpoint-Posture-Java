<#
.SYNOPSIS
Previews or applies least-privilege ACLs to the dedicated worker install and data trees.
.DESCRIPTION
This script replaces ACLs recursively and is intentionally limited to the canonical
EndpointPostureWorker directories directly beneath Program Files and ProgramData.
Always run once with -WhatIf and review every listed path before an explicit apply.
#>
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory = $true)][string]$ServiceAccount,
    [string]$InstallDirectory = "$env:ProgramFiles\EndpointPostureWorker",
    [string]$DataDirectory = "$env:ProgramData\EndpointPostureWorker",
    [string]$LogDirectory = "$env:ProgramData\EndpointPostureWorker\logs",
    [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:ProgramFiles) -or [string]::IsNullOrWhiteSpace($env:ProgramData)) { throw 'Program Files and ProgramData locations must be defined.' }
if ($ServiceAccount -match '(^|\\)svc-posture$') { throw 'The backend svc-posture account is not the worker identity.' }

function Get-CanonicalExistingDirectory([string]$Value, [string]$Label) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw "$Label path is empty." }
    if ($Value.StartsWith('\\') -or $Value.StartsWith('\\?\') -or $Value.StartsWith('\\.\')) { throw "$Label path must be a local filesystem path, not UNC/device syntax." }
    $full = [IO.Path]::GetFullPath($Value).TrimEnd('\')
    $volume = [IO.Path]::GetPathRoot($full).TrimEnd('\')
    if (-not $full -or $full -eq $volume) { throw "Refusing $Label volume root." }
    if (-not (Test-Path -LiteralPath $full -PathType Container)) { throw "$Label directory must exist before ACL review: $full" }
    $resolved = (Resolve-Path -LiteralPath $full -ErrorAction Stop).ProviderPath.TrimEnd('\')
    if (-not $resolved.Equals($full,[StringComparison]::OrdinalIgnoreCase)) { throw "$Label path is not canonical or resolves through a path alias: $full" }
    return $full
}

$install = Get-CanonicalExistingDirectory $InstallDirectory 'Install'
$data = Get-CanonicalExistingDirectory $DataDirectory 'Data'
$logs = Get-CanonicalExistingDirectory $LogDirectory 'Log'
$expectedInstall = [IO.Path]::GetFullPath((Join-Path $env:ProgramFiles 'EndpointPostureWorker')).TrimEnd('\')
$expectedData = [IO.Path]::GetFullPath((Join-Path $env:ProgramData 'EndpointPostureWorker')).TrimEnd('\')
$expectedLogs = [IO.Path]::GetFullPath((Join-Path $expectedData 'logs')).TrimEnd('\')
if (-not $install.Equals($expectedInstall,[StringComparison]::OrdinalIgnoreCase)) { throw "Install path must be exactly $expectedInstall." }
if (-not $data.Equals($expectedData,[StringComparison]::OrdinalIgnoreCase)) { throw "Data path must be exactly $expectedData." }
if (-not $logs.Equals($expectedLogs,[StringComparison]::OrdinalIgnoreCase)) { throw "Log path must be exactly $expectedLogs." }
if ($install.StartsWith($data + '\',[StringComparison]::OrdinalIgnoreCase) -or $data.StartsWith($install + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Install and data trees must be disjoint.' }

$paths = @(
    @{ Path = $install; Kind = 'Install (worker receives ReadAndExecute)' },
    @{ Path = $data; Kind = 'Data (worker receives Modify)' },
    @{ Path = $logs; Kind = 'Logs (worker receives Modify)' }
)
foreach ($entry in $paths) {
    $children = @(Get-ChildItem -LiteralPath $entry.Path -Force -Recurse -ErrorAction Stop)
    if ($entry.Path -eq $data) { $children = @($children | Where-Object { $_.FullName -ne $logs -and -not $_.FullName.StartsWith($logs + '\',[StringComparison]::OrdinalIgnoreCase) }) }
    $items = @((Get-Item -LiteralPath $entry.Path -Force)) + $children
    $reparse = @($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
    if ($reparse.Count) { throw "Reparse point found under $($entry.Path): $($reparse[0].FullName). No ACLs were changed." }
}
if ($ValidateOnly) {
    Write-Host "Validated canonical worker ACL paths only: install=$install; data=$data; logs=$logs. No ACLs were changed."
    return
}
$principal = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $WhatIfPreference -and -not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run ACL provisioning from an elevated PowerShell session.' }

$accountSid = ([Security.Principal.NTAccount]$ServiceAccount).Translate([Security.Principal.SecurityIdentifier])
$systemSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-18')
$adminsSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
foreach ($entry in $paths) {
    $children = @(Get-ChildItem -LiteralPath $entry.Path -Force -Recurse -ErrorAction Stop)
    if ($entry.Path -eq $data) { $children = @($children | Where-Object { $_.FullName -ne $logs -and -not $_.FullName.StartsWith($logs + '\',[StringComparison]::OrdinalIgnoreCase) }) }
    $items = @((Get-Item -LiteralPath $entry.Path -Force)) + $children
    foreach ($item in $items) {
        if (-not $PSCmdlet.ShouldProcess($item.FullName, "Replace ACL for $($entry.Kind); retain only worker, SYSTEM and Administrators")) { continue }
        $workerRights = if ($entry.Path -eq $install) { [Security.AccessControl.FileSystemRights]::ReadAndExecute } else { [Security.AccessControl.FileSystemRights]::Modify }
        if ($item.PSIsContainer) {
            $acl = [Security.AccessControl.DirectorySecurity]::new()
            $acl.SetAccessRuleProtection($true, $false)
            $inherit = [Security.AccessControl.InheritanceFlags]'ContainerInherit, ObjectInherit'
            $propagation = [Security.AccessControl.PropagationFlags]::None
            $allow = [Security.AccessControl.AccessControlType]::Allow
            [void]$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($systemSid,[Security.AccessControl.FileSystemRights]::FullControl,$inherit,$propagation,$allow))
            [void]$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($adminsSid,[Security.AccessControl.FileSystemRights]::FullControl,$inherit,$propagation,$allow))
            [void]$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($accountSid,$workerRights,$inherit,$propagation,$allow))
        } else {
            $acl = [Security.AccessControl.FileSecurity]::new()
            $acl.SetAccessRuleProtection($true, $false)
            foreach ($sid in @($systemSid,$adminsSid,$accountSid)) {
                $rights = if ($sid -eq $accountSid) { $workerRights } else { [Security.AccessControl.FileSystemRights]::FullControl }
                [void]$acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($sid,$rights,[Security.AccessControl.AccessControlType]::Allow))
            }
        }
        Set-Acl -LiteralPath $item.FullName -AclObject $acl
    }
}
if ($WhatIfPreference) { Write-Host 'ACL preview complete. No ACLs were changed.' }
else { Write-Host "ACL operation complete for $ServiceAccount. Verify with icacls; install=ReadAndExecute, data/logs=Modify." }
