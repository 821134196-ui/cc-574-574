<script>
  import { onMount } from 'svelte';
  import { api, post } from '../lib/api.js';
  import { currentUser, refreshTick, bump, toast } from '../lib/store.js';
  import Modal from '../lib/Modal.svelte';

  let me = $currentUser;
  let filter = 'OPEN';
  let anomalies = [];
  let resolveTarget = null;
  let resolution = '';
  let busy = false;

  async function load() {
    me = $currentUser;
    anomalies = await api('/anomalies' + (filter ? '?status=' + filter : ''));
  }

  async function doResolve() {
    busy = true;
    try {
      await post(`/anomalies/${resolveTarget.id}/resolve`, { resolution });
      toast.ok('异常已闭环；档案员可重新逐页核验，完好方可签还');
      resolveTarget = null;
      bump();
    } catch (e) { toast.err(e.message); }
    finally { busy = false; }
  }

  onMount(load);
  $: $refreshTick, load();
</script>

<div class="panel">
  <h2>归还异常处理 <span class="sub">缺页/损坏原件暂缓入库，必须逐单闭环；未闭环不能签为已还</span></h2>
  <div class="row">
    <button class="btn sm ghost" class:ghost={filter !== 'OPEN'} on:click={() => { filter = 'OPEN'; load(); }}>待处理</button>
    <button class="btn sm ghost" class:ghost={filter !== 'RESOLVED'} on:click={() => { filter = 'RESOLVED'; load(); }}>已处理</button>
    <button class="btn sm ghost" on:click={() => { filter = ''; load(); }}>全部</button>
  </div>
</div>

<div class="panel" style="padding:6px 8px">
  <table>
    <thead>
      <tr><th>#</th><th>借阅单/合同</th><th>借阅人</th><th>问题</th><th>描述</th><th>报告人/时间</th><th>处理结论</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each anomalies as a}
        <tr>
          <td class="mono small">#{a.id}</td>
          <td><b>#{a.loanId}</b>《{a.contractTitle}》<div class="muted small mono">{#if a.contractId}合同#{a.contractId}{/if}</div></td>
          <td>{a.borrower.name}<div class="muted small">{a.borrower.post.name}</div></td>
          <td>
            <span class="badge {a.issueType === 'MISSING' ? 'red' : 'orange'}">
              {a.issueTypeLabel}{a.pageNo ? ' 第' + a.pageNo + '页' : ''}
            </span>
          </td>
          <td class="small" style="max-width:240px">{a.description}</td>
          <td class="small muted">{a.reportedBy}<br />{a.reportedAt}</td>
          <td class="small">
            {#if a.status === 'RESOLVED'}
              <div>{a.resolution}</div>
              <div class="muted">{a.handledBy} · {a.handledAt}</div>
            {:else}<span class="badge orange">待处理</span>{/if}
          </td>
          <td>
            {#if a.status !== 'RESOLVED' && me.role === 'ADMIN'}
              <button class="btn sm" on:click={() => { resolveTarget = a; resolution = ''; }}>处理闭环</button>
            {:else if a.status !== 'RESOLVED'}
              <span class="muted small">等待管理人员处理</span>
            {/if}
          </td>
        </tr>
      {/each}
      {#if anomalies.length === 0}
        <tr><td colspan="8" class="muted" style="text-align:center;padding:24px">没有相关异常单</td></tr>
      {/if}
    </tbody>
  </table>
</div>

{#if resolveTarget}
  <Modal title={`处理归还异常 #${resolveTarget.id}`} on:close={() => resolveTarget = null}>
    <div class="alert err small">
      {resolveTarget.issueTypeLabel}（{resolveTarget.description}）。处理完成并由档案员重新逐页核验完好后，借阅单才可签为已还。
    </div>
    <label class="field"><span>处理结论（补回 / 修复 / 追责 / 情况说明）</span>
      <textarea bind:value={resolution} placeholder="如：第 5 页附件已由借阅人找回补交，经核对与原件一致，重新归档" />
    </label>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => resolveTarget = null}>取消</button>
      <button class="btn" disabled={busy} on:click={doResolve}>闭环异常</button>
    </div>
  </Modal>
{/if}
