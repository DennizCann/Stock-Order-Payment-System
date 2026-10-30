param([string]$JavaCommand = 'java')
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskState = Join-Path $taskRoot 'target/demo-processes.json'
if (Test-Path $taskState) { throw 'A demo process record exists. Run scripts/Stop-Demo.ps1 first.' }
$taskApps = @(@{name='central';port=8080;jar='target/Stock-Order-Payment-System-1.0-SNAPSHOT.jar'})
$taskPort = 8081
foreach($taskName in @('trendyol','hepsiburada','n11','webstore','branch')) {
    $taskApps += @{name=$taskName;port=$taskPort;jar="simulators/$taskName/target/$taskName-simulator-1.0.0.jar"}
    $taskPort++
}
foreach($taskApp in $taskApps) {
    if (!(Test-Path (Join-Path $taskRoot $taskApp.jar))) { throw "Build is missing: $($taskApp.jar)" }
    if (Get-NetTCPConnection -LocalPort $taskApp.port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $($taskApp.port) is already occupied. Existing processes were not changed."
    }
}
$taskStarted = @()
try {
    foreach($taskApp in $taskApps) {
        $taskJar = (Resolve-Path (Join-Path $taskRoot $taskApp.jar)).Path
        $taskArgs = @('-jar', ('"' + $taskJar + '"'))
        if($taskApp.name -eq 'central') { $taskArgs += '--spring.profiles.active=demo' }
        $taskArgs += '--server.address=127.0.0.1'
        $taskProcess = Start-Process -FilePath $JavaCommand -ArgumentList $taskArgs -WorkingDirectory $taskRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskRoot "target/demo-$($taskApp.name).log") -RedirectStandardError (Join-Path $taskRoot "target/demo-$($taskApp.name)-error.log")
        $taskStarted += @{name=$taskApp.name;id=$taskProcess.Id;jar=$taskJar;port=$taskApp.port}
        $taskStarted | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $taskState
    }
    Write-Output 'Demo processes launched. Allow startup to finish; see target/demo-*.log for details.'
    Write-Output 'Central panel: http://127.0.0.1:8080 (admin / admin123)'
    Write-Output 'Channel panels: ports 8081 through 8085. Stop with scripts/Stop-Demo.ps1.'
} catch {
    Write-Warning 'Startup was interrupted. Stop-Demo.ps1 can stop the processes already recorded.'
    throw
}
