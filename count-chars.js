const fs = require('fs');
const text = fs.readFileSync('d:/Asolo2/2936A/迭代.md', 'utf8').replace(/\r\n/g, '\n');
const re = /### (F-\d+) [^\n]+\n\n\*\*标题：\*\*[^\n]+\n\n\*\*描述：\*\*  \n([\s\S]+?)\n\n---/g;
console.log('题号 | 描述字数(含标点英文) | 纯汉字数 | 与300字差距');
console.log('-'.repeat(55));
const totals = [];
let m;
while ((m = re.exec(text)) !== null) {
  const id = m[1];
  const desc = m[2].trim();
  const all = desc.length;
  const han = (desc.match(/[\u4e00-\u9fff]/g) || []).length;
  const diff = all - 300;
  console.log(`${id} | ${all} | ${han} | ${diff > 0 ? '+' : ''}${diff}`);
  totals.push(all);
}
console.log('-'.repeat(55));
const avg = Math.round(totals.reduce((a, b) => a + b, 0) / totals.length);
console.log(`平均: ${avg}  最少: ${Math.min(...totals)}  最多: ${Math.max(...totals)}`);
