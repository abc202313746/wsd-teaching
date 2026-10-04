param(
    [int]$Port = 18082,
    [string]$JavaExe = "java",
    [switch]$CaptureScreenshots
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot "build\libs\wsd-teaching-0.0.1-SNAPSHOT.jar"
$outputDir = Join-Path $projectRoot "docs\verification"
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "Build the application first: .\gradlew.bat test bootJar"
}
[void](New-Item -ItemType Directory -Path $outputDir -Force)
$stdoutPath = Join-Path $outputDir "server.log"
$stderrPath = Join-Path $outputDir "server.err.log"
$results = [System.Collections.Generic.List[object]]::new()
$baseUrl = "http://127.0.0.1:$Port"
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(10)
$server = $null

function Invoke-ApiCheck {
    param(
        [string]$Label, [string]$Group, [string]$Method,
        [string]$Path, [int]$ExpectedCode, [string]$Body, [hashtable]$Headers
    )
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method), $baseUrl + $Path
    )
    try {
        if ($Body) {
            $request.Content = [System.Net.Http.StringContent]::new(
                $Body, [System.Text.Encoding]::UTF8, "application/json"
            )
        }
        if ($Headers) {
            foreach ($headerName in $Headers.Keys) {
                [void]$request.Headers.TryAddWithoutValidation($headerName, [string]$Headers[$headerName])
            }
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if ([int]$response.StatusCode -ne $ExpectedCode) {
                throw "$Label expected HTTP $ExpectedCode, received $([int]$response.StatusCode): $responseText"
            }
            $parsed = $responseText | ConvertFrom-Json
            $expectedStatus = if ($ExpectedCode -lt 400) { "success" } else { "error" }
            if ($parsed.status -ne $expectedStatus -or
                $parsed.PSObject.Properties.Name -notcontains "data") {
                throw "$Label did not use the standard response envelope."
            }
            if ($ExpectedCode -eq 503 -and
                $response.Headers.GetValues("Retry-After") -notcontains "5") {
                throw "The 503 response did not include Retry-After: 5."
            }
            $results.Add([pscustomobject]@{
                label = $Label
                group = $Group
                method = $Method
                path = $Path
                httpStatus = [int]$response.StatusCode
                expectedStatus = $ExpectedCode
                passed = $true
                requestBody = $Body
                requestHeaders = $Headers
                response = $parsed
            })
            Write-Host "PASS $Method $Path -> HTTP $ExpectedCode"
            return $parsed
        } finally {
            $response.Dispose()
        }
    } finally {
        $request.Dispose()
    }
}

