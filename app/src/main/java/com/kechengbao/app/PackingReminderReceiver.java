package com.kechengbao.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class PackingReminderReceiver extends BroadcastReceiver {
    private static final String ACTION_REMIND = "com.kechengbao.app.action.PACKING_REMINDER";
    private static final String CHANNEL_ID = "packing_reminders";
    private static final int REQUEST_CODE = 290;
    private static final int NOTIFICATION_ID = 290;

    @Override public void onReceive(Context context, Intent intent) {
        if (ACTION_REMIND.equals(intent.getAction())) showIfNeeded(context);
        schedule(context);
    }

    static void schedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppText.PREFS, Context.MODE_PRIVATE);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent operation = reminderIntent(context);
        alarms.cancel(operation);
        if (!prefs.getBoolean(AppText.KEY_REMINDER_ENABLED, false)) return;
        int hour = prefs.getInt(AppText.KEY_REMINDER_HOUR, 20);
        int minute = prefs.getInt(AppText.KEY_REMINDER_MINUTE, 0);
        long triggerAt = ReminderSchedule.nextTrigger(ZonedDateTime.now(ZoneId.systemDefault()), hour, minute).toInstant().toEpochMilli();
        if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        } else if (Build.VERSION.SDK_INT < 31) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation);
        }
    }

    static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(reminderIntent(context));
        dismissNotification(context);
    }

    static void dismissNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private static PendingIntent reminderIntent(Context context) {
        Intent intent = new Intent(context, PackingReminderReceiver.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void showIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        WidgetData data = WidgetData.load(context);
        int total = data.items.size();
        if (!ReminderSchedule.shouldNotify(data.configured, total, data.done, data.missingSubjects)) return;
        int remaining = Math.max(0, total - data.done);
        String detail;
        if (remaining > 0 && data.missingSubjects > 0) detail = AppText.t(context, "还有 " + remaining + " 件没装好，" + data.missingSubjects + " 个科目未设置", remaining + " items left and " + data.missingSubjects + " subjects need setup");
        else if (remaining > 0) detail = AppText.t(context, "还有 " + remaining + " 件物品没有装好", remaining + " items still need packing");
        else detail = AppText.t(context, "还有 " + data.missingSubjects + " 个科目没有设置携带物", data.missingSubjects + " subjects still need carry items");
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, AppText.t(context, "书包提醒", "Packing reminders"), NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(AppText.t(context, "在设定时间提醒尚未完成的书包清单", "Reminds you when the packing checklist is unfinished"));
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, CoursePackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openIntent = PendingIntent.getActivity(context, REQUEST_CODE, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification).setContentTitle(AppText.t(context, "书包还没收好", "Your bag is not packed yet")).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(openIntent).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).setShowWhen(true);
        manager.notify(NOTIFICATION_ID, builder.build());
    }
}
