#!/usr/bin/env python3
"""Generate matching DOCX/PDF assignment content from actual sanitized execution evidence."""
import argparse
import csv
import datetime as dt
import json
from pathlib import Path
import re
from xml.sax.saxutils import escape
from docx import Document
from docx.shared import Inches, Pt
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.pagesizes import A4
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether
from reportlab.platypus.tableofcontents import TableOfContents
from pypdf import PdfReader

ROOT=Path(__file__).resolve().parents[2]
HERE=Path(__file__).resolve().parent
OUT=ROOT/'docs/testing'
REFERENCES=[
 ('[1] Group 17 Project 2 proposal and testing assignment','Supplied by the user in this conversation; course CS3300. The YouTube/resource links were placeholders, not accessible URLs; their content is not claimed as reviewed.'),
 ('[2] Testing.docx / Test_assignment_template.docx','User-supplied course document Testing.docx. Section hierarchy and test/comparison fields retained; instructor comments removed.'),
 ('[3] Fair Share public interface contracts','Repository readme.md, backend/README.md, backend/INVITATIONS.md, backend/BALANCES.md, HTTP request/response DTO definitions, and issue #3. Product commit is recorded with run evidence.'),
 ('[4] ISTQB Foundation Level Syllabus v4.0.1','ISTQB, 2024; black-box equivalence partitions, boundary values, decision tables, and state transitions. https://istqb.org/wp-content/uploads/2024/11/ISTQB_CTFL_Syllabus_v4.0.1.pdf'),
 ('[5] Playwright documentation','Microsoft/Playwright: locators, auto-waiting, retrying assertions, isolated browser contexts and trace diagnostics. https://playwright.dev/docs/writing-tests ; https://playwright.dev/docs/actionability ; https://playwright.dev/docs/trace-viewer'),
]

def plain(value):
    text=re.sub(r'\x1b\[[0-9;]*m','',str(value))
    return ''.join(char for char in text if char in '\n\t' or ord(char)>=32)

