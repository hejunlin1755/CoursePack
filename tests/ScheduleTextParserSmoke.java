package com.kechengbao.app;

public final class ScheduleTextParserSmoke {
    public static void main(String[] args) {
        assertResult("weekday lines", "星期一：语文，数学，英语\n星期二：历史，地理，体育", 6, 0, 1, "语文", "体育");
        assertResult("weekday blocks", "星期一\n第1节 语文\n第2节 数学\n星期三\n1 英语\n2 美术", 4, 0, 2, "语文", "美术");
        assertResult("copied grid", "节次 星期一 星期二 星期三\n1 语文 数学 英语\n2 历史 地理 体育", 6, 0, 2, "语文", "体育");
        ScheduleTextParser.Result emptySlots = ScheduleTextParser.parse("周一：语文，空，数学，-，英语");
        if (emptySlots.lessons().size() != 3 || emptySlots.lessons().get(1).period != 3 || emptySlots.lessons().get(2).period != 5) throw new AssertionError("empty slot preservation");
        if (!ScheduleTextParser.parse("random text without weekdays").isEmpty()) throw new AssertionError("invalid text should stay empty");
        System.out.println("ScheduleTextParser smoke tests passed");
    }

    private static void assertResult(String name, String source, int size, int firstDay, int lastDay, String firstSubject, String lastSubject) {
        ScheduleTextParser.Result result = ScheduleTextParser.parse(source);
        if (result.lessons().size() != size) throw new AssertionError(name + " size");
        ScheduleTextParser.Lesson first = result.lessons().get(0);
        ScheduleTextParser.Lesson last = result.lessons().get(result.lessons().size() - 1);
        if (first.day != firstDay || last.day != lastDay || !first.subject.equals(firstSubject) || !last.subject.equals(lastSubject)) throw new AssertionError(name + " content");
    }
}
