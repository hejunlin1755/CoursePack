package com.kechengbao.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.window.OnBackInvokedDispatcher;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String PREFS = "course_pack_data_v1";
    private static final String KEY_COURSES = "courses";
    private static final String KEY_CHECKS = "checks";
    private static final String[] DAYS = {"星期一", "星期二", "星期三", "星期四", "星期五"};

    private final List<Course> courses = new ArrayList<>();
    private final Set<String> checks = new HashSet<>();
    private SharedPreferences prefs;
    private FrameLayout root;
    private FrameLayout contentHost;
    private LinearLayout bottomBar;
    private LinearLayout scheduleNav;
    private LinearLayout tomorrowNav;
    private FrameLayout scheduleIndicator;
    private FrameLayout tomorrowIndicator;
    private ImageView scheduleNavIcon;
    private ImageView tomorrowNavIcon;
    private TextView scheduleNavLabel;
    private TextView tomorrowNavLabel;
    private TextView fab;
    private int selectedDay;
    private int selectedTab;
    private LocalDate checklistDate;
    private int bg, surface, surfaceHigh, text, muted, primary, onPrimary, primaryContainer, onPrimaryContainer,
            secondary, onSecondary, tertiaryContainer, onTertiaryContainer, outline, success;
    private boolean dark;
    private final PathInterpolator emphasized = new PathInterpolator(.2f, 0f, 0f, 1f);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        resolveColors();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadData();
        int today = LocalDate.now().getDayOfWeek().getValue() - 1;
        selectedDay = Math.min(4, Math.max(0, today));
        buildShell();
        selectedTab = 1;
        showTomorrow(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (selectedTab == 0) showTomorrow(true); else finishAfterTransition(); }
            );
        }
    }

    @Override public void onBackPressed() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            if (selectedTab == 0) showTomorrow(true);
            else super.onBackPressed();
        }
    }

    private void resolveColors() {
        primary = systemColor(dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xFFD0BCFF : 0xFF6750A4);
        onPrimary = dark ? 0xFF27184A : Color.WHITE;
        primaryContainer = systemColor(dark ? "system_accent1_700" : "system_accent1_100", dark ? 0xFF4F378B : 0xFFEADDFF);
        onPrimaryContainer = systemColor(dark ? "system_accent1_100" : "system_accent1_900", dark ? 0xFFEADDFF : 0xFF21005D);
        secondary = systemColor(dark ? "system_accent2_700" : "system_accent2_100", dark ? 0xFF4A4458 : 0xFFE8DEF8);
        onSecondary = 0xFF21182C;
        tertiaryContainer = systemColor(dark ? "system_accent3_700" : "system_accent3_100", dark ? 0xFF633B48 : 0xFFFFD8E4);
        onTertiaryContainer = systemColor(dark ? "system_accent3_100" : "system_accent3_900", dark ? 0xFFFFD8E4 : 0xFF31111D);
        bg = dark ? 0xFF151218 : 0xFFFFF8FF;
        surface = dark ? 0xFF211F26 : 0xFFFFF8FF;
        surfaceHigh = dark ? 0xFF2B2930 : 0xFFF4EFF7;
        text = dark ? 0xFFEAE0EC : 0xFF1D1B20;
        muted = dark ? 0xFFCAC4D0 : 0xFF625B71;
        outline = dark ? 0xFF49454F : 0xFFE3DDE7;
        success = dark ? 0xFF9BD4A8 : 0xFF2F6B3D;
    }

    private int systemColor(String name, int fallback) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            int id = getResources().getIdentifier(name, "color", "android");
            if (id != 0) return getColor(id);
        }
        return fallback;
    }

    private void buildShell() {
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(bg);
        root = new FrameLayout(this);
        root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        contentHost = new FrameLayout(this);
        shell.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1));
        shell.addView(buildBottomBar(), lp(-1, 80));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        fab = label("添加课程", 14, onPrimary, true);
        fab.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable addIcon = getDrawable(R.drawable.ic_add);
        if (addIcon != null) addIcon.setTint(onPrimary);
        fab.setCompoundDrawablesWithIntrinsicBounds(addIcon, null, null, null);
        fab.setCompoundDrawablePadding(dp(8));
        fab.setPadding(dp(18), 0, dp(20), 0);
        fab.setContentDescription("添加课程");
        fab.setBackground(ripple(primary, 18));
        fab.setElevation(dp(6));
        fab.setOnClickListener(v -> showEditor(null));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(142), dp(56), Gravity.END | Gravity.BOTTOM);
        fp.setMargins(0, 0, dp(20), dp(94));
        root.addView(fab, fp);
        setContentView(root);
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(12), dp(6), dp(12), dp(4));
        bar.setBackground(shape(surfaceHigh, 0, 0, 0));
        tomorrowNav = navItem("明日", "打卡明天的课程", R.drawable.ic_today, true);
        scheduleNav = navItem("课表", "查看本周课程", R.drawable.ic_calendar, false);
        bar.addView(tomorrowNav, new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(scheduleNav, new LinearLayout.LayoutParams(0, -1, 1));
        scheduleNav.setOnClickListener(v -> showSchedule(true));
        tomorrowNav.setOnClickListener(v -> showTomorrow(true));
        bottomBar = bar;
        return bar;
    }

    private LinearLayout navItem(String title, String desc, int icon, boolean tomorrow) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        item.setContentDescription(desc);
        item.setClickable(true);
        item.setFocusable(true);
        item.setBackground(ripple(Color.TRANSPARENT, 20));

        FrameLayout indicator = new FrameLayout(this);
        indicator.setBackground(shape(Color.TRANSPARENT, 18, 0, 0));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(muted);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        indicator.addView(image, imageParams);
        item.addView(indicator, lp(64, 32));

        TextView label = label(title, 12, muted, true);
        label.setGravity(Gravity.CENTER);
        item.addView(label, margin(-1, 20, 0, 2, 0, 0));
        if (tomorrow) {
            tomorrowIndicator = indicator; tomorrowNavIcon = image; tomorrowNavLabel = label;
        } else {
            scheduleIndicator = indicator; scheduleNavIcon = image; scheduleNavLabel = label;
        }
        return item;
    }

    private void showSchedule(boolean animate) {
        if (selectedTab == 0 && animate) return;
        int old = selectedTab;
        selectedTab = 0;
        View page = buildSchedulePage();
        swapPage(page, old > selectedTab, animate);
        updateNav();
        fab.setVisibility(View.VISIBLE);
    }

    private void showTomorrow(boolean animate) {
        if (selectedTab == 1 && animate) return;
        int old = selectedTab;
        selectedTab = 1;
        View page = buildTomorrowPage();
        swapPage(page, old > selectedTab, animate);
        updateNav();
        fab.setVisibility(View.GONE);
    }

    private void swapPage(View next, boolean down, boolean animate) {
        View previous = contentHost.getChildCount() == 0 ? null : contentHost.getChildAt(0);
        contentHost.addView(next, new FrameLayout.LayoutParams(-1, -1));
        if (!animate || !motionEnabled()) {
            if (previous != null) contentHost.removeView(previous);
            return;
        }
        float start = dp(down ? -42 : 42);
        float exit = dp(down ? 28 : -28);
        next.setTranslationY(start);
        next.setAlpha(.25f);
        next.animate().translationY(0).alpha(1).setDuration(260).setInterpolator(emphasized).start();
        if (previous != null) previous.animate().translationY(exit).alpha(0).setDuration(180).setInterpolator(emphasized)
                .withEndAction(() -> contentHost.removeView(previous)).start();
    }

    private void updateNav() {
        boolean schedule = selectedTab == 0;
        scheduleIndicator.setBackground(shape(schedule ? secondary : Color.TRANSPARENT, 18, 0, 0));
        tomorrowIndicator.setBackground(shape(!schedule ? secondary : Color.TRANSPARENT, 18, 0, 0));
        scheduleNavLabel.setTextColor(schedule ? onSecondary : muted);
        tomorrowNavLabel.setTextColor(!schedule ? onSecondary : muted);
        scheduleNavIcon.setColorFilter(schedule ? onSecondary : muted);
        tomorrowNavIcon.setColorFilter(!schedule ? onSecondary : muted);
    }

    private View buildSchedulePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(22), dp(20), dp(112));
        TextView title = label("本周课表", 32, text, true);
        title.setLetterSpacing(-.02f);
        page.addView(title, lp(-1, -2));
        TextView sub = label("点课程即可修改时间和携带物", 15, muted, false);
        page.addView(sub, margin(-1, -2, 0, 4, 0, 18));
        page.addView(buildDaySelector(), margin(-1, 52, 0, 0, 0, 22));

        List<Course> day = coursesFor(selectedDay);
        LinearLayout sectionTitle = new LinearLayout(this);
        sectionTitle.setOrientation(LinearLayout.HORIZONTAL);
        sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
        sectionTitle.addView(label(DAYS[selectedDay], 22, text, true), new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView count = label(day.size() + " 节课", 13, onPrimaryContainer, true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(shape(primaryContainer, 16, 0, 0));
        sectionTitle.addView(count, lp(70, 34));
        page.addView(sectionTitle, margin(-1, 40, 0, 0, 0, 10));

        if (day.isEmpty()) page.addView(emptyState("这天没有课", "点“添加课程”排一节课。"));
        else page.addView(courseList(day, false));
        scroll.addView(page);
        return scroll;
    }

    private View buildDaySelector() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackground(shape(surfaceHigh, 22, 0, 0));
        for (int i = 0; i < DAYS.length; i++) {
            final int day = i;
            TextView chip = label(String.valueOf("一二三四五".charAt(i)), 14, i == selectedDay ? onPrimaryContainer : muted, true);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(ripple(i == selectedDay ? primaryContainer : Color.TRANSPARENT, 18));
            chip.setContentDescription(DAYS[i]);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                if (selectedDay != day) { boolean movingBack = day < selectedDay; selectedDay = day; swapPage(buildSchedulePage(), movingBack, true); }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 0) params.setMargins(dp(2), 0, 0, 0);
            row.addView(chip, params);
        }
        return row;
    }

    private View buildTomorrowPage() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        boolean weekend = tomorrow.getDayOfWeek().getValue() > 5;
        checklistDate = tomorrow;
        while (checklistDate.getDayOfWeek().getValue() > 5) checklistDate = checklistDate.plusDays(1);
        int d = checklistDate.getDayOfWeek().getValue() - 1;
        List<Course> list = coursesFor(d);
        String dateKey = checklistDate.toString();
        int done = 0;
        for (Course c : list) if (checks.contains(dateKey + ":" + c.id)) done++;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = column();
        page.setPadding(dp(20), dp(22), dp(20), dp(112));
        String week = chineseWeek(tomorrow.getDayOfWeek());
        TextView title = label("明天 · " + week, 32, text, true);
        title.setLetterSpacing(-.02f);
        page.addView(title);
        page.addView(label(tomorrow.format(DateTimeFormatter.ofPattern("M月d日")), 15, muted, false), margin(-1, -2, 0, 4, 0, 24));

        if (weekend) {
            LinearLayout rest = column();
            rest.setPadding(dp(18), dp(15), dp(18), dp(15));
            rest.setBackground(shape(tertiaryContainer, 22, 0, 0));
            rest.addView(label("明天没有课", 19, onTertiaryContainer, true));
            rest.addView(label("周末轻松一点，也可以先把下周一装好", 14, onTertiaryContainer, false), margin(-1, -2, 0, 4, 0, 0));
            page.addView(rest, margin(-1, -2, 0, 0, 0, 24));
            page.addView(label("提前准备 · " + chineseWeek(checklistDate.getDayOfWeek()), 24, text, true));
            page.addView(label(checklistDate.format(DateTimeFormatter.ofPattern("M月d日")) + " · " + list.size() + " 节课", 14, muted, false), margin(-1, -2, 0, 4, 0, 14));
        }

        String status;
        if (list.isEmpty()) status = "还没排课";
        else if (done == list.size()) status = "书包准备好了";
        else status = "还差 " + (list.size() - done) + " 科";

        LinearLayout hero = column();
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));
        hero.setBackground(shape(primaryContainer, 30, 0, 0));
        hero.addView(label("书包进度", 13, onPrimaryContainer, true));
        hero.addView(label(status, 26, onPrimaryContainer, true), margin(-1, -2, 0, 6, 0, 0));
        if (!list.isEmpty()) {
            hero.addView(progressSegments(list.size(), done), margin(-1, 10, 0, 16, 0, 0));
            hero.addView(label(done + " / " + list.size() + " 已装好", 13, onPrimaryContainer, false), margin(-1, -2, 0, 10, 0, 0));
        }
        page.addView(hero, margin(-1, -2, 0, 0, 0, 22));

        if (!list.isEmpty()) {
            page.addView(label("课程清单", 20, text, true));
            page.addView(label("勾选左侧完成打卡，点课程可修改携带物", 13, muted, false), margin(-1, -2, 0, 4, 0, 12));
        }

        if (list.isEmpty()) page.addView(emptyState("还没有课程", "去课表页添加课程，再回来准备书包。"));
        else page.addView(courseList(list, true));
        scroll.addView(page);
        return scroll;
    }

    private View progressSegments(int total, int done) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < total; i++) {
            View segment = new View(this);
            segment.setBackground(shape(i < done ? primary : secondary, 4, 0, 0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(8), 1);
            if (i > 0) p.setMargins(dp(5), 0, 0, 0);
            row.addView(segment, p);
        }
        row.setContentDescription("已完成 " + done + " 节，共 " + total + " 节");
        return row;
    }

    private View courseList(List<Course> list, boolean checkMode) {
        LinearLayout group = column();
        group.setBackground(shape(surfaceHigh, 26, 0, 0));
        group.setClipToOutline(true);
        for (int i = 0; i < list.size(); i++) {
            group.addView(courseRow(list.get(i), checkMode));
            if (i < list.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(outline);
                group.addView(divider, margin(-1, 1, checkMode ? 74 : 72, 0, 16, 0));
            }
        }
        return group;
    }

    private View courseRow(Course c, boolean checkMode) {
        boolean checked = checkMode && checklistDate != null && checks.contains(checklistDate + ":" + c.id);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(8), dp(10));
        row.setMinimumHeight(dp(84));
        row.setBackground(ripple(checked ? (dark ? 0xFF203827 : 0xFFDDF2E2) : surfaceHigh, 0));

        View token;
        if (checkMode) {
            FrameLayout target = new FrameLayout(this);
            target.setBackground(ripple(Color.TRANSPARENT, 24));
            target.setContentDescription((checked ? "取消打卡 " : "打卡 ") + c.subject);
            target.setClickable(true);
            target.setFocusable(true);
            target.setOnClickListener(v -> toggleCheck(c, row));
            ImageView state = new ImageView(this);
            state.setPadding(dp(5), dp(5), dp(5), dp(5));
            if (checked) {
                state.setImageResource(R.drawable.ic_check);
                state.setColorFilter(dark ? 0xFF14371D : Color.WHITE);
                state.setBackground(shape(success, 14, 0, 0));
            } else {
                state.setBackground(shape(surfaceHigh, 14, 2, primary));
            }
            target.addView(state, new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
            token = target;
        } else {
            TextView periodToken = label(String.valueOf(c.period), 15, onPrimaryContainer, true);
            periodToken.setGravity(Gravity.CENTER);
            periodToken.setBackground(shape(primaryContainer, 14, 0, 0));
            token = periodToken;
        }
        row.addView(token, margin(48, 48, 0, 0, 10, 0));

        LinearLayout body = column();
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setClickable(true);
        body.setFocusable(true);
        body.setContentDescription("编辑 " + c.subject + " 的携带物");
        body.setOnClickListener(v -> showEditor(c));
        TextView subject = label(c.subject, 17, checked ? muted : text, true);
        subject.setMaxLines(2);
        body.addView(subject);
        boolean missing = c.items.trim().isEmpty();
        String itemCopy = missing ? "添加携带物" : c.items;
        TextView time = label(c.time, 12, primary, true);
        body.addView(time, margin(-1, -2, 0, 3, 0, 0));
        TextView items = label(itemCopy, 13, missing ? primary : muted, missing);
        items.setMaxLines(2);
        body.addView(items, margin(-1, -2, 0, 2, 0, 0));
        row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(muted);
        arrow.setPadding(dp(8), dp(12), dp(8), dp(12));
        arrow.setContentDescription("编辑课程");
        arrow.setOnClickListener(v -> showEditor(c));
        row.addView(arrow, lp(40, 48));
        if (!checkMode) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription("编辑 " + c.subject + "，" + itemCopy + "，" + c.time);
            row.setOnClickListener(v -> showEditor(c));
        }
        return row;
    }

    private void toggleCheck(Course c, View card) {
        LocalDate date = checklistDate == null ? LocalDate.now().plusDays(1) : checklistDate;
        String key = date + ":" + c.id;
        if (!checks.add(key)) checks.remove(key);
        saveChecks();
        if (motionEnabled()) {
            card.animate().scaleX(.96f).scaleY(.96f).setDuration(90).withEndAction(() ->
                    card.animate().scaleX(1).scaleY(1).setDuration(300).setInterpolator(emphasized).withEndAction(() -> showTomorrow(false)).start()).start();
        } else showTomorrow(false);
    }

    private void showEditor(Course existing) {
        final boolean editing = existing != null;
        final int returnTab = selectedTab;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = column();
        sheet.setPadding(dp(24), dp(14), dp(24), dp(28));
        sheet.setBackground(shape(surface, 30, 0, 0));
        TextView handle = new TextView(this);
        handle.setBackground(shape(muted, 3, 0, 0));
        LinearLayout handleWrap = new LinearLayout(this);
        handleWrap.setGravity(Gravity.CENTER);
        handleWrap.addView(handle, lp(36, 5));
        sheet.addView(handleWrap, margin(-1, 22, 0, 0, 0, 12));
        sheet.addView(label(editing ? "课程详情" : "添加课程", 27, text, true), margin(-1, -2, 0, 0, 0, 4));
        sheet.addView(label("科目和携带物会同步到明日清单", 14, muted, false), margin(-1, -2, 0, 0, 0, 18));

        EditText subject = input("科目名称", editing ? existing.subject : "");
        EditText items = input("要带什么？例如：课本、练习册", editing ? existing.items : "");
        EditText time = input("上课时间", editing ? existing.time : "08:50–09:30");
        int[] editorDay = {editing ? existing.day : selectedDay};
        View day = buildEditorDaySelector(editorDay);

        EditText period = input("第几节（1–12）", editing ? String.valueOf(existing.period) : "1");
        period.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sheet.addView(fieldGroup("科目", subject), margin(-1, -2, 0, 0, 0, 12));
        sheet.addView(fieldGroup("要带什么", items), margin(-1, -2, 0, 0, 0, 12));
        sheet.addView(fieldGroup("星期", day), margin(-1, -2, 0, 0, 0, 12));
        LinearLayout timing = new LinearLayout(this);
        timing.setOrientation(LinearLayout.HORIZONTAL);
        timing.addView(fieldGroup("节次", period), new LinearLayout.LayoutParams(0, -2, 1));
        timing.addView(fieldGroup("上课时间", time), margin(0, -2, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) timing.getChildAt(1).getLayoutParams()).weight = 2;
        sheet.addView(timing, margin(-1, -2, 0, 0, 0, 18));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (editing) {
            TextView delete = button("删除", false);
            delete.setTextColor(dark ? 0xFFFFB4AB : 0xFFBA1A1A);
            delete.setOnClickListener(v -> { courses.remove(existing); saveCourses(); dialog.dismiss(); if (returnTab == 1) showTomorrow(false); else showSchedule(false); });
            actions.addView(delete, margin(88, 52, 0, 0, 10, 0));
        }
        TextView save = button("保存课程", true);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));
        sheet.addView(actions);

        save.setOnClickListener(v -> {
            String name = subject.getText().toString().trim();
            if (name.isEmpty()) { subject.setError("请填写科目名称"); subject.requestFocus(); return; }
            int p;
            try { p = Math.max(1, Math.min(12, Integer.parseInt(period.getText().toString()))); }
            catch (Exception e) { period.setError("请输入 1–12"); return; }
            Course target = editing ? existing : new Course(UUID.randomUUID().toString(), editorDay[0], p, name, time.getText().toString().trim(), items.getText().toString().trim());
            target.day = editorDay[0]; target.period = p; target.subject = name;
            target.time = time.getText().toString().trim(); target.items = items.getText().toString().trim();
            if (!editing) courses.add(target);
            selectedDay = target.day;
            saveCourses(); dialog.dismiss(); if (returnTab == 1) showTomorrow(false); else showSchedule(false);
        });

        ScrollView container = new ScrollView(this);
        container.addView(sheet);
        dialog.setContentView(container);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(-1, -2);
            w.setGravity(Gravity.BOTTOM);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (motionEnabled()) w.setWindowAnimations(com.kechengbao.app.R.style.Animation_KeChengBao_Sheet);
        }
        dialog.setOnShowListener(x -> { Window win = dialog.getWindow(); if (win != null) win.setLayout(-1, -2); });
        dialog.show();
    }

    private View buildEditorDaySelector(int[] selected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        row.setBackground(shape(surfaceHigh, 18, 0, 0));
        TextView[] choices = new TextView[DAYS.length];
        for (int i = 0; i < DAYS.length; i++) {
            final int day = i;
            TextView choice = label(String.valueOf("一二三四五".charAt(i)), 14, i == selected[0] ? onPrimaryContainer : muted, true);
            choice.setGravity(Gravity.CENTER);
            choice.setContentDescription(DAYS[i]);
            choice.setBackground(ripple(i == selected[0] ? primaryContainer : Color.TRANSPARENT, 15));
            choice.setClickable(true);
            choice.setFocusable(true);
            choice.setOnClickListener(v -> {
                selected[0] = day;
                for (int n = 0; n < choices.length; n++) {
                    choices[n].setTextColor(n == day ? onPrimaryContainer : muted);
                    choices[n].setBackground(ripple(n == day ? primaryContainer : Color.TRANSPARENT, 15));
                }
            });
            choices[i] = choice;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) params.setMargins(dp(2), 0, 0, 0);
            row.addView(choice, params);
        }
        return row;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setTextSize(16); e.setTextColor(text); e.setHintTextColor(muted);
        e.setSingleLine(true); e.setPadding(dp(16), 0, dp(16), 0); e.setBackground(ripple(surfaceHigh, 15));
        return e;
    }

    private LinearLayout fieldGroup(String title, View field) {
        LinearLayout group = column();
        group.addView(label(title, 13, muted, true), margin(-1, -2, 2, 0, 0, 7));
        group.addView(field, lp(-1, 54));
        return group;
    }

    private TextView button(String title, boolean filled) {
        TextView b = label(title, 15, filled ? onPrimary : text, true);
        b.setText(title); b.setTextSize(15); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setFocusable(true);
        b.setTextColor(filled ? onPrimary : text); b.setBackground(ripple(filled ? primary : surfaceHigh, 18));
        return b;
    }

    private View emptyState(String title, String copy) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(26), dp(42), dp(26), dp(42));
        box.setBackground(shape(surfaceHigh, 28, 0, 0));
        box.addView(label(title, 23, text, true));
        TextView c = label(copy, 15, muted, false); c.setGravity(Gravity.CENTER);
        box.addView(c, margin(-1, -2, 0, 8, 0, 0));
        return box;
    }

    private List<Course> coursesFor(int day) {
        List<Course> out = new ArrayList<>();
        for (Course c : courses) if (c.day == day) out.add(c);
        out.sort(Comparator.comparingInt(c -> c.period));
        return out;
    }

    private boolean motionEnabled() {
        try { return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f; }
        catch (Exception ignored) { return true; }
    }

    private void loadData() {
        String raw = prefs.getString(KEY_COURSES, "");
        if (raw.isEmpty()) seedCourses(); else {
            for (String line : raw.split("\\n")) { Course c = Course.decode(line); if (c != null) courses.add(c); }
        }
        checks.addAll(prefs.getStringSet(KEY_CHECKS, new HashSet<>()));
    }

    private void seedCourses() {
        addDay(0, new String[][]{{"综合科学","08:50–09:30"},{"历史","09:35–10:15"},{"地理","10:20–11:00"},{"人工智能","11:05–11:45"},{"中文阅读","11:50–12:30"},{"英语","14:00–14:40"},{"数学","15:00–15:40"},{"数学保底","15:45–16:25"}});
        addDay(1, new String[][]{{"英语","08:50–09:30"},{"体育","09:35–10:15"},{"体育","10:20–11:00"},{"公民","11:05–11:45"},{"公民","11:50–12:30"},{"数学","14:00–14:40"},{"班务","15:00–15:40"},{"班务","15:45–16:25"}});
        addDay(2, new String[][]{{"综合科学","08:50–09:30"},{"综合科学","09:35–10:15"},{"中文","10:20–11:00"},{"资讯科技","11:05–11:45"},{"历史","11:50–12:30"},{"地理","14:00–14:40"},{"数学","15:00–15:40"},{"英语保底","15:45–16:25"}});
        addDay(3, new String[][]{{"英语","08:50–09:30"},{"中文","09:35–10:15"},{"中文","10:20–11:00"},{"英语阅读与听力","11:05–11:45"},{"综合科学","11:50–12:30"},{"数学","14:00–14:40"},{"音乐","15:00–15:40"},{"余暇活动","15:45–17:10"}});
        addDay(4, new String[][]{{"英语","08:50–09:30"},{"英语","09:35–10:15"},{"数学实践与阅读","10:20–11:00"},{"视觉艺术","11:05–11:45"},{"应用科学与科技","11:50–12:30"},{"中文","14:00–14:40"},{"数学","15:00–15:40"}});
        saveCourses();
    }

    private void addDay(int day, String[][] entries) {
        for (int i = 0; i < entries.length; i++) courses.add(new Course("seed-" + day + "-" + (i + 1), day, i + 1, entries[i][0], entries[i][1], ""));
    }

    private void saveCourses() {
        StringBuilder b = new StringBuilder();
        for (Course c : courses) { if (b.length() > 0) b.append('\n'); b.append(c.encode()); }
        prefs.edit().putString(KEY_COURSES, b.toString()).apply();
    }

    private void saveChecks() { prefs.edit().putStringSet(KEY_CHECKS, new HashSet<>(checks)).apply(); }

    private String chineseWeek(DayOfWeek day) {
        String[] names = {"星期一","星期二","星期三","星期四","星期五","星期六","星期日"};
        return names[day.getValue() - 1];
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView label(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setGravity(Gravity.CENTER_VERTICAL); v.setIncludeFontPadding(false); return v;
    }
    private GradientDrawable shape(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke > 0) d.setStroke(dp(stroke), strokeColor); return d;
    }
    private android.graphics.drawable.RippleDrawable ripple(int color, int radius) {
        return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((primary & 0x00FFFFFF) | 0x28000000), shape(color, radius, 0, 0), null);
    }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private LinearLayout.LayoutParams margin(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = lp(w, h); p.setMargins(dp(l), dp(t), dp(r), dp(b)); return p;
    }
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }

    static class Course {
        String id, subject, time, items; int day, period;
        Course(String id, int day, int period, String subject, String time, String items) { this.id=id; this.day=day; this.period=period; this.subject=subject; this.time=time; this.items=items; }
        String encode() { return b64(id)+"|"+day+"|"+period+"|"+b64(subject)+"|"+b64(time)+"|"+b64(items); }
        static Course decode(String line) {
            try { String[] p=line.split("\\|",-1); return new Course(un64(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),un64(p[3]),un64(p[4]),un64(p[5])); }
            catch(Exception e){ return null; }
        }
        static String b64(String s){ return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP|Base64.URL_SAFE); }
        static String un64(String s){ return new String(Base64.decode(s,Base64.NO_WRAP|Base64.URL_SAFE),StandardCharsets.UTF_8); }
    }
}
