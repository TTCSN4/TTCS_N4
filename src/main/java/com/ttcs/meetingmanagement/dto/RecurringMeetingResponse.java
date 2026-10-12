package com.ttcs.meetingmanagement.dto;
import java.util.List;
public record RecurringMeetingResponse(Long seriesId,String rule,int occurrences,List<Long> meetingIds){}
