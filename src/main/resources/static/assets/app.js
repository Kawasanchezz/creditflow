'use strict';

const state = {
  auth: null, me: null, tab: 'sim',
  simInput: { system: 'PRICE', amount: '10000', rate: '1.99', term: '12' },
  sim: null, proposals: [], selectedId: null, detail: null, keys: {}, demo: false, session: 0
};

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const money = v => brl.format(Number(v));
const pct = (v, d = 2) => (Number(v) * 100).toLocaleString('pt-BR', { maximumFractionDigits: d }) + '%';
const dateBR = s => new Date(s + 'T00:00:00Z').toLocaleDateString('pt-BR', { timeZone: 'UTC' });
const dateTimeBR = s => new Date(s).toLocaleString('pt-BR');

function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) {
    if (kid === null || kid === undefined || kid === false) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const SVG_NS = 'http://www.w3.org/2000/svg';
function s(tag, attrs, ...kids) {
  const el = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, v);
  kids.forEach(k => el.append(k));
  return el;
}

function basic(user, pass) {
  const bytes = new TextEncoder().encode(user + ':' + pass);
  return 'Basic ' + btoa(String.fromCharCode(...bytes));
}

async function api(path, opts = {}) {
  const res = await fetch(path, {
    method: opts.method || 'GET',
    credentials: 'omit',
    headers: {
      ...(state.auth ? { Authorization: state.auth } : {}),
      ...(opts.body ? { 'Content-Type': 'application/json' } : {}),
      ...(opts.headers || {})
    },
    body: opts.body ? JSON.stringify(opts.body) : undefined
  });
  if (res.status === 401 && state.auth) {
    logout();
    throw new Error('Sessão expirada. Entre novamente.');
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
  if (!res.ok) throw new Error((data && (data.detail || data.message)) || 'Erro ' + res.status);
  return data;
}

function toast(msg, err) {
  const t = h('div', { class: 'toast' + (err ? ' err' : ''), role: 'status', text: msg });
  document.body.append(t);
  setTimeout(() => t.remove(), 4200);
}

function logout() {
  state.session++;
  state.auth = null; state.me = null; state.sim = null; state.proposals = [];
  state.detail = null; state.selectedId = null; state.keys = {};
  render();
}

const statusClass = v => ({
  APROVADA: 'good', CONTRATADA: 'good', ATIVA: 'good', PASS: 'good', PAGA: 'good', ANTECIPADA: 'good',
  EM_ANALISE: 'warn', ANALISE_MANUAL: 'warn', MANUAL: 'warn', PENDENTE: 'warn',
  RECUSADA: 'bad', EXPIRADA: 'bad', REJECT: 'bad'
}[v] || '');
const badge = v => h('span', { class: 'status ' + statusClass(v), text: v.replace('_', ' ') });

/* ---------- login ---------- */
async function signIn(user, pass) {
  state.auth = basic(user, pass);
  try {
    state.me = await api('/api/me');
    state.tab = 'sim';
    render();
    return true;
  } catch (e) {
    state.auth = null;
    return false;
  }
}

function demoCard() {
  const box = h('section', { class: 'demo-card', hidden: true });
  fetch('/api/demo', { credentials: 'omit' }).then(r => (r.ok ? r.json() : null)).then(info => {
    if (!info || !info.enabled) return;
    state.demo = true;
    box.hidden = false;
    box.append(h('h2', { text: 'Modo demonstração' }),
      h('p', { class: 'label', text: 'Entre com um clique, sem senha. Os dados são fictícios.' }),
      h('div', { class: 'demo-buttons' }, info.accounts.map(a => h('button', {
        class: 'btn lime', type: 'button', text: a.label,
        onclick: () => signIn(a.username, a.password)
      }))));
  }).catch(() => {});
  return box;
}

function loginView() {
  const err = h('p', { class: 'note', role: 'alert' });
  const user = h('input', { id: 'user', autocomplete: 'username', required: true });
  const pass = h('input', { id: 'pass', type: 'password', autocomplete: 'current-password', required: true });
  const form = h('form', {
    onsubmit: async ev => {
      ev.preventDefault();
      err.textContent = '';
      if (!(await signIn(user.value.trim(), pass.value))) err.textContent = 'Usuário ou senha inválidos.';
    }
  },
  h('div', { class: 'field' }, h('label', { for: 'user', text: 'Usuário' }), user),
  h('div', { class: 'field' }, h('label', { for: 'pass', text: 'Senha' }), pass),
  err,
  h('button', { class: 'btn primary', type: 'submit', text: 'Entrar' }));
  return h('main', { class: 'login' },
    logoSvg(),
    h('h1', { text: 'CreditFlow' }),
    h('p', { class: 'label', text: 'Simulador e esteira de crédito. Projeto acadêmico de demonstração.' }),
    demoCard(),
    form);
}

function logoSvg() {
  return s('svg', { class: 'logo', viewBox: '0 0 64 64', 'aria-hidden': 'true' },
    s('circle', { cx: 32, cy: 32, r: 30, fill: '#2f5d46' }),
    s('rect', { x: 16, y: 16, width: 10, height: 30, rx: 2, fill: '#cfe3d6' }),
    s('rect', { x: 29, y: 25, width: 10, height: 21, rx: 2, fill: '#cfe3d6' }),
    s('rect', { x: 42, y: 34, width: 10, height: 12, rx: 2, fill: '#cfe3d6' }));
}

/* ---------- casca ---------- */
const ICONS = {
  sim: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
  prop: ['M6 3h9l4 4v14H6z', 'M14 3v5h5', 'M9 13h7', 'M9 17h7'],
  quality: ['M5 20V10', 'M12 20V4', 'M19 20v-7'],
  out: ['M9 4H5v16h4', 'M16 8l4 4-4 4', 'M20 12H9']
};
function icon(name) {
  return s('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 1.8,
    'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' },
  ...ICONS[name].map(d => s('path', { d })));
}

