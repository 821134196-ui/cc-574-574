<script>
  import { onMount, onDestroy } from 'svelte';
  import { api, setCurrentUserId, getCurrentUserId } from './api.js';
  import { ok, fail, ROLE_LABEL, SECRECY_LABEL } from './stores.js';

  import Toast from './components/Toast.svelte';
  import Modal from './components/Modal.svelte';
  import BorrowView from './components/BorrowView.svelte';
  import ApprovalView from './components/ApprovalView.svelte';
  import ArchivistView from './components/ArchivistView.svelte';
  import AdminOverview from './components/AdminOverview.svelte';
  import ContractsView from './components/ContractsView.svelte';
  import EmployeesView from './components/EmployeesView.svelte';
  import AuditView from './components/AuditView.svelte';
  import LoanDetail from './components/LoanDetail.svelte';
  import ApplyForm from './components/ApplyForm.svelte';
  import ApproveForm from './components/ApproveForm.svelte';
  import IssueForm from './components/IssueForm.svelte';
  import ReturnCheckForm from './components/ReturnCheckForm.svelte';
  import RenewForm from './components/RenewForm.svelte';
  import AnomalyForm from './components/AnomalyForm.svelte';
  import TransferForm from './components/TransferForm.svelte';
  import ContractForm from './components/ContractForm.svelte';

  let employees = [];
  let contracts = [];
  let loans = [];
  let custody = [];
  let anomalies = [];
  let reminders = [];
  let trail = [];

  let currentUserId = getCurrentUserId();
  let me = null;
  let tab = 'loans';
  let modal = null; // {type, data}
  let timer;

  const tabsFor = (role) => {
    const base = [{ key: 'loans', label: '借阅' }, { key: 'contracts', label: '合同档案' }];
    if (role === 'ADMIN') {
      base.push({ key: 'approval', label: '审批' });
      base.push({ key: 'overview', label: '保管总览' });
      base.push({ key: 'employees', label: '员工权限' });
      base.push({ key: 'audit', label: '审计追溯' });
    }
    if (role === 'ARCHIVIST') {
      base.splice(1, 0, { key: 'archivist', label: '档案台' });
      base.push({ key: 'audit', label: '审计追溯' });
    }
    if (role === 'EMPLOYEE') {
      // 仅借阅
    }
    return base;
  };
  let visibleTabs = [];

  onMount(async () => {
    await loadEmployees();
    if (currentUserId) await selectUser(currentUserId, true);
    timer = setInterval(refreshReminders, 30000);
  });
  onDestroy(() => clearInterval(timer));

  async function loadEmployees() {
    employees = await api.get('/api/employees').catch(() => []);
    // 未登录时该接口 401，使用宽松引导
  }

  async function selectUser(id, silent) {
    currentUserId = String(id);
    setCurrentUserId(id);
    try {
      const all = await api.get('/api/employees');
      employees = all;
      me = all.find((e) => String(e.id) === String(id));
      visibleTabs = tabsFor(me.role);
      await switchTab('loans');
      if (!silent) ok(`已切换为 ${me.name}（${ROLE_LABEL[me.role]}）`);
    } catch (e) {
      me = null;
      fail(e.message);
    }
  }

  async function refreshAll() {
    if (!me) return;
    try {
      const [cs, ls, rs] = await Promise.all([
        api.get('/api/contracts'),
        api.get('/api/loans'),
        api.get('/api/reminders').catch(() => [])
      ]);
      contracts = cs; loans = ls; reminders = rs;
      if (me.role === 'ADMIN') {
        custody = await api.get('/api/loans/custody/overview');
        anomalies = await api.get('/api/loans/anomalies');
      }
      if (me.role !== 'EMPLOYEE' && (tab === 'audit' || modal?.type === 'detail')) {
        trail = await api.get('/api/loans/audit/trail');
      }
    } catch (e) {
      fail(e.message);
    }
  }

  async function refreshReminders() {
    if (!me) return;
    reminders = await api.get('/api/reminders').catch(() => reminders);
  }

  async function switchTab(t) {
    tab = t;
    if (t === 'audit') {
      trail = await api.get('/api/loans/audit/trail').catch(() => trail);
    }
    if (t === 'overview' && me.role === 'ADMIN') {
      custody = await api.get('/api/loans/custody/overview').catch(() => custody);
      anomalies = await api.get('/api/loans/anomalies').catch(() => anomalies);
    }
  }

  function logout() {
    me = null;
    currentUserId = '';
    setCurrentUserId('');
  }

  const unack = () => reminders.filter((r) => !r.acknowledged).length;

  // ---------- 操作处理 ----------

  async function handleApply(payload) {
    try {
      await api.post('/api/loans', payload);
      ok('借阅申请已提交，等待管理员审批');
      modal = null;
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleApprove(payload) {
    try {
      await api.post(`/api/loans/${modal.data.id}/approve`, payload);
      ok(payload.approved ? '已批准，待档案员出库' : '已驳回');
      modal = null;
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleIssue(payload) {
    try {
      await api.post(`/api/loans/${modal.data.id}/issue`, payload);
      ok(`已出库：经手人 ${payload.handler}，交接与门禁已记录`);
      modal = null;
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleReturn(payload) {
    try {
      await api.post(`/api/loans/${modal.data.id}/return`, payload);
      ok('核验通过，原件已签为已还并归位');
      modal = null;
      await refreshAll();
    } catch (e) {
      if (e.status === 409) {
        fail(e.message); // 缺页/损坏 -> 已进入异常处理
        modal = null;
        await refreshAll();
      } else {
        fail(e.message);
      }
    }
  }

  async function handleRenew(payload) {
    try {
      await api.post(`/api/loans/${modal.data.id}/renewals`, payload);
      ok('续借申请已提交，等待审批');
      modal = null;
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleReverify(loan) {
    try {
      await api.post(`/api/loans/${loan.id}/reverify`);
      ok('权限重新核验通过');
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleResolve(payload) {
    try {
      await api.post(`/api/loans/anomalies/${payload.id}/resolve`, {
        resolution: payload.resolution, completeReturn: payload.completeReturn
      });
      ok(payload.completeReturn ? '异常已闭环并完成归还结案' : '异常已闭环');
      modal = null;
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleTransfer(payload) {
    try {
      await api.put(`/api/employees/${modal.data.id}/post`, payload);
      ok('调岗/权限已保存，在途借阅将要求重新核验');
      modal = null;
      await loadEmployees();
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleCreateContract(payload) {
    try {
      await api.post('/api/contracts', payload);
      ok('合同已登记');
      modal = null;
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleChangeSecrecy(payload) {
    try {
      await api.put(`/api/contracts/${payload.contract.id}/secrecy`, { secrecy: payload.secrecy });
      ok(`合同密级已变更为 ${SECRECY_LABEL[payload.secrecy]}，在途借阅需重新核验`);
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function handleUploadScan(payload) {
    try {
      await api.post(`/api/contracts/${payload.contract.id}/scan-content`, {
        fileName: payload.fileName, contentBase64: payload.contentBase64
      });
      ok('扫描件已保存到本地目录');
      await refreshAll();
    } catch (e) { fail(e.message); }
  }

  async function ackReminder(id) {
    try {
      await api.post(`/api/reminders/${id}/ack`);
      await refreshReminders();
    } catch (e) { fail(e.message); }
  }

  function openLoan(id) {
    const loan = loans.find((l) => l.id === id);
    if (loan) modal = { type: 'detail', data: loan };
  }

  const onCard = (name, loan) => {
    if (name === 'detail') modal = { type: 'detail', data: loan };
    else if (name === 'approve') modal = { type: 'approve', data: loan };
    else if (name === 'issue') modal = { type: 'issue', data: loan };
    else if (name === 'return') modal = { type: 'return', data: loan };
    else if (name === 'renew') modal = { type: 'renew', data: loan };
    else if (name === 'reverify') handleReverify(loan);
  };
</script>

<Toast />

<header class="topbar">
  <h1>📋 企业合同原件借阅与归还核验系统</h1>
  <div class="spacer"></div>
  {#if me}
    {#if unack() > 0 && me.role === 'ADMIN'}
      <button class="ghost sm bell" on:click={() => tab = 'overview'}>
        🔔 提醒 {unack()}
      </button>
    {/if}
    <span class="small">当前：{me.name} · {ROLE_LABEL[me.role]} · {SECRECY_LABEL[me.clearance]}</span>
    <select value={currentUserId} on:change={(e) => selectUser(e.target.value)}>
      {#each employees as e}
        <option value={e.id}>{e.employeeNo} {e.name}（{ROLE_LABEL[e.role]}）</option>
      {/each}
    </select>
    <button class="ghost sm" on:click={logout}>退出</button>
  {/if}
</header>

{#if !me}
  <main>
    <div class="panel" style="max-width:520px;margin:40px auto">
      <h2>选择身份登录（演示环境）</h2>
      <p class="muted small">系统通过 X-User-Id 识别操作人；生产环境应接入统一身份认证。</p>
      <select bind:value={currentUserId} style="width:100%">
        <option value="">— 请选择员工 —</option>
        {#each employees as e}
          <option value={e.id}>{e.employeeNo} {e.name} · {e.post}（{ROLE_LABEL[e.role]}）</option>
        {/each}
      </select>
      <div class="mt16" style="text-align:right">
        <button disabled={!currentUserId} on:click={() => selectUser(currentUserId)}>进入系统</button>
      </div>
    </div>
  </main>
{:else}
  <nav class="tabs">
    {#each visibleTabs as t}
      <button class={tab === t.key ? 'active' : ''} on:click={() => switchTab(t.key)}>{t.label}</button>
    {/each}
    <span class="spacer"></span>
  </nav>

  <main>
    {#if tab === 'loans'}
      <BorrowView {loans} {me} {contracts}
        on:apply={() => modal = { type: 'apply' }}
        on:detail={(e) => onCard('detail', e.detail)}
        on:renew={(e) => onCard('renew', e.detail)} />
    {:else if tab === 'approval'}
      <ApprovalView {loans} {me}
        on:detail={(e) => onCard('detail', e.detail)}
        on:approve={(e) => onCard('approve', e.detail)} />
    {:else if tab === 'archivist'}
      <ArchivistView {loans} {me}
        on:detail={(e) => onCard('detail', e.detail)}
        on:issue={(e) => onCard('issue', e.detail)}
        on:return={(e) => onCard('return', e.detail)} />
    {:else if tab === 'overview'}
      <AdminOverview {custody} {anomalies}
        on:detail={(e) => openLoan(e.detail.id)}
        on:resolve={(e) => modal = { type: 'anomaly', data: e.detail }} />
      {#if reminders.length > 0}
        <div class="panel">
          <h2>提醒与跟进（本地模拟）</h2>
          <table>
            <tr><th>类型</th><th>内容</th><th>到期日</th><th>时间</th><th>状态</th><th></th></tr>
            {#each reminders as r}
              <tr>
                <td>{r.kind === 'OVERDUE' ? '逾期' : r.kind === 'DUE_SOON' ? '临期' : '跟进'}</td>
                <td>{r.message}</td>
                <td>{r.dueDate}</td>
                <td class="small">{new Date(r.createdAt).toLocaleString('zh-CN', { hour12: false })}</td>
                <td>{r.acknowledged ? '已知悉' : '未处理'}</td>
                <td>{#if !r.acknowledged}<button class="ghost sm" on:click={() => ackReminder(r.id)}>知悉</button>{/if}</td>
              </tr>
            {/each}
          </table>
        </div>
      {/if}
    {:else if tab === 'contracts'}
      <ContractsView {contracts} {me}
        on:createContract={() => modal = { type: 'contract' }}
        on:changeSecrecy={(e) => handleChangeSecrecy(e.detail)}
        on:uploadScan={(e) => handleUploadScan(e.detail)} />
    {:else if tab === 'employees'}
      <EmployeesView {employees} {me}
        on:transfer={(e) => modal = { type: 'transfer', data: e.detail }} />
    {:else if tab === 'audit'}
      <AuditView {trail} />
    {/if}
  </main>
{/if}

{#if modal}
  {#if modal.type === 'apply'}
    <Modal title="申请借阅合同原件" on:close={() => modal = null}>
      <ApplyForm {contracts} {me} on:close={() => modal = null} on:submit={(e) => handleApply(e.detail)} />
    </Modal>
  {:else if modal.type === 'approve'}
    <Modal title="借阅审批" on:close={() => modal = null}>
      <ApproveForm loan={modal.data} {me} on:close={() => modal = null} on:submit={(e) => handleApprove(e.detail)} />
    </Modal>
  {:else if modal.type === 'issue'}
    <Modal title="档案员出库登记" on:close={() => modal = null}>
      <IssueForm loan={modal.data} on:close={() => modal = null} on:submit={(e) => handleIssue(e.detail)} />
    </Modal>
  {:else if modal.type === 'return'}
    <Modal title="归还逐页核验" on:close={() => modal = null}>
      <ReturnCheckForm loan={modal.data} on:close={() => modal = null} on:submit={(e) => handleReturn(e.detail)} />
    </Modal>
  {:else if modal.type === 'renew'}
    <Modal title="申请续借" on:close={() => modal = null}>
      <RenewForm loan={modal.data} on:close={() => modal = null} on:submit={(e) => handleRenew(e.detail)} />
    </Modal>
  {:else if modal.type === 'anomaly'}
    <Modal title="异常处理闭环" on:close={() => modal = null}>
      <AnomalyForm anomaly={modal.data} on:close={() => modal = null} on:submit={(e) => handleResolve(e.detail)} />
    </Modal>
  {:else if modal.type === 'transfer'}
    <Modal title="员工调岗 / 权限变更" on:close={() => modal = null}>
      <TransferForm employee={modal.data} on:close={() => modal = null} on:submit={(e) => handleTransfer(e.detail)} />
    </Modal>
  {:else if modal.type === 'contract'}
    <Modal title="登记合同原件" on:close={() => modal = null}>
      <ContractForm on:close={() => modal = null} on:submit={(e) => handleCreateContract(e.detail)} />
    </Modal>
  {:else if modal.type === 'detail'}
    <Modal title={`借阅单 #${modal.data.id} · ${modal.data.contractCode}`} on:close={() => modal = null}>
      <LoanDetail loan={modal.data} {me}
        on:approve={(e) => modal = { type: 'approve', data: e.detail }}
        on:issue={(e) => modal = { type: 'issue', data: e.detail }}
        on:return={(e) => modal = { type: 'return', data: e.detail }}
        on:renew={(e) => modal = { type: 'renew', data: e.detail }}
        on:reverify={(e) => handleReverify(e.detail)} />
    </Modal>
  {/if}
{/if}
