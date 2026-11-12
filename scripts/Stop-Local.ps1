$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$local = Join-Path $root '.local'
$pidFile = Join-Path $local 'pids.json'
if (Test-Path -LiteralPath $pidFile) {
    foreach ($entry in (Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json)) {
        $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        # 同时核对路径及项目启动参数，避免 PID 被其他进程复用后误终止。
        $command = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.id)" -ErrorAction SilentlyContinue
        if ($process -and $process.Path -eq $entry.executable -and $command.CommandLine -and
            $command.CommandLine.Contains($root)) { Stop-Process -Id $entry.id }
    }
}
$pgBinFile = Join-Path $local 'pg-bin.txt'
if (Test-Path -LiteralPath $pgBinFile) {
    $pgBin = (Get-Content -LiteralPath $pgBinFile -Raw).Trim()
    # pg_ctl 仅停止项目自己的数据目录，不操作系统 PostgreSQL 服务。
    & (Join-Path $pgBin 'pg_ctl.exe') -D (Join-Path $local 'postgres') -m fast -w stop
}
Write-Host '本地进程已停止，数据库与配置文件保留。'
