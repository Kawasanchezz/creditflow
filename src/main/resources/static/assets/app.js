'use strict';

const state = {
  auth: null, me: null, tab: 'sim',
  simInput: { system: 'PRICE', amount: '10000', rate: '1.99', term: '12' },
  sim: null, proposals: [], selectedId: null, detail: null, keys: {}, demo: false, session: 0, showAll: false, pop: null, seen: false, cmp: null, cmpBusy: false
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
  state.pop = null; state.seen = false; state.cmp = null; state.showAll = false;
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
    s('path', { d: 'M32 6L60 56H44L32 33 20 56H4z', fill: '#111111' }),
    s('path', { d: 'M32 33l9 17H23z', fill: '#ff7a3d' }));
}

/* ---------- casca ---------- */
const ICONS = {
  up: ['M7 17L17 7', 'M8 7h9v9'],
  down: ['M7 7l10 10', 'M17 8v9H8'],
  swap: ['M7 4v16', 'M4 7l3-3 3 3', 'M17 20V4', 'M14 17l3 3 3-3'],
  bell: ['M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7', 'M10 20a2 2 0 0 0 4 0'],
  out: ['M9 4H5v16h4', 'M16 8l4 4-4 4', 'M20 12H9'],
  doc: ['M6 3h9l4 4v14H6z', 'M14 3v5h5'],
  sliders: ['M4 7h9', 'M17 7h3', 'M4 17h3', 'M11 17h9', 'M13 5v4', 'M7 15v4'],
  clock: ['M12 7v5l3 2', 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z'],
  percent: ['M19 5L5 19', 'M7 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4z', 'M17 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4z'],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21a8 8 0 0 1 16 0'],
  download: ['M12 4v11', 'M7 11l5 5 5-5', 'M5 20h14'],
  copy: ['M9 9h10v11H9z', 'M5 15V4h10'],
  list: ['M8 6h12', 'M8 12h12', 'M8 18h12', 'M4 6h.01', 'M4 12h.01', 'M4 18h.01'],
  wallet: ['M4 7h15a1 1 0 0 1 1 1v11H5a1 1 0 0 1-1-1z', 'M4 7l12-3v3', 'M16 13h4']
};
function icon(name) {
  return s('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': 1.9,
    'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true' },
  ...ICONS[name].map(d => s('path', { d })));
}

function togglePop(name) {
  state.pop = state.pop === name ? null : name;
  if (name === 'bell' && state.pop === 'bell') {
    state.seen = true;
    api('/api/proposals').then(list => { state.proposals = list; if (state.pop === 'bell') render(); }).catch(() => {});
  }
  render();
}

function newSimulation() {
  state.pop = null; state.tab = 'sim';
  state.simInput = { system: 'PRICE', amount: '10000', rate: '1.99', term: '12' };
  state.sim = null; state.cmp = null; state.showAll = false;
  render();
  paramsDialog();
}

function goTo(tab, id) {
  state.pop = null; state.tab = tab;
  if (id !== undefined) state.selectedId = id;
  render();
}

function notifPanel() {
  const list = state.proposals.slice(0, 6);
  return h('div', { class: 'pop', role: 'dialog', 'aria-label': 'Notificações' },
    h('div', { class: 'pophead' }, h('b', { text: 'Notificações' }), h('small', { text: list.length + ' proposta(s)' })),
    list.length ? list.map(p => h('button', { class: 'popitem', type: 'button', onclick: () => goTo('prop', p.id) },
      h('span', { class: 'badge', text: '#' + p.id }),
      h('span', { class: 'grow' }, h('b', { text: money(p.amount) }), h('small', { text: p.system + ' · ' + p.term + 'x' })),
      badge(p.status)))
      : h('p', { class: 'empty', text: 'Nenhuma proposta ainda. Faça uma simulação e crie a primeira.' }),
    h('button', { class: 'popfoot', type: 'button', text: 'Ver todas as propostas', onclick: () => goTo('prop') }));
}