function shell(content) {
  const tabs = [['sim', 'Simulador'], ['prop', 'Propostas']];
  if (state.me.role === 'ANALYST') tabs.push(['quality', 'Qualidade']);
  const subtitle = { sim: 'Simule um financiamento e compare Price e SAC.', prop: 'Acompanhe propostas, contratos e parcelas.',
    quality: 'Veja como a política de crédito se comporta nos desfechos.' }[state.tab];
  return h('div', { class: 'shell' },
    h('nav', { class: 'side', 'aria-label': 'Navegação' },
      tabs.map(([id, label]) => h('button', {
        class: 'nav-btn' + (state.tab === id ? ' active' : ''), type: 'button', title: label, 'aria-label': label,
        'aria-current': state.tab === id ? 'page' : false,
        onclick: () => { state.tab = id; render(); }
      }, icon(id))),
      h('span', { class: 'spacer' }),
      h('button', { class: 'nav-btn', type: 'button', title: 'Sair', 'aria-label': 'Sair', onclick: logout }, icon('out')),
      h('span', { class: 'avatar', 'aria-hidden': 'true', text: state.me.username[0] })),
    h('div', { class: 'main' },
      state.demo && h('div', { class: 'demo-banner', text: 'Modo demonstração · dados fictícios' }),
      h('header', { class: 'top' },
        h('div', null, h('h1', { class: 'hello-title', text: 'Olá, ' + state.me.username + '!' }), h('p', { class: 'hello-sub', text: subtitle })),
        h('div', { class: 'who' },
          h('span', { class: 'pill dark', text: state.me.role === 'ANALYST' ? 'Analista' : 'Cliente ' + state.me.customerId }))),
      content));
}

/* ---------- simulador ---------- */
async function simulate() {
  const i = state.simInput;
  try {
    state.sim = await api('/api/simulations', {
      method: 'POST',
      body: { system: i.system, amount: Number(i.amount), monthlyRate: Number((Number(i.rate) / 100).toFixed(8)), term: Number(i.term) }
    });
    render();
  } catch (e) { toast(e.message, true); }
}

function gauge(sim) {
  const share = Math.min(1, Number(sim.amount) / Number(sim.totalPaid)), L = Math.PI * 90;
  const arc = 'M 25 115 A 90 90 0 0 1 205 115';
  return h('div', { class: 'gauge-wrap' },
    s('svg', { width: 230, height: 130, viewBox: '0 0 230 130', role: 'img', 'aria-label': 'Composição do total pago' },
      s('path', { d: arc, fill: 'none', stroke: '#e4ece7', 'stroke-width': 24, 'stroke-linecap': 'round' }),
      s('path', { d: arc, fill: 'none', stroke: '#5f8a70', 'stroke-width': 24, 'stroke-linecap': 'round',
        'stroke-dasharray': (share * L) + ' ' + L })),
    h('div', { class: 'gauge-center' }, h('b', { text: Math.round(share * 100) + '%' }), h('span', { class: 'label', text: 'é principal' })));
}

