package com.yiyue.common;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

/** BCrypt输入按字节限制，避免多字节密码被截断或抛出未处理异常。 */
public final class PasswordPolicy {
  private PasswordPolicy() {}

  public static void validate(String password) {
    if (password == null
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BusinessException(HttpStatus.BAD_REQUEST, "密码至少12个字符，UTF-8编码不超过72字节");
  }
}
