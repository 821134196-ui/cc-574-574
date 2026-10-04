#!/usr/bin/env bash
#
# 一条命令启动「企业合同原件借阅与归还核验系统」
#   ./run.sh          构建前端并启动后端(Quarkus:8080) + 前端(Vite preview:5173)
#   ./run.sh dev      前端使用 Vite 热更新开发服务器
#   ./run.sh test     仅运行后端拦截场景测试
#   ./run.sh build    仅构建后端(jar)与前端(dist)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
DATA="$ROOT/data"

# ---------- 探测 Java 17 / Maven（支持用户态安装到 ~/tools） ----------
detect_toolchain() {
  if ! command -v java >/dev/null 2>&1; then
    JDK_DIR="$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1 || true)"
    if [ -n "$JDK_DIR" ] && [ -x "$JDK_DIR/bin/java" ]; then
      export JAVA_HOME="$JDK_DIR"
      export PATH="$JDK_DIR/bin:$PATH"
    fi
  fi
  if ! command -v mvn >/dev/null 2>&1; then
    MVN_DIR="$(ls -d "$HOME"/tools/apache-maven-* 2>/dev/null | head -1 || true)"
    if [ -n "$MVN_DIR" ] && [ -x "$MVN_DIR/bin/mvn" ]; then
      export PATH="$MVN_DIR/bin:$PATH"
    fi
  fi
  command -v java >/dev/null 2>&1 || { echo "✗ 未找到 java（需要 JDK 17+）"; exit 1; }
  command -v mvn  >/dev/null 2>&1 || { echo "✗ 未找到 mvn（需要 Maven 3.8+）"; exit 1; }
  command -v node >/dev/null 2>&1 || { echo "✗ 未找到 node（需要 Node 18+）"; exit 1; }
  echo "✓ 工具链：$(java -version 2>&1 | head -1)"
}

mkdir -p "$DATA/scans"

MVN_OPTS=(-Ddata.file="$DATA/contracts.db" -Dscan.dir="$DATA/scans")

start_backend() {
  echo "→ 启动后端 Quarkus http://localhost:8080 ..."
  ( cd "$BACKEND" && exec mvn -q quarkus:dev "${MVN_OPTS[@]}" )
}

ensure_frontend_deps() {
  if [ ! -d "$FRONTEND/node_modules" ]; then
    echo "→ 安装前端依赖 ..."
    ( cd "$FRONTEND" && npm install )
  fi
}

start_frontend() {
  ensure_frontend_deps
  if [ "${MODE:-prod}" = "dev" ]; then
    echo "→ 启动前端 Vite dev http://localhost:5173 ..."
    ( cd "$FRONTEND" && exec npm run dev )
  else
    echo "→ 构建前端 ..."
    ( cd "$FRONTEND" && npm run build )
    echo "→ 启动前端 Vite preview http://localhost:5173 ..."
    ( cd "$FRONTEND" && exec npm run preview -- --host )
  fi
}

PIDS=()
cleanup() {
  echo; echo "→ 正在停止服务 ..."
  for pid in "${PIDS[@]:-}"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  done
  # 清理 Quarkus dev 派生进程
  pkill -P $$ 2>/dev/null || true
}
trap cleanup EXIT INT TERM

detect_toolchain

case "${1:-run}" in
  test)
    echo "→ 运行后端拦截测试 ..."
    ( cd "$BACKEND" && mvn -B test )
    ;;
  build)
    ( cd "$BACKEND" && mvn -B package -DskipTests "${MVN_OPTS[@]}" )
    ensure_frontend_deps
    ( cd "$FRONTEND" && npm run build )
    echo "✓ 构建完成：backend/target/quarkus-app 与 frontend/dist"
    ;;
  dev)
    MODE=dev
    start_backend & PIDS+=($!)
    start_frontend & PIDS+=($!)
    echo
    echo "================================================"
    echo " 前端：http://localhost:5173"
    echo " 后端：http://localhost:8080 （健康检查 /q/health）"
    echo " 数据：$DATA/contracts.db ；扫描件：$DATA/scans"
    echo " 按 Ctrl+C 停止全部服务"
    echo "================================================"
    wait
    ;;
  run|"")
    start_backend & PIDS+=($!)
    start_frontend & PIDS+=($!)
    echo
    echo "================================================"
    echo " 前端：http://localhost:5173"
    echo " 后端：http://localhost:8080"
    echo " 数据：$DATA/contracts.db ；扫描件：$DATA/scans"
    echo " 演示账号：周敏(管理员)/李建国(管理员)/王芳(档案员)/张伟等员工"
    echo " 按 Ctrl+C 停止全部服务"
    echo "================================================"
    wait
    ;;
  *)
    echo "未知参数：$1（可用：run | dev | test | build）"; exit 1
    ;;
esac
