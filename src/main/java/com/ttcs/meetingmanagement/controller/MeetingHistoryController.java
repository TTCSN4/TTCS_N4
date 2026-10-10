
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.MeetingHistoryResponse;
import com.ttcs.meetingmanagement.history.MeetingHistoryService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/meeting-history")
public class MeetingHistoryController {

    private final MeetingHistoryService historyService;

    public MeetingHistoryController(
            MeetingHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<MeetingHistoryResponse> getHistory(
            @PathVariable String userId) {

        return historyService.getHistory(userId);
    }

    @GetMapping("/{meetingId}")
    public MeetingHistoryResponse getHistoryDetail(
            @PathVariable String userId,
            @PathVariable String meetingId) {

        return historyService.getHistoryDetail(
                userId, meetingId);
    }
}
