"""本地虚构数据验证共用客户端；不打印凭证。"""
from pathlib import Path
import http.cookiejar,json,urllib.request,urllib.error,os,subprocess,time
ROOT=Path(__file__).resolve().parents[1]
SETTINGS=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
BASE='http://127.0.0.1:18080'
class Client:
 def __init__(self,role):
  self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
  self.csrf=self.call('/auth/csrf')
  deadline=time.monotonic()+125
  while True:
   try:
    self.call('/auth/login',{'tenantCode':'hospital','username':role,'password':SETTINGS['DEMO_'+role.upper()+'_PASSWORD']});break
   except urllib.error.HTTPError as ex:
    # 负载准备遵守真实登录限流，不关闭防护；准备耗时不计入业务操作延迟。
    if ex.code!=429 or time.monotonic()>=deadline:raise
    time.sleep(5)
  self.csrf=self.call('/auth/csrf')
 def call(self,path,body=None):
  headers={'Content-Type':'application/json'}
  if body is not None:headers[self.csrf['headerName']]=self.csrf['token']
  req=urllib.request.Request(BASE+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
  with self.http.open(req,timeout=45) as r:
   data=r.read();return json.loads(data) if data else None
PG=r'D:\software\PostgreSQL\17\bin\psql.exe'
def sql(query):
 # 密码只经进程环境传入；调用方仅使用固定SQL和内部生成数字。
 env=dict(os.environ,PGPASSWORD=SETTINGS['DB_PASSWORD'])
 return subprocess.check_output([PG,'-h','127.0.0.1','-p','55433','-U','yiyue','-d','yiyue','-At','-v','ON_ERROR_STOP=1','-c',query],env=env,text=True).strip()
def fixture(admin,label,capacity=1):
 from datetime import datetime,timedelta
 cat=admin.call('/catalog');campus=cat['campuses'][0]['id'];dep=cat['departments'][0]['id']
 doctor=admin.call('/admin/operations/doctors',{'name':label,'title':'虚构测试医生','specialty':'验证','departmentId':dep,'departmentIds':[dep],'campusIds':[campus],'enabled':True})
 day=(datetime.now()+timedelta(days=20)).strftime('%Y-%m-%d')
 schedule=admin.call('/admin/schedules',{'campusId':campus,'doctorId':doctor['id'],'departmentId':dep,'startAt':day+'T09:00:00','endAt':day+'T10:00:00','slotMinutes':30,'capacity':capacity})
 return schedule,admin.call('/slots?scheduleId='+str(schedule['id']))[0]
