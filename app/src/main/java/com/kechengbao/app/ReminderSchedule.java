package com.kechengbao.app;

import java.time.ZonedDateTime;

final class ReminderSchedule {
    private ReminderSchedule() {}

    static ZonedDateTime nextTrigger(ZonedDateTime now, int hour, int minute) {
        ZonedDateTime trigger = now.toLocalDate().atTime(hour, minute).atZone(now.getZone());
        return trigger.isAfter(now) ? trigger : trigger.plusDays(1);
    }

    static boolean shouldNotify(boolean configured, int total, int done, int missingSubjects) {
        return configured && (done < total || missingSubjects > 0);
    }
}