function spark(values, bars) {
  const w = 84, hgt = 44, max = Math.max(...values, 1);
  if (bars) {
    return s('svg', { viewBox: '0 0 ' + w + ' ' + hgt, 'aria-hidden': 'true' },
      ...values.map((v, i) => {
        const bh = Math.max(4, (v / max) * hgt);
        return s('rect', { x: i * 14 + 2, y: hgt - bh, width: 8, height: bh, rx: 4, fill: '#4f7a62' });
      }));
  }
  const pts = values.map((v, i) => [(i / (values.length - 1)) * w, hgt - 4 - (v / max) * (hgt - 8)]);
  return s('svg', { viewBox: '0 0 ' + w + ' ' + hgt, 'aria-hidden': 'true' },
    s('path', { d: 'M' + pts.map(p => p.map(n => n.toFixed(1)).join(' ')).join(' L'), fill: 'none', stroke: 'currentColor',
      'stroke-width': 2.5, 'stroke-linecap': 'round', 'stroke-linejoin': 'round' }));
}

function kpis(sim) {
  const rows = sim ? sim.installments : [];
  const every = Math.max(1, Math.ceil(rows.length / 6));
  const sample = key => {
    const v = rows.filter((_, i) => i % every === 0).slice(0, 6).map(l => Number(l[key]) || 0);
    return v.length > 1 ? v : [1, 1, 1, 1];
  };
  const tile = (label, value, chart, cls) => h('div', { class: 'kpi' + (cls ? ' ' + cls : '') },
    h('div', null, h('span', { class: 'label', text: label }), h('b', { text: value })), chart);
  return h('div', { class: 'kpis' },
    tile('Primeira parcela', sim ? money(rows[0].payment) : '—', spark(sample('payment'), true)),
    tile('Total pago', sim ? money(sim.totalPaid) : '—', spark(sample('balance'), false)),
    tile('Juros totais', sim ? money(sim.totalInterest) : '—', spark(sample('interest'), true)),
    tile('CET anual', sim ? pct(sim.cetAnnual) : '—', spark(sample('principal'), false), 'grad'));
}

function balanceChart(sim) {
  const rows = sim.installments, W = 560, H = 150, max = Number(sim.amount);
  const y = v => H - (Number(v) / max) * (H - 20) - 10;
  const pts = [[0, y(sim.amount)], ...rows.map((l, i) => [((i + 1) / rows.length) * W, y(l.balance)])];
  const line = pts.map(p => p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' L');
  return s('svg', { class: 'chart', viewBox: '0 0 ' + W + ' ' + H, preserveAspectRatio: 'none', role: 'img', 'aria-label': 'Evolução do saldo devedor' },
    s('defs', null, s('linearGradient', { id: 'bal-fill', x1: 0, y1: 0, x2: 0, y2: 1 },
      s('stop', { offset: '0%', 'stop-color': '#5f8a70', 'stop-opacity': 0.28 }),
      s('stop', { offset: '100%', 'stop-color': '#5f8a70', 'stop-opacity': 0 }))),
    s('path', { d: 'M' + line + ' L' + W + ' ' + H + ' L0 ' + H + ' Z', fill: 'url(#bal-fill)' }),
    s('path', { d: 'M' + line, fill: 'none', stroke: '#4f7a62', 'stroke-width': 2.5, 'vector-effect': 'non-scaling-stroke', 'stroke-linejoin': 'round' }));
}