try {
    $launchOptions = @{
        FilePath = $JavaExe
        ArgumentList = @("-jar", ('"' + $jarPath + '"'), "--server.port=$Port", "--server.address=127.0.0.1")
        WorkingDirectory = $projectRoot
        WindowStyle = "Hidden"
        PassThru = $true
        RedirectStandardOutput = $stdoutPath
        RedirectStandardError = $stderrPath
    }
    $server = Start-Process @launchOptions
    $ready = $false
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($server.HasExited) {
            throw "The verification server exited. Check docs/verification/server.log and server.err.log."
        }
        if ((Get-Content -LiteralPath $stdoutPath -Raw -Encoding UTF8) -match "Tomcat started on port $Port ") {
            $ready = $true
            break
        }
        Start-Sleep -Milliseconds 200
    }
    if (-not $ready) { throw "The verification server did not become ready within 30 seconds." }

    $first = Invoke-ApiCheck "POST 1 - Create item" "API" POST "/api/v1/items" 201 '{"name":"Coffee","price":1000}'
    $second = Invoke-ApiCheck "POST 2 - Create with headers" "API" POST "/api/v3/items/with-header" 201 '{"name":"Tea","price":2000}' @{ "X-USER-ID" = "student01"; "Authorization" = "Bearer demo-token" }
    $third = Invoke-ApiCheck "Create fixture" "Setup" POST "/api/v1/items" 201 '{"name":"Milk","price":3000}'
    $firstId = $first.data.id
    $secondId = $second.data.item.id
    $thirdId = $third.data.id
    if ($firstId -eq $secondId -or $secondId -eq $thirdId) { throw "Generated IDs must differ." }
    if ($second.data.userId -ne "student01" -or -not $second.data.authorizationProvided) {
        throw "Header values were not handled correctly."
    }

    $list = Invoke-ApiCheck "GET 1 - Search and list" "API" GET "/api/v1/items?keyword=Coffee&page=0&size=10" 200
    if ($list.data.total -ne 1 -or $list.data.items[0].id -ne $firstId) { throw "Search results were incorrect." }
    $found = Invoke-ApiCheck "GET 2 - Read item" "API" GET "/api/v1/items/$secondId" 200
    if ($found.data.name -ne "Tea") { throw "The header-created item was not in the shared store." }

    $updated = Invoke-ApiCheck "PUT 1 - Replace item" "API" PUT "/api/v1/items/$firstId" 200 '{"name":"Americano","price":1800}'
    if ($updated.data.name -ne "Americano" -or $updated.data.price -ne 1800) { throw "Replacement failed." }
    $updated = Invoke-ApiCheck "PUT 2 - Replace price" "API" PUT "/api/v1/items/$firstId/price" 200 '{"price":2200}'
    if ($updated.data.name -ne "Americano" -or $updated.data.price -ne 2200) { throw "Price update failed." }
    $found = Invoke-ApiCheck "Update persists" "Validation" GET "/api/v1/items/$firstId" 200
    if ($found.data.price -ne 2200) { throw "The price update was not persisted." }

    [void](Invoke-ApiCheck "Failed batch is atomic" "Validation" DELETE "/api/v1/items?ids=$firstId,999999" 404)
    [void](Invoke-ApiCheck "Existing item survives failed batch" "Validation" GET "/api/v1/items/$firstId" 200)
    $deleted = Invoke-ApiCheck "DELETE 1 - Delete item" "API" DELETE "/api/v1/items/$secondId" 200
    if ($deleted.data.deletedCount -ne 1) { throw "Single deletion failed." }
    [void](Invoke-ApiCheck "Deleted item is missing" "Validation" GET "/api/v1/items/$secondId" 404)
    $deleted = Invoke-ApiCheck "DELETE 2 - Delete selected items" "API" DELETE "/api/v1/items?ids=$firstId,$thirdId" 200
    if ($deleted.data.deletedCount -ne 2) { throw "Batch deletion failed." }

    [void](Invoke-ApiCheck "400 - Invalid input" "Errors" POST "/api/v1/items" 400 '{"name":" ","price":100}')
    [void](Invoke-ApiCheck "400 - Malformed JSON" "Validation" POST "/api/v1/items" 400 '{broken')
    [void](Invoke-ApiCheck "404 - Missing item" "Errors" GET "/api/v1/items/999999" 404)
    [void](Invoke-ApiCheck "500 - Demo internal error" "Errors" GET "/api/v1/items" 500 "" @{ "X-Demo-Error" = "500" })
    [void](Invoke-ApiCheck "503 - Demo unavailable" "Errors" GET "/api/v1/items" 503 "" @{ "X-Demo-Error" = "503" })
    [void](Invoke-ApiCheck "404 - Missing route" "Validation" GET "/api/no-such-path" 404)
    [void](Invoke-ApiCheck "405 - Unsupported method" "Validation" POST "/api/v1/items/1" 405)
    [void](Invoke-ApiCheck "400 - Missing IDs" "Validation" DELETE "/api/v1/items" 400)
    $final = Invoke-ApiCheck "Final store is empty" "Validation" GET "/api/v1/items" 200
    if ($final.data.total -ne 0) { throw "The final store should be empty." }

    Start-Sleep -Milliseconds 200
    $serverLog = Get-Content -LiteralPath $stdoutPath -Raw -Encoding UTF8
    foreach ($code in @(200, 201, 400, 404, 500, 503)) {
        if ($serverLog -notmatch "status=$code") { throw "Middleware did not log HTTP $code." }
    }
    $verification = [pscustomobject]@{
        verifiedAt = [DateTimeOffset]::Now.ToString("o")
        baseUrl = $baseUrl
        passed = $true
        checkCount = $results.Count
        apiCount = @($results | Where-Object group -eq "API").Count
        responseCodes = @($results.httpStatus | Sort-Object -Unique)
        middlewareVerified = $true
        results = $results
    }
    $verification | ConvertTo-Json -Depth 12 |
        Set-Content -LiteralPath (Join-Path $outputDir "results.json") -Encoding UTF8

    $style = @"
