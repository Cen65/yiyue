"""临时HTTPS生产配置实例；仅信任本次自签证书，不改系统证书库。"""
from verification_client import *
import ssl,secrets,time,http.client,shutil,uuid
work=ROOT/'.local/tls-drill';work.mkdir(exist_ok=True)
run=uuid.uuid4().hex[:10];store=work/(run+'.p12');cert=work/(run+'.pem')
env=dict(os.environ,**SETTINGS)
env['TLS_KEY_STORE_PASSWORD']=secrets.token_urlsafe(24)
keytool=shutil.which('keytool.exe')
subprocess.run([keytool,'-genkeypair','-alias','local-drill','-keyalg','RSA','-keysize','2048','-validity','2','-storetype','PKCS12','-keystore',str(store),'-storepass:env','TLS_KEY_STORE_PASSWORD','-dname','CN=localhost','-ext','SAN=DNS:localhost,IP:127.0.0.1','-noprompt'],env=env,check=True,capture_output=True)
subprocess.run([keytool,'-exportcert','-alias','local-drill','-keystore',str(store),'-storepass:env','TLS_KEY_STORE_PASSWORD','-rfc','-file',str(cert)],env=env,check=True,capture_output=True)
env.update(DB_URL='jdbc:postgresql://127.0.0.1:55433/yiyue',DB_USERNAME='yiyue',REDIS_HOST='127.0.0.1',REDIS_PORT='56379',SERVER_PORT='18443',SPRING_PROFILES_ACTIVE='production',TLS_KEY_STORE=str(store))
version='0.7.0'
jar=ROOT/f'yiyue-server/yiyue-application/target/yiyue-server-{version}.jar'
context=ssl.create_default_context(cafile=str(cert))
with (work/'backend.log').open('w') as out,(work/'backend.err.log').open('w') as err:
 backend=subprocess.Popen([shutil.which('java.exe'),'-jar',str(jar)],cwd=ROOT,env=env,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)
 try:
  deadline=time.monotonic()+55
  while time.monotonic()<deadline:
   if backend.poll() is not None:raise AssertionError('HTTPS实例启动失败，请查看.local/tls-drill日志')
   try:
    with urllib.request.urlopen('https://localhost:18443/actuator/health',context=context,timeout=2) as response:
     if response.status==200:break
   except Exception:time.sleep(1)
  else:raise AssertionError('HTTPS健康检查超时')
  client=urllib.request.build_opener(urllib.request.HTTPSHandler(context=context),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
  with client.open('https://localhost:18443/api/auth/csrf') as response:
   cookie=response.headers.get('Set-Cookie');csrf=json.loads(response.read())
  assert cookie and all(x.lower() in cookie.lower() for x in ['Secure','HttpOnly','SameSite=Lax'])
  statuses=[]
  # 仅使用不存在的虚构身份；转发头伪造不能改变真实连接地址限流。
  for n in range(9):
   request=urllib.request.Request('https://localhost:18443/api/auth/login',data=json.dumps({'tenantCode':'virtual-drill','username':'missing-'+run,'password':secrets.token_urlsafe(18)}).encode(),headers={'Content-Type':'application/json',csrf['headerName']:csrf['token'],'X-Forwarded-For':f'192.0.2.{n+1}'})
   try:
    with client.open(request) as response:statuses.append(response.status)
   except urllib.error.HTTPError as ex:statuses.append(ex.code)
  assert statuses==[401]*8+[429],statuses
  request=urllib.request.Request('https://localhost:18443/api/auth/login',data=json.dumps({'tenantCode':'hospital','username':'patient','password':SETTINGS['DEMO_PATIENT_PASSWORD']}).encode(),headers={'Content-Type':'application/json',csrf['headerName']:csrf['token']})
  with client.open(request) as response:assert response.status==200
  try:
   client.open('https://localhost:18443/api/patient/payment-orders');raise AssertionError('生产配置不应暴露模拟支付API')
  except urllib.error.HTTPError as ex:assert ex.code==404,ex.code
  with client.open('https://localhost:18443/api/auth/capabilities') as response:assert json.loads(response.read())['mockPayments'] is False
  request=urllib.request.Request('https://localhost:18443/api/auth/login',data=json.dumps({'tenantCode':'hospital','username':'admin','password':SETTINGS['DEMO_ADMIN_PASSWORD']}).encode(),headers={'Content-Type':'application/json',csrf['headerName']:csrf['token']})
  with client.open(request) as response:assert response.status==200
  with client.open('https://localhost:18443/api/auth/csrf') as response:csrf=json.loads(response.read())
  from datetime import datetime,timedelta
  day=(datetime.now()+timedelta(days=20)).strftime('%Y-%m-%d')
  request=urllib.request.Request('https://localhost:18443/api/admin/schedules',data=json.dumps({'campusId':1,'doctorId':1,'startAt':day+'T19:00:00','endAt':day+'T20:00:00','slotMinutes':30,'capacity':1,'feeCents':100}).encode(),headers={'Content-Type':'application/json',csrf['headerName']:csrf['token']})
  try:client.open(request);raise AssertionError('生产配置不应允许发布模拟收费排班')
  except urllib.error.HTTPError as ex:assert ex.code==409 and '未启用收费渠道' in json.loads(ex.read())['detail']
  connection=http.client.HTTPSConnection('localhost',18443,context=context);connection.connect();tls=connection.sock.version();connection.close()
  report={'certificate_verified':True,'tls_version':tls,'secure_cookie':True,'http_only_cookie':True,'same_site_lax':True,'login_statuses':statuses,'forwarded_header_cannot_bypass':True,'mock_payment_api_disabled':True,'charged_schedule_rejected':True,'capabilities_consistent':True,'scope':'本地自签证书生产profile；无公网证书或真实渠道验收'}
  (ROOT/'.local/https-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False))
 finally:
  # 只终止本脚本创建并持有句柄的临时实例。
  backend.terminate();backend.wait(timeout=15)
