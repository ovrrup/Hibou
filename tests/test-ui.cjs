const fs=require('fs'), assert=require('assert');
const {JSDOM}=require('jsdom');
const root=process.env.HIBOU_TEST_WEB_DIR || process.env.LEOSTRYX_TEST_WEB_DIR; if(!root) throw new Error('Set HIBOU_TEST_WEB_DIR to extracted APK assets/web/ with trailing slash');let passed=0;
function check(name,fn){fn();console.log('PASS '+name);passed++;}
function setup(available=true){
 const dom=new JSDOM('<div id="host"></div>',{url:'https://app.local',runScripts:'outside-only'}),w=dom.window;
 let calls=[],jobs={},counter=0, installs=0;
 w.AgyTools={getAppInfo:()=>JSON.stringify({versionName:'3.0.6',versionCode:22,variant:'bypass'}),installUpdate:()=>{installs++;return'installer';},start:(op,arg)=>{calls.push({op,arg:JSON.parse(arg)});let id=String(++counter);jobs[id]={rc:'0',log:op==='updatecheck'?'@@UPDATE_JSON '+JSON.stringify({available,tag:'v3.0.7',variant:'bypass',name:'Hibou 3.0.7 with spaces'}):'@@UPDATE_DOWNLOAD {"ready":true}'};return id;},read:id=>JSON.stringify(jobs[id])};
 w.eval(fs.readFileSync(root+'agy_tools.js','utf8'));w.__agyAboutMount(w.document.getElementById('host'));
 return {dom,w,calls,get installs(){return installs;}};
}
check('About shows native version and actual Bypass variant',()=>{let a=setup();assert(a.w.document.body.textContent.includes('3.0.6 · bypass'));assert(!a.w.document.body.textContent.includes('3.0.2'));a.dom.window.close();});
check('Current release does not offer download',()=>{let a=setup(false);[...a.w.document.querySelectorAll('button')].find(b=>b.textContent==='Проверить обновления').click();assert(a.w.document.body.textContent.includes('Установлена актуальная версия'));assert(!a.w.document.body.textContent.includes('Скачать обновление'));a.dom.window.close();});
check('Release name with spaces uses JSON and keeps Bypass',()=>{let a=setup();[...a.w.document.querySelectorAll('button')].find(b=>b.textContent==='Проверить обновления').click();[...a.w.document.querySelectorAll('button')].find(b=>b.textContent==='Скачать обновление').click();assert.equal(a.calls[0].arg.variant,'bypass');assert.equal(a.calls[1].arg.tag,'v3.0.7');assert.equal(a.installs,1);assert(a.w.document.body.textContent.includes('системном установщике'));assert(!a.w.document.body.textContent.includes('Обновление установлено!'));a.dom.window.close();});
check('Repeated checks do not accumulate download buttons',()=>{let a=setup();let b=[...a.w.document.querySelectorAll('button')].find(b=>b.textContent==='Проверить обновления');b.click();b.click();assert.equal([...a.w.document.querySelectorAll('button')].filter(b=>b.textContent==='Скачать обновление').length,1);a.dom.window.close();});
check('All selected distinct model IDs are grouped by configured provider',()=>{
 let dom=new JSDOM('',{url:'https://app.local',runScripts:'outside-only'}),w=dom.window;
 const cfg={useProviders:true,connections:[{id:'one',name:'My OpenAI',type:'openai',selected:['gpt-a','gpt-b']},{id:'two',name:'My Claude',type:'anthropic',selected:['claude-a']},{id:'three',name:'My Gemini',type:'gemini',selected:['gemini-a','gemini-b']}]};
 w.localStorage.setItem('agyProviders',JSON.stringify(cfg));w.eval(fs.readFileSync(root+'agy_providers.js','utf8'));
 let specs=w.__agyProviderSpecs();assert.equal(specs.length,5);let groups=w.__agyGroupProviderModels(specs);assert.equal(groups.length,3);assert.equal(groups.map(g=>g.models.length).join(','),'2,1,2');assert.equal(groups.map(g=>g.name).join(','),'My OpenAI,My Claude,My Gemini');
 // Execute the actual patched selector component with a small React element recorder.
 let main=fs.readFileSync(root+'generated/main.js','utf8');let component=main.slice(main.indexOf('var SXb='),main.indexOf(';var TXb=',main.indexOf('var SXb=')));
 w.A={createElement:(type,props,...children)=>({type,props,children}),useId:()=> 'id',Fragment:'fragment'};w.P4=()=>({supportsCustomModels:true});w.aj=(...x)=>x.join(' ');w.RXb='model-row';w.W='icon';w.ty='tooltip';w.eval(component+';window.testSelector=SXb;');
 let tree=w.testSelector({customModels:specs});let headers=[],rows=[];function walk(n){if(Array.isArray(n))return n.forEach(walk);if(!n||typeof n!=='object')return;if(n.type==='model-row')rows.push(n.props.model.label);if(n.props&&n.props.role==='presentation')headers.push(n.children[0]);walk(n.children);}
 walk(tree);assert.equal(rows.length,5);assert.equal(headers.join(','),'My OpenAI,My Claude,My Gemini');dom.window.close();
});
console.log(passed+' UI checks passed.');
