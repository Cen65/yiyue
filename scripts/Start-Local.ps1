param(
    [string]$PostgresBin = 'D:\software\PostgreSQL\17\bin',
    [string]$RedisServer = 'D:\software\redis\redis-server.exe',
    [int]$PostgresPort = 55433,
    [int]$RedisPort = 56379,
    [int]$ServerPort = 18080,
    [int]$WebPort = 15173
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$local = Join-Path $root '.local'
New-Item -ItemType Directory -Path $local -Force | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $root '.env'))) { & (Join-Path $PSScriptRoot 'Setup-Env.ps1') }
Get-Content -LiteralPath (Join-Path $root '.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
}
foreach ($port in @($PostgresPort,$RedisPort,$ServerPort,$WebPort)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "端口 $port 已被使用。请先停止对应服务，或检查 .local/pids.json。" }
}
if (!(Test-Path -LiteralPath (Join-Path $PostgresBin 'initdb.exe'))) { throw '找不到 PostgreSQL，请用 -PostgresBin 指定 bin 目录。' }
if (!(Test-Path -LiteralPath $RedisServer)) { throw '找不到 Redis，请用 -RedisServer 指定可执行文件。' }
$data = Join-Path $local 'postgres'
if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    $pwfile = Join-Path $local 'pg-password.tmp'
    [IO.File]::WriteAllText($pwfile, $env:DB_PASSWORD)
    try {
        # 独立数据库目录和端口，不访问本机现有实例；本地也启用密码认证。
        & (Join-Path $PostgresBin 'initdb.exe') -D $data -U yiyue -A scram-sha-256 --pwfile=$pwfile --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 初始化失败' }
    } finally { Remove-Item -LiteralPath $pwfile -ErrorAction SilentlyContinue }
}
& (Join-Path $PostgresBin 'pg_ctl.exe') -D $data -l (Join-Path $local 'postgres.log') -o "-p $PostgresPort -h 127.0.0.1" -w start
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 启动失败' }
[IO.File]::WriteAllText((Join-Path $local 'pg-bin.txt'), $PostgresBin)
$env:PGPASSWORD = $env:DB_PASSWORD
$exists = & (Join-Path $PostgresBin 'psql.exe') -h 127.0.0.1 -p $PostgresPort -U yiyue -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='yiyue'"
if ($exists -ne '1') { & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $PostgresPort -U yiyue yiyue }
Remove-Item Env:PGPASSWORD
$redisConfig = Join-Path $local 'redis.conf'
$redisDir = $local.Replace('\','/')
[IO.File]::WriteAllLines($redisConfig, @('bind 127.0.0.1', "port $RedisPort", "requirepass $env:REDIS_PASSWORD", "dir `"$redisDir`"", 'appendonly yes'))
$redis = Start-Process -FilePath $RedisServer -ArgumentList ('"{0}"' -f $redisConfig) -WorkingDirectory $local -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'redis.log') -RedirectStandardError (Join-Path $local 'redis.err.log')
$processes = @(@{ id=$redis.Id; executable=$RedisServer })
$processes | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $local 'pids.json')
$env:DB_URL = "jdbc:postgresql://127.0.0.1:$PostgresPort/yiyue"
$env:SERVER_PORT = "$ServerPort"
$env:API_PROXY_TARGET = "http://127.0.0.1:$ServerPort"
$env:DB_USERNAME = 'yiyue'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = "$RedisPort"
$jar = Join-Path $root 'yiyue-server/yiyue-application/target/yiyue-server-0.3.0.jar'
if (!(Test-Path -LiteralPath $jar)) {
    Push-Location (Join-Path $root 'yiyue-server')
    try { & mvn.cmd -B -DskipTests package; if ($LASTEXITCODE -ne 0) { throw '后端构建失败' } } finally { Pop-Location }
}
$java = (Get-Command java.exe).Source
$backend = Start-Process -FilePath $java -ArgumentList ('-jar "{0}"' -f $jar) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'backend.log') -RedirectStandardError (Join-Path $local 'backend.err.log')
$processes += @{ id=$backend.Id; executable=$java }
$processes | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $local 'pids.json')
$ready = $false
for ($n=0; $n -lt 60; $n++) {
    $backend.Refresh(); if ($backend.HasExited) { throw '后端进程已退出，请查看 .local/backend.log' }
    try { $r=Invoke-WebRequest "http://127.0.0.1:$ServerPort/actuator/health" -TimeoutSec 2 -NoProxy; if ($r.StatusCode -eq 200) { $ready=$true; break } } catch { }
    Start-Sleep -Seconds 1
}
if (!$ready) { throw '后端启动失败，请查看 .local/backend.log 和 backend.err.log，并运行 Stop-Local.ps1。' }
$frontend = Join-Path $root 'yiyue-web'
if (!(Test-Path -LiteralPath (Join-Path $frontend 'node_modules/vite/bin/vite.js'))) {
    Push-Location $frontend
    try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' } } finally { Pop-Location }
}
$node = (Get-Command node.exe).Source
$web = Start-Process -FilePath $node -ArgumentList ('"{0}" --host 127.0.0.1 --port {1} --strictPort' -f (Join-Path $frontend 'node_modules/vite/bin/vite.js'),$WebPort) -WorkingDirectory $frontend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'frontend.log') -RedirectStandardError (Join-Path $local 'frontend.err.log')
$processes += @{ id=$web.Id; executable=$node }
$processes | ConvertTo-Json -AsArray | Set-Content -LiteralPath (Join-Path $local 'pids.json')
Write-Host "医约已启动：http://127.0.0.1:$WebPort；账号 patient / admin；密码见项目 .env。"
