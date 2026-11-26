import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';
const token='test-only-'.repeat(4);
const now=Math.floor(Date.now()/1000/300)*300;
const awake=[75,0.15,30,1,1,0.9];
const asleep=[58,0.02,60,1,1,0.9];
const env={ENABLE_SLEEP_AI_DETECTION:'true',ACTIVITY_ACCESS_TOKEN:token,GROQ_API_KEY:'mock',DB:{prepare(){return{run:async()=>({meta:{changes:1}}),bind(){return this;}};}}};
const request=(overrides={},auth=token)=>new Request('https://example.test/v1/sleep/detect',{method:'POST',
  headers:{authorization:'Bearer '+auth,'content-type':'application/json'},body:JSON.stringify({
    schemaVersion:'sleep-detection-v1',requestId:'a'.repeat(64),sleepConsent:true,
    startTs:now-3600,bins:[awake,awake,...Array.from({length:8},()=>asleep),awake,awake],
    offsetMinutes:420,habitualMidsleepSec:10800,excludedIntervals:[],...overrides})});
test('sleep endpoint requires separate opt-in and auth, validates bins',async()=>{
  assert.equal((await worker.fetch(request(),{...env,ENABLE_SLEEP_AI_DETECTION:'false'})).status,503);
  assert.equal((await worker.fetch(request({sleepConsent:false}),env)).status,403);
  assert.equal((await worker.fetch(request({},'bad'),env)).status,401);
  assert.equal((await worker.fetch(request({bins:[[0,0,0,1,1,1]]}),env)).status,400);
});
test('cloud candidate contract: all categories, missing data, edges, overlap, confidence',async t=>{
  const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);
  let predictions=[{type:'MAIN_SLEEP',startBin:2,endBin:10,confidence:'HIGH'}];
  let calls=0;
  globalThis.fetch=async(url,opts)=>{calls++;const p=JSON.parse(opts.body);
    assert.equal(p.response_format.json_schema.name,'alke_sleep_v1');
    assert.ok(p.messages[0].content.includes('no universal 30-minute cutoff'));
    return new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:JSON.stringify({episodes:predictions})}}],usage:{total_tokens:100}}),{status:200});
  };
  async function result(o={}){const r=await worker.fetch(request(o),env);assert.equal(r.status,200,await r.clone().text());return(await r.json()).sleepDetection}
  for(const type of ['MAIN_SLEEP','NAP','DAY_SLEEP','UNCLASSIFIED_SLEEP']){
    predictions=[{type,startBin:2,endBin:10,confidence:'HIGH'}];assert.equal((await result()).episodes[0].type,type);
  }
  predictions=[{type:'NAP',startBin:2,endBin:4,confidence:'MODERATE'}];assert.equal((await result()).episodes.length,0);
  predictions=[{type:'NAP',startBin:0,endBin:4,confidence:'HIGH'}];assert.equal((await result()).episodes.length,0);
  predictions=[{type:'MAIN_SLEEP',startBin:2,endBin:12,confidence:'HIGH'}];assert.equal((await result()).episodes.length,0);
  predictions=[{type:'NAP',startBin:2,endBin:4,confidence:'HIGH'}];assert.equal((await result({excludedIntervals:[[now-3000,now-2700]]})).episodes.length,0);
  predictions=[{type:'NAP',startBin:2,endBin:4,confidence:'HIGH'},{type:'DAY_SLEEP',startBin:3,endBin:5,confidence:'HIGH'}];
  assert.equal((await worker.fetch(request(),env)).status,502);
  const before=calls;const missing=Array.from({length:12},()=>[null,null,null,0,0,0]);
  assert.equal((await result({bins:missing})).status,'INSUFFICIENT_DATA');assert.equal(calls,before);
});
