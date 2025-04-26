package com.chestnut.cms.search.config;

import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet配置类
 * 启用Servlet组件扫描，使@WebServlet注解生效
 */
@Configuration
@ServletComponentScan(basePackages = "com.chestnut.cms.search.servlet")
public class ServletConfig {
    // 无需其他配置，@ServletComponentScan注解会自动扫描并注册带有@WebServlet注解的类
}
