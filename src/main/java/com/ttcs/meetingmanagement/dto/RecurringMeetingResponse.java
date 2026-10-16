<<<<<<< HEAD

package com.ttcs.meetingmanagement.dto;

import java.util.List;

public record RecurringMeetingResponse(
        String recurrenceSeriesId,
        String recurrenceRule,
        int createdCount,
        List<String> meetingIds
) {
}
=======
package com.ttcs.meetingmanagement.dto;
import java.util.List;
public record RecurringMeetingResponse(Long seriesId,String rule,int occurrences,List<Long> meetingIds){}
>>>>>>> parent of 64bacae (chore(US03): remove backend files and preserve directories)
