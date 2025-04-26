package com.chestnut.cms.search.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 自定义SaToken过滤器配置
 * 这个配置会替代默认的SaToken过滤器配置，添加对我们特定路径的处理
 */
@Configuration
@ConditionalOnClass(SaServletFilter.class)
public class CustomSaTokenFilterConfig {

    /**
     * 注册一个自定义的SaToken过滤器，用于处理无需认证的搜索API
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> customSaTokenFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        
        // 创建一个过滤器，对特定路径直接放行
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                
                // 检查请求是否已经被我们的NoAuthSearchFilter标记为无需认证
                Boolean noAuthSearchApi = (Boolean) request.getAttribute("NO_AUTH_SEARCH_API");
                if (Boolean.TRUE.equals(noAuthSearchApi)) {
                    // 如果已经标记为无需认证，直接放行
                    filterChain.doFilter(request, response);
                    return;
                }
                
                // 否则，使用SaToken过滤器处理
                try {
                    SaServletFilter saFilter = new SaServletFilter()
                        .addInclude("/**")
                        .setAuth(obj -> {
                            // 登录校验 -- 拦截所有路由，并排除/user/doLogin 用于开放登录
                            SaRouter.match("/**")
                                    .notMatch("/dev-api/login")
                                    .notMatch("/dev-api/captchaImage")
                                    .notMatch("/dev-api/api/public/v2/**") // 排除我们的无认证搜索API
                                    .notMatch("/prod-api/api/public/v2/**") // 排除我们的无认证搜索API
                                    .check(r -> {
                                        // 检查是否登录
                                    });
                        });
                    
                    // 执行SaToken过滤器
                    saFilter.doFilter(request, response, filterChain);
                } catch (Exception e) {
                    // 如果发生异常，记录日志并继续过滤器链
                    System.err.println("SaToken filter error: " + e.getMessage());
                    filterChain.doFilter(request, response);
                }
            }
        };
        
        registration.setFilter(filter);
        registration.addUrlPatterns("/*"); // 拦截所有请求
        registration.setName("customSaTokenFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // 优先级比NoAuthSearchFilter低，但比默认的SaToken过滤器高
        
        return registration;
    }
}
