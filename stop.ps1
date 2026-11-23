# AcademicSearch 强制停止脚本：按端口结束三个应用服务的整棵进程树
# 用法：.\stop.ps1          停止前端/Agent/Java 后端
#       .\stop.ps1 -All     同时停止 Milvus（docker compose stop，数据卷保留）
param([switch]$All)

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

foreach ($port in 5173, 8000, 8080) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in @($conns)) {
        if ($c.OwningProcess -and $c.OwningProcess -gt 4) {
            taskkill /PID $c.OwningProcess /T /F | Out-Null
            Write-Host "[x] 已停止 :$port (pid $($c.OwningProcess))"
        }
    }
    if (!(@($conns).Count)) { Write-Host "[i] :$port 未在运行" -ForegroundColor DarkGray }
}

if ($All) {
    Push-Location (Join-Path $Root 'milvus')
    docker compose stop | Out-Null
    Pop-Location
    Write-Host '[x] 已停止 Milvus（数据卷保留）'
}
Write-Host '完成。' -ForegroundColor Green
