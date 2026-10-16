package com.ttcs.meetingmanagement.notification.reminder;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
@Configuration @EnableScheduling
class US16ReminderConfiguration {
    @Bean Clock us16Clock(){return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));}
}
@org.springframework.stereotype.Component
class US16ReminderScheduler {
    private final US16ReminderService service;private final boolean enabled;
    US16ReminderScheduler(US16ReminderService service,@Value("${app.reminder.enabled:true}")boolean enabled){this.service=service;this.enabled=enabled;}
    @Scheduled(fixedDelayString="${app.reminder.scan-ms:60000}",initialDelayString="${app.reminder.initial-delay-ms:60000}")
    void scan(){if(enabled)service.dispatchDue();}
}
