package com.yiyue.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yiyue.auth.SessionUser;
import com.yiyue.common.BusinessException;
import com.yiyue.mapper.PatientProfileMapper;
import com.yiyue.model.entity.PatientProfileDO;
import com.yiyue.model.req.PatientProfileReq;
import com.yiyue.service.PatientProfileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientProfileServiceImpl implements PatientProfileService {
  private final PatientProfileMapper patients;
  private final com.yiyue.mapper.AccountMapper accounts;

  public List<PatientProfileDO> list(SessionUser user) {
    return patients.selectList(
        Wrappers.<PatientProfileDO>lambdaQuery()
            .eq(PatientProfileDO::getTenantId, user.tenantId())
            .eq(PatientProfileDO::getOwnerId, user.id())
            .orderByAsc(PatientProfileDO::getId));
  }

  @Transactional
  public PatientProfileDO save(SessionUser user, Long id, PatientProfileReq req) {
    var p = id == null ? new PatientProfileDO() : requireOwned(user, id);
    boolean self = "SELF".equals(req.relationship());
    if (id != null && self != Boolean.TRUE.equals(p.getSelfProfile()))
      throw BusinessException.conflict("本人档案不能转换为其他关系，请另建档案");
    if (self
        && patients.exists(
            Wrappers.<PatientProfileDO>lambdaQuery()
                .eq(PatientProfileDO::getTenantId, user.tenantId())
                .eq(PatientProfileDO::getOwnerId, user.id())
                .eq(PatientProfileDO::getSelfProfile, true)
                .ne(id != null, PatientProfileDO::getId, id)))
      throw BusinessException.conflict("本人档案已存在");
    p.setTenantId(user.tenantId());
    p.setOwnerId(user.id());
    p.setName(req.name().strip());
    p.setRelationship(req.relationship());
    p.setAuthorized(req.consent());
    p.setSelfProfile(self);
    if (id == null) patients.insert(p);
    else patients.updateById(p);
    return p;
  }

  @Transactional
  public PatientProfileDO requireOwned(SessionUser user, Long id) {
    if (!accounts.exists(
        Wrappers.<com.yiyue.model.entity.AccountDO>lambdaQuery()
            .eq(com.yiyue.model.entity.AccountDO::getId, user.id())
            .eq(com.yiyue.model.entity.AccountDO::getTenantId, user.tenantId())
            .eq(com.yiyue.model.entity.AccountDO::getEnabled, true)))
      throw BusinessException.missing();
    if (id == null) {
      var self =
          patients.selectOne(
              Wrappers.<PatientProfileDO>lambdaQuery()
                  .eq(PatientProfileDO::getTenantId, user.tenantId())
                  .eq(PatientProfileDO::getOwnerId, user.id())
                  .eq(PatientProfileDO::getSelfProfile, true));
      if (self != null) return requireOwned(user, self.getId());
      return save(user, null, new PatientProfileReq(user.name(), "SELF", true));
    }
    patients.lock(user.tenantId(), user.id(), id);
    var p =
        patients.selectOne(
            Wrappers.<PatientProfileDO>lambdaQuery()
                .eq(PatientProfileDO::getTenantId, user.tenantId())
                .eq(PatientProfileDO::getOwnerId, user.id())
                .eq(PatientProfileDO::getId, id)
                .eq(PatientProfileDO::getAuthorized, true));
    if (p == null) throw BusinessException.missing();
    return p;
  }

  @Transactional
  public void revoke(SessionUser user, long id) {
    var p = requireOwned(user, id);
    if (Boolean.TRUE.equals(p.getSelfProfile())) throw BusinessException.conflict("本人档案不能撤销");
    patients.update(
        Wrappers.<PatientProfileDO>lambdaUpdate()
            .eq(PatientProfileDO::getTenantId, user.tenantId())
            .eq(PatientProfileDO::getOwnerId, user.id())
            .eq(PatientProfileDO::getId, id)
            .set(PatientProfileDO::getAuthorized, false));
  }
}
