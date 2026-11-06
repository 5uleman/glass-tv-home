package com.gathin.tvhome;

/**
 * The page the TV serves to a phone: one self-contained HTML file, no external requests except the
 * footer's GitHub link. Kept out of {@link WallpaperUploadServer} so a unit test can read the exact
 * bytes that are served and check the size cap and the absence of outside URLs.
 * The page is kept byte-tight for that cap, so its inline script uses short names.
 */
final class UploadPage {

    /** The raw page must stay under this many UTF-8 bytes. */
    static final int MAX_BYTES = 20_480;

    static final String GITHUB_URL = "https://github.com/5uleman";

    static final String HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title>Send a wallpaper · Glass TV</title>
<style>
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;background:#0B0C0F;color:#F5F6F8;font:15px/1.5 system-ui,sans-serif}
.wrap{max-width:390px;margin:0 auto;min-height:100vh;padding:24px 20px 12px;display:flex;flex-direction:column}
header{display:flex;align-items:center;justify-content:space-between;gap:12px;font-weight:600}
.pill{display:inline-flex;align-items:center;gap:8px;border:1px solid #ffffff14;border-radius:999px;padding:6px 10px;color:#9AA0AA;font-size:12px;font-weight:500}
.pill i{width:6px;height:6px;border-radius:50%;background:#34D399}.pill.bad{color:#F87171}.pill.bad i{background:#F87171}
h1{font-size:28px;line-height:1.2;margin:24px 0 20px}
h2{font-size:16px;margin:0}
.card{background:#ffffff0a;border:1px solid #ffffff14;border-radius:24px;padding:16px}
.drop{display:flex;flex-direction:column;align-items:center;justify-content:center;width:100%;min-height:200px;padding:16px;border:1px dashed #ffffff33;border-radius:16px;background:#ffffff05;color:inherit;font:inherit;cursor:pointer}
.drop .i{margin-bottom:12px;color:#9AA0AA}
.drop b{color:#3B82F6;font-weight:500}
.btn{width:100%;min-height:52px;border:0;border-radius:14px;background:#3B82F6;color:#fff;font:inherit;font-weight:500;cursor:pointer;margin-top:12px}
.btn[disabled]{opacity:.4;cursor:not-allowed}
.btn.ghost{background:none;border:1px solid #ffffff24;color:#9AA0AA}
.btn.bad{background:#F87171;color:#1A0B0B}
.mini{min-height:44px;min-width:44px;padding:0 12px;border:1px solid #ffffff24;border-radius:10px;background:none;color:#9AA0AA;font:inherit;font-size:13px;font-weight:500;cursor:pointer;display:inline-flex;align-items:center;justify-content:center;gap:6px}
.meta{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:8px}
.meta>div{font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0}
.queue{display:flex;flex-direction:column;gap:4px;margin-top:10px}
.qrow{display:flex;align-items:center;gap:10px;padding:4px 0}
.qthumb{width:44px;height:44px;border-radius:10px;object-fit:cover;background:#151A24;flex:none}
.qmeta{min-width:0;flex:1}
.qline{display:flex;justify-content:space-between;gap:8px;font-size:13px}
.qname{font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.qstate{color:#9AA0AA;font-size:12px;white-space:nowrap}
.qrow.ok .qstate{color:#34D399}.qrow.bad .qstate{color:#F87171}
.pg{height:4px;border-radius:999px;background:#ffffff14;overflow:hidden}
.pg i{display:block;height:100%;width:0;background:#3B82F6;transition:width .2s linear}
.qpg{height:3px;margin-top:6px}
#panel{margin-top:12px}
.pgrow{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:8px;font-size:13px;font-weight:500}
.status{margin-top:12px;display:flex;flex-direction:column}
.status .t{margin:0;font-weight:600}
.status.ok .t{color:#34D399}.status.bad .t{color:#F87171}
.status .d{margin:2px 0 0;color:#9AA0AA;font-size:13px;overflow-wrap:anywhere}
#sact{align-self:flex-end;margin-top:10px}
.listsec{margin-top:28px}
.lhead{display:flex;align-items:center;justify-content:space-between;gap:12px}
.tools{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin:8px 0 10px;font-size:13px;color:#9AA0AA}
#mode{flex:1;min-width:110px}
#selbar{display:flex;gap:8px;flex-wrap:wrap}
.rows{display:flex;flex-direction:column;gap:8px}
.row{display:flex;align-items:center;gap:10px;padding:8px 10px;background:#ffffff0a;border:1px solid #ffffff14;border-radius:16px}
.row.active{border-color:#3b82f666}
.row.fresh{animation:fresh 3s ease-out forwards}
@keyframes fresh{0%,70%{border-color:#3b82f6e6}}
.check{display:flex;width:44px;height:44px;align-items:center;justify-content:center;flex:none}
.selmode .racts{display:none}
.check input{width:20px;height:20px;accent-color:#3B82F6;cursor:pointer}
.thumb{width:56px;height:56px;border-radius:12px;object-fit:cover;background:#151A24;flex:none}
.rmeta{min-width:0;flex:1}
.rline{display:flex;align-items:baseline;gap:8px}
.rname{margin:0;font-size:14px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;min-width:0}
.rsub{margin:0;font-size:12px;color:#9AA0AA;white-space:nowrap;flex:none}
.racts{display:flex;align-items:center;gap:4px;flex:none}
.act{width:44px;height:44px;padding:0;border:1px solid #ffffff24;border-radius:10px;background:none;color:#9AA0AA;display:flex;align-items:center;justify-content:center;cursor:pointer}
.act[aria-pressed=true]{background:#0284C7;border-color:#38BDF8;color:#fff}
.act.del{color:#F87171;border-color:transparent}
.i{width:20px;height:20px;stroke:currentColor;fill:none;stroke-width:1.75;stroke-linecap:round;stroke-linejoin:round}
.status .i{margin-right:6px}
.empty{padding:24px;text-align:center;border:1px dashed #ffffff1f;border-radius:16px}
.empty p{margin:0;font-weight:500}
.note{color:#9AA0AA;font-size:13px;margin:6px 0 12px;overflow-wrap:anywhere}
.foot{margin-top:auto;padding:32px 0 8px;text-align:center;color:#6B7280;font-size:12px}
.foot a{display:inline-flex;align-items:center;min-height:44px;padding:0 8px;color:inherit;text-decoration:none;font-weight:500}
.foot a:hover,.foot a:focus-visible{text-decoration:underline}
.sheet{position:fixed;inset:0;z-index:9;display:flex;align-items:flex-end;justify-content:center}
.sheetbg{position:absolute;inset:0;background:#00000099}
.sheetcard{position:relative;width:100%;max-width:390px;background:#11141D;border:1px solid #ffffff24;border-bottom:0;border-radius:24px 24px 0 0;padding:20px 20px 28px;text-align:center}
.sheetcard h3{margin:0 0 6px;font-size:17px}
:focus-visible{outline:2px solid #3B82F6;outline-offset:2px}
</style>
</head>
<body>
<div class="wrap">
<header><span>Glass TV</span><span class="pill" id="pill"><i></i><span id="pillText">Connected</span></span></header>
<h1>Send a wallpaper</h1>
<section class="card">
<button class="drop" id="pick"><svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 16.5V4m0 0L8 8m4-4 4 4M4.5 19.5h15"/></svg><b>Choose photos</b></button>
<input id="file" type="file" accept="image/*" multiple hidden>
<div id="chosen" hidden>
<div class="meta"><div id="fname"></div><button class="mini" id="change">Change</button></div>
<div class="queue" id="queue"></div>
</div>
<button class="btn" id="send" disabled>Send to TV</button>
<div id="panel" hidden>
<div class="pg"><i id="bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"></i></div>
<div class="pgrow"><span id="ptext" aria-live="polite">Sending…</span><button class="mini" id="cancel">Cancel</button></div>
</div>
<div class="status" id="status" role="status" aria-live="polite" hidden>
<p class="t"><span id="sicon"></span><span id="stitle"></span></p><p class="d" id="sdetail"></p>
<button class="mini" id="sact">Dismiss</button>
</div>
</section>
<section class="listsec">
<div class="lhead"><h2 id="listtitle" tabindex="-1">On your TV</h2><button class="mini" id="selectbtn" hidden>Select</button></div>
<div class="tools" id="tools" hidden><span id="mode"></span><button class="mini" id="cycle" hidden><svg class="i" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5v5h5M20 19v-5h-5M5.6 9a8 8 0 0 1 13-2.5M18.4 15a8 8 0 0 1-13 2.5"/></svg>Cycle all photos</button><span id="selbar" hidden><button class="mini" id="selall">Select all</button><button class="mini" id="dsel" disabled>Delete (0)</button><button class="mini" id="done">Done</button></span></div>
<div class="rows" id="list"><p class="note">Loading photos…</p></div>
</section>
<footer class="foot"><a href="https://github.com/5uleman" target="_blank" rel="noopener noreferrer">5ULEMAN</a></footer>
</div>
<div class="sheet" id="sheet" hidden>
<div class="sheetbg" id="sheetbg"></div>
<div class="sheetcard" role="dialog" aria-modal="true" aria-labelledby="sheettitle">
<h3 id="sheettitle">Delete this photo?</h3>
<p class="note" id="sheettext"></p>
<button class="btn bad" id="dodelete">Delete photo</button>
<button class="btn ghost" id="nocancel">Cancel</button>
</div>
</div>
<template id="iLock"><svg class="i" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg></template>
<template id="iTrash"><svg class="i" viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3m-8 0 .8 12.1a2 2 0 0 0 2 1.9h4.4a2 2 0 0 0 2-1.9L17 7"/></svg></template>
<template id="iCheck"><svg class="i" viewBox="0 0 24 24"><path d="M5 12.5 9.5 17 19 6"/></svg></template>
<template id="iAlert"><svg class="i" viewBox="0 0 24 24"><path d="M12 8v5m0 3h.01M10.3 4 2.7 17a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 4a2 2 0 0 0-3.4 0Z"/></svg></template>
<script>
(function(){
var $=function(i){return document.getElementById(i)},C=function(t){return document.createElement(t)};
var SN=$('send'),DD=$('dodelete'),SB=$('selectbtn'),CH=$('change'),PT=$('ptext'),DL=$('dsel'),SH=$('sheet'),ST=$('status'),LS=$('list');
function E(t,c,x){var e=C(t);if(c)e.className=c;if(x!=null)e.textContent=x;return e}
function ic(i){return $(i).content.firstChild.cloneNode(true)}
function sz(b){return b>=1048576?(b/1048576).toFixed(1)+' MB':b>=1024?Math.round(b/1024)+' KB':b+' B'}
var F=[],R=null,O=null,P=null,H=null,K={},ok=false,B=false,X=false,N=0,Z=null,M=false,Q=[],V={};
var NOPE="Couldn't reach your TV",WIFI='Check your Wi-Fi, or open the QR code on the TV again.',SENT='Sent to your TV',NOW="It's on your screen now.",CAN='Sending cancelled',NOT='The photo was not sent to the TV.',REJ="The TV couldn't take that photo",REJ2='Rejected.',TRY='Try again';
function net(ok){$('pill').classList.toggle('bad',!ok);$('pillText').textContent=ok?'Connected':"Can't reach TV"}
function prog(p){var b=$('bar');b.style.width=p+'%';b.setAttribute('aria-valuenow',''+p)}
function hush(){ST.hidden=true}
function say(k,t,d,a){
 var i=$('sicon'),b=$('sact');ST.className='status '+k;$('stitle').textContent=t;$('sdetail').textContent=d;
 i.textContent='';i.appendChild(ic(k==='ok'?'iCheck':'iAlert'));
 b.textContent=a==='retry'?TRY:a==='again'?'Send another':'Dismiss';b.onclick=a==='retry'?function(){go(F)}:a==='again'?reset:hush;ST.hidden=false;
}
function cp(o){var r={};for(var k in o)r[k]=1;return r}
function oops(a){net(false);say('bad',NOPE,WIFI,a||'')}
function post(u){return fetch(u,{method:'POST'}).then(function(r){if(!r.ok)throw 0;return r.json()})}
function gl(){return fetch('/api/list',{cache:'no-store'}).then(function(r){if(!r.ok)throw 0;return r.json()})}
function mk(it,c,t){it.st.textContent=t;it.el.className='qrow'+(c?' '+c:'')}
function pc(it,p){it.pb.style.width=p+'%'}
function reset(){
 F.forEach(function(it){URL.revokeObjectURL(it.url)});F=[];
 $('file').value='';$('queue').textContent='';$('chosen').hidden=true;$('pick').hidden=false;
 SN.hidden=false;SN.disabled=true;SN.textContent='Send to TV';
 $('panel').hidden=true;CH.disabled=false;hush();H=null;
}
function qr(it){
 var r=E('div','qrow'),img=E('img','qthumb'),m=E('div','qmeta'),line=E('div','qline'),s=E('span','qstate','Waiting'),pg=E('div','pg qpg'),b=E('i');
 img.alt='';img.src=it.url;
 line.appendChild(E('span','qname',it.file.name));line.appendChild(s);pg.appendChild(b);m.appendChild(line);m.appendChild(pg);
 var t=E('button','mini',TRY);t.hidden=true;t.onclick=function(){go([it])};
 r.appendChild(img);r.appendChild(m);r.appendChild(t);it.el=r;it.st=s;it.pb=b;it.rt=t;return r;
}
function pick(list){
 var all=list?Array.prototype.slice.call(list):[];if(!all.length)return;
 var over=all.length>10,arr=all.slice(0,10);
 reset();
 arr.forEach(function(f){F.push({file:f,url:URL.createObjectURL(f),state:'waiting'})});
 var total=0;arr.forEach(function(f){total+=f.size});
 $('pick').hidden=true;$('chosen').hidden=false;
 $('fname').textContent=(F.length===1?F[0].file.name:F.length+' photos')+' · '+sz(total)+(over?' · first 10 only':'');
 F.forEach(function(it){$('queue').appendChild(qr(it))});
 SN.disabled=false;SN.textContent=F.length===1?'Send to TV':'Send '+F.length+' photos';hush();
}
function go(items){
 if(B||!items.length)return;
 var whole=items===F;
 B=true;X=false;N=0;Z=null;hush();
 CH.disabled=true;SN.hidden=!whole;SN.disabled=true;
 $('panel').hidden=false;prog(0);PT.textContent='Sending…';
 items.forEach(function(it){it.state='waiting';it.rt.hidden=true;it.el.className='qrow';it.st.textContent='Waiting';pc(it,0)});
 step(items,0,whole);
}
function step(items,i,whole){
 if(X){end(items,whole,1);return}
 if(i>=items.length){end(items,whole);return}
 var it=items[i];PT.textContent='Sending '+(i+1)+' of '+items.length+'…';
 if(whole)SN.textContent=PT.textContent;
 up(it,function(){
  if(it.state==='sent')N++;
  prog(Math.round((i+1)/items.length*100));step(items,i+1,whole);
 });
}
function up(it,done){
 it.network=false;it.err=null;it.state='sending';mk(it,'','Sending…');pc(it,0);
 var before=cp(K),x=new XMLHttpRequest();R=x;
 x.open('POST','/upload?name='+encodeURIComponent(it.file.name));
 x.setRequestHeader('Content-Type',it.file.type||'image/jpeg');
 x.upload.onprogress=function(e){
  if(!e.lengthComputable)return;
  var p=Math.max(1,Math.round(e.loaded/e.total*100));pc(it,p);mk(it,'','Sending… '+p+'%');
  if(F.length===1){prog(p);PT.textContent='Sending… '+p+'%'}
 };
 x.onload=function(){
  R=null;net(true);
  if(x.status>=200&&x.status<300){
   var n=null;try{n=JSON.parse(x.responseText).file}catch(e){}
   if(n)K[n]=1;it.state='sent';pc(it,100);mk(it,'ok','Sent');Z=n||Z;
  }else{it.err=(x.responseText||'').trim().slice(0,160)||('HTTP '+x.status+'.');it.state='failed';mk(it,'bad','Failed')}
  done();
 };
 x.onerror=function(){R=null;net(false);heal(it,before,done)};
 x.onabort=function(){R=null;it.state='stopped';mk(it,'','Not sent');done()};
 x.send(it.file);
}
function heal(it,before,done){
 function bad(){it.network=true;it.state='failed';mk(it,'bad','Failed');done()}
 if(!ok){bad();return}
 gl().then(function(items){
  var n=null;for(var i=0;i<items.length;i++){if(!before[items[i].name]){n=items[i].name;break}}
  if(!n)throw 0;
  K[n]=1;it.state='sent';pc(it,100);mk(it,'ok','Sent');Z=n;net(true);done();
 }).catch(bad);
}
function end(items,whole,stop){
 B=false;R=null;CH.disabled=false;$('panel').hidden=true;SN.hidden=true;
 if(stop)items.forEach(function(it){if(it.state==='waiting'){it.state='stopped';mk(it,'','Not sent')}});
 var n=items.length,bad=0;
 items.forEach(function(it){if(it.state!=='sent')bad++});
 if(whole)F.forEach(function(it){it.rt.hidden=it.state==='sent'});
 if(!whole){
  var it=items[0];
  if(it.state==='sent')say('ok',SENT,NOW,'again');
  else{it.rt.hidden=false;if(it.state==='stopped')say('bad',CAN,NOT,'');else if(it.network)oops('');else say('bad',REJ,it.err||REJ2,'')}
 }else if(stop)say('bad',CAN,n===1?NOT:N+' of '+n+' photos were sent.',n===1?'retry':'');
 else if(!bad)say('ok',SENT,n===1?NOW:n+' sent. The last is on your TV.','again');
 else if(n===1){if(items[0].network)oops('retry');else say('bad',REJ,items[0].err||REJ2,'retry')}
 else say('bad','Some photos did not send',N+' of '+n+' photos were sent.','');
 pull(Z);
}
function hit(){return Q.some(function(it){return it.active})}
function tally(){
 var n=0;Q.forEach(function(it){if(V[it.name])n++});
 DL.textContent='Delete ('+n+')';DL.disabled=!n;$('selall').textContent=n&&n===Q.length?'Clear all':'Select all';
}
function tog(on){
 M=on;if(!on)V={};
 $('selbar').hidden=!on;$('mode').hidden=on;$('cycle').hidden=on||!hit();SB.hidden=on||!Q.length;
 draw(Q);
 if(Q.length)(on?$('done'):SB).focus();
}
function rw(it,f){
 var r=E('div','row'+(it.active?' active':'')+(f===it.name?' fresh':''));
 if(M){var c=E('label','check'),cb=E('input');cb.type='checkbox';cb.dataset.n=it.name;cb.checked=!!V[it.name];cb.setAttribute('aria-label','Select '+it.date);c.appendChild(cb);r.appendChild(c)}
 var img=E('img','thumb');img.alt='';img.src='/thumb?file='+encodeURIComponent(it.name);r.appendChild(img);
 var m=E('div','rmeta'),line=E('div','rline');
 line.appendChild(E('span','rname','Photo · '+it.date));line.appendChild(E('span','rsub',it.size));m.appendChild(line);
 var a=E('div','racts'),l=E('button','act lock'),d=E('button','act del');
 l.dataset.a='lock';d.dataset.a='del';l.dataset.n=d.dataset.n=it.name;
 l.setAttribute('aria-pressed',it.active?'true':'false');l.setAttribute('aria-label',(it.active?'Locked: ':'Lock ')+it.date);
 d.setAttribute('aria-label','Delete '+it.date);l.appendChild(ic('iLock'));d.appendChild(ic('iTrash'));
 a.appendChild(l);a.appendChild(d);m.appendChild(a);r.appendChild(m);return r;
}
function draw(items){
 var f=H;H=null;Q=items;K={};
 var l=LS;l.textContent='';
 items.forEach(function(it){K[it.name]=1});ok=true;
 var keep={};items.forEach(function(it){keep[it.name]=1});for(var k in V)if(!keep[k])delete V[k];
 var lk=hit();
 $('tools').hidden=!items.length;$('mode').textContent=lk?'Locked on 1 photo':'Cycling through '+items.length+(items.length===1?' photo':' photos');
 $('cycle').hidden=!lk||M;SB.hidden=!items.length||M;l.classList.toggle('selmode',M);
 if(!items.length){
  if(M){M=false;V={};$('selbar').hidden=true;$('mode').hidden=false}
  var e=E('div','empty');e.appendChild(E('p',null,'No photos on your TV yet.'));l.appendChild(e);return;
 }
 items.forEach(function(it){l.appendChild(rw(it,f))});
 if(M)tally();
}
function pull(name){
 if(name!==undefined)H=name;
 gl().then(function(items){net(true);draw(items)}).catch(function(){
  net(false);$('tools').hidden=true;var l=LS;l.textContent='';l.appendChild(E('p','note',"Couldn't load photos. Reload the page."));
 });
}
function pin(n){
 post('/api/select?file='+encodeURIComponent(n)).then(function(){net(true);pull(null);say('ok','Locked on your TV','Stays as the wallpaper.','')}).catch(oops);
}
function ask(names,t){
 P=names;O=t;
 var n=names.length;
 $('sheettitle').textContent=n===1?'Delete this photo?':'Delete '+n+' photos?';
 $('sheettext').textContent=n===1?'Delete '+names[0]+' from your TV?':'Delete '+n+' photos from your TV?';
 DD.textContent=n===1?'Delete photo':'Delete '+n+' photos';
 SH.hidden=false;$('nocancel').focus();
}
function shut(){var f=O;SH.hidden=true;P=null;if(f&&document.contains(f))f.focus();else $('listtitle').focus()}
function zap(){
 var names=P;if(!names||!names.length)return;
 DD.disabled=true;
 var i=0,bad=0;
 (function next(){
  if(i<names.length){post('/api/delete?file='+encodeURIComponent(names[i++])).then(next).catch(function(){bad++;net(false);next()});return}
  DD.disabled=false;SH.hidden=true;P=null;
  if(M)tog(false);
  $('listtitle').focus();
  if(bad)say('bad','Some photos were not deleted',bad+' of '+names.length+' photos are still on the TV.','');
  pull(null);
 })();
}
$('pick').onclick=CH.onclick=function(){$('file').click()};
$('file').onchange=function(e){pick(e.target.files)};
SN.onclick=function(){go(F)};
$('cancel').onclick=function(){if(!B)return;X=true;if(R)R.abort()};
SB.onclick=function(){tog(true)};
$('done').onclick=function(){tog(false)};
$('selall').onclick=function(){
 var n=0;Q.forEach(function(it){if(V[it.name])n++});V={};
 if(n!==Q.length)Q.forEach(function(it){V[it.name]=1});
 draw(Q);
};
DL.onclick=function(){
 var ns=[];Q.forEach(function(it){if(V[it.name])ns.push(it.name)});
 if(ns.length)ask(ns,DL);
};
$('cycle').onclick=function(){post('/api/slideshow').then(function(){net(true);pull(null);say('ok','Cycling all photos','Every photo cycles again.','')}).catch(oops)};
LS.onclick=function(e){
 var b=e.target.closest('button');if(!b||!b.dataset.a)return;
 if(b.dataset.a==='lock'){if(b.ariaPressed!=='true')pin(b.dataset.n)}
 else ask([b.dataset.n],b);
};
LS.onchange=function(e){var c=e.target;if(c.type!=='checkbox')return;V[c.dataset.n]=c.checked;tally()};
$('sheetbg').onclick=$('nocancel').onclick=shut;
DD.onclick=zap;
document.addEventListener('keydown',function(e){if(e.key==='Escape'&&!SH.hidden)shut()});
window.addEventListener('pagehide',function(){F.forEach(function(it){URL.revokeObjectURL(it.url)})});
pull(null);
})();
</script>
</body>
</html>
""";

    private UploadPage() {}
}
