# AcademicSearch 一键启动脚本
# 单窗口拉起全部服务并统一输出日志：
#   前端 Vite :5173 / Java 后端 :8080 / Python Agent :8000 / Milvus :19530（Docker，按需）
# 用法：
#   .\start.ps1            启动并进入统一日志跟随（Ctrl+C 停止本次启动的所有服务）
#   .\start.ps1 -NoTail    启动完成后即退出（服务留在后台，日志在 logs\ 目录）
# 前置：MySQL(:3306) 需自行运行；根目录需有 .env（参考 .env.example）
param([switch]$NoTail)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}

# 终端 PATH 可能不含 Docker Desktop 的可执行目录，优先补入（常见安装位置）
if (!(Get-Command docker -ErrorAction SilentlyContinue)) {
    $dockerBin = @(
        "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin",
        "$env:ProgramFiles\Docker\Docker\resources\bin"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($dockerBin) { $env:PATH = "$dockerBin;$env:PATH" }
}

$Logs = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $Logs | Out-Null
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$Started = @{}          # 本次启动的服务: name -> pid
$script:MilvusStarted = $false

function Test-Port([int]$Port) {
    @((Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)).Count -gt 0
}

function Wait-Http([string]$Url, [string]$Name, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 3
            if ($r.StatusCode -lt 500) { return $true }
        } catch {}
        Start-Sleep -Milliseconds 800
    }
    Write-Host "  [!] $Name 未在 ${TimeoutSec}s 内就绪，继续启动（稍后可查看 logs\ 目录）" -ForegroundColor Yellow
    return $false
}

function Test-DockerEngine {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try {
        $v = docker info --format '{{.ServerVersion}}' 2>$null
        return ($LASTEXITCODE -eq 0 -and $v)
    } finally { $ErrorActionPreference = $old }
}

