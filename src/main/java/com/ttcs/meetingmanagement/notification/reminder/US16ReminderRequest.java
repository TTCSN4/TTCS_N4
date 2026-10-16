package com.ttcs.meetingmanagement.notification.reminder;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.ttcs.meetingmanagement.model.Meeting;

record US16ReminderRequest(@NotBlank String userId,@Min(1) @Max(10080) int minutesBefore,@NotNull @jakarta.validation.constraints.Pattern(regexp="EMAIL|APP") String channel,@Email String email){
  US16ReminderRequest(String userId,int minutesBefore,String channel){this(userId,minutesBefore,channel,null);}
}