def content(evidence,plan):
    results={result['id']:result for result in evidence['results']}
    passed=sum(result['status']=='passed' for result in results.values())
    failed=[result['id'] for result in results.values() if result['status'] not in ['passed','not_run','skipped']]
    defects=list(csv.DictReader((OUT/'defects.csv').open()))
    humans=list(csv.DictReader((HERE/'traditional/results.csv').open()))
    implemented=[requirement for requirement in plan['requirements'] if requirement['implemented']]
    touched={case['requirement'] for case in plan['cases'] if results.get(case['id'],{}).get('status')!='not_run'}
    start=json.loads((HERE/'authoring-window.json').read_text())
    window_start=dt.datetime.strptime(start['start_utc'],'%Y-%m-%d %H:%M:%S UTC').replace(tzinfo=dt.timezone.utc)
    authoring_seconds=round((dt.datetime.now(dt.timezone.utc)-window_start).total_seconds())
    summary=f'{passed}/{len(plan["cases"])} cases passed; {len(failed)} failed/error cases; {sum(r["status"]=="not_run" for r in results.values())} not run. '
    blocks=[]
    def h(level,text):blocks.append(('h',level,text))
    def p(text):blocks.append(('p',text))
    def table(headers,rows):blocks.append(('table',headers,rows))
    h(1,'1 Introduction')
    h(2,'1.1 Overview (Executive Summary)')
    p('Fair Share organizes roommate households, expense splits, invitations, history, and balances. This document is Group 17’s AI-assisted BLACK-BOX contribution. It is not a completed combined submission: independent traditional results and the separate white-box work in PR #59 must be integrated and human-reviewed before course submission.')
    p(summary+'Results are recorded rather than changed to force a green suite. A failed assertion is triaged before being counted as a product defect; harness problems are excluded from defect metrics.')
    p(f'Execution: {evidence["started_utc"]} through {evidence["finished_utc"]}; {evidence["execution_seconds"]} seconds including service startup. Product commit: {evidence["product_commit"]}. No statement/branch coverage is claimed by black-box tests.')
    h(2,'1.2 Assumptions')
    p('All scenarios run locally with Java 21, Node 22, Python, Chromium, and a Firestore emulator. A unique demo-bb- namespace is created and deleted for each run. The production app, services, API transport, and persistence are real; the external identity provider/key source is a test fixture. No real cloud accounts are created. Signup/password checking, Google token rotation/revocation, and a production deployment are therefore not fully tested.')
    p('The AI agent had earlier implementation context from this conversation. Test oracles were derived from requirements, documented contracts, and fixed arithmetic examples, not copied from internal methods. This is a requirements-based black-box approach, not a claim that the author was blind to all source code. Identity injection and teardown are fixture infrastructure, not business-logic assertions.')
    p('Splitting-component zero expenses are valid; the published create-expense API requires positive integer cents. These are distinct contracts. Test-only signing keys/entry points are outside the production image and raw tokens/logs/traces remain ignored. Browser cases use only accessible UI controls and public HTTP results.')
    table(['Environment','Observed'],[[key,value] for key,value in evidence['versions'].items()])
    h(2,'1.3 Definitions and Acronyms')
    table(['Term','Definition'],[['BBU / BBA / BBS','Black-box public-interface unit / HTTP integration / browser system case IDs.'],['Oracle','Expected result fixed before execution from the specification.'],['EP / BVA','Equivalence partitioning / boundary value analysis.'],['P / F / NR','Passed / failed / not run. A known failure remains a failure in strict execution.'],['Requirement touched','At least one executed example maps to a requirement; not proof that the whole requirement is covered.'],['Confirmed issue','A reproducible externally observed behavior mismatch; root-cause grouping remains provisional until implementation analysis.']])
    h(2,'1.4 References')
    for title,description in REFERENCES:p(title+'. '+description)
    h(2,'1.5 Items Not Covered By These Test Cases')
    for case in plan['unexecuted']:p(case['id']+': '+case['input']+'. '+case['reason'])
    p('Payment requests, mock payments/settlement writes, Gmail reminders, messaging, and monthly summaries are planned features not implemented in the tested commit. Their absence is a scope gap, not a test failure or a passed test. No load testing, full accessibility audit, or cross-browser certification is claimed.')
    h(1,'2 Quality Control')
    h(2,'2.1 Test Plan Quality')
    p('Each case records a requirement, public input, fixed expected result, actual observation, status, technique, and AI provenance. Human reviewers must independently check expected cent amounts and access-control decisions. First-run locator/environment mistakes are classified as harness issues, not counted as defects. Failure reruns retain the original oracle. The traditional cohort should freeze its own test plan before reading AI cases to limit contamination.')
    h(2,'2.2 Adequacy criterion')
    p('Black-box contribution completion means all 35 catalogued cases are attempted with evidence, failures triaged and reproducible, missing behaviors recorded explicitly, and the runner/report reproducible. Product acceptance requires all blocking failures resolved and passing on the same commit. Final assignment acceptance additionally requires an independently measured traditional baseline, white-box unit/integration evidence and AI justification, completed team contributions, and human review.')
    p(f'{len(touched & {r["id"] for r in implemented})}/{len(implemented)} implemented requirement groups are touched by executed examples; {len(touched)}/{len(plan["requirements"])} proposal requirement groups are touched. This is traceability, not a branch-coverage percentage or proof of complete requirements coverage. Live-identity and delayed-expiry gaps remain even when local examples pass.')
    table(['Requirement','Scope','Mapped cases'],[[r['id']+' '+r['description'],'Implemented/local' if r['implemented'] else 'Planned; not tested',', '.join(c['id'] for c in plan['cases'] if c['requirement']==r['id']) or 'None'] for r in plan['requirements']])
    h(2,'2.3 Bug Tracking')
    p('docs/testing/defects.csv tracks source cases, status, severity, reproduction, assignment, and resolution. Evidence is in docs/testing/evidence/latest.json. Failures sharing an external symptom are grouped conservatively; distinct root causes are not asserted without investigation. No person is assigned a bug without team confirmation. Harness dependency/locator problems are recorded separately from product findings.')
    table(['ID / source','Status / severity','Finding / resolution'],[[d['defect_id']+' / '+d['source_case'],d['status']+' / '+d['severity'],d['description']+' Resolution: '+d['resolution']] for d in defects] or [['None recorded','Pending triage','No issue count is invented.']])
    h(1,'3 Test Strategy')
    h(2,'3.1 Testing Process')
    p('Unit: invoke only the published expense-splitting strategy interface with independent examples. Integration: send HTTP requests through a real running Spring server into Firestore, observing status codes, returned shares/history/balances and authorization boundaries. System: drive the actual React app in Chromium for sign-in/session, household, invitation, expense and history workflows. Test records are isolated and removed; no private repository state is used as an assertion oracle.')
    p('Traditional cohort: use tests/blackbox/traditional/protocol.md and the blank results CSV; independently design EP/BVA, decision-table and state-transition cases, measure active design/setup/execution/repair separately, then record actual results. The AI-generated protocol is not itself human-authored traditional testing. There are currently '+str(len(humans))+' recorded traditional rows.')
    p('AI-assisted cohort: OpenAI Codex/GPT generated the catalog, executable tests, fixture orchestration and report; inspected failure output; corrected harness configuration; and kept product failures as evidence. Students must validate conclusions and reflect on tool limitations. White-box source/branch-based testing is a separate contribution in PR #59 and is not relabelled as black-box here.')
    h(2,'3.2 Technology')
    p('JUnit 5 runs public-interface examples; Python unittest/urllib supplies independent HTTP clients and per-case timing; Playwright 1.58.2 drives Chromium with role/label locators, auto-waiting, isolated browser contexts, and failure diagnostics [5]. Those Playwright features reduce fixed-sleep/selector maintenance but do not make Playwright itself an AI tool. The mandatory AI tool is Codex/GPT, used for requirement-to-case expansion, automation and evidence synthesis. Report generation uses python-docx, ReportLab and pypdf.')
    p('Reproduction: install frontend and tests/blackbox npm locks; install Chromium; start Firestore; run python3 tests/blackbox/run.py. Optional separate CI workflow uses the same runner. A nonzero exit preserves true product failures; do not weaken expected results to make CI green. Runtime processes use separate ports and cannot stop the ordinary development server.')
    h(1,'4 Test Cases')
    h(2,'4.1 Blackbox testing cases')
    p('Derived using EP/BVA and sequences/access decision tables [4]. “Did not cover” names fixture/scope limits; “only covered by AI” is UNKNOWN until an independent traditional inventory is available. All executed case designs below are AI-assisted; no human-authored cases are implied.')
    for case in plan['cases']:
        result=results.get(case['id'],{'status':'not_run','observed':[]})
        h(3,case['id']+' — '+case['level'].capitalize())
        actual='; '.join(result.get('observed',[])) or 'No completed observation recorded.'
        if result.get('error'):actual+=' Assertion/error: '+plain(result['error'])[-900:]
        rows=[['Test #',case['id']],['Requirement / purpose',case['requirement']+'; '+case['technique']],
              ['Action / input',case['input']],['Expected result',case['expected']],['Actual result',actual],
              ['P/F',result['status'].upper()+f'; {result.get("duration_seconds",0):.3f} execution seconds'],
              ['AI tool utilization',case['author']+'; generated from requirements and external contracts.'],
              ['Notes', 'Identity provider is simulated for account/system cases; real API/database for business operations.'],
              ['Did not cover this requirement','Only the stated example/partition is covered; broader gaps are in 1.5.'],
              ['Was only covered by AI','Unknown; independent traditional case inventory not supplied.']]
        table(['Field','Case evidence'],rows)
    h(2,'4.2 Whitebox testing cases')
    p('Not part of this PR. White-box PR #59 contains separate work; no results, snippets, structural coverage, authors, or AI-tool use from it are invented here. Before final submission, the white-box team must provide traditional and AI-assisted UNIT AND INTEGRATION cases, internal code paths/snippets, expected/actual behavior, coverage evidence, prompts, metrics, and reflections. The assignment’s mandatory white-box AI requirement remains unverified by this black-box document.')
    table(['Test / unit / purpose','Snippet / behavior / P-F','AI utilization / gaps'],[['Pending white-box team evidence','No unexecuted test is marked passed.','Integrate PR #59 evidence and confirm both testing levels.']])
    h(1,'5 AI Integration and Comparative Analysis')
    h(2,'5.1 Overview of AI Tool Impact')
    p('Observed benefits: one session produced a systematic catalog plus unit, HTTP, browser and report artifacts; execution and failure evidence can be reproduced. Constraints: AI initially needed harness dependency/locator corrections; it cannot supply an independent human baseline, silently infer intended HTTP errors, wait out a 48-hour expiry in this bounded run, or validate real Firebase password security through a fixture. No superiority or time saving is claimed without matched human data.')
    h(2,'5.2 Detailed Comparative Analysis')
    defect_count=len([d for d in defects if d['status'].startswith('Confirmed')])
    rows=[['Development time','Not measured; independent cohort pending',f'{authoring_seconds} seconds in instrumented implementation window','No valid relative speedup. This includes waits/setup, excludes initial planning, and is not active effort.'],
          ['Execution time','Not measured',str(evidence['execution_seconds'])+' seconds end-to-end','Includes test server startup; per-case durations are in evidence.'],
          ['Defects detected / detection rate','Unknown, not zero',str(defect_count)+' confirmed issue groups; '+str(len(failed))+' failed cases; '+f'{defect_count}/{len(results)} = {defect_count/max(len(results),1):.3f} issue groups per executed case','Detection yield, not recall: the true defect inventory is unknown. Conservative groups are not proven distinct root causes. No comparison to an absent baseline.'],
          ['Test coverage','No independent inventory',f'{len(touched & {r["id"] for r in implemented})}/{len(implemented)} implemented groups touched; '+str(passed)+'/'+str(len(plan['cases']))+' case oracles passed','Requirement examples, not structural coverage. Full proposal and identity/expiry gaps remain.'],
          ['Quality of edge cases','Human assessment pending','Remainder cents, >2^53 integer money, invalid totals, boundaries, concurrent joins, cross-household IDs, currency partitions','Quality is not automatically higher because a case was generated by AI.'],
          ['Ease of writing/maintaining tests','Timed repair/rubric pending','Shared fixture clients, accessible locators and one evidence/report model; harness repair was still needed','Measure actual minutes for a locator change, new boundary, and API contract update before scoring ease.']]
    table(['Metric','Traditional method','AI-enhanced method','Difference / notes'],rows)
    p('White-box comparison: pending the separate team. Record the same development-time, unique-defect, structural-coverage, edge-case, and maintenance metrics for its traditional versus AI-assisted unit/integration cohorts. Black-box traceability must not be substituted for white-box branch/statement coverage.')
    h(2,'5.3 AI Tool Utilization')
    table(['Tool / role','Concrete use','Benefit hypothesis / limitation'],[['OpenAI Codex / GPT-based assistant','Generated requirement partitions, cent oracles, unit/API/browser cases, runner, failure triage and this report. Exact model build was not independently verified.','Multi-file generation and tool execution make experiments repeatable; prior implementation context and hallucinated assumptions require independent review.'],['Playwright (not itself the AI tool)','Browser automation with role/label locators, auto-waiting, isolated contexts, screenshots/traces [5].','Less fixed-sleep fragility; still needs correct setup and semantically accurate expectations.'],['Traditional/manual or Selenium cohort','Protocol and blank results sheet supplied only.','Must be executed by assigned students before comparisons or AI-only claims are made.']])
    p('Prompt record: the user requested a new black-box PR following the provided CS3300 assignment and Testing.docx. The agent expanded public requirements into EP/BVA, authorization, sequence and concurrency cases, ran real local layers behind a simulated identity boundary, and recorded actual outcomes. The agent was instructed not to fabricate traditional/white-box results or alter production code to satisfy tests. tests/blackbox/cases.json is the concrete generated case inventory.')
    h(1,'6 Team Contribution')
    table(['Contributor','Recorded/proposed role','Confirmation / actual contribution'],[['Joseph Huang','Requested/coordinated this AI-assisted black-box contribution','Request is recorded; student review, measured active effort and final contribution statement still require confirmation.'],['OpenAI Codex/GPT agent','AI-assisted design, implementation, execution, triage and documentation','Actual automation in this PR; not represented as student manual testing.'],['Traditional testers (1–2)','Unassigned','No independent design/execution evidence supplied.'],['White-box team (2–3)','Separate PR #59','Names, traditional/AI roles and unit/integration results must be integrated by that team.']])
    p('Before submission: confirm member names/roles; independently verify expectations and findings; execute/measure the traditional cohort; integrate white-box evidence and AI justification; resolve or explicitly disclose known defects; regenerate both documents and review the final PDF. The requested filename is provided, but this contribution must not be submitted as if those missing activities were complete.')
    metrics={'passed':passed,'total_cases':len(plan['cases']),'failed_cases':failed,'confirmed_issue_groups':defect_count,
        'implemented_groups_touched':len(touched & {r['id'] for r in implemented}),'implemented_groups':len(implemented),
        'proposal_groups':len(plan['requirements']),'authoring_window_seconds':authoring_seconds,'authoring_scope':start['scope'],
        'traditional_rows':len(humans),'comparative_speedup':None,'whitebox_status':'Separate PR #59; not evaluated here'}
    return blocks,metrics

