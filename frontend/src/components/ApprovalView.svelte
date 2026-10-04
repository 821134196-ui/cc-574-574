<script>
  import { createEventDispatcher } from 'svelte';
  import LoanCard from './LoanCard.svelte';

  export let loans = [];
  export let me;
  const dispatch = createEventDispatcher();

  const pending = loans.filter((l) => l.status === 'PENDING');
</script>

<div class="panel">
  <h2>借阅审批（{pending.length} 笔待办）</h2>
  <p class="muted small" style="margin:0">
    审批时会复核借阅人密级；<b>管理员不能批准自己的借阅</b>，系统将拦截自审操作。
  </p>
</div>

<div class="grid cols2">
  {#each pending as loan (loan.id)}
    <LoanCard {loan} {me}
      on:detail={(e) => dispatch('detail', e.detail)}
      on:approve={(e) => dispatch('approve', e.detail)} />
  {/each}
</div>
{#if pending.length === 0}<div class="panel muted">暂无待审批申请。</div>{/if}
