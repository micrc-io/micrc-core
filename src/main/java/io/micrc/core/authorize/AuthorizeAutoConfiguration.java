package io.micrc.core.authorize;

import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.spring.LifecycleBeanPostProcessor;
import org.apache.shiro.spring.security.interceptor.AuthorizationAttributeSourceAdvisor;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

@Configuration
public class AuthorizeAutoConfiguration {

    @Bean("jwtFilter")
    public JwtFilter jwtFilter(){
        return new JwtFilter();
    }

    @Bean("jwtCredentialsMatcher")
    public JwtCredentialsMatcher jwtCredentialsMatcher(){
        return new JwtCredentialsMatcher();
    }

    /**
     * 自定义realm
     *
     * @param credentialMatcher credentialMatcher
     * @return                  myRealm
     */
    @Bean("myRealm")
    public MyRealm myRealm(@Qualifier("jwtCredentialsMatcher") HashedCredentialsMatcher credentialMatcher) {
        MyRealm myRealm = new MyRealm();
        myRealm.setCredentialsMatcher(credentialMatcher);
        return myRealm;
    }

    @Bean("securityManager")
    public DefaultWebSecurityManager defaultWebSecurityManager(@Qualifier("myRealm") MyRealm myRealm) {
        DefaultWebSecurityManager manager = new DefaultWebSecurityManager();
        manager.setRealm(myRealm);
        /*
         * 关闭shiro自带的session，详情见文档
         */
        DefaultSubjectDAO subjectDAO = new DefaultSubjectDAO();
        DefaultSessionStorageEvaluator defaultSessionStorageEvaluator = new DefaultSessionStorageEvaluator();
        defaultSessionStorageEvaluator.setSessionStorageEnabled(false);
        subjectDAO.setSessionStorageEvaluator(defaultSessionStorageEvaluator);
        manager.setSubjectDAO(subjectDAO);
        return manager;
    }

    @Bean("shiroFilterFactoryBean")
    public ShiroFilterFactoryBean shiroFilterFactoryBean(@Qualifier("securityManager") DefaultWebSecurityManager securityManager) {
        ShiroFilterFactoryBean filter = new ShiroFilterFactoryBean();
        filter.setSecurityManager(securityManager);
        //设置自定义过滤器
        Map<String, Filter> filterMap = new LinkedHashMap<>();
        filterMap.put("jwt", new JwtFilter());
        filter.setFilters(filterMap);
        //设置匹配路径
        LinkedHashMap<String, String> filterChainDefinitionMap = new LinkedHashMap<>();
        Properties properties = loadMicrcProperties();
        Optional<String> publicUri = Optional.ofNullable(properties.getProperty("micrc.api.public.uris"));
        // swagger地址
        filterChainDefinitionMap.put("/swagger-ui/**", "anon");
        filterChainDefinitionMap.put("/v3/api-docs/swagger-config", "anon");
        filterChainDefinitionMap.put("/api/apidoc/**", "anon");
        // springboot fall-back error
        filterChainDefinitionMap.put("/error", "anon");
        // public uri
        Arrays.stream(publicUri.orElse("").split(","))
            .filter(StringUtils::hasText)
            .forEach(it -> filterChainDefinitionMap.put(it, "anon"));

        filterChainDefinitionMap.put("/**", "jwt");
        filter.setFilterChainDefinitionMap(filterChainDefinitionMap);
        //返回
        return filter;
    }

    private Properties loadMicrcProperties() {
        Resource resource = new ClassPathResource("micrc.properties");
        Properties properties = new Properties();
        try {
            properties.load(resource.getInputStream());
        } catch (IOException e) {
            throw new IllegalStateException("Unable loading properties from 'micrc.properties'", e);
        }
        return properties;
    }

    @Bean
    public FilterRegistrationBean<Filter> filterRegistrationBean(DefaultWebSecurityManager securityManager) throws Exception {
        FilterRegistrationBean<Filter> filterRegistrationBean = new FilterRegistrationBean<>();
        filterRegistrationBean.setFilter((Filter) Objects.requireNonNull(this.shiroFilterFactoryBean(securityManager).getObject()));
        filterRegistrationBean.addInitParameter("targetFilterLifecycle", "true");
        //bean注入开启异步方式
        filterRegistrationBean.setAsyncSupported(true);
        filterRegistrationBean.setEnabled(true);
        filterRegistrationBean.setDispatcherTypes(DispatcherType.REQUEST);
        return filterRegistrationBean;
    }
    /**
     * shiro声明周期
     * @return LifecycleBeanPostProcessor
     */
    @Bean
    public LifecycleBeanPostProcessor lifecycleBeanPostProcessor() {
        return new LifecycleBeanPostProcessor();
    }

    // 以下配置开启shiro注解(@RequiresPermissions)

    @Bean
    @DependsOn({"lifecycleBeanPostProcessor"})
    public DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator advisorAutoProxyCreator = new DefaultAdvisorAutoProxyCreator();
        advisorAutoProxyCreator.setProxyTargetClass(true);
        return advisorAutoProxyCreator;
    }

    /**
     * 启用shiro注解
     *
     * @param securityManager   securityManager
     * @return                  AuthorizationAttributeSourceAdvisor
     */
    @Bean
    public AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor(@Qualifier("securityManager") DefaultWebSecurityManager securityManager) {
        AuthorizationAttributeSourceAdvisor authorizationAttributeSourceAdvisor = new AuthorizationAttributeSourceAdvisor();
        authorizationAttributeSourceAdvisor.setSecurityManager(securityManager);
        return authorizationAttributeSourceAdvisor;
    }
}
