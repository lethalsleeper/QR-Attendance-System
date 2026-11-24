<#
  QR Attendance - Windows Firewall rules for the Mobile Hotspot setup.

  Lets phones connected to this laptop's Windows "Mobile hotspot" (192.168.137.x) reach
  the local Firebase emulators. Nothing else is opened:
    * only TCP ports 9099 (Auth), 8080 (Firestore) and 5001 (Functions)
    * only from devices on the hotspot network 192.168.137.0/24
  It also adds hotspot-only rules for node.exe and java.exe so Windows doesn't show
  "Allow access?" popups, and disables any old BLOCK rule for those two programs
  (a block rule always wins over an allow rule).

  Run through allow-hotspot-firewall.bat (needs administrator rights).
  Undo everything:  allow-hotspot-firewall.bat remove
#>
param([switch]$Remove)

$ErrorActionPreference = 'Stop'
$group = 'QR Attendance (local emulators)'
$hotspotSubnet = '192.168.137.0/24'
$ports = @('9099', '8080', '5001')

Write-Host 'Removing previous QR Attendance firewall rules (if any)...'
Get-NetFirewallRule -Group $group -ErrorAction SilentlyContinue | Remove-NetFirewallRule

if ($Remove) {
    Write-Host 'Done. All QR Attendance firewall rules were removed.'
    return
}

New-NetFirewallRule -DisplayName 'QR Attendance - emulator ports (hotspot only)' -Group $group `
    -Direction Inbound -Action Allow -Protocol TCP -LocalPort $ports `
    -RemoteAddress $hotspotSubnet -Profile Any | Out-Null
Write-Host "[OK] Allowed ports $($ports -join ', ') from the hotspot network $hotspotSubnet"

$programs = @()
$node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
if ($node) { $programs += $node } else { Write-Warning 'Node.js was not found. Install it, then run this again.' }

$javaSettings = cmd /c 'java -XshowSettings:properties -version 2>&1'
$javaHomeLine = $javaSettings | Where-Object { $_ -match '^\s*java\.home\s*=' } | Select-Object -First 1
if ($javaHomeLine) {
    $programs += Join-Path ($javaHomeLine -split '=', 2)[1].Trim() 'bin\java.exe'
} else {
    Write-Warning 'Java was not found. Install Java 11 or newer, then run this again.'
}

foreach ($program in $programs) {
    $name = Split-Path $program -Leaf
    New-NetFirewallRule -DisplayName "QR Attendance - $name (hotspot only)" -Group $group `
        -Direction Inbound -Action Allow -Program $program `
        -RemoteAddress $hotspotSubnet -Profile Any | Out-Null
    Write-Host "[OK] Allowed $program on the hotspot network only"

    Get-NetFirewallRule -Direction Inbound -Action Block -Enabled True -ErrorAction SilentlyContinue | ForEach-Object {
        $filter = $_ | Get-NetFirewallApplicationFilter
        if ($filter.Program -and ([Environment]::ExpandEnvironmentVariables($filter.Program) -ieq $program)) {
            Disable-NetFirewallRule -Name $_.Name
            Write-Host "[OK] Disabled an old blocking rule: $($_.DisplayName)"
        }
    }
}

Write-Host ''
Write-Host 'Done. Phones on your Mobile hotspot can now reach the QR Attendance backend.'
