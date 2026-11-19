"""默认本地演示库的有界负载验证；仅虚构数据，结果不等同医院验收。"""
from verification_client import *
import time,uuid,concurrent.futures,statistics,threading
tag=uuid.uuid4().hex[:10]
a=Client('admin');p=Client('patient')
tenant=int(p.call('/auth/me')['tenantId']);user=int(p.call('/auth/me')['id'])
schedule,slot=fixture(a,'压测热点'+tag,100)
doctor=int(schedule['doctorId']);campus=int(schedule['campusId']);dep=int(schedule['departmentId'])
profile=p.call('/patient/profiles',{'name':'虚构压测本人','relationship':'OTHER','consent':True});patient=int(profile['id'])
# 通过固定SQL批量生成虚构历史/未来数据，避免将准备数据时间算入接口耗时。
sql(f"""INSERT INTO clinic_schedule(tenant_id,campus_id,doctor_id,department_id,start_at,end_at,status)
 SELECT {tenant},{campus},{doctor},{dep},CURRENT_DATE+interval '1 day'*n+interval '9 hour',CURRENT_DATE+interval '1 day'*n+interval '10 hour','OPEN' FROM generate_series(31,10030) n;
 INSERT INTO appointment_slot(tenant_id,schedule_id,start_at,end_at,capacity,reserved)
 SELECT {tenant},id,start_at,end_at,100,0 FROM clinic_schedule WHERE tenant_id={tenant} AND doctor_id={doctor} AND id<>{int(schedule['id'])};
 INSERT INTO appointment(tenant_id,user_id,patient_id,slot_id,request_key,status,created_at)
 SELECT {tenant},{user},{patient},{int(slot['id'])},'load-{tag}-'||n,'CANCELLED',CURRENT_TIMESTAMP FROM generate_series(1,100000) n;""")
profiles=[p.call('/patient/profiles',{'name':'虚构并发'+str(i),'relationship':'OTHER','consent':True})['id'] for i in range(100)]
clients=[Client('patient') for _ in range(100)]
report={'scope':'单机闭环HTTP混合负载；真实认证/CSRF/PostgreSQL/Redis，非医院生产验收','seed':{'extra_schedules':10000,'extra_slots':10000,'extra_cancelled_appointments':100000},'stages':[]}
def stage(concurrency,seconds):
 barrier=threading.Barrier(concurrency+1)
 def worker(i):
  c=clients[i];lat=[];codes={};counts={};barrier.wait();end=time.monotonic()+seconds;n=0
  while time.monotonic()<end:
   kind=['schedule_page','appointments','catalog','write_cycle'][n%4];n+=1;t=time.perf_counter()
   try:
    if kind=='schedule_page':c.call('/schedules/page?page=1&size=20')
    elif kind=='appointments':c.call('/patient/appointments?page=1')
    elif kind=='catalog':c.call('/catalog')
    else:
     b=c.call('/patient/appointments',{'slotId':slot['id'],'patientId':profiles[i],'requestKey':uuid.uuid4().hex})
     c.call('/patient/appointments/'+str(b['id'])+'/cancel',{})
    code='ok'
   except urllib.error.HTTPError as e:code=str(e.code)
   except Exception as e:code=type(e).__name__
   lat.append((time.perf_counter()-t)*1000);codes[code]=codes.get(code,0)+1;counts[kind]=counts.get(kind,0)+1
  return lat,codes,counts
 start=time.monotonic()
 with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
  futures=[pool.submit(worker,i) for i in range(concurrency)];barrier.wait();results=[f.result() for f in futures]
 elapsed=time.monotonic()-start;values=sorted(x for lat,_,_ in results for x in lat);codes={};counts={}
 for _,c,k in results:
  for key,v in c.items():codes[key]=codes.get(key,0)+v
  for key,v in k.items():counts[key]=counts.get(key,0)+v
 result={'concurrency':concurrency,'duration_seconds':round(elapsed,2),'operations':len(values),'operations_per_second':round(len(values)/elapsed,2),'p50_ms':round(values[int(len(values)*.50)],2),'p95_ms':round(values[int(len(values)*.95)],2),'p99_ms':round(values[int(len(values)*.99)],2),'results':codes,'operation_mix':counts}
 report['stages'].append(result);print(json.dumps(result),flush=True)
for c in [10,50,100]:stage(c,20)
report['capacity_check']=sql(f"SELECT reserved||':'||(SELECT count(*) FROM appointment WHERE slot_id={int(slot['id'])} AND status='BOOKED') FROM appointment_slot WHERE id={int(slot['id'])}")
payment_holds=f"(SELECT count(*) FROM payment_order po WHERE po.slot_id=s.id AND po.status='PENDING')" if sql("SELECT to_regclass('public.payment_order') IS NOT NULL")=="t" else '0'
report['all_capacity_invariant_violations']=sql(f"SELECT count(*) FROM appointment_slot s WHERE tenant_id={tenant} AND reserved<>((SELECT count(*) FROM appointment a WHERE a.slot_id=s.id AND a.status<>'CANCELLED')+(SELECT count(*) FROM waitlist_entry w WHERE w.slot_id=s.id AND w.status='OFFERED')+{payment_holds})")
assert report['capacity_check']=='0:0',report['capacity_check']
assert report['all_capacity_invariant_violations']=='0',report['all_capacity_invariant_violations']
(ROOT/'.local/load-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: capacity invariants',flush=True)
