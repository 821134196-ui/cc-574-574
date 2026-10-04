<script>
  import { createEventDispatcher } from 'svelte';
  import { todayPlus } from '../stores.js';
  const dispatch = createEventDispatcher();

  export let loan;
  let newDueDate = todayPlus(14);
  let reason = '';
</script>

{#if loan.overdue}
  <div class="errbox">该借阅已逾期，续借被系统拦截。请先归还或联系管理员跟进。</div>
{:else}
  <div class="field">
    <label>当前到期日：{loan.dueDate}</label>
  </div>
  <div class="field">
    <label>申请新到期日（必须晚于当前到期日）</label>
    <input type="date" bind:value={newDueDate} min={loan.dueDate} />
  </div>
  <div class="field">
    <label>续借理由</label>
    <textarea rows="2" bind:value={reason}></textarea>
  </div>
  {#if loan.reverify === 'REQUIRED'}
    <div class="warnbox">存在待重核验的权限变更，提交后将被拦截，请先由管理员重新核验。</div>
  {/if}
{/if}
<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  {#if !loan.overdue}
    <button on:click={() => dispatch('submit', { newDueDate, reason })}>提交续借申请</button>
  {/if}
</div>
