package com.kechengbao.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns positioned OCR lines from a photographed timetable into weekday lists. */
public final class ScheduleOcrLayout {
    private static final Pattern PERIOD = Pattern.compile("第\\s*(1[0-2]|[1-9])\\s*[节節]");
    private static final Pattern TIME = Pattern.compile(".*\\d{1,2}[:.：]\\d{2}\\s*[-–—~至]\\s*\\d{1,2}[:.：]\\d{2}.*");
    private static final String[] DAY_NAMES = {"星期一", "星期二", "星期三", "星期四", "星期五"};

    private ScheduleOcrLayout() {}

    public static final class Token {
        public final String text;
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        public Token(String text, int left, int top, int right, int bottom) {
            this.text = text == null ? "" : text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float centerX() { return (left + right) / 2f; }
        float centerY() { return (top + bottom) / 2f; }
    }

    public static String format(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) return "";
        Map<Integer, Float> observedDays = new HashMap<>();
        Map<Integer, Float> periods = new HashMap<>();
        float headerY = 0f;
        int headerCount = 0;
        for (Token token : tokens) {
            int day = dayIndex(token.text);
            if (day >= 0) {
                observedDays.put(day, token.centerX());
                headerY += token.centerY();
                headerCount++;
            }
            Matcher matcher = PERIOD.matcher(compact(token.text));
            if (matcher.find()) periods.put(Integer.parseInt(matcher.group(1)), token.centerY());
        }
        if (observedDays.size() < 3 || periods.size() < 2) return "";
        headerY /= Math.max(1, headerCount);
        float[] dayCenters = inferDayCenters(observedDays);
        float averageColumnWidth = Math.abs(dayCenters[4] - dayCenters[0]) / 4f;
        int maxPeriod = periods.keySet().stream().max(Integer::compareTo).orElse(0);
        Map<String, List<Token>> cells = new HashMap<>();
        for (Token token : tokens) {
            String subject = cleanSubject(token.text);
            if (subject.isEmpty() || token.centerY() <= headerY || dayIndex(subject) >= 0) continue;
            int day = nearestDay(token.centerX(), dayCenters);
            if (Math.abs(token.centerX() - dayCenters[day]) > averageColumnWidth * .72f) continue;
            int period = nearestPeriod(token.centerY(), periods);
            if (period < 1 || period > 12) continue;
            cells.computeIfAbsent(day + ":" + period, ignored -> new ArrayList<>()).add(new Token(subject, token.left, token.top, token.right, token.bottom));
        }
        StringBuilder formatted = new StringBuilder();
        int nonEmpty = 0;
        for (int day = 0; day < 5; day++) {
            if (formatted.length() > 0) formatted.append('\n');
            formatted.append(DAY_NAMES[day]).append('：');
            for (int period = 1; period <= maxPeriod; period++) {
                if (period > 1) formatted.append('，');
                List<Token> cell = cells.get(day + ":" + period);
                if (cell == null || cell.isEmpty()) {
                    formatted.append('空');
                    continue;
                }
                cell.sort(Comparator.comparingInt((Token token) -> token.left).thenComparingInt(token -> token.top));
                LinkedHashSet<String> parts = new LinkedHashSet<>();
                for (Token token : cell) parts.add(token.text);
                formatted.append(String.join("/", parts));
                nonEmpty++;
            }
        }
        return nonEmpty >= 5 ? formatted.toString() : "";
    }

    private static float[] inferDayCenters(Map<Integer, Float> observed) {
        float meanDay = 0f;
        float meanX = 0f;
        for (Map.Entry<Integer, Float> entry : observed.entrySet()) {
            meanDay += entry.getKey();
            meanX += entry.getValue();
        }
        meanDay /= observed.size();
        meanX /= observed.size();
        float numerator = 0f;
        float denominator = 0f;
        for (Map.Entry<Integer, Float> entry : observed.entrySet()) {
            numerator += (entry.getKey() - meanDay) * (entry.getValue() - meanX);
            denominator += (entry.getKey() - meanDay) * (entry.getKey() - meanDay);
        }
        float step = denominator == 0f ? 1f : numerator / denominator;
        float base = meanX - step * meanDay;
        float[] centers = new float[5];
        for (int day = 0; day < centers.length; day++) centers[day] = observed.getOrDefault(day, base + step * day);
        return centers;
    }

    private static int nearestDay(float x, float[] centers) {
        int best = 0;
        float distance = Float.MAX_VALUE;
        for (int day = 0; day < centers.length; day++) {
            float candidate = Math.abs(x - centers[day]);
            if (candidate < distance) { distance = candidate; best = day; }
        }
        return best;
    }

    private static int nearestPeriod(float y, Map<Integer, Float> periods) {
        int best = -1;
        float distance = Float.MAX_VALUE;
        for (Map.Entry<Integer, Float> entry : periods.entrySet()) {
            float candidate = Math.abs(y - entry.getValue());
            if (candidate < distance) { distance = candidate; best = entry.getKey(); }
        }
        return best;
    }

    private static int dayIndex(String raw) {
        String value = compact(raw).replace("週", "周");
        String[] suffixes = {"一", "二", "三", "四", "五"};
        for (int day = 0; day < suffixes.length; day++) {
            if (value.contains("星期" + suffixes[day]) || value.contains("周" + suffixes[day]) || value.contains("礼拜" + suffixes[day]) || value.contains("禮拜" + suffixes[day])) return day;
        }
        return -1;
    }

    private static String cleanSubject(String raw) {
        String value = raw == null ? "" : raw.trim().replaceAll("^[|丨:：,，;；·.\\-—_]+|[|丨:：,，;；·.\\-—_]+$", "");
        String compact = compact(value);
        if (compact.isEmpty() || PERIOD.matcher(compact).find() || TIME.matcher(compact).matches()) return "";
        if (compact.matches(".*(课表|課表|早读|早讀|早操|午休|时间|時間).*")) return "";
        return value;
    }

    private static String compact(String value) { return value == null ? "" : value.replaceAll("\\s+", ""); }
}
