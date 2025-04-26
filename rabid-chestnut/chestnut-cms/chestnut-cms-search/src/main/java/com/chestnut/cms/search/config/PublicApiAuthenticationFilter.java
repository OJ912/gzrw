/*
 * Copyright 2022-2024 兮玥(190785909@qq.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chestnut.cms.search.config;

import cn.dev33.satoken.filter.SaFilterAuthStrategy;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.Filter;

/**
 * 公共API接口身份验证过滤器配置
 * 解决特定API路径无需认证的访问需求
 */
@Configuration
public class PublicApiAuthenticationFilter {

    /**
     * 注册Sa-Token全局过滤器，并配置放行规则
     */
    @Bean
    public FilterRegistrationBean<Filter> publicApiAuthFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        
        // 创建一个自定义过滤器，专门用于处理公共API路径
        // 这个过滤器需要在SaTokenFilter之前运行
        registration.setFilter(new SaServletFilter()
            .addInclude("/dev-api/api/public/**", "/prod-api/api/public/**") // 同时支持两个路径
            .setAuth(obj -> {}) // 空实现，不进行任何认证
            .setBeforeAuth(obj -> {
                // 在此拦截器最开始，直接放行所有公共API路径的请求
                SaRouter.match("/dev-api/api/public/**", "/prod-api/api/public/**")
                        .stop();
                
                // 其它请求，继续走正常流程
            })
        );
        
        // 设置高优先级，确保此过滤器在SaToken主过滤器之前执行
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/dev-api/api/public/**", "/prod-api/api/public/**");
        registration.setName("publicApiAuthenticationFilter");
        
        return registration;
    }
}
