<script>
  import { createEventDispatcher } from 'svelte';
  import { fmtDate, dueClass } from '../stores.js';

  export let loan;
  export let me;
  const dispatch = createEventDispatcher();

  const isAdmin = () => me.role === 'ADMIN';
  const isArchivist = () => me.role === 'ARCHIVIST';
  const isBorrower = () => me.id === loan.borrowerId;

  const canApprove = isAdmin() && loan.status === 'PENDING' && me.id !== loan.borrowerId;
  const canIssue = isArchivist() && loan.status === 'APPROVED';
  const canReturn = isArchivist() && ['ISSUED', 'OVERDUE', 'ANOMALY'].includes(loan.status);
  const canRenew = (isBorrower() || isAdmin()) && ['ISSUED', 'OVERDUE'].includes(loan.status);
  const canReverify = isAdmin() && loan.reverify === 'REQUIRED';
  const isAnomaly = loan.status === 'ANOMALY';
</script>

<div class="loan-card">
  <div class="row" style="justify-content:space-between">
    <span class="code">{loan.contractCode} <span class="chip secrecy">{loan.secrecyLabel}</span></span>
    <span class="chip {loan.status}">{loan.statusLabel}{loan.overdue ? ' · 需跟进' : ''}</span>
  </div>
  <div class="small muted">{loan.contractTitle}</div>
  <div class="purpose">用途：{loan.purpose}</div>

  <!-- 借阅人、实际经手人、到期日放在一起 -->
  <div class="trio">
    <div>
      <div class="k">借阅人</div>
      <div class="v">{loan.borrowerName}</div>
      <div class="small muted">{loan.borrowerPost}</div>
    </div>
    <div>
      <div class="k">实际经手人</div>
      <div class="v">{loan.issueHandler || loan.returnHandler || '—'}</div>
      <div class="small muted">{loan.status === 'RETURNED' ? '送还经手' : '取走经手'}</div>
    </div>
    <div>
      <div class="k">到期日</div>
      <div class="v {dueClass(loan)}">{fmtDate(loan.dueDate)}</div>
      <div class="small muted">续借 {loan.renewalCount} 次</div>
    </div>
  </div>

  {#if loan.reverify === 'REQUIRED'}
    <div class="warnbox">借阅人已调岗或合同密级变更，须管理员重新核验权限后续借。</div>
  {:else if loan.reverify === 'OK'}
    <div class="okbox">权限已重新核验通过。</div>
  {/if}

  <div class="actions">
    <button class="ghost sm" on:click={() => dispatch('detail', loan)}>详情/追溯</button>
    {#if canApprove}
      <button class="sm" on:click={() => dispatch('approve', loan)}>审批</button>
    {/if}
    {#if canIssue}
      <button class="sm" on:click={() => dispatch('issue', loan)}>档案员出库</button>
    {/if}
    {#if canReturn}
      <button class="sm {isAnomaly ? 'warn' : ''}" on:click={() => dispatch('return', loan)}>
        {isAnomaly ? '再次归还核验' : '归还逐页核验'}
      </button>
    {/if}
    {#if canRenew}
      <button class="ghost sm" on:click={() => dispatch('renew', loan)}>申请续借</button>
    {/if}
    {#if canReverify}
      <button class="warn sm" on:click={() => dispatch('reverify', loan)}>重新核验权限</button>
    {/if}
  </div>
</div>
