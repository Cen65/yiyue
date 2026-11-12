package com.yiyue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yiyue.mapper")
public class YiYueApplication {
  public static void main(String[] args) {
    SpringApplication.run(YiYueApplication.class, args);
  }
}
