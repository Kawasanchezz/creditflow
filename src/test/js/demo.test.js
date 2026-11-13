// Testes da demonstracao estatica (assets/demo.js): `node --test src/test/js`
const test = require('node:test');
const assert = require('node:assert/strict');
const Demo = require('../../main/resources/static/assets/demo.js');

const basic = (user, pw) => 'Basic ' + Buffer.from(`${user}:${pw}`).toString('base64');
const CUSTOMER_1 = { Authorization: basic('customer-1', 'demo-cliente') };
const CUSTOMER_2 = { Authorization: basic('customer-2', 'demo-cliente') };
const CUSTOMER_3 = { Authorization: basic('customer-3', 'demo-cliente') };
const ANALYST = { Authorization: basic('analyst', 'demo-analista') };
const NUL = String.fromCharCode(0);

async function call(method, url, body, headers = {}) {
  const res = await Demo.handle(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  return { status: res.status, body: await res.json() };
}
const proposalBody = (customer, extra = {}) => ({
  customerId: customer, system: 'PRICE', amount: 3000, monthlyRate: 0.01, term: 6,
  monthlyIncome: 9000, existingCommitments: 0, relationshipMonths: 24, ...extra
});

test.beforeEach(async () => { await Demo.reset(); });

test('Price: exemplo do README e CET', () => {
  const r = Demo.simulate('PRICE', 10000, 0.0199, 12);
  assert.equal(r.installments[0].payment, 945.02);
  assert.equal(r.installments[0].interest, 199);
  assert.equal(r.installments[0].balance, 9253.98);
  assert.equal(r.totalInterest, 1340.17);
  assert.equal(r.installments[11].payment, 944.95);
  assert.ok(Math.abs(r.cetAnnual - 0.2667) < 0.001);
});

test('Price: tabela conferida em planilha', () => {
  const rows = [[10000, 0.01, 12, 888.49], [100000, 0.01, 360, 1028.61], [5000, 0.02, 6, 892.63], [1200, 0, 12, 100], [20000, 0.0199, 48, 650.71]];
  for (const [pv, i, n, pmt] of rows) {
    assert.equal(Demo.simulate('PRICE', pv, i, n).installments[0].payment, pmt, `${pv}/${i}/${n}`);
  }
});

test('arredondamento HALF_EVEN: 13.465 vira 13.46', () => {
  const r = Demo.simulate('PRICE', 1000, 0.02, 3);
  assert.equal(r.installments[1].interest, 13.46);
  assert.equal(r.installments[2].balance, 0);
});

test('SAC: ultima parcela absorve o residuo e juros totais', () => {
  const r = Demo.simulate('SAC', 1000, 0.01, 3);
  assert.deepEqual(r.installments.map(l => l.principal), [333.33, 333.33, 333.34]);
  assert.equal(Demo.simulate('SAC', 12000, 0.01, 12).totalInterest, 780);
  assert.equal(Demo.simulate('SAC', 10000, 0.0199, 12).totalInterest, 1293.5);
});

test('propriedade: soma das amortizacoes igual ao valor e saldo final zero', () => {
  let seed = 42;
  const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;
  for (const system of ['PRICE', 'SAC']) {
    for (let k = 0; k < 150; k++) {
      const n = 1 + Math.floor(rnd() * 360);
      const pv = (n * 100 + Math.floor(rnd() * 2000000)) / 100;
      const rate = Math.floor(rnd() * 800) / 10000;
      const r = Demo.simulate(system, pv, rate, n);
      const sum = r.installments.reduce((a, l) => a + Math.round(l.principal * 100), 0);
      assert.equal(sum, Math.round(pv * 100), `${system} ${pv} ${rate} ${n}`);
      assert.equal(r.installments[n - 1].balance, 0);
    }
  }
});

test('entradas invalidas sao recusadas', async () => {
  const base = { system: 'PRICE', amount: 1000, monthlyRate: 0.01, term: 12 };
  for (const bad of [{ term: 481 }, { term: 0 }, { amount: -1 }, { amount: 0.05, term: 6 }, { monthlyRate: 5 }, { system: 'XYZ' }]) {
    assert.equal((await call('POST', '/api/simulations', { ...base, ...bad }, CUSTOMER_1)).status, 400, JSON.stringify(bad));
  }
});

test('versao da politica igual a do backend Java', () => {
  assert.equal(Demo.policyVersion(), 'pol-378f6f7c');
});

test('autenticacao e papeis', async () => {
  assert.equal((await call('GET', '/api/me')).status, 401);
  assert.equal((await call('GET', '/api/me', undefined, { Authorization: basic('customer-1', 'errada') })).status, 401);
  assert.equal((await call('GET', '/api/me', undefined, CUSTOMER_1)).body.role, 'CUSTOMER');
  assert.equal((await call('GET', '/api/demo')).body.accounts.length, 3);
  assert.equal((await call('GET', '/api/outcomes/summary', undefined, CUSTOMER_1)).status, 403);
  assert.equal((await call('GET', '/api/outcomes/summary', undefined, ANALYST)).status, 200);
});

test('dados de exemplo respeitam o escopo de cada cliente', async () => {
  const mine = (await call('GET', '/api/proposals', undefined, CUSTOMER_1)).body;
  assert.deepEqual(new Set(mine.map(p => p.status)), new Set(['ATIVA', 'APROVADA', 'SIMULADA']));
  assert.equal((await call('GET', '/api/proposals', undefined, ANALYST)).body.length, 6);
  assert.equal((await call('GET', '/api/proposals/1', undefined, CUSTOMER_2)).status, 404);
  assert.equal((await call('POST', '/api/proposals', proposalBody(2), CUSTOMER_1)).status, 403);
});

test('fluxo completo com contratacao idempotente e antecipacao', async () => {
  const id = (await call('POST', '/api/proposals', proposalBody(1), CUSTOMER_1)).body.id;
  assert.equal((await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_1, 'Idempotency-Key': 'k' })).status, 409);
  assert.equal((await call('POST', `/api/proposals/${id}/submit`, undefined, CUSTOMER_1)).body.status, 'APROVADA');
  const first = await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_1, 'Idempotency-Key': 'key-1' });
  const again = await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_1, 'Idempotency-Key': 'key-1' });
  const other = await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_1, 'Idempotency-Key': 'key-2' });
  assert.deepEqual([first.status, again.status, other.status], [201, 200, 200]);
  assert.equal(first.body.id, again.body.id);
  assert.equal(first.body.installments.length, 6);
  const paid = await call('POST', `/api/contracts/${first.body.id}/installments/6/prepay`, undefined, CUSTOMER_1);
  assert.equal(paid.body.status, 'ANTECIPADA');
  assert.ok(paid.body.paidAmount < paid.body.amount);
  assert.equal((await call('POST', `/api/contracts/${first.body.id}/installments/6/prepay`, undefined, CUSTOMER_1)).status, 409);
});

