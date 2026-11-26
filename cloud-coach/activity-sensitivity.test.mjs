import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';
const token = 'test-only-'.repeat(4);
const now = Math.floor(Date.now()/1000/30)*30;
const bin = [110,0.4,22,null,1,1];
const env = {ENABLE_ACTIVITY_AI_DETECTION:'true', ACTIVITY_ACCESS_TOKEN:token, GROQ_API_KEY:'mock-test-only', DB:{prepare(){return {run:async()=>({meta:{changes:1}}),bind(){return this;}};}}};
function req(mode,bins=Array.from({length:30},()=>bin),excludedIntervals=[]) {
  const body={schemaVersion:'activity-detection-v1',requestId:'a'.repeat(64),activityConsent:true,startTs:now-900,restingHr:60,bins,excludedIntervals};
  if(mode!==undefined) body.sensitivity=mode;
  return new Request('https://example.test/v1/activity/detect',{method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},body:JSON.stringify(body)});
}
test('duration policies, compatibility and strict evidence checks',async t=>{
  const original=globalThis.fetch;
  t.after(()=>{globalThis.fetch=original;});
  let prediction={type:'WALKING',startBin:0,endBin:2,confidence:'HIGH'};
  globalThis.fetch=async()=>new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:JSON.stringify({activities:[prediction]})}}],usage:{total_tokens:100}}),{status:200});
  async function result(mode,bins,excluded){
    const res=await worker.fetch(req(mode,bins,excluded),env);
    assert.equal(res.status,200,await res.clone().text());
    return (await res.json()).activityDetection;
  }
  const high=await result('HIGH');
  assert.equal(high.sensitivity,'HIGH');assert.equal(high.activities.length,1);
  assert.equal((await result('LOW')).activities.length,0);
  const old=await result(undefined);assert.equal(old.sensitivity,undefined);assert.equal(old.activities.length,0);
  prediction={...prediction,endBin:3};
  assert.equal((await result(undefined)).activities.length,1);assert.equal((await result('LOW')).activities.length,0);
  prediction={...prediction,endBin:6};assert.equal((await result('LOW')).activities.length,1);
  prediction={type:'WORKOUT',startBin:0,endBin:6,confidence:'HIGH'};
  assert.equal((await result('HIGH')).activities.length,1);assert.equal((await result('MEDIUM')).activities.length,0);
  prediction={...prediction,endBin:10};
  assert.equal((await result('MEDIUM')).activities.length,1);assert.equal((await result('LOW')).activities.length,0);
  prediction={...prediction,endBin:20};assert.equal((await result('LOW')).activities.length,1);
  prediction={...prediction,confidence:'MODERATE'};assert.equal((await result('HIGH')).activities.length,0);
  prediction={type:'CLIMBING_STAIRS',startBin:0,endBin:2,confidence:'HIGH'};assert.equal((await result('HIGH')).activities.length,0);
  prediction={type:'WALKING',startBin:0,endBin:2,confidence:'HIGH'};
  assert.equal((await result('HIGH',undefined,[[now-900,now-870]])).activities.length,0);
  const bad=Array.from({length:30},()=>bin);bad[0]=[110,0.4,22,null,1,0.1];assert.equal((await result('HIGH',bad)).activities.length,0);
  const invalid=await worker.fetch(req('INVALID'),env);assert.equal(invalid.status,400);
});

test('R2 increases completion allowance and records fixed diagnostic on truncated reply',async t=>{
  const originalFetch=globalThis.fetch, originalWarn=console.warn;
  const logs=[];
  t.after(()=>{globalThis.fetch=originalFetch;console.warn=originalWarn;});
  console.warn=message=>logs.push(String(message));
  globalThis.fetch=async (url,options)=>{
    const body=JSON.parse(options.body);
    assert.equal(body.max_completion_tokens,2048);
    assert.equal(body.reasoning_effort,'low');
    return new Response(JSON.stringify({choices:[{finish_reason:'length',message:{content:'PRIVATE_TEST_MARKER'}}],usage:{total_tokens:2500,completion_tokens:2048}}),{status:200});
  };
  const response=await worker.fetch(req('HIGH'),env);
  assert.equal(response.status,502);
  assert.ok(logs.some(x=>x.includes('completion_token_limit')));
  assert.ok(logs.every(x=>!x.includes('PRIVATE_TEST_MARKER')&&!x.includes(token)));
});