def word(blocks,template,destination):
    doc=Document(str(template)) if template and template.exists() else Document()
    for child in list(doc.element.body):
        if child.tag!=qn('w:sectPr'):doc.element.body.remove(child)
    for section in doc.sections:section.top_margin=Inches(.7);section.bottom_margin=Inches(.7)
    doc.styles['Normal'].font.name='Arial';doc.styles['Normal'].font.size=Pt(10)
    doc.add_heading('CS3300: Introduction to Software Engineering',0)
    doc.add_heading('Fair Share — Group 17 Test Plan and Report',0)
    doc.add_paragraph('Black-box contribution draft | '+dt.date.today().isoformat())
    doc.add_paragraph('Traditional comparison and white-box integration pending. Actual test failures are disclosed.')
    doc.add_heading('Contents',1)
    for item in blocks:
        if item[0]=='h' and item[1]<=2:doc.add_paragraph(item[2])
    doc.add_page_break()
    for item in blocks:
        if item[0]=='h':doc.add_heading(item[2],item[1])
        elif item[0]=='p':doc.add_paragraph(plain(item[1]))
        else:
            table=doc.add_table(rows=1,cols=len(item[1]));table.style='Table Grid'
            for cell,text in zip(table.rows[0].cells,item[1]):cell.text=plain(text)
            for row in item[2]:
                cells=table.add_row().cells
                for cell,text in zip(cells,row):cell.text=plain(text)
            for row in table.rows:
                for cell in row.cells:
                    for para in cell.paragraphs:
                        for run in para.runs:run.font.size=Pt(8)
            doc.add_paragraph('')
    doc.save(destination)

