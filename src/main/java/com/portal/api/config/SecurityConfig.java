package com.portal.api.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
         http
         		.cors().and().csrf().disable()
                .authorizeRequests()
                .antMatchers("/portal/actuator/health").permitAll()
                .antMatchers(HttpMethod.POST, "/portal/login").permitAll()
                .antMatchers(HttpMethod.POST, "/portal/signup").permitAll()
                .antMatchers(HttpMethod.POST, "/shopify/orders").permitAll()
                .antMatchers("/portal/test").permitAll()
                //.antMatchers("/portal/cohort/**").permitAll()
                //.antMatchers("/portal/profile/**").permitAll()
                //.antMatchers("/portal/children/**").permitAll()
                .antMatchers("/portal/swagger-ui/**", "/portal/v3/api-docs/**", "/portal/swagger-ui.html", "/portal/swagger-resources/**", "/webjars/**").permitAll()
                .anyRequest().authenticated().and()
                .oauth2ResourceServer().jwt();
    }

}