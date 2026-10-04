<script>
  import { onMount } from 'svelte';
  import { api, post, viewScan, uploadScan, daysUntil } from '../lib/api.js';
  import { currentUser, posts, refreshTick, bump, toast } from '../lib/store.js';
  import StatusBadge from '../lib/StatusBadge.svelte';
  import Modal from '../lib/Modal.svelte';

  let contracts = [];
  let q = '';
  let me = $currentUser;

  let showCreate = false;
  let uploadTarget = null;
  let grantTarget = null;
  let applyTarget = null;

  // 表单
  let f = { code: '', title: '', counterparty: '', securityLevel: 'INTERNAL', boxLocation: '', totalPages: 5, sealPages: '1,5', scanGrantedPostId: '' };
  let uploadFile = null;
  let grantPostId = '';
  let applyForm = { purpose: '', days: 7 };
  let busy = false;

  async function load() {
    me = $currentUser;
    contracts = await api('/contracts?q=' + encodeURIComponent(q));
  }

  function levelClass(lv) {
    return { INTERNAL: 'gray', CONFIDENTIAL: 'orange', SECRET: 'red' }[lv] || 'gray';
  }
  function levelLabel(lv) {
    return { INTERNAL: '内部', CONFIDENTIAL: '秘密', SECRET: '机密' }[lv] || lv;
  }

  async function doCreate() {
    busy = true;
    try {
      await post('/contracts', { ...f, scanGrantedPostId: f.scanGrantedPostId ? Number(f.scanGrantedPostId) : null });
      toast.ok('合同档案已建立');
      showCreate = false;
      bump();
    } catch (e) { toast.err(e.message); } finally { busy = false; }
  }

  async function doUpload() {
    if (!uploadFile) return toast.err('请选择扫描件文件');
    busy = true;
    try {
      await uploadScan(uploadTarget.id, uploadFile);
      toast.ok('扫描件已存入本地目录');
      uploadTarget = null;
      bump();
    } catch (e) { toast.err(e.message); } finally { busy = false; }
  }

  async function doGrant() {
    busy = true;
    try {
      await post(`/contracts/${grantTarget.id}/scan-grant`, { postId: grantPostId === '' ? 0 : Number(grantPostId) });
      toast.ok('扫描件查看授权已更新');
      grantTarget = null;
      bump();
    } catch (e) { toast.err(e.message); } finally { busy = false; }
  }

  async function doApply() {
    busy = true;
    try {
      await post('/loans', { contractId: applyTarget.id, purpose: applyForm.purpose, days: Number(applyForm.days) });
      toast.ok('借阅申请已提交，等待管理人员审批');
      applyTarget = null;
      applyForm = { purpose: '', days: 7 };
      bump();
    } catch (e) { toast.err(e.message); } finally { busy = false; }
  }

  async function openScan(c) {
    try { await viewScan(c.id); } catch (e) { toast.err(e.message); }
  }

  $: if ($currentUser) me = $currentUser;
  onMount(load);
  $: $refreshTick, load();
</script>

