package com.ttcs.meetingmanagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping({"/", "/create-meeting.html"})
    public String createMeetingPage() {
        return "create-meeting";
    }

    @GetMapping("/dashboard.html")
    public String dashboardPage() {
        return "dashboard";
    }

    @GetMapping("/participants.html")
    public String participantsPage() {
        return "participants";
    }

    @GetMapping("/rooms.html")
    public String roomsPage() {
        return "rooms";
    }
}