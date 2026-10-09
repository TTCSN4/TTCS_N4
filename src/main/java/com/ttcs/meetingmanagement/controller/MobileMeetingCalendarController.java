
package com.ttcs.meetingmanagement.controller;

import com.ttcs.meetingmanagement.dto.MobileCalendarPageResponse;
import com.ttcs.meetingmanagement.dto.MobileMeetingDetailResponse;

import com.ttcs.meetingmanagement.security.CurrentUserIdResolver;
import com.ttcs.meetingmanagement.service.MobileMeetingCalendarService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/mobile/calendar")
public class MobileMeetingCalendarController {

    private final MobileMeetingCalendarService service;
    private final CurrentUserIdResolver userResolver;

    public MobileMeetingCalendarController(
            MobileMeetingCalendarService service,
            CurrentUserIdResolver userResolver
    ) {
        this.service = service;
        this.userResolver = userResolver;
    }

    // GET DANH SACH LICH HOP
    @GetMapping
    public MobileCalendarPageResponse getCalendar(
            @AuthenticationPrincipal Jwt jwt,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size
    ) {

        String userId = userResolver.resolve(jwt);

        return service.getCalendar(
                userId,
                from,
                to,
                page,
                size
        );
    }

    // GET CHI TIET CUOC HOP
    @GetMapping("/{meetingId}")
    public MobileMeetingDetailResponse getDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String meetingId
    ) {

        String userId = userResolver.resolve(jwt);

        return service.getDetail(
                userId,
                meetingId
        );
    }
}
