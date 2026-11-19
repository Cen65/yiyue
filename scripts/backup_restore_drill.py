"""备份本地虚构库并恢复到全新独立库；不覆盖或删除数据库。"""
from verification_client import *
import uuid,hashlib,datetime
run=uuid.uuid4().hex[:12]
target='yiyue_restore_'+run
env=dict(os.environ,PGPASSWORD=SETTINGS['DB_PASSWORD'])
bin=Path(PG).parent
def query(db,text):
 return subprocess.check_output([str(bin/'psql.exe'),'-h','127.0.0.1','-p','55433','-U','yiyue','-d',db,'-At','-v','ON_ERROR_STOP=1','-c',text],env=env,text=True).strip()
assert query('postgres',f"SELECT count(*) FROM pg_database WHERE datname='{target}'")=='0'
tables=query('yiyue',"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").splitlines()
def counts(db):
 # 表名仅来自数据库元数据，不接收外部路径或SQL片段。
 return {table:int(query(db,'SELECT count(*) FROM "'+table.replace('"','""')+'"')) for table in tables}
def fingerprints(db):
 return {table:query(db,"SELECT md5(COALESCE(string_agg(h,'' ORDER BY h),'')) FROM (SELECT md5(row_to_json(t)::text) h FROM \""+table.replace('"','""')+'" t) hashes') for table in tables}
before=counts('yiyue')
source_hashes=fingerprints('yiyue')
directory=ROOT/'.local/backups';directory.mkdir(exist_ok=True)
dump=directory/(run+'.dump')
subprocess.run([str(bin/'pg_dump.exe'),'-h','127.0.0.1','-p','55433','-U','yiyue','-d','yiyue','-Fc','--file',str(dump)],env=env,check=True,capture_output=True)
subprocess.run([str(bin/'createdb.exe'),'-h','127.0.0.1','-p','55433','-U','yiyue',target],env=env,check=True,capture_output=True)
subprocess.run([str(bin/'pg_restore.exe'),'-h','127.0.0.1','-p','55433','-U','yiyue','-d',target,'--exit-on-error','--no-owner','--no-acl',str(dump)],env=env,check=True,capture_output=True)
restored=counts(target);after=counts('yiyue')
assert before==after==restored,'演练期间数据变动或恢复不完整，不能宣称恢复成功'
assert source_hashes==fingerprints('yiyue')==fingerprints(target),'恢复前后数据指纹不一致'
capacity=query(target,"SELECT count(*) FROM appointment_slot s WHERE reserved<>((SELECT count(*) FROM appointment a WHERE a.slot_id=s.id AND a.status<>'CANCELLED')+(SELECT count(*) FROM waitlist_entry w WHERE w.slot_id=s.id AND w.status='OFFERED')+(SELECT count(*) FROM payment_order p WHERE p.slot_id=s.id AND p.status='PENDING'))")
overlap=query(target,"SELECT count(*) FROM schedule_resource a JOIN schedule_resource b ON a.tenant_id=b.tenant_id AND a.resource_id=b.resource_id AND a.id<b.id WHERE a.status='ACTIVE' AND b.status='ACTIVE' AND a.start_at<b.end_at AND a.end_at>b.start_at")
assert capacity==overlap=='0'
report={'database':target,'table_counts':restored,'source_counts_stable':True,'all_table_data_fingerprints_match':True,'dump_bytes':dump.stat().st_size,'dump_sha256':hashlib.sha256(dump.read_bytes()).hexdigest(),'capacity_violations':int(capacity),'resource_overlap_violations':int(overlap),'restore_exit_success':True,'scope':'本地PostgreSQL全新独立库；未删除任何数据库'}
(ROOT/'.local/backup-result.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
