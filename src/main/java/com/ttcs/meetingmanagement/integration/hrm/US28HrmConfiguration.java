package com.ttcs.meetingmanagement.integration.hrm;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling
class US28HrmConfiguration {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Scope("prototype")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(org.springframework.web.client.RestClient.Builder.class)
    org.springframework.web.client.RestClient.Builder us28RestClientBuilder() {
        return org.springframework.web.client.RestClient.builder();
    }
}
