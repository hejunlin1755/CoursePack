package com.kechengbao.app;

import android.app.PendingIntent;
import android.app.AlarmManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Base64;
import android.view.View;
import android.widget.RemoteViews;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class TomorrowWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_MIDNIGHT_REFRESH = "com.kechengbao.app.action.MIDNIGHT_WIDGET_REFRESH";
    static final Class<?>[] PROVIDERS = {
            TomorrowWidgetTallProvider.class,
            TomorrowWidgetLargeProvider.class
    };
    private static final int[] SEGMENTS = {
            R.id.widget_segment_1, R.id.widget_segment_2, R.id.widget_segment_3,
            R.id.widget_segment_4, R.id.widget_segment_5, R.id.widget_segment_6,
            R.id.widget_segment_7, R.id.widget_segment_8, R.id.widget_segment_9,
            R.id.widget_segment_10, R.id.widget_segment_11, R.id.widget_segment_12,
            R.id.widget_segment_13
    };
    private static final int[] PENDING_ROWS = {
            R.id.widget_pending_row_1, R.id.widget_pending_row_2, R.id.widget_pending_row_3
    };
    private static final int[] PENDING_TEXTS = {
            R.id.widget_pending_1, R.id.widget_pending_2, R.id.widget_pending_3
    };

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
        scheduleBoundaryRefresh(context);
    }

    @Override public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleBoundaryRefresh(context);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, android.os.Bundle options) {
        updateWidget(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) || ACTION_MIDNIGHT_REFRESH.equals(action)) {
            refreshAll(context);
            scheduleBoundaryRefresh(context);
        }
    }

    private static void scheduleBoundaryRefresh(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(zone).plusSeconds(2);
        ZonedDateTime evening = now.toLocalDate().atTime(18, 0).atZone(zone);
        if (!evening.isAfter(now)) evening = evening.plusDays(1);
        long triggerAt = (evening.isBefore(midnight) ? evening : midnight).toInstant().toEpochMilli();
        Intent refresh = new Intent(context, TomorrowWidgetProvider.class).setAction(ACTION_MIDNIGHT_REFRESH);
        PendingIntent operation = PendingIntent.getBroadcast(context, 79, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<?> providerClass : PROVIDERS) {
            ComponentName provider = new ComponentName(context, providerClass);
            int[] ids = manager.getAppWidgetIds(provider);
            for (int id : ids) updateWidget(context, manager, id);
        }
    }

    static void updateWidget(Context context, AppWidgetManager manager, int id) {
        WidgetData data = WidgetData.load(context);
        android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        boolean large = info != null && info.provider != null
                && TomorrowWidgetLargeProvider.class.getName().equals(info.provider.getClassName());
        RemoteViews views = new RemoteViews(context.getPackageName(),
                large ? R.layout.widget_tomorrow_large : R.layout.widget_tomorrow_square);
        int total = data.items.size();
        boolean complete = total > 0 && data.done == total && data.missingSubjects == 0;
        views.setTextViewText(R.id.widget_status, data.status());
        views.setTextViewText(R.id.widget_progress, data.progress());
        views.setTextViewText(R.id.widget_day_label, data.title());
        views.setViewVisibility(R.id.widget_complete_icon, complete ? View.VISIBLE : View.GONE);
        if (large) updateLargeDetail(views, data, complete);
        int shown = Math.min(total, SEGMENTS.length);
        int completed = total <= SEGMENTS.length ? data.done : Math.round((data.done / (float) total) * SEGMENTS.length);
        views.setViewVisibility(R.id.widget_segments, total == 0 ? View.GONE : View.VISIBLE);
        for (int i = 0; i < SEGMENTS.length; i++) {
            views.setViewVisibility(SEGMENTS[i], i < shown || total > SEGMENTS.length ? View.VISIBLE : View.GONE);
            views.setInt(SEGMENTS[i], "setBackgroundResource", i < completed ? R.drawable.widget_segment_done : R.drawable.widget_segment_pending);
        }

        Intent open = new Intent(context, CoursePackActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_status, openIntent);
        views.setOnClickPendingIntent(R.id.widget_progress, openIntent);
        views.setOnClickPendingIntent(R.id.widget_complete_icon, openIntent);
        if (large) views.setOnClickPendingIntent(R.id.widget_detail, openIntent);
        manager.updateAppWidget(id, views);
    }

    private static void updateLargeDetail(RemoteViews views, WidgetData data, boolean complete) {
        List<String> pending = data.pendingItems(3);
        boolean showList = !pending.isEmpty();
        views.setViewVisibility(R.id.widget_pending_list, showList ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.widget_detail_state, showList ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_detail_complete_icon, complete ? View.VISIBLE : View.GONE);
        views.setTextViewText(R.id.widget_detail_state_text, data.detail());
        for (int i=0; i<PENDING_ROWS.length; i++) {
            boolean visible = i < pending.size();
            views.setViewVisibility(PENDING_ROWS[i], visible ? View.VISIBLE : View.GONE);
            if (visible) views.setTextViewText(PENDING_TEXTS[i], pending.get(i));
        }
    }
}

class WidgetData {
    static final String PREFS = "course_pack_data_v1";
    static final String KEY_SUBJECTS = "subjects_v2";
    static final String KEY_ENTRIES = "entries_v2";
    static final String KEY_CHECKS = "item_checks_v2";

    LocalDate date;
    final List<WidgetItem> items = new ArrayList<>();
    int courseCount;
    int missingSubjects;
    int done;
    boolean configured;

