param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$file = Join-Path $root '.env'
if (Test-Path -LiteralPath $file) { throw '.env 已存在；为避免覆盖现有配置，未作修改。' }
function New-Secret {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToHexString($bytes)
}
# 所有本地凭证随机生成并写入已被 Git 忽略的文件，不进入源码和日志。
$lines = @("DB_PASSWORD=$(New-Secret)", "REDIS_PASSWORD=$(New-Secret)", 'DEMO_ENABLED=true', "DEMO_ADMIN_PASSWORD=$(New-Secret)", "DEMO_PATIENT_PASSWORD=$(New-Secret)", 'COOKIE_SECURE=false', 'AI_ENABLED=false', 'DEEPSEEK_API_KEY=', 'DEEPSEEK_MODEL=deepseek-flash')
[IO.File]::WriteAllLines($file, $lines)
Write-Host '已生成 .env。请在本地查看 DEMO_ADMIN_PASSWORD 和 DEMO_PATIENT_PASSWORD。'
