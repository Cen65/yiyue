package com.yiyue.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.*;

@Configuration
public class MybatisConfig {
  @Bean
  MybatisPlusInterceptor interceptor() {
    var interceptor = new MybatisPlusInterceptor();
    // 统一分页入口与最大页大小，避免接口被参数放大为全表扫描。
    var pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
    pagination.setMaxLimit(100L);
    interceptor.addInnerInterceptor(pagination);
    return interceptor;
  }
}