function simView() {
  const i = state.simInput, sim = state.sim;
  const field = (label, key, attrs) => h('div', { class: 'field' }, h('label', { text: label }),
    h('input', { value: i[key], inputmode: 'decimal', ...attrs, oninput: e => { i[key] = e.target.value; } }));
  const seg = h('div', { class: 'seg', role: 'group', 'aria-label': 'Sistema de amortização' }, ['PRICE', 'SAC'].map(sys =>
    h('button', { type: 'button', class: i.system === sys ? 'active' : '', text: sys, onclick: () => { i.system = sys; render(); } })));
  const form = h('div', null,
    seg,
    h('div', { class: 'form' },
      field('Valor financiado (R$)', 'amount'), field('Taxa mensal (%)', 'rate'), field('Prazo (meses)', 'term')),
    h('div', { class: 'actions' },
      h('button', { class: 'btn primary', text: 'Simular', onclick: simulate }),
      h('button', { class: 'btn', text: 'Criar proposta', disabled: !sim, onclick: proposalDialog })));
  const first = sim && sim.installments[0];
  const left = h('section', { class: 'card col-5' },
    h('h2', { text: 'Simulação' }),
    h('span', { class: 'label', text: sim ? 'Primeira parcela (' + sim.system + ')' : 'Preencha os dados e simule' }),
    h('div', { class: 'big', text: sim ? money(first.payment) : 'R$ 0,00' }),
    form);
  const right = h('section', { class: 'card lime col-7' },
    s('svg', { class: 'sparkle', viewBox: '0 0 100 100', 'aria-hidden': 'true' },
      s('path', { d: 'M50 0C55 35 65 45 100 50C65 55 55 65 50 100C45 65 35 55 0 50C35 45 45 35 50 0Z', fill: '#fff' })),
    h('h2', { text: 'Juros totais' }),
    h('div', { class: 'big', text: sim ? money(sim.totalInterest) : 'R$ 0,00' }),
    sim && h('span', { class: 'chip', text: 'CET aprox. ' + pct(sim.cetAnnual) + ' a.a.' }),
    h('div', { class: 'row' },
      h('div', { class: 'box' }, h('span', { class: 'label', text: 'Valor liberado' }), h('b', { text: sim ? money(sim.amount) : '—' })),
      h('div', { class: 'box' }, h('span', { class: 'label', text: 'Última parcela' }), h('b', { text: sim ? money(sim.installments[sim.installments.length - 1].payment) : '—' }))),
    h('p', { class: 'note', text: 'CET aproximado: TIR do fluxo, sem tarifas nem IOF.' }));
  const parts = [left, right];
  if (sim) {
    parts.push(h('section', { class: 'card col-4' }, h('h2', { text: 'Composição' }), gauge(sim),
      h('div', { class: 'legend' },
        h('span', null, h('i', { class: 'dot principal' }), 'Principal'),
        h('span', null, h('i', { class: 'dot juros' }), 'Juros'))));
    parts.push(h('section', { class: 'card col-8' }, h('h2', { text: 'Saldo devedor' }), balanceChart(sim)));
    parts.push(h('section', { class: 'card col-12' }, h('h2', { text: 'Cronograma' }),
      h('div', { class: 'table-wrap' }, h('table', null,
        h('thead', null, h('tr', null, ['#', 'Parcela', 'Amortização', 'Juros', 'Saldo'].map(t => h('th', { text: t })))),
        h('tbody', null, sim.installments.map(l => h('tr', null,
          h('td', { text: l.number }), h('td', { text: money(l.payment) }), h('td', { text: money(l.principal) }),
          h('td', { text: money(l.interest) }), h('td', { text: money(l.balance) }))))))));
  }
  return h('div', null, kpis(sim), h('div', { class: 'grid' }, parts));
}

function proposalDialog() {
  const sim = state.sim, me = state.me;
  const f = (label, id, value, attrs) => h('div', { class: 'field' }, h('label', { for: id, text: label }), h('input', { id, value, inputmode: 'decimal', ...attrs }));
  const customer = f('Cliente (id)', 'd-customer', me.customerId || '1', me.customerId ? { readonly: true } : {});
  const income = f('Renda mensal (R$)', 'd-income', '8000');
  const debts = f('Compromissos mensais (R$)', 'd-debts', '0');
  const months = f('Tempo de relacionamento (meses)', 'd-months', '24');
  const dlg = h('dialog', null,
    h('h2', { text: 'Criar proposta' }),
    h('p', { class: 'label', text: sim.system + ' · ' + money(sim.amount) + ' · ' + sim.term + 'x' }),
    h('div', { class: 'form' }, customer, income, debts, months),
    h('div', { class: 'actions' },
      h('button', { class: 'btn', text: 'Cancelar', onclick: () => { dlg.close(); dlg.remove(); } }),
      h('button', {
        class: 'btn primary', text: 'Criar',
        onclick: async () => {
          try {
            const p = await api('/api/proposals', {
              method: 'POST',
              body: {
                customerId: Number(customer.querySelector('input').value), system: sim.system, amount: sim.amount,
                monthlyRate: sim.monthlyRate, term: sim.term,
                monthlyIncome: Number(income.querySelector('input').value),
                existingCommitments: Number(debts.querySelector('input').value),
                relationshipMonths: Number(months.querySelector('input').value)
              }
            });
            dlg.close(); dlg.remove();
            toast('Proposta #' + p.id + ' criada.');
            state.tab = 'prop'; state.selectedId = p.id; render();
          } catch (e) { toast(e.message, true); }
        }
      })));
  document.body.append(dlg);
  dlg.showModal();
}

