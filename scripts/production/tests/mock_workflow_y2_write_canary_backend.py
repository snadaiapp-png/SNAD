#!/usr/bin/env python3
import argparse, json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

DEF_ID='11111111-1111-1111-1111-111111111111'
FAMILY_ID='22222222-2222-2222-2222-222222222222'
START_ID='33333333-3333-3333-3333-333333333333'
END_ID='44444444-4444-4444-4444-444444444444'
TRANSITION_ID='55555555-5555-5555-5555-555555555555'
INSTANCE_ID='66666666-6666-6666-6666-666666666666'
TENANT_ID='77777777-7777-7777-7777-777777777777'

class Handler(BaseHTTPRequestHandler):
    state={'published': False, 'steps': []}
    immutability_status=409
    existing_canary=False
    full_list=False

    def log_message(self, *_):
        pass

    def _read_json(self):
        n=int(self.headers.get('Content-Length','0') or 0)
        raw=self.rfile.read(n) if n else b'{}'
        try: return json.loads(raw or b'{}')
        except Exception: return {}

    def _send(self, code, body):
        data=json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type','application/json')
        self.send_header('Content-Length',str(len(data)))
        self.end_headers(); self.wfile.write(data)

    def do_GET(self):
        path=urlparse(self.path).path
        if path == '/actuator/health':
            return self._send(200, {'status':'UP'})
        if path == '/api/v1/workflows/definitions':
            if self.full_list:
                return self._send(200, [
                    {'id':f'{i:08d}-1111-1111-1111-111111111111','code':f'OTHER-{i}'}
                    for i in range(200)
                ])
            if self.existing_canary:
                return self._send(200, [{'id':DEF_ID,'code':'Y2-PROD-CANARY-aaaaaaaaaaaa','publicationState':'PUBLISHED','engineGeneration':'Y2'}])
            return self._send(200, [])
        if path == f'/api/v1/workflows/definitions/{DEF_ID}':
            return self._send(200, {'id':DEF_ID,'definitionFamilyId':FAMILY_ID,'version':1,'versionLock':1 if self.state['published'] else 0,'publicationState':'PUBLISHED' if self.state['published'] else 'DRAFT','engineGeneration':'Y2' if self.state['published'] else 'LEGACY'})
        return self._send(404, {'status':404})

    def do_POST(self):
        path=urlparse(self.path).path
        body=self._read_json()
        if path == '/api/v1/auth/login':
            return self._send(200, {'accessToken':'test-token-abcdefghijklmnopqrstuvwxyz','user':{'tenantId':TENANT_ID}})
        if path == '/api/v1/workflows/definitions':
            if body.get('code') != 'Y2-PROD-CANARY-aaaaaaaaaaaa':
                return self._send(400, {'status':400,'message':'unexpected canary code'})
            self.state={'published':False,'steps':[]}
            return self._send(200, {'id':DEF_ID,'definitionFamilyId':FAMILY_ID,'version':1,'versionLock':0,'publicationState':'DRAFT','engineGeneration':'LEGACY'})
        if path == f'/api/v1/workflows/definitions/{DEF_ID}/steps':
            if self.state['published']:
                return self._send(self.immutability_status, {'status':self.immutability_status,'error':'Conflict'})
            sid=START_ID if body.get('stepType') == 'START' else END_ID
            self.state['steps'].append(sid)
            return self._send(200, {'id':sid,'stepKey':body.get('stepKey'),'stepType':body.get('stepType'),'version':0})
        if path == f'/api/v1/workflows/definitions/{DEF_ID}/transitions':
            if self.state['published']:
                return self._send(self.immutability_status, {'status':self.immutability_status,'error':'Conflict'})
            return self._send(200, {'id':TRANSITION_ID,'fromStepId':START_ID,'toStepId':END_ID,'transitionKey':'finish','outcome':'SUCCESS','priority':10})
        if path == f'/api/v1/workflows/definitions/{DEF_ID}/validate':
            return self._send(200, {'valid':True,'errors':[]})
        if path == f'/api/v1/workflows/definitions/{DEF_ID}/simulate':
            return self._send(200, {'valid':True,'simulated':True,'visitedStepIds':[START_ID,END_ID],'notes':[]})
        if path == f'/api/v1/workflows/definitions/{DEF_ID}/publish':
            if body.get('expectedVersion') != 0:
                return self._send(409, {'status':409,'code':'STALE_VERSION'})
            self.state['published']=True
            return self._send(200, {'id':DEF_ID,'definitionFamilyId':FAMILY_ID,'version':1,'versionLock':1,'publicationState':'PUBLISHED','engineGeneration':'Y2'})
        if path == '/api/v1/workflows/instances':
            return self._send(200, {'id':INSTANCE_ID,'workflowDefinitionId':DEF_ID,'workflowVersion':1,'status':'COMPLETED','currentStepKey':'end','engineGeneration':'Y2','definitionFamilyId':FAMILY_ID,'definitionVersionId':DEF_ID})
        return self._send(404, {'status':404,'path':path})

if __name__ == '__main__':
    p=argparse.ArgumentParser(); p.add_argument('--port',type=int,required=True); p.add_argument('--immutability-status',type=int,default=409); p.add_argument('--existing-canary',action='store_true'); p.add_argument('--full-list',action='store_true')
    a=p.parse_args(); Handler.immutability_status=a.immutability_status; Handler.existing_canary=a.existing_canary; Handler.full_list=a.full_list
    ThreadingHTTPServer(('127.0.0.1',a.port),Handler).serve_forever()
