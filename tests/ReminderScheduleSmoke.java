package com.kechengbao.app;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ReminderScheduleSmoke {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime before = ZonedDateTime.of(2026, 9, 19, 19, 30, 0, 0, zone);
        ZonedDateTime after = ZonedDateTime.of(2026, 9, 19, 20, 30, 0, 0, zone);
        if (!ReminderSchedule.nextTrigger(before, 20, 0).equals(ZonedDateTime.of(2026, 9, 19, 20, 0, 0, 0, zone))) throw new AssertionError("same-day trigger");
        if (!ReminderSchedule.nextTrigger(after, 20, 0).equals(ZonedDateTime.of(2026, 9, 20, 20, 0, 0, 0, zone))) throw new AssertionError("next-day trigger");
        if (ReminderSchedule.shouldNotify(true, 5, 5, 0)) throw new AssertionError("completed bag must stay quiet");
        if (!ReminderSchedule.shouldNotify(true, 5, 4, 0)) throw new AssertionError("unfinished item must notify");
        if (!ReminderSchedule.shouldNotify(true, 0, 0, 1)) throw new AssertionError("missing subject setup must notify");
        if (ReminderSchedule.shouldNotify(false, 5, 0, 0)) throw new AssertionError("unconfigured app must stay quiet");
        System.out.println("ReminderSchedule smoke tests passed");
    }
}
