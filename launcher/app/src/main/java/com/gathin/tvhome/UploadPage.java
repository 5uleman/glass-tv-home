package com.gathin.tvhome;

/**
 * The page the TV serves to a phone: one self-contained HTML file, no external requests except the
 * footer's GitHub link. Kept out of {@link WallpaperUploadServer} so a unit test can read the exact
 * bytes that are served and check the size cap and the absence of outside URLs.
 */
final class UploadPage {

    /** The raw page must stay under this many UTF-8 bytes. */
    static final int MAX_BYTES = 20 * 1024;

    static final String GITHUB_URL = "https://github.com/5uleman";

    static final String HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Send a wallpaper · Glass TV</title>
<style>
*{box-sizing:border-box}
[hidden]{display:none!important}
body{margin:0;background:#0B0C0F;color:#F5F6F8;font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
.wrap{max-width:390px;margin:0 auto;min-height:100vh;padding:24px 20px 12px;display:flex;flex-direction:column}
header{display:flex;align-items:center;justify-content:space-between;gap:12px;font-weight:600}
.pill{display:inline-flex;align-items:center;gap:8px;border:1px solid rgba(255,255,255,.08);border-radius:999px;padding:6px 10px;color:#9AA0AA;font-size:12px;font-weight:500}
.pill i{width:6px;height:6px;border-radius:50%;background:#34D399}
.pill.bad{color:#F87171}.pill.bad i{background:#F87171}
h1{font-size:28px;line-height:1.2;margin:24px 0 6px}
h2{font-size:16px;margin:0}
.lead{color:#9AA0AA;margin:0 0 24px}
.card{background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);border-radius:24px;padding:16px}
.drop{display:flex;flex-direction:column;align-items:center;justify-content:center;width:100%;min-height:200px;padding:16px;border:1px dashed rgba(255,255,255,.2);border-radius:16px;background:rgba(255,255,255,.02);color:inherit;font:inherit;cursor:pointer}
.drop svg{width:22px;height:22px;stroke:#9AA0AA;fill:none;stroke-width:1.75;stroke-linecap:round;stroke-linejoin:round;margin-bottom:12px}
.drop b{color:#3B82F6;font-weight:500}
.drop span{color:#9AA0AA;font-size:13px}
.drop small{color:#6B7280;font-size:12px;margin-top:10px}
.btn{width:100%;min-height:52px;border:0;border-radius:14px;background:#3B82F6;color:#fff;font:inherit;font-weight:500;cursor:pointer;margin-top:12px}
.btn[disabled]{opacity:.4;cursor:not-allowed}
.btn.ghost{background:none;border:1px solid rgba(255,255,255,.14);color:#9AA0AA}
.btn.bad{background:#F87171;color:#1A0B0B}
.mini{min-height:44px;min-width:44px;padding:0 12px;border:1px solid rgba(255,255,255,.14);border-radius:10px;background:none;color:#9AA0AA;font:inherit;font-size:13px;font-weight:500;cursor:pointer}
.preview{width:100%;aspect-ratio:16/9;border-radius:16px;overflow:hidden;border:1px solid rgba(255,255,255,.08);background:#141822}
.preview img{width:100%;height:100%;object-fit:cover;display:block}
.crop,.fmeta{color:#9AA0AA;font-size:12px}
.crop{margin:8px 0 0}
.meta{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:8px}
.min0{min-width:0}
.fname{font-weight:500;margin:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.fmeta{margin:2px 0 0}
.queue{display:flex;flex-direction:column;gap:4px;margin-top:10px}
.qrow{display:flex;align-items:center;gap:10px;padding:4px 0}
.qmeta{min-width:0;flex:1}
.qline{display:flex;justify-content:space-between;gap:8px;font-size:13px}
.qname{font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.qstate{color:#9AA0AA;font-size:12px;white-space:nowrap}
.qrow.ok .qstate{color:#34D399}
.qrow.bad .qstate{color:#F87171}
.qpg{height:3px;margin-top:6px}
#panel{margin-top:12px}
.pg{height:4px;border-radius:999px;background:rgba(255,255,255,.08);overflow:hidden}
.pg i{display:block;height:100%;width:0;background:#3B82F6;transition:width .2s linear}
.pgrow{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:8px;font-size:13px;font-weight:500}
.status{margin-top:12px}
.status .t{margin:0;font-weight:600}
.status.ok .t{color:#34D399}.status.bad .t{color:#F87171}
.status .d{margin:2px 0 0;color:#9AA0AA;font-size:13px;overflow-wrap:anywhere}
.acts{display:flex;justify-content:flex-end;margin-top:10px}
.listsec{margin-top:28px}
.lhead{display:flex;align-items:center;justify-content:space-between;gap:12px}
.h2row{display:flex;align-items:center;gap:8px}
.chip{min-width:20px;height:20px;padding:0 6px;border-radius:6px;background:rgba(255,255,255,.06);color:#9AA0AA;font-size:12px;display:inline-flex;align-items:center;justify-content:center}
.note{color:#9AA0AA;font-size:13px;margin:6px 0 12px;overflow-wrap:anywhere}
.rows{display:flex;flex-direction:column;gap:8px}
.row{display:flex;align-items:center;gap:10px;padding:8px 10px;background:rgba(255,255,255,.04);border:1px solid rgba(255,255,255,.08);border-radius:16px}
.row.active{border-color:rgba(59,130,246,.4)}
.row.fresh{animation:fresh 3s ease-out forwards}
@keyframes fresh{0%,70%{border-color:rgba(59,130,246,.9)}}
.thumb{width:56px;height:56px;border-radius:12px;object-fit:cover;background:#151A24;flex:none}
.rmeta{min-width:0;flex:1}
.rname{margin:0;font-size:14px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.rsub{margin:4px 0 0;font-size:12px;color:#9AA0AA}
.racts{display:flex;align-items:center;gap:6px;flex:none}
.lock{min-height:44px;padding:0 10px;border:1px solid rgba(255,255,255,.14);border-radius:10px;background:none;color:#9AA0AA;font:inherit;font-size:12px;font-weight:500;cursor:pointer}
.lock[aria-pressed=true]{background:#0284C7;border-color:#38BDF8;color:#fff}
.del{min-height:44px;min-width:44px;border:0;background:none;color:#F87171;font:inherit;font-size:12px;cursor:pointer}
.empty{padding:24px;text-align:center;border:1px dashed rgba(255,255,255,.12);border-radius:16px}
.empty p{margin:0;font-weight:500}
.empty span{display:block;color:#9AA0AA;font-size:13px;margin-top:4px}
.foot{margin-top:auto;padding:32px 0 8px;text-align:center;color:#6B7280;font-size:12px}
.foot a{display:inline-flex;align-items:center;min-height:44px;padding:0 8px;color:inherit;text-decoration:none;font-weight:500;letter-spacing:.2em}
.foot a:hover,.foot a:focus-visible{text-decoration:underline}
.foot p{margin:0}
.sheet{position:fixed;inset:0;z-index:9;display:flex;align-items:flex-end;justify-content:center}
.sheetbg{position:absolute;inset:0;background:rgba(0,0,0,.6)}
.sheetcard{position:relative;width:100%;max-width:390px;background:#11141D;border:1px solid rgba(255,255,255,.14);border-bottom:0;border-radius:24px 24px 0 0;padding:20px 20px 28px;text-align:center}
.sheetcard h3{margin:0 0 6px;font-size:17px}
:focus-visible{outline:2px solid #3B82F6;outline-offset:2px}
@media (prefers-reduced-motion:reduce){*{transition:none!important;animation:none!important}}
</style>
</head>
<body>
<div class="wrap">
<header><span>Glass TV</span><span class="pill" id="pill"><i></i><span id="pillText">Connected</span></span></header>
<h1>Send a wallpaper</h1>
<p class="lead">Pick a photo from your phone and it appears on your TV instantly.</p>
<section class="card">
<button class="drop" id="pick" type="button"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 16.5V4m0 0L8 8m4-4 4 4M4.5 19.5h15"/></svg><b>Choose a photo</b><span>or tap to browse your gallery</span><small>Landscape photos at 1920 × 1080 or larger look best.</small></button>
<input id="file" type="file" accept="image/*" multiple hidden>
<div id="chosen" hidden>
<div id="single">
<div class="preview"><img id="preview" alt="Your photo, cropped to 16:9"></div>
<p class="crop">The TV shows this crop.</p>
</div>
<div class="meta"><div class="min0"><p class="fname" id="fname"></p><p class="fmeta" id="fmeta"></p></div><button class="mini" id="change" type="button">Change photo</button></div>
<div class="queue" id="queue" hidden></div>
</div>
<button class="btn" id="send" type="button" disabled>Send to TV</button>
<div id="panel" hidden>
<div class="pg"><i id="bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0"></i></div>
<div class="pgrow"><span id="ptext" aria-live="polite">Sending…</span><button class="mini" id="cancel" type="button">Cancel</button></div>
</div>
<div class="status" id="status" role="status" aria-live="polite" hidden>
<p class="t" id="stitle"></p><p class="d" id="sdetail"></p>
<div class="acts"><button class="mini" id="sact" type="button">Dismiss</button></div>
</div>
</section>
<section class="listsec">
<div class="lhead"><div class="h2row"><h2 id="listtitle" tabindex="-1">On your TV</h2><span class="chip" id="count">0</span></div><button class="mini" id="rotate" type="button">Rotate all</button></div>
<p class="note">Lock keeps one photo as your wallpaper.</p>
<div class="rows" id="list"><p class="note">Loading photos…</p></div>
</section>
<footer class="foot"><a href="https://github.com/5uleman" target="_blank" rel="noopener noreferrer">5ULEMAN</a><p>Sent directly to your TV over local Wi-Fi.</p></footer>
</div>
<div class="sheet" id="sheet" hidden>
<div class="sheetbg" id="sheetbg"></div>
<div class="sheetcard" role="dialog" aria-modal="true" aria-labelledby="sheettitle">
<h3 id="sheettitle">Delete this photo?</h3>
<p class="note" id="sheettext"></p>
<button class="btn bad" id="dodelete" type="button">Delete photo</button>
<button class="btn ghost" id="nocancel" type="button">Cancel</button>
</div>
</div>
<script>
(function(){
var $=function(i){return document.getElementById(i)},C=function(t){return document.createElement(t)};
var files=[],xhr=null,lastFocus=null,pending=null,fresh=null,known={},listOk=false,sending=false,cancelled=false,sentCount=0,lastSent=null;
var NOPE="Couldn't reach your TV",WIFI='Check your Wi-Fi, or open the QR code on the TV again.',SENT='Sent to your TV',NOW="It's on your screen now.",CAN='Sending cancelled',NOT='The photo was not sent to the TV.',REJ="The TV couldn't take that photo",REJ2='The upload was rejected.';
function fmt(b){return b>=1048576?(b/1048576).toFixed(1)+' MB':b>=1024?Math.round(b/1024)+' KB':b+' B'}
function conn(ok){$('pill').classList.toggle('bad',!ok);$('pillText').textContent=ok?'Connected':"Can't reach TV"}
function status(k,t,d,act){
 var s=$('status'),b=$('sact');s.className='status '+k;$('stitle').textContent=t;$('sdetail').textContent=d;
 b.textContent=act==='retry'?'Try again':act==='again'?'Send another':'Dismiss';
 b.onclick=act==='retry'?function(){run(files)}:act==='again'?clearAll:hideStatus;s.hidden=false;
}
function hideStatus(){$('status').hidden=true}
function bar(p){$('bar').style.width=p+'%';$('bar').setAttribute('aria-valuenow',''+p)}
function copy(o){var r={};for(var k in o)r[k]=1;return r}
function fail(a){conn(false);status('bad',NOPE,WIFI,a||'')}
function post(u){return fetch(u,{method:'POST'}).then(function(r){if(!r.ok)throw 0;return r.json()})}
function mark(it,cls,txt){it.st.textContent=txt;it.el.className='qrow'+(cls?' '+cls:'')}
function pct(it,p){it.pb.style.width=p+'%'}
function clearAll(){
 files.forEach(function(it){URL.revokeObjectURL(it.url)});files=[];
 $('file').value='';$('queue').textContent='';$('queue').hidden=true;$('single').hidden=false;
 $('chosen').hidden=true;$('pick').hidden=false;$('send').hidden=false;$('send').disabled=true;$('send').textContent='Send to TV';
 $('panel').hidden=true;$('change').disabled=false;hideStatus();fresh=null;
}
function qrow(it){
 var r=C('div');r.className='qrow';
 var m=C('div');m.className='qmeta';
 var line=C('div');line.className='qline';
 var n=C('span');n.className='qname';n.textContent=it.file.name;
 var s=C('span');s.className='qstate';s.textContent='Waiting';line.appendChild(n);line.appendChild(s);
 var pg=C('div');pg.className='pg qpg';
 var b=C('i');pg.appendChild(b);
 m.appendChild(line);m.appendChild(pg);r.appendChild(m);
 var t=C('button');t.type='button';t.className='mini';t.textContent='Try again';t.hidden=true;
 t.onclick=function(){run([it])};r.appendChild(t);
 it.el=r;it.st=s;it.pb=b;it.rt=t;
 return r;
}
function choose(list){
 var all=list?Array.prototype.slice.call(list):[];if(!all.length)return;
 $('file').value='';
 var over=all.length>10,arr=all.slice(0,10);
 clearAll();
 arr.forEach(function(f){files.push({file:f,url:URL.createObjectURL(f),state:'waiting'})});
 $('pick').hidden=true;$('chosen').hidden=false;
 var total=0;arr.forEach(function(f){total+=f.size});
 if(files.length===1){
  var f=files[0].file;
  $('preview').src=files[0].url;$('fname').textContent=f.name;$('fmeta').textContent=fmt(f.size);
  var probe=new Image();probe.onload=function(){if(files[0]&&files[0].file===f)$('fmeta').textContent=fmt(f.size)+' · '+probe.naturalWidth+' × '+probe.naturalHeight};
  probe.src=files[0].url;
  $('change').textContent='Change photo';
 }else{
  $('single').hidden=true;
  $('fname').textContent=files.length+' photos selected';
  $('fmeta').textContent=fmt(total)+' total'+(over?' · first 10 only':'');
  $('change').textContent='Change photos';
 }
 files.forEach(function(it){$('queue').appendChild(qrow(it))});
 $('queue').hidden=files.length===1;
 $('send').hidden=false;$('send').disabled=false;$('send').textContent=files.length===1?'Send to TV':'Send '+files.length+' photos';
 hideStatus();
}
function run(items){
 if(sending||!items.length)return;
 var whole=items===files;
 sending=true;cancelled=false;sentCount=0;lastSent=null;hideStatus();
 $('change').disabled=true;$('send').hidden=!whole;$('send').disabled=true;
 $('panel').hidden=false;bar(0);$('ptext').textContent='Sending…';
 items.forEach(function(it){it.state='waiting';it.rt.hidden=true;it.el.className='qrow';it.st.textContent='Waiting';pct(it,0)});
 pump(items,0,whole);
}
function pump(items,i,whole){
 if(cancelled){finish(items,whole,'cancelled');return}
 if(i>=items.length){finish(items,whole);return}
 var it=items[i];
 $('ptext').textContent='Sending '+(i+1)+' of '+items.length+'…';
 if(whole)$('send').textContent='Sending '+(i+1)+' of '+items.length+'…';
 upload(it,function(){
  if(it.state==='sent')sentCount++;
  bar(Math.round(((i+1)/items.length)*100));
  pump(items,i+1,whole);
 });
}
function upload(it,done){
 it.network=false;it.err=null;it.state='sending';mark(it,'','Sending…');pct(it,0);
 var before=copy(known),x=new XMLHttpRequest();xhr=x;
 x.open('POST','/upload?name='+encodeURIComponent(it.file.name),true);
 x.setRequestHeader('Content-Type',it.file.type||'image/jpeg');
 x.upload.onprogress=function(e){if(e.lengthComputable){var p=Math.max(1,Math.round(e.loaded/e.total*100));pct(it,p);mark(it,'','Sending… '+p+'%');if(files.length===1){bar(p);$('ptext').textContent='Sending… '+p+'%'}}};
 x.onload=function(){
  xhr=null;conn(true);
  if(x.status>=200&&x.status<300){
   var name=null;try{name=JSON.parse(x.responseText).file}catch(e){}
   if(name)known[name]=1;
   it.state='sent';pct(it,100);mark(it,'ok','Sent');lastSent=name||lastSent;
  }else{
   it.err=(x.responseText||'').trim().slice(0,160)||('HTTP '+x.status+'.');
   it.state='failed';mark(it,'bad','Failed');
  }
  done();
 };
 x.onerror=function(){xhr=null;conn(false);recover(it,before,done)};
 x.onabort=function(){xhr=null;it.state='stopped';mark(it,'','Not sent');done()};
 x.send(it.file);
}
function recover(it,before,done){
 var bad=function(){it.network=true;it.state='failed';mark(it,'bad','Failed');done()};
 if(!listOk){bad();return}
 fetch('/api/list',{cache:'no-store'}).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(items){
  var found=null;for(var i=0;i<items.length;i++){if(!before[items[i].name]){found=items[i].name;break}}
  if(!found)throw 0;
  known[found]=1;it.state='sent';pct(it,100);mark(it,'ok','Sent');lastSent=found;conn(true);done();
 }).catch(bad);
}
function finish(items,whole,kind){
 sending=false;xhr=null;$('change').disabled=false;$('panel').hidden=true;$('send').hidden=true;
 if(kind==='cancelled')items.forEach(function(it){if(it.state==='waiting'){it.state='stopped';mark(it,'','Not sent')}});
 var n=items.length,bad=0;
 items.forEach(function(it){if(it.state!=='sent')bad++});
 if(whole)files.forEach(function(it){it.rt.hidden=it.state==='sent'});
 if(!whole){
  var it=items[0];
  if(it.state==='sent')status('ok',SENT,NOW,'again');
  else{
   it.rt.hidden=false;
   if(it.state==='stopped')status('bad',CAN,NOT,'');
   else if(it.network)fail('');
   else status('bad',REJ,it.err||REJ2,'');
  }
 }else if(kind==='cancelled'){
  status('bad',CAN,n===1?NOT:sentCount+' of '+n+' photos were sent.',n===1?'retry':'');
 }else if(!bad){
  status('ok',SENT,n===1?NOW:n+' photos sent. The last one is on your TV.','again');
 }else if(n===1){
  if(items[0].network)fail('retry');else status('bad',REJ,items[0].err||REJ2,'retry');
 }else{
  status('bad','Some photos did not send',sentCount+' of '+n+' photos were sent. Use Try again on a failed row.','');
 }
 load(lastSent);
}
function row(it,f){
 var r=C('div');r.className='row'+(it.active?' active':'')+(f===it.name?' fresh':'');
 var img=C('img');img.className='thumb';img.alt='';img.src='/thumb?file='+encodeURIComponent(it.name);r.appendChild(img);
 var m=C('div');m.className='rmeta';
 var n=C('p');n.className='rname';n.textContent=it.name;
 var s=C('p');s.className='rsub';s.textContent=it.date+' · '+it.size;
 m.appendChild(n);m.appendChild(s);
 var a=C('div');a.className='racts';
 var l=C('button');l.type='button';l.className='lock';l.textContent=it.active?'Locked':'Lock';
 l.setAttribute('aria-pressed',it.active?'true':'false');
 l.onclick=function(){if(!it.active)lock(it.name)};
 var d=C('button');d.type='button';d.className='del';d.textContent='Delete';
 d.onclick=function(){sheet(it.name,d)};
 a.appendChild(l);a.appendChild(d);r.appendChild(m);r.appendChild(a);return r;
}
function render(items){
 var f=fresh;fresh=null;
 var l=$('list');l.textContent='';$('count').textContent=''+items.length;
 known={};items.forEach(function(it){known[it.name]=1});listOk=true;
 if(!items.length){
  var e=C('div');e.className='empty';
  var p=C('p');p.textContent='No photos on your TV yet.';
  var s=C('span');s.textContent='Send your first wallpaper from the card above.';
  e.appendChild(p);e.appendChild(s);l.appendChild(e);return;
 }
 items.forEach(function(it){l.appendChild(row(it,f))});
}
function load(name){
 if(name!==undefined)fresh=name;
 fetch('/api/list',{cache:'no-store'}).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(items){conn(true);render(items)}).catch(function(){
  conn(false);var l=$('list');l.textContent='';var p=C('p');p.className='note';p.textContent="Couldn't load photos from your TV. Reload the page to try again.";l.appendChild(p);
 });
}
function lock(name){
 post('/api/select?file='+encodeURIComponent(name)).then(function(){conn(true);load(null);status('ok','Locked on your TV',name+' stays as the wallpaper.','')}).catch(fail);
}
function sheet(name,t){pending=name;lastFocus=t;$('sheettext').textContent='Delete '+name+' from your TV?';$('sheet').hidden=false;$('nocancel').focus()}
function close(){var f=lastFocus;$('sheet').hidden=true;pending=null;if(f&&document.contains(f))f.focus();else $('listtitle').focus()}
function del(){
 var name=pending;if(!name)return;$('dodelete').disabled=true;
 post('/api/delete?file='+encodeURIComponent(name)).then(function(){
  conn(true);$('sheet').hidden=true;pending=null;$('listtitle').focus();load(null);
 }).catch(function(){conn(false);close();status('bad',NOPE,WIFI,'')}).then(function(){$('dodelete').disabled=false});
}
$('pick').onclick=function(){$('file').click()};
$('change').onclick=function(){$('file').click()};
$('file').onchange=function(e){choose(e.target.files)};
$('send').onclick=function(){run(files)};
$('cancel').onclick=function(){if(!sending)return;cancelled=true;if(xhr)xhr.abort()};
$('rotate').onclick=function(){
 post('/api/slideshow').then(function(){conn(true);load(null);status('ok','Rotating all photos','The TV cycles through every photo again.','')}).catch(fail);
};
$('sheetbg').onclick=close;
$('nocancel').onclick=close;
$('dodelete').onclick=del;
document.addEventListener('keydown',function(e){if(e.key==='Escape'&&!$('sheet').hidden)close()});
window.addEventListener('pagehide',function(){files.forEach(function(it){URL.revokeObjectURL(it.url)})});
load(null);
})();
</script>
</body>
</html>
""";

    private UploadPage() {}
}