# Docker 引擎未就绪时尝试拉起 Docker Desktop（经 explorer 启动，避免受当前进程树限制）
function Ensure-DockerEngine([int]$TimeoutSec = 120) {
    if (Test-DockerEngine) { return $true }
    $exe = @(
        "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (!$exe) { return $false }
    Write-Host '  [i] Docker 引擎未就绪，正在启动 Docker Desktop（约 30-60s）...' -ForegroundColor DarkGray
    Start-Process -FilePath explorer.exe -ArgumentList "`"$exe`"" | Out-Null
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerEngine) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Start-Svc([string]$Name, [string]$File, [string]$Arguments, [string]$Cwd) {
    $out = Join-Path $Logs "$Stamp-$Name.out.log"
    $err = Join-Path $Logs "$Stamp-$Name.err.log"
    $p = Start-Process -FilePath $File -ArgumentList $Arguments -WorkingDirectory $Cwd `
        -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
    $script:Started[$Name] = $p.Id
    Write-Host ("  [+] {0,-9} pid {1,-6} 日志 {2}" -f $Name, $p.Id, "$Stamp-$Name.out.log") -ForegroundColor Green
}

function Stop-Started {
    foreach ($k in @($script:Started.Keys)) {
        $procId = $script:Started[$k]
        if (Get-Process -Id $procId -ErrorAction SilentlyContinue) {
            taskkill /PID $procId /T /F | Out-Null
            Write-Host "  [-] 已停止 $k (pid $procId)"
        }
    }
    if ($script:MilvusStarted) {
        Push-Location (Join-Path $Root 'milvus')
        docker compose stop | Out-Null
        Pop-Location
        Write-Host '  [-] 已停止 Milvus（docker compose stop，数据卷保留）'
    }
}

# ---------- 前置检查 ----------
Write-Host "`n== 前置检查 ==" -ForegroundColor Cyan

if (!(Test-Path (Join-Path $Root '.env'))) {
    Write-Host "  [x] 缺少根目录 .env（数据库密码 / JWT_SECRET / DashScope Key 等）。请参照 .env.example 创建后重试。" -ForegroundColor Red
    exit 1
}
if (!(Test-Path (Join-Path $Root 'frontend-react\node_modules'))) {
    Write-Host "  [x] 前端依赖未安装：cd frontend-react && npm install" -ForegroundColor Red
    exit 1
}
if (!(Test-Path (Join-Path $Root 'agent-python\.venv\Scripts\python.exe'))) {
    Write-Host "  [x] Agent 虚拟环境缺失：cd agent-python && python -m venv .venv && .venv\Scripts\pip install -r requirements.txt" -ForegroundColor Red
    exit 1
}
if (!(Test-Port 3306)) {
    $mysqlSvc = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue
    if ($mysqlSvc -and $mysqlSvc.Status -ne 'Running') {
        Write-Host "  [i] MySQL80 服务未运行，正在启动..." -ForegroundColor DarkGray
        try {
            Start-Service -Name 'MySQL80' -ErrorAction Stop
            $deadline = (Get-Date).AddSeconds(30)
            while (!(Test-Port 3306) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 1000 }
        } catch {
            Write-Host "  [!] MySQL80 启动失败：$($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
    if (Test-Port 3306) {
        Write-Host '  [+] MySQL :3306 已就绪' -ForegroundColor Green
    } else {
        Write-Host "  [!] MySQL(:3306) 未监听，Java 后端将启动失败——请先启动 MySQL 服务" -ForegroundColor Yellow
    }
} else {
    Write-Host '  [+] MySQL :3306 已在运行' -ForegroundColor DarkGray
}

# Milvus：未运行则确保 Docker 引擎可用后 docker compose 拉起
if (!(Test-Port 19530)) {
    if (Ensure-DockerEngine) {
        try {
            Write-Host '  [i] Milvus 未运行，正在 docker compose up -d ...' -ForegroundColor DarkGray
            Push-Location (Join-Path $Root 'milvus')
            docker compose up -d | Out-Null
            Pop-Location
            $script:MilvusStarted = $true
            $deadline = (Get-Date).AddSeconds(120)
            while (!(Test-Port 19530) -and (Get-Date) -lt $deadline) { Start-Sleep -Milliseconds 1000 }
            if (Test-Port 19530) { Write-Host '  [+] Milvus :19530 已就绪' -ForegroundColor Green }
            else { Write-Host '  [!] Milvus 120s 内未就绪（向量检索功能可能不可用）' -ForegroundColor Yellow }
        } catch {
            Write-Host '  [!] Milvus 启动失败，请手动运行 milvus\docker-compose.yml' -ForegroundColor Yellow
        }
    } else {
        Write-Host '  [!] Docker 不可用，无法自动启动 Milvus；请手动启动 Docker Desktop' -ForegroundColor Yellow
    }
} else {
    Write-Host '  [+] Milvus :19530 已在运行' -ForegroundColor Green
}

# ---------- 启动服务（已占用的端口视为已在运行，跳过） ----------
Write-Host "`n== 启动服务 ==" -ForegroundColor Cyan

$startedBackend = $false
$startedAgent = $false
$startedFrontend = $false

if (Test-Port 8080) {
    Write-Host '  [i] :8080 已被占用，Java 后端视为已在运行，跳过（重启请先 .\stop.ps1）' -ForegroundColor Yellow
} else {
    Start-Svc 'backend' 'cmd.exe' '/c mvn spring-boot:run' (Join-Path $Root 'backend')
    $startedBackend = $true
}
if (Test-Port 8000) {
    Write-Host '  [i] :8000 已被占用，Python Agent 视为已在运行，跳过' -ForegroundColor Yellow
} else {
    Start-Svc 'agent' (Join-Path $Root 'agent-python\.venv\Scripts\python.exe') 'main.py' (Join-Path $Root 'agent-python')
    $startedAgent = $true
}
if (Test-Port 5173) {
    Write-Host '  [i] :5173 已被占用，前端视为已在运行，跳过' -ForegroundColor Yellow
} else {
    Start-Svc 'frontend' 'cmd.exe' '/c npm run dev' (Join-Path $Root 'frontend-react')
    $startedFrontend = $true
}

# ---------- 健康检查（仅针对本次启动的服务） ----------
Write-Host "`n== 健康检查 ==" -ForegroundColor Cyan
if ($startedBackend) {
    if (Wait-Http 'http://localhost:8080/api/auth/captcha' 'Java 后端' 180) {
        Write-Host '  [+] Java 后端 http://localhost:8080' -ForegroundColor Green
    }
}
if ($startedAgent) {
    if (Wait-Http 'http://localhost:8000/health' 'Python Agent' 90) {
        Write-Host '  [+] Python Agent http://localhost:8000' -ForegroundColor Green
    }
}
if ($startedFrontend) {
    if (Wait-Http 'http://localhost:5173' '前端' 60) {
        Write-Host '  [+] 前端 http://localhost:5173' -ForegroundColor Green
    }
}

Write-Host "`n全部就绪：前端 http://localhost:5173 （日志目录 logs\，停止用 .\stop.ps1）`n" -ForegroundColor Cyan

if ($NoTail) { return }

# ---------- 统一日志跟随（Ctrl+C 退出并停止本次启动的服务） ----------
$tailFiles = @(
    @{ tag = 'JAVA';   color = 'Gray';           path = Join-Path $Logs "$Stamp-backend.out.log" },
    @{ tag = 'JAVA!';  color = 'DarkYellow';     path = Join-Path $Logs "$Stamp-backend.err.log" },
    @{ tag = 'AGENT';  color = 'Cyan';           path = Join-Path $Logs "$Stamp-agent.out.log" },
    @{ tag = 'AGENT!'; color = 'DarkYellow';     path = Join-Path $Logs "$Stamp-agent.err.log" },
    @{ tag = 'WEB';    color = 'Magenta';        path = Join-Path $Logs "$Stamp-frontend.out.log" },
    @{ tag = 'WEB!';   color = 'DarkYellow';     path = Join-Path $Logs "$Stamp-frontend.err.log" }
)
$offsets = @{}
foreach ($f in $tailFiles) { $offsets[$f.path] = 0 }

try {
    Write-Host "=== 统一日志输出中（Ctrl+C 停止全部本次启动的服务） ===`n" -ForegroundColor Cyan
    while ($true) {
        foreach ($f in $tailFiles) {
            if (Test-Path $f.path) {
                $fi = Get-Item $f.path
                if ($fi.Length -gt $offsets[$f.path]) {
                    $fs = [IO.File]::Open($f.path, 'Open', 'Read', 'ReadWrite')
                    $null = $fs.Seek($offsets[$f.path], 'Begin')
                    $sr = New-Object IO.StreamReader($fs, [Text.Encoding]::UTF8)
                    $chunk = $sr.ReadToEnd()
                    $sr.Close(); $fs.Close()
                    $offsets[$f.path] = $fi.Length
                    foreach ($line in ($chunk -split "`r?`n")) {
                        if ($line.Trim()) { Write-Host ("[{0}] {1}" -f $f.tag, $line) -ForegroundColor $f.color }
                    }
                }
            }
        }
        Start-Sleep -Milliseconds 500
    }
}
finally {
    Write-Host "`n正在停止本次启动的全部服务..." -ForegroundColor Yellow
    Stop-Started
    Write-Host '已全部停止。' -ForegroundColor Yellow
}
