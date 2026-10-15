// Deterministic artwork derived from the existing app vector and actual Android captures.
// This script composes store layouts; it does not recreate or retouch application UI.
const fs = require('node:fs');
const path = require('node:path');
let sharp;
try { sharp = require('sharp'); }
catch { sharp = require('C:/Users/tripl/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp'); }
const base = path.join(__dirname, 'en-US');
for (const dir of ['graphics', 'editable', 'phone']) fs.mkdirSync(path.join(base, dir), {recursive:true});
const save = (file, value) => fs.writeFileSync(path.join(base, file), value);
const xml = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;');
const svg = (w,h,body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`;
const text = (x,y,value,size=24,color='#243550',weight=400) => `<text x="${x}" y="${y}" font-family="Segoe UI,Arial,sans-serif" font-size="${size}" font-weight="${weight}" fill="${color}">${xml(value)}</text>`;
const bottle = `<path fill="#2D69CB" d="M20,5h8v7h-8zM16,12h16v5h-16z"/><path fill="#7BA7EB" d="M17,18h14l3,5v16q0,4 -4,4h-12q-4,0 -4,-4v-16z"/><path fill="#FFFFFF" d="M18,25h12v13h-12z"/><path fill="#2D69CB" d="M18,28h5v2h-5zM18,33h4v2h-4z"/>`;
const icon = svg(512,512,`<rect width="512" height="512" fill="#E6F0FD"/><g transform="scale(10.6666667)">${bottle}</g>`);
const feature = svg(1024,500,`
<rect width="1024" height="500" fill="#DFEBFC"/>
<circle cx="1000" cy="-60" r="290" fill="#D1E2FA"/>
<circle cx="26" cy="557" r="175" fill="#D3E5F9"/>
<path d="M657 106C586 190 615 355 729 403" fill="none" stroke="#B7D0F4" stroke-width="2" stroke-dasharray="5 10"/>
${text(80,108,'Baby Feed',24,'#2D69CB',650)}
${text(80,193,'Small moments.',49,'#243550',700)}
${text(80,255,'A clearer routine.',49,'#243550',700)}
${text(82,317,'Bottle feeds. Daily records. Reminders.',21,'#526681')}
<circle cx="785" cy="247" r="116" fill="#F6F9FF"/>
<circle cx="785" cy="247" r="93" fill="none" stroke="#A6C5F2" stroke-width="3"/>
<g stroke="#2D69CB" stroke-width="5" stroke-linecap="round"><path d="M785 165v9M867 247h-9M785 329v-9M703 247h9"/><path d="M785 194v53l35 23" stroke-width="7"/></g>
<circle cx="785" cy="247" r="7" fill="#2D69CB"/>
<circle cx="871" cy="342" r="49" fill="#E6F0FD" stroke="#F6F9FF" stroke-width="5"/>
<g transform="translate(833,304) scale(1.58)">${bottle}</g>
<circle cx="684" cy="140" r="9" fill="#7BA7EB"/><circle cx="914" cy="180" r="5" fill="#7BA7EB"/>`);

const shots = [
 ['01-feeds','Know the next feed','Your chosen time and amount, at a glance.','Baby Feed home screen with the next bottle reminder, milk amount and daily totals.'],
 ['02-report','See the day clearly','Milk totals and a timeline of daily records.','Daily report with milk consumed, milk discarded, Vitamin K2D3 status and feeding history.'],
 ['03-record','Keep a simple diary','Log milk consumed and milk left over.','Milk recording dialog with date, time, amount consumed and amount discarded fields.'],
 ['04-vitamin','Keep vitamin records','Check whether today is already recorded.','Vitamin K2D3 record dialog showing that a dose has already been recorded for the selected day.'],
 ['05-settings','Set your own routine','Choose sleep hours and vitamin reminders.','Settings screen with language, appearance, daily vitamin reminders and sleep hours.'],
 ['06-dark','A softer view at night','Switch between light, dark and system themes.','Baby Feed home screen in dark appearance with the next feeding reminder and daily totals.'],
];

(async () => {
 save('editable/app-icon.svg',icon);
 save('editable/feature-graphic.svg',feature);
 await sharp(Buffer.from(icon)).ensureAlpha().png().toFile(path.join(base,'graphics/app-icon.png'));
 await sharp(Buffer.from(feature)).removeAlpha().png().toFile(path.join(base,'graphics/feature-graphic.png'));
 const alts = {
  'graphics/app-icon.png':'Blue baby bottle with milk and measurement marks on a pale blue background.',
  'graphics/feature-graphic.png':'Baby Feed: Small moments. A clearer routine. A clock and bottle represent feeding reminders and daily records.'
 };
 for (let i=0; i<shots.length; i++) {
  const [name,heading,subtitle,alt] = shots[i];
  const source = path.join(base,'source',name+'.png');
  if (!fs.existsSync(source)) continue;
  const stats = await sharp(source).stats();
  if (stats.channels.slice(0,3).every(channel => channel.max === 0)) throw new Error('Blank device capture: '+name);
  const meta = await sharp(source).metadata();
  if (meta.width!==1080 || meta.height!==2400) throw new Error('Review screenshot crop for new device size');
  const dark = name === '06-dark';
  const bg = dark ? '#172538' : '#E6F0FD';
  const layout = svg(1080,1920,`
    <rect width="1080" height="1920" fill="${bg}"/>
    ${text(80,68,'BABY FEED',20,dark?'#A9C8F5':'#2D69CB',650)}
    ${text(80,145,heading,55,dark?'#F0F5FF':'#243550',700)}
    ${text(80,198,subtitle,27,dark?'#BBCBE0':'#526681')}
    <rect x="145" y="259" width="790" height="1613" rx="28" fill="${dark?'#29405C':'#C8DAF3'}"/>
    ${text(81,1895,`0${i+1} / 06`,15,dark?'#A9C8F5':'#526681')}
  `);
  // Remove only Android status/navigation bars. Keep the native app UI unchanged.
  const capture = await sharp(source).extract({left:0,top:100,width:1080,height:2250}).resize(760,1583).removeAlpha().png().toBuffer();
  await sharp(Buffer.from(layout)).composite([{input:capture,left:160,top:274}]).removeAlpha().png().toFile(path.join(base,'phone',name+'.png'));
  save('editable/'+name+'-layout.svg',layout);
  alts['phone/'+name+'.png'] = alt;
 }
 save('alt-text.json',JSON.stringify(alts,null,2)+'\n');
 const tiles = [];
 const header = await sharp(Buffer.from(feature)).resize(820,400).png().toBuffer();
 tiles.push({input:header,left:30,top:30});
 const smallIcon = await sharp(Buffer.from(icon)).resize(220,220).png().toBuffer();
 tiles.push({input:smallIcon,left:900,top:110});
 let count=0;
 for (const [name] of shots) {
  const file=path.join(base,'phone',name+'.png');
  if (!fs.existsSync(file)) continue;
  tiles.push({input:await sharp(file).resize(350,622).png().toBuffer(),left:30+(count%3)*385,top:470+Math.floor(count/3)*655});
  count++;
 }
 await sharp({create:{width:1200,height:1805,channels:3,background:'#F6F8FC'}}).composite(tiles).jpeg({quality:90}).toFile(path.join(base,'preview.jpg'));
 const validation = [];
 for (const file of Object.keys(alts)) {
  const m=await sharp(path.join(base,file)).metadata();
  validation.push({file,width:m.width,height:m.height,channels:m.channels,bytes:fs.statSync(path.join(base,file)).size});
 }
 for(const file of ['title.txt','short-description.txt','full-description.txt','release-notes.txt']) {
  const content=fs.readFileSync(path.join(base,file),'utf8').trim();
  validation.push({file,characters:content.length});
 }
 save('validation.json',JSON.stringify(validation,null,2)+'\n');
 console.log(JSON.stringify(validation,null,2));
})().catch(error=>{console.error(error);process.exitCode=1});