function userPanel() {
  return h('div', { class: 'pop narrow', role: 'menu', 'aria-label': 'Conta' },
    h('div', { class: 'pophead' }, h('b', { text: state.me.username }), h('small', { text: displayName() })),
    h('button', { class: 'popitem', type: 'button', role: 'menuitem', onclick: () => { state.pop = null; newSimulation(); } }, icon('sliders'), h('span', { class: 'grow', text: 'Nova simulação' })),
    h('button', { class: 'popitem', type: 'button', role: 'menuitem', onclick: () => goTo('prop') }, icon('list'), h('span', { class: 'grow', text: 'Minhas propostas' })),
    state.me.role === 'ANALYST' && h('button', { class: 'popitem', type: 'button', role: 'menuitem', onclick: () => goTo('quality') }, icon('doc'), h('span', { class: 'grow', text: 'Qualidade da política' })),
    h('button', { class: 'popfoot danger', type: 'button', role: 'menuitem', text: 'Sair', onclick: logout }));
}

function whoBlock() {
  return h('div', { class: 'who' },
    h('span', { class: 'pill dark', text: state.me.role === 'ANALYST' ? 'Analista' : 'Cliente ' + state.me.customerId }),
    h('div', { class: 'popwrap' },
      h('button', { class: 'round', type: 'button', title: 'Notificações', 'aria-label': 'Notificações', 'aria-expanded': state.pop === 'bell' ? 'true' : 'false',
        onclick: () => togglePop('bell') }, icon('bell'), !state.seen && h('i', { class: 'ping' })),
      state.pop === 'bell' && notifPanel()),
    h('button', { class: 'round', type: 'button', title: 'Sair', 'aria-label': 'Sair', onclick: logout }, icon('out')),
    h('div', { class: 'popwrap' },
      h('button', { class: 'avatar', type: 'button', title: 'Minha conta', 'aria-label': 'Minha conta', 'aria-expanded': state.pop === 'user' ? 'true' : 'false',
        onclick: () => togglePop('user'), text: state.me.username[0] }),
      state.pop === 'user' && userPanel()));
}

function displayName() {
  const u = state.me.username;
  return state.me.role === 'ANALYST' ? 'Analista' : 'Cliente ' + (state.me.customerId || u);
}

