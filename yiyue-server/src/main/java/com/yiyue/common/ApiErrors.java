package com.yiyue.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(BusinessException.class)
  ProblemDetail business(BusinessException ex) {
    return ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException ex) {
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("请求参数不正确"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail duplicate(DataIntegrityViolationException ex) {
    // 不向客户端暴露 SQL、表结构或内部异常中的敏感字段。
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "数据冲突，请刷新后重试");
  }
}
