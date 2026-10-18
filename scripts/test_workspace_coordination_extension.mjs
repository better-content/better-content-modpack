#!/usr/bin/env node
/** Real installed Pi SDK runtime, inert model/tool, isolated agentDir; no network.
 * This does NOT prove an existing interactive session reloaded the installed hook.
 */
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
const bundle = '/home/dev/.local/share/worklane/pi-bundle/node_modules';
const { createAgentSession, DefaultResourceLoader, SessionManager, SettingsManager, ModelRuntime } = await import(pathToFileURL(`${bundle}/@earendil-works/pi-coding-agent/dist/index.js`));
const { AssistantMessageEventStream } = await import(pathToFileURL(`${bundle}/@earendil-works/pi-ai/dist/utils/event-stream.js`));
const { InMemoryCredentialStore } = await import(pathToFileURL(`${bundle}/@earendil-works/pi-ai/dist/auth/credential-store.js`));
const { InMemoryModelsStore } = await import(pathToFileURL(`${bundle}/@earendil-works/pi-ai/dist/models-store.js`));
mkdirSync('/home/dev/.tmp', {recursive:true});
const fixture = mkdtempSync('/home/dev/.tmp/coordination-pi-sdk-');
const root = join(fixture,'coordination');
const agentDir = join(fixture,'agent');
mkdirSync(agentDir);
const script = resolve('scripts/workspace_coordination.py');
process.env.WORKSPACE_COORDINATION_ROOT = root;
process.env.WORKSPACE_COORDINATION_SCRIPT = script;
process.env.WORKSPACE_COORDINATION_AREAS = 'identity:inert-fixture';
const storage=join(fixture,'storage.py');
writeFileSync(storage,'import json; print(json.dumps({"warnings":["inert storage pressure warning"]}))\n');
process.env.WORKSPACE_COORDINATION_MAINTENANCE_SCRIPT=storage;
process.env.WORKSPACE_COORDINATION_TASK = 'fixture-task';
process.env.WORKSPACE_COORDINATION_GENERATION = '1';
// Explicitly isolated from the real terminal/credential configuration.
for (const k of ['HERDR_SESSION','HERDR_WORKSPACE_ID','HERDR_TAB_ID','HERDR_PANE_ID']) delete process.env[k];
process.env.PI_OFFLINE = '1';
const cli = (...args) => JSON.parse(execFileSync('python3',['-B',script,'--root',root,...args],{encoding:'utf8'}));
const live = () => cli('live');
cli('task-register','--task','fixture-task','--owner','fixture-lead');
const lease = cli('lease-acquire','--task','fixture-task','--generation','1','--owner','fixture-lead','--resource','inert-resource').lease;
let toolCalls=0, modelCalls=0, lowEnds=0, settleRequests=0, settled=0;
const observations=[];
const observer = pi => {
  pi.registerTool({name:'inert_fixture',label:'Inert fixture',description:'No side effects',
    parameters:{type:'object',properties:{},additionalProperties:false},
    execute: async () => {
      assert.equal(Object.keys(live().turns).length,1,'claim held during actual tool execution');
      toolCalls++;
      return {content:[{type:'text',text:'inert result'}],details:{inert:true}};
    }});
  pi.on('agent_end',async()=>{
    lowEnds++;
    assert.equal(Object.keys(live().turns).length,1,'low-level completion must not release');
    observations.push({event:'agent_end',turns:Object.keys(live().turns).length});
  });
  pi.on('agent_before_settle',async()=>{
    if (settleRequests++===0) return {continue:true,entries:[{type:'custom_message',customType:'inert-continuation',content:'Continue once with inert completion.',display:false}]};
  });
  pi.on('agent_settled',async()=>{
    settled++;
    assert.equal(Object.keys(live().turns).length,0,'final settled releases');
    assert.ok(live().leases[lease],'turn end must preserve resource lease');
    observations.push({event:'agent_settled',turns:Object.keys(live().turns).length});
  });
};
const settingsManager=SettingsManager.inMemory({compaction:{enabled:false},retry:{enabled:false},cacheWarming:'off'});
const loader=new DefaultResourceLoader({cwd:fixture,agentDir,settingsManager,noSkills:true,noPromptTemplates:true,noThemes:true,
  additionalExtensionPaths:['/home/dev/.pi/agent/extensions/workspace-coordination.ts'],extensionFactories:[observer]});
await loader.reload();
// SDK factory defaults would otherwise construct file-backed auth/model stores.
// Explicit empty in-memory stores + modelsPath:null require no user credential/config files.
const credentials=new InMemoryCredentialStore();
const modelsStore=new InMemoryModelsStore();
const modelRuntime=await ModelRuntime.create({credentials,modelsStore,modelsPath:null,
  refreshOnCreate:false,allowModelNetwork:false});
assert.deepEqual(await credentials.list(),[]);
// Only the in-process inert model is selected. Satisfy SDK preflight without
// requesting any auth at all; the overridden stream below never contacts a provider.
modelRuntime.hasConfiguredAuth=provider=>{assert.equal(provider,'openai');return true;};
modelRuntime.checkAuth=async()=>{throw new Error('inert fixture must not query provider auth');};
modelRuntime.getAuth=async()=>{throw new Error('inert fixture must not retrieve credentials');};
const {session}=await createAgentSession({cwd:fixture,agentDir,modelRuntime,resourceLoader:loader,settingsManager,
  sessionManager:SessionManager.inMemory(fixture),tools:['inert_fixture','write','bash','coordination_claim'],thinkingLevel:'off',
  model:{id:'inert',name:'Inert fixture',api:'openai-completions',provider:'openai',baseUrl:'http://127.0.0.1:1',
    reasoning:false,input:['text'],cost:{input:0,output:0,cacheRead:0,cacheWrite:0},contextWindow:100000,maxTokens:100}});
