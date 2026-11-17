param([switch]$KillOnly,[switch]$DisableWorker)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$local=Join-Path $root '.local'
$pidFile=Join-Path $local 'pids.json'
$entries=@(Get-Content -LiteralPath $pidFile -Raw|ConvertFrom-Json)
$jar=Join-Path $root 'yiyue-server/yiyue-application/target/yiyue-server-0.4.0.jar'
foreach($entry in $entries){
 $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.id)" -ErrorAction SilentlyContinue
 # 严格匹配登记的可执行路径及完整JAR路径，禁止影响其他Java进程。
 if($process -and $process.ExecutablePath -eq $entry.executable -and $process.CommandLine.Contains($jar)){
  Stop-Process -Id $entry.id -Force
  $oldBackend=$entry.id
 }
}
if($KillOnly){if(!$oldBackend){throw '没有找到可核对的项目后端进程'};Write-Host '已强制终止项目后端';exit}
Get-Content -LiteralPath (Join-Path $root '.env')|ForEach-Object{if($_ -match '^([A-Z_]+)=(.*)$'){[Environment]::SetEnvironmentVariable($matches[1],$matches[2],'Process')}}
$env:DB_URL='jdbc:postgresql://127.0.0.1:55433/yiyue'
$env:DB_USERNAME='yiyue';$env:SERVER_PORT='18080';$env:REDIS_HOST='127.0.0.1';$env:REDIS_PORT='56379'
$env:YIYUE_WAITLIST_WORKER_ENABLED=(!$DisableWorker).ToString().ToLowerInvariant()
$java=(Get-Command java.exe).Source
$backend=Start-Process -FilePath $java -ArgumentList ('-jar "{0}"' -f $jar) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'backend.log') -RedirectStandardError (Join-Path $local 'backend.err.log')
$entries=@($entries|Where-Object{$_.id -ne $oldBackend -and (Get-Process -Id $_.id -ErrorAction SilentlyContinue)})+@(@{id=$backend.Id;executable=$java})
$entries|ConvertTo-Json -AsArray|Set-Content -LiteralPath $pidFile
for($i=0;$i -lt 45;$i++){try{$r=Invoke-WebRequest 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 1 -NoProxy;if($r.StatusCode -eq 200){Write-Host '后端已恢复';exit}}catch{};Start-Sleep -Seconds 1}
throw '后端恢复超时'
