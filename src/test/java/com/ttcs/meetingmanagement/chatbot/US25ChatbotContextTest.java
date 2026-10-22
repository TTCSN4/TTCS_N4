package com.ttcs.meetingmanagement.chatbot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes=com.ttcs.meetingmanagement.MeetingmanagementApplication.class)
class US25ChatbotContextTest {
    @Autowired US25ChatRoomRepository rooms;
    @Autowired US25ChatConversationService conversation;
    @Test void chatbotUsesDevelopContextWithoutDuplicateBeans(){assertNotNull(conversation);assertNotNull(rooms);}
}