test('chave de idempotencia: tamanho, caractere de controle e propriedade', async () => {
  const id = (await call('POST', '/api/proposals', proposalBody(1), CUSTOMER_1)).body.id;
  await call('POST', `/api/proposals/${id}/submit`, undefined, CUSTOMER_1);
  for (const key of ['k'.repeat(101), ' ', `a${NUL}b`]) {
    assert.equal((await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_1, 'Idempotency-Key': key })).status, 400);
  }
  assert.equal((await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_2, 'Idempotency-Key': 'demo-seed-1' })).status, 404);
});

test('score instavel cai em analise manual; decisao manual so nesse estado', async () => {
  const id = (await call('POST', '/api/proposals', proposalBody(3), CUSTOMER_3)).body.id;
  const body = { approved: true, reason: 'documentos ok' };
  assert.equal((await call('POST', `/api/proposals/${id}/manual-decision`, body, ANALYST)).status, 409);
  assert.equal((await call('POST', `/api/proposals/${id}/submit`, undefined, CUSTOMER_3)).body.status, 'ANALISE_MANUAL');
  assert.equal((await call('POST', `/api/proposals/${id}/manual-decision`, body, CUSTOMER_1)).status, 403);
  const invalid = [{ reason: 'x' }, { approved: null, reason: 'x' }, { approved: true, reason: '  ' }, { approved: true, reason: `a${NUL}b` }];
  for (const bad of invalid) {
    assert.equal((await call('POST', `/api/proposals/${id}/manual-decision`, bad, ANALYST)).status, 400, JSON.stringify(bad));
  }
  const ok = await call('POST', `/api/proposals/${id}/manual-decision`, body, ANALYST);
  assert.equal(ok.body.status, 'APROVADA');
  assert.ok(ok.body.validUntil);
});

test('proposta recusada nao pode ser contratada', async () => {
  const id = (await call('POST', '/api/proposals', proposalBody(2), CUSTOMER_2)).body.id;
  assert.equal((await call('POST', `/api/proposals/${id}/submit`, undefined, CUSTOMER_2)).body.status, 'RECUSADA');
  const r = await call('POST', `/api/proposals/${id}/contract`, undefined, { ...CUSTOMER_2, 'Idempotency-Key': 'x' });
  assert.equal(r.status, 409);
  assert.match(r.body.detail, /RECUSADA -> CONTRATADA/);
});

test('desfechos: regras, sinais e resumo', async () => {
  const day = new Date().toISOString().slice(0, 10);
  const undecided = (await call('POST', '/api/proposals', proposalBody(1), CUSTOMER_1)).body.id;
  assert.equal((await call('POST', `/api/proposals/${undecided}/outcomes`, { type: 'CONTESTACAO', occurredOn: day }, ANALYST)).status, 409);
  const ok = await call('POST', '/api/proposals/1/outcomes', { type: 'INADIMPLENCIA', detail: '30 dias', occurredOn: day }, ANALYST);
  assert.equal(ok.status, 201);
  assert.equal(ok.body.signal, 'POSSIVEL_FALSO_POSITIVO');
  assert.equal(ok.body.policyVersion, 'pol-378f6f7c');
  const future = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
  assert.equal((await call('POST', '/api/proposals/1/outcomes', { type: 'QUITACAO', occurredOn: future }, ANALYST)).status, 400);
  assert.equal((await call('POST', '/api/proposals/1/outcomes', { type: 'QUITACAO', detail: `a${NUL}b`, occurredOn: day }, ANALYST)).status, 400);
  assert.equal((await call('POST', '/api/proposals/1/outcomes', { type: 'QUITACAO', occurredOn: day }, CUSTOMER_1)).status, 403);
  assert.equal((await call('POST', '/api/proposals/3/outcomes', { type: 'CHARGEBACK', occurredOn: day }, ANALYST)).status, 409);
  const signals = new Set((await call('GET', '/api/outcomes/summary', undefined, ANALYST)).body.map(r => r.signal));
  for (const s of ['POSSIVEL_FALSO_POSITIVO', 'POSSIVEL_FALSO_NEGATIVO', 'DECISAO_CONFIRMADA']) assert.ok(signals.has(s), s);
});

test('rotas desconhecidas e corpo invalido', async () => {
  assert.equal((await call('GET', '/api/inexistente', undefined, CUSTOMER_1)).status, 404);
  const res = await Demo.handle('/api/simulations', { method: 'POST', headers: CUSTOMER_1, body: '{nao e json' });
  assert.equal(res.status, 400);
});