    static WidgetData load(Context context) {
        WidgetData data = new WidgetData();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<CoursePackActivity.Subject> subjects = new ArrayList<>();
        List<CoursePackActivity.Entry> allEntries = new ArrayList<>();
        String subjectRaw = prefs.getString(KEY_SUBJECTS, "");
        String entryRaw = prefs.getString(KEY_ENTRIES, "");
        data.configured = !subjectRaw.isEmpty() && !entryRaw.isEmpty();
        if (!subjectRaw.isEmpty()) for (String line : subjectRaw.split("\\n")) {
            CoursePackActivity.Subject subject = CoursePackActivity.Subject.decode(line);
            if (subject != null) subjects.add(subject);
        }
        if (!entryRaw.isEmpty()) for (String line : entryRaw.split("\\n")) {
            CoursePackActivity.Entry entry = CoursePackActivity.Entry.decode(line);
            if (entry != null) allEntries.add(entry);
        }
        data.date = resolveDate(allEntries);
        List<CoursePackActivity.Entry> entries = new ArrayList<>();
        for (CoursePackActivity.Entry entry : allEntries)
            if (entry.day == data.date.getDayOfWeek().getValue()-1) entries.add(entry);
        entries.sort(Comparator.comparingInt(e -> e.period));
        data.courseCount = entries.size();
        LinkedHashMap<CoursePackActivity.Subject, Boolean> grouped = new LinkedHashMap<>();
        for (CoursePackActivity.Entry entry : entries) {
            for (CoursePackActivity.Subject subject : subjects) if (subject.id.equals(entry.subjectId)) {
                grouped.put(subject, true);
                break;
            }
        }
        Set<String> checks = prefs.getStringSet(KEY_CHECKS, new HashSet<>());
        for (CoursePackActivity.Subject subject : grouped.keySet()) {
            if (subject.noItems) continue;
            if (subject.items.isEmpty()) { data.missingSubjects++; continue; }
            for (String item : subject.items) {
                String encoded = b64(item.trim());
                String key = "packed:" + subject.id + ":" + encoded;
                boolean checked = checks.contains(key);
                if (!checked) {
                    String suffix = ":" + subject.id + ":" + encoded;
                    LocalDate oldest = LocalDate.now().minusDays(1);
                    for (String saved : checks) if (saved.endsWith(suffix)) {
                        int split = saved.indexOf(':');
                        try {
                            if (split > 0 && !LocalDate.parse(saved.substring(0, split)).isBefore(oldest)) { checked = true; break; }
                        } catch (Exception ignored) { }
                    }
                }
                data.items.add(new WidgetItem(subject.name, item, key, checked));
                if (checked) data.done++;
            }
        }
        return data;
    }

    String status() {
        if (!configured) return "点击开始设置";
        if (items.isEmpty() && missingSubjects > 0) return "先设置携带物";
        if (items.isEmpty()) return courseCount == 0 ? dayWord()+"没有课" : "无需准备物品";
        if (done == items.size() && missingSubjects > 0) return "还有科目未设置";
        if (done == items.size() && missingSubjects == 0) return "全部装好了";
        return "还差 " + (items.size()-done) + " 件";
    }

    String progress() {
        if (!configured) return "创建科目和课程表后显示书包进度";
        if (items.isEmpty()) return courseCount == 0 ? dayWord()+"没有安排课程" : missingSubjects > 0 ? missingSubjects + " 个科目尚未设置" : dayWord()+"无需携带物品";
        return done + " / " + items.size() + " 已装好";
    }

    String title() { return dayWord()+"书包"; }

    String detail() {
        if (!configured) return "打开课程包，先创建科目和课程表";
        if (items.isEmpty()) {
            if (courseCount == 0) return dayWord()+"没有课程，不用整理书包";
            if (missingSubjects > 0) return "有 " + missingSubjects + " 个科目还没设置携带物";
            return dayWord()+"的课程都不需要携带物品";
        }
        int remaining = items.size() - done;
        if (remaining == 0 && missingSubjects == 0) return "全部完成，可以放心出发";
        if (remaining == 0) return "还有 " + missingSubjects + " 个科目未设置携带物";
        return "还有 " + remaining + " 件物品没有装好";
    }

    List<String> pendingItems(int limit) {
        List<String> pending = new ArrayList<>();
        for (WidgetItem item : items) {
            if (!item.checked) pending.add(item.item);
            if (pending.size() == limit) break;
        }
        return pending;
    }

    private String dayWord() {
        LocalDate today = LocalDate.now();
        if (date.equals(today)) return "今天";
        if (date.equals(today.plusDays(1))) return "明天";
        return "下一上课日";
    }

    private static LocalDate resolveDate(List<CoursePackActivity.Entry> entries) {
        LocalDate candidate = LocalTime.now().isBefore(LocalTime.of(CoursePackActivity.PREPARATION_SWITCH_HOUR, 0)) ? LocalDate.now() : LocalDate.now().plusDays(1);
        if (entries.isEmpty()) {
            while (candidate.getDayOfWeek().getValue() > 5) candidate = candidate.plusDays(1);
            return candidate;
        }
        for (int i=0; i<8; i++) {
            int day = candidate.getDayOfWeek().getValue()-1;
            if (day >= 0 && day < 5) {
                for (CoursePackActivity.Entry entry : entries) if (entry.day == day) return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static String b64(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP | Base64.URL_SAFE);
    }
}

class WidgetItem {
    final String subject, item, key;
    final boolean checked;
    WidgetItem(String subject, String item, String key, boolean checked) {
        this.subject = subject; this.item = item; this.key = key; this.checked = checked;
    }
}