/* ---------- propostas ---------- */
async function loadProposals() {
  state.proposals = await api('/api/proposals');
  if (state.selectedId === null && state.proposals.length) state.selectedId = state.proposals[0].id;
  if (state.selectedId !== null) await loadDetail(state.selectedId);
}

async function loadDetail(id) {
  const session = state.session;
  state.selectedId = id;
  const p = await api('/api/proposals/' + id);
  const [history, rules] = await Promise.all([api('/api/proposals/' + id + '/history'), api('/api/proposals/' + id + '/policy-results')]);
  let contract = null;
  if (p.status === 'CONTRATADA' || p.status === 'ATIVA') {
    try { contract = await api('/api/proposals/' + id + '/contract'); } catch (e) { contract = null; }
  }
  let outcomes = [];
  try { outcomes = await api('/api/proposals/' + id + '/outcomes'); } catch (e) { outcomes = []; }
  if (session !== state.session) return;
  state.detail = { p, history, rules, contract, outcomes };
}

async function act(fn, okMsg) {
  try { await fn(); toast(okMsg); await loadProposals(); render(); } catch (e) { toast(e.message, true); }
}

const FLOW = ['SIMULADA', 'EM_ANALISE', 'APROVADA', 'CONTRATADA', 'ATIVA'];

function detailView() {
  const d = state.detail;
  if (!d) return h('section', { class: 'card col-8' }, h('div', { class: 'empty', text: 'Selecione ou crie uma proposta.' }));
  const { p, history, rules, contract, outcomes } = d;
  const visited = new Set(history.map(x => x.to));
  const steps = h('div', { class: 'steps' }, FLOW.map(st => h('span', {
    class: 'step' + (p.status === st ? ' now' : visited.has(st) ? ' done' : ''), text: st.replace('_', ' ')
  })));
  const actions = h('div', { class: 'actions' });
  const post = (path, body, headers) => api(path, { method: 'POST', body, headers });
  if (p.status === 'SIMULADA') {
    actions.append(h('button', { class: 'btn primary', text: 'Enviar para análise', onclick: () => act(() => post('/api/proposals/' + p.id + '/submit'), 'Proposta analisada.') }));
  }
  if (p.status === 'ANALISE_MANUAL') {
    if (state.me.role === 'ANALYST') {
      const reason = h('input', { placeholder: 'Motivo da decisão', 'aria-label': 'Motivo', maxlength: 300 });
      const decide = ok => () => act(() => post('/api/proposals/' + p.id + '/manual-decision', { approved: ok, reason: reason.value.trim() || 'sem motivo informado' }), 'Decisão registrada.');
      actions.append(h('div', { class: 'field' }, reason), h('button', { class: 'btn lime', text: 'Aprovar', onclick: decide(true) }), h('button', { class: 'btn danger', text: 'Recusar', onclick: decide(false) }));
    } else {
      actions.append(h('span', { class: 'note', text: 'Aguardando decisão de um analista.' }));
    }
  }
  if (p.status === 'APROVADA') {
    actions.append(h('button', {
      class: 'btn lime', text: 'Contratar',
      onclick: () => {
        const key = state.keys[p.id] || (state.keys[p.id] = crypto.randomUUID());
        act(() => post('/api/proposals/' + p.id + '/contract', null, { 'Idempotency-Key': key }), 'Contrato gerado.');
      }
    }), p.validUntil && h('span', { class: 'note', text: 'Válida até ' + dateBR(p.validUntil) }));
  }
  if (contract && state.keys[p.id]) {
    actions.append(h('button', {
      class: 'btn small', text: 'Repetir pedido (mesma chave)',
      onclick: async () => {
        try {
          const again = await api('/api/proposals/' + p.id + '/contract', { method: 'POST', headers: { 'Idempotency-Key': state.keys[p.id] } });
          toast('Mesmo contrato devolvido (#' + again.id + '): nada foi duplicado.');
        } catch (e) { toast(e.message, true); }
      }
    }));
  }
  const head = h('section', { class: 'card' },
    h('h2', null, 'Proposta #' + p.id, badge(p.status)),
    h('div', { class: 'mid', text: money(p.amount) }),
    h('p', { class: 'label', text: p.system + ' · ' + p.term + 'x · ' + pct(p.monthlyRate) + ' a.m. · cliente ' + p.customerId }),
    steps, actions);
  const rulesCard = h('section', { class: 'card' },
    h('h2', null, 'Política de crédito', rules.length > 0 && h('span', { class: 'status', title: 'Versão da política que produziu esta decisão', text: rules[0].policyVersion })),
    rules.length ? rules.map(r => h('div', { class: 'rule' }, badge(r.outcome), h('div', null, h('b', { text: r.rule }), h('small', { class: 'label', text: ' ' + r.detail }))))
      : h('div', { class: 'empty', text: 'Ainda não analisada.' }));
  const histCard = h('section', { class: 'card' }, h('h2', { text: 'Histórico' }),
    h('ul', { class: 'timeline' }, history.map(x => h('li', null, h('span', { class: 'tdot' }),
      h('div', null, h('b', { text: (x.from ? x.from.replace('_', ' ') + ' → ' : '') + x.to.replace('_', ' ') }),
        h('small', { text: dateTimeBR(x.at) + (x.reason ? ' · ' + x.reason : '') }))))));
  const wrap = h('div', { class: 'col-8', role: 'region', 'aria-label': 'Detalhe da proposta' });
  wrap.style.display = 'grid'; wrap.style.gap = '18px'; wrap.style.alignContent = 'start';
  wrap.append(head, rulesCard, histCard, outcomesCard(p, outcomes));
  if (contract) {
    const prepay = i => h('button', {
      class: 'btn small', text: 'Antecipar',
      onclick: () => act(() => api('/api/contracts/' + contract.id + '/installments/' + i.number + '/prepay', { method: 'POST' }), 'Parcela antecipada com desconto de juros.')
    });
    const row = i => h('tr', null,
      h('td', { text: i.number }), h('td', { text: dateBR(i.dueDate) }), h('td', { text: money(i.principal) }),
      h('td', { text: money(i.interest) }), h('td', { text: i.paidAmount ? money(i.paidAmount) : money(i.amount) }),
      h('td', null, badge(i.status)),
      h('td', null, i.status === 'PENDENTE' ? prepay(i) : ''));
    const heads = ['#', 'Vencimento', 'Amortização', 'Juros', 'Parcela', 'Situação', ''].map(t => h('th', { text: t }));
    wrap.append(h('section', { class: 'card' }, h('h2', { text: 'Contrato #' + contract.id + ' · parcelas' }),
      h('div', { class: 'table-wrap' }, h('table', null,
        h('thead', null, h('tr', null, heads)),
        h('tbody', null, contract.installments.map(row))))));
  }
  return wrap;
}

