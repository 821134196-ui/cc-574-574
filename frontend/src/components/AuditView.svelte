<script>
  import { fmtInstant } from '../stores.js';
  export let trail = [];

  const ACTION_LABEL = {
    LOAN_APPLY: '借阅申请', LOAN_APPROVE: '审批通过', LOAN_REJECT: '审批驳回',
    LOAN_ISSUE: '原件出库', LOAN_RETURNED: '签为已还',
    RENEW_APPLY: '续借申请', RENEW_APPROVE: '续借批准', RENEW_REJECT: '续借驳回',
    REVERIFY_OK: '重核验通过', REVERIFY_FAIL: '重核验未通过',
    RETURN_ANOMALY: '归还异常', RETURN_CHECK_PASS_PENDING_ANOMALY: '核验通过但异常未闭环',
    ANOMALY_RESOLVE: '异常闭环',
    EMP_CREATE: '建档员工', EMP_TRANSFER: '员工调岗/改权限', EMP_ENABLE: '启用员工', EMP_DISABLE: '停用员工',
    CONTRACT_CREATE: '登记合同', CONTRACT_SECRECY_CHANGE: '合同密级变更',
    SCAN_UPLOAD: '上传扫描件', SCAN_VIEW: '查看扫描件',
    SCAN_DENIED_NO_AUTH: '拒绝查看扫描件(未授权)', SCAN_DENIED_SECRECY: '拒绝查看扫描件(密级不足)'
  };
</script>

<div class="panel">
  <h2>全局顺序审计链（{trail.length} 条）</h2>
  <p class="muted small" style="margin:0">
    所有关键操作只追加、不修改删除；每条带全局 <b>seq</b> 与 <b>prevId</b>，可从头顺序追查，发现断链即说明记录被破坏。
  </p>
</div>

<div class="panel" style="padding:0;overflow-x:auto">
  <table>
    <tr><th>seq</th><th>动作</th><th>借阅单</th><th>合同</th><th>员工</th><th>操作人</th><th>明细</th><th>时间</th><th>链路</th></tr>
    {#each trail as r}
      <tr>
        <td><b>{r.seq}</b></td>
        <td>{ACTION_LABEL[r.action] || r.action}</td>
        <td>{r.loanId ?? '—'}</td>
        <td>{r.contractId ?? '—'}</td>
        <td>{r.employeeId ?? '—'}</td>
        <td>{r.actor}</td>
        <td class="small muted">{r.detail || ''}</td>
        <td class="small">{fmtInstant(r.at)}</td>
        <td class="small muted">{r.id} ← {r.prevId ?? '∅'}</td>
      </tr>
    {/each}
  </table>
</div>
