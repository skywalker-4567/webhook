param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$WebhookSecret = "test_webhook_secret"
)
$ErrorActionPreference = "Stop"
$passed = 0; $failed = 0
function Pass($msg) { Write-Host "  OK $msg" -ForegroundColor Green; $script:passed++ }
function Fail($msg) { Write-Host "  FAIL $msg" -ForegroundColor Red; $script:failed++ }
function Section($t) { Write-Host ""; Write-Host "--- $t ---" -ForegroundColor Cyan }
function GetBody($_) { try { $r = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); return $r.ReadToEnd() } catch { return $_.ToString() } }
function Send-Webhook($url, $payloadBytes, $sig) {
    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = "POST"; $req.ContentType = "application/json"
    $req.Headers.Add("X-Razorpay-Signature", $sig)
    $req.ContentLength = $payloadBytes.Length
    $stream = $req.GetRequestStream(); $stream.Write($payloadBytes, 0, $payloadBytes.Length); $stream.Close()
    try {
        $resp = $req.GetResponse()
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        return $reader.ReadToEnd() | ConvertFrom-Json
    } catch [System.Net.WebException] {
        $code = [int]$_.Exception.Response.StatusCode
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        throw "HTTP $code : $($reader.ReadToEnd())"
    }
}

Section "00 - Health"
try { $h = Invoke-RestMethod "$BaseUrl/actuator/health"; if ($h.status -eq "UP") { Pass "Health UP" } else { Fail "Health $($h.status)" } } catch { Fail "Health: $_" }

