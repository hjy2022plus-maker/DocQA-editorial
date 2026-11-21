package com.yizhaoqi.smartpai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.yizhaoqi.smartpai.config.EsConfig;
import com.yizhaoqi.smartpai.config.LightweightAsyncConfig;

@SpringBootApplication(scanBasePackages = "com.yizhaoqi.smartpai.lightweight")
@EntityScan("com.yizhaoqi.smartpai.lightweight")
@EnableJpaRepositories("com.yizhaoqi.smartpai.lightweight")
@Import({EsConfig.class, LightweightAsyncConfig.class})
public class SmartPaiApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SmartPaiApplication.class);
        application.setDefaultProperties(java.util.Map.of("spring.config.name", "docqa"));
        application.run(args);
    }

}
