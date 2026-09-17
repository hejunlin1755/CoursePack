package com.kechengbao.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppText {
    static final String PREFS = "course_pack_data_v1";
    static final String KEY_LANGUAGE = "settings_language_v1";
    static final String KEY_THEME = "settings_theme_v1";
    static final String KEY_PALETTE = "settings_palette_v1";
    static final String KEY_HAPTICS = "settings_haptics_v1";

    private AppText() { }

    static Context wrap(Context base) {
        SharedPreferences prefs = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_LANGUAGE, "system");
        Locale system = base.getResources().getConfiguration().getLocales().get(0);
        Locale target = "en".equals(mode) ? Locale.ENGLISH : "zh".equals(mode) ? Locale.SIMPLIFIED_CHINESE : system;
        Locale.setDefault(target);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= 24) configuration.setLocale(target);
        return base.createConfigurationContext(configuration);
    }

    static boolean isEnglish(Context context) {
        String mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "system");
        if ("en".equals(mode)) return true;
        if ("zh".equals(mode)) return false;
        return "en".equalsIgnoreCase(context.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    static String t(Context context, String chinese, String english) {
        return isEnglish(context) ? english : chinese;
    }

    static String translate(Context context, String raw) {
        if (raw == null || !isEnglish(context)) return raw;
        String exact = switch (raw) {
            case "准备" -> "Pack";
            case "课表" -> "Schedule";
            case "科目" -> "Subjects";
            case "查看当前要准备的携带物" -> "View the items to pack";
            case "查看本周课表" -> "View this week's schedule";
            case "管理科目和携带物" -> "Manage subjects and carry items";
            case "设置" -> "Settings";
            case "返回" -> "Back";
            case "让课程包更符合你的使用习惯" -> "Make CoursePack work the way you do";
            case "概览" -> "Overview";
            case "外观" -> "Appearance";
            case "主题模式" -> "Theme mode";
            case "跟随系统" -> "Use system setting";
            case "浅色" -> "Light";
            case "深色" -> "Dark";
            case "强调色" -> "Accent color";
            case "系统动态色" -> "Dynamic color";
            case "紫罗兰" -> "Violet";
            case "海洋蓝" -> "Ocean";
            case "森林绿" -> "Forest";
            case "语言" -> "Language";
            case "应用语言" -> "App language";
            case "简体中文" -> "Simplified Chinese";
            case "交互" -> "Interaction";
            case "触感反馈" -> "Haptic feedback";
            case "数据" -> "Data";
            case "导出备份" -> "Export backup";
            case "保存课程、携带物、打卡和设置" -> "Save lessons, carry items, checks, and settings";
            case "导入备份" -> "Import backup";
            case "从课程包备份文件恢复数据" -> "Restore from a CoursePack backup";
            case "备份已导出" -> "Backup exported";
            case "导出失败，请换一个位置重试" -> "Export failed. Choose another location and try again";
            case "无法读取备份，请选择课程包导出的文件" -> "Could not read this backup. Choose a file exported by CoursePack";
            case "导入这份备份？" -> "Import this backup?";
            case "确认导入" -> "Import backup";
            case "导入失败，原有数据没有改变" -> "Import failed. Your existing data was not changed";
            case "备份已导入" -> "Backup imported";
            case "关于" -> "About";
            case "课程包 2.6.1" -> "CoursePack 2.6.1";
            case "数据仅保存在本机 · 无需联网" -> "Stored only on this device · Works offline";
            case "排一节课" -> "Add lesson";
            case "新建科目" -> "New subject";
            case "今天" -> "Today";
            case "明天" -> "Tomorrow";
            case "下一上课日" -> "Next school day";
            case "星期一" -> "Monday";
            case "星期二" -> "Tuesday";
            case "星期三" -> "Wednesday";
            case "星期四" -> "Thursday";
            case "星期五" -> "Friday";
            case "星期六" -> "Saturday";
            case "星期日" -> "Sunday";
            case "书包进度" -> "Bag progress";
            case "清空已选" -> "Clear checks";
            case "清空已选择的物品" -> "Clear checked items";
            case "当前没有已选择的物品" -> "No checked items";
            case "按物品准备" -> "Pack by item";
            case "已装状态会自动沿用，也可以逐件标记拿出" -> "Packed items stay checked until you take them out";
            case "还没有课程" -> "No lessons yet";
            case "先到课表页排课。" -> "Add lessons from the Schedule page first.";
            case "每日随身" -> "Everyday carry";
            case "每日随身物品" -> "Everyday items";
            case "永久项每天重置，临时项只出现一次" -> "Daily items reset each day; temporary items appear once";
            case "添加" -> "Add";
            case "添加每日随身物品" -> "Add an everyday item";
            case "暂时没有随身物品" -> "No everyday items yet";
            case "可添加校卡、水杯或临时用品" -> "Add an ID card, bottle, or a temporary item";
            case "没有每日随身物品，点此添加" -> "No everyday items. Tap to add one";
            case "每天都要确认" -> "Check every day";
            case "编辑随身物品" -> "Edit everyday item";
            case "添加随身物品" -> "Add everyday item";
            case "这是可选功能，不设置时不会出现在准备首页" -> "Optional. This section stays hidden until you add an item";
            case "永久项每天出现；临时项只加入当前准备日" -> "Daily items recur; temporary items only appear for this packing day";
            case "永久 · 每天出现" -> "Daily · appears every day";
            case "出现方式" -> "Frequency";
            case "物品名称" -> "Item name";
            case "科目名称" -> "Subject name";
            case "删除" -> "Delete";
            case "物" -> "I";
            case "永久，每天出现" -> "Daily, appears every day";
            case "临时，仅当前准备日" -> "Temporary, this packing day only";
            case "例如：校卡、水杯" -> "For example: ID card or bottle";
            case "保存修改" -> "Save changes";
            case "添加物品" -> "Add item";
            case "请填写物品名称" -> "Enter an item name";
            case "这些可以拿出" -> "Take these out";
            case "拿出" -> "Take out";
            case "清空所有已选物品？" -> "Clear all checked items?";
            case "课程携带物和当前准备日的随身物品都会恢复为未选，已有设置不会被删除。" -> "Course items and everyday items for this packing day will be unchecked. Your setup will stay intact.";
            case "取消" -> "Cancel";
            case "确认清空" -> "Clear checks";
            case "无需准备" -> "Nothing to pack";
            case "尚未设置携带物 · 点此设置" -> "Carry items not set · Tap to set up";
            case "书包收好啦" -> "Bag packed";
            case "这次要带的物品已经全部装好" -> "Everything for this school day is packed";
            case "先设置携带物" -> "Set carry items first";
            case "无需准备物品" -> "Nothing to pack";
            case "全部装好了" -> "All packed";
            case "这些科目都确认无需携带物品" -> "These subjects need no carry items";
            case "本周课表" -> "Weekly schedule";
            case "课表只安排科目，携带物统一在科目库管理" -> "Schedule subjects here; manage carry items in Subjects";
            case "批量设置节次时间" -> "Set lesson times in bulk";
            case "批量设置星期一到星期五的节次时间" -> "Set lesson times for multiple weekdays";
            case "这天没有课" -> "No lessons this day";
            case "点“排一节课”，从科目库选择科目。" -> "Tap Add lesson and choose a subject.";
            case "已删除的科目" -> "Deleted subject";
            case "请重新选择科目" -> "Choose a subject again";
            case "编辑这节课" -> "Edit this lesson";
            case "科目库" -> "Subjects";
            case "每个科目只设置一次携带物，所有课表自动同步" -> "Set carry items once per subject; every lesson stays in sync";
            case "还没有科目" -> "No subjects yet";
            case "先新建科目和需要携带的物品，再去课表排课。" -> "Create subjects and carry items, then add them to the schedule.";
            case "未启用 · 可选功能" -> "Not enabled · Optional";
            case "管理每日随身物品" -> "Manage everyday items";
            case "随" -> "E";
            case "科" -> "S";
            case "无需携带物品" -> "No carry items";
            case "尚未设置携带物" -> "Carry items not set";
            case "科目设置" -> "Subject setup";
            case "修改一次，所有课表中的该科目都会同步" -> "Edit once and every scheduled lesson updates";
            case "先建立科目，再把它排进课表" -> "Create the subject first, then add it to the schedule";
            case "例如：数学" -> "For example: Math";
            case "默认携带物" -> "Default carry items";
            case "添加一件物品" -> "Add one item";
            case "例如：课本" -> "For example: textbook";
            case "移除这件物品" -> "Remove this item";
            case "保存科目" -> "Save subject";
            case "请填写科目名称" -> "Enter a subject name";
            case "编辑排课" -> "Edit lesson";
            case "点选开始和结束时间，不需要手动输入格式" -> "Choose start and end times without typing a format";
            case "选择科目" -> "Choose subject";
            case "携带物会自动使用科目库中的设置" -> "Carry items come from the subject library";
            case "星期" -> "Day";
            case "节次" -> "Period";
            case "开始" -> "Start";
            case "结束" -> "End";
            case "开始时间" -> "Start time";
            case "结束时间" -> "End time";
            case "选择开始时间" -> "Choose start time";
            case "选择结束时间" -> "Choose end time";
            case "结束时间需要晚于开始时间" -> "End time must be later than start time";
            case "移除这节" -> "Delete lesson";
            case "保存排课" -> "Save lesson";
            case "选择多个星期，按上课时长和课间时长自动计算；只更新时间，不会新增或删除课程" -> "Choose weekdays and calculate periods from lesson and break lengths. This only updates times.";
            case "应用到星期" -> "Apply to weekdays";
            case "从第几节开始" -> "Start at period";
            case "生成几节" -> "Number of periods";
            case "第一节开始" -> "First lesson starts";
            case "选择第一节开始时间" -> "Choose the first lesson start time";
            case "每节时长" -> "Lesson length";
            case "课间时长" -> "Break length";
            case "时间预览" -> "Time preview";
            case "应用批量时间" -> "Apply times";
            case "至少选择一个星期" -> "Choose at least one weekday";
            case "起始节次和生成节数超过第 12 节" -> "The generated range goes beyond period 12";
            case "生成后的结束时间超出当天，请减少节数或时长" -> "The generated end time exceeds the day. Reduce the number or duration.";
            case "小时" -> "Hour";
            case "分钟" -> "Minute";
            case "24 小时制，使用加减按钮调整，不需要键盘输入" -> "24-hour time. Use the minus and plus buttons";
            case "选用这个时间" -> "Use this time";
            case "减少" -> "Decrease";
            case "增加" -> "Increase";
            case "点击开始设置" -> "Tap to set up";
            case "创建科目和课程表后显示书包进度" -> "Create subjects and a schedule to see bag progress";
            case "打开课程包，先创建科目和课程表" -> "Open CoursePack and create subjects and a schedule";
            case "还有科目未设置" -> "Some subjects are not set up";
            case "没有课" -> "No lessons";
            case "没有安排课程" -> "has no scheduled lessons";
            case "没有课程，不用整理书包" -> "has no lessons, so there is nothing to pack";
            case "的课程都不需要携带物品" -> "'s lessons need no carry items";
            case "全部完成，可以放心出发" -> "Everything is ready";
            case "临时随身" -> "Temporary item";
            case "书包" -> " bag";
            default -> null;
        };
        if (exact != null) return exact;

        Matcher m;
        if ((m = Pattern.compile("^(今天|明天|下一上课日) · (星期[一二三四五六日])$").matcher(raw)).matches())
            return translate(context, m.group(1)) + " · " + translate(context, m.group(2));
        if ((m = Pattern.compile("^(今天|明天|下一上课日)书包$").matcher(raw)).matches())
            return translate(context, m.group(1)) + " bag";
        if ((m = Pattern.compile("^(今天|明天|下一上课日)没有课$").matcher(raw)).matches())
            return translate(context, m.group(1)) + " has no lessons";
        if ((m = Pattern.compile("^(今天|明天|下一上课日)没有安排课程$").matcher(raw)).matches())
            return translate(context, m.group(1)) + " has no scheduled lessons";
        if ((m = Pattern.compile("^(今天|明天|下一上课日)无需携带物品$").matcher(raw)).matches())
            return "Nothing to pack for " + translate(context, m.group(1)).toLowerCase(Locale.ROOT);
        if ((m = Pattern.compile("^(今天|明天|下一上课日)没有课程，不用整理书包$").matcher(raw)).matches())
            return translate(context, m.group(1)) + " has no lessons, so there is nothing to pack";
        if ((m = Pattern.compile("^(今天|明天|下一上课日)的课程都不需要携带物品$").matcher(raw)).matches())
            return "No carry items are needed for " + translate(context, m.group(1)).toLowerCase(Locale.ROOT) + "'s lessons";
        if ((m = Pattern.compile("^(\\d{1,2})月(\\d{1,2})日 · (\\d+) 节课$").matcher(raw)).matches())
            return m.group(1) + "/" + m.group(2) + " · " + m.group(3) + " lessons";
        if ((m = Pattern.compile("^(仅 |临时 · |临时 · 仅 )(\\d{1,2})月(\\d{1,2})日$").matcher(raw)).matches())
            return (m.group(1).startsWith("临时") ? "Temporary · " : "Only ") + m.group(2) + "/" + m.group(3);
        if ((m = Pattern.compile("^第 ?(\\d+)节(?:  )?(.*)$").matcher(raw)).matches())
            return "Period " + m.group(1) + (m.group(2).isEmpty() ? "" : "  " + m.group(2));
        if ((m = Pattern.compile("^(\\d+) 节课$").matcher(raw)).matches()) return m.group(1) + " lessons";
        if ((m = Pattern.compile("^(\\d+) 件$").matcher(raw)).matches()) return m.group(1) + " items";
        if ((m = Pattern.compile("^(\\d+) 个科目 · (\\d+) 节周课程$").matcher(raw)).matches())
            return m.group(1) + " subjects · " + m.group(2) + " weekly lessons";
        if ((m = Pattern.compile("^(\\d+) 件携带物 · 正在准备 (.+)$").matcher(raw)).matches())
            return m.group(1) + " carry items · Preparing " + m.group(2);
        if ((m = Pattern.compile("^(\\d+) 件 · (\\d+) 件每天出现$").matcher(raw)).matches())
            return m.group(1) + " items · " + m.group(2) + " daily";
        if ((m = Pattern.compile("^(\\d+) 件物品在当前准备日用不到$").matcher(raw)).matches())
            return m.group(1) + " items are not needed for this day";
        if ((m = Pattern.compile("^(.+) · 当前准备日用不到$").matcher(raw)).matches()) return m.group(1) + " · not needed this day";
        if ((m = Pattern.compile("^还差 (\\d+) 件$").matcher(raw)).matches()) return m.group(1) + " left";
        if ((m = Pattern.compile("^(\\d+) / (\\d+) 已装好$").matcher(raw)).matches()) return m.group(1) + " / " + m.group(2) + " packed";
        if ((m = Pattern.compile("^(\\d+) 个科目尚未设置 · (\\d+) / (\\d+) 已装好$").matcher(raw)).matches())
            return m.group(1) + " subjects not set · " + m.group(2) + " / " + m.group(3) + " packed";
        if ((m = Pattern.compile("^已装好 (\\d+) 件，共 (\\d+) 件$").matcher(raw)).matches())
            return m.group(1) + " of " + m.group(2) + " packed";
        if ((m = Pattern.compile("^删除 · (\\d+) 节$").matcher(raw)).matches()) return "Delete · " + m.group(1) + " lessons";
        if ((m = Pattern.compile("^编辑第 (\\d+) 节 (.+)$").matcher(raw)).matches()) return "Edit period " + m.group(1) + ", " + m.group(2);
        if ((m = Pattern.compile("^编辑 (.+) 的携带物$").matcher(raw)).matches()) return "Edit carry items for " + m.group(1);
        if ((m = Pattern.compile("^编辑随身物品 (.+)$").matcher(raw)).matches()) return "Edit everyday item " + m.group(1);
        if ((m = Pattern.compile("^编辑 (.+)$").matcher(raw)).matches()) return "Edit " + m.group(1);
        if ((m = Pattern.compile("^拿出 (.+)，属于 (.+)$").matcher(raw)).matches()) return "Take out " + m.group(1) + " from " + m.group(2);
        if ((m = Pattern.compile("^(勾选|取消勾选) (.+)$").matcher(raw)).matches()) return ("勾选".equals(m.group(1)) ? "Check " : "Uncheck ") + m.group(2);
        if ((m = Pattern.compile("^(选择|取消|已选择|未选择) (.+)$").matcher(raw)).matches()) {
            String prefix = switch (m.group(1)) { case "选择" -> "Select "; case "取消" -> "Deselect "; case "已选择" -> "Selected "; default -> "Not selected "; };
            return prefix + translate(context, m.group(2));
        }
        if ((m = Pattern.compile("^每日随身物品，(.+)$").matcher(raw)).matches()) return "Everyday items, " + translate(context, m.group(1));
        if ((m = Pattern.compile("^(\\d+) 个科目尚未设置$").matcher(raw)).matches()) return m.group(1) + " subjects are not set up";
        if ((m = Pattern.compile("^有 (\\d+) 个科目还没设置携带物$").matcher(raw)).matches()) return m.group(1) + " subjects still need carry items";
        if ((m = Pattern.compile("^还有 (\\d+) 个科目未设置携带物$").matcher(raw)).matches()) return m.group(1) + " subjects are not set up";
        if ((m = Pattern.compile("^还有 (\\d+) 件物品没有装好$").matcher(raw)).matches()) return m.group(1) + " items still need packing";
        return raw;
    }
}
