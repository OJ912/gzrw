package com.chestnut.cms.search.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 完全绕过认证的过滤器配置
 * 这个过滤器会在SaToken过滤器之前执行，并且对特定的URL路径直接放行，不经过SaToken过滤器
 */
@Configuration
public class NoAuthSearchFilterConfig {

    /**
     * 注册一个高优先级的过滤器，用于处理无需认证的搜索API
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> noAuthSearchFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        
        // 创建一个过滤器，对特定路径直接放行
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                
                // 获取请求路径
                String path = request.getRequestURI();
                
                // 如果是我们的无认证搜索API路径，直接放行
                if (path.startsWith("/dev-api/api/public/v2/") || path.startsWith("/prod-api/api/public/v2/")) {
                    // 设置跨域头
                    response.setHeader("Access-Control-Allow-Origin", "*");
                    response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
                    
                    // 如果是OPTIONS请求，直接返回200
                    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        return;
                    }
                    
                    // 设置一个请求属性，标记这个请求已经被我们的过滤器处理过
                    // 后续的SaToken过滤器可以检查这个属性，如果存在就跳过认证
                    request.setAttribute("NO_AUTH_SEARCH_API", Boolean.TRUE);
                }
                
                // 继续过滤器链
                filterChain.doFilter(request, response);
            }
        };
        
        registration.setFilter(filter);
        registration.addUrlPatterns("/*"); // 拦截所有请求
        registration.setName("noAuthSearchFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // 最高优先级，确保在SaToken过滤器之前执行
        
        return registration;
    }
}
