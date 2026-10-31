// Candidate-facing pages. Static HTML + vanilla JS that talk to the JSON endpoints.
const page = (mode) => `<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${mode === 'book' ? 'Book your interview' : 'Reschedule your interview'}</title>
<style>
 body{font:16px/1.4 system-ui,sans-serif;max-width:560px;margin:0 auto;padding:16px;color:#1a1a1a}
 h1{font-size:1.4rem} h2{font-size:1rem;margin:20px 0 8px;color:#444}
 label{display:block;margin:10px 0 4px;font-size:.9rem} input{width:100%;padding:10px;font-size:1rem;box-sizing:border-box;border:1px solid #bbb;border-radius:6px}
 .slots{display:flex;flex-wrap:wrap;gap:8px} .slot{padding:10px 14px;border:1px solid #888;border-radius:6px;background:#fff;font-size:1rem;cursor:pointer}
 .slot[aria-pressed=true]{background:#0b5fff;color:#fff;border-color:#0b5fff}
 .go{margin-top:20px;width:100%;padding:12px;font-size:1rem;border:0;border-radius:6px;background:#0b5fff;color:#fff;cursor:pointer} .go:disabled{opacity:.5}
 .msg{padding:12px;border-radius:6px;margin:12px 0} .err{background:#fde8e8;color:#8a1c1c} .ok{background:#e6f6ea;color:#14532d}
</style></head><body>
<h1>${mode === 'book' ? 'Book your interview' : 'Reschedule your interview'}</h1>
<div id="app">Loading…</div>
<script>
const MODE=${JSON.stringify(mode)};
const token=location.pathname.split('/').pop();
const api=MODE==='book'?'/booking-link/':'/reschedule-link/';
const $=(t,p={},...k)=>{const e=Object.assign(document.createElement(t),p);k.forEach(c=>e.append(c));return e};
const app=document.getElementById('app');
const fmtDay=d=>new Date(d).toLocaleDateString(undefined,{weekday:'long',day:'numeric',month:'long',year:'numeric'});
const fmtTime=d=>new Date(d).toLocaleTimeString(undefined,{hour:'2-digit',minute:'2-digit'});
const fmtFull=d=>fmtDay(d)+', '+fmtTime(d);
const show=(cls,text)=>{app.replaceChildren($('div',{className:'msg '+cls,textContent:text}))};

async function load(){
  const r=await fetch(api+token); const d=await r.json();
  if(!r.ok) return show('err',d.error||'Something went wrong');
  if(d.status==='BOOKED'){
    const box=$('div',{className:'msg ok',textContent:'You are booked for '+fmtFull(d.booking.slotStart)+'.'});
    if(d.rescheduleUrl) box.append(' ',$('a',{href:d.rescheduleUrl,textContent:'Need another time?'}));
    return app.replaceChildren(box);
  }
  render(d);
}
function render(d){
  let chosen=null;
  const form=$('div');
  const inputs={};
  if(MODE==='book'){
    for(const [k,label,type,val] of [['candidateName','Full name','text',d.candidate.name],['candidateEmail','Email','email',d.candidate.email],['candidatePhone','Phone','tel',d.candidate.phone]]){
      if(val) continue; // prefilled by the application server
      inputs[k]=$('input',{type,required:k!=='candidatePhone'});
      form.append($('label',{textContent:label}),inputs[k]);
    }
  } else if(d.current){
    form.append($('div',{className:'msg',textContent:'Current time: '+fmtFull(d.current.slotStart)}));
  }
  if(!d.slots.length) return app.replaceChildren($('div',{className:'msg err',textContent:'No time slots are available right now. Please contact us.'}));
  const days=new Map();
  for(const s of d.slots){const k=new Date(s.slotStart).toDateString();(days.get(k)||days.set(k,[]).get(k)).push(s)}
  const btn=$('button',{className:'go',disabled:true,textContent:MODE==='book'?'Confirm booking':'Confirm new time'});
  const all=[];
  for(const list of days.values()){
    form.append($('h2',{textContent:fmtDay(list[0].slotStart)}));
    const row=$('div',{className:'slots'});
    for(const s of list){
      const b=$('button',{className:'slot',type:'button',textContent:fmtTime(s.slotStart)});
      b.setAttribute('aria-pressed','false');
      b.onclick=()=>{chosen=s;all.forEach(x=>x.setAttribute('aria-pressed',String(x===b)));btn.disabled=false};
      all.push(b);row.append(b);
    }
    form.append(row);
  }
  btn.onclick=async()=>{
    btn.disabled=true;
    const body={slotStart:chosen.slotStart};
    for(const [k,i] of Object.entries(inputs)) if(i.value.trim()) body[k]=i.value.trim();
    const r=await fetch(api+token,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});
    const res=await r.json();
    if(r.ok){
      const slot=res.slot_start||res.booking.slot_start;
      const ok=$('div',{className:'msg ok',textContent:'Confirmed for '+fmtFull(slot)+'. We have sent you the details.'});
      const link=res.rescheduleUrl||(res.reschedule_token&&location.origin+'/reschedule/'+res.reschedule_token);
      if(link) ok.append($('br'),$('a',{href:link,textContent:'Need to change it? Reschedule'}));
      app.replaceChildren(ok);
    } else {
      alert((res.error||'Something went wrong')+(res.details?': '+res.details.map(x=>x.message).join(', '):''));
      if(r.status===409) load(); else btn.disabled=false;
    }
  };
  form.append(btn);
  app.replaceChildren(form);
}
load().catch(()=>show('err','Could not load. Please try again.'));
</script></body></html>`;

export const bookPage = page('book');
export const reschedulePage = page('reschedule');