"""只针对本地虚构演示机构运行，会创建并取消预约。需要 Python 3。"""
from pathlib import Path
import http.cookiejar
import json
import os
import urllib.request
import urllib.error
import uuid
from datetime import datetime, timezone, timedelta

root = Path(__file__).resolve().parents[1]
settings = dict(line.split("=", 1) for line in (root / ".env").read_text().splitlines() if "=" in line and not line.startswith("#"))
base = os.environ.get("SMOKE_BASE_URL", "http://127.0.0.1:18080")


class Client:
    def __init__(self, tenant, role):
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None
        self.csrf = self.call("/auth/csrf")
        self.call("/auth/login", {"tenantCode": tenant, "username": role, "password": settings[f"DEMO_{role.upper()}_PASSWORD"]})
        self.csrf = self.call("/auth/csrf")

    def call(self, path, body=None):
        headers = {"Content-Type": "application/json"}
        if body is not None and self.csrf:
            headers[self.csrf["headerName"]] = self.csrf["token"]
        request = urllib.request.Request(base + "/api" + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        # 使用真实 Cookie 和 CSRF 链路，包含 Redis 会话读写。
        with self.http.open(request, timeout=10) as response:
            data = response.read()
            return json.loads(data) if data else None


patient = Client("hospital", "patient")
schedules = patient.call("/schedules")
# 跨天运行时跳过已开始的演示时段，避免把业务拒绝误认为接口回归。
now = datetime.now(timezone(timedelta(hours=8))).replace(tzinfo=None)
slot = next((s for schedule in schedules
             for s in patient.call(f"/slots?scheduleId={schedule['id']}")
             if s["reserved"] < s["capacity"]
             and datetime.fromisoformat(s["startAt"]) > now), None)
if slot is None:
    raise SystemExit("未找到未来且有余量的演示号源，请先由管理员发布排班。")
key = str(uuid.uuid4())
created = patient.call("/patient/appointments", {"slotId": slot["id"], "requestKey": key})
try:
    retry = patient.call("/patient/appointments", {"slotId": slot["id"], "requestKey": key})
    assert retry["id"] == created["id"]
    page = patient.call("/patient/appointments?page=1")
    row = next(a for a in page["records"] if a["id"] == created["id"])
    assert row["doctorName"] and row["campusName"] and row["startAt"]
    try:
        patient.call("/admin/appointments")
        raise AssertionError("患者不应获得管理权限")
    except urllib.error.HTTPError as error:
        assert error.code == 403
    other = Client("clinic", "patient")
    assert other.call(f"/slots?scheduleId={schedules[0]['id']}") == []
    admin = Client("hospital", "admin")
    assert any(a["id"] == created["id"] for a in admin.call("/admin/appointments")["records"])
finally:
    # 冒烟测试清理自身创建的预约，释放占用，不删除审计记录。
    patient.call(f"/patient/appointments/{created['id']}/cancel", {})
    patient.call(f"/patient/appointments/{created['id']}/cancel", {})
patient.call("/auth/logout", {})
try:
    patient.call("/auth/me")
    raise AssertionError("退出后应失去会话")
except urllib.error.HTTPError as error:
    assert error.code == 401
print("PASS: Redis会话、CSRF登录、预约幂等、关联分页、角色权限、跨机构隔离、取消重试、退出会话")