const errors=[];
await session.bindExtensions({mode:'print',onError:e=>errors.push(String(e.error ?? e))});
session.agent.streamFunction = () => {
  modelCalls++;
  const current=Object.values(live().turns);
  assert.equal(current.length,1,'claim held on automatic continuation model request');
  assert.equal(current[0].liveness,'live','actual Pi process identity is recorded');
  assert.equal(current[0].actor_identity.pid,process.pid);
  assert.equal(current[0].session_id,session.sessionManager.getSessionId(),'stable conversation identity recorded');
  const stream = new AssistantMessageEventStream();
  let content=[{type:'text',text:'inert completion'}];
  if (modelCalls===1) content=[{type:'toolCall',id:'inert-call',name:'inert_fixture',arguments:{}}];
  if (modelCalls===2) content=[{type:'toolCall',id:'blocked-shell',name:'bash',arguments:{command:'printf blocked-shell'}}];
  if (modelCalls===3) content=[{type:'toolCall',id:'write-fixture',name:'write',arguments:{path:join(fixture,'output.txt'),content:'retained inert write'}},
    {type:'toolCall',id:'declare-directory',name:'coordination_claim',arguments:{areas:[join(fixture,'shell-output.txt')]}}];
  if (modelCalls===4) content=[{type:'toolCall',id:'admitted-shell',name:'bash',arguments:{command:'printf admitted-shell'}}];
  const toolUse=content.some(c=>c.type==='toolCall');
  const message={role:'assistant',content,
    api:'openai-completions',provider:'openai',model:'inert',usage:{input:0,output:0,cacheRead:0,cacheWrite:0,totalTokens:0,cost:{input:0,output:0,cacheRead:0,cacheWrite:0,total:0}},
    stopReason:toolUse?'toolUse':'stop',timestamp:Date.now()};
  queueMicrotask(()=>{stream.push({type:'done',reason:message.stopReason,message});stream.end(message);});
  return stream;
};
const manualShell=async label=>{
  assert.equal(Object.keys(live().turns).length,0,'manual shell starts outside agent turn');
  const before=modelCalls;
  const command=`printf ${label}`;
  // Same actual runner dispatch and session executor used by interactive !commands.
  const eventResult=await session.extensionRunner.emitUserBash({type:'user_bash',command,
    excludeFromContext:true,cwd:fixture});
  assert.equal(eventResult,undefined,'coordination must not intercept direct human shell');
  const result=await session.executeBash(command,undefined,{excludeFromContext:true});
  assert.equal(result.exitCode,0);
  assert.equal(result.output,label);
  assert.equal(modelCalls,before);
  assert.equal(Object.keys(live().turns).length,0,'human command does not invent an agent claim');
  observations.push({event:'manual_shell',label,exitCode:result.exitCode,turns:0});
};
try {
  await manualShell('manual-before-agent');
  await session.prompt('Run only the inert fixture.');
  assert.equal(toolCalls,1);
  assert.ok(modelCalls>=6,'tool follow-ups plus explicit automatic continuation');
  assert.equal(readFileSync(join(fixture,'output.txt'),'utf8'),'retained inert write');
  const blocked=session.messages.find(m=>m.role==='toolResult' && m.toolCallId==='blocked-shell');
  assert.ok(blocked?.isError,'undeclared shell is actually blocked');
  const admitted=session.messages.find(m=>m.role==='toolResult' && m.toolCallId==='admitted-shell');
  assert.equal(admitted?.isError,false,'declared narrow-scope shell admitted without whole-cwd lock');
  assert.ok(lowEnds>=2,'multiple low-level runs occurred');
  assert.equal(settled,1);
  assert.deepEqual(errors,[]);
  assert.equal(Object.keys(live().turns).length,0);
  assert.ok(live().tasks['fixture-task']);
  await session.prompt('Complete another inert turn to test warning deduplication.');
  const notices=session.messages.filter(m=>m.role==='custom' && m.customType==='workspace-coordination').map(m=>JSON.parse(m.content));
  assert.equal(notices.filter(m=>m.storageWarnings.includes('inert storage pressure warning')).length,1,'warning deduplicated across turns');
  assert.equal(settled,2);
  cli('task-cancel','--task','fixture-task','--owner','fixture-lead');
  const callsBefore=modelCalls;
  // Admission of a cancelled generation aborts before any model/tool work.
  await session.prompt('This cancelled generation must not run.');
  assert.equal(modelCalls,callsBefore);
  assert.equal(Object.keys(live().turns).length,0);
  await manualShell('manual-after-task-cancellation');
  assert.deepEqual(errors,[]);
  assert.deepEqual(await credentials.list(),[],'fixture credentials remain empty');
  console.log(JSON.stringify({ok:true,fixture,modelCalls,toolCalls,lowEnds,settled,observations}));
  writeFileSync(join(fixture,'observations.json'),JSON.stringify({modelCalls,toolCalls,lowEnds,settled,observations,errors},null,2));
} finally { session.dispose(); }
