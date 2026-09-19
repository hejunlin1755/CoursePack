package com.kechengbao.app;

import java.util.ArrayList;
import java.util.List;

public final class ScheduleOcrLayoutSmoke {
    public static void main(String[] args) {
        List<ScheduleOcrLayout.Token> tokens = new ArrayList<>();
        for (int day = 0; day < 5; day++) tokens.add(token("星期" + "一二三四五".substring(day, day + 1), 300 + day * 200, 100));
        for (int period = 1; period <= 3; period++) tokens.add(token("第" + period + "節", 80, 220 + period * 120));
        tokens.add(token("綜合科學", 300, 340));
        tokens.add(token("歷史", 300, 460));
        tokens.add(token("體育", 500, 340));
        tokens.add(token("中文", 700, 340));
        tokens.add(token("英1", 900, 340));
        tokens.add(token("英2", 940, 340));
        tokens.add(token("視覺藝術", 1100, 340));
        String formatted = ScheduleOcrLayout.format(tokens);
        if (!formatted.contains("星期一：綜合科學，歷史，空")) throw new AssertionError(formatted);
        if (!formatted.contains("星期四：英1/英2，空，空")) throw new AssertionError(formatted);
        ScheduleTextParser.Result parsed = ScheduleTextParser.parse(formatted);
        if (parsed.lessons().size() != 6) throw new AssertionError("Expected 6 lessons, got " + parsed.lessons().size());
        System.out.println("ScheduleOcrLayout smoke tests passed");
    }

    private static ScheduleOcrLayout.Token token(String text, int centerX, int centerY) {
        return new ScheduleOcrLayout.Token(text, centerX - 35, centerY - 18, centerX + 35, centerY + 18);
    }
}
