<script>
  import { onMount } from 'svelte';
  import { api, post } from '../lib/api.js';
  import { users, posts, refreshTick, bump, toast } from '../lib/store.js';
  import Modal from '../lib/Modal.svelte';

  let allUsers = [];
  let contracts = [];
  let rechecks = [];
  let busy = false;

  let transfer = { userId: '', postId: '' };
  let levelForm = { contractId: '', securityLevel: 'CONFIDENTIAL' };
  let decisionTarget = null;
  let decision = { pass: true, note: '' };

  async function load() {
    [allUsers, contracts, rechecks] = await Promise.all([
      $users.length ? $users : api('/users'),
      api('/contracts'),
      api('/rechecks')
    ]);
  }

  const userName = id => {
    const u = allUsers.find(x => x.id === id);
    return u ? u.name : ('#' + id);
  };
  const postName = id => {
    const p = $posts.find(x => x.id === id);
    return p ? p.name : ('#' + id);
  };

  async function doTransfer() {
    if (!transfer.userId || !transfer.postId) return toast.err('请选择员工与目标岗位');
    const u = allUsers.find(x => x.id === Number(transfer.userId));
    const p = $posts.find(x => x.id === Number(transfer.postId));
    busy = true;
    try {
      await post('/admin/transfer', { userId: Number(transfer.userId), postId: Number(transfer.postId) });
      toast.ok(`已将 ${u.name} 调岗至「${p.name}」；其在册借阅已生成权限重新核验`);
      transfer = { userId: '', postId: '' };
      bump();
    } catch (e) { toast.err(e.message); }
    finally { busy = false; }
  }

  async function doLevel() {
    if (!levelForm.contractId) return toast.err('请选择合同');
    busy = true;
    try {
      await post('/admin/contract-level', {
        contractId: Number(levelForm.contractId),
        securityLevel: levelForm.securityLevel
      });
      toast.ok('密级已调整，相关在册借阅已触发权限重新核验');
      levelForm = { contractId: '', securityLevel: 'CONFIDENTIAL' };
      bump();
    } catch (e) { toast.err(e.message); }
    finally { busy = false; }
  }

  async function doDecision() {
    busy = true;
    try {
      await post(`/rechecks/${decisionTarget.id}/decision`, { pass: decision.pass, note: decision.note });
      toast.ok(decision.pass ? '已判定权限通过，借阅继续' : '已判定不通过：借阅中止并发送催还');
      decisionTarget = null;
      bump();
    } catch (e) { toast.err(e.message); }
    finally { busy = false; }
  }

  const pending = () => rechecks.filter(r => r.status === 'PENDING');

  onMount(load);
  $: $refreshTick, load();
</script>

<div class="panel">
  <h2>权限重新核验 <span class="sub">借阅人调岗或合同密级改变后，在册借阅逐单重检；不通过即中止借阅、立即催还</span></h2>
  <div class="grid2">
    <div>
      <h3 class="small" style="margin-bottom:8px">① 员工调岗</h3>
      <label class="field"><span>员工</span>
        <select bind:value={transfer.userId}>
          <option value="">选择员工…</option>
          {#each allUsers.filter(u => u.active) as u}
            <option value={u.id}>{u.name}（{u.post.name}，许可{u.post.clearance}级）</option>
          {/each}
        </select>
      </label>
      <label class="field"><span>调入岗位</span>
        <select bind:value={transfer.postId}>
          <option value="">选择目标岗位…</option>
          {#each $posts as p}
            <option value={p.id}>{p.name}（密级许可 {p.clearance} 级）</option>
          {/each}
        </select>
      </label>
      <button class="btn" disabled={busy} on:click={doTransfer}>执行调岗并重检</button>
    </div>
    <div>
      <h3 class="small" style="margin-bottom:8px">② 合同密级调整</h3>
      <label class="field"><span>合同</span>
        <select bind:value={levelForm.contractId}>
          <option value="">选择合同…</option>
          {#each contracts as c}
            <option value={c.id}>{c.code}《{c.title}》当前：{c.securityLevelLabel}</option>
          {/each}
        </select>
      </label>
      <label class="field"><span>新密级</span>
        <select bind:value={levelForm.securityLevel}>
          <option value="INTERNAL">内部（1 级）</option>
          <option value="CONFIDENTIAL">秘密（2 级）</option>
          <option value="SECRET">机密（3 级）</option>
        </select>
      </label>
      <button class="btn" disabled={busy} on:click={doLevel}>调整密级并重检</button>
    </div>
  </div>
</div>

<div class="panel" style="padding:6px 8px">
  <h2 style="padding:10px 10px 0">重检单（{pending().length} 笔待核验）</h2>
  <table>
    <thead>
      <tr><th>#</th><th>触发原因</th><th>借阅单/合同</th><th>借阅人</th><th>变更说明</th><th>状态</th><th>核验人</th><th>操作</th></tr>
    </thead>
    <tbody>
      {#each rechecks as r}
        <tr>
          <td class="mono small">#{r.id}</td>
          <td><span class="badge orange">{r.reasonLabel}</span></td>
          <td class="small">#{r.loanId}《{r.contractTitle}》</td>
          <td>{r.borrower.name}<div class="muted small">{r.borrower.post.name}（许可{r.borrower.post.clearance}级）</div></td>
          <td class="small" style="max-width:260px">{r.changeDescription}</td>
          <td>
            <span class="badge {r.status === 'PENDING' ? 'orange' : r.status === 'PASSED' ? 'green' : 'red'}">{r.statusLabel}</span>
          </td>
          <td class="small muted">{r.checkedBy || '—'}</td>
          <td>
            {#if r.status === 'PENDING'}
              <button class="btn sm" on:click={() => { decisionTarget = r; decision = { pass: true, note: '' }; }}>出具结论</button>
            {/if}
          </td>
        </tr>
      {/each}
      {#if rechecks.length === 0}
        <tr><td colspan="8" class="muted" style="text-align:center;padding:24px">暂无重检记录</td></tr>
      {/if}
    </tbody>
  </table>
</div>

{#if decisionTarget}
  <Modal title={`权限重检结论 #${decisionTarget.id}`} on:close={() => decisionTarget = null}>
    <div class="alert info small">{decisionTarget.changeDescription}
      <br />借阅人当前岗位许可 <b>{decisionTarget.borrower.post.clearance} 级</b>，
      借阅单 #{decisionTarget.loanId}。
    </div>
    <div class="row" style="margin:10px 0">
      <label class="row" style="gap:6px"><input type="radio" bind:group={decision.pass} value={true} /> 权限通过，借阅继续</label>
      <label class="row" style="gap:6px"><input type="radio" bind:group={decision.pass} value={false} /> 权限不符，中止借阅并催还</label>
    </div>
    <label class="field"><span>核验备注</span>
      <textarea bind:value={decision.note} placeholder="核验依据与处理说明"></textarea>
    </label>
    <div class="alert warn small">注：若当前真实密级仍不满足，即使选择“通过”系统也会拒绝，防止人工误放。</div>
    <div class="row" style="justify-content:flex-end">
      <button class="btn ghost" on:click={() => decisionTarget = null}>取消</button>
      <button class="btn" disabled={busy} on:click={doDecision}>提交结论</button>
    </div>
  </Modal>
{/if}
