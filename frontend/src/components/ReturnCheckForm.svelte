<script>
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();

  export let loan;

  let handler = loan.issueHandler || loan.borrowerName || '';
  let pagesPresent = loan.contractPages;
  let missingPages = '';
  let missingAttachments = '';
  let stampedPagesOk = true;
  let damageDesc = '';
  let note = '';
  let confirmAnomaly = false;

  const totalPages = loan.contractPages;
  const pageShort = () => totalPages - Number(pagesPresent || 0) > 0 || missingPages.trim() !== ''
    || missingAttachments.trim() !== '' || !stampedPagesOk;
  const damaged = () => damageDesc.trim() !== '';
  const hasProblem = () => pageShort() || damaged();

  function submit() {
    if (!handler.trim()) { alert('必须登记实际送还经手人'); return; }
    if (hasProblem() && !confirmAnomaly) {
      confirmAnomaly = true; // 二次确认：问题件不能直接签为已还
      return;
    }
    dispatch('submit', {
      handler: handler.trim(),
      pagesPresent: Number(pagesPresent),
      missingPages: missingPages.trim(),
      missingAttachments: missingAttachments.trim(),
      stampedPagesOk,
      damageDesc: damageDesc.trim(),
      note: note.trim()
    });
  }
</script>

<div class="okbox">
  归还合同 <b>{loan.contractCode}</b>，原件共 <b>{totalPages}</b> 页，请逐页核对正文、附件与盖章页。
</div>

<div class="field">
  <label>实际送还经手人</label>
  <input bind:value={handler} />
</div>

<div class="grid cols2">
  <div class="field">
    <label>实到页数（共 {totalPages} 页）</label>
    <input type="number" min="0" max={totalPages} bind:value={pagesPresent} />
  </div>
  <div class="field">
    <label>缺失页码（逗号分隔，如 3,4）</label>
    <input bind:value={missingPages} placeholder="无则留空" />
  </div>
</div>

<div class="field">
  <label>缺失/异常附件</label>
  <input bind:value={missingAttachments} placeholder="无则留空" />
</div>

<div class="field">
  <label class="row" style="gap:8px">
    <input type="checkbox" bind:checked={stampedPagesOk} />
    盖章页齐全（{loan.contractCode} 要求的盖章页）
  </label>
</div>

<div class="field">
  <label>损坏情况（水渍/撕裂/涂改等）</label>
  <textarea rows="2" bind:value={damageDesc} placeholder="无则留空"></textarea>
</div>

<div class="field">
  <label>备注</label>
  <input bind:value={note} />
</div>

{#if hasProblem()}
  <div class="errbox">
    <b>核验发现缺页/附件或盖章页缺失或损坏。</b><br />
    提交后借阅单将进入“异常处理”，<b>不能直接签为已还</b>，须管理员处理闭环后方可结案。
  </div>
  {#if confirmAnomaly}
    <div class="warnbox">请确认：确实要按异常件登记并转管理员处理？</div>
  {/if}
{/if}

<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  <button class={hasProblem() ? 'danger' : ''} on:click={submit}>
    {hasProblem() ? (confirmAnomaly ? '确认登记异常' : '提交核验（异常件）') : '核验通过，签为已还'}
  </button>
</div>
