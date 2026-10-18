package com.ttcs.meetingmanagement.user.creation;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
@Configuration
class US18UserConfiguration {
    @Bean BCryptPasswordEncoder us18PasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
