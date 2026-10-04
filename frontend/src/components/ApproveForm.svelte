<script>
  import { createEventDispatcher } from 'svelte';

  export let loan;
  export let me;
  const dispatch = createEventDispatcher();

  let decision = 'yes';
  let rejectReason = '';

  const selfApprove = me.id === loan.borrowerId;
  const approved = () => decision === 'yes';
</script>

{#if selfApprove}
  <div class="errbox">管理员不能批准自己提交的借阅申请，请由其他管理人员审批。</div>
{:else}
  <div class="field">
    <label>审批结论</label>
    <div class="row">
      <label class="row" style="gap:6px"><input type="radio" bind:group={decision} value="yes" /> 批准</label>
      <label class="row" style="gap:6px"><input type="radio" bind:group={decision} value="no" /> 驳回</label>
    </div>
  </div>
  {#if !approved}
    <div class="field">
      <label>驳回原因</label>
      <textarea rows="2" bind:value={rejectReason}></textarea>
    </div>
  {/if}
  <div class="small muted mt8">
    批准后将锁定当前合同密级与借阅人岗位版本；其后调岗或密级变更会触发重新核验。
  </div>
{/if}
<div class="row mt16" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  {#if !selfApprove}
    <button class={decision === 'yes' ? '' : 'danger'}
      on:click={() => dispatch('submit', { approved: approved(), rejectReason })}>
      {decision === 'yes' ? '确认批准' : '确认驳回'}
    </button>
  {/if}
</div>
