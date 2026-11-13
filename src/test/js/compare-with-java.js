// Manual: compara a simulacao do demo.js com a do backend Java em centenas de casos aleatorios.
//   node src/test/js/compare-with-java.js http://localhost:8080 <usuario> <senha>
const Demo = require('../../main/resources/static/assets/demo.js');

const [javaUrl, user, password] = process.argv.slice(2);
const auth = 'Basic ' + Buffer.from(`${user}:${password}`).toString('base64');
let seed = 20260101;
const rnd = () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296;

(async () => {
  let checked = 0, diffs = 0;
  for (let i = 0; i < 400; i++) {
    const term = 1 + Math.floor(rnd() * (i % 10 === 0 ? 480 : 120));
    const amount = Number((Math.round(term + rnd() * 2000000) / 100 + term / 100).toFixed(2));
    const body = { system: i % 2 ? 'SAC' : 'PRICE', amount, monthlyRate: Math.floor(rnd() * 800) / 10000, term };
    const res = await fetch(javaUrl + '/api/simulations', {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: auth }, body: JSON.stringify(body)
    });
    const java = await res.json();
    const mine = Demo.simulate(body.system, body.amount, body.monthlyRate, body.term);
    const same = (a, b) => Math.abs(a - b) < 1e-9;
    let bad = !same(java.totalPaid, mine.totalPaid) || !same(java.totalInterest, mine.totalInterest)
      || !same(java.cetMonthly, mine.cetMonthly) || !same(java.cetAnnual, mine.cetAnnual);
    for (let k = 0; !bad && k < java.installments.length; k++) {
      const a = java.installments[k], b = mine.installments[k];
      bad = !(same(a.payment, b.payment) && same(a.principal, b.principal) && same(a.interest, b.interest) && same(a.balance, b.balance));
    }
    checked++;
    if (bad) { diffs++; console.log('DIFF', JSON.stringify(body)); }
  }
  console.log(`comparados=${checked} diferencas=${diffs}`);
  process.exit(diffs ? 1 : 0);
})();