function shell(content) {
  const tabs = [['sim', 'Simulador'], ['prop', 'Propostas']];
  if (state.me.role === 'ANALYST') tabs.push(['quality', 'Qualidade']);
  const subtitle = { sim: 'Simule financiamentos e compare Price e SAC em tempo real',
    prop: 'Acompanhe propostas, contratos e parcelas em um só lugar',
    quality: 'Veja como a política de crédito se comporta nos desfechos' }[state.tab];
  const cta = state.tab === 'sim'
    ? h('button', { class: 'btn primary cta', type: 'button', onclick: paramsDialog }, icon('sliders'), ' Editar simulação')
    : h('button', { class: 'btn primary cta', type: 'button', onclick: newSimulation }, icon('sliders'), ' Nova simulação');
  return h('div', { class: 'shell' },
    h('header', { class: 'top' },
      h('div', { class: 'brand' }, logoSvg(), h('span', { text: 'CreditFlow' })),
      h('nav', { class: 'navpill', 'aria-label': 'Navegação' }, tabs.map(([id, label]) => h('button', {
        class: state.tab === id ? 'active' : '', type: 'button', text: label,
        'aria-current': state.tab === id ? 'page' : false,
        onclick: () => { state.tab = id; render(); }
      }))),
      whoBlock()),
    h('main', { class: 'main' },
      state.demo && h('div', { class: 'demo-banner', text: 'Modo demonstração · dados fictícios' }),
      h('div', { class: 'welcome' },
        h('div', null, h('p', { class: 'hello-sub', text: subtitle }), h('h1', { class: 'hello-title', text: 'Bem-vindo de volta, ' + displayName() + '!' })),
        cta),
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

function splitMoney(v) {
  const t = money(v), i = t.lastIndexOf(',');
  return [t.slice(0, i), t.slice(i)];
}
function amountEl(v, cls) {
  if (v && v.text !== undefined) return h('div', { class: cls || 'amount', text: v.text });
  const [a, b] = splitMoney(v);
  return h('div', { class: cls || 'amount' }, a, h('span', { class: 'cents', text: b }));
}
const compact = v => (v >= 1000 ? (v / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + 'k' : String(Math.round(v)));

function barChart(sim) {
  const W = 600, H = 220, L = 36, B = 24, T = 10;
  const rows = sim.installments, step = Math.max(1, Math.ceil(rows.length / 96));
  const pick = rows.filter((l, i) => i % step === 0 || i === rows.length - 1);
  const max = Number(sim.amount), slot = (W - L) / pick.length, bw = Math.max(1.6, slot * 0.42);
  const yOf = v => T + (H - T - B) * (1 - Number(v) / max);
  const box = h('div', { class: 'chartbox' });
  const tip = h('div', { class: 'tip' });
  const bars = [];
  const dot = s('circle', { class: 'dot', r: 5 });
  const show = idx => {
    const l = pick[idx], prev = idx === 0 ? max : Number(pick[idx - 1].balance);
    bars.forEach((r, k) => r.setAttribute('class', 'bar' + (k === idx ? ' sel' : '')));
    const drop = prev > 0 ? (prev - Number(l.balance)) / prev : 0;
    tip.replaceChildren(
      h('div', { class: 'tiprow' }, h('b', { text: money(l.balance) }), h('span', { class: 'chipk' }, icon('down'), ' ' + pct(drop, 1))),
      h('small', { text: 'saldo após a parcela ' + l.number }));
    const cx = L + slot * idx + slot / 2, cy = yOf(l.balance);
    dot.setAttribute('cx', cx); dot.setAttribute('cy', cy);
    tip.style.left = Math.min(88, Math.max(12, cx / W * 100)) + '%';
    tip.style.top = (Math.max(cy - 14, 70) / H * 100) + '%';
  };
  const ticks = [0.25, 0.5, 0.75, 1].map(p => {
    const t = s('text', { class: 'axis', x: 0, y: yOf(max * p) + 3 });
    t.textContent = compact(max * p); return t;
  });
  const labelEvery = Math.ceil(pick.length / 12);
  const xl = pick.map((l, idx) => idx % labelEvery === 0 ? l : null).filter(Boolean).map(l => {
    const idx = pick.indexOf(l), t = s('text', { class: 'axis', x: L + slot * idx + slot / 2, y: H - 6, 'text-anchor': 'middle' });
    t.textContent = l.number; return t;
  });
  const svg = s('svg', { class: 'bars', viewBox: '0 0 ' + W + ' ' + H, role: 'img', 'aria-label': 'Saldo devedor ao longo das parcelas' }, ...ticks, ...xl);
  pick.forEach((l, idx) => {
    const x = L + slot * idx + (slot - bw) / 2, y = yOf(l.balance);
    const r = s('rect', { class: 'bar', x, y, width: bw, height: Math.max(2, H - B - y), rx: Math.min(bw / 2, 2) });
    bars.push(r);
    const hit = s('rect', { class: 'hit', x: L + slot * idx, y: 0, width: slot, height: H - B });
    hit.addEventListener('mouseenter', () => show(idx));
    hit.addEventListener('click', () => show(idx));
    svg.append(r, hit);
  });
  svg.append(dot);
  box.append(svg, tip);
  show(Math.floor(pick.length * 0.55));
  return box;
}

const KEY = s => [s.amount, s.monthlyRate, s.term].join('|');

function ensureCompare(sim) {
  if ((state.cmp && state.cmp.key === KEY(sim)) || state.cmpBusy) return;
  state.cmpBusy = true;
  const key = KEY(sim), session = state.session;
  const body = sys => ({ system: sys, amount: Number(sim.amount), monthlyRate: Number(sim.monthlyRate), term: Number(sim.term) });
  Promise.all(['PRICE', 'SAC'].map(sys => api('/api/simulations', { method: 'POST', body: body(sys) })))
    .then(([price, sac]) => { if (session === state.session) state.cmp = { key, price, sac }; })
    .catch(() => { if (session === state.session) state.cmp = { key, failed: true }; })
    .finally(() => { state.cmpBusy = false; if (session === state.session && state.tab === 'sim') render(); });
}

function compareCard(sim) {
  const cmp = state.cmp && state.cmp.key === KEY(sim) ? state.cmp : null;
  const head = h('div', { class: 'cardhead' }, h('h2', { text: 'Comparar Price × SAC' }));
  if (!cmp) return h('section', { class: 'card col-7' }, head, h('p', { class: 'empty', text: 'Calculando a comparação…' }));
  if (cmp.failed) return h('section', { class: 'card col-7' }, head, h('p', { class: 'empty', text: 'Não foi possível comparar agora.' }));
  const { price, sac } = cmp;
  const maxInterest = Math.max(Number(price.totalInterest), Number(sac.totalInterest), 1);
  const saving = Number(price.totalInterest) - Number(sac.totalInterest);
  const col = (sim2, label) => {
    const rows = sim2.installments;
    const bar = h('div', { class: 'cmpbar' }, h('i', null));
    bar.firstChild.style.width = (Number(sim2.totalInterest) / maxInterest * 100) + '%';
    return h('div', { class: 'cmpcol' + (state.simInput.system === label ? ' current' : '') },
      h('b', { text: label }),
      h('dl', null,
        h('dt', { text: 'Primeira parcela' }), h('dd', { text: money(rows[0].payment) }),
        h('dt', { text: 'Última parcela' }), h('dd', { text: money(rows[rows.length - 1].payment) }),
        h('dt', { text: 'Juros totais' }), h('dd', { text: money(sim2.totalInterest) }),
        h('dt', { text: 'Total pago' }), h('dd', { text: money(sim2.totalPaid) })),
      bar,
      h('button', { class: 'btn small', type: 'button', text: 'Usar ' + label,
        disabled: state.simInput.system === label,
        onclick: () => { state.simInput.system = label; simulate(); } }));
  };
  return h('section', { class: 'card col-7' }, head,
    h('div', { class: 'cmpgrid' }, col(price, 'PRICE'), col(sac, 'SAC')),
    h('p', { class: 'note', text: saving > 0
      ? 'O SAC paga ' + money(saving) + ' a menos em juros, com parcelas iniciais maiores.'
      : saving < 0 ? 'O Price paga ' + money(-saving) + ' a menos em juros.' : 'Os dois sistemas têm o mesmo custo total.' }));
}

function dueDate(months) {
  const d = new Date();
  d.setMonth(d.getMonth() + months);
  return d.toLocaleDateString('pt-BR');
}

function summaryText(sim) {
  const rows = sim.installments;
  return ['CreditFlow · simulação ' + sim.system,
    'Valor: ' + money(sim.amount) + ' em ' + sim.term + 'x a ' + pct(sim.monthlyRate) + ' a.m.',
    'Primeira parcela: ' + money(rows[0].payment) + ' · última: ' + money(rows[rows.length - 1].payment),
    'Juros totais: ' + money(sim.totalInterest) + ' · total pago: ' + money(sim.totalPaid),
    'CET aprox.: ' + pct(sim.cetAnnual) + ' a.a.'].join('\n');
}

function exportCsv(sim) {
  const lines = ['parcela;valor_parcela;amortizacao;juros;saldo'].concat(sim.installments.map(l =>
    [l.number, l.payment, l.principal, l.interest, l.balance].map(v => String(v).replace('.', ',')).join(';')));
  const url = URL.createObjectURL(new Blob(['\ufeff' + lines.join('\n')], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url; a.download = 'cronograma-' + sim.system.toLowerCase() + '.csv';
  document.body.append(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast('Cronograma exportado em CSV.');
}

async function copySummary(sim) {
  try {
    await navigator.clipboard.writeText(summaryText(sim));
    toast('Resumo copiado.');
  } catch (e) { toast('Não foi possível copiar neste navegador.', true); }
}

function summaryCard(sim) {
  const rows = sim.installments, n = rows.length;
  const avg = rows.reduce((a, l) => a + Number(l.payment), 0) / n;
  const line = (k, v) => h('div', { class: 'sumrow' }, h('span', { text: k }), h('b', { text: v }));
  return h('section', { class: 'card col-5' },
    h('div', { class: 'cardhead' }, h('h2', { text: 'Resumo' })),
    line('Valor liberado', money(sim.amount)),
    line('Parcela média', money(avg)),
    line('Juros médios por mês', money(Number(sim.totalInterest) / n)),
    line('Juros sobre o valor', pct(Number(sim.totalInterest) / Number(sim.amount), 1)),
    line('CET mensal', pct(sim.cetMonthly)),
    line('Última parcela em', dueDate(n)),
    h('div', { class: 'actions sumact' },
      h('button', { class: 'btn small', type: 'button', onclick: () => exportCsv(sim) }, icon('download'), ' Exportar CSV'),
      h('button', { class: 'btn small', type: 'button', onclick: () => copySummary(sim) }, icon('copy'), ' Copiar resumo')));
}

function paramsDialog() {
  const i = state.simInput;
  const draft = { ...i };
  const f = (label, key) => h('div', { class: 'field' }, h('label', { text: label }),
    h('input', { value: draft[key], inputmode: 'decimal', oninput: e => { draft[key] = e.target.value; } }));
  const segBtns = ['PRICE', 'SAC'].map(sys => h('button', {
    type: 'button', class: draft.system === sys ? 'active' : '', text: sys,
    onclick: () => { draft.system = sys; segBtns.forEach(b => b.classList.toggle('active', b.textContent === sys)); }
  }));
  const dlg = h('dialog', null,
    h('h2', { text: 'Editar simulação' }),
    h('p', { class: 'label', text: 'Escolha o sistema, o valor, a taxa mensal e o prazo.' }),
    h('div', { class: 'seg', role: 'group', 'aria-label': 'Sistema de amortização' }, segBtns),
    h('div', { class: 'form' }, f('Valor financiado (R$)', 'amount'), f('Taxa mensal (%)', 'rate'), f('Prazo (meses)', 'term')),
    h('div', { class: 'actions' },
      h('button', { class: 'btn', type: 'button', text: 'Cancelar', onclick: () => { dlg.close(); dlg.remove(); } }),
      h('button', { class: 'btn primary', type: 'button', text: 'Simular', onclick: () => {
        Object.assign(state.simInput, draft); dlg.close(); dlg.remove(); simulate();
      } })));
  document.body.append(dlg);
  dlg.showModal();
}

function simView() {
  const i = state.simInput, sim = state.sim;
  const rows = sim ? sim.installments : [];
  const first = rows[0];
  const setSystem = sys => { if (i.system !== sys) { i.system = sys; simulate(); } };
  const sysPills = ['PRICE', 'SAC'].map(sys => h('button', {
    class: 'glass' + (i.system === sys ? ' on' : ''), type: 'button', text: sys, onclick: () => setSystem(sys)
  }));

  const hero1 = h('section', { class: 'hero' },
    h('div', { class: 'hero-head' }, h('h2', { text: 'Simulação' }),
      h('div', { class: 'pillrow' }, ...sysPills,
        h('button', { class: 'round-glass', type: 'button', title: 'Editar parâmetros', 'aria-label': 'Editar parâmetros', onclick: paramsDialog }, icon('sliders')))),
    amountEl(sim ? first.payment : 0),
    h('div', { class: 'chips' },
      h('span', { class: 'glass soft' }, icon('up'), sim ? ' CET ' + pct(sim.cetAnnual) + ' a.a.' : ' CET —'),
      h('span', { class: 'glass soft', text: sim ? 'primeira parcela · ' + sim.term + ' meses' : 'primeira parcela' }),
      h('span', { class: 'presets', role: 'group', 'aria-label': 'Prazo' }, [12, 24, 36, 48].map(t => h('button', {
        class: 'glass' + (String(i.term) === String(t) ? ' on' : ''), type: 'button', text: t + 'x',
        onclick: () => { if (String(i.term) !== String(t)) { i.term = String(t); simulate(); } }
      })))),
    sim ? barChart(sim) : h('div', { class: 'empty', text: 'Preencha os dados e simule para ver o saldo devedor.' }));

  const hero2 = h('section', { class: 'hero' },
    h('div', { class: 'hero-head' }, h('h2', { text: 'Juros totais' }),
      h('span', { class: 'glass', text: sim ? sim.term + ' meses' : '—' })),
    amountEl(sim ? sim.totalInterest : 0),
    h('div', { class: 'chips split' },
      h('span', { class: 'glass soft' }, icon('up'), sim ? ' ' + pct(Number(sim.totalInterest) / Number(sim.amount), 1) + ' do valor' : ' do valor'),
      h('span', { class: 'glass soft' }, icon('wallet'), sim ? ' Total pago: ' + money(sim.totalPaid) : ' Total pago: —')),
    h('div', { class: 'hero-actions' },
      h('button', { class: 'white-btn', type: 'button', disabled: !sim, onclick: () => sim && proposalDialog() }, icon('up'), 'Criar proposta'),
      h('button', { class: 'round-white', type: 'button', title: 'Alternar Price/SAC', 'aria-label': 'Alternar Price/SAC', onclick: () => setSystem(i.system === 'PRICE' ? 'SAC' : 'PRICE') }, icon('swap')),
      h('button', { class: 'white-btn', type: 'button', onclick: simulate }, icon('down'), 'Recalcular')),
    h('div', { class: 'stack', 'aria-hidden': 'true' },
      h('div', { class: 'layer l1' }), h('div', { class: 'layer l2' }),
      h('div', { class: 'layer face' },
        h('span', { text: '•••• ' + (sim ? String(sim.term).padStart(2, '0') : '--') + '   ' + (sim ? sim.system : i.system) }),
        h('b', { text: 'CREDITFLOW' }))));

  const parts = [h('div', { class: 'herorow' }, hero1, hero2)];
  if (sim) {
    const share = Number(sim.amount) / Number(sim.totalPaid);
    const shown = state.showAll ? rows : rows.slice(0, 8);
    const tx = h('section', { class: 'card soft col-7' },
      h('div', { class: 'cardhead' }, h('h2', { text: 'Cronograma' }),
        h('div', { class: 'linkrow' },
          h('button', { class: 'link', type: 'button', text: 'Exportar CSV', onclick: () => exportCsv(sim) }),
          rows.length > 8 && h('button', { class: 'link', type: 'button', text: state.showAll ? 'Ver menos' : 'Ver todas',
            onclick: () => { state.showAll = !state.showAll; render(); } }))),
      h('div', { class: 'txlist' }, shown.map(l => h('div', { class: 'tx' },
        h('span', { class: 'badge', text: l.number }),
        h('div', { class: 'name' }, h('b', { text: 'Parcela ' + l.number }), h('small', { text: 'saldo restante ' + money(l.balance) })),
        h('div', { class: 'pillx' }, h('span', { text: 'Juros ' + money(l.interest) })),
        h('div', { class: 'dots', text: 'amort. ' + money(l.principal) }),
        h('div', { class: 'val', text: money(l.payment) })))));
    const tile = (label, ic, value, delta, good) => h('div', { class: 'tile' },
      h('div', { class: 'tilehead' }, h('span', { class: 'label', text: label }), icon(ic)),
      amountEl(value, 'tilevalue'),
      h('span', { class: 'delta' }, h('i', { class: good ? 'good' : '', text: good ? '↗ ' : '↘ ' }), delta));
    const side = h('div', { class: 'col-5' }, h('div', { class: 'tiles' },
      tile('Principal', 'wallet', sim.amount, Math.round(share * 100) + '% do total pago', true),
      tile('Juros', 'up', sim.totalInterest, Math.round((1 - share) * 100) + '% do total pago', false),
      tile('Prazo', 'clock', { text: sim.term + ' meses' }, sim.system === 'PRICE' ? 'parcelas fixas' : 'parcelas decrescentes', true),
      tile('Taxa mensal', 'percent', { text: pct(sim.monthlyRate) + ' a.m.' }, 'CET ' + pct(sim.cetAnnual) + ' a.a.', false)),
    h('p', { class: 'note', text: 'CET aproximado: TIR do fluxo, sem tarifas nem IOF.' }));
    ensureCompare(sim);
    parts.push(h('div', { class: 'grid' }, tx, side));
    parts.push(h('div', { class: 'grid sec' }, compareCard(sim), summaryCard(sim)));
  }
  return h('div', null, ...parts);
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
    h('div', { class: 'actions' }, h('button', { class: 'btn primary block', type: 'button', onclick: newSimulation }, icon('sliders'), ' Nova simulação')));
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

document.addEventListener('click', ev => {
  if (state.pop && !ev.target.closest('.popwrap')) { state.pop = null; render(); }
});
document.addEventListener('keydown', ev => {
  if (ev.key === 'Escape' && state.pop) { state.pop = null; render(); }
});

render();
