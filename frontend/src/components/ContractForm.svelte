<script>
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();

  let code = '', title = '', secrecy = 'INTERNAL', boxLocation = '';
  let pages = 10, stampedPages = '1';
  let error = '';

  function submit() {
    if (!code.trim() || !title.trim()) { error = '编号和名称必填'; return; }
    if (!Number.isFinite(Number(pages)) || Number(pages) <= 0) { error = '页数必须为正整数'; return; }
    dispatch('submit', {
      code: code.trim(), title: title.trim(), secrecy,
      boxLocation: boxLocation.trim(), pages: Number(pages), stampedPages: stampedPages.trim()
    });
  }
</script>

<div class="grid cols2">
  <div class="field"><label>合同编号</label><input bind:value={code} placeholder="HT-2026-0005" /></div>
  <div class="field"><label>密级</label>
    <select bind:value={secrecy}>
      <option value="PUBLIC">公开</option>
      <option value="INTERNAL">内部</option>
      <option value="CONFIDENTIAL">秘密</option>
      <option value="SECRET">机密</option>
    </select>
  </div>
</div>
<div class="field"><label>合同名称</label><input bind:value={title} /></div>
<div class="grid cols2">
  <div class="field"><label>档案盒位</label><input bind:value={boxLocation} placeholder="A-01-03" /></div>
  <div class="field"><label>原件总页数</label><input type="number" min="1" bind:value={pages} /></div>
</div>
<div class="field"><label>盖章页页码（逗号分隔）</label><input bind:value={stampedPages} /></div>
{#if error}<div class="errbox">{error}</div>{/if}
<div class="row" style="justify-content:flex-end">
  <button class="ghost" on:click={() => dispatch('close')}>取消</button>
  <button on:click={submit}>登记合同</button>
</div>
