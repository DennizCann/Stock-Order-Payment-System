$ErrorActionPreference = 'Stop'
function Assert-Demo($Condition, [string]$Message) { if(!$Condition) { throw $Message } }
$taskBase = 'http://127.0.0.1:8080/api/v1'
$taskLogin = Invoke-RestMethod "$taskBase/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$taskHeaders = @{Authorization="Bearer $($taskLogin.accessToken)"}
$taskSku = 'DEMO-' + [guid]::NewGuid().ToString('N').Substring(0,12)
$taskProduct = Invoke-RestMethod "$taskBase/products" -Method Post -Headers $taskHeaders -ContentType 'application/json' -Body (@{name='Demo Coffee Machine';sku=$taskSku;price=2500} | ConvertTo-Json)
Invoke-RestMethod "$taskBase/products/$($taskProduct.id)/stock" -Method Post -Headers $taskHeaders -ContentType 'application/json' -Body '{"quantity":10}' | Out-Null
$taskSync = Invoke-RestMethod "$taskBase/operations/channels/sync-stock" -Method Post -Headers $taskHeaders
Assert-Demo (($taskSync | Where-Object { !$_.success }).Count -eq 0) 'All five simulators must be running.'
$taskChannelOrders = @()
foreach($taskPort in 8081..8085) {
    $taskOrder = Invoke-RestMethod "http://127.0.0.1:$taskPort/api/orders" -Method Post -ContentType 'application/json' -Body (@{items=@(@{sku=$taskSku;quantity=1})} | ConvertTo-Json -Depth 4)
    $taskChannelOrders += $taskOrder
}
$taskImported = Invoke-RestMethod "$taskBase/operations/channels/import" -Method Post -Headers $taskHeaders
Assert-Demo (($taskImported | Where-Object { !$_.success }).Count -eq 0) 'An import or acknowledgement failed.'
$taskStock = Invoke-RestMethod "$taskBase/products/$($taskProduct.id)/stock" -Headers $taskHeaders
Assert-Demo ($taskStock.quantity -eq 10 -and $taskStock.reservedQuantity -eq 5 -and $taskStock.availableQuantity -eq 5) 'Expected five reservations against ten physical units.'
Invoke-RestMethod "$taskBase/operations/channels/sync-stock" -Method Post -Headers $taskHeaders | Out-Null
foreach($taskPort in 8081..8085) {
    $taskInventory = Invoke-RestMethod "http://127.0.0.1:$taskPort/api/inventory"
    Assert-Demo (($taskInventory | Where-Object sku -eq $taskSku).availableQuantity -eq 5) "Stock differs on channel port $taskPort"
}
$taskRepeated = Invoke-RestMethod "$taskBase/operations/channels/import" -Method Post -Headers $taskHeaders
Assert-Demo (($taskRepeated | Measure-Object imported -Sum).Sum -eq 0) 'Acknowledged orders should not be imported again.'
$taskReplay = @{externalOrderId=$taskChannelOrders[0].externalOrderId;items=@(@{sku=$taskSku;quantity=1})} | ConvertTo-Json -Depth 4
Invoke-RestMethod "$taskBase/operations/channels/TRENDYOL/orders" -Method Post -Headers $taskHeaders -ContentType 'application/json' -Body $taskReplay | Out-Null
$taskFinalStock = Invoke-RestMethod "$taskBase/products/$($taskProduct.id)/stock" -Headers $taskHeaders
Assert-Demo ($taskFinalStock.reservedQuantity -eq 5) 'A replay reserved stock twice.'
[pscustomobject]@{result='PASS';sku=$taskSku;channels=5;physical=10;reserved=5;available=5;duplicateDelivery='No second reservation'}