test('HIGH walking accepts partial counter evidence but never guesses gait or stairs',async t=>{
  const original=globalThis.fetch, originalInfo=console.info;
  const logs=[];
  t.after(()=>{globalThis.fetch=original;console.info=originalInfo;});
  console.info=message=>logs.push(String(message));
  let prediction={type:'WALKING',startBin:0,endBin:2,confidence:'HIGH'};
  globalThis.fetch=async()=>new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:JSON.stringify({activities:[prediction]})}}],usage:{total_tokens:100}}),{status:200});
  async function detect(first,second,mode='HIGH'){
    const bins=Array.from({length:30},()=>bin);
    bins[0]=first;bins[1]=second;
    const res=await worker.fetch(req(mode,bins),env);
    assert.equal(res.status,200,await res.clone().text());
    return (await res.json()).activityDetection.activities;
  }
  const unknown=[110,0.4,null,null,1,1];
  assert.equal((await detect(bin,unknown)).length,1);
  assert.equal((await detect(unknown,unknown)).length,0);
  assert.equal((await detect(bin,[110,0.4,0,null,1,1])).length,0);
  assert.equal((await detect(bin,[110,0,null,null,1,1])).length,0);
  assert.equal((await detect(bin,[110,0.4,null,null,0.1,1])).length,0);
  prediction={...prediction,type:'RUNNING'};
  assert.equal((await detect(bin,unknown)).length,0);
  prediction={...prediction,type:'CLIMBING_STAIRS'};
  assert.equal((await detect(bin,bin)).length,0);
  assert.equal((await detect([110,0.4,22,2,1,1],[110,0.4,22,2,1,1])).length,1);
  assert.ok(logs.some(x=>x.includes('evidence_rejected')));
  assert.ok(logs.some(x=>x.includes('"accepted"')));
  assert.ok(logs.every(x=>!x.includes(token)&&!x.includes('WALKING')&&!x.includes('CLIMBING_STAIRS')));
});


test('v626 worker trial: sparse cadence, generic exercise and unchanged client safeguards',async t=>{
  const original=globalThis.fetch;
  t.after(()=>{globalThis.fetch=original;});
  let prediction={type:'RUNNING',startBin:0,endBin:6,confidence:'HIGH'};
  globalThis.fetch=async(url,options)=>{
    const payload=JSON.parse(options.body);
    assert.ok(payload.messages[0].content.includes('treadmill'));
    assert.ok(payload.messages[0].content.includes('generic WORKOUT'));
    return new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:JSON.stringify({activities:[prediction]})}}],usage:{total_tokens:100}}),{status:200});
  };
  const measured=[145,0.4,65,null,1,1], missing=[145,0.4,null,null,1,1];
  const weakCadence=[145,0.4,22,null,1,1], zero=[145,0.4,0,null,1,1];
  async function detect(mode,first,excluded=[]){
    const bins=[...first,...Array.from({length:30-first.length},()=>missing)];
    const response=await worker.fetch(req(mode,bins,excluded),env);
    assert.equal(response.status,200,await response.clone().text());
    const result=(await response.json()).activityDetection;
    for(const a of result.activities) assert.equal(a.confidence,'HIGH');
    return result.activities;
  }
  assert.equal((await detect('HIGH',[measured,missing,measured,missing,measured,missing])).length,1);
  assert.equal((await detect('MEDIUM',[measured,missing,measured,missing,measured,missing])).length,0);
  assert.equal((await detect('MEDIUM',[measured,measured,missing,measured,measured,missing])).length,1);
  assert.equal((await detect('LOW',[measured,missing,measured,missing,measured,missing])).length,0);
  assert.equal((await detect('HIGH',Array(6).fill(missing))).length,0);
  assert.equal((await detect('HIGH',[weakCadence,missing,weakCadence,missing,weakCadence,missing])).length,0);
  assert.equal((await detect('HIGH',[measured,zero,measured,missing,measured,missing])).length,0);
  assert.equal((await detect('HIGH',[measured,[145,0.4,null,null,0.1,1],measured,missing,measured,missing])).length,0);
  assert.equal((await detect('HIGH',Array(6).fill(measured),[[now-900,now-870]])).length,0);
  prediction={type:'WORKOUT',startBin:0,endBin:6,confidence:'HIGH'};
  assert.equal((await detect('HIGH',Array(6).fill(missing))).length,1);
  assert.equal((await detect('MEDIUM',Array(6).fill(missing))).length,0);
  assert.equal((await detect('HIGH',Array(6).fill([145,0,null,null,1,1]))).length,0);
  assert.equal((await detect('HIGH',Array(6).fill([null,0.4,65,null,0,1]))).length,0);
  prediction={...prediction,confidence:'MODERATE'};
  assert.equal((await detect('HIGH',Array(6).fill(missing))).length,0);
  prediction={type:'CLIMBING_STAIRS',startBin:0,endBin:6,confidence:'HIGH'};
  assert.equal((await detect('HIGH',Array(6).fill(measured))).length,0);
});
