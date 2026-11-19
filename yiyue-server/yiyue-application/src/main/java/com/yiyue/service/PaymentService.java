package com.yiyue.service;

import com.yiyue.auth.SessionUser;
import com.yiyue.model.req.PaymentCreateReq;
import com.yiyue.model.resp.PaymentResp;
import java.util.*;

public interface PaymentService {
  PaymentResp create(SessionUser u, PaymentCreateReq r);

  List<PaymentResp> mine(SessionUser u);

  PaymentResp mockPay(SessionUser u, long id);

  void cancel(SessionUser u, long id);

  void poll();

  void onCancelled(long tenant, long appointmentId);

  void closeSchedule(long tenant, long scheduleId);

  List<Map<String, Object>> reconcile(SessionUser u);
}