function propView() {
  const list = h('section', { class: 'card col-4' },
    h('h2', { text: 'Propostas' }),
    h('div', { class: 'list' }, state.proposals.length ? state.proposals.map(p => h('button', {
      class: 'item' + (p.id === state.selectedId ? ' sel' : ''),
      onclick: async () => { try { await loadDetail(p.id); render(); } catch (e) { toast(e.message, true); } }
    }, h('span', { class: 'icon', text: '#' + p.id }),
    h('span', { class: 'grow' }, h('b', { text: money(p.amount) }), h('small', { text: p.system + ' · ' + p.term + 'x' })),
    badge(p.status))) : h('div', { class: 'empty', text: 'Nenhuma proposta ainda. Faça uma simulação e crie a primeira.' })),
    h('div', { class: 'actions' }, h('button', { class: 'btn small', text: 'Nova simulação', onclick: () => { state.tab = 'sim'; render(); } })));
  return h('div', { class: 'grid' }, list, detailView());
}

/* ---------- desfechos e qualidade da politica ---------- */
const SIGNALS = {
  POSSIVEL_FALSO_POSITIVO: ['Possível falso positivo', 'bad', 'Aprovou, mas o cliente deixou de pagar ou houve chargeback'],
  POSSIVEL_FALSO_NEGATIVO: ['Possível falso negativo', 'warn', 'Recusou, mas o cliente contestou a decisão'],
  DECISAO_CONFIRMADA: ['Decisão confirmada', 'good', 'Aprovou e o cliente pagou em dia'],
  INCONCLUSIVO: ['Inconclusivo', '', 'Sozinho, este desfecho não diz se a decisão estava certa']
};
const TYPES = { CONTESTACAO: 'Contestação', CHARGEBACK: 'Chargeback', INADIMPLENCIA: 'Inadimplência', QUITACAO: 'Quitação' };
const signalBadge = sig => { const s = SIGNALS[sig] || SIGNALS.INCONCLUSIVO; return h('span', { class: 'status ' + s[1], title: s[2], text: s[0] }); };

