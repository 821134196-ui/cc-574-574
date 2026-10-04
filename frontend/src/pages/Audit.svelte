<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { currentUser } from '../lib/store.js';

  let logs = [];
  let entityType = '';
  let loanId = '';
  let me = $currentUser;

  async function load() {
    let path = '/audit?limit=300';
    if (entityType) path += '&entityType=' + encodeURIComponent(entityType);
    if (loanId) path += '&loanId=' + encodeURIComponent(loanId);
    logs = await api(path);
  }

  function actionClass(a) {
    if (/拒绝|不通过|中止|异常|逾期|缺页|损坏|催还/.test(a.action)) return 'red';
    if (/通过|归还|入库|闭环/.test(a.action)) return 'green';
    if (/申请|交接|续借|调整|调岗|上传/.test(a.action)) return 'blue';
    return 'gray';
  }

  onMount(load);
</script>

<div class="panel">
  <h2>顺序追查审计 <span class="sub">申请→审批→交接→续借→核验→异常处理→权限重检的全部操作只追加、不可改，按序号顺序排列</span></h2>
  <div class="row">
    <label class="field" style="margin:0;min-width:200px"><span>实体类型</span>
      <select bind:value={entityType}>
        <option value="">全部</option>
        <option value="Loan">借阅单</option>
        <option value="Anomaly">异常单</option>
        <option value="Contract">合同</option>
        <option value="PermissionRecheck">权限重检</option>
        <option value="RenewalRequest">续借</option>
      </select>
    </label>
    <label class="field" style="margin:0;width:160px"><span>按借阅单 # 过滤</span>
      <input type="number" bind:value={loanId} placeholder="如 1" />
    </label>
    <button class="btn" on:click={load}>查询</button>
  </div>
</div>

<div class="panel">
  <ul class="timeline">
    {#each logs as a}
      <li>
        <div class="ts">#{a.id} · {a.ts} · <b>{a.actor}</b>
          {#if a.stateChange}<span class="sc">{a.stateChange}</span>{/if}
          <span class="badge {actionClass(a)}" style="margin-left:6px">{a.action}</span>
        </div>
        <div class="dt">
          {#if a.entityType}<span class="muted">[{a.entityType}{#if a.entityId} #{a.entityId}{/if}]</span> {/if}
          {a.detail || ''}
        </div>
      </li>
    {/each}
    {#if logs.length === 0}
      <li><div class="muted">暂无日志</div></li>
    {/if}
  </ul>
</div>
