#!/usr/bin/env bash
# 一条命令启动：企业合同原件借阅与归还核验系统
# 自动构建 Svelte 前端 -> 启动 Quarkus（SQLite + 静态页面同进程，端口 8080）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

# ---- Java / Maven 定位 ----
# 优先使用系统 JAVA_HOME；若系统无 JDK，尝试本地 tools 目录（免 root 安装的 JDK/Maven）
if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/tools/jdk-17.0.20.1+1" ]; then
  export JAVA_HOME="$HOME/tools/jdk-17.0.20.1+1"
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v java >/dev/null 2>&1; then
  echo "[run.sh] 未找到 java 17，请先安装 JDK 17（或设置 JAVA_HOME）" >&2; exit 1
fi
MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
  if [ -x "$HOME/tools/apache-maven-3.9.9/bin/mvn" ]; then
    MVN="$HOME/tools/apache-maven-3.9.9/bin/mvn"
  else
    echo "[run.sh] 未找到 maven，请先安装 Maven 3.9+" >&2; exit 1
  fi
fi

mkdir -p data/scans

# ---- 端口预检 ----
if curl -s -o /dev/null --max-time 2 http://localhost:8080/; then
  echo "[run.sh] 8080 端口已被占用（可能已有实例在运行）。" >&2
  echo "[run.sh] 如需重启，请先结束旧进程：" >&2
  echo '  for pid in $(ls /proc | grep -E "^[0-9]+$"); do grep -qa "quarkus" /proc/$pid/cmdline && kill -9 $pid; done' >&2
  exit 1
fi

# ---- 前端构建（产物直接进入 Quarkus 静态资源目录） ----
if [ "${SKIP_FRONTEND:-0}" != "1" ]; then
  echo "[run.sh] 构建 Svelte 前端 ..."
  if [ ! -d frontend/node_modules ]; then
    (cd frontend && npm install --no-audit --no-fund)
  fi
  (cd frontend && npm run build)
fi

# ---- 启动后端（开发模式：热重载、自动建表、导入种子数据） ----
echo "[run.sh] 启动 Quarkus：http://localhost:8080 （Ctrl+C 退出）"
cd backend
exec "$MVN" quarkus:dev \
  -Dquarkus.http.host=0.0.0.0 \
  -Dquarkus.analytics.disabled=true \
  -Dapp.data.dir=../data \
  -Dapp.scans.dir=../data/scans
