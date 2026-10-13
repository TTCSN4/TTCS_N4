package com.ttcs.meetingmanagement.dto;
import java.util.List;
public record RecurringMeetingResponse(String seriesId,String rule,int occurrences,List<String> meetingIds){}
