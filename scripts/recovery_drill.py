"""强杀本地项目Java进程；仅适用于默认Start-Local端口和虚构演示库。"""
from verification_client import *
import concurrent.futures,time,uuid,http.client
def control(*args):
 subprocess.run(['pwsh','-NoProfile','-File',str(ROOT/'scripts/Drill-Backend.ps1'),*args],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,text=True)
print('restarting worker-disabled backend',flush=True)
control('-DisableWorker')
print('preparing fixture',flush=True)
a=Client('admin');p=Client('patient');schedule,slot=fixture(a,'强杀恢复'+uuid.uuid4().hex[:8])
profile=p.call('/patient/profiles',{'name':'虚构演练家属','relationship':'OTHER','consent':True})
book=p.call('/patient/appointments',{'slotId':slot['id'],'requestKey':uuid.uuid4().hex})
entry=p.call('/patient/waitlist',{'slotId':slot['id'],'patientId':profile['id']})
p.call('/patient/appointments/'+str(book['id'])+'/cancel',{})
id=int(entry['id']);tenant=int(p.call('/auth/me')['tenantId'])
assert sql(f"SELECT count(*) FROM notification_event WHERE event_key='wait-{id}-offered' AND NOT delivered")=='1'
# 持有机构行锁；确认真实HTTP事务进入锁等待后再强杀Java。
env=dict(os.environ,PGPASSWORD=SETTINGS['DB_PASSWORD'])
locker=subprocess.Popen([PG,'-h','127.0.0.1','-p','55433','-U','yiyue','-d','yiyue','-At','-c',f"BEGIN; SELECT id FROM tenant WHERE id={tenant} FOR UPDATE; SELECT pg_sleep(30); ROLLBACK;"],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
try:
 deadline=time.monotonic()+10
 while time.monotonic()<deadline:
  if int(sql("SELECT count(*) FROM pg_stat_activity WHERE query LIKE '%pg_sleep(30)%' AND state='active' AND pid<>pg_backend_pid()")):break
  time.sleep(.2)
 else:raise AssertionError('未建立演练锁')
 with concurrent.futures.ThreadPoolExecutor() as pool:
  future=pool.submit(p.call,f'/patient/waitlist/{id}/accept',{'consent':True})
  deadline=time.monotonic()+10
  while time.monotonic()<deadline:
   if int(sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%tenant%FOR UPDATE%'")):break
   time.sleep(.2)
  else:raise AssertionError('未观测到确认事务锁等待')
  control('-KillOnly')
  try:future.result(timeout=10);raise AssertionError('强杀期间请求不应成功')
  except (urllib.error.URLError,ConnectionError,TimeoutError,http.client.RemoteDisconnected):pass
finally:
 locker.terminate();locker.communicate(timeout=10)
assert sql(f"SELECT status FROM waitlist_entry WHERE id={id}")=='OFFERED'
assert sql(f"SELECT reserved FROM appointment_slot WHERE id={int(slot['id'])}")=='1'
assert sql(f"SELECT count(*) FROM appointment WHERE request_key='wait-{id}'")=='0'
control()
assert p.call('/auth/me')['tenantId']==tenant
deadline=time.monotonic()+25
while time.monotonic()<deadline:
 if sql(f"SELECT delivered FROM notification_event WHERE event_key='wait-{id}-offered'")=='t':break
 time.sleep(1)
else:raise AssertionError('通知未恢复投递')
accepted=p.call(f'/patient/waitlist/{id}/accept',{'consent':True})
assert p.call(f'/patient/waitlist/{id}/accept',{'consent':True})['id']==accepted['id']
assert sql(f"SELECT reserved FROM appointment_slot WHERE id={int(slot['id'])}")=='1'
assert sql(f"SELECT count(*) FROM inbox_message m JOIN notification_event e ON e.id=m.event_id WHERE e.event_key='wait-{id}-offered'")=='1'
p.call(f"/patient/appointments/{accepted['id']}/cancel",{})
report={'java_force_kill':True,'observed_lock_wait':True,'uncommitted_confirmation_rolled_back':True,'committed_hold_survived':True,'redis_session_survived':True,'outbox_recovered_once':True,'confirmation_retry_idempotent':True,'capacity_consistent':True,'scope':'Java进程；数据库与Redis未强杀'}
(ROOT/'.local/recovery-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
