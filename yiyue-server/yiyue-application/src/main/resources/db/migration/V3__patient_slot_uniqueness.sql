-- 防重维度改为就诊人，允许同一账号为不同已授权就诊人预约。
DROP INDEX uk_active_appointment;
CREATE UNIQUE INDEX uk_active_patient_appointment ON appointment(tenant_id,patient_id,slot_id) WHERE status <> 'CANCELLED';
