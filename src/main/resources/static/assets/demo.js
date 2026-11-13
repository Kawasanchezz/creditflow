/*
 * Demonstracao estatica do CreditFlow (Vercel): simula a API REST dentro do navegador.
 *
 * So entra em acao quando NAO ha backend: na carga, sonda GET /api/me. O backend Java responde 401 (existe e pede
 * credencial) e, nesse caso, todas as chamadas seguem para ele; numa hospedagem estatica a rota nao existe (404) e as
 * chamadas /api/* passam a ser atendidas aqui, com localStorage. Reproduz as regras do backend (Price/SAC com
 * HALF_EVEN, politica em cadeia, estados, contratacao idempotente, antecipacao e desfechos) com dinheiro em
 * centavos inteiros (BigInt). O sistema de verdade, em Java 21 e Spring Boot, esta no repositorio.
 */
(function (root) {
  'use strict';

  const S = 10n ** 40n;
  const E8 = 10n ** 8n;
  const MAX_TERM = 480;
  const STORE_KEY = 'creditflow-demo-v1';
  const POLICY = { minIncomeCents: 100000, maxRatio4: 3000n, minMonths: 3, minScore: 500 };
  const POLICY_FINGERPRINT = 'MIN_INCOME>INCOME_COMMITMENT>RELATIONSHIP>SCORE>1000|0.3|3|500';
  const SEED_RATE = 0.0199;
  const CUSTOMER_PASSWORD = 'demo-cliente';
  const ANALYST_PASSWORD = 'demo-analista';

  const ALLOWED = {
    SIMULADA: ['EM_ANALISE'],
    EM_ANALISE: ['APROVADA', 'RECUSADA', 'ANALISE_MANUAL'],
    ANALISE_MANUAL: ['APROVADA', 'RECUSADA'],
    APROVADA: ['CONTRATADA', 'EXPIRADA'],
    CONTRATADA: ['ATIVA'],
    ATIVA: [], RECUSADA: [], EXPIRADA: []
  };
  const DECIDED = ['APROVADA', 'RECUSADA', 'CONTRATADA', 'ATIVA', 'EXPIRADA'];
  const OUTCOME_TYPES = ['CONTESTACAO', 'CHARGEBACK', 'INADIMPLENCIA', 'QUITACAO'];

  const sleep = ms => new Promise(r => setTimeout(r, ms));
  function fail(status, message) { const e = new Error(message); e.status = status; return e; }
  const today = () => new Date().toISOString().slice(0, 10);
  const hasControl = s => typeof s === 'string' && s.indexOf('\u0000') >= 0;

  /* ---------- aritmetica exata ---------- */
  function divHalfEven(n, d) {
    const q = n / d, r = n % d, twice = 2n * r;
    return twice > d || (twice === d && q % 2n !== 0n) ? q + 1n : q;
  }
  function toCents(x, label) {
    const c = Math.round(Number(x) * 100);
    if (!Number.isFinite(c) || Math.abs(Number(x) * 100 - c) > 1e-6) throw fail(400, label + ': no maximo 2 casas decimais');
    return c;
  }
  function toRateScaled(x) {
    const r = Math.round(Number(x) * 1e8);
    if (!Number.isFinite(r) || Math.abs(Number(x) * 1e8 - r) > 1e-3) throw fail(400, 'monthlyRate: no maximo 8 casas decimais');
    return r;
  }

  /* ---------- simulacao Price e SAC ---------- */
  function pmtPrice(pv, rateS, n) {
    if (rateS === 0n) return divHalfEven(pv, BigInt(n));
    const base = S + rateS * (S / E8);
    let g = S;
    for (let k = 0; k < n; k++) g = g * base / S;
    return divHalfEven(pv * rateS * g, E8 * (g - S));
  }

  function schedule(system, pvC, rateN, n) {
    const pv = BigInt(pvC), rateS = BigInt(rateN);
    const interestOf = bal => divHalfEven(bal * rateS, E8);
    const lines = [];
    let balance = pv;
    if (system === 'PRICE') {
      const pmt = pmtPrice(pv, rateS, n);
      for (let k = 1; k <= n; k++) {
        const interest = interestOf(balance);
        const regular = pmt - interest > 0n ? pmt - interest : 0n;
        let amort, paid;
        if (k === n || regular >= balance) { amort = balance; paid = amort + interest; }
        else { paid = pmt > interest ? pmt : interest; amort = regular; }
        balance -= amort;
        lines.push({ number: k, paymentC: paid, principalC: amort, interestC: interest, balanceC: balance });
      }
    } else {
      const constant = divHalfEven(pv, BigInt(n));
      for (let k = 1; k <= n; k++) {
        const interest = interestOf(balance);
        const amort = k === n ? balance : (constant < balance ? constant : balance);
        balance -= amort;
        lines.push({ number: k, paymentC: amort + interest, principalC: amort, interestC: interest, balanceC: balance });
      }
    }
    return lines;
  }

  function monthlyIrr(releasedC, paymentsC) {
    const rel = releasedC / 100, pays = paymentsC.map(p => p / 100);
    const npv = r => { const b = 1 + r; let f = 1, t = -rel; for (const p of pays) { f *= b; t += p / f; } return t; };
    if (npv(0) <= 0) return 0;
    let lo = 0, hi = 10;
    for (let i = 0; i < 200; i++) { const mid = (lo + hi) / 2; if (npv(mid) > 0) lo = mid; else hi = mid; }
    return Number(((lo + hi) / 2).toFixed(8));
  }

  function validateSimulation(system, amount, monthlyRate, term) {
    if (system !== 'PRICE' && system !== 'SAC') throw fail(400, 'system: must not be null');
    const a = Number(amount), r = Number(monthlyRate), t = Number(term);
    if (!(a >= 0.01)) throw fail(400, 'amount: must be greater than or equal to 0.01');
    if (a > 1000000000) throw fail(400, 'amount: must be less than or equal to 1000000000');
    if (!(r >= 0)) throw fail(400, 'monthlyRate: must be greater than or equal to 0.0');
    if (r > 1) throw fail(400, 'monthlyRate: must be less than or equal to 1');
    if (!Number.isInteger(t) || t < 1) throw fail(400, 'term: must be greater than or equal to 1');
    if (t > MAX_TERM) throw fail(400, 'term: must be less than or equal to ' + MAX_TERM);
    const pvC = toCents(a, 'amount'), rateN = toRateScaled(r);
    if (pvC < t) throw fail(400, 'amount too small: at least 0.01 per installment');
    return { pvC, rateN, term: t };
  }

  function simulate(system, amount, monthlyRate, term) {
    const { pvC, rateN, term: n } = validateSimulation(system, amount, monthlyRate, term);
    const lines = schedule(system, pvC, rateN, n);
    const num = v => Number(v) / 100;
    const totalPaid = lines.reduce((a, l) => a + l.paymentC, 0n);
    const totalInterest = lines.reduce((a, l) => a + l.interestC, 0n);
    const cetMonthly = monthlyIrr(pvC, lines.map(l => Number(l.paymentC)));
    return {
      system, amount: pvC / 100, term: n, monthlyRate: rateN / 1e8,
      installments: lines.map(l => ({ number: l.number, payment: num(l.paymentC), principal: num(l.principalC), interest: num(l.interestC), balance: num(l.balanceC) })),
      totalPaid: num(totalPaid), totalInterest: num(totalInterest),
      cetMonthly, cetAnnual: Number((Math.pow(1 + cetMonthly, 12) - 1).toFixed(8)),
      _lines: lines
    };
  }

  /* ---------- datas ---------- */
  function plusMonths(iso, k) {
    const [y, m, d] = iso.split('-').map(Number);
    const total = m - 1 + k, ny = y + Math.floor(total / 12), nm = total % 12;
    const last = new Date(Date.UTC(ny, nm + 1, 0)).getUTCDate();
    return new Date(Date.UTC(ny, nm, Math.min(d, last))).toISOString().slice(0, 10);
  }
  const plusDays = (iso, n) => new Date(Date.parse(iso + 'T00:00:00Z') + n * 86400000).toISOString().slice(0, 10);
  const daysBetween = (a, b) => Math.round((Date.parse(b + 'T00:00:00Z') - Date.parse(a + 'T00:00:00Z')) / 86400000);

  /* ---------- persistencia (localStorage; memoria nos testes) ---------- */
  const memory = {};
  const browserStorage = () => { try { return root.localStorage || null; } catch (e) { return null; } };
  const storage = () => browserStorage() || {
    getItem: k => (k in memory ? memory[k] : null), setItem: (k, v) => { memory[k] = v; }
  };
  let db = null, policyVersion = 'pol-00000000';
  const emptyDb = () => ({ v: 1, seq: { p: 0, h: 0, r: 0, c: 0, i: 0, o: 0 }, proposals: [], history: [], results: [], contracts: [], installments: [], outcomes: [] });
  const save = () => { try { storage().setItem(STORE_KEY, JSON.stringify(db)); } catch (e) { /* sem persistencia */ } };
  const nextId = k => ++db.seq[k];

  async function computePolicyVersion() {
    const bytes = new TextEncoder().encode(POLICY_FINGERPRINT);
    const digest = await root.crypto.subtle.digest('SHA-256', bytes);
    return 'pol-' + [...new Uint8Array(digest)].slice(0, 4).map(b => b.toString(16).padStart(2, '0')).join('');
  }

  /* ---------- dominio ---------- */
  function move(p, to, reason) {
    if (!ALLOWED[p.status].includes(to)) throw fail(409, 'Transicao invalida: ' + p.status + ' -> ' + to);
    db.history.push({ id: nextId('h'), proposalId: p.id, from: p.status, to, reason: reason.slice(0, 500), at: new Date().toISOString() });
    p.status = to;
  }

  const findProposal = id => db.proposals.find(p => p.id === Number(id));
  function guardProposal(me, id) {
    const p = findProposal(id);
    if (!p || (me.role !== 'ANALYST' && p.customerId !== me.customerId)) throw fail(404, 'Proposta ' + id + ' nao encontrada');
    return p;
  }
  const requireAnalyst = me => { if (me.role !== 'ANALYST') throw fail(403, 'Acesso negado'); };

  const proposalView = p => ({ id: p.id, customerId: p.customerId, amount: p.amountC / 100, term: p.term, monthlyRate: p.rateN / 1e8, system: p.system, status: p.status, validUntil: p.validUntil || null });

  function createProposal(me, b) {
    if (!(Number(b.customerId) > 0)) throw fail(400, 'customerId: must be greater than 0');
    simulate(b.system, b.amount, b.monthlyRate, b.term);
    const income = Number(b.monthlyIncome), commit = Number(b.existingCommitments), months = Number(b.relationshipMonths);
    if (!(income >= 0) || income > 1e9) throw fail(400, 'monthlyIncome: fora do intervalo permitido');
    if (!(commit >= 0) || commit > 1e9) throw fail(400, 'existingCommitments: fora do intervalo permitido');
    if (!Number.isInteger(months) || months < 0 || months > 1200) throw fail(400, 'relationshipMonths: fora do intervalo permitido');
    if (me && me.role !== 'ANALYST' && me.customerId !== Number(b.customerId)) throw fail(403, 'Cliente nao autorizado');
    const p = {
      id: nextId('p'), customerId: Number(b.customerId), amountC: toCents(b.amount, 'amount'), term: Number(b.term),
      rateN: toRateScaled(b.monthlyRate), system: b.system, status: 'SIMULADA', incomeC: toCents(income, 'monthlyIncome'),
      commitC: toCents(commit, 'existingCommitments'), months, validUntil: null
    };
    db.proposals.push(p);
    db.history.push({ id: nextId('h'), proposalId: p.id, from: null, to: 'SIMULADA', reason: 'Simulacao criada', at: new Date().toISOString() });
    return p;
  }

  const money2 = c => (c / 100).toFixed(2);
  function ratio4String(r) { const s = r.toString().padStart(5, '0'); return s.slice(0, -4) + '.' + s.slice(-4); }

  function scoreOf(customerId) {
    if (customerId === 3) return { unavailable: true, delay: 1300 };
    if (customerId === 4) return { unavailable: true, delay: 900 };
    return { score: customerId === 2 ? 300 : 720 };
  }

  async function evaluatePolicy(p, firstPaymentC, withDelay) {
    const trail = [];
    const done = verdict => ({ verdict, trail, reason: trail[trail.length - 1].detail });
    if (p.incomeC < POLICY.minIncomeCents) {
      trail.push({ rule: 'MIN_INCOME', outcome: 'REJECT', detail: 'Renda ' + money2(p.incomeC) + ' abaixo do minimo 1000.00' });
      return done('REJECTED');
    }
    trail.push({ rule: 'MIN_INCOME', outcome: 'PASS', detail: 'Renda ' + money2(p.incomeC) + ' atende o minimo 1000.00' });
    const ratio = divHalfEven(BigInt(p.commitC + firstPaymentC) * 10000n, BigInt(p.incomeC));
    const ratioDetail = 'Comprometimento ' + ratio4String(ratio) + ' (limite 0.30)';
    if (ratio > POLICY.maxRatio4) { trail.push({ rule: 'INCOME_COMMITMENT', outcome: 'REJECT', detail: ratioDetail }); return done('REJECTED'); }
    trail.push({ rule: 'INCOME_COMMITMENT', outcome: 'PASS', detail: ratioDetail });
    const relDetail = 'Relacionamento de ' + p.months + ' meses (minimo ' + POLICY.minMonths + ')';
    if (p.months < POLICY.minMonths) { trail.push({ rule: 'RELATIONSHIP', outcome: 'REJECT', detail: relDetail }); return done('REJECTED'); }
    trail.push({ rule: 'RELATIONSHIP', outcome: 'PASS', detail: relDetail });
    const sc = scoreOf(p.customerId);
    if (sc.unavailable) {
      if (withDelay) await sleep(sc.delay);
      trail.push({ rule: 'SCORE', outcome: 'MANUAL', detail: 'Servico de score indisponivel; encaminhado para analise manual' });
      return done('MANUAL_REVIEW');
    }
    const scDetail = 'Score ' + sc.score + ' (minimo ' + POLICY.minScore + ')';
    if (sc.score < POLICY.minScore) { trail.push({ rule: 'SCORE', outcome: 'REJECT', detail: scDetail }); return done('REJECTED'); }
    trail.push({ rule: 'SCORE', outcome: 'PASS', detail: scDetail });
    return done('APPROVED');
  }

  function approve(p, reason) {
    move(p, 'APROVADA', reason);
    p.validUntil = plusDays(today(), 7);
  }

  async function submit(me, id, withDelay) {
    const p = guardProposal(me, id);
    if (p.status !== 'EM_ANALISE') move(p, 'EM_ANALISE', 'Proposta enviada para analise');
    const first = schedule(p.system, p.amountC, p.rateN, p.term)[0].paymentC;
    const d = await evaluatePolicy(p, Number(first), withDelay);
    d.trail.forEach(e => db.results.push({ id: nextId('r'), proposalId: p.id, rule: e.rule, outcome: e.outcome, detail: e.detail, policyVersion }));
    if (d.verdict === 'APPROVED') approve(p, d.reason);
    else if (d.verdict === 'REJECTED') move(p, 'RECUSADA', d.reason);
    else move(p, 'ANALISE_MANUAL', d.reason);
    return p;
  }

  function manualDecision(me, id, b) {
    requireAnalyst(me);
    const p = guardProposal(me, id);
    if (typeof b.approved !== 'boolean') throw fail(400, 'approved: must not be null');
    const reason = String(b.reason || '').trim();
    if (!reason) throw fail(400, 'reason: must not be blank');
    if (reason.length > 400) throw fail(400, 'reason: size must be between 0 and 400');
    if (hasControl(reason)) throw fail(400, 'reason: caracteres de controle nao permitidos');
    if (p.status !== 'ANALISE_MANUAL') throw fail(409, 'Decisao manual so se aplica a proposta em ANALISE_MANUAL');
    if (b.approved) approve(p, 'Analise manual: ' + reason);
    else move(p, 'RECUSADA', 'Analise manual: ' + reason);
    return p;
  }

  const installmentView = i => ({ number: i.number, dueDate: i.dueDate, principal: i.principalC / 100, interest: i.interestC / 100, amount: i.amountC / 100, status: i.status, paidAmount: i.paidC == null ? null : i.paidC / 100 });
  const contractView = c => ({ id: c.id, proposalId: c.proposalId, signedAt: c.signedAt, installments: db.installments.filter(i => i.contractId === c.id).sort((a, b) => a.number - b.number).map(installmentView) });

  function contract(me, id, key) {
    if (typeof key !== 'string' || !key.trim() || key.length > 100 || hasControl(key)) throw fail(400, 'Idempotency-Key deve ter de 1 a 100 caracteres');
    guardProposal(me, id);
    const byKey = db.contracts.find(c => c.key === key);
    if (byKey) {
      if (byKey.proposalId !== Number(id)) throw fail(409, 'Idempotency-Key ja usada em outra proposta');
      return { view: contractView(byKey), created: false };
    }
    const p = guardProposal(me, id);
    const existing = db.contracts.find(c => c.proposalId === p.id);
    if (existing) return { view: contractView(existing), created: false };
    if (p.status === 'APROVADA' && p.validUntil && p.validUntil < today()) throw fail(409, 'Proposta com validade vencida');
    move(p, 'CONTRATADA', 'Aceite do cliente');
    const c = { id: nextId('c'), proposalId: p.id, key, signedAt: new Date().toISOString() };
    db.contracts.push(c);
    const base = c.signedAt.slice(0, 10);
    schedule(p.system, p.amountC, p.rateN, p.term).forEach(l => db.installments.push({
      id: nextId('i'), contractId: c.id, number: l.number, dueDate: plusMonths(base, l.number), principalC: Number(l.principalC),
      interestC: Number(l.interestC), amountC: Number(l.paymentC), status: 'PENDENTE', paidC: null
    }));
    move(p, 'ATIVA', 'Liberacao e parcelas geradas');
    return { view: contractView(c), created: true };
  }

  function prepay(me, contractId, number) {
    const c = db.contracts.find(x => x.id === Number(contractId));
    if (!c) throw fail(404, 'Contrato nao encontrado');
    try { guardProposal(me, c.proposalId); } catch (e) { throw fail(404, 'Contrato nao encontrado'); }
    const i = db.installments.find(x => x.contractId === c.id && x.number === Number(number));
    if (!i) throw fail(404, 'Parcela nao encontrada');
    if (i.status !== 'PENDENTE') throw fail(409, 'Parcela ja quitada');
    const rate = findProposal(c.proposalId).rateN;
    const days = daysBetween(today(), i.dueDate);
    let pay = i.amountC;
    if (days > 0) {
      const den = 30n * E8 + BigInt(rate) * BigInt(days);
      const discounted = Number(divHalfEven(BigInt(i.amountC) * 30n * E8, den));
      pay = Math.max(discounted, i.principalC);
    }
    i.status = 'ANTECIPADA'; i.paidC = pay;
    return installmentView(i);
  }

  /* ---------- desfechos ---------- */
  function signalOf(decision, type) {
    if (decision === 'APROVADA' && (type === 'CHARGEBACK' || type === 'INADIMPLENCIA')) return 'POSSIVEL_FALSO_POSITIVO';
    if (decision === 'RECUSADA' && type === 'CONTESTACAO') return 'POSSIVEL_FALSO_NEGATIVO';
    if (decision === 'APROVADA' && type === 'QUITACAO') return 'DECISAO_CONFIRMADA';
    return 'INCONCLUSIVO';
  }
  function contextOf(proposalId) {
    const hist = db.history.filter(h => h.proposalId === proposalId);
    const decision = hist.some(h => h.to === 'APROVADA') ? 'APROVADA' : hist.some(h => h.to === 'RECUSADA') ? 'RECUSADA' : 'SEM_DECISAO';
    const res = db.results.find(r => r.proposalId === proposalId);
    return { decision, policyVersion: res ? res.policyVersion : 'n/a' };
  }
  const outcomeView = (o, ctx) => ({ id: o.id, proposalId: o.proposalId, type: o.type, detail: o.detail, occurredOn: o.occurredOn, recordedAt: o.recordedAt, recordedBy: o.recordedBy, policyVersion: ctx.policyVersion, decision: ctx.decision, signal: signalOf(ctx.decision, o.type) });

  function recordOutcome(me, id, b, by) {
    requireAnalyst(me);
    const p = guardProposal(me, id);
    if (!OUTCOME_TYPES.includes(b.type)) throw fail(400, 'type: must not be null');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(b.occurredOn || ''))) throw fail(400, 'occurredOn: must not be null');
    if (b.detail && String(b.detail).length > 400) throw fail(400, 'detail: size must be between 0 and 400');
    if (hasControl(b.detail)) throw fail(400, 'detail: caracteres de controle nao permitidos');
    if (b.type === 'CONTESTACAO') {
      if (!DECIDED.includes(p.status)) throw fail(409, 'Proposta ainda sem decisao: nao ha o que contestar');
    } else if (p.status !== 'ATIVA') throw fail(409, 'Desfecho ' + b.type + ' exige proposta ATIVA (com contrato)');
    if (b.occurredOn > today()) throw fail(400, 'occurredOn nao pode estar no futuro');
    const o = { id: nextId('o'), proposalId: p.id, type: b.type, detail: b.detail || null, occurredOn: b.occurredOn, recordedBy: by, recordedAt: new Date().toISOString() };
    db.outcomes.push(o);
    return outcomeView(o, contextOf(p.id));
  }

  function summary(me) {
    requireAnalyst(me);
    const counts = new Map();
    [...db.outcomes].sort((a, b) => b.id - a.id).slice(0, 1000).forEach(o => {
      const ctx = contextOf(o.proposalId), signal = signalOf(ctx.decision, o.type);
      const k = [ctx.policyVersion, ctx.decision, o.type, signal].join('|');
      counts.set(k, (counts.get(k) || 0) + 1);
    });
    return [...counts.entries()].map(([k, count]) => { const [pv, decision, type, signal] = k.split('|'); return { policyVersion: pv, decision, type, signal, count }; })
      .sort((a, b) => a.policyVersion.localeCompare(b.policyVersion) || a.decision.localeCompare(b.decision) || a.type.localeCompare(b.type));
  }

  /* ---------- dados de exemplo (os mesmos do modo demonstracao do backend) ---------- */
  async function seed() {
    const analyst = { role: 'ANALYST', username: 'demo' };
    const mk = async (customer, amount, term, system, doSubmit, doContract) => {
      const p = createProposal(null, { customerId: customer, system, amount, monthlyRate: SEED_RATE, term, monthlyIncome: 8000, existingCommitments: 0, relationshipMonths: 24 });
      if (doSubmit) await submit(analyst, p.id, false);
      if (doContract) contract(analyst, p.id, 'demo-seed-' + p.id);
      return p.id;
    };
    const active = await mk(1, 10000, 12, 'PRICE', true, true);
    await mk(1, 25000, 36, 'SAC', true, false);
    const refused = await mk(2, 15000, 24, 'PRICE', true, false);
    await mk(3, 8000, 12, 'PRICE', true, false);
    await mk(4, 5000, 6, 'SAC', true, false);
    await mk(1, 3000, 6, 'PRICE', false, false);
    recordOutcome(analyst, active, { type: 'QUITACAO', detail: 'Primeira parcela paga em dia', occurredOn: today() }, 'demo');
    recordOutcome(analyst, refused, { type: 'CONTESTACAO', detail: 'Cliente contestou a recusa e apresentou comprovante de renda', occurredOn: today() }, 'demo');
  }

  async function load() {
    let raw = null;
    try { raw = JSON.parse(storage().getItem(STORE_KEY)); } catch (e) { raw = null; }
    if (raw && raw.v === 1) { db = raw; return; }
    db = emptyDb();
    await seed();
    save();
  }

  const ready = computePolicyVersion().then(v => { policyVersion = v; return load(); });

  async function reset() {
    await ready;
    db = emptyDb();
    await seed();
    save();
  }

  /* ---------- contas, autenticacao e roteamento (espelha a API REST) ---------- */
  const ACCOUNTS = {
    analyst: { password: ANALYST_PASSWORD, role: 'ANALYST', customerId: null },
    'customer-1': { password: CUSTOMER_PASSWORD, role: 'CUSTOMER', customerId: 1 },
    'customer-2': { password: CUSTOMER_PASSWORD, role: 'CUSTOMER', customerId: 2 },
    'customer-3': { password: CUSTOMER_PASSWORD, role: 'CUSTOMER', customerId: 3 },
    'customer-4': { password: CUSTOMER_PASSWORD, role: 'CUSTOMER', customerId: 4 }
  };
  const DEMO_BUTTONS = [
    { label: 'Entrar como Cliente', username: 'customer-1', password: CUSTOMER_PASSWORD },
    { label: 'Cliente com score instável', username: 'customer-3', password: CUSTOMER_PASSWORD },
    { label: 'Entrar como Analista', username: 'analyst', password: ANALYST_PASSWORD }
  ];

  function authenticate(headers) {
    const raw = headers.authorization || headers.Authorization || '';
    if (!raw.startsWith('Basic ')) return null;
    let text;
    try { text = new TextDecoder().decode(Uint8Array.from(root.atob(raw.slice(6)), c => c.charCodeAt(0))); } catch (e) { return null; }
    const i = text.indexOf(':');
    const username = text.slice(0, i), password = text.slice(i + 1);
    const a = ACCOUNTS[username];
    return a && a.password === password ? { username, role: a.role, customerId: a.customerId } : null;
  }

  async function route(method, url, body, headers) {
    await ready;
    const path = url.split('?')[0];
    if (method === 'GET' && path === '/api/demo') return { status: 200, data: { enabled: true, accounts: DEMO_BUTTONS } };
    const me = authenticate(headers);
    if (!me) throw fail(401, 'Nao autenticado');
    let m;
    const ok = (data, status = 200) => { save(); return { status, data }; };

    if (method === 'GET' && path === '/api/me') return { status: 200, data: { username: me.username, role: me.role, customerId: me.customerId } };
    if (method === 'POST' && path === '/api/simulations') { const r = simulate(body.system, body.amount, body.monthlyRate, body.term); delete r._lines; return { status: 200, data: r }; }
    if (path === '/api/proposals') {
      if (method === 'GET') return { status: 200, data: db.proposals.filter(p => me.role === 'ANALYST' || p.customerId === me.customerId).sort((a, b) => b.id - a.id).slice(0, 50).map(proposalView) };
      if (method === 'POST') return ok(proposalView(createProposal(me, body)), 201);
    }
    if (method === 'GET' && path === '/api/outcomes/summary') return { status: 200, data: summary(me) };
    if ((m = path.match(/^\/api\/proposals\/(\d+)(?:\/([a-z-]+))?$/))) {
      const id = Number(m[1]), sub = m[2] || '';
      if (method === 'GET') {
        const p = guardProposal(me, id);
        if (sub === '') return { status: 200, data: proposalView(p) };
        if (sub === 'history') return { status: 200, data: db.history.filter(h => h.proposalId === id).map(h => ({ from: h.from, to: h.to, reason: h.reason, at: h.at })) };
        if (sub === 'policy-results') return { status: 200, data: db.results.filter(r => r.proposalId === id).map(r => ({ rule: r.rule, outcome: r.outcome, detail: r.detail, policyVersion: r.policyVersion })) };
        if (sub === 'contract') { const c = db.contracts.find(x => x.proposalId === id); if (!c) throw fail(404, 'Proposta sem contrato'); return { status: 200, data: contractView(c) }; }
        if (sub === 'outcomes') { const ctx = contextOf(id); return { status: 200, data: db.outcomes.filter(o => o.proposalId === id).map(o => outcomeView(o, ctx)) }; }
      }
      if (method === 'POST') {
        if (sub === 'submit') return ok(proposalView(await submit(me, id, true)));
        if (sub === 'manual-decision') return ok(proposalView(manualDecision(me, id, body)));
        if (sub === 'contract') { const r = contract(me, id, headers['Idempotency-Key'] || headers['idempotency-key']); return ok(r.view, r.created ? 201 : 200); }
        if (sub === 'outcomes') return ok(recordOutcome(me, id, body, me.username), 201);
      }
    }
    if (method === 'POST' && (m = path.match(/^\/api\/contracts\/(\d+)\/installments\/(\d+)\/prepay$/))) return ok(prepay(me, m[1], m[2]));
    throw fail(404, 'Recurso nao encontrado');
  }

  async function handle(url, init) {
    const method = ((init && init.method) || 'GET').toUpperCase();
    const headers = {};
    const h = init && init.headers;
    if (h && typeof h.forEach === 'function' && !Array.isArray(h)) h.forEach((v, k) => { headers[k] = v; });
    else if (h) Object.assign(headers, h);
    let body = {};
    if (init && typeof init.body === 'string' && init.body) {
      try { body = JSON.parse(init.body); } catch (e) { return respond(400, { title: 'Bad Request', status: 400, detail: 'JSON invalido' }); }
    }
    try {
      const r = await route(method, url, body, headers);
      return respond(r.status, r.data);
    } catch (e) {
      const status = e.status || 500;
      return respond(status, { title: 'Error', status, detail: e.status ? e.message : 'Erro interno' });
    }
  }
  const respond = (status, data) => new root.Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });

  const api = { ready, handle, reset, simulate, signalOf, policyVersion: () => policyVersion, _db: () => db };
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
    return;
  }

  /* ---------- navegador: so atende /api/* quando nao ha backend ---------- */
  const realFetch = root.fetch.bind(root);
  const backendPresent = realFetch('/api/me', { credentials: 'omit' })
    .then(r => r.status === 401 || r.status === 200)
    .catch(() => false);
  root.fetch = async function (input, init) {
    const url = typeof input === 'string' ? input : input.url;
    if (!url.startsWith('/api/') || await backendPresent) return realFetch(input, init);
    return handle(url, init);
  };
  root.CreditFlowDemo = { reset: () => reset().then(() => root.location.reload()) };
})(typeof window !== 'undefined' ? window : globalThis);
