#!/usr/bin/env bash
# 端到端拦截验证：并发借阅 / 逾期续借 / 密级权限变更 / 归还缺页
# 另含：管理员自批拦截、扫描件岗位授权、顺序审计追查。
# 用法：./test/verify.sh   （会重建 data 数据库并重启本地 Quarkus）
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-17.0.20.1+1}"
MVN="${MVN:-$HOME/tools/apache-maven-3.9.9/bin/mvn}"
export JAVA_HOME
BASE="http://localhost:8080/api"
DATA="$ROOT/data"
LOG=/tmp/cloans-verify.log

PASS=0; FAIL=0
ok()   { echo "  ✅ $*"; PASS=$((PASS+1)); }
bad()  { echo "  ❌ $*"; FAIL=$((FAIL+1)); }

# req METHOD PATH USER JSON  ->  stdout:  BODY\n<<<HTTP_STATUS
req() {
  local method="$1" path="$2" user="$3" json="${4:-}"
  if [ -n "$json" ]; then
    curl -s -X "$method" "$BASE$path" -H "X-User-Id: $user" -H "Content-Type: application/json" -d "$json" \
      -w $'\n<<<%{http_code}'
  else
    curl -s -X "$method" "$BASE$path" -H "X-User-Id: $user" -w $'\n<<<%{http_code}'
  fi
}
code()   { sed -n 's/^<<<//p' <<< "$1"; }
body()   { sed '/^<<</d' <<< "$1"; }
jval()   { python3 -c "import json,sys; d=json.load(sys.stdin);
ks='$2'.split('.')
for k in ks: d=d[int(k)] if isinstance(d,list) else d.get(k)
print('' if d is None else d)" <<< "$1"; }

wait_http() {
  # 以日志中出现 Listening on 为准（避免打到尚未完全退出的旧实例）
  for i in $(seq 1 90); do
    if grep -q "Listening on" "$LOG" 2>/dev/null && curl -s -o /dev/null "$BASE/posts" -H "X-User-Id: 1"; then return 0; fi
    sleep 2
  done
  echo "服务未启动"; tail -20 "$LOG"; return 1
}

kill_port_8080() {
  # 通过 /proc/net/tcp{,6} 的 inode 反查占用 8080（0x1F90，含 IPv6 通配监听）的进程并杀掉
  local inodes inode pid
  inodes=$(awk 'FNR>1 && $2 ~ /:1F90$/ && $4=="0A" {print $10}' /proc/net/tcp /proc/net/tcp6 2>/dev/null)
  for inode in $inodes; do
    for pid in $(ls /proc | grep -E '^[0-9]+$'); do
      if ls -l /proc/$pid/fd 2>/dev/null | grep -q "socket:\[$inode\]"; then
        echo "  杀掉占用 8080 的进程 $pid"
        kill -9 "$pid" 2>/dev/null || true
      fi
    done
  done
  # 兜底：清理本工程遗留的 maven/quarkus 进程
  for pid in $(ls /proc | grep -E '^[0-9]+$'); do
    if grep -qa "quarkus-internal\|quarkus:dev\|DevModeMain" /proc/$pid/cmdline 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
}

reset_server() {
  echo "[verify] 重启服务并重建数据库 ..."
  local pat="Dev""ModeMain"
  for pid in $(ls /proc | grep -E '^[0-9]+$'); do
    if grep -qa "$pat" /proc/$pid/cmdline 2>/dev/null; then kill -9 "$pid" 2>/dev/null || true; fi
  done
  kill_port_8080
  # 等待端口真正释放
  for i in $(seq 1 20); do curl -s -o /dev/null --max-time 1 "$BASE/posts" || break; sleep 1; done
  sleep 2
  kill_port_8080
  rm -f "$DATA"/contract-loan.db*
  : > "$LOG"
  ( cd "$ROOT/backend" && "$MVN" quarkus:dev -Dquarkus.http.host=0.0.0.0 \
      -Dquarkus.analytics.disabled=true \
      -Dapp.data.dir="$DATA" -Dapp.scans.dir="$DATA/scans" ) >> "$LOG" 2>&1 &
  wait_http || { echo "服务启动失败，终止验证"; tail -30 "$LOG"; exit 2; }
}

# 全部页码完好的核验体（contract 1 = 5 页）
all_ok_5() {
  local id="${1:-}"
  echo '{"loanId":'$id',"pages":[{"pageNo":1,"status":"OK","note":""},{"pageNo":2,"status":"OK","note":""},{"pageNo":3,"status":"OK","note":""},{"pageNo":4,"status":"OK","note":""},{"pageNo":5,"status":"OK","note":""}]}'
}

reset_server

echo; echo "== 场景 1：并发借阅——一份原件不能同时借给两人 =="
a=$(req POST /loans 3 '{"contractId":1,"purpose":"并发验证申请人甲业务用印核对","days":5}')
b=$(req POST /loans 5 '{"contractId":1,"purpose":"并发验证申请人乙商务谈判核对","days":5}')
ida=$(jval "$(body "$a")" id); idb=$(jval "$(body "$b")" id)
[ "$(code "$a")" = 200 ] && ok "甲申请成功 #$ida" || bad "甲申请失败：$(body "$a")"
[ "$(code "$b")" = 200 ] && ok "乙申请成功 #$idb（待审批可排队）" || bad "乙申请失败：$(body "$b")"

# 两笔审批同时打出去（显式等待各自 PID，不能用无参 wait——那会把后台服务进程也等上）
req POST /loans/$ida/approve 1 > /tmp/r1.out &
p1=$!
req POST /loans/$idb/approve 1 > /tmp/r2.out &
p2=$!
wait $p1 $p2
c1=$(sed -n 's/^<<<//p' /tmp/r1.out); c2=$(sed -n 's/^<<<//p' /tmp/r2.out)
s200=0; s409=0
for c in "$c1" "$c2"; do [ "$c" = 200 ] && s200=$((s200+1)); [ "$c" = 409 ] && s409=$((s409+1)); done
[ "$s200" = 1 ] && ok "并发审批只有一笔成功（成功 $s200 笔）" || bad "成功笔数=$s200，应为 1（$c1 / $c2）"
[ "$s409" = 1 ] && ok "另一笔被拦截 409：$(sed '/^<<</d' /tmp/r1.out /tmp/r2.out | python3 -c 'import json,sys
for l in sys.stdin:
 l=l.strip()
 if l.startswith("{"):
  try: print(json.loads(l).get("message",""))
  except: pass' | head -1)" || bad "未出现 409 拦截（$c1 / $c2）"

# 赢家走完交接+归还恢复在库；输家拒绝
if [ "$c1" = 200 ]; then winner=$ida; loser=$idb; else winner=$idb; loser=$ida; fi
req POST /loans/$winner/handover 2 > /dev/null
h=$(req POST /return-check 2 "$(all_ok_5 $winner)")
[ "$(code "$h")" = 200 ] && [ "$(jval "$(body "$h")" returned)" = True ] && ok "赢家交接并完好归还，原件回库" || bad "赢家归还失败：$(body "$h")"
rj=$(req POST /loans/$loser/reject 1 '{"reason":"同一原件已有在册借阅"}')
[ "$(code "$rj")" = 200 ] && ok "排队中的另一申请已拒绝释放" || bad "拒绝失败：$(body "$rj")"

echo; echo "== 场景 2：逾期续借被拒 + 逾期提醒跟进 =="
# 种子借阅单 #1（赵磊 PICKED_UP）改成昨天到期，状态仍为借阅中
python3 - "$DATA/contract-loan.db" <<'EOF'
import sqlite3, sys, datetime
yesterday = (datetime.date.today() - datetime.timedelta(days=1)).isoformat()
con = sqlite3.connect(sys.argv[1], timeout=10)
con.execute("update contract_loan set due_date=? where id=1", (yesterday + " 00:00:00",))
con.commit()
EOF
rn=$(req POST /loans/1/renew 4 '{"days":5}')
[ "$(code "$rn")" = 400 ] && ok "到期后续借被 400 拒绝：$(jval "$(body "$rn")" message)" || bad "逾期续借未拦截：$(code "$rn") $(body "$rn")"
sw=$(req POST /admin/overdue-sweep 2)
[ "$(jval "$(body "$sw")" overdueMarked)" = 1 ] && ok "逾期扫描将 #1 标记为逾期并发出跟进提醒" || bad "逾期扫描结果异常：$(body "$sw")"
l1=$(req GET /loans/1 4)
[ "$(jval "$(body "$l1")" status)" = OVERDUE ] && ok "借阅单状态=OVERDUE" || bad "状态未翻转：$(body "$l1")"
n4=$(req GET /notifications 4)
echo "$(body "$n4")" | grep -q "借阅逾期" && ok "借阅人收到逾期提醒" || bad "借阅人未收到逾期提醒"
n1=$(req GET /notifications 1)
echo "$(body "$n1")" | grep -q "逾期跟进" && ok "管理人员收到逾期跟进提醒" || bad "管理员未收到跟进提醒"
rmd=$(req POST /loans/1/remind 1 '{"message":"请今日归还原件"}')
[ "$(code "$rmd")" = 200 ] && ok "管理员催还已发送（本地模拟提醒中心）" || bad "催还失败：$(body "$rmd")"

echo; echo "== 场景 3：密级调整 / 调岗后重新核验权限 =="
lv=$(req POST /admin/contract-level 1 '{"contractId":2,"securityLevel":"SECRET"}')
[ "$(code "$lv")" = 200 ] && ok "合同2密级提升为机密，在册借阅生成重检单" || bad "密级调整失败：$(body "$lv")"
rc=$(req GET /rechecks 1)
rid=$(python3 -c "import json,sys; d=json.load(sys.stdin); print([r['id'] for r in d if r['loanId']==1 and r['status']=='PENDING'][0])" <<<"$(body "$rc")")
tryPass=$(req POST /rechecks/$rid/decision 1 '{"pass":true,"note":"尝试放行"}')
[ "$(code "$tryPass")" = 403 ] && ok "系统拒绝在密级仍不满足时判定通过：$(jval "$(body "$tryPass")" message)" || bad "误放行了权限不满足的重检：$(code "$tryPass")"
rk=$(req POST /rechecks/$rid/decision 1 '{"pass":false,"note":"密级不足，中止并催还"}')
[ "$(code "$rk")" = 200 ] && ok "判定不通过，借阅中止" || bad "重检结论失败：$(body "$rk")"
l1b=$(req GET /loans/1 1)
[ "$(jval "$(body "$l1b")" status)" = REVOKED ] && ok "借阅单状态=REVOKED（权限中止·催还中，原件仍被占用）" || bad "状态非 REVOKED：$(body "$l1b")"
# 权限不足员工申请机密合同
ap=$(req POST /loans 5 '{"contractId":3,"purpose":"无权岗位尝试借阅机密件测试","days":3}')
[ "$(code "$ap")" = 403 ] && ok "低密级岗位申请机密合同被 403 拦截" || bad "密级申请拦截缺失：$(code "$ap") $(body "$ap")"

# 调岗流程：孙丽借合同1（内部），批准后调岗，待重检期间禁止交接，通过后放行
a5=$(req POST /loans 5 '{"contractId":1,"purpose":"孙丽调岗重检场景业务用件","days":5}')
l5=$(jval "$(body "$a5")" id)
p5=$(req POST /loans/$l5/approve 1); [ "$(code "$p5")" = 200 ] || bad "孙丽借阅批准失败：$(body "$p5")"
tf=$(req POST /admin/transfer 1 '{"userId":5,"postId":3}')
[ "$(code "$tf")" = 200 ] && ok "孙丽调岗（市场岗→采购执行岗），在册借阅生成重检" || bad "调岗失败：$(body "$tf")"
rc5=$(req GET /rechecks 1)
rid5=$(python3 -c "import json,sys; d=json.load(sys.stdin); print([r['id'] for r in d if r['loanId']==$l5 and r['status']=='PENDING'][0])" <<<"$(body "$rc5")")
hb=$(req POST /loans/$l5/handover 2)
[ "$(code "$hb")" = 409 ] && ok "重检未完成时交接被拦截：$(jval "$(body "$hb")" message)" || bad "交接拦截缺失：$(code "$hb") $(body "$hb")"
dp=$(req POST /rechecks/$rid5/decision 1 '{"pass":true,"note":"新岗位许可仍满足内部件"}')
[ "$(code "$dp")" = 200 ] || bad "重检通过失败：$(body "$dp")"
hb2=$(req POST /loans/$l5/handover 2)
[ "$(code "$hb2")" = 200 ] && ok "重检通过后交接成功（记录实际经手人）" || bad "交接失败：$(body "$hb2")"
ret5=$(req POST /return-check 2 "$(all_ok_5 $l5)")
[ "$(jval "$(body "$ret5")" returned)" = True ] && ok "调岗场景借阅完好归还、原件回库" || bad "调岗场景归还失败：$(body "$ret5")"

echo; echo "== 场景 4：归还缺页——进入异常处理，不能签为已还 =="
a3=$(req POST /loans 3 '{"contractId":1,"purpose":"李强缺页场景业务办理用件","days":5}')
l3=$(jval "$(body "$a3")" id)
req POST /loans/$l3/approve 1 > /dev/null
req POST /loans/$l3/handover 2 > /dev/null
miss='{"loanId":'$l3',"pages":[{"pageNo":1,"status":"OK","note":""},{"pageNo":2,"status":"OK","note":""},{"pageNo":3,"status":"MISSING","note":"附件验收单缺失"},{"pageNo":4,"status":"DAMAGED","note":"水渍"},{"pageNo":5,"status":"OK","note":"盖章页完好"}]}'
chk=$(req POST /return-check 2 "$miss")
[ "$(code "$chk")" = 200 ] && [ "$(jval "$(body "$chk")" blockedReturn)" = True ] && ok "缺页+损坏被识别（第3页缺页/第4页损坏），返回 blockedReturn" || bad "缺页核验异常：$(body "$chk")"
l3v=$(req GET /loans/$l3 2)
[ "$(jval "$(body "$l3v")" status)" != RETURNED ] && [ "$(jval "$(body "$l3v")" anomalyOpen)" = True ] && ok "借阅单未签为已还，保持在册且 anomalyOpen=true" || bad "缺页后竟可签还：$(body "$l3v")"
an=$(req GET /anomalies?status=OPEN 2)
anid=$(python3 -c "import json,sys; d=json.load(sys.stdin); print([a['id'] for a in d if a['loanId']==$l3][0])" <<<"$(body "$an")")
echo "$(body "$an")" | python3 -c "import json,sys; d=json.load(sys.stdin); n=len([a for a in d if a['loanId']==$l3]); sys.exit(0 if n==2 else 1)" && ok "为两个问题页各生成一张异常单（共 2 张）" || bad "异常单数量不对"
again=$(req POST /return-check 2 "$(all_ok_5 $l3)")
[ "$(code "$again")" = 409 ] && ok "即使再次核验全部完好，异常未闭环仍禁止签还：$(jval "$(body "$again")" message)" || bad "异常未闭环竟可签还：$(code "$again")"
rv=$(req POST /anomalies/$anid/resolve 3 '{"resolution":"员工尝试自行闭环异常应被拒"}')
[ "$(code "$rv")" = 403 ] && ok "非管理员闭环异常被 403 拦截" || bad "越权闭环未拦截：$(code "$rv")"
req POST /anomalies/$anid/resolve 1 '{"resolution":"第3页验收单已找回，经核对与原件一致"}' > /dev/null
ids=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(' '.join(str(a['id']) for a in d if a['loanId']==$l3 and a['status']=='OPEN'))" <<<"$(body "$an")")
for x in $ids; do req POST /anomalies/$x/resolve 1 "{\"resolution\":\"第4页水渍已修复并情况说明，单$x\"}" > /dev/null; done
left=$(req GET /anomalies?status=OPEN 1)
echo "$(body "$left")" | grep -q "\"loanId\":$l3" && bad "仍有未闭环异常" || ok "异常单全部闭环"
final=$(req POST /return-check 2 "$(all_ok_5 $l3)")
[ "$(jval "$(body "$final")" returned)" = True ] && ok "重新逐页核验完好后方才签为已还、回库并核销门禁" || bad "闭环后签还失败：$(body "$final")"

echo; echo "== 附加 1：管理员不能批准自己的借阅 =="
self=$(req POST /loans 1 '{"contractId":1,"purpose":"管理员本人申请测试自批拦截","days":3}')
sid=$(jval "$(body "$self")" id)
sa=$(req POST /loans/$sid/approve 1)
[ "$(code "$sa")" = 403 ] && ok "自批被 403 拦截：$(jval "$(body "$sa")" message)" || bad "自批拦截缺失：$(code "$sa")"
req POST /loans/$sid/cancel 1 > /dev/null

echo; echo "== 附加 2：扫描件仅授权岗位可查看 =="
printf '%%PDF-1.4\n%%%%dummyscan\n%%%%%d\n' 1 2 3 > /tmp/scan-test.pdf
up=$(curl -s -X POST "$BASE/contracts/1/scan" -H "X-User-Id: 2" -H "Content-Type: application/octet-stream" -H "X-Filename: scan-test.pdf" --data-binary @/tmp/scan-test.pdf -w $'\n<<<%{http_code}')
[ "$(code "$up")" = 200 ] && ok "档案员上传扫描件到本地目录" || bad "上传失败：$(body "$up")"
s3=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/contracts/1/scan" -H "X-User-Id: 3")
[ "$s3" = 403 ] && ok "采购执行岗（未授权）查看扫描件 403" || bad "未授权查看未拦截：$s3"
s2=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/contracts/1/scan" -H "X-User-Id: 2")
[ "$s2" = 200 ] && ok "法务档案岗（授权岗位）可查看扫描件" || bad "授权岗位查看失败：$s2"

echo; echo "== 附加 3：交接与核验记录顺序可追查 =="
tr=$(req GET /audit/trace/$l3 1)
n=$(python3 -c "import json,sys; print(len(json.load(sys.stdin)['trace']))" <<<"$(body "$tr")")
[ "$n" -ge 6 ] && ok "借阅单 #$l3 顺序链路 $n 条（申请→审批→交接→异常→处理→重检→签还）" || bad "链路记录不完整：$n 条"
echo "$(body "$tr")" | grep -q "缺页" && ok "链路中可追到缺页异常记录" || bad "链路缺异常记录"

echo; echo "=========================================="
echo "通过 $PASS 项，失败 $FAIL 项"
[ "$FAIL" = 0 ] && echo "🎉 全部拦截场景验证通过" || exit 1
