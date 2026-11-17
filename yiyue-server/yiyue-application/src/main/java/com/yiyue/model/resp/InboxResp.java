package com.yiyue.model.resp;

import com.yiyue.model.entity.InboxMessageDO;

public record InboxResp(
    Long id, String message, java.time.LocalDateTime readAt, java.time.LocalDateTime createdAt) {
  public static InboxResp from(InboxMessageDO e) {
    return new InboxResp(e.getId(), e.getMessage(), e.getReadAt(), e.getCreatedAt());
  }
}
