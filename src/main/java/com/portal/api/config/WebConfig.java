package com.portal.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RootUriTemplateHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UriTemplateHandler;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${games-port}")
    private String GAMES_PORT;

    @Value("${games-service}")
    private String GAMES_SERVICE;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
        	.addMapping("/**")
        	.allowedMethods("HEAD", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    }

    @Bean(name = "gameApiRestClient")
    public RestTemplate gameApiRestClient(RestTemplateBuilder builder) {
        UriTemplateHandler uriTemplateHandler = new RootUriTemplateHandler("http://" + GAMES_SERVICE + ":" + GAMES_PORT);

        return  builder
                .uriTemplateHandler(uriTemplateHandler)
                .build();
    }

}