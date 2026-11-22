"""Black-box integration cases: HTTP inputs/outputs only; no service/repository imports."""
import concurrent.futures
import json
import os
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request

IDENTITIES=json.load(open(os.environ['BB_IDENTITIES_FILE']))
BASE=os.environ.get('BB_API_URL','http://127.0.0.1:18080')

def call(method,path,role='alice',body=None,raw_token=None):
    headers={'Content-Type':'application/json'}
    if raw_token is not None: headers['Authorization']='Bearer '+raw_token
    elif role: headers['Authorization']='Bearer '+IDENTITIES[role]['token']
    request=urllib.request.Request(BASE+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=20) as response:
            text=response.read().decode();return response.status,json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        raw=error.read().decode()
        try: data=json.loads(raw) if raw else None
        except json.JSONDecodeError: data={'message':'non-JSON error response'}
        return error.code,data

class ApiCases(unittest.TestCase):
    def setUp(self): self.observations=[]
    def check(self,method,path,status=200,role='alice',body=None,raw_token=None):
        actual,data=call(method,path,role,body,raw_token)
        self.observations.append(f'{method} {path.split("?")[0]} -> HTTP {actual}')
        self.assertEqual(status,actual,repr(data));return data
    def home(self,two=True):
        home=self.check('POST','/api/households',201,body={'name':'Black-box '+self._testMethodName})
        if two:
            invite=self.check('POST',f'/api/households/{home["id"]}/invitations',201)
            self.check('POST','/api/invitations/redeem',role='bob',body={'code':invite['code']})
        return home['id']
    def expense(self,home,**changes):
        data={'description':'Black-box groceries','amountCents':101,'currency':'USD',
            'payerId':IDENTITIES['alice']['uid'],'participantIds':[IDENTITIES['alice']['uid'],IDENTITIES['bob']['uid']],
            'expenseDate':'2026-11-20','category':'GROCERIES','splitType':'EQUAL'}
        data.update(changes);return data
    def create(self,home,**changes):
        return self.check('POST',f'/api/households/{home}/expenses',201,body=self.expense(home,**changes))

    def test_BBA01_health_is_public(self): self.assertEqual('UP',self.check('GET','/api/health',role=None)['status'])
    def test_BBA02_missing_and_forged_tokens(self):
        self.check('GET','/api/households',401,role=None)
        self.check('GET','/api/households',401,raw_token='not-a-valid-signed-token')
    def test_BBA03_identity_is_not_a_query_parameter(self):
        data=self.check('GET','/api/me?uid='+IDENTITIES['bob']['uid'])
        self.assertEqual(IDENTITIES['alice']['uid'],data['uid']);self.observations.append('verified UID remains Alice')
    def test_BBA04_household_name_boundaries(self):
        self.check('POST','/api/households',400,body={'name':' '})
        self.check('POST','/api/households',400,body={'name':'x'*81})
        data=self.check('POST','/api/households',201,body={'name':'x'*80})
        self.assertEqual(80,len(data['name']))
    def test_BBA05_nonmember_cannot_read_or_write(self):
        home=self.home()
        for method,path,body in [('GET',f'/api/households/{home}/expenses',None),
                ('POST',f'/api/households/{home}/expenses',self.expense(home)),
                ('GET',f'/api/households/{home}/balances',None)]:
            with self.subTest(operation=method+' '+path): self.check(method,path,404,role='outsider',body=body)
    def test_BBA06_join_then_repeat(self):
        home=self.home(False);invite=self.check('POST',f'/api/households/{home}/invitations',201)
        first=self.check('POST','/api/invitations/redeem',role='bob',body={'code':invite['code']})
        again=self.check('POST','/api/invitations/redeem',role='bob',body={'code':invite['code']})
        self.assertEqual('joined',first['status']);self.assertEqual('already_member',again['status'])
        found=next(h for h in self.check('GET','/api/households',role='bob') if h['id']==home)
        self.assertEqual(2,len(found['members']));self.observations.append('two members; joined then already_member')
    def test_BBA07_invalid_and_unknown_invitation_codes(self):
        self.check('POST','/api/invitations/redeem',400,body={'code':'bad'})
        self.check('POST','/api/invitations/redeem',404,body={'code':'A'*43})
    def test_BBA08_concurrent_join_does_not_duplicate_members(self):
        home=self.home(False);invite=self.check('POST',f'/api/households/{home}/invitations',201)
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            outcomes=list(executor.map(lambda _:call('POST','/api/invitations/redeem','bob',{'code':invite['code']}),range(4)))
        self.assertTrue(all(status==200 for status,_ in outcomes));self.assertEqual(1,sum(r['status']=='joined' for _,r in outcomes))
        self.observations.append('4 concurrent HTTP 200; exactly 1 joined and 3 already_member')
    def test_BBA09_equal_split_remainder_is_preserved(self):
        home=self.home();result=self.create(home)
        self.assertEqual({IDENTITIES['alice']['uid']:51,IDENTITIES['bob']['uid']:50},result['sharesCents'])
        self.observations.append('101 cents allocated as 51 + 50')
    def test_BBA10_custom_shares_are_preserved(self):
        home=self.home();allocations={IDENTITIES['alice']['uid']:1,IDENTITIES['bob']['uid']:100}
        result=self.create(home,splitType='CUSTOM',customAllocations=allocations)
        self.assertEqual(allocations,result['sharesCents']);self.observations.append('custom shares 1 + 100 = 101')
    def test_BBA11_custom_under_and_over_total_are_rejected(self):
        home=self.home()
        for amount in [99,101]:
            self.check('POST',f'/api/households/{home}/expenses',400,body=self.expense(home,amountCents=100,splitType='CUSTOM',
                customAllocations={IDENTITIES['alice']['uid']:0,IDENTITIES['bob']['uid']:amount}))
        self.assertEqual([],self.check('GET',f'/api/households/{home}/expenses')['items'])
    def test_BBA12_zero_negative_and_fractional_amounts(self):
        home=self.home()
        for amount in [0,-1,1.5]:
            with self.subTest(amount=amount):
                self.check('POST',f'/api/households/{home}/expenses',400,body=self.expense(home,amountCents=amount))
        self.assertEqual([],self.check('GET',f'/api/households/{home}/expenses')['items'])
    def test_BBA13_empty_or_foreign_participants(self):
        home=self.home()
        for participants in [[],[IDENTITIES['outsider']['uid']]]:
            with self.subTest(participants=participants):
                self.check('POST',f'/api/households/{home}/expenses',400,body=self.expense(home,participantIds=participants))
    def test_BBA14_invalid_date_category_currency(self):
        home=self.home()
        for changes in [{'expenseDate':'2026-02-30'},{'category':'UNKNOWN'},{'currency':'usd'}]:
            with self.subTest(changes=changes):
                self.check('POST',f'/api/households/{home}/expenses',400,body=self.expense(home,**changes))
    def test_BBA15_expense_detail_round_trip(self):
        home=self.home();created=self.create(home,description='Round-trip receipt')
        detail=self.check('GET',f'/api/households/{home}/expenses/{created["id"]}')
        self.assertEqual(created,detail);self.observations.append('all public expense fields match the create response')
    def test_BBA16_cross_household_resource_id_is_rejected(self):
        first=self.home();second=self.home();expense=self.create(first)
        self.check('GET',f'/api/households/{second}/expenses/{expense["id"]}',404)
    def test_BBA17_history_pagination_has_no_gaps_or_duplicates(self):
        home=self.home();ids=set()
        for date in ['2026-11-18','2026-11-19','2026-11-20']: ids.add(self.create(home,expenseDate=date)['id'])
        page=self.check('GET',f'/api/households/{home}/expenses?limit=2')
        self.assertEqual(['2026-11-20','2026-11-19'],[e['expenseDate'] for e in page['items']])
        next_page=self.check('GET',f'/api/households/{home}/expenses?limit=2&cursor='+urllib.parse.quote(page['nextCursor']))
        actual=[e['id'] for e in page['items']+next_page['items']]
        self.assertEqual(ids,set(actual));self.assertEqual(3,len(actual));self.assertIsNone(next_page['nextCursor'])
        self.observations.append('3 unique expenses across pages of 2 + 1 in descending date order')
    def test_BBA18_invalid_history_limits(self):
        home=self.home()
        for limit in [0,51]: self.check('GET',f'/api/households/{home}/expenses?limit={limit}',400)
    def test_BBA19_balances_exclude_self_share_and_net_opposing_debts(self):
        home=self.home();self.create(home)
        result=self.check('GET',f'/api/households/{home}/balances')
        self.assertEqual([{'debtorId':IDENTITIES['bob']['uid'],'creditorId':IDENTITIES['alice']['uid'],'currency':'USD','amountCents':50}],result['debts'])
        self.create(home,amountCents=40,payerId=IDENTITIES['bob']['uid'])
        result=self.check('GET',f'/api/households/{home}/balances')
        self.assertEqual(30,result['debts'][0]['amountCents']);self.assertEqual(1,len(result['debts']))
        self.assertEqual(result['totals'][0]['totalOwingCents'],result['totals'][0]['totalOwedCents'])
        self.observations.append('Bob owes Alice 50, then 30 after a reciprocal 20-cent obligation; debit=credit')
    def test_BBA20_balances_keep_currencies_separate(self):
        home=self.home();self.create(home,amountCents=100,currency='USD');self.create(home,amountCents=200,currency='EUR')
        result=self.check('GET',f'/api/households/{home}/balances')
        self.assertEqual({'USD':50,'EUR':100},{d['currency']:d['amountCents'] for d in result['debts']})
        self.observations.append('USD 50 and EUR 100 remain separate debts')

class RecordingResult(unittest.TextTestResult):
    records=[]
    def startTest(self,test):
        self.started=time.monotonic();self.case_status='passed';self.case_errors=[];super().startTest(test)
    def record(self,test,status,error=None):
        self.records.append({'id':test._testMethodName.split('_')[1],'status':status,'duration_seconds':round(time.monotonic()-self.started,4),
            'observed':getattr(test,'observations',[]),'error':error})
    def addFailure(self,test,error):
        self.case_status='failed';self.case_errors.append(self._exc_info_to_string(error,test));super().addFailure(test,error)
    def addError(self,test,error):
        self.case_status='error';self.case_errors.append(self._exc_info_to_string(error,test));super().addError(test,error)
    def addSubTest(self,test,subtest,error):
        if error is not None:
            self.case_status='failed' if issubclass(error[0],test.failureException) else 'error'
            self.case_errors.append(str(subtest)+': '+self._exc_info_to_string(error,test))
        super().addSubTest(test,subtest,error)
    def stopTest(self,test):
        self.record(test,self.case_status,'\n'.join(self.case_errors) or None);super().stopTest(test)

if __name__=='__main__':
    result=unittest.TextTestRunner(verbosity=2,resultclass=RecordingResult).run(unittest.defaultTestLoader.loadTestsFromTestCase(ApiCases))
    with open(os.environ['BB_API_RESULTS'],'w') as output: json.dump(result.records,output,indent=2)
    raise SystemExit(0 if result.wasSuccessful() else 1)