<div class="panel">
  <h2>合同台账 <span class="sub">纸质原件档案：盒位、密级、当前保管人与应归还日期</span></h2>
  <div class="row" style="justify-content:space-between">
    <div class="row">
      <input type="text" placeholder="按编号 / 名称搜索" bind:value={q} on:keyup|preventDefault
             style="width:260px" />
      <button class="btn ghost" on:click={load}>搜索</button>
    </div>
    {#if me && me.role !== 'EMPLOYEE'}
      <button class="btn" on:click={() => { f = { code: '', title: '', counterparty: '', securityLevel: 'INTERNAL', boxLocation: '', totalPages: 5, sealPages: '1,5', scanGrantedPostId: '' }; showCreate = true; }}>+ 新建合同档案</button>
    {/if}
  </div>
</div>

<div class="panel" style="padding:6px 8px">
  <table>
    <thead>
      <tr>
        <th>编号 / 名称</th><th>相对方</th><th>密级</th><th>盒位</th><th>页数/盖章页</th>
        <th>当前保管状态</th><th>扫描件</th><th>操作</th>
      </tr>
    </thead>
    <tbody>
      {#each contracts as c}
        <tr>
          <td>
            <div><b>{c.title}</b></div>
            <div class="muted small mono">{c.code}</div>
          </td>
          <td class="small">{c.counterparty}</td>
          <td><span class="badge {levelClass(c.securityLevel)}">{levelLabel(c.securityLevel)}</span></td>
          <td class="mono small">{c.boxLocation}</td>
          <td class="small">{c.totalPages} 页<span class="seal-tag">盖章页 {c.sealPages}</span></td>
          <td>
            {#if c.holder}
              <div><b>{c.holder.name}</b></div>
              <div class="small"><StatusBadge status={c.holderStatus} /></div>
              <div class="small {daysUntil(c.dueDate) < 0 ? '' : 'muted'}">
                应还：<b class:red={daysUntil(c.dueDate) < 0}>{c.dueDate}</b>
                {#if daysUntil(c.dueDate) < 0}<span class="badge red">逾期 {-daysUntil(c.dueDate)} 天</span>{/if}
              </div>
              {#if c.anomalyOpen}<div class="small"><span class="badge red">异常处理中</span></div>{/if}
              {#if c.recheckPending}<div class="small"><span class="badge orange">权限重检中</span></div>{/if}
            {:else}
              <span class="badge green">在库 {c.boxLocation}</span>
            {/if}
          </td>
          <td>
            {#if c.hasScan}
              <div class="small">授权岗位：<b>{c.scanGrantedPost ? c.scanGrantedPost.name : '无'}</b></div>
              {#if c.scanAccessible}
                <button class="btn sm ghost" on:click={() => openScan(c)}>查看扫描件</button>
              {:else}
                <span class="badge gray">🔒 岗位未授权</span>
              {/if}
            {:else}
              <span class="muted small">未上传</span>
            {/if}
          </td>
          <td>
            <div class="row" style="gap:6px">
              {#if me && me.role === 'EMPLOYEE'}
                <button class="btn sm" class:disabled={!!c.holder || me.post.clearance < c.clearanceRequired}
                        on:click={() => { applyTarget = c; applyForm = { purpose: '', days: 7 }; }}>
                  {me.post.clearance < c.clearanceRequired ? '密级不足' : c.holder ? '原件借出中' : '申请借阅'}
                </button>
              {/if}
              {#if me && me.role !== 'EMPLOYEE'}
                <button class="btn sm ghost" on:click={() => { uploadTarget = c; uploadFile = null; }}>上传扫描</button>
                <button class="btn sm ghost" on:click={() => { grantTarget = c; grantPostId = c.scanGrantedPost ? c.scanGrantedPost.id : ''; }}>授权岗位</button>
              {/if}
            </div>
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

{#if showCreate}
  <Modal title="新建合同档案" on:close={() => showCreate = false}>
    <div class="grid2">
      <label class="field"><span>合同编号</span><input type="text" bind:value={f.code} placeholder="HT-2026-005" /></label>
      <label class="field"><span>盒位</span><input type="text" bind:value={f.boxLocation} placeholder="B-1-08" /></label>
    </div>
    <label class="field"><span>合同名称</span><input type="text" bind:value={f.title} /></label>
    <div class="grid2">
      <label class="field"><span>相对方</span><input type="text" bind:value={f.counterparty} /></label>
      <label class="field"><span>密级</span>
        <select bind:value={f.securityLevel}>
          <option value="INTERNAL">内部（1 级）</option>
          <option value="CONFIDENTIAL">秘密（2 级）</option>
          <option value="SECRET">机密（3 级）</option>
        </select>
      </label>
    </div>
    <div class="grid2">
      <label class="field"><span>总页数（归还逐页核对基准）</span><input type="number" bind:value={f.totalPages} min="1" /></label>
      <label class="field"><span>盖章页（逗号分隔）</span><input type="text" bind:value={f.sealPages} placeholder="1,8" /></label>
    </div>
    <label class="field"><span>扫描件授权查看岗位（可选）</span>
      <select bind:value={f.scanGrantedPostId}>
        <option value="">不授权</option>
        {#each $posts as p}<option value={p.id}>{p.name}（许可 {p.clearance} 级）</option>{/each}
      </select>
    </label>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => showCreate = false}>取消</button>
      <button class="btn" disabled={busy} on:click={doCreate}>建档</button>
    </div>
  </Modal>
{/if}

{#if uploadTarget}
  <Modal title={`上传扫描件 · ${uploadTarget.code}`} on:close={() => uploadTarget = null}>
    <p class="muted small">扫描件仅保存在服务器本地目录（data/scans），不落数据库。查看时按授权岗位校验。</p>
    <input type="file" accept=".pdf,.png,.jpg,.jpeg" on:change={e => uploadFile = e.target.files[0]} />
    <div class="row" style="justify-content:flex-end;margin-top:14px">
      <button class="btn ghost" on:click={() => uploadTarget = null}>取消</button>
      <button class="btn" disabled={busy} on:click={doUpload}>上传</button>
    </div>
  </Modal>
{/if}

{#if grantTarget}
  <Modal title={`扫描件查看授权 · ${grantTarget.code}`} on:close={() => grantTarget = null}>
    <p class="small">仅被授权<b>岗位</b>的员工可查看扫描件（按当前岗位判定）。当前：
      <b>{grantTarget.scanGrantedPost ? grantTarget.scanGrantedPost.name : '无（任何人不可查看）'}</b></p>
    <select bind:value={grantPostId}>
      <option value="">取消全部授权</option>
      {#each $posts as p}<option value={p.id}>{p.name}（许可 {p.clearance} 级）</option>{/each}
    </select>
    <div class="row" style="justify-content:flex-end;margin-top:14px">
      <button class="btn ghost" on:click={() => grantTarget = null}>取消</button>
      <button class="btn" disabled={busy} on:click={doGrant}>保存授权</button>
    </div>
  </Modal>
{/if}

{#if applyTarget}
  <Modal title={`申请借阅 · ${applyTarget.code} ${applyTarget.title}`} on:close={() => applyTarget = null}>
    <div class="kvline">
      <span><b>密级</b>{levelLabel(applyTarget.securityLevel)}</span>
      <span><b>盒位</b>{applyTarget.boxLocation}</span>
      <span><b>总页数</b>{applyTarget.totalPages}（盖章页 {applyTarget.sealPages}）</span>
    </div>
    <label class="field" style="margin-top:10px"><span>借阅用途（必填，供审批与审计）</span>
      <textarea bind:value={applyForm.purpose} placeholder="如：履约谈判需核对原件条款，用于与对方现场会议"></textarea>
    </label>
    <label class="field"><span>借阅天数（1~15 天，到期日前可申请续借）</span>
      <input type="number" min="1" max="15" bind:value={applyForm.days} />
    </label>
    <div class="alert info small">审批通过后由法务档案员从盒位取出原件并登记交接，交接时记录实际经手人。</div>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => applyTarget = null}>取消</button>
      <button class="btn" disabled={busy} on:click={doApply}>提交申请</button>
    </div>
  </Modal>
{/if}
