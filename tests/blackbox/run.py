#!/usr/bin/env python3
"""Launch an isolated test-only backend/web entry, execute black-box suites, save evidence."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
import uuid
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
def now(): return dt.datetime.now(dt.timezone.utc).isoformat()
def scrub(value): return re.sub(r'eyJ[A-Za-z0-9_.-]+','[EPHEMERAL_TEST_TOKEN]',str(value))
def wait_http(url,process,timeout=180):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if process.poll() is not None: raise RuntimeError('Test service exited; inspect ignored runtime logs')
        try:
            with urllib.request.urlopen(url,timeout=2) as response:
                if response.status==200:return
        except Exception: pass
        time.sleep(.25)
    raise TimeoutError('Test service did not become ready: '+url)
def free(port):
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1',port))==0: raise RuntimeError(f'Port {port} is occupied; no existing service will be stopped')
def unit_records(path):
    if not path.exists(): return []
    root=ET.parse(path).getroot(); output=[]
    for case in root.findall('testcase'):
        match=re.search(r'BBU\d+',case.attrib['name'])
        if not match: continue
        failure=case.find('failure'); error=case.find('error'); skipped=case.find('skipped')
        status='failed' if failure is not None else 'error' if error is not None else 'skipped' if skipped is not None else 'passed'
        log=case.findtext('system-out','')
        observed=[line.strip() for line in log.splitlines() if match[0]+' actual=' in line]
        node=failure if failure is not None else error
        output.append({'id':match[0],'status':status,'duration_seconds':float(case.attrib.get('time',0)),
                       'observed':observed,'error':scrub(node.text) if node is not None else None})
    return output
def browser_records(path):
    if not path.exists():return []
    output=[]
    def walk(suites):
        for suite in suites:
            for spec in suite.get('specs',[]):
                match=re.search(r'BBS\d+',spec['title'])
                if not match:continue
                result=spec['tests'][0]['results'][-1] if spec.get('tests') and spec['tests'][0].get('results') else {}
                status=result.get('status','not_run')
                output.append({'id':match[0],'status':'passed' if status=='passed' else status,
                    'duration_seconds':result.get('duration',0)/1000,
                    'observed':['Browser interactions and visible/API assertions matched the stated oracle.'] if status=='passed' else [],
                    'error':scrub(result.get('error',{}).get('message','')) or None})
            walk(suite.get('suites',[]))
    walk(json.loads(path.read_text()).get('suites',[]));return output

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--emulator-host',default=os.environ.get('FIRESTORE_EMULATOR_HOST','127.0.0.1:8081'))
    parser.add_argument('--api-port',type=int,default=18080)
    parser.add_argument('--web-port',type=int,default=18081)
    parser.add_argument('--skip-browser',action='store_true')
    args=parser.parse_args()
    if not re.fullmatch(r'(127\.0\.0\.1|localhost):\d+',args.emulator_host):
        parser.error('Only an explicitly local emulator host is accepted')
    free(args.api_port);free(args.web_port)
    run=uuid.uuid4().hex[:10];project='demo-bb-'+run
    artifacts=HERE/'.artifacts'/run;artifacts.mkdir(parents=True)
    identities=artifacts/'identities.json'
    env=os.environ.copy();env.update(BB_RUN_ID=run,BB_PROJECT_ID=project,FIRESTORE_EMULATOR_HOST=args.emulator_host,
        BB_IDENTITIES_FILE=str(identities),BB_API_PORT=str(args.api_port),BB_WEB_PORT=str(args.web_port),
        BB_API_URL=f'http://127.0.0.1:{args.api_port}',BB_WEB_URL=f'http://127.0.0.1:{args.web_port}',
        BB_API_RESULTS=str(artifacts/'api.json'),BB_BROWSER_RESULTS=str(artifacts/'browser.json'),
        BB_BROWSER_ARTIFACTS=str(artifacts/'browser-artifacts'))
    processes=[];logs=[];records=[];commands=[];started=now();start=time.monotonic();fatal=None
    def run_step(command,cwd,log_name):
        print('Running '+log_name,flush=True);begin=time.monotonic()
        with open(artifacts/log_name,'w') as log:
            result=subprocess.run(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT)
        commands.append({'stage':log_name,'return_code':result.returncode,'duration_seconds':round(time.monotonic()-begin,3)})
        return result.returncode
    def launch(command,cwd,name):
        log=open(artifacts/name,'w');logs.append(log)
        process=subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        processes.append(process);return process
    try:
        run_step(['./mvnw','--batch-mode','--no-transfer-progress','-Dtest=ExpenseSplitBlackBoxTest','test'],ROOT/'backend','unit.log')
        records.extend(unit_records(ROOT/'backend/target/surefire-reports/TEST-edu.group17.roommates.blackbox.ExpenseSplitBlackBoxTest.xml'))
        print('Starting test-only HTTP backend (not the normal production launcher)',flush=True)
        server=launch(['./mvnw','--batch-mode','--no-transfer-progress','test-compile',
            'org.codehaus.mojo:exec-maven-plugin:3.5.0:java','-Dexec.mainClass=edu.group17.roommates.blackbox.BlackBoxTestServer',
            '-Dexec.classpathScope=test'],ROOT/'backend','server.log')
        wait_http(env['BB_API_URL']+'/api/health',server)
        run_step([sys.executable,str(HERE/'api_tests.py')],ROOT,'api.log')
        if (artifacts/'api.json').exists():records.extend(json.loads((artifacts/'api.json').read_text()))
        if not args.skip_browser:
            web=launch(['node',str(ROOT/'frontend/node_modules/vite/bin/vite.js'),'--config',str(HERE/'vite.config.mjs')],ROOT,'web.log')
            wait_http(env['BB_WEB_URL'],web)
            run_step(['node',str(HERE/'node_modules/@playwright/test/cli.js'),'test','--config',str(HERE/'playwright.config.mjs')],HERE,'browser.log')
            records.extend(browser_records(artifacts/'browser.json'))
    except Exception as error:
        fatal=scrub(error);print(fatal,flush=True)
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=12)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
        for log in logs:log.close()
        # Cleanup targets the exclusive generated project, never another emulator project's data.
        try:
            assert re.fullmatch(r'demo-bb-[a-f0-9]{10}',project)
            request=urllib.request.Request(f'http://{args.emulator_host}/emulator/v1/projects/{project}/databases/(default)/documents',
                method='DELETE',headers={'Authorization':'Bearer owner'})
            with urllib.request.urlopen(request,timeout=15):pass
        except Exception as error: print('Isolated fixture cleanup warning: '+scrub(error),flush=True)
        if identities.exists():identities.unlink()
    plan=json.loads((HERE/'cases.json').read_text());found={record['id'] for record in records}
    for case in plan['cases']:
        if case['id'] not in found: records.append({'id':case['id'],'status':'not_run','duration_seconds':0,
            'observed':[],'error':'Browser intentionally skipped' if args.skip_browser and case['level']=='system' else fatal or 'No execution evidence'})
    evidence={'started_utc':started,'finished_utc':now(),'execution_seconds':round(time.monotonic()-start,3),
        'product_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
        'product_branch':subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip(),
        'identity_boundary':'Ephemeral RSA-signed fixture identities; no live Firebase account creation. Production identity verification trust source is replaced only on the test classpath.',
        'database':'Exclusive demo-bb- namespace in localhost emulator; deleted after run',
        'versions':{'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.splitlines()[0],
                    'node':subprocess.check_output(['node','--version'],text=True).strip(),'python':sys.version.split()[0]},
        'commands':commands,'fatal_error':fatal,'results':sorted(records,key=lambda item:item['id'])}
    destination=ROOT/'docs/testing/evidence/latest.json';destination.parent.mkdir(parents=True,exist_ok=True)
    sanitized=scrub(json.dumps(evidence,indent=2)).replace(str(ROOT),'[REPO]')
    destination.write_text(sanitized+'\n')
    passed=sum(record['status']=='passed' for record in records)
    print(f'Black-box results: {passed}/{len(records)} passed. Evidence: {destination.relative_to(ROOT)}',flush=True)
    return 0 if not fatal and passed==len(records) else 1

if __name__=='__main__':raise SystemExit(main())
