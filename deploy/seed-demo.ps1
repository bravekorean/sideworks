$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $passwordLine = Get-Content -LiteralPath .env | Where-Object { $_ -match '^SIDEWORKS_DEMO_PASSWORD=' }
    $password = $passwordLine -replace '^SIDEWORKS_DEMO_PASSWORD=', ''
    if ($password -isnot [string] -or $password -cnotmatch '^[A-Za-z0-9]{16,64}$') {
        throw 'Set SIDEWORKS_DEMO_PASSWORD in .env to 16–64 letters or digits.'
    }

    $accounts = @('demo-writer', 'demo-approver', 'demo-delegate')
    $accountRows = foreach ($account in $accounts) {
        $record = $password | docker run --rm -i --entrypoint sh httpd:2.4-alpine -c 'tr -d "\r" | htpasswd -niBC 10 "$1"' sh $account
        $hash = [regex]::Match(($record -join "`n").Trim(), '^[-a-z]+:(\$2[aby]\$10\$[./A-Za-z0-9]{53})$')
        if ($LASTEXITCODE -ne 0 -or !$hash.Success) {
            throw "Could not hash the demo password for $account."
        }
        "('$account', '$($hash.Groups[1].Value)', '$account', '$account', 'USER', 'ACTIVE', NOW(6), NOW(6))"
    }

    $sql = @"
INSERT INTO usertbl (login_id, user_pass, user_name, employee_no, user_role, status, created_at, updated_at)
VALUES $($accountRows -join ",`n")
ON DUPLICATE KEY UPDATE user_id = usertbl.user_id;
"@
    $sql | docker compose exec -T db sh -c 'test "$MYSQL_DATABASE" = sideworks_demo && MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
    if ($LASTEXITCODE -ne 0) { throw 'Could not insert demo accounts.' }
    Write-Output "Demo accounts available: $($accounts -join ', ')"
}
finally {
    Pop-Location
}
