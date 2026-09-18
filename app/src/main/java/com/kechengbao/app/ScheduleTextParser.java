package com.kechengbao.app;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScheduleTextParser {
    private static final String[][] DAY_ALIASES = {
            {"星期一", "周一", "礼拜一", "monday", "mon"},
            {"星期二", "周二", "礼拜二", "tuesday", "tue", "tues"},
            {"星期三", "周三", "礼拜三", "wednesday", "wed"},
            {"星期四", "周四", "礼拜四", "thursday", "thu", "thur", "thurs"},
            {"星期五", "周五", "礼拜五", "friday", "fri"}
    };
    private static final Pattern PERIOD_PREFIX = Pattern.compile("^(?:第\\s*)?(\\d{1,2})(?:\\s*节)?(?:\\s*[.、:：)）-])?\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    private ScheduleTextParser() { }

    public static Result parse(String source) {
        Result result = new Result();
        if (source == null || source.trim().isEmpty()) return result;
        String normalized = source.replace('\r', '\n').replace("\u00A0", " ").replaceAll("\\n+", "\n").trim();
        if (normalized.length() > 50_000) {
            result.warningCount++;
            normalized = normalized.substring(0, 50_000);
        }
        List<String> lines = new ArrayList<>();
        for (String raw : normalized.split("\\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) lines.add(line);
        }
        if (!parseGrid(lines, result)) parseDaySections(lines, result);
        return result;
    }

    private static boolean parseGrid(List<String> lines, Result result) {
        for (int headerIndex = 0; headerIndex < lines.size(); headerIndex++) {
            String[] header = splitGridLine(lines.get(headerIndex));
            List<Integer> days = new ArrayList<>();
            for (String cell : header) {
                int day = dayIndex(cell);
                if (day >= 0) days.add(day);
            }
            if (days.size() < 2) continue;
            int parsedRows = 0;
            for (int i = headerIndex + 1; i < lines.size(); i++) {
                String[] cells = splitGridLine(lines.get(i));
                if (cells.length < days.size() + 1) continue;
                int period = parsePeriod(cells[0]);
                if (period < 1 || period > 12) continue;
                int offset = cells.length - days.size();
                for (int column = 0; column < days.size(); column++) {
                    String subject = cleanSubject(cells[offset + column]);
                    if (!isEmptyMarker(subject)) add(result, days.get(column), period, subject);
                }
                parsedRows++;
            }
            if (parsedRows > 0) {
                result.gridMode = true;
                return true;
            }
        }
        return false;
    }

    private static String[] splitGridLine(String line) {
        String normalized = line.replace('｜', '|').replace('，', ',').trim();
        if (normalized.contains("\t")) return normalized.split("\\t", -1);
        if (normalized.contains("|")) return normalized.split("\\|", -1);
        if (normalized.matches(".*\\s{2,}.*")) return normalized.split("\\s{2,}", -1);
        return normalized.split("\\s+");
    }

    private static void parseDaySections(List<String> lines, Result result) {
        int currentDay = -1;
        int[] nextPeriod = {1, 1, 1, 1, 1};
        for (String line : lines) {
            DayLine dayLine = extractDayLine(line);
            if (dayLine != null) {
                currentDay = dayLine.day;
                if (!dayLine.remainder.isEmpty()) parseSubjectSequence(dayLine.remainder, currentDay, nextPeriod, result);
                continue;
            }
            if (currentDay < 0) {
                result.warningCount++;
                continue;
            }
            parseSubjectSequence(line, currentDay, nextPeriod, result);
        }
    }

    private static void parseSubjectSequence(String raw, int day, int[] nextPeriod, Result result) {
        String value = raw.trim();
        String[] tokens;
        if (value.matches(".*[，,、;；|\\t].*")) tokens = value.split("[，,、;；|\\t]", -1);
        else if (!PERIOD_PREFIX.matcher(value).matches() && value.matches(".*\\s+.*")) tokens = value.split("\\s+");
        else tokens = new String[]{value};
        for (String token : tokens) {
            String subject = cleanSubject(token);
            int period = nextPeriod[day];
            Matcher matcher = PERIOD_PREFIX.matcher(subject);
            if (matcher.matches() && !matcher.group(2).trim().isEmpty()) {
                period = Integer.parseInt(matcher.group(1));
                subject = cleanSubject(matcher.group(2));
            }
            if (period < 1 || period > 12) {
                result.warningCount++;
                continue;
            }
            nextPeriod[day] = period + 1;
            if (!isEmptyMarker(subject)) add(result, day, period, subject);
        }
    }

    private static void add(Result result, int day, int period, String subject) {
        String key = day + ":" + period;
        Lesson previous = result.bySlot.put(key, new Lesson(day, period, subject));
        if (previous != null) result.warningCount++;
    }

    private static DayLine extractDayLine(String line) {
        String trimmed = line.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (int day = 0; day < DAY_ALIASES.length; day++) {
            for (String alias : DAY_ALIASES[day]) {
                String normalizedAlias = alias.toLowerCase(Locale.ROOT);
                if (!lower.startsWith(normalizedAlias)) continue;
                int end = alias.length();
                if (trimmed.length() > end) {
                    char next = trimmed.charAt(end);
                    if (!(Character.isWhitespace(next) || next == ':' || next == '：' || next == '-' || next == '—')) continue;
                }
                String rest = trimmed.substring(end).replaceFirst("^[\\s:：—-]+", "").trim();
                return new DayLine(day, rest);
            }
        }
        return null;
    }

    private static int dayIndex(String raw) {
        String value = raw.trim().replaceAll("[:：]$", "").toLowerCase(Locale.ROOT);
        for (int day = 0; day < DAY_ALIASES.length; day++) for (String alias : DAY_ALIASES[day]) if (value.equals(alias.toLowerCase(Locale.ROOT))) return day;
        return -1;
    }

    private static int parsePeriod(String raw) {
        Matcher matcher = Pattern.compile(".*?(\\d{1,2}).*").matcher(raw);
        if (!matcher.matches()) return -1;
        try { return Integer.parseInt(matcher.group(1)); } catch (Exception ignored) { return -1; }
    }

    private static String cleanSubject(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("^[·•]+|[·•]+$", "").trim();
    }

    private static boolean isEmptyMarker(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.equals("-") || normalized.equals("—") || normalized.equals("空") || normalized.equals("无") || normalized.equals("无课") || normalized.equals("none") || normalized.equals("empty");
    }

    private static final class DayLine {
        final int day;
        final String remainder;
        DayLine(int day, String remainder) { this.day = day; this.remainder = remainder; }
    }

    public static final class Lesson {
        public final int day;
        public final int period;
        public final String subject;
        Lesson(int day, int period, String subject) { this.day = day; this.period = period; this.subject = subject; }
    }

    public static final class Result {
        private final LinkedHashMap<String, Lesson> bySlot = new LinkedHashMap<>();
        public int warningCount;
        public boolean gridMode;
        public List<Lesson> lessons() { return new ArrayList<>(bySlot.values()); }
        public boolean isEmpty() { return bySlot.isEmpty(); }
    }
}
