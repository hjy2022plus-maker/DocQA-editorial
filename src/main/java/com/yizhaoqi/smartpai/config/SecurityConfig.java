package com.yizhaoqi.smartpai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 配置Spring Security的类
 * 该类定义了应用的安全配置，包括请求的授权规则、CSRF保护的配置以及会话管理策略
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 日志记录器，用于记录安全配置的相关信息
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    /**
     * 配置SecurityFilterChain bean的方法
     * 该方法主要用于配置应用的安全规则，包括哪些请求需要授权、CSRF保护的启用或禁用、会话管理策略等
     *
     * @param http HttpSecurity对象，用于配置应用的安全规则
     * @return SecurityFilterChain对象，代表配置好的安全过滤链
     * @throws Exception 如果配置过程中发生错误，会抛出异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        try {
            // 禁用CSRF保护
            http.csrf(csrf -> csrf.disable())
                    // 配置请求的授权规则
                    .authorizeHttpRequests(authorize -> authorize
                            // 允许静态资源访问
                            .requestMatchers("/", "/test.html", "/static/test.html", "/static/**", "/*.js", "/*.css", "/*.ico").permitAll()
                            // 允许登录注册接口
                            .requestMatchers("/api/v1/users/register", "/api/v1/users/login", "/api/v1/auth/refreshToken").permitAll()
                            // 轻量知识库：基础 JWT 登录 + 公开/私有文档权限
                            .requestMatchers("/api/v1/lightweight/**").hasAnyRole("USER", "ADMIN")
                            // 管理员接口仍保留最小角色保护
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            // 其他请求需要认证
                            .anyRequest().authenticated())
                    // 配置会话管理策略
                    // 设置会话创建策略为STATELESS，表示不会创建会话，通常用于无状态的API应用
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    // 添加JWT认证过滤器
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

            // 记录安全配置加载成功的信息
            logger.info("Security configuration loaded successfully.");
            // 返回配置好的安全过滤链
            return http.build();
        } catch (Exception e) {
            // 记录配置安全过滤链失败的错误信息
            logger.error("Failed to configure security filter chain", e);
            // 抛出异常，以便外部处理
            throw e;
        }
    }
}