class Report(SimpleDocTemplate):
    def afterFlowable(self,flowable):
        if isinstance(flowable,Paragraph) and flowable.style.name in ['Heading1','Heading2']:
            self.notify('TOCEntry',(0 if flowable.style.name=='Heading1' else 1,flowable.getPlainText(),self.page))

def pdf(blocks,destination):
    styles=getSampleStyleSheet();styles['Normal'].fontSize=9;styles['Normal'].leading=12
    styles.add(ParagraphStyle(name='Cell',fontName='Helvetica',fontSize=7.5,leading=10,spaceAfter=0,wordWrap='CJK'))
    styles['Heading1'].spaceBefore=18;styles['Heading2'].spaceBefore=12;styles['Heading3'].spaceBefore=10
    def para(text,style='Normal'):return Paragraph(escape(plain(text)).replace('\n','<br/>'),styles[style])
    story=[para('CS3300: Introduction to Software Engineering','Title'),para('Fair Share — Group 17','Title'),
           para('Test Plan and Report: Black-box Contribution','Title'),Spacer(1,20),
           para('Version 1.0 | '+dt.date.today().isoformat()),
           para('DRAFT FOR COMBINED SUBMISSION: independent traditional results and white-box integration are pending. Actual failures are disclosed.'),PageBreak(),para('Contents','Title')]
    toc=TableOfContents();toc.levelStyles=[ParagraphStyle(name='TOC1',fontSize=10,leading=14,spaceBefore=7),ParagraphStyle(name='TOC2',fontSize=9,leading=12,leftIndent=14)]
    story.extend([toc,PageBreak()])
    width=A4[0]-84
    for item in blocks:
        if item[0]=='h':story.append(para(item[2],'Heading'+str(item[1])))
        elif item[0]=='p':story.extend([para(item[1]),Spacer(1,7)])
        else:
            data=[[para(cell,'Cell') for cell in item[1]]]+[[para(cell,'Cell') for cell in row] for row in item[2]]
            columns=len(item[1]);widths=[width/columns]*columns
            if columns==2:widths=[width*.25,width*.75]
            table=Table(data,colWidths=widths,repeatRows=1,hAlign='LEFT')
            table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),colors.HexColor('#dfece7')),
                ('GRID',(0,0),(-1,-1),.35,colors.HexColor('#c2cec8')),('VALIGN',(0,0),(-1,-1),'TOP'),
                ('LEFTPADDING',(0,0),(-1,-1),6),('RIGHTPADDING',(0,0),(-1,-1),6),
                ('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),5)]))
            story.extend([table,Spacer(1,10)])
    def footer(canvas,doc):
        canvas.setFont('Helvetica',8);canvas.drawString(42,24,'Group 17 | Black-box contribution draft')
        canvas.drawRightString(A4[0]-42,24,str(doc.page))
    report=Report(str(destination),pagesize=A4,rightMargin=42,leftMargin=42,topMargin=42,bottomMargin=42,
                  title='Group 17 Project 2 Testing',author='Group 17 / AI-assisted black-box contribution')
    report.multiBuild(story,onFirstPage=footer,onLaterPages=footer)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--template',type=Path);args=parser.parse_args()
    evidence=json.loads((OUT/'evidence/latest.json').read_text());plan=json.loads((HERE/'cases.json').read_text())
    blocks,metrics=content(evidence,plan)
    word(blocks,args.template,OUT/'Group_17_Project2_Testing.docx')
    pdf(blocks,OUT/'Group_17_Project2_Testing.pdf')
    reader=PdfReader(OUT/'Group_17_Project2_Testing.pdf');text='\n'.join(page.extract_text() or '' for page in reader.pages)
    for required in ['1 Introduction','4.1 Blackbox','4.2 Whitebox','5.2 Detailed','6 Team Contribution','BBU01','BBA20','BBS07']:
        assert required in text,required+' missing from PDF'
    for forbidden in ['[Project Name]','[Date published]','IMPORTANT:']:
        assert forbidden not in text,'Template placeholder remains'
    metrics['pdf_pages']=len(reader.pages);metrics['pdf_text_verified']=True
    (OUT/'metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
    print('Generated and text-verified PDF ('+str(len(reader.pages))+' pages) and editable DOCX.')

if __name__=='__main__':main()