Section "00 - Auth"
$TOKEN = $null
try { $login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'; $TOKEN = $login.token; if ($TOKEN) { Pass "Login OK" } else { Fail "No token" } } catch { Fail "Login: $_" }
if (-not $TOKEN) { Write-Host "Cannot continue" -ForegroundColor Red; exit 1 }
$H = @{ "Authorization" = "Bearer $TOKEN" }
try { Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" -Body '{"username":"admin","password":"wrong"}'; Fail "Bad creds should 401" } catch { $c = $_.Exception.Response.StatusCode.value__; if ($c -eq 401) { Pass "Bad creds=401" } else { Fail "Bad creds=$c" } }

Section "01 - Webhook"
$ts = Get-Date -Format "yyyyMMddHHmmss"
$PAYMENT_ID = "pay_ps_$ts"
$EVENT_ID = "evt_ps_$ts"
$EPOCH = [int](Get-Date -UFormat %s)
$jsonStr = '{"id":"' + $EVENT_ID + '","event":"payment.captured","created_at":' + $EPOCH + ',"payload":{"payment":{"entity":{"id":"' + $PAYMENT_ID + '","amount":150000,"currency":"INR","status":"captured","method":"card","order_id":null,"email":"test@example.com","contact":"+919999999999","error_description":null}}}}'
$payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($jsonStr)
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($WebhookSecret)
$SIG = ($hmac.ComputeHash($payloadBytes) | ForEach-Object { "{0:x2}" -f $_ }) -join ""
try { $wh = Send-Webhook "$BaseUrl/webhooks/razorpay" $payloadBytes $SIG; if ($wh.status -eq "received") { Pass "Webhook accepted" } else { Fail "Unexpected: $($wh.status)" } } catch { Fail "Webhook: $_" }
try { $wh2 = Send-Webhook "$BaseUrl/webhooks/razorpay" $payloadBytes $SIG; if ($wh2.status -eq "received") { Pass "Idempotency OK" } else { Fail "Idempotency: $($wh2.status)" } } catch { Fail "Idempotency: $_" }
try { Send-Webhook "$BaseUrl/webhooks/razorpay" $payloadBytes "badsig"; Fail "Bad sig should 400" } catch { if ($_ -like "HTTP 400*") { Pass "Bad sig=400" } else { Fail "Bad sig: $_" } }
Write-Host "  Waiting 4s..." -ForegroundColor Yellow; Start-Sleep 4

Section "02 - Payment"
try { $p = Invoke-RestMethod "$BaseUrl/payments/$PAYMENT_ID" -Headers $H; if ($p.paymentId -eq $PAYMENT_ID) { Pass "Found" } else { Fail "Wrong paymentId" }; if ($p.amount -eq 150000) { Pass "Amount=150000" } else { Fail "Amount=$($p.amount)" }; if ($p.status -eq "CAPTURED") { Pass "CAPTURED" } else { Fail "Status=$($p.status)" }; if ($p.currency -eq "INR") { Pass "INR" } else { Fail "Currency=$($p.currency)" }; if ($p.correlationId) { Pass "correlationId OK" } else { Fail "correlationId missing" } } catch { Fail "Payment: $_" }
try { $ps = Invoke-RestMethod "$BaseUrl/payments?status=CAPTURED&page=0&size=5" -Headers $H; if ($ps.items.Count -gt 0) { Pass "List: $($ps.items.Count)" } else { Fail "No CAPTURED" } } catch { Fail "Payment list: $_" }

Section "03 - Fraud"
try { $fraud = Invoke-RestMethod "$BaseUrl/fraud-checks?paymentId=$PAYMENT_ID" -Headers $H; Pass "Fraud: $($fraud.Count)"; if ($fraud.Count -gt 0) { if ($fraud[0].isFraud) { Pass "isFraud=true" } else { Fail "isFraud=false" }; if ($fraud[0].triggeredRules -contains "HIGH_AMOUNT") { Pass "HIGH_AMOUNT" } else { Fail "HIGH_AMOUNT missing" } } else { Fail "No fraud check" } } catch { Fail "Fraud: $_" }

Section "04 - Ledger"
try {
    $ledger = Invoke-RestMethod "$BaseUrl/ledger?paymentId=$PAYMENT_ID&size=10" -Headers $H
    $n = $ledger.entries.Count
    if ($n -eq 2) { Pass "2 entries" } else { Fail "Got $n entries" }
    $d = @($ledger.entries | Where-Object { $_.entryType -eq "DEBIT" }).Count
    $c2 = @($ledger.entries | Where-Object { $_.entryType -eq "CREDIT" }).Count
    if ($d -eq 1) { Pass "1 DEBIT" } else { Fail "DEBIT=$d" }
    if ($c2 -eq 1) { Pass "1 CREDIT" } else { Fail "CREDIT=$c2" }
    if ($ledger.totalDebit -eq $ledger.totalCredit) { Pass "Balanced: $($ledger.totalDebit)" } else { Fail "Imbalanced" }
    if ($n -gt 0) {
        $ref = $ledger.entries[0].transactionRef
        if ($ref -match "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$") { Pass "transactionRef UUID" } else { Fail "transactionRef: $ref" }
        $txIds = @($ledger.entries | Select-Object -ExpandProperty transactionId -Unique)
        if ($txIds.Count -eq 1) { Pass "Shared transactionId" } else { Fail "Different transactionIds" }
    }
} catch { Fail "Ledger: $_" }

Section "05 - Audit"
try { $audit = Invoke-RestMethod "$BaseUrl/audit/logs?entityId=$PAYMENT_ID&entityType=PAYMENT&page=0&size=20" -Headers $H; if ($audit.totalElements -gt 0) { Pass "Audit: $($audit.totalElements)" } else { Fail "No audit logs" }; $pend = @($audit.items | Where-Object { $_.currentHash -eq "PENDING" }).Count; if ($pend -eq 0) { Pass "No PENDING" } else { Fail "$pend PENDING" } } catch { Fail "Audit logs: $_" }
try { $v = Invoke-RestMethod "$BaseUrl/audit/verify?entityType=PAYMENT&entityId=$PAYMENT_ID" -Headers $H; if ($v.result -eq "VALID") { Pass "Audit VALID" } else { Fail "Audit BROKEN: $($v.message)" } } catch { Fail "Audit verify: $_" }

Section "06 - Reconciliation"
try { $recon = Invoke-RestMethod "$BaseUrl/reconciliation?page=0&size=20" -Headers $H; if ($null -ne $recon.items) { Pass "Recon: $($recon.totalElements)" } else { Fail "No items" } } catch { Fail "Recon: $_" }

Section "07 - Settlement"
$now = Get-Date; $from = $now.AddDays(-30).ToString("yyyy-MM-ddTHH:mm:ssZ"); $to = $now.ToString("yyyy-MM-ddTHH:mm:ssZ")
$fEnc = [uri]::EscapeDataString($from); $tEnc = [uri]::EscapeDataString($to)
try { $sum = Invoke-RestMethod "$BaseUrl/settlement/summary?from=$fEnc&to=$tEnc" -Headers $H; if ($null -ne $sum.netAmount) { Pass "Summary netAmount=$($sum.netAmount)" } else { Fail "netAmount missing" }; if ($sum.currency -eq "INR") { Pass "INR" } else { Fail "Currency=$($sum.currency)" } } catch { Fail "Summary: $(GetBody $_)" }
try { $csv = Invoke-WebRequest -Uri "$BaseUrl/settlement/report?from=$fEnc&to=$tEnc" -Headers $H -UseBasicParsing; if ($csv.StatusCode -eq 200) { Pass "CSV OK" } else { Fail "CSV=$($csv.StatusCode)" }; if ($csv.Headers["Content-Type"] -like "*text/csv*") { Pass "text/csv" } else { Fail "CT=$($csv.Headers['Content-Type'])" } } catch { Fail "CSV: $(GetBody $_)" }

Section "08 - Order"
$ikey = "idem-$(Get-Date -Format 'yyyyMMddHHmmss')"
$oBody = '{"customerId":"cust_001","amount":50000,"currency":"INR","description":"Test"}'
$firstOrderId = $null
try { $o1 = Invoke-RestMethod "$BaseUrl/orders" -Method POST -ContentType "application/json" -Headers @{ "Authorization" = "Bearer $TOKEN"; "X-Idempotency-Key" = $ikey } -Body $oBody; $firstOrderId = $o1.orderId; Pass "Order: $firstOrderId" } catch { $c = $_.Exception.Response.StatusCode.value__; if ($c -eq 502 -or $c -eq 500) { Write-Host "  Razorpay N/A ($c)" -ForegroundColor Yellow } else { Fail "Order ($c): $(GetBody $_)" } }
if ($firstOrderId) { try { $o2 = Invoke-RestMethod "$BaseUrl/orders" -Method POST -ContentType "application/json" -Headers @{ "Authorization" = "Bearer $TOKEN"; "X-Idempotency-Key" = $ikey } -Body $oBody; if ($o2.orderId -eq $firstOrderId) { Pass "Idempotency OK" } else { Fail "Different orderId" } } catch { Fail "Order repeat: $_" } }

Section "09 - JWT"
try { Invoke-RestMethod "$BaseUrl/payments"; Fail "No token should 401/403" } catch { $c = $_.Exception.Response.StatusCode.value__; if ($c -eq 401 -or $c -eq 403) { Pass "No token=$c" } else { Fail "Expected 401/403 got $c" } }
try { Send-Webhook "$BaseUrl/webhooks/razorpay" ([System.Text.Encoding]::UTF8.GetBytes("{}")) "x"; Fail "Bad sig should 400" } catch { if ($_ -like "HTTP 400*") { Pass "Webhook public" } else { Fail "Webhook: $_" } }
try { $h2 = Invoke-RestMethod "$BaseUrl/actuator/health"; if ($h2.status -eq "UP") { Pass "Health public" } } catch { Fail "Health: $_" }

Section "10 - Stats"
try { $stats = Invoke-RestMethod "$BaseUrl/webhooks/stats" -Headers $H; Pass "Stats: total=$($stats.total) processed=$($stats.processed)"; if ($stats.processed -gt 0) { Pass "Processed>0" } else { Fail "0 processed" } } catch { Fail "Stats: $_" }

Write-Host ""
Write-Host "===============================" -ForegroundColor Cyan
Write-Host "  Passed: $passed" -ForegroundColor Green
if ($failed -gt 0) { Write-Host "  Failed: $failed" -ForegroundColor Red } else { Write-Host "  Failed: 0" -ForegroundColor Green }
Write-Host "===============================" -ForegroundColor Cyan
if ($failed -gt 0) { exit 1 } else { Write-Host "All passed!" -ForegroundColor Green }