package com.ttcs.meetingmanagement.chatbot;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.*;
import com.ttcs.meetingmanagement.model.*;
import com.ttcs.meetingmanagement.room.*;
import com.ttcs.meetingmanagement.repository.*;
@Service class US25GeminiIntentService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(US25GeminiIntentService.class);
    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey,model;
    US25GeminiIntentService(ObjectMapper json,@Value("${gemini.api-key:}")String apiKey,@Value("${gemini.model:gemini-2.5-flash}")String model,@Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")String baseUrl) {
        this.http=RestClient.builder().baseUrl(baseUrl).build();
        this.json=json;
        this.apiKey=apiKey;
        this.model=model;
    }
    boolean enabled() {
        return apiKey!=null&&!apiKey.isBlank();
    }
    Optional<US25AiBookingDraft> analyze(String message) {
        if(!enabled())return Optional.empty();
        try {
            Map<String,Object> nullableString=Map.of("type",List.of("string","null"));
            Map<String,Object> nullableInteger=Map.of("type",List.of("integer","null"));
            Map<String,Object> schema=Map.of("type","object","properties",Map.of("organizerId",nullableString,"title",nullableString,"startTime",nullableString,"endTime",nullableString,"attendees",nullableInteger,"roomId",nullableString,"participantIds",Map.of("type","array","items",Map.of("type","string")),"reply",Map.of("type","string")),"required",List.of("organizerId","title","startTime","endTime","attendees","roomId","participantIds","reply"));
            String instruction="Bạn trích xuất yêu cầu đặt phòng họp bằng tiếng Việt. Hôm nay là "+LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))+". Múi giờ +07:00. Chuyển thời gian sang ISO 8601 có offset. Trường thiếu trả null, participantIds thiếu trả mảng rỗng. roomId là mã kiểu ROOM-A; nếu người dùng muốn hệ thống tự chọn thì null. reply là câu tiếng Việt ngắn giải thích thông tin đã hiểu hoặc cần bổ sung. Không tự bịa ID người dùng. Yêu cầu của người dùng: "+message;
            Map<String,Object> body=Map.of("contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",instruction)))),"generationConfig",Map.of("response_mime_type","application/json","response_schema",schema));
            JsonNode response=http.post().uri("/models/{model}:generateContent",model).header("x-goog-api-key",apiKey).body(body).retrieve().body(JsonNode.class);
            String output=response==null?null:response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            return output==null?Optional.empty():Optional.of(json.readValue(output,US25AiBookingDraft.class));
        }
        catch(Exception ex) {
            log.warn("Không thể gọi Gemini model {}: {}",model,ex.getMessage());
            return Optional.empty();
        }
    }
}