function outcomesCard(p, outcomes) {
  const list = outcomes.length
    ? h('ul', { class: 'timeline' }, outcomes.map(o => h('li', null, h('span', { class: 'tdot' }),
      h('div', null, h('b', null, TYPES[o.type] + ' ', signalBadge(o.signal)),
        h('small', { text: dateBR(o.occurredOn) + (o.detail ? ' · ' + o.detail : '') + ' · registrado por ' + o.recordedBy + ' · decisão original ' + o.decision + ' · política ' + o.policyVersion })))))
    : h('div', { class: 'empty', text: 'Nenhum desfecho registrado ainda.' });
  const card = h('section', { class: 'card' }, h('h2', { text: 'Desfechos' }),
    h('p', { class: 'note', text: 'O que aconteceu depois da decisão: o sinal real para saber se a política acertou.' }), list);
  if (state.me.role !== 'ANALYST') return card;
  const type = h('select', { 'aria-label': 'Tipo de desfecho' }, Object.entries(TYPES).map(([k, v]) => h('option', { value: k, text: v })));
  const date = h('input', { type: 'date', 'aria-label': 'Data do desfecho', value: new Date().toISOString().slice(0, 10) });
  const detail = h('input', { placeholder: 'Detalhe (opcional)', 'aria-label': 'Detalhe', maxlength: 300 });
  card.append(h('div', { class: 'outcome-form' }, type, date, detail,
    h('button', {
      class: 'btn small', text: 'Registrar desfecho',
      onclick: () => act(() => api('/api/proposals/' + p.id + '/outcomes', { method: 'POST', body: { type: type.value, detail: detail.value.trim() || null, occurredOn: date.value } }), 'Desfecho registrado.')
    })));
  return card;
}

async function qualityView() {
  let rows = [];
  try { rows = await api('/api/outcomes/summary'); } catch (e) { toast(e.message, true); }
  const body = rows.length ? rows.map(r => h('tr', null,
    h('td', { text: r.policyVersion }), h('td', { text: r.decision }), h('td', { text: TYPES[r.type] }),
    h('td', null, signalBadge(r.signal)), h('td', { text: r.count })))
    : [h('tr', null, h('td', { colspan: 5, text: 'Ainda não há desfechos registrados.' }))];
  return h('section', { class: 'card' }, h('h2', { text: 'Qualidade da política' }),
    h('p', { class: 'note', text: 'Cruza a versão da política, a decisão original e o desfecho posterior. Falso positivo: aprovou quem não pagou. Falso negativo: recusou e o cliente contestou. A contestação é um sinal barato: não exige uma equipe de revisão manual.' }),
    h('div', { class: 'table-wrap' }, h('table', null,
      h('thead', null, h('tr', null, ['Política', 'Decisão', 'Desfecho', 'Sinal', 'Qtde'].map(t => h('th', { text: t })))),
      h('tbody', null, body))));
}

/* ---------- raiz ---------- */
async function render() {
  const app = document.getElementById('app');
  if (!state.auth) { app.replaceChildren(loginView()); return; }
  if (state.tab === 'sim') {
    app.replaceChildren(shell(simView()));
    if (!state.sim) simulate();
  } else if (state.tab === 'quality' && state.me.role === 'ANALYST') {
    app.replaceChildren(shell(await qualityView()));
  } else {
    try { await loadProposals(); } catch (e) { toast(e.message, true); }
    app.replaceChildren(shell(propView()));
  }
}

render();
