import test from "node:test";
import assert from "node:assert/strict";
import worker from "./worker.js";
import { createGroqAI } from "./groq-ai.js";

const endpoint = "https://alke.example.test/v1/coach/chat";
const question = {userId:"synthetic-test", conversationId:"test-1", question:"Hi Coach", recentMessages:[]};
function request(body=question) {
  return new Request(endpoint, {method:"POST", headers:{"Content-Type":"application/json"},
    body:JSON.stringify(body)});
}
const mockGroq = (answer="Ready to help.") => async (url, options) => {
  assert.equal(url, "https://api.groq.com/openai/v1/chat/completions");
  assert.equal(options.method, "POST");
  assert.equal(options.headers.Authorization, "Bearer example-synthetic-secret");
  const payload=JSON.parse(options.body);
  assert.ok(["openai/gpt-oss-20b","openai/gpt-oss-120b"].includes(payload.model));
  assert.equal(payload.stream, false);
  assert.ok(Array.isArray(payload.messages));
  assert.ok(payload.max_completion_tokens > 0);
  return new Response(JSON.stringify({choices:[{finish_reason:"stop",
    message:{role:"assistant", content:answer}}]}), {status:200,headers:{"content-type":"application/json"}});
};

test("Groq adapter maps existing 120B Worker binding model to Groq chat completions", async () => {
  const result=await createGroqAI("example-synthetic-secret",mockGroq()).run("@cf/openai/gpt-oss-120b",
    {input:[{role:"user",content:"Hello"}],max_output_tokens:1400,reasoning:{effort:"low"}});
  assert.equal(result.choices[0].message.content,"Ready to help.");
});
test("Groq adapter maps 20B for food", async () => {
  const result=await createGroqAI("example-synthetic-secret",mockGroq()).run("@cf/openai/gpt-oss-20b",
    {input:[{role:"user",content:"One egg"}],max_output_tokens:1000,reasoning:{effort:"low"}});
  assert.equal(result.choices[0].finish_reason,"stop");
});
test("Groq adapter returns safe error on 401 and never leaks secret or upstream body", async () => {
  const ai=createGroqAI("example-synthetic-secret",async()=>new Response("PRIVATE",{status:401}));
  await assert.rejects(ai.run("@cf/openai/gpt-oss-120b",
    {input:[{role:"user",content:"test"}],max_output_tokens:50}), e => {
    assert.doesNotMatch(e.message,/PRIVATE|synthetic-secret/);
    return /API key rejected/.test(e.message);
  });
});
test("Groq adapter does not forward arbitrary model or unsafe messages", async () => {
  const ai=createGroqAI("example-synthetic-secret",async()=>{throw Error("network must not run")});
  await assert.rejects(ai.run("other-model",{input:[{role:"user",content:"test"}],max_output_tokens:20}),/Unsupported/);
  await assert.rejects(ai.run("@cf/openai/gpt-oss-20b",
    {input:[{role:"tool",content:"malicious"}],max_output_tokens:20}),/Invalid Groq input/);
});
test("Cloud Coach strictly requires Groq secret when AI_BACKEND=groq",async()=>{
  const response=await worker.fetch(request(),{AI_BACKEND:"groq",DISABLE_EXTERNAL_REFERENCES:"true"});
  assert.equal(response.status,503);
  assert.equal((await response.json()).retryable,false);
});
test("Cloud Coach calls Groq for normal chat and preserves Android response envelope", async(t)=>{
  const oldFetch=globalThis.fetch;
  globalThis.fetch=mockGroq();
  t.after(()=>{globalThis.fetch=oldFetch});
  const response=await worker.fetch(request(),{AI_BACKEND:"groq",GROQ_API_KEY:"example-synthetic-secret",
    DISABLE_EXTERNAL_REFERENCES:"true"});
  const body=await response.json();
  assert.equal(response.status,200);
  assert.equal(body.answer,"Ready to help.");
  assert.equal(body.conversationId,"test-1");
  assert.equal(body.intent,"chat");
  assert.deepEqual(body.references,[]);
});
