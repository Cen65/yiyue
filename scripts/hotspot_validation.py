"""100个独立会话同时争抢最后一个号源；409属于预期业务拒绝。"""
from verification_client import *
import concurrent.futures,threading,time,uuid
a=Client('admin');p=Client('patient');schedule,slot=fixture(a,'最后一号'+uuid.uuid4().hex[:8])
profiles=[x['id'] for x in p.call('/patient/profiles') if x['authorized']][:100]
assert len(profiles)==100
clients=[Client('patient') for _ in profiles];barrier=threading.Barrier(101)
def compete(i):
 barrier.wait();start=time.perf_counter()
 try:
  result=clients[i].call('/patient/appointments',{'slotId':slot['id'],'patientId':profiles[i],'requestKey':uuid.uuid4().hex})
  return 200,(time.perf_counter()-start)*1000,result['id']
 except urllib.error.HTTPError as ex:return ex.code,(time.perf_counter()-start)*1000,None
with concurrent.futures.ThreadPoolExecutor(max_workers=100) as pool:
 futures=[pool.submit(compete,i) for i in range(100)];barrier.wait();results=[f.result() for f in futures]
codes={str(c):sum(1 for r in results if r[0]==c) for c in set(r[0] for r in results)}
assert codes=={'200':1,'409':99},codes
assert sql(f"SELECT reserved FROM appointment_slot WHERE id={int(slot['id'])}")=='1'
for code,_,id in results:
 if id:p.call(f'/patient/appointments/{id}/cancel',{})
lat=sorted(r[1] for r in results)
report=json.loads((ROOT/'.local/load-result.json').read_text(encoding='utf-8'))
report['last_slot_100_sessions']={'results':codes,'p95_ms':round(lat[95],2),'capacity_consistent':True}
(ROOT/'.local/load-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report['last_slot_100_sessions']))