<style>
body{font:15px 'Segoe UI','Malgun Gothic',sans-serif;color:#172033;background:#eef2f7;margin:0;padding:28px}
h1{font-size:28px;margin:0 0 8px} .meta{color:#536174;margin:0 0 22px}
.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.card{background:white;border:1px solid #dce3ed;border-radius:10px;padding:16px;break-inside:avoid}
h2{font-size:17px;margin:0 0 10px}.route{font:13px Consolas,monospace;overflow-wrap:anywhere}
.code{display:inline-block;background:#d9f3e5;color:#126c44;border-radius:6px;padding:5px 9px;font-weight:700}
pre{font:13px/1.5 Consolas,'Malgun Gothic',monospace;white-space:pre-wrap;overflow-wrap:anywhere;background:#f7f9fc;padding:12px;margin-bottom:0}
</style>
"@
    function Convert-ResultToCard($entry) {
        $label = [System.Net.WebUtility]::HtmlEncode($entry.label)
        $route = [System.Net.WebUtility]::HtmlEncode($entry.method + " " + $entry.path)
        $body = [System.Net.WebUtility]::HtmlEncode(($entry.response | ConvertTo-Json -Depth 8))
        return "<article class='card'><h2>$label</h2><div class='route'>$route</div><p><span class='code'>PASS - HTTP $($entry.httpStatus)</span></p><pre>$body</pre></article>"
    }
    $apiCards = ($results | Where-Object group -eq "API" | ForEach-Object { Convert-ResultToCard $_ }) -join ""
    $statusCards = (@(200, 201, 400, 404, 500, 503) | ForEach-Object {
        $code = $_
        $entry = $results | Where-Object httpStatus -eq $code | Select-Object -First 1
        Convert-ResultToCard $entry
    }) -join ""
    $logLines = ($serverLog -split "\r?\n" | Where-Object { $_ -match "LoggingInterceptor" }) -join [Environment]::NewLine
    $encodedLog = [System.Net.WebUtility]::HtmlEncode($logLines)
    $meta = "Actual local HTTP requests - $($verification.verifiedAt) - $($verification.checkCount) checks passed"
    foreach ($report in @(
        @{ Name = "apis"; Title = "8 APIs - GET / POST / PUT / DELETE"; Content = "<div class='grid'>$apiCards</div>" },
        @{ Name = "response-codes"; Title = "HTTP responses - 200 / 201 / 400 / 404 / 500 / 503"; Content = "<div class='grid'>$statusCards</div>" },
        @{ Name = "middleware"; Title = "Request logging middleware"; Content = "<article class='card'><pre>$encodedLog</pre></article>" }
    )) {
        $html = "<!doctype html><html lang='ko'><meta charset='utf-8'><title>$($report.Title)</title>$style<body><h1>$($report.Title)</h1><p class='meta'>$meta</p>$($report.Content)</body></html>"
        Set-Content -LiteralPath (Join-Path $outputDir ($report.Name + ".html")) -Value $html -Encoding UTF8
    }
    Write-Host "PASS: $($verification.checkCount) HTTP checks, exactly 8 APIs, six required response codes, and middleware logs."
} finally {
    $client.Dispose()
    if ($null -ne $server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -ErrorAction Stop
    }
}

if ($CaptureScreenshots) {
    $browserExe = @(
        "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Google\Chrome\Application\chrome.exe"
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $browserExe) { throw "Edge or Chrome is required for screenshots; HTML reports have already been saved." }
    $profileDir = Join-Path $projectRoot "build\verification-browser"
    foreach ($name in @("apis", "response-codes", "middleware")) {
        $htmlPath = Join-Path $outputDir ($name + ".html")
        $pngPath = Join-Path $outputDir ($name + ".png")
        $fileUrl = [Uri]::new($htmlPath).AbsoluteUri
        $browserOptions = @{
            FilePath = $browserExe
            ArgumentList = @(
                "--headless", "--disable-gpu", "--no-first-run", "--disable-extensions",
                ('--user-data-dir="' + $profileDir + '"'),
                ('--screenshot="' + $pngPath + '"'),
                "--window-size=1280,2200", "--hide-scrollbars", "--virtual-time-budget=1000",
                $fileUrl
            )
            WindowStyle = "Hidden"
            PassThru = $true
        }
        $browser = Start-Process @browserOptions
        if (-not $browser.WaitForExit(20000)) {
            Stop-Process -Id $browser.Id
            throw "Screenshot rendering timed out for $name."
        }
        if (-not (Test-Path -LiteralPath $pngPath)) { throw "Screenshot was not produced for $name." }
        Write-Host "Saved docs/verification/$name.png"
    }
}
