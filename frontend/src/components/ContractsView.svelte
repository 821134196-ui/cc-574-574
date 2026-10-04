<script>
  import { createEventDispatcher } from 'svelte';
  import { fetchScanBlob } from '../api.js';
  import { SECRECY_LABEL } from '../stores.js';

  export let contracts = [];
  export let me;
  const dispatch = createEventDispatcher();

  let busy = null;
  let scanMsg = {};

  async function viewScan(c) {
    busy = c.id;
    scanMsg = {};
    try {
      const blob = await fetchScanBlob(c.id);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (e) {
      scanMsg[c.id] = e.message;
    } finally {
      busy = null;
    }
  }

  function uploadScan(c) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.pdf,.png,.jpg,.jpeg';
    input.onchange = async () => {
      const file = input.files[0];
      if (!file) return;
      const buf = await file.arrayBuffer();
      let binary = '';
      const bytes = new Uint8Array(buf);
      const chunk = 0x8000;
      for (let i = 0; i < bytes.length; i += chunk) {
        binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
      }
      const contentBase64 = btoa(binary);
      dispatch('uploadScan', { contract: c, fileName: file.name, contentBase64 });
    };
    input.click();
  }
</script>

<div class="panel">
  <div class="row" style="justify-content:space-between">
    <h2 style="margin:0">合同原件档案</h2>
    {#if me.role === 'ADMIN'}<button on:click={() => dispatch('createContract')}>＋ 登记合同</button>{/if}
  </div>
  <p class="muted small mt8" style="margin-bottom:0">
    扫描件仅对<b>获授权岗位</b>且密级匹配者开放；原件页数与盖章页是归还逐页核验基准。
  </p>
</div>

<div class="panel" style="padding:0;overflow-x:auto">
  <table>
    <tr>
      <th>编号</th><th>名称</th><th>密级</th><th>盒位</th><th>页数</th>
      <th>盖章页</th><th>扫描件</th><th>操作</th>
    </tr>
    {#each contracts as c}
      <tr>
        <td><b>{c.code}</b></td>
        <td>{c.title}</td>
        <td>
          {SECRECY_LABEL[c.secrecy]}
          {#if me.role === 'ADMIN'}
            <select class="sm" value={c.secrecy}
              on:change={(e) => dispatch('changeSecrecy', { contract: c, secrecy: e.target.value })}>
              {#each Object.keys(SECRECY_LABEL) as k}
                <option value={k}>{SECRECY_LABEL[k]}</option>
              {/each}
            </select>
          {/if}
        </td>
        <td>{c.boxLocation || '—'}</td>
        <td>{c.pages}</td>
        <td>{c.stampedPages || '—'}</td>
        <td>{c.scanFile ? '已上传' : '无'}</td>
        <td>
          <div class="row" style="gap:6px">
            <button class="ghost sm" disabled={busy === c.id || !c.scanFile} on:click={() => viewScan(c)}>
              {busy === c.id ? '校验中…' : '查看扫描件'}
            </button>
            {#if me.role === 'ADMIN'}
              <button class="ghost sm" on:click={() => uploadScan(c)}>上传</button>
            {/if}
          </div>
          {#if scanMsg[c.id]}<div class="errbox small">{scanMsg[c.id]}</div>{/if}
        </td>
      </tr>
    {/each}
  </table>
</div>
