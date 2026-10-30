$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskState = Join-Path $taskRoot 'target/demo-processes.json'
if (!(Test-Path $taskState)) { Write-Output 'No demo process record found.'; return }
$taskUnresolved = @()
foreach($taskApp in @(Get-Content -LiteralPath $taskState -Raw | ConvertFrom-Json)) {
    $taskProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($taskApp.id)" -ErrorAction SilentlyContinue
    if(!$taskProcess) { continue }
    if($taskProcess.Name -match '^java(w)?\.exe$' -and $taskProcess.CommandLine -and $taskProcess.CommandLine.Contains($taskApp.jar)) {
        Stop-Process -Id $taskApp.id
        Write-Output "Stopped $($taskApp.name)"
    } else {
        $taskUnresolved += $taskApp
        Write-Warning "Process identity could not be confirmed for $($taskApp.name); left untouched."
    }
}
if($taskUnresolved.Count -gt 0) { $taskUnresolved | ConvertTo-Json -Depth 4 | Set-Content $taskState }
else { Remove-Item -LiteralPath $taskState }
