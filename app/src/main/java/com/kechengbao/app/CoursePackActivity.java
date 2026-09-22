package com.kechengbao.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CoursePackActivity extends Activity {
    static final int PREPARATION_SWITCH_HOUR = 12;
    private static final String PREFS = "course_pack_data_v1";
    private static final String KEY_LEGACY_COURSES = "courses";
    private static final String KEY_SUBJECTS = "subjects_v2";
    private static final String KEY_ENTRIES = "entries_v2";
    private static final String KEY_CHECKS = "item_checks_v2";
    private static final String KEY_DAILY_ITEMS = "daily_items_v1";
    private static final String KEY_DAILY_CHECKS = "daily_checks_v1";
    private static final String KEY_TIME_SLOT_PREFIX = "time_slot_v1_";
    private static final String KEY_IMPORT_SUCCESS = "import_success_v1";
    private static final String KEY_ONBOARDING_DONE = "settings_onboarding_done_v1";
    private static final String KEY_WHATS_NEW_VERSION = "settings_whats_new_version_v1";
    private static final int CURRENT_VERSION_CODE = 34;
    private static final int REQUEST_EXPORT_BACKUP = 601;
    private static final int REQUEST_IMPORT_BACKUP = 602;
    private static final int REQUEST_SCHEDULE_IMAGE = 603;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 604;
    private static final int BACKUP_FORMAT_VERSION = 1;
    private static final int MAX_BACKUP_BYTES = 2 * 1024 * 1024;
    private static final String[] DAYS = {"星期一", "星期二", "星期三", "星期四", "星期五"};

    private final List<Subject> subjects = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<DailyItem> dailyItems = new ArrayList<>();
    private final Set<String> checks = new HashSet<>();
    private final Set<String> dailyChecks = new HashSet<>();
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final FrameLayout[] navIndicators = new FrameLayout[3];
    private final ImageView[] navIcons = new ImageView[3];
    private final TextView[] navLabels = new TextView[3];
    private SharedPreferences prefs;
    private FrameLayout root, contentHost;
    private View completionOverlay;
    private FrameLayout onboardingOverlay, onboardingStage, onboardingNext;
    private LinearLayout onboardingDots, reminderToggleRow;
    private TextView onboardingBack, onboardingStepLabel, onboardingTimeLabel, onboardingActionTitle;
    private ImageView onboardingNextIcon;
    private float[] onboardingFabCorners={32f,32f,32f,32f};
    private float onboardingFabRotation;
    private ValueAnimator onboardingFabCornerAnimator,onboardingFabRotationAnimator;
    private View reminderTimeRow;
    private int onboardingStep;
    private boolean pendingReminderEnable, pendingOnboardingPermission, onboardingLaunchedFromSettings;
    private TextView fab, tomorrowStatus, tomorrowProgressLabel, clearChecksButton;
    private Dialog scheduleImportDialog;
    private EditText scheduleImportInput;
    private TextView scheduleImportImageAction;
    private TextView scheduleImportError;
    private LinearLayout tomorrowProgressRow;
    private int selectedDay, selectedTab = 1, tomorrowTotalItems, tomorrowMissingSubjects;
    private int settingsReturnTab = 1;
    private int settingsScrollY;
    private boolean animateSettingsRestore;
    private LocalDate checklistDate;
    private int pageTransitionId;
    private int bg, surface, surfaceHigh, text, muted, primary, onPrimary, primaryContainer, onPrimaryContainer,
            secondary, onSecondary, tertiaryContainer, onTertiaryContainer, outline, success,
            warningContainer, onWarningContainer, warningAccent;
    private boolean dark;
    private boolean english;
    private final PathInterpolator emphasized = new PathInterpolator(.2f, 0f, 0f, 1f);

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppText.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean freshInstall = prefs.getAll().isEmpty();
        english = AppText.isEnglish(this);
        String themeMode = prefs.getString(AppText.KEY_THEME, "system");
        boolean systemDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        dark = "dark".equals(themeMode) || ("system".equals(themeMode) && systemDark);
        resolveColors();
        loadData();
        int today = LocalDate.now().getDayOfWeek().getValue() - 1;
        selectedDay = Math.min(4, Math.max(0, today));
        buildShell();
        int startTab = state == null ? 1 : state.getInt("selected_tab", 1);
        settingsReturnTab = state == null ? 1 : state.getInt("settings_return_tab", 1);
        settingsScrollY = state == null ? 0 : state.getInt("settings_scroll_y", 0);
        animateSettingsRestore = state != null && state.getBoolean("animate_settings_restore", false);
        if (startTab == 3) showSettings(false);
        else if (startTab == 0) showSchedule(false);
        else if (startTab == 2) showSubjects(false);
        else showTomorrow(false);
        if(animateSettingsRestore&&motionEnabled()){contentHost.setAlpha(.15f);contentHost.setTranslationY(dp(14));contentHost.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(emphasized).start();animateSettingsRestore=false;}
        if (prefs.getBoolean(KEY_IMPORT_SUCCESS, false)) {
            prefs.edit().remove(KEY_IMPORT_SUCCESS).apply();
            root.postDelayed(() -> showTransientMessage("备份已导入"), 260);
        }
        PackingReminderReceiver.schedule(this);
        if(state==null){if(freshInstall&&!prefs.getBoolean(KEY_ONBOARDING_DONE,false))root.postDelayed(this::showFirstRun,220);else if(prefs.getInt(KEY_WHATS_NEW_VERSION,0)<CURRENT_VERSION_CODE)root.postDelayed(this::showWhatsNew,260);}
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if(onboardingOverlay!=null){handleOnboardingBack();return;}if (selectedTab == 3) returnFromSettings(true); else if (selectedTab != 1) showTomorrow(true); else finishAfterTransition(); });
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selected_tab", selectedTab);
        outState.putInt("settings_return_tab", settingsReturnTab);
        outState.putInt("settings_scroll_y", settingsScrollY);
        outState.putBoolean("animate_settings_restore", animateSettingsRestore);
        super.onSaveInstanceState(outState);
    }

    @Override public void onBackPressed() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            if(onboardingOverlay!=null){handleOnboardingBack();return;}
            if (selectedTab == 3) returnFromSettings(true);
            else if (selectedTab != 1) showTomorrow(true); else super.onBackPressed();
        }
    }

    @Override protected void onResume(){
        super.onResume();
        TomorrowWidgetProvider.refreshAll(this);
    }

    private void resolveColors() {
        String palette = prefs.getString(AppText.KEY_PALETTE, "dynamic");
        if ("dynamic".equals(palette)) {
            primary = systemColor(dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xFFD0BCFF : 0xFF6750A4);
            primaryContainer = systemColor(dark ? "system_accent1_700" : "system_accent1_100", dark ? 0xFF4F378B : 0xFFEADDFF);
            onPrimaryContainer = systemColor(dark ? "system_accent1_100" : "system_accent1_900", dark ? 0xFFEADDFF : 0xFF21005D);
            secondary = systemColor(dark ? "system_accent2_700" : "system_accent2_100", dark ? 0xFF4A4458 : 0xFFE8DEF8);
            tertiaryContainer = systemColor(dark ? "system_accent3_700" : "system_accent3_100", dark ? 0xFF633B48 : 0xFFFFD8E4);
            onTertiaryContainer = systemColor(dark ? "system_accent3_100" : "system_accent3_900", dark ? 0xFFFFD8E4 : 0xFF31111D);
        } else if ("ocean".equals(palette)) {
            primary = dark ? 0xFFA8C7FA : 0xFF3F5F90; primaryContainer = dark ? 0xFF244776 : 0xFFD7E3FF;
            onPrimaryContainer = dark ? 0xFFD7E3FF : 0xFF001B3F; secondary = dark ? 0xFF3B485B : 0xFFD9E3F7;
            tertiaryContainer = dark ? 0xFF4D3E5F : 0xFFF0DBFF; onTertiaryContainer = dark ? 0xFFF0DBFF : 0xFF271335;
        } else if ("forest".equals(palette)) {
            primary = dark ? 0xFFA8D5B7 : 0xFF3E6652; primaryContainer = dark ? 0xFF244D3A : 0xFFC0EED0;
            onPrimaryContainer = dark ? 0xFFC0EED0 : 0xFF002113; secondary = dark ? 0xFF3D4B42 : 0xFFD4E8DA;
            tertiaryContainer = dark ? 0xFF324B50 : 0xFFBCEBF1; onTertiaryContainer = dark ? 0xFFBCEBF1 : 0xFF001F24;
        } else {
            primary = dark ? 0xFFD0BCFF : 0xFF6750A4; primaryContainer = dark ? 0xFF4F378B : 0xFFEADDFF;
            onPrimaryContainer = dark ? 0xFFEADDFF : 0xFF21005D; secondary = dark ? 0xFF4A4458 : 0xFFE8DEF8;
            tertiaryContainer = dark ? 0xFF633B48 : 0xFFFFD8E4; onTertiaryContainer = dark ? 0xFFFFD8E4 : 0xFF31111D;
        }
        onPrimary = dark ? 0xFF172032 : Color.WHITE;
        onSecondary = dark ? 0xFFF1EAF5 : 0xFF21182C;
        bg = dark ? 0xFF151218 : 0xFFFFF8FF; surface = dark ? 0xFF211F26 : 0xFFFFF8FF;
        surfaceHigh = dark ? 0xFF2B2930 : 0xFFF4EFF7; text = dark ? 0xFFEAE0EC : 0xFF1D1B20;
        muted = dark ? 0xFFCAC4D0 : 0xFF625B71; outline = dark ? 0xFF49454F : 0xFFE3DDE7;
        success = dark ? 0xFF9BD4A8 : 0xFF2F6B3D;
        warningContainer = dark ? 0xFF3B2D17 : 0xFFFFEBC8; onWarningContainer = dark ? 0xFFFFE1AD : 0xFF3D2B09;
        warningAccent = dark ? 0xFFFFB95C : 0xFF8A5A00;
    }

    private int systemColor(String name, int fallback) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            int id = getResources().getIdentifier(name, "color", "android"); if (id != 0) return getColor(id);
        }
        return fallback;
    }

    private void buildShell() {
        if (android.os.Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(bg);
        root = new FrameLayout(this); root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(0, bars.top, 0, bars.bottom);
            } else v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout shell = column(); contentHost = new FrameLayout(this);
        shell.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1)); shell.addView(buildBottomBar(), lp(-1, 80));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        fab = label("", 14, onPrimary, true); fab.setGravity(Gravity.CENTER); fab.setPadding(dp(18), 0, dp(20), 0);
        fab.setBackground(ripple(primary, 18)); fab.setElevation(dp(6));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(154), dp(56), Gravity.END | Gravity.BOTTOM);
        fp.setMargins(0, 0, dp(20), dp(94)); root.addView(fab, fp); setContentView(root);
        if (android.os.Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int appearance = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(dark ? 0 : appearance, appearance);
        }
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(6), dp(8), dp(4)); bar.setBackgroundColor(surfaceHigh);
        navItems[1] = navItem(1, "准备", "查看当前要准备的携带物", R.drawable.ic_today);
        navItems[0] = navItem(0, "课表", "查看本周课表", R.drawable.ic_calendar);
        navItems[2] = navItem(2, "科目", "管理科目和携带物", R.drawable.ic_subjects);
        bar.addView(navItems[1], new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(navItems[0], new LinearLayout.LayoutParams(0, -1, 1));
        bar.addView(navItems[2], new LinearLayout.LayoutParams(0, -1, 1));
        navItems[1].setOnClickListener(v -> showTomorrow(true)); navItems[0].setOnClickListener(v -> showSchedule(true));
        navItems[2].setOnClickListener(v -> showSubjects(true)); return bar;
    }

    private LinearLayout navItem(int index, String title, String desc, int icon) {
        LinearLayout item = column(); item.setGravity(Gravity.CENTER); item.setContentDescription(L(desc)); item.setClickable(true);
        item.setFocusable(true); item.setBackground(ripple(Color.TRANSPARENT, 18));
        FrameLayout indicator = new FrameLayout(this); ImageView image = new ImageView(this); image.setImageResource(icon); image.setColorFilter(muted);
        indicator.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)); item.addView(indicator, lp(58, 32));
        TextView label = label(title, 12, muted, true); label.setGravity(Gravity.CENTER); item.addView(label, margin(-1, 20, 0, 2, 0, 0));
        navIndicators[index] = indicator; navIcons[index] = image; navLabels[index] = label; return item;
    }

    private void showTomorrow(boolean animate) { if (selectedTab == 1 && animate) return; int old=selectedTab; selectedTab=1; swapPage(buildTomorrowPage(), old>1, animate); updateChrome(); }
    private void showSchedule(boolean animate) { if (selectedTab == 0 && animate) return; int old=selectedTab; selectedTab=0; swapPage(buildSchedulePage(), old>0, animate); updateChrome(); }
    private void showSubjects(boolean animate) { if (selectedTab == 2 && animate) return; int old=selectedTab; selectedTab=2; swapPage(buildSubjectsPage(), old>2, animate); updateChrome(); }
    private void showSettings(boolean animate) { if (selectedTab == 3 && animate) return; if (selectedTab >= 0 && selectedTab <= 2){settingsReturnTab = selectedTab;settingsScrollY=0;} selectedTab = 3; swapPage(buildSettingsPage(), false, animate); updateChrome(); }
    private void returnFromSettings(boolean animate) { if (settingsReturnTab == 0) showSchedule(animate); else if (settingsReturnTab == 2) showSubjects(animate); else showTomorrow(animate); }

    private void updateChrome() {
        for (int i=0;i<3;i++) { boolean active=selectedTab==i; navIndicators[i].setBackground(shape(active?secondary:Color.TRANSPARENT,18,0,0)); navIcons[i].setColorFilter(active?onSecondary:muted); navLabels[i].setTextColor(active?onSecondary:muted); }
        if (selectedTab==1 || selectedTab==3) fab.setVisibility(View.GONE); else {
            fab.setVisibility(View.VISIBLE); String title=selectedTab==0?"排一节课":"新建科目"; fab.setText(L(title)); fab.setContentDescription(L(title));
            android.graphics.drawable.Drawable add=getDrawable(R.drawable.ic_add); if(add!=null)add.setTint(onPrimary);
            fab.setCompoundDrawablesWithIntrinsicBounds(add,null,null,null); fab.setCompoundDrawablePadding(dp(8));
            fab.setOnClickListener(v->{if(selectedTab==0)showScheduleEditor(null);else showSubjectEditor(null);});
        }
    }

    private void swapPage(View next, boolean down, boolean animate) {
        int transition=++pageTransitionId;
        View previous=contentHost.getChildCount()==0?null:contentHost.getChildAt(contentHost.getChildCount()-1);
        for(int i=contentHost.getChildCount()-2;i>=0;i--){View stale=contentHost.getChildAt(i);stale.animate().cancel();contentHost.removeViewAt(i);}
        if(previous!=null){previous.animate().cancel();previous.setTranslationY(0);previous.setAlpha(1f);}
        contentHost.addView(next,new FrameLayout.LayoutParams(-1,-1));
        if(!animate||!motionEnabled()){if(previous!=null&&previous.getParent()==contentHost)contentHost.removeView(previous);return;}
        next.setTranslationY(dp(down?-42:42));next.setAlpha(.3f);
        next.animate().translationY(0).alpha(1).setDuration(240).setInterpolator(emphasized).withEndAction(()->finishPageTransition(next,transition)).start();
        if(previous!=null){View outgoing=previous;outgoing.animate().translationY(dp(down?26:-26)).alpha(0).setDuration(170).setInterpolator(emphasized).withEndAction(()->{if(outgoing.getParent()==contentHost)contentHost.removeView(outgoing);}).start();}
    }

    private void finishPageTransition(View current,int transition){if(transition!=pageTransitionId||current.getParent()!=contentHost)return;for(int i=contentHost.getChildCount()-1;i>=0;i--){View child=contentHost.getChildAt(i);if(child!=current){child.animate().cancel();contentHost.removeViewAt(i);}}current.setTranslationY(0);current.setAlpha(1f);}

    private void addPageHeader(LinearLayout page,String title,String subtitle,int bottomMargin){
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=label(title,32,text,true);heading.setLetterSpacing(-.02f);top.addView(heading,new LinearLayout.LayoutParams(0,dp(52),1));
        if(selectedTab==2){ImageView settings=settingsButton();top.addView(settings,lp(48,48));}page.addView(top);
        page.addView(label(subtitle,15,muted,false),margin(-1,-2,0,2,0,bottomMargin));
    }

    private ImageView settingsButton(){ImageView button=iconButton(R.drawable.ic_settings,"设置");button.setColorFilter(onPrimaryContainer);button.setPadding(dp(12),dp(12),dp(12),dp(12));button.setBackground(ripple(primaryContainer,18));button.setOnClickListener(v->showSettings(true));return button;}

    private View buildSettingsPage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=column();page.setPadding(dp(20),dp(18),dp(20),dp(48));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);ImageView back=iconButton(R.drawable.ic_back,"返回");back.setColorFilter(text);back.setOnClickListener(v->returnFromSettings(true));top.addView(back,lp(48,48));TextView title=label("设置",32,text,true);title.setLetterSpacing(-.02f);top.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));page.addView(top);
        page.addView(label("让课程包更符合你的使用习惯",15,muted,false),margin(-1,-2,48,-2,0,22));

        LocalDate target=resolveChecklistDate();LinkedHashMap<Subject,List<Entry>> grouped=groupBySubject(entriesFor(target.getDayOfWeek().getValue()-1));List<DailyItem> activeDaily=dailyItemsFor(target);int total=totalItems(grouped)+activeDaily.size();int done=countDoneItems(grouped)+countDoneDailyItems(activeDaily,target);int itemCount=dailyItems.size();for(Subject subject:subjects)itemCount+=subject.items.size();
        LinearLayout overview=column();overview.setPadding(dp(20),dp(16),dp(20),dp(18));overview.setBackground(shape(primaryContainer,28,0,0));overview.addView(label("概览",13,onPrimaryContainer,true));overview.addView(label(subjects.size()+" 个科目 · "+entries.size()+" 节周课程",25,onPrimaryContainer,true),margin(-1,-2,0,8,0,0));overview.addView(label(itemCount+" 件携带物 · 正在准备 "+formatDate(target),14,onPrimaryContainer,false),margin(-1,-2,0,5,0,0));if(total>0){LinearLayout segments=new LinearLayout(this);segments.setOrientation(LinearLayout.HORIZONTAL);buildProgressSegments(segments,total,done);overview.addView(segments,margin(-1,10,0,16,0,0));overview.addView(label(done+" / "+total+" 已装好",13,onPrimaryContainer,true),margin(-1,-2,0,9,0,0));}page.addView(overview,margin(-1,-2,0,0,0,26));

        page.addView(settingsSectionTitle("外观"));
        String theme=prefs.getString(AppText.KEY_THEME,"system");page.addView(settingsChoiceCard("主题模式",new String[]{"跟随系统","浅色","深色"},new String[]{"system","light","dark"},theme,value->applySettingAndRecreate(scroll,AppText.KEY_THEME,value)),margin(-1,-2,0,8,0,12));
        String palette=prefs.getString(AppText.KEY_PALETTE,"dynamic");page.addView(settingsChoiceCard("强调色",new String[]{"系统动态色","紫罗兰","海洋蓝","森林绿"},new String[]{"dynamic","violet","ocean","forest"},palette,value->applySettingAndRecreate(scroll,AppText.KEY_PALETTE,value)),margin(-1,-2,0,0,0,24));

        page.addView(settingsSectionTitle("语言"));String language=prefs.getString(AppText.KEY_LANGUAGE,"system");page.addView(settingsChoiceCard("应用语言",new String[]{"跟随系统","简体中文","English"},new String[]{"system","zh","en"},language,value->applySettingAndRecreate(scroll,AppText.KEY_LANGUAGE,value)),margin(-1,-2,0,0,0,24));

        page.addView(settingsSectionTitle("交互"));boolean[] haptics={prefs.getBoolean(AppText.KEY_HAPTICS,true)};LinearLayout hapticRow=toggleRow("触感反馈",haptics);hapticRow.setOnClickListener(v->{haptics[0]=!haptics[0];prefs.edit().putBoolean(AppText.KEY_HAPTICS,haptics[0]).apply();updateToggleRow(hapticRow,haptics[0]);if(haptics[0])vibrateTap(false);});page.addView(hapticRow,margin(-1,56,0,0,0,10));page.addView(reminderSettingsCard(),margin(-1,-2,0,0,0,24));

        page.addView(settingsSectionTitle("数据"));page.addView(settingsDataCard(),margin(-1,-2,0,8,0,24));

        page.addView(settingsSectionTitle("关于"));LinearLayout about=column();about.setPadding(dp(8),dp(8),dp(8),dp(8));about.setBackground(shape(surfaceHigh,20,0,0));LinearLayout version=column();version.setPadding(dp(10),dp(7),dp(10),dp(10));version.addView(label("课程包 2.9.3",17,text,true));version.addView(label("数据仅保存在本机 · 无需联网",13,muted,false),margin(-1,-2,0,5,0,0));about.addView(version);about.addView(settingsActionRow(R.drawable.ic_today,"版本更新","查看 2.9.3 的新功能",this::showWhatsNew),lp(-1,68));about.addView(settingsActionRow(R.drawable.ic_subjects,"使用引导","重新查看第一次使用流程",this::showFirstRun),lp(-1,68));page.addView(about);scroll.addView(page);if(settingsScrollY>0)scroll.post(()->scroll.scrollTo(0,settingsScrollY));return scroll;
    }

    private View reminderSettingsCard(){
        boolean enabled=prefs.getBoolean(AppText.KEY_REMINDER_ENABLED,false);LinearLayout card=column();card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setBackground(shape(surfaceHigh,22,0,0));boolean[] state={enabled};LinearLayout toggle=toggleRow("书包提醒",state);reminderToggleRow=toggle;card.addView(toggle,lp(-1,56));View divider=new View(this);divider.setBackgroundColor(outline);card.addView(divider,margin(-1,1,56,0,12,0));View time=reminderTimeAction();reminderTimeRow=time;card.addView(time,lp(-1,68));toggle.setOnClickListener(v->{boolean next=!prefs.getBoolean(AppText.KEY_REMINDER_ENABLED,false);if(next&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingReminderEnable=true;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATION_PERMISSION);return;}setReminderEnabled(next);});updateReminderUi(enabled);return card;
    }

    private View reminderTimeAction(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(4),dp(4),dp(4));row.setBackground(ripple(Color.TRANSPARENT,16));ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_today);icon.setColorFilter(primary);icon.setPadding(dp(11),dp(11),dp(11),dp(11));icon.setBackground(shape(primaryContainer,15,0,0));row.addView(icon,lp(48,48));LinearLayout copy=column();copy.addView(label("提醒时间",15,text,true));TextView detail=label(reminderTimeText(),12,muted,false);copy.addView(detail,margin(-1,-2,0,3,0,0));LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.setMargins(dp(12),0,0,0);row.addView(copy,copyParams);ImageView arrow=iconButton(R.drawable.ic_chevron_right,"调整提醒时间");arrow.setColorFilter(muted);row.addView(arrow,lp(40,48));row.setOnClickListener(v->{int hour=prefs.getInt(AppText.KEY_REMINDER_HOUR,20),minute=prefs.getInt(AppText.KEY_REMINDER_MINUTE,0);new android.app.TimePickerDialog(this,(picker,newHour,newMinute)->{prefs.edit().putInt(AppText.KEY_REMINDER_HOUR,newHour).putInt(AppText.KEY_REMINDER_MINUTE,newMinute).apply();detail.setText(String.format(Locale.getDefault(),"%02d:%02d",newHour,newMinute)+T(" · 未完成时通知"," · Only when unfinished"));PackingReminderReceiver.schedule(this);showTransientMessage(T("提醒时间已改为 ","Reminder set to ")+String.format(Locale.getDefault(),"%02d:%02d",newHour,newMinute));},hour,minute,true).show();});arrow.setOnClickListener(v->row.performClick());row.setClickable(true);return row;
    }

    private String reminderTimeText(){return String.format(Locale.getDefault(),"%02d:%02d",prefs.getInt(AppText.KEY_REMINDER_HOUR,20),prefs.getInt(AppText.KEY_REMINDER_MINUTE,0))+T(" · 未完成时通知"," · Only when unfinished");}
    private void setReminderEnabled(boolean enabled){prefs.edit().putBoolean(AppText.KEY_REMINDER_ENABLED,enabled).apply();if(enabled)PackingReminderReceiver.schedule(this);else PackingReminderReceiver.cancel(this);updateReminderUi(enabled);showTransientMessage(T(enabled?"书包提醒已开启":"书包提醒已关闭",enabled?"Packing reminder enabled":"Packing reminder disabled"));}
    private void updateReminderUi(boolean enabled){if(reminderToggleRow!=null)updateToggleRow(reminderToggleRow,enabled);if(reminderTimeRow!=null){setEnabledTree(reminderTimeRow,enabled);reminderTimeRow.setAlpha(enabled?1f:.42f);}}
    private void setEnabledTree(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)setEnabledTree(group.getChildAt(i),enabled);}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=REQUEST_NOTIFICATION_PERMISSION)return;
        boolean granted=grantResults.length>0&&grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED;
        if(pendingOnboardingPermission){
            pendingOnboardingPermission=false;
            if(granted){vibrateTap(true);renderOnboardingStep(true);root.postDelayed(()->advanceOnboarding(2),420);}
            else{renderOnboardingStep(true);showTransientMessage(T("可以稍后在设置中开启通知","You can enable notifications later in Settings"));}
            return;
        }
        if(!pendingReminderEnable)return;
        pendingReminderEnable=false;
        if(granted)setReminderEnabled(true);else showTransientMessage(T("没有通知权限，书包提醒未开启","Notification permission denied; reminder stays off"));
    }

    private void showFirstRun(){
        if(onboardingOverlay!=null)return;
        onboardingLaunchedFromSettings=selectedTab==3;onboardingStep=0;onboardingFabCorners=new float[]{32f,32f,32f,32f};onboardingFabRotation=0f;
        FrameLayout overlay=new FrameLayout(this);onboardingOverlay=overlay;overlay.setBackgroundColor(bg);overlay.setClickable(true);overlay.setFocusable(true);
        LinearLayout shell=column();shell.setPadding(dp(20),dp(18),dp(20),dp(18));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label("课程包",18,text,true),new LinearLayout.LayoutParams(0,dp(48),1));
        TextView stepLabel=label("1 / 4",14,muted,true);onboardingStepLabel=stepLabel;stepLabel.setGravity(Gravity.CENTER);top.addView(stepLabel,lp(64,48));shell.addView(top);
        FrameLayout stage=new FrameLayout(this);onboardingStage=stage;shell.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dots=new LinearLayout(this);onboardingDots=dots;dots.setGravity(Gravity.CENTER);dots.setOrientation(LinearLayout.HORIZONTAL);shell.addView(dots,lp(-1,28));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setClipChildren(false);actions.setClipToPadding(false);actions.setPadding(dp(18),dp(10),dp(10),dp(10));actions.setBackground(shape(surfaceHigh,36,0,0));
        LinearLayout actionCopy=column();TextView actionTitle=label("开始使用",20,primary,true);onboardingActionTitle=actionTitle;actionCopy.addView(actionTitle,lp(-1,28));TextView back=label("稍后设置",13,muted,true);onboardingBack=back;back.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);back.setPadding(0,0,dp(12),0);back.setClickable(true);back.setFocusable(true);actionCopy.addView(back,lp(-1,48));actions.addView(actionCopy,new LinearLayout.LayoutParams(0,dp(76),1));
        FrameLayout next=new FrameLayout(this);onboardingNext=next;next.setClickable(true);next.setFocusable(true);next.setContentDescription(L("下一步"));next.setBackground(ripple(primaryContainer,32));ImageView nextIcon=new ImageView(this);onboardingNextIcon=nextIcon;nextIcon.setTag("next");nextIcon.setImageResource(R.drawable.ic_arrow_forward);nextIcon.setColorFilter(onPrimaryContainer);nextIcon.setPadding(dp(18),dp(18),dp(18),dp(18));next.addView(nextIcon,new FrameLayout.LayoutParams(-1,-1));actions.addView(next,lp(64,64));shell.addView(actions,lp(-1,96));
        overlay.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        back.setOnClickListener(v->handleOnboardingSecondary());
        next.setOnClickListener(v->animateOnboardingAction(this::handleOnboardingPrimary));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        if(motionEnabled()){overlay.setAlpha(0);overlay.setScaleX(.985f);overlay.setScaleY(.985f);overlay.animate().alpha(1).scaleX(1).scaleY(1).setDuration(280).setInterpolator(emphasized).start();}
        renderOnboardingStep(true);
    }

    private void handleOnboardingPrimary(){
        if(onboardingStep==0){advanceOnboarding(1);return;}
        if(onboardingStep==1){
            if(hasNotificationPermission()){advanceOnboarding(2);return;}
            pendingOnboardingPermission=true;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},REQUEST_NOTIFICATION_PERMISSION);return;
        }
        if(onboardingStep==2){if(hasNotificationPermission())setReminderEnabled(true);advanceOnboarding(3);return;}
        finishOnboarding(true);
    }

    private void handleOnboardingSecondary(){
        if(onboardingStep==0){finishOnboarding(false);return;}
        if(onboardingStep==1||onboardingStep==2){advanceOnboarding(onboardingStep+1);return;}
        finishOnboarding(false);
    }

    private void advanceOnboarding(int step){if(onboardingOverlay==null)return;onboardingStep=Math.max(0,Math.min(3,step));renderOnboardingStep(true);}
    private boolean hasNotificationPermission(){return Build.VERSION.SDK_INT<33||checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED;}

    private void renderOnboardingStep(boolean forward){
        if(onboardingStage==null)return;
        String[] titles={T("明天带什么，一眼就知道","Know what to pack at a glance"),T("先允许整理提醒","Allow packing reminders"),T("什么时候提醒你？","When should we remind you?"),T("准备好了","You're ready")};
        String[] bodies={T("课程、课本和用品汇成一份清单，逐件装进书包。","Courses, books, and supplies become one clear checklist."),T("只有到时间仍未整理完，课程包才会提醒。通知可随时关闭。","CoursePack only notifies you when packing is still unfinished. You can turn it off anytime."),T("默认晚上 20:00 提醒。点时间可以调整；不想用也可以跳过。","The default is 20:00. Tap the time to change it, or skip reminders entirely."),T("下一步导入课表，再给科目设置需要携带的物品。","Next, import your timetable and set carry items for each subject.")};
        LinearLayout page=column();page.setGravity(Gravity.CENTER_HORIZONTAL);page.setPadding(dp(8),dp(12),dp(8),dp(16));
        View hero=buildOnboardingHero(onboardingStep);page.addView(hero,lp(-1,224));
        TextView titleView=label(titles[onboardingStep],30,text,true);titleView.setGravity(Gravity.CENTER);titleView.setLetterSpacing(-.02f);page.addView(titleView,margin(-1,-2,8,20,8,0));
        TextView body=label(bodies[onboardingStep],15,muted,false);body.setGravity(Gravity.CENTER);body.setLineSpacing(dp(3),1f);page.addView(body,margin(-1,-2,14,12,14,0));
        if(onboardingStep==1){TextView state=label(hasNotificationPermission()?"通知权限已允许":"稍后也可从设置开启",13,hasNotificationPermission()?success:muted,true);state.setGravity(Gravity.CENTER);state.setPadding(dp(14),0,dp(14),0);state.setBackground(shape(hasNotificationPermission()?tertiaryContainer:surfaceHigh,16,0,0));page.addView(state,margin(-2,36,0,16,0,0));}
        if(onboardingStep==2){TextView time=label(String.format(Locale.getDefault(),"%02d:%02d",prefs.getInt(AppText.KEY_REMINDER_HOUR,20),prefs.getInt(AppText.KEY_REMINDER_MINUTE,0)),24,onPrimaryContainer,true);onboardingTimeLabel=time;time.setGravity(Gravity.CENTER);time.setBackground(ripple(primaryContainer,22));time.setOnClickListener(v->pickOnboardingReminderTime());page.addView(time,margin(132,54,0,16,0,0));}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(page);
        View previous=onboardingStage.getChildCount()==0?null:onboardingStage.getChildAt(onboardingStage.getChildCount()-1);onboardingStage.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        if(previous!=null){previous.animate().cancel();if(motionEnabled()){scroll.setAlpha(0);scroll.setTranslationX(dp(forward?72:-72));scroll.setScaleX(.96f);scroll.setScaleY(.96f);scroll.animate().alpha(1).translationX(0).scaleX(1).scaleY(1).setDuration(380).setInterpolator(emphasized).start();previous.animate().alpha(0).translationX(dp(forward?-44:44)).scaleX(.92f).scaleY(.92f).rotation(forward?-4:4).setDuration(190).setInterpolator(emphasized).withEndAction(()->{if(previous.getParent()==onboardingStage)onboardingStage.removeView(previous);}).start();}else onboardingStage.removeView(previous);}
        animateOnboardingHero(hero);
        onboardingDots.removeAllViews();for(int i=0;i<4;i++){View dot=new View(this);dot.setBackground(shape(i==onboardingStep?primary:secondary,5,0,0));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(i==onboardingStep?28:8),dp(8));if(i>0)params.setMargins(dp(7),0,0,0);onboardingDots.addView(dot,params);}
        if(onboardingStepLabel!=null)onboardingStepLabel.setText((onboardingStep+1)+" / 4");
        String backText=onboardingStep==0?"稍后设置":onboardingStep==1?"暂不允许":onboardingStep==2?"暂不提醒":"先看看";
        String nextText=onboardingStep==0?"开始使用":onboardingStep==1?(hasNotificationPermission()?"继续设置":"允许通知"):onboardingStep==2?(hasNotificationPermission()?"开启提醒":"继续设置"):"导入课表";
        onboardingBack.setText(L(backText));animateOnboardingActionLabel(nextText,forward);morphOnboardingFab(onboardingStep);onboardingNext.setContentDescription(L(nextText));onboardingNext.setEnabled(true);onboardingNext.setAlpha(1f);
    }

    private View buildOnboardingHero(int step){
        FrameLayout hero=new FrameLayout(this);hero.setClipChildren(false);hero.setClipToPadding(false);
        View halo=new View(this);halo.setTag("halo");halo.setBackground(shape(step==3?tertiaryContainer:primaryContainer,54,0,0));FrameLayout.LayoutParams haloP=new FrameLayout.LayoutParams(dp(178),dp(178),Gravity.CENTER);hero.addView(halo,haloP);
        ImageView icon=new ImageView(this);icon.setTag("heroIcon");
        if(step==0){icon.setImageResource(R.drawable.ic_launcher_generated_v2);icon.setPadding(dp(13),dp(13),dp(13),dp(13));}
        else{icon.setImageResource(step==1?R.drawable.ic_notification:step==2?R.drawable.ic_today:R.drawable.ic_check);icon.setColorFilter(step==3?onTertiaryContainer:onPrimaryContainer);icon.setPadding(dp(40),dp(40),dp(40),dp(40));}
        FrameLayout.LayoutParams iconP=new FrameLayout.LayoutParams(dp(166),dp(166),Gravity.CENTER);hero.addView(icon,iconP);
        int[] colors={primary,warningAccent,success};String[] marks=step==0?new String[]{"语","数","英"}:step==1?new String[]{"8","✓","!"}:step==2?new String[]{"19","20","21"}:new String[]{"✓","✓","✓"};
        for(int i=0;i<3;i++){TextView chip=label(marks[i],12,i==0?onPrimary:(dark?0xFF172117:Color.WHITE),true);chip.setTag("chip"+i);chip.setGravity(Gravity.CENTER);chip.setBackground(shape(colors[i],14,0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER);p.leftMargin=dp((i-1)*70);p.topMargin=dp(i==1?-78:72);hero.addView(chip,p);}
        return hero;
    }

    private void animateOnboardingHero(View hero){
        if(!motionEnabled())return;View halo=hero.findViewWithTag("halo"),icon=hero.findViewWithTag("heroIcon");
        halo.setScaleX(.64f);halo.setScaleY(.64f);halo.setRotation(-10);halo.setAlpha(0);halo.animate().scaleX(1).scaleY(1).rotation(0).alpha(1).setDuration(520).setInterpolator(emphasized).start();
        icon.setScaleX(.72f);icon.setScaleY(.72f);icon.setRotation(8);icon.setAlpha(0);icon.animate().scaleX(1).scaleY(1).rotation(0).alpha(1).setStartDelay(70).setDuration(520).setInterpolator(emphasized).start();
        for(int i=0;i<3;i++){View chip=hero.findViewWithTag("chip"+i);chip.setAlpha(0);chip.setScaleX(.35f);chip.setScaleY(.35f);chip.setRotation((i-1)*18);chip.setTranslationY(dp(i==1?-24:24));chip.animate().alpha(1).scaleX(1).scaleY(1).rotation(0).translationY(0).setStartDelay(150+i*85L).setDuration(390).setInterpolator(emphasized).start();}
    }

    private void animateOnboardingAction(Runnable action){
        if(onboardingNext==null||!motionEnabled()){action.run();return;}FrameLayout button=onboardingNext;button.setEnabled(false);vibrateTap(false);
        button.animate().scaleX(.91f).scaleY(.91f).setDuration(90).setInterpolator(emphasized).withEndAction(()->{action.run();if(onboardingNext==button)button.animate().scaleX(1).scaleY(1).setDuration(180).setInterpolator(emphasized).withEndAction(()->button.setEnabled(true)).start();}).start();
    }

    private void animateOnboardingActionLabel(String value,boolean forward){
        if(onboardingActionTitle==null)return;String localized=L(value);if(localized.contentEquals(onboardingActionTitle.getText()))return;
        if(!motionEnabled()){onboardingActionTitle.setText(localized);return;}float distance=dp(18);onboardingActionTitle.animate().cancel();onboardingActionTitle.animate().alpha(0).translationY(forward?-distance:distance).setDuration(90).setInterpolator(emphasized).withEndAction(()->{if(onboardingActionTitle==null)return;onboardingActionTitle.setText(localized);onboardingActionTitle.setTranslationY(forward?distance:-distance);onboardingActionTitle.animate().alpha(1).translationY(0).setStartDelay(90).setDuration(220).setInterpolator(emphasized).start();}).start();
    }

    private void morphOnboardingFab(int step){
        if(onboardingNext==null||onboardingNextIcon==null)return;float[] target=switch(step%3){case 0->new float[]{32f,32f,32f,32f};case 1->new float[]{22f,22f,22f,22f};default->new float[]{14f,32f,14f,32f};};float targetRotation=step*360f;boolean finish=step==3;
        swapOnboardingFabIcon(finish);
        if(!motionEnabled()){applyOnboardingFabCorners(target);onboardingNext.setRotation(0);onboardingNextIcon.setRotation(0);onboardingFabCorners=target;onboardingFabRotation=targetRotation;return;}
        if(onboardingFabCornerAnimator!=null)onboardingFabCornerAnimator.cancel();if(onboardingFabRotationAnimator!=null)onboardingFabRotationAnimator.cancel();
        float[] start=onboardingFabCorners.clone();ValueAnimator corners=ValueAnimator.ofFloat(0f,1f);onboardingFabCornerAnimator=corners;corners.setDuration(600);corners.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());corners.addUpdateListener(value->{float progress=(float)value.getAnimatedValue();float[] now=new float[4];for(int i=0;i<4;i++)now[i]=start[i]+(target[i]-start[i])*progress;onboardingFabCorners=now;applyOnboardingFabCorners(now);});corners.start();
        float startRotation=onboardingNext.getRotation();ValueAnimator rotation=ValueAnimator.ofFloat(startRotation,targetRotation);onboardingFabRotationAnimator=rotation;rotation.setDuration(900);rotation.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());rotation.addUpdateListener(value->{if(onboardingNext==null||onboardingNextIcon==null)return;float angle=(float)value.getAnimatedValue();onboardingFabRotation=angle;onboardingNext.setRotation(angle);onboardingNextIcon.setRotation(-angle);});rotation.start();
    }

    private void applyOnboardingFabCorners(float[] corners){
        if(onboardingNext==null||!(onboardingNext.getBackground() instanceof android.graphics.drawable.RippleDrawable ripple))return;android.graphics.drawable.Drawable content=ripple.getDrawable(0);if(!(content instanceof GradientDrawable fill))return;float tl=dp(corners[0]),tr=dp(corners[1]),bl=dp(corners[2]),br=dp(corners[3]);fill.setCornerRadii(new float[]{tl,tl,tr,tr,br,br,bl,bl});
    }

    private void swapOnboardingFabIcon(boolean finish){
        if(onboardingNextIcon==null)return;String state=finish?"finish":"next";if(state.equals(onboardingNextIcon.getTag()))return;onboardingNextIcon.setTag(state);int icon=finish?R.drawable.ic_check:R.drawable.ic_arrow_forward;
        if(!motionEnabled()){onboardingNextIcon.setImageResource(icon);return;}onboardingNextIcon.animate().alpha(0).scaleX(.9f).scaleY(.9f).setDuration(90).withEndAction(()->{if(onboardingNextIcon==null)return;onboardingNextIcon.setImageResource(icon);onboardingNextIcon.setAlpha(0);onboardingNextIcon.setScaleX(.9f);onboardingNextIcon.setScaleY(.9f);onboardingNextIcon.animate().alpha(1).scaleX(1).scaleY(1).setStartDelay(90).setDuration(220).setInterpolator(emphasized).start();}).start();
    }

    private void pickOnboardingReminderTime(){
        int hour=prefs.getInt(AppText.KEY_REMINDER_HOUR,20),minute=prefs.getInt(AppText.KEY_REMINDER_MINUTE,0);
        new android.app.TimePickerDialog(this,(picker,newHour,newMinute)->{prefs.edit().putInt(AppText.KEY_REMINDER_HOUR,newHour).putInt(AppText.KEY_REMINDER_MINUTE,newMinute).apply();if(onboardingTimeLabel!=null){onboardingTimeLabel.setText(String.format(Locale.getDefault(),"%02d:%02d",newHour,newMinute));animateValue(onboardingTimeLabel);}},hour,minute,true).show();
    }

    private void handleOnboardingBack(){if(onboardingStep>0){onboardingStep--;renderOnboardingStep(false);}else if(onboardingLaunchedFromSettings)finishOnboarding(false);else finishAfterTransition();}
    private void finishOnboarding(boolean importNow){prefs.edit().putBoolean(KEY_ONBOARDING_DONE,true).putInt(KEY_WHATS_NEW_VERSION,CURRENT_VERSION_CODE).apply();FrameLayout overlay=onboardingOverlay;if(overlay==null)return;Runnable remove=()->{if(onboardingFabCornerAnimator!=null)onboardingFabCornerAnimator.cancel();if(onboardingFabRotationAnimator!=null)onboardingFabRotationAnimator.cancel();if(overlay.getParent()==root)root.removeView(overlay);onboardingOverlay=null;onboardingStage=null;onboardingDots=null;onboardingNext=null;onboardingNextIcon=null;onboardingActionTitle=null;onboardingBack=null;onboardingStepLabel=null;onboardingTimeLabel=null;onboardingFabCornerAnimator=null;onboardingFabRotationAnimator=null;if(importNow){showSchedule(true);root.postDelayed(this::showScheduleTextImport,300);}};if(motionEnabled())overlay.animate().alpha(0).scaleX(1.04f).scaleY(1.04f).rotation(.8f).setDuration(260).setInterpolator(emphasized).withEndAction(remove).start();else remove.run();}

    private void showWhatsNew(){
        prefs.edit().putInt(KEY_WHATS_NEW_VERSION,CURRENT_VERSION_CODE).apply();Dialog dialog=bottomDialog();LinearLayout content=sheet("2.9.3 更新","课表图片导入现在更清楚");TextView badge=label("2.9.3",13,onPrimaryContainer,true);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(primaryContainer,16,0,0));content.addView(badge,lp(78,34));LinearLayout list=column();list.setPadding(dp(8),dp(6),dp(8),dp(6));list.setBackground(shape(surfaceHigh,22,0,0));list.addView(whatsNewFeature(R.drawable.ic_import,"图片按钮不再拥挤","选择课程表图片与识别内容之间增加清楚的分组间距。"));list.addView(whatsNewFeature(R.drawable.ic_subjects,"识别方式说明更准确","明确说明会读取文字位置，并按星期列和节次行重建课程。"));content.addView(list,margin(-1,-2,0,14,0,16));TextView done=button("知道了",true);done.setOnClickListener(v->dialog.dismiss());content.addView(done,lp(-1,52));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);showBottomDialog(dialog,scroll,(int)(getResources().getDisplayMetrics().heightPixels*.82f));
    }

    private View whatsNewFeature(int iconRes,String title,String detail){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setColorFilter(onPrimaryContainer);icon.setPadding(dp(11),dp(11),dp(11),dp(11));icon.setBackground(shape(primaryContainer,16,0,0));row.addView(icon,lp(50,50));LinearLayout copy=column();copy.addView(label(title,16,text,true));TextView body=label(detail,13,muted,false);body.setMaxLines(3);copy.addView(body,margin(-1,-2,0,3,0,0));LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.setMargins(dp(13),0,0,0);row.addView(copy,copyParams);return row;}

    private void applySettingAndRecreate(ScrollView scroll,String key,String value){if(value.equals(prefs.getString(key,"system")))return;settingsScrollY=scroll.getScrollY();animateSettingsRestore=true;prefs.edit().putString(key,value).apply();if(motionEnabled())contentHost.animate().alpha(.1f).translationY(dp(-10)).setDuration(150).setInterpolator(emphasized).withEndAction(this::recreate).start();else recreate();}

    private TextView settingsSectionTitle(String title){TextView view=label(title,20,text,true);return view;}
    private View settingsChoiceCard(String title,String[] labels,String[] values,String selected,StringPicked picked){
        LinearLayout card=column();card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(shape(surfaceHigh,22,0,0));TextView heading=label(title,13,muted,true);heading.setPadding(dp(10),dp(5),dp(10),dp(8));card.addView(heading);
        for(int i=0;i<labels.length;i++){String value=values[i];boolean active=value.equals(selected);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setBackground(ripple(active?primaryContainer:Color.TRANSPARENT,16));TextView name=label(labels[i],15,active?onPrimaryContainer:text,true);row.addView(name,new LinearLayout.LayoutParams(0,dp(52),1));ImageView state=new ImageView(this);setItemCheckVisual(state,active);row.addView(state,lp(28,28));row.setContentDescription(L(labels[i])+(active?T("，已选择",", selected"):""));row.setOnClickListener(v->picked.accept(value));row.setClickable(true);row.setFocusable(true);card.addView(row);}
        return card;
    }

    private View settingsDataCard(){
        LinearLayout card=column();card.setPadding(dp(8),dp(6),dp(8),dp(6));card.setBackground(shape(surfaceHigh,22,0,0));
        card.addView(settingsActionRow(R.drawable.ic_export,"导出备份","保存课程、携带物、打卡和设置",this::startBackupExport),lp(-1,68));
        View divider=new View(this);divider.setBackgroundColor(outline);card.addView(divider,margin(-1,1,56,0,12,0));
        card.addView(settingsActionRow(R.drawable.ic_import,"导入备份","从课程包备份文件恢复数据",this::startBackupImport),lp(-1,68));
        return card;
    }

    private View settingsActionRow(int iconRes,String title,String subtitle,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(4),dp(4),dp(4));row.setBackground(ripple(Color.TRANSPARENT,16));
        ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setColorFilter(primary);icon.setPadding(dp(11),dp(11),dp(11),dp(11));icon.setBackground(shape(primaryContainer,15,0,0));row.addView(icon,lp(48,48));
        LinearLayout copy=column();copy.addView(label(title,15,text,true));TextView detail=label(subtitle,12,muted,false);detail.setMaxLines(2);copy.addView(detail,margin(-1,-2,0,3,0,0));LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.setMargins(dp(12),0,0,0);row.addView(copy,copyParams);
        ImageView arrow=iconButton(R.drawable.ic_chevron_right,title);arrow.setColorFilter(muted);row.addView(arrow,lp(40,48));row.setContentDescription(L(title)+"，"+L(subtitle));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->{vibrateTap(false);action.run();});return row;
    }

    private void startBackupExport(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"CoursePack-backup-"+LocalDate.now()+".json");startActivityForResult(intent,REQUEST_EXPORT_BACKUP);
    }

    private void startBackupImport(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");startActivityForResult(intent,REQUEST_IMPORT_BACKUP);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(requestCode==REQUEST_EXPORT_BACKUP)writeBackup(uri);else if(requestCode==REQUEST_IMPORT_BACKUP)readBackupForImport(uri);else if(requestCode==REQUEST_SCHEDULE_IMAGE)recognizeScheduleImage(uri);
    }

    private void writeBackup(Uri uri){
        try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){if(stream==null)throw new IllegalStateException("no output stream");byte[] bytes=createBackupJson().toString(2).getBytes(StandardCharsets.UTF_8);stream.write(bytes);stream.flush();showTransientMessage("备份已导出");}
        catch(Exception error){showTransientMessage("导出失败，请换一个位置重试");}
    }

    private JSONObject createBackupJson() throws Exception{
        JSONObject rootObject=new JSONObject();rootObject.put("format","coursepack-backup");rootObject.put("formatVersion",BACKUP_FORMAT_VERSION);rootObject.put("appVersion","2.9.3");rootObject.put("exportedAt",java.time.OffsetDateTime.now().toString());JSONObject data=new JSONObject();
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){if(KEY_IMPORT_SUCCESS.equals(entry.getKey()))continue;Object value=entry.getValue();JSONObject item=new JSONObject();if(value instanceof String){item.put("type","string");item.put("value",value);}else if(value instanceof Boolean){item.put("type","boolean");item.put("value",value);}else if(value instanceof Integer){item.put("type","integer");item.put("value",value);}else if(value instanceof Long){item.put("type","long");item.put("value",value);}else if(value instanceof Float){item.put("type","float");item.put("value",value);}else if(value instanceof Set){item.put("type","stringSet");JSONArray values=new JSONArray();for(Object member:(Set<?>)value)if(member instanceof String)values.put(member);item.put("value",values);}else continue;data.put(entry.getKey(),item);}
        rootObject.put("data",data);return rootObject;
    }

    private void readBackupForImport(Uri uri){
        try(InputStream stream=getContentResolver().openInputStream(uri)){if(stream==null)throw new IllegalStateException("no input stream");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int read,total=0;while((read=stream.read(buffer))!=-1){total+=read;if(total>MAX_BACKUP_BYTES)throw new IllegalArgumentException("too large");bytes.write(buffer,0,read);}JSONObject backup=new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));validateBackup(backup);showImportConfirmation(backup);}
        catch(Exception error){showTransientMessage("无法读取备份，请选择课程包导出的文件");}
    }

    private void validateBackup(JSONObject backup) throws Exception{
        if(!"coursepack-backup".equals(backup.optString("format")))throw new IllegalArgumentException("format");int version=backup.optInt("formatVersion",-1);if(version<1||version>BACKUP_FORMAT_VERSION)throw new IllegalArgumentException("version");JSONObject data=backup.getJSONObject("data");if(data.length()>500)throw new IllegalArgumentException("too many keys");java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){String key=keys.next();if(!isAllowedBackupKey(key))throw new IllegalArgumentException("key");JSONObject item=data.getJSONObject(key);String type=item.getString("type");if(!"string".equals(type)&&!"boolean".equals(type)&&!"integer".equals(type)&&!"long".equals(type)&&!"float".equals(type)&&!"stringSet".equals(type))throw new IllegalArgumentException("type");Object value=item.get("value");if("string".equals(type)&&(!(value instanceof String)||((String)value).length()>MAX_BACKUP_BYTES))throw new IllegalArgumentException("string");if("stringSet".equals(type)){JSONArray array=item.getJSONArray("value");if(array.length()>10000)throw new IllegalArgumentException("set");for(int i=0;i<array.length();i++)if(array.getString(i).length()>4096)throw new IllegalArgumentException("member");}}
    }

    private boolean isAllowedBackupKey(String key){return KEY_LEGACY_COURSES.equals(key)||KEY_SUBJECTS.equals(key)||KEY_ENTRIES.equals(key)||KEY_CHECKS.equals(key)||KEY_DAILY_ITEMS.equals(key)||KEY_DAILY_CHECKS.equals(key)||key.startsWith(KEY_TIME_SLOT_PREFIX)||key.startsWith("settings_");}

    private void showImportConfirmation(JSONObject backup){
        JSONObject data=backup.optJSONObject("data");String subjectsRaw=backupString(data,KEY_SUBJECTS);String entriesRaw=backupString(data,KEY_ENTRIES);int subjectCount=countBackupLines(subjectsRaw);int entryCount=countBackupLines(entriesRaw);Dialog dialog=bottomDialog();LinearLayout sheet=sheet("导入这份备份？","将替换当前设备上的课程、打卡和设置。备份包含 "+subjectCount+" 个科目、"+entryCount+" 节周课程。");LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView cancel=button("取消",false);TextView confirm=button("确认导入",true);actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams confirmParams=new LinearLayout.LayoutParams(0,dp(52),1);confirmParams.setMargins(dp(12),0,0,0);actions.addView(confirm,confirmParams);sheet.addView(actions);cancel.setOnClickListener(v->dialog.dismiss());confirm.setOnClickListener(v->{try{applyBackup(backup);dialog.dismiss();}catch(Exception error){dialog.dismiss();showTransientMessage("导入失败，原有数据没有改变");}});showBottomDialog(dialog,sheet);
    }

    private String backupString(JSONObject data,String key){if(data==null)return"";JSONObject item=data.optJSONObject(key);return item==null?"":item.optString("value","");}
    private int countBackupLines(String raw){if(raw==null||raw.isEmpty())return 0;return raw.split("\\n",-1).length;}

    private void applyBackup(JSONObject backup) throws Exception{
        JSONObject data=backup.getJSONObject("data");SharedPreferences.Editor editor=prefs.edit().clear();java.util.Iterator<String> keys=data.keys();while(keys.hasNext()){String key=keys.next();JSONObject item=data.getJSONObject(key);String type=item.getString("type");switch(type){case"string"->editor.putString(key,item.getString("value"));case"boolean"->editor.putBoolean(key,item.getBoolean("value"));case"integer"->editor.putInt(key,item.getInt("value"));case"long"->editor.putLong(key,item.getLong("value"));case"float"->editor.putFloat(key,(float)item.getDouble("value"));case"stringSet"->{JSONArray array=item.getJSONArray("value");Set<String> values=new HashSet<>();for(int i=0;i<array.length();i++)values.add(array.getString(i));editor.putStringSet(key,values);}}}editor.putBoolean(KEY_IMPORT_SUCCESS,true);if(!editor.commit())throw new IllegalStateException("commit");TomorrowWidgetProvider.refreshAll(this);PackingReminderReceiver.schedule(this);recreate();
    }

    private void showTransientMessage(String message){
        if(root==null)return;TextView banner=label(message,14,onPrimary,true);banner.setGravity(Gravity.CENTER);banner.setPadding(dp(18),0,dp(18),0);banner.setBackground(shape(primary,18,0,0));banner.setElevation(dp(8));FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,dp(52),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);params.setMargins(dp(20),0,dp(20),dp(92));root.addView(banner,params);banner.setAlpha(0f);banner.setTranslationY(dp(18));banner.animate().alpha(1f).translationY(0).setDuration(motionEnabled()?220:0).setInterpolator(emphasized).start();banner.announceForAccessibility(L(message));banner.postDelayed(()->banner.animate().alpha(0f).translationY(dp(12)).setDuration(motionEnabled()?180:0).setInterpolator(emphasized).withEndAction(()->root.removeView(banner)).start(),2600);
    }

    private View buildTomorrowPage() {
        LocalDate today=LocalDate.now(); checklistDate=resolveChecklistDate();
        List<Entry> dayEntries=entriesFor(checklistDate.getDayOfWeek().getValue()-1); LinkedHashMap<Subject,List<Entry>> grouped=groupBySubject(dayEntries);
        List<DailyItem> activeDaily=dailyItemsFor(checklistDate);tomorrowTotalItems=totalItems(grouped)+activeDaily.size(); tomorrowMissingSubjects=countMissingSubjects(grouped); int done=countDoneItems(grouped)+countDoneDailyItems(activeDaily,checklistDate);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); LinearLayout page=column(); page.setPadding(dp(20),dp(22),dp(20),dp(112));
        String focus=checklistDate.equals(today)?"今天":checklistDate.equals(today.plusDays(1))?"明天":"下一上课日";
        addPageHeader(page,L(focus)+" · "+chineseWeek(checklistDate.getDayOfWeek()),formatDate(checklistDate)+" · "+T(dayEntries.size()+" 节课",dayEntries.size()+" lessons"),24);
        LinearLayout hero=column();hero.setPadding(dp(20),dp(14),dp(20),dp(18));hero.setBackground(shape(primaryContainer,30,0,0));
        LinearLayout heroHeading=new LinearLayout(this);heroHeading.setOrientation(LinearLayout.HORIZONTAL);heroHeading.setGravity(Gravity.CENTER_VERTICAL);heroHeading.addView(label("书包进度",13,onPrimaryContainer,true),new LinearLayout.LayoutParams(0,dp(40),1));clearChecksButton=label("清空已选",13,onPrimaryContainer,true);clearChecksButton.setGravity(Gravity.CENTER);clearChecksButton.setPadding(dp(13),0,dp(13),0);clearChecksButton.setBackground(ripple(secondary,16));clearChecksButton.setOnClickListener(v->showClearChecksDialog());updateClearChecksButton();heroHeading.addView(clearChecksButton,lp(-2,36));hero.addView(heroHeading);
        boolean noLessonsOrDaily=dayEntries.isEmpty()&&activeDaily.isEmpty();tomorrowStatus=label(noLessonsOrDaily?"没有课":progressStatus(tomorrowTotalItems,done,tomorrowMissingSubjects),26,onPrimaryContainer,true);hero.addView(tomorrowStatus,margin(-1,-2,0,6,0,0));
        tomorrowProgressRow=new LinearLayout(this);tomorrowProgressRow.setOrientation(LinearLayout.HORIZONTAL);if(tomorrowTotalItems>0){buildProgressSegments(tomorrowProgressRow,tomorrowTotalItems,done);hero.addView(tomorrowProgressRow,margin(-1,10,0,16,0,0));}
        tomorrowProgressLabel=label(noLessonsOrDaily?focus+"没有安排课程":progressLabel(tomorrowTotalItems,done,tomorrowMissingSubjects),13,onPrimaryContainer,false);hero.addView(tomorrowProgressLabel,margin(-1,-2,0,10,0,0));page.addView(hero,margin(-1,-2,0,0,0,22));
        List<BagItem> takeOutItems=packedItemsNotNeededFor(grouped);if(!takeOutItems.isEmpty())page.addView(takeOutSection(takeOutItems),margin(-1,-2,0,0,0,22));
        if(!activeDaily.isEmpty())page.addView(dailyItemsSection(activeDaily),margin(-1,-2,0,0,0,22));
        if(dayEntries.isEmpty())page.addView(emptyState("还没有课程","先到课表页排课。"));else{page.addView(label("按物品准备",20,text,true));page.addView(label("已装状态会自动沿用，也可以逐件标记拿出",13,muted,false),margin(-1,-2,0,4,0,12));for(Map.Entry<Subject,List<Entry>> group:grouped.entrySet())page.addView(subjectChecklist(group.getKey(),group.getValue()),margin(-1,-2,0,0,0,12));}
        scroll.addView(page);return scroll;
    }

    private View dailyItemsSection(List<DailyItem> items){
        LinearLayout section=column();LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=column();copy.addView(label("每日随身",20,text,true));copy.addView(label("永久项每天重置，临时项只出现一次",13,muted,false),margin(-1,-2,0,4,0,0));heading.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView add=label("添加",13,primary,true);add.setGravity(Gravity.CENTER);add.setContentDescription(L("添加每日随身物品"));add.setBackground(ripple(primaryContainer,15));add.setClickable(true);add.setFocusable(true);add.setOnClickListener(v->showDailyItemEditor(null));heading.addView(add,margin(70,40,10,0,0,0));section.addView(heading);
        LinearLayout list=column();list.setPadding(dp(8),dp(6),dp(8),dp(6));list.setBackground(shape(surfaceHigh,22,0,0));section.addView(list,margin(-1,-2,0,12,0,0));
        if(items.isEmpty()){LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.HORIZONTAL);empty.setGravity(Gravity.CENTER_VERTICAL);empty.setPadding(dp(8),dp(6),dp(8),dp(6));ImageView plus=new ImageView(this);plus.setImageResource(R.drawable.ic_add);plus.setColorFilter(primary);plus.setPadding(dp(10),dp(10),dp(10),dp(10));plus.setBackground(shape(primaryContainer,14,0,0));empty.addView(plus,lp(44,44));LinearLayout emptyCopy=column();emptyCopy.addView(label("暂时没有随身物品",15,text,true));emptyCopy.addView(label("可添加校卡、水杯或临时用品",12,muted,false),margin(-1,-2,0,3,0,0));LinearLayout.LayoutParams emptyCopyParams=new LinearLayout.LayoutParams(0,-2,1);emptyCopyParams.setMargins(dp(12),0,0,0);empty.addView(emptyCopy,emptyCopyParams);empty.setContentDescription(L("没有每日随身物品，点此添加"));empty.setBackground(ripple(Color.TRANSPARENT,16));empty.setClickable(true);empty.setFocusable(true);empty.setOnClickListener(v->showDailyItemEditor(null));list.addView(empty,lp(-1,64));}
        else for(DailyItem item:items)list.addView(dailyItemRow(item),margin(-1,-2,0,0,0,4));return section;
    }

    private View dailyItemRow(DailyItem item){
        boolean checked=isDailyChecked(checklistDate,item.id);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(4),0,dp(4));row.setBackground(ripple(Color.TRANSPARENT,16));FrameLayout target=new FrameLayout(this);target.setContentDescription(L((checked?"取消勾选 ":"勾选 ")+item.name));target.setClickable(true);target.setFocusable(true);ImageView state=new ImageView(this);setItemCheckVisual(state,checked);target.addView(state,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));row.addView(target,lp(48,56));
        LinearLayout copy=column();TextView name=label(item.name,15,checked?muted:text,true);name.setPaintFlags(checked?name.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG:name.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);copy.addView(name);copy.addView(label(item.permanent?"每天都要确认":T("仅 "+formatDate(item.date),"Only "+formatDate(item.date)),12,muted,false),margin(-1,-2,0,3,0,0));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView edit=iconButton(R.drawable.ic_chevron_right,"编辑随身物品 "+item.name);edit.setOnClickListener(v->showDailyItemEditor(item));row.addView(edit,lp(44,48));target.setOnClickListener(v->toggleDailyItem(item,row,target,state,name));row.setOnClickListener(v->target.performClick());row.setClickable(true);return row;
    }

    private void toggleDailyItem(DailyItem item,View row,View target,ImageView state,TextView name){
        int beforeDone=countDoneForChecklist();boolean checked=!isDailyChecked(checklistDate,item.id);setDailyChecked(checklistDate,item.id,checked);saveDailyChecks();updateClearChecksButton();target.setContentDescription(L((checked?"取消勾选 ":"勾选 ")+item.name));setItemCheckVisual(state,checked);name.setTextColor(checked?muted:text);name.setPaintFlags(checked?name.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG:name.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);vibrateTap(checked);if(motionEnabled()){state.setScaleX(.62f);state.setScaleY(.62f);state.setAlpha(.35f);state.animate().scaleX(1.16f).scaleY(1.16f).alpha(1).setDuration(140).setInterpolator(emphasized).withEndAction(()->state.animate().scaleX(1).scaleY(1).setDuration(170).setInterpolator(emphasized).start()).start();row.animate().scaleX(.985f).scaleY(.985f).setDuration(80).withEndAction(()->row.animate().scaleX(1).scaleY(1).setDuration(190).setInterpolator(emphasized).start()).start();}refreshTomorrowProgress(true);int afterDone=countDoneForChecklist();if(checked&&tomorrowTotalItems>0&&tomorrowMissingSubjects==0&&beforeDone<tomorrowTotalItems&&afterDone==tomorrowTotalItems)root.postDelayed(this::showCompletionCelebration,260);
    }

    private void showDailyItemsManager(){Dialog dialog=bottomDialog();LinearLayout sheet=sheet("每日随身物品","这是可选功能，不设置时不会出现在准备首页");LinearLayout list=column();list.setBackground(shape(surfaceHigh,20,0,0));for(int i=0;i<dailyItems.size();i++){DailyItem item=dailyItems.get(i);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(9),dp(8),dp(9));LinearLayout copy=column();copy.addView(label(item.name,16,text,true));copy.addView(label(item.permanent?"永久 · 每天出现":T("临时 · "+formatDate(item.date),"Temporary · "+formatDate(item.date)),13,muted,false),margin(-1,-2,0,3,0,0));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=iconButton(R.drawable.ic_chevron_right,"编辑 "+item.name);row.addView(arrow,lp(40,48));row.setBackground(ripple(Color.TRANSPARENT,0));row.setContentDescription(L("编辑随身物品 "+item.name));row.setOnClickListener(v->{dialog.dismiss();root.postDelayed(()->showDailyItemEditor(item),120);});arrow.setOnClickListener(v->{dialog.dismiss();root.postDelayed(()->showDailyItemEditor(item),120);});row.setClickable(true);list.addView(row,lp(-1,68));if(i<dailyItems.size()-1){View divider=new View(this);divider.setBackgroundColor(outline);list.addView(divider,margin(-1,1,16,0,16,0));}}sheet.addView(list,margin(-1,-2,0,0,0,14));LinearLayout add=iconTextButton(R.drawable.ic_add,"添加随身物品",primary);add.setOnClickListener(v->{dialog.dismiss();root.postDelayed(()->showDailyItemEditor(null),120);});sheet.addView(add,lp(-1,52));showBottomDialog(dialog,sheet);}

    private void showDailyItemEditor(DailyItem existing){
        int returnTab=selectedTab;boolean editing=existing!=null;Dialog dialog=bottomDialog();LinearLayout sheet=sheet(editing?"编辑随身物品":"添加随身物品","永久项每天出现；临时项只加入当前准备日");EditText name=input("例如：校卡、水杯",editing?existing.name:"");boolean[] permanent={editing?existing.permanent:true};TextView forever=label("永久 · 每天出现",15,text,true);TextView once=label(T("临时 · 仅 "+formatDate(checklistDate),"Temporary · Only "+formatDate(checklistDate)),15,text,true);for(TextView choice:new TextView[]{forever,once}){choice.setGravity(Gravity.CENTER_VERTICAL);choice.setPadding(dp(16),0,dp(16),0);choice.setClickable(true);choice.setFocusable(true);}LinearLayout modes=column();modes.addView(forever,lp(-1,52));modes.addView(once,margin(-1,52,0,8,0,0));Runnable render=()->{forever.setTextColor(permanent[0]?onPrimaryContainer:text);once.setTextColor(!permanent[0]?onPrimaryContainer:text);forever.setBackground(ripple(permanent[0]?primaryContainer:surfaceHigh,15));once.setBackground(ripple(!permanent[0]?primaryContainer:surfaceHigh,15));forever.setContentDescription(L((permanent[0]?"已选择 ":"选择 ")+"永久，每天出现"));once.setContentDescription(L((!permanent[0]?"已选择 ":"选择 ")+"临时，仅当前准备日"));};forever.setOnClickListener(v->{permanent[0]=true;render.run();animateValue(forever);});once.setOnClickListener(v->{permanent[0]=false;render.run();animateValue(once);});render.run();sheet.addView(fieldGroup("物品名称",name),margin(-1,-2,0,0,0,12));LinearLayout modeGroup=column();modeGroup.addView(label("出现方式",13,muted,true),margin(-1,-2,2,0,0,7));modeGroup.addView(modes);sheet.addView(modeGroup,margin(-1,-2,0,0,0,18));LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);if(editing){TextView delete=button("删除",false);delete.setTextColor(dark?0xFFFFB4AB:0xFFBA1A1A);delete.setOnClickListener(v->{dailyItems.remove(existing);dailyChecks.removeIf(saved->saved.endsWith(":"+existing.id));saveDailyItems();saveDailyChecks();dialog.dismiss();if(returnTab==2)showSubjects(false);else showTomorrow(false);});actions.addView(delete,margin(100,52,0,0,10,0));}TextView save=button(editing?"保存修改":"添加物品",true);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));sheet.addView(actions);save.setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError(L("请填写物品名称"));return;}DailyItem target=editing?existing:new DailyItem(UUID.randomUUID().toString(),value,permanent[0],permanent[0]?null:checklistDate);target.name=value;target.permanent=permanent[0];target.date=permanent[0]?null:checklistDate;if(!editing)dailyItems.add(target);saveDailyItems();dialog.dismiss();if(returnTab==2)showSubjects(false);else showTomorrow(false);});showBottomDialog(dialog,sheet);
    }

    private View takeOutSection(List<BagItem> items){
        LinearLayout section=column();section.setPadding(dp(16),dp(16),dp(12),dp(12));section.setBackground(shape(warningContainer,26,0,0));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_take_out);icon.setColorFilter(dark?0xFF3D2B09:Color.WHITE);icon.setPadding(dp(9),dp(9),dp(9),dp(9));icon.setBackground(shape(warningAccent,14,0,0));heading.addView(icon,lp(42,42));
        LinearLayout headingCopy=column();headingCopy.addView(label("这些可以拿出",19,onWarningContainer,true));TextView summary=label(items.size()+" 件物品在当前准备日用不到",13,onWarningContainer,false);summary.setAlpha(.76f);headingCopy.addView(summary,margin(-1,-2,0,3,0,0));LinearLayout.LayoutParams headingCopyParams=new LinearLayout.LayoutParams(0,-2,1);headingCopyParams.setMargins(dp(12),0,0,0);heading.addView(headingCopy,headingCopyParams);section.addView(heading);
        LinearLayout list=column();list.setPadding(dp(6),dp(6),dp(6),dp(2));list.setBackground(shape(surface,20,0,0));section.addView(list,margin(-1,-2,0,12,0,0));
        int[] remaining={items.size()};for(BagItem bagItem:items)list.addView(takeOutRow(bagItem,list,section,summary,remaining),margin(-1,-2,0,0,0,4));return section;
    }

    private View takeOutRow(BagItem bagItem,LinearLayout list,LinearLayout section,TextView summary,int[] remaining){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(5),dp(6),dp(5));row.setMinimumHeight(dp(62));row.setBackground(ripple(Color.TRANSPARENT,16));row.setContentDescription("拿出 "+bagItem.item+"，属于 "+bagItem.subject.name);row.setClickable(true);row.setFocusable(true);
        TextView token=label(bagItem.subject.name.isEmpty()?"物":bagItem.subject.name.substring(0,1),14,onWarningContainer,true);token.setGravity(Gravity.CENTER);token.setBackground(shape(warningContainer,13,0,0));row.addView(token,lp(40,40));
        LinearLayout copy=column();copy.addView(label(bagItem.item,16,text,true));copy.addView(label(bagItem.subject.name+" · 当前准备日用不到",12,muted,false),margin(-1,-2,0,3,0,0));LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.setMargins(dp(12),0,0,0);row.addView(copy,copyParams);
        TextView action=label("拿出",13,warningAccent,true);action.setGravity(Gravity.CENTER);action.setBackground(ripple((warningAccent&0x00FFFFFF)|0x18000000,14));row.addView(action,margin(66,40,10,0,0,0));
        row.setOnClickListener(v->removePackedItem(bagItem,row,list,section,summary,remaining));return row;
    }

    private void removePackedItem(BagItem bagItem,LinearLayout row,LinearLayout list,LinearLayout section,TextView summary,int[] remaining){
        if(!isPacked(bagItem.subject.id,bagItem.item)||!row.isEnabled())return;vibrateTap(true);row.setEnabled(false);setPacked(bagItem.subject.id,bagItem.item,false);saveChecks();updateClearChecksButton();
        Runnable removed=()->{list.removeView(row);remaining[0]--;summary.setText(remaining[0]+" 件物品在当前准备日用不到");if(remaining[0]==0){ViewGroup parent=(ViewGroup)section.getParent();if(parent!=null){if(motionEnabled())section.animate().alpha(0).translationY(-dp(12)).setDuration(170).setInterpolator(emphasized).withEndAction(()->parent.removeView(section)).start();else parent.removeView(section);}}};
        if(!motionEnabled()){removed.run();return;}int startHeight=Math.max(1,row.getHeight());row.animate().alpha(0).translationX(dp(32)).setDuration(150).setInterpolator(emphasized).withEndAction(()->{ValueAnimator collapse=ValueAnimator.ofInt(startHeight,0);collapse.setDuration(180);collapse.setInterpolator(emphasized);collapse.addUpdateListener(animation->{ViewGroup.LayoutParams params=row.getLayoutParams();params.height=(int)animation.getAnimatedValue();row.setLayoutParams(params);});collapse.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator animation){removed.run();}});collapse.start();}).start();
    }

    private void showClearChecksDialog(){
        if(checks.isEmpty()&&!hasDailyChecksFor(checklistDate))return;
        Dialog dialog=bottomDialog();LinearLayout sheet=sheet("清空所有已选物品？","课程携带物和当前准备日的随身物品都会恢复为未选，已有设置不会被删除。");LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView cancel=button("取消",false);TextView confirm=button("确认清空",true);confirm.setTextColor(Color.WHITE);confirm.setBackground(ripple(dark?0xFF8C1D18:0xFFBA1A1A,18));actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams confirmParams=new LinearLayout.LayoutParams(0,dp(52),1);confirmParams.setMargins(dp(12),0,0,0);actions.addView(confirm,confirmParams);sheet.addView(actions);cancel.setOnClickListener(v->dialog.dismiss());confirm.setOnClickListener(v->{checks.clear();String prefix="daily:"+checklistDate+":";dailyChecks.removeIf(saved->saved.startsWith(prefix));saveChecks();saveDailyChecks();dialog.dismiss();showTomorrow(false);});showBottomDialog(dialog,sheet);
    }

    private LocalDate resolveChecklistDate(){
        LocalDate candidate=LocalTime.now().isBefore(LocalTime.of(PREPARATION_SWITCH_HOUR,0))?LocalDate.now():LocalDate.now().plusDays(1);
        if(entries.isEmpty()){while(candidate.getDayOfWeek().getValue()>5)candidate=candidate.plusDays(1);return candidate;}
        for(int i=0;i<8;i++){
            int day=candidate.getDayOfWeek().getValue()-1;
            if(day>=0&&day<5&&!entriesFor(day).isEmpty())return candidate;
            candidate=candidate.plusDays(1);
        }
        return candidate;
    }

    private View subjectChecklist(Subject subject,List<Entry> subjectEntries){
        LinearLayout card=column();card.setPadding(dp(16),dp(subject.noItems?10:14),dp(12),dp(subject.noItems?10:10));card.setBackground(shape(surfaceHigh,24,0,0));
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=column();copy.addView(label(subject.name,18,text,true));copy.addView(label(timesFor(subjectEntries),12,primary,true),margin(-1,-2,0,3,0,0));heading.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        if(subject.noItems){TextView badge=label("无需准备",12,onTertiaryContainer,true);badge.setGravity(Gravity.CENTER);badge.setBackground(shape(tertiaryContainer,13,0,0));heading.addView(badge,margin(76,30,8,0,2,0));}
        ImageView edit=iconButton(R.drawable.ic_chevron_right,"编辑 "+subject.name+" 的携带物");edit.setOnClickListener(v->showSubjectEditor(subject));heading.addView(edit,lp(40,48));card.addView(heading);
        if(subject.noItems){card.setOnClickListener(v->showSubjectEditor(subject));card.setClickable(true);return card;}
        else if(subject.items.isEmpty()){TextView missing=label("尚未设置携带物 · 点此设置",14,primary,true);missing.setPadding(dp(12),dp(12),dp(12),dp(12));missing.setBackground(ripple(primaryContainer,14));missing.setClickable(true);missing.setOnClickListener(v->showSubjectEditor(subject));card.addView(missing,margin(-1,-2,0,10,0,4));}
        else for(String item:subject.items)card.addView(itemCheckRow(subject,item));return card;
    }

    private View itemCheckRow(Subject subject,String item){
        boolean checked=isPacked(subject.id,item);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(3),0,dp(3));
        FrameLayout target=new FrameLayout(this);target.setContentDescription(L((checked?"取消勾选 ":"勾选 ")+item));target.setClickable(true);target.setFocusable(true);target.setBackground(ripple(Color.TRANSPARENT,24));ImageView state=new ImageView(this);setItemCheckVisual(state,checked);target.addView(state,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));row.addView(target,lp(48,48));
        TextView itemLabel=label(item,15,checked?muted:text,false);itemLabel.setPaintFlags(checked?itemLabel.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG:itemLabel.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);row.addView(itemLabel,new LinearLayout.LayoutParams(0,-1,1));
        target.setOnClickListener(v->toggleItemCheck(subject,item,row,target,state,itemLabel));row.setOnClickListener(v->target.performClick());row.setClickable(true);return row;
    }

    private void toggleItemCheck(Subject subject,String item,View row,View target,ImageView state,TextView itemLabel){
        int beforeDone=countDoneForChecklist();
        boolean checked=!isPacked(subject.id,item);setPacked(subject.id,item,checked);saveChecks();updateClearChecksButton();target.setContentDescription(L((checked?"取消勾选 ":"勾选 ")+item));setItemCheckVisual(state,checked);itemLabel.setTextColor(checked?muted:text);itemLabel.setPaintFlags(checked?itemLabel.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG:itemLabel.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);
        vibrateTap(checked);
        if(motionEnabled()){state.setScaleX(.62f);state.setScaleY(.62f);state.setAlpha(.35f);state.animate().scaleX(1.16f).scaleY(1.16f).alpha(1).setDuration(140).setInterpolator(emphasized).withEndAction(()->state.animate().scaleX(1).scaleY(1).setDuration(170).setInterpolator(emphasized).start()).start();row.animate().scaleX(.985f).scaleY(.985f).setDuration(80).withEndAction(()->row.animate().scaleX(1).scaleY(1).setDuration(190).setInterpolator(emphasized).start()).start();}refreshTomorrowProgress(true);
        int afterDone=countDoneForChecklist();if(checked&&tomorrowTotalItems>0&&tomorrowMissingSubjects==0&&beforeDone<tomorrowTotalItems&&afterDone==tomorrowTotalItems)root.postDelayed(this::showCompletionCelebration,260);
    }

    private void showCompletionCelebration(){
        if(completionOverlay!=null)return;
        Vibrator vibrator=deviceVibrator();if(prefs.getBoolean(AppText.KEY_HAPTICS,true)&&vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,55,55,90,55,140},new int[]{0,180,0,220,0,255},-1));
        FrameLayout overlay=new FrameLayout(this);completionOverlay=overlay;overlay.setClickable(true);overlay.setFocusable(true);overlay.setContentDescription(L("全部装好了"));overlay.setBackgroundColor(dark?0xEE111318:0xEEDFE6FF);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=column();center.setGravity(Gravity.CENTER);ImageView badge=new ImageView(this);badge.setImageResource(R.drawable.ic_check);badge.setColorFilter(Color.WHITE);badge.setPadding(dp(28),dp(28),dp(28),dp(28));badge.setBackground(shape(success,64,0,0));center.addView(badge,lp(128,128));TextView title=label("书包收好啦",34,dark?Color.WHITE:0xFF17213B,true);title.setGravity(Gravity.CENTER);center.addView(title,margin(-2,-2,0,24,0,0));TextView copy=label("这次要带的物品已经全部装好",16,dark?0xFFDDE2F2:0xFF44506A,false);copy.setGravity(Gravity.CENTER);center.addView(copy,margin(-2,-2,0,8,0,0));overlay.addView(center,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        int[] colors={primary,success,secondary,0xFFFFB95C,0xFFFF7A8A};for(int i=0;i<30;i++){View dot=new View(this);int size=dp(6+(i%4)*2);dot.setBackground(shape(colors[i%colors.length],size,0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(size,size,Gravity.CENTER);overlay.addView(dot,p);double angle=Math.PI*2*i/30d;float distance=dp(150+(i%6)*34);dot.setAlpha(0f);dot.setScaleX(.3f);dot.setScaleY(.3f);if(motionEnabled())dot.animate().alpha(i%3==0?.35f:.9f).scaleX(1).scaleY(1).translationX((float)Math.cos(angle)*distance).translationY((float)Math.sin(angle)*distance).rotation(160+(i%5)*55).setStartDelay((i%8)*22L).setDuration(720).setInterpolator(emphasized).withEndAction(()->dot.animate().alpha(0).translationYBy(dp(40)).setDuration(420).start()).start();}
        if(motionEnabled()){overlay.setAlpha(0);overlay.animate().alpha(1).setDuration(180).start();center.setScaleX(.55f);center.setScaleY(.55f);center.setAlpha(0);center.animate().scaleX(1.05f).scaleY(1.05f).alpha(1).setDuration(480).setInterpolator(emphasized).withEndAction(()->center.animate().scaleX(1).scaleY(1).setDuration(180).start()).start();}
        Runnable dismiss=()->{if(completionOverlay!=overlay)return;if(motionEnabled())overlay.animate().alpha(0).setDuration(240).withEndAction(()->{root.removeView(overlay);completionOverlay=null;}).start();else{root.removeView(overlay);completionOverlay=null;}};overlay.setOnClickListener(v->dismiss.run());overlay.postDelayed(dismiss,motionEnabled()?2100:1000);
    }

    private void setItemCheckVisual(ImageView state,boolean checked){state.setPadding(dp(5),dp(5),dp(5),dp(5));if(checked){state.setImageResource(R.drawable.ic_check);state.setColorFilter(dark?0xFF14371D:Color.WHITE);state.setBackground(shape(success,14,0,0));}else{state.setImageDrawable(null);state.setBackground(shape(surfaceHigh,14,2,primary));}}
    private void refreshTomorrowProgress(boolean animate){int done=countDoneForChecklist();tomorrowStatus.setText(L(progressStatus(tomorrowTotalItems,done,tomorrowMissingSubjects)));tomorrowProgressLabel.setText(L(progressLabel(tomorrowTotalItems,done,tomorrowMissingSubjects)));if(done==tomorrowTotalItems&&tomorrowMissingSubjects==0)PackingReminderReceiver.dismissNotification(this);if(tomorrowProgressRow!=null){tomorrowProgressRow.setContentDescription(L("已装好 "+done+" 件，共 "+tomorrowTotalItems+" 件"));for(int i=0;i<tomorrowProgressRow.getChildCount();i++){View segment=tomorrowProgressRow.getChildAt(i);segment.setBackground(shape(i<done?primary:secondary,4,0,0));if(animate&&motionEnabled()){segment.setScaleY(.55f);segment.animate().scaleY(1).setDuration(220).setInterpolator(emphasized).start();}}}}
    private String progressStatus(int total,int done,int missing){if(total==0&&missing>0)return"先设置携带物";if(total==0)return"无需准备物品";if(done==total&&missing==0)return"全部装好了";return"还差 "+(total-done)+" 件";}
    private String progressLabel(int total,int done,int missing){if(missing>0)return missing+" 个科目尚未设置 · "+done+" / "+total+" 已装好";return total==0?"这些科目都确认无需携带物品":done+" / "+total+" 已装好";}
    private void buildProgressSegments(LinearLayout row,int total,int done){for(int i=0;i<total;i++){View segment=new View(this);segment.setBackground(shape(i<done?primary:secondary,4,0,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(8),1);if(i>0)p.setMargins(dp(5),0,0,0);row.addView(segment,p);}row.setContentDescription(L("已装好 "+done+" 件，共 "+total+" 件"));}

    private View buildSchedulePage(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=column();page.setPadding(dp(20),dp(22),dp(20),dp(112));addPageHeader(page,"本周课表","课表只安排科目，携带物统一在科目库管理",18);page.addView(buildDaySelector(),margin(-1,52,0,0,0,10));
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);TextView importText=button("批量导入课表",false);importText.setContentDescription(L("从图片或文字导入课程表"));importText.setOnClickListener(v->showScheduleTextImport());tools.addView(importText,new LinearLayout.LayoutParams(0,dp(50),1));TextView batchTime=button("设置节次时间",false);batchTime.setContentDescription(L("批量设置星期一到星期五的节次时间"));batchTime.setOnClickListener(v->showBatchTimeEditor());LinearLayout.LayoutParams timeParams=new LinearLayout.LayoutParams(0,dp(50),1);timeParams.setMargins(dp(16),0,0,0);tools.addView(batchTime,timeParams);page.addView(tools,margin(-1,50,0,0,0,22));
        List<Entry> day=entriesFor(selectedDay);LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(label(DAYS[selectedDay],22,text,true),new LinearLayout.LayoutParams(0,dp(40),1));TextView count=label(day.size()+" 节课",13,onPrimaryContainer,true);count.setGravity(Gravity.CENTER);count.setBackground(shape(primaryContainer,16,0,0));heading.addView(count,lp(english?88:70,34));page.addView(heading,margin(-1,40,0,0,0,10));if(day.isEmpty())page.addView(emptyState("这天没有课","可以批量导入，也可以点“排一节课”手动添加。"));else page.addView(scheduleList(day));scroll.addView(page);return scroll;
    }

    private void showScheduleTextImport(){
        Dialog dialog=bottomDialog();scheduleImportDialog=dialog;LinearLayout content=sheet("导入课表","选择图片离线识别，或粘贴课程文字；确认预览后才会写入");
        TextView imageAction=button("选择课程表图片",false);scheduleImportImageAction=imageAction;imageAction.setContentDescription(L("从相册选择课程表图片并离线识别"));content.addView(imageAction,margin(-1,52,0,0,0,16));imageAction.setOnClickListener(v->startScheduleImageImport());
        TextView example=label(T("示例：星期一：语文，数学，英语\n星期二：历史，地理，体育","Example: Monday: Math, English, PE\nTuesday: History, Science, Art"),13,onPrimaryContainer,false);example.setPadding(dp(16),dp(13),dp(16),dp(13));example.setBackground(shape(primaryContainer,18,0,0));content.addView(example,margin(-1,-2,0,0,0,14));
        LinearLayout fieldGroup=column();fieldGroup.addView(label("识别与课程文字",13,muted,true),margin(-1,-2,2,0,0,7));EditText input=multiLineInput("选择图片后会在这里显示识别结果，也可以直接粘贴并修改");scheduleImportInput=input;fieldGroup.addView(input,lp(-1,190));content.addView(fieldGroup,margin(-1,-2,0,0,0,12));
        TextView hint=label("先在本机识别文字与位置，再按星期列和节次行重建；请检查错字和分组",12,muted,false);hint.setLineSpacing(dp(2),1f);content.addView(hint,margin(-1,-2,2,0,0,14));TextView error=label("",13,dark?0xFFFFB4AB:0xFFBA1A1A,true);scheduleImportError=error;error.setVisibility(View.GONE);content.addView(error,margin(-1,-2,2,0,0,10));TextView preview=button("生成导入预览",true);content.addView(preview,lp(-1,52));preview.setOnClickListener(v->{ScheduleTextParser.Result result=ScheduleTextParser.parse(input.getText().toString());if(result.isEmpty()){showBatchError(error,"没有识别到课程，请检查星期和科目的排列");return;}dialog.dismiss();root.postDelayed(()->showScheduleImportPreview(result),120);});
        dialog.setOnDismissListener(ignored->{if(scheduleImportDialog==dialog){scheduleImportDialog=null;scheduleImportInput=null;scheduleImportImageAction=null;scheduleImportError=null;}});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);showBottomDialog(dialog,scroll,(int)(getResources().getDisplayMetrics().heightPixels*.9f));
    }

    private void startScheduleImageImport(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/*");startActivityForResult(intent,REQUEST_SCHEDULE_IMAGE);
    }

    private void recognizeScheduleImage(Uri uri){
        if(scheduleImportDialog==null||scheduleImportInput==null)return;
        setScheduleImageLoading(true);if(scheduleImportError!=null)scheduleImportError.setVisibility(View.GONE);
        try{
            InputImage image=InputImage.fromFilePath(this,uri);TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
            recognizer.process(image).addOnSuccessListener(result->{String recognized=formatScheduleOcr(result);if(recognized.isEmpty())recognized=result.getText().trim();if(recognized.isEmpty()){showScheduleImageError("图片中没有识别到文字，请换一张更清晰、正对课表的图片");return;}scheduleImportInput.setText(recognized);scheduleImportInput.setSelection(recognized.length());showTransientMessage("识别完成，请先检查文字再生成预览");}).addOnFailureListener(error->showScheduleImageError("图片识别失败，请换一张图片重试")).addOnCompleteListener(task->{recognizer.close();setScheduleImageLoading(false);});
        }catch(Exception error){setScheduleImageLoading(false);showScheduleImageError("无法读取这张图片，请换一张图片重试");}
    }

    private void setScheduleImageLoading(boolean loading){if(scheduleImportImageAction==null)return;scheduleImportImageAction.setEnabled(!loading);scheduleImportImageAction.setAlpha(loading?.55f:1f);scheduleImportImageAction.setText(L(loading?"正在离线识别…":"选择课程表图片"));}
    private void showScheduleImageError(String message){if(scheduleImportError!=null)showBatchError(scheduleImportError,message);}
    private String formatScheduleOcr(Text result){List<ScheduleOcrLayout.Token> tokens=new ArrayList<>();for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){android.graphics.Rect box=line.getBoundingBox();if(box!=null)tokens.add(new ScheduleOcrLayout.Token(line.getText(),box.left,box.top,box.right,box.bottom));}return ScheduleOcrLayout.format(tokens);}

    private EditText multiLineInput(String hint){EditText field=input(hint,"");field.setSingleLine(false);field.setGravity(Gravity.TOP|Gravity.START);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);field.setPadding(dp(16),dp(14),dp(16),dp(14));field.setMaxLines(12);field.setHorizontallyScrolling(false);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(50_000)});return field;}

    private void showScheduleImportPreview(ScheduleTextParser.Result result){
        List<ScheduleTextParser.Lesson> lessons=result.lessons();boolean[] replace={false};Dialog dialog=bottomDialog();LinearLayout content=sheet("检查导入预览","确认星期、节次和科目；默认跳过已经排课的位置");TextView summary=label("",15,onPrimaryContainer,true);summary.setPadding(dp(16),dp(14),dp(16),dp(14));summary.setBackground(shape(primaryContainer,18,0,0));content.addView(summary,margin(-1,-2,0,0,0,12));
        LinearLayout replaceRow=toggleRow("替换相同星期和节次的课程",replace);content.addView(replaceRow,lp(-1,56));content.addView(label("关闭时会安全跳过冲突，不会覆盖现有课程",12,muted,false),margin(-1,-2,12,3,0,16));
        LinearLayout previewList=column();previewList.setPadding(dp(8),dp(6),dp(8),dp(6));previewList.setBackground(shape(surfaceHigh,22,0,0));List<TextView> statuses=new ArrayList<>();int lastDay=-1;for(ScheduleTextParser.Lesson lesson:lessons){if(lesson.day!=lastDay){TextView day=label(DAYS[lesson.day],14,primary,true);day.setPadding(dp(10),dp(lastDay<0?7:15),dp(10),dp(5));previewList.addView(day);lastDay=lesson.day;}LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);TextView period=label(String.valueOf(lesson.period),14,onPrimaryContainer,true);period.setGravity(Gravity.CENTER);period.setBackground(shape(primaryContainer,12,0,0));row.addView(period,margin(40,40,4,3,10,3));LinearLayout copy=column();copy.addView(label(lesson.subject,15,text,true));TextView status=label("",12,muted,false);copy.addView(status,margin(-1,-2,0,3,0,0));statuses.add(status);row.addView(copy,new LinearLayout.LayoutParams(0,dp(54),1));previewList.addView(row);}content.addView(previewList,margin(-1,-2,0,0,0,16));
        TextView confirm=button("",true);content.addView(confirm,lp(-1,52));Runnable refresh=()->{int conflicts=0;LinkedHashSet<String> newSubjects=new LinkedHashSet<>();for(int i=0;i<lessons.size();i++){ScheduleTextParser.Lesson lesson=lessons.get(i);boolean conflict=entryAtSlot(lesson.day,lesson.period)!=null;if(conflict)conflicts++;else statuses.get(i).setText(L("将导入"));if(conflict)statuses.get(i).setText(L(replace[0]?"已有课程 · 将替换":"已有课程 · 将跳过"));if(findSubjectByName(lesson.subject)==null)newSubjects.add(lesson.subject.toLowerCase(Locale.ROOT));}int importCount=replace[0]?lessons.size():lessons.size()-conflicts;summary.setText(L(importCount+" 节课将导入 · "+newSubjects.size()+" 个新科目")+(result.warningCount>0?L(" · "+result.warningCount+" 处内容已忽略"):""));confirm.setText(L(importCount>0?"确认导入 "+importCount+" 节课":"没有可导入的课程"));confirm.setEnabled(importCount>0);confirm.setAlpha(importCount>0?1f:.42f);};replaceRow.setOnClickListener(v->{replace[0]=!replace[0];updateToggleRow(replaceRow,replace[0]);refresh.run();animateValue(replaceRow);});confirm.setOnClickListener(v->{int imported=applyScheduleImport(lessons,replace[0]);dialog.dismiss();showSchedule(false);showTransientMessage(T("已导入 "+imported+" 节课，请继续设置节次时间",imported+" lessons imported. Set lesson times next"));});refresh.run();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);showBottomDialog(dialog,scroll,(int)(getResources().getDisplayMetrics().heightPixels*.92f));
    }

    private Entry entryAtSlot(int day,int period){for(Entry entry:entries)if(entry.day==day&&entry.period==period)return entry;return null;}
    private Subject findSubjectByName(String name){for(Subject subject:subjects)if(subject.name.trim().equalsIgnoreCase(name.trim()))return subject;return null;}
    private int applyScheduleImport(List<ScheduleTextParser.Lesson> lessons,boolean replace){int imported=0;for(ScheduleTextParser.Lesson lesson:lessons){Entry existing=entryAtSlot(lesson.day,lesson.period);if(existing!=null&&!replace)continue;String time=existing==null?timeSlot(lesson.day,lesson.period,"—"):existing.time;if(existing!=null)entries.removeIf(entry->entry.day==lesson.day&&entry.period==lesson.period);Subject subject=findSubjectByName(lesson.subject);if(subject==null){subject=new Subject(UUID.randomUUID().toString(),lesson.subject,new ArrayList<>(),false);subjects.add(subject);}entries.add(new Entry(UUID.randomUUID().toString(),subject.id,lesson.day,lesson.period,time));imported++;}if(!lessons.isEmpty())selectedDay=lessons.get(0).day;saveAll();return imported;}
    private View buildDaySelector(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));row.setBackground(shape(surfaceHigh,22,0,0));String[] shortDays=english?new String[]{"M","T","W","T","F"}:new String[]{"一","二","三","四","五"};for(int i=0;i<DAYS.length;i++){final int day=i;TextView chip=label(shortDays[i],14,i==selectedDay?onPrimaryContainer:muted,true);chip.setGravity(Gravity.CENTER);chip.setContentDescription(L(DAYS[i]));chip.setBackground(ripple(i==selectedDay?primaryContainer:Color.TRANSPARENT,18));chip.setOnClickListener(v->{if(selectedDay!=day){boolean back=day<selectedDay;selectedDay=day;swapPage(buildSchedulePage(),back,true);}});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(44),1);if(i>0)params.setMargins(dp(2),0,0,0);row.addView(chip,params);}return row;}
    private View scheduleList(List<Entry> list){LinearLayout group=column();group.setBackground(shape(surfaceHigh,24,0,0));group.setClipToOutline(true);for(int i=0;i<list.size();i++){Entry entry=list.get(i);group.addView(scheduleRow(entry,subjectById(entry.subjectId)));if(i<list.size()-1){View divider=new View(this);divider.setBackgroundColor(outline);group.addView(divider,margin(-1,1,72,0,16,0));}}return group;}
    private View scheduleRow(Entry entry,Subject subject){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(8),dp(10));row.setMinimumHeight(dp(84));row.setBackground(ripple(surfaceHigh,0));TextView period=label(String.valueOf(entry.period),15,onPrimaryContainer,true);period.setGravity(Gravity.CENTER);period.setBackground(shape(primaryContainer,14,0,0));row.addView(period,margin(48,48,0,0,10,0));LinearLayout body=column();String name=subject==null?"已删除的科目":subject.name;body.addView(label(name,17,text,true));body.addView(label(entry.time,12,primary,true),margin(-1,-2,0,3,0,0));TextView summary=label(subject==null?"请重新选择科目":subjectSummary(subject),13,subject!=null&&!subject.noItems&&subject.items.isEmpty()?primary:muted,false);summary.setMaxLines(2);body.addView(summary,margin(-1,-2,0,2,0,0));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=iconButton(R.drawable.ic_chevron_right,"编辑这节课");row.addView(arrow,lp(40,48));row.setContentDescription("编辑第 "+entry.period+" 节 "+name);row.setOnClickListener(v->showScheduleEditor(entry));arrow.setOnClickListener(v->showScheduleEditor(entry));row.setClickable(true);return row;}

    private View buildSubjectsPage(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=column();page.setPadding(dp(20),dp(22),dp(20),dp(112));addPageHeader(page,"科目库","每个科目只设置一次携带物，所有课表自动同步",18);page.addView(dailyItemsManagementRow(),margin(-1,72,0,0,0,22));if(subjects.isEmpty())page.addView(emptyState("还没有科目","先新建科目和需要携带的物品，再去课表排课。"));else{LinearLayout list=column();list.setBackground(shape(surfaceHigh,24,0,0));list.setClipToOutline(true);for(int i=0;i<subjects.size();i++){list.addView(subjectRow(subjects.get(i)));if(i<subjects.size()-1){View divider=new View(this);divider.setBackgroundColor(outline);list.addView(divider,margin(-1,1,72,0,16,0));}}page.addView(list);}scroll.addView(page);return scroll;}
    private View dailyItemsManagementRow(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(8),dp(8),dp(8));row.setBackground(ripple(surfaceHigh,20));TextView token=label("随",16,onPrimaryContainer,true);token.setGravity(Gravity.CENTER);token.setBackground(shape(primaryContainer,14,0,0));row.addView(token,margin(48,48,0,0,12,0));LinearLayout copy=column();copy.addView(label("每日随身物品",16,text,true));int permanentCount=0;for(DailyItem item:dailyItems)if(item.permanent)permanentCount++;String summary=dailyItems.isEmpty()?"未启用 · 可选功能":dailyItems.size()+" 件 · "+permanentCount+" 件每天出现";copy.addView(label(summary,13,muted,false),margin(-1,-2,0,3,0,0));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=iconButton(R.drawable.ic_chevron_right,"管理每日随身物品");row.addView(arrow,lp(40,48));row.setContentDescription("每日随身物品，"+summary);row.setOnClickListener(v->{if(dailyItems.isEmpty())showDailyItemEditor(null);else showDailyItemsManager();});arrow.setOnClickListener(v->{if(dailyItems.isEmpty())showDailyItemEditor(null);else showDailyItemsManager();});row.setClickable(true);return row;}
    private View subjectRow(Subject subject){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(8),dp(12));row.setBackground(ripple(surfaceHigh,0));TextView token=label(subject.name.isEmpty()?"科":subject.name.substring(0,1),16,onPrimaryContainer,true);token.setGravity(Gravity.CENTER);token.setBackground(shape(primaryContainer,14,0,0));row.addView(token,margin(48,48,0,0,12,0));LinearLayout body=column();body.addView(label(subject.name,17,text,true));TextView summary=label(subjectSummary(subject),13,!subject.noItems&&subject.items.isEmpty()?primary:muted,false);summary.setMaxLines(3);body.addView(summary,margin(-1,-2,0,4,0,0));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=iconButton(R.drawable.ic_chevron_right,"编辑 "+subject.name);row.addView(arrow,lp(40,48));row.setOnClickListener(v->showSubjectEditor(subject));arrow.setOnClickListener(v->showSubjectEditor(subject));row.setClickable(true);return row;}

    private void showSubjectEditor(Subject existing){boolean editing=existing!=null;Dialog dialog=bottomDialog();LinearLayout sheet=sheet("科目设置",editing?"修改一次，所有课表中的该科目都会同步":"先建立科目，再把它排进课表");EditText name=input("例如：数学",editing?existing.name:"");boolean[] noItems={editing&&existing.noItems};List<EditText> itemInputs=new ArrayList<>();LinearLayout itemFields=column();if(editing&&!existing.items.isEmpty()){for(String item:existing.items)addItemInputRow(itemFields,itemInputs,item);}else addItemInputRow(itemFields,itemInputs,"");LinearLayout addItem=iconTextButton(R.drawable.ic_add,"添加一件物品",primary);addItem.setOnClickListener(v->addItemInputRow(itemFields,itemInputs,""));LinearLayout itemGroup=column();itemGroup.addView(label("默认携带物",13,muted,true),margin(-1,-2,2,0,0,7));itemGroup.addView(itemFields);itemGroup.addView(addItem,margin(-1,48,0,8,0,0));LinearLayout noItemsRow=toggleRow("无需携带物品",noItems);setItemEditorEnabled(itemFields,addItem,!noItems[0]);noItemsRow.setOnClickListener(v->{noItems[0]=!noItems[0];updateToggleRow(noItemsRow,noItems[0]);setItemEditorEnabled(itemFields,addItem,!noItems[0]);});sheet.addView(fieldGroup("科目名称",name),margin(-1,-2,0,0,0,12));sheet.addView(itemGroup,margin(-1,-2,0,0,0,10));sheet.addView(noItemsRow,margin(-1,52,0,0,0,18));LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);if(editing){int useCount=entriesForSubject(existing.id).size();TextView delete=button(useCount==0?"删除":"删除 · "+useCount+" 节",false);delete.setTextColor(dark?0xFFFFB4AB:0xFFBA1A1A);delete.setOnClickListener(v->{subjects.remove(existing);entries.removeIf(e->e.subjectId.equals(existing.id));saveAll();dialog.dismiss();showSubjects(false);});actions.addView(delete,margin(118,52,0,0,10,0));}TextView save=button("保存科目",true);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));sheet.addView(actions);save.setOnClickListener(v->{String subjectName=name.getText().toString().trim();if(subjectName.isEmpty()){name.setError("请填写科目名称");return;}Subject target=editing?existing:new Subject(UUID.randomUUID().toString(),subjectName,new ArrayList<>(),false);target.name=subjectName;target.noItems=noItems[0];target.items=noItems[0]?new ArrayList<>():collectItemInputs(itemInputs);if(!editing)subjects.add(target);saveAll();dialog.dismiss();if(selectedTab==2)showSubjects(false);else if(selectedTab==0)showSchedule(false);else showTomorrow(false);});showBottomDialog(dialog,sheet);}

    private void addItemInputRow(LinearLayout container,List<EditText> inputs,String value){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);EditText field=input("例如：课本",value);row.addView(field,new LinearLayout.LayoutParams(0,dp(54),1));ImageView remove=iconButton(R.drawable.ic_remove,"移除这件物品");row.addView(remove,margin(48,48,8,3,0,3));container.addView(row,margin(-1,-2,0,0,0,8));inputs.add(field);remove.setOnClickListener(v->{if(inputs.size()==1){field.setText("");field.requestFocus();return;}inputs.remove(field);container.removeView(row);});
    }
    private void setItemEditorEnabled(LinearLayout container,View addItem,boolean enabled){container.setEnabled(enabled);container.setAlpha(enabled?1f:.38f);addItem.setEnabled(enabled);addItem.setAlpha(enabled?1f:.38f);for(int i=0;i<container.getChildCount();i++){View row=container.getChildAt(i);row.setEnabled(enabled);if(row instanceof LinearLayout)for(int n=0;n<((LinearLayout)row).getChildCount();n++)((LinearLayout)row).getChildAt(n).setEnabled(enabled);}}
    private List<String> collectItemInputs(List<EditText> inputs){LinkedHashSet<String> unique=new LinkedHashSet<>();for(EditText input:inputs){String value=input.getText().toString().trim();if(!value.isEmpty())unique.add(value);}return new ArrayList<>(unique);}

    private void showScheduleEditor(Entry existing){
        if(subjects.isEmpty()){showSubjectEditor(null);return;}
        boolean editing=existing!=null;Dialog dialog=bottomDialog();LinearLayout sheet=sheet(editing?"编辑排课":"排一节课","点选开始和结束时间，不需要手动输入格式");
        Subject initial=editing?subjectById(existing.subjectId):subjects.get(0);if(initial==null)initial=subjects.get(0);String[] subjectId={initial.id};TextView subjectField=label(initial.name,16,text,false);subjectField.setPadding(dp(16),0,dp(16),0);subjectField.setBackground(ripple(surfaceHigh,15));subjectField.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_chevron_right,0);subjectField.setClickable(true);subjectField.setOnClickListener(v->showSubjectPicker(subjectId,subjectField));
        int[] day={editing?existing.day:selectedDay};int[] period={editing?existing.period:1};String initialTime=editing?existing.time:timeSlot(day[0],period[0],"08:50–09:30");int[] range=parseTimeRange(initialTime);TextView startField=timeChoice(range[0],"选择开始时间");TextView endField=timeChoice(range[1],"选择结束时间");Runnable renderRange=()->{startField.setText(formatClock(range[0]));endField.setText(formatClock(range[1]));};
        Runnable loadSlot=()->{int[] slot=parseTimeRange(timeSlot(day[0],period[0],formatTimeRange(range[0],range[1])));range[0]=slot[0];range[1]=slot[1];renderRange.run();};
        View daySelector=buildEditorDaySelector(day,loadSlot);View periodSelector=numberStepper(period,1,12,1,"第 %d 节",loadSlot);
        startField.setOnClickListener(v->showTimePicker("开始时间",range[0],minutes->{range[0]=minutes;if(range[1]<=range[0])range[1]=Math.min(1439,range[0]+40);renderRange.run();animateValue(startField);}));endField.setOnClickListener(v->showTimePicker("结束时间",range[1],minutes->{range[1]=minutes;renderRange.run();animateValue(endField);}));
        sheet.addView(fieldGroup("选择科目",subjectField),margin(-1,-2,0,0,0,12));sheet.addView(fieldGroup("星期",daySelector),margin(-1,-2,0,0,0,12));sheet.addView(fieldGroup("节次",periodSelector),margin(-1,-2,0,0,0,12));LinearLayout times=new LinearLayout(this);times.setOrientation(LinearLayout.HORIZONTAL);times.addView(fieldGroup("开始",startField),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams endParams=new LinearLayout.LayoutParams(0,-2,1);endParams.setMargins(dp(10),0,0,0);times.addView(fieldGroup("结束",endField),endParams);sheet.addView(times,margin(-1,-2,0,0,0,18));
        TextView timeError=label("结束时间需要晚于开始时间",13,dark?0xFFFFB4AB:0xFFBA1A1A,true);timeError.setVisibility(View.GONE);sheet.addView(timeError,margin(-1,-2,2,-10,0,12));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);if(editing){TextView delete=button("移除这节",false);delete.setTextColor(dark?0xFFFFB4AB:0xFFBA1A1A);delete.setOnClickListener(v->{entries.remove(existing);saveAll();dialog.dismiss();showSchedule(false);});actions.addView(delete,margin(118,52,0,0,10,0));}TextView save=button("保存排课",true);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));sheet.addView(actions);save.setOnClickListener(v->{if(range[1]<=range[0]){timeError.setVisibility(View.VISIBLE);return;}String value=formatTimeRange(range[0],range[1]);Entry target=editing?existing:new Entry(UUID.randomUUID().toString(),subjectId[0],day[0],period[0],value);target.subjectId=subjectId[0];target.day=day[0];target.period=period[0];target.time=value;if(!editing)entries.add(target);prefs.edit().putString(timeSlotKey(day[0],period[0]),value).apply();selectedDay=day[0];saveAll();dialog.dismiss();showSchedule(false);});showBottomDialog(dialog,sheet);
    }

    private void showSubjectPicker(String[] selectedId,TextView field){Dialog picker=bottomDialog();LinearLayout sheet=sheet("选择科目","携带物会自动使用科目库中的设置");ScrollView scroll=new ScrollView(this);LinearLayout list=column();for(Subject subject:subjects){TextView row=label(subject.name,16,text,true);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(ripple(subject.id.equals(selectedId[0])?primaryContainer:surfaceHigh,14));row.setContentDescription("选择 "+subject.name);row.setOnClickListener(v->{selectedId[0]=subject.id;field.setText(subject.name);picker.dismiss();});row.setClickable(true);list.addView(row,margin(-1,-2,0,0,0,8));}scroll.addView(list);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showBottomDialog(picker,sheet,(int)(getResources().getDisplayMetrics().heightPixels*.72f));}
    private LinearLayout toggleRow(String title,boolean[] state){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setBackground(ripple(surfaceHigh,16));ImageView icon=new ImageView(this);row.addView(icon,lp(28,28));TextView titleView=label(title,15,text,true);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-1,1);titleParams.setMargins(dp(12),0,0,0);row.addView(titleView,titleParams);updateToggleRow(row,state[0]);row.setClickable(true);return row;}
    private void updateToggleRow(LinearLayout row,boolean checked){ImageView icon=(ImageView)row.getChildAt(0);setItemCheckVisual(icon,checked);row.setContentDescription(L((checked?"已选择 ":"未选择 ")+((TextView)row.getChildAt(1)).getText()));}
    private View buildEditorDaySelector(int[] selected,Runnable changed){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));row.setBackground(shape(surfaceHigh,18,0,0));TextView[] choices=new TextView[DAYS.length];String[] shortDays=english?new String[]{"M","T","W","T","F"}:new String[]{"一","二","三","四","五"};for(int i=0;i<DAYS.length;i++){final int day=i;TextView choice=label(shortDays[i],14,i==selected[0]?onPrimaryContainer:muted,true);choice.setGravity(Gravity.CENTER);choice.setContentDescription(L(DAYS[i]));choice.setBackground(ripple(i==selected[0]?primaryContainer:Color.TRANSPARENT,15));choice.setOnClickListener(v->{selected[0]=day;for(int n=0;n<choices.length;n++){choices[n].setTextColor(n==day?onPrimaryContainer:muted);choices[n].setBackground(ripple(n==day?primaryContainer:Color.TRANSPARENT,15));}if(changed!=null)changed.run();});choices[i]=choice;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)params.setMargins(dp(2),0,0,0);row.addView(choice,params);}return row;}

    private void showBatchTimeEditor(){
        Dialog dialog=bottomDialog();LinearLayout content=sheet("批量设置节次时间","选择多个星期，按上课时长和课间时长自动计算；只更新时间，不会新增或删除课程");boolean[] days=new boolean[DAYS.length];days[selectedDay]=true;int[] fromPeriod={1};int[] count={Math.max(1,Math.min(8,12-fromPeriod[0]+1))};int[] firstRange=parseTimeRange(timeSlot(selectedDay,1,"08:20–09:00"));int[] startMinutes={firstRange[0]};int[] lessonMinutes={Math.max(5,firstRange[1]-firstRange[0])};int[] breakMinutes={10};Runnable[] refreshPreview={null};Runnable refresh=()->{if(refreshPreview[0]!=null)refreshPreview[0].run();};
        content.addView(fieldGroup("应用到星期",buildMultiDaySelector(days)),margin(-1,-2,0,0,0,12));
        LinearLayout periodControls=new LinearLayout(this);periodControls.setOrientation(LinearLayout.HORIZONTAL);periodControls.addView(fieldGroup("从第几节开始",numberStepper(fromPeriod,1,12,1,"第 %d 节",refresh)),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(0,-2,1);countParams.setMargins(dp(10),0,0,0);periodControls.addView(fieldGroup("生成几节",numberStepper(count,1,12,1,"%d 节",refresh)),countParams);content.addView(periodControls,margin(-1,-2,0,0,0,12));
        TextView startField=timeChoice(startMinutes[0],"选择第一节开始时间");startField.setOnClickListener(v->showTimePicker("第一节开始时间",startMinutes[0],minutes->{startMinutes[0]=minutes;startField.setText(formatClock(minutes));animateValue(startField);refresh.run();}));content.addView(fieldGroup("第一节开始",startField),margin(-1,-2,0,0,0,12));
        LinearLayout durationControls=new LinearLayout(this);durationControls.setOrientation(LinearLayout.HORIZONTAL);durationControls.addView(fieldGroup("每节时长",numberStepper(lessonMinutes,5,180,5,"%d 分钟",refresh)),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams breakParams=new LinearLayout.LayoutParams(0,-2,1);breakParams.setMargins(dp(10),0,0,0);durationControls.addView(fieldGroup("课间时长",numberStepper(breakMinutes,0,120,5,"%d 分钟",refresh)),breakParams);content.addView(durationControls,margin(-1,-2,0,0,0,12));
        LinearLayout previewGroup=column();previewGroup.addView(label("时间预览",13,muted,true),margin(-1,-2,2,0,0,7));TextView preview=label("",14,onPrimaryContainer,true);preview.setPadding(dp(16),dp(14),dp(16),dp(14));preview.setMinHeight(dp(106));preview.setBackground(shape(primaryContainer,18,0,0));previewGroup.addView(preview,lp(-1,-2));content.addView(previewGroup,margin(-1,-2,0,0,0,12));refreshPreview[0]=()->preview.setText(generatedTimePreview(fromPeriod[0],count[0],startMinutes[0],lessonMinutes[0],breakMinutes[0]));refresh.run();
        TextView error=label("",13,dark?0xFFFFB4AB:0xFFBA1A1A,true);error.setVisibility(View.GONE);content.addView(error,margin(-1,-2,2,0,0,10));TextView save=button("应用批量时间",true);content.addView(save,margin(-1,52,0,0,0,0));save.setOnClickListener(v->{int selectedCount=0;int firstSelected=-1;for(int i=0;i<days.length;i++)if(days[i]){selectedCount++;if(firstSelected<0)firstSelected=i;}if(selectedCount==0){showBatchError(error,"至少选择一个星期");return;}if(fromPeriod[0]+count[0]>13){showBatchError(error,"起始节次和生成节数超过第 12 节");return;}int lastEnd=startMinutes[0]+count[0]*lessonMinutes[0]+(count[0]-1)*breakMinutes[0];if(lastEnd>1439){showBatchError(error,"生成后的结束时间超出当天，请减少节数或时长");return;}SharedPreferences.Editor editor=prefs.edit();for(int day=0;day<days.length;day++)if(days[day])for(int i=0;i<count[0];i++){int period=fromPeriod[0]+i;int start=startMinutes[0]+i*(lessonMinutes[0]+breakMinutes[0]);String value=formatTimeRange(start,start+lessonMinutes[0]);editor.putString(timeSlotKey(day,period),value);for(Entry entry:entries)if(entry.day==day&&entry.period==period)entry.time=value;}editor.apply();selectedDay=firstSelected;saveAll();dialog.dismiss();showSchedule(false);});
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(content);showBottomDialog(dialog,scroll,(int)(getResources().getDisplayMetrics().heightPixels*.9f));
    }

    private View buildMultiDaySelector(boolean[] selected){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));row.setBackground(shape(surfaceHigh,18,0,0));TextView[] choices=new TextView[DAYS.length];String[] shortDays=english?new String[]{"M","T","W","T","F"}:new String[]{"一","二","三","四","五"};for(int i=0;i<DAYS.length;i++){final int day=i;TextView choice=label(shortDays[i],14,selected[i]?onPrimaryContainer:muted,true);choice.setGravity(Gravity.CENTER);choice.setContentDescription(L((selected[i]?"取消 ":"选择 ")+DAYS[i]));choice.setBackground(ripple(selected[i]?primaryContainer:Color.TRANSPARENT,15));choice.setOnClickListener(v->{selected[day]=!selected[day];choice.setTextColor(selected[day]?onPrimaryContainer:muted);choice.setBackground(ripple(selected[day]?primaryContainer:Color.TRANSPARENT,15));choice.setContentDescription(L((selected[day]?"取消 ":"选择 ")+DAYS[day]));animateValue(choice);});choices[i]=choice;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)params.setMargins(dp(2),0,0,0);row.addView(choice,params);}return row;}

    private LinearLayout numberStepper(int[] value,int min,int max,int step,String format,Runnable changed){String localized=english?format.replace("第 %d 节","Period %d").replace("%d 节","%d periods").replace("%d 分钟","%d min").replace("%02d 时","%02d h").replace("%02d 分","%02d m"):format;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setBackground(shape(surfaceHigh,15,0,0));ImageView minus=iconButton(R.drawable.ic_remove,"减少");ImageView plus=iconButton(R.drawable.ic_add,"增加");TextView number=label(String.format(Locale.ROOT,localized,value[0]),15,text,true);number.setGravity(Gravity.CENTER);row.addView(minus,lp(48,54));row.addView(number,new LinearLayout.LayoutParams(0,dp(54),1));row.addView(plus,lp(48,54));Runnable render=()->{number.setText(String.format(Locale.ROOT,localized,value[0]));minus.setEnabled(value[0]>min);minus.setAlpha(value[0]>min?1f:.35f);plus.setEnabled(value[0]<max);plus.setAlpha(value[0]<max?1f:.35f);if(changed!=null)changed.run();};minus.setOnClickListener(v->{value[0]=Math.max(min,value[0]-step);render.run();animateValue(number);});plus.setOnClickListener(v->{value[0]=Math.min(max,value[0]+step);render.run();animateValue(number);});render.run();return row;}
    private TextView timeChoice(int minutes,String description){TextView field=label(formatClock(minutes),18,text,true);field.setGravity(Gravity.CENTER);field.setContentDescription(L(description));field.setBackground(ripple(surfaceHigh,15));field.setClickable(true);field.setFocusable(true);return field;}
    private void showTimePicker(String title,int initialMinutes,MinutesPicked picked){Dialog dialog=bottomDialog();LinearLayout sheet=sheet(title,"24 小时制，使用加减按钮调整，不需要键盘输入");int[] hour={initialMinutes/60};int[] minute={initialMinutes%60};TextView preview=label(formatClock(initialMinutes),38,onPrimaryContainer,true);preview.setGravity(Gravity.CENTER);preview.setBackground(shape(primaryContainer,24,0,0));sheet.addView(preview,margin(-1,92,0,0,0,16));Runnable refresh=()->{preview.setText(formatClock(hour[0]*60+minute[0]));animateValue(preview);};LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);controls.addView(fieldGroup("小时",numberStepper(hour,0,23,1,"%02d 时",refresh)),new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams minuteParams=new LinearLayout.LayoutParams(0,-2,1);minuteParams.setMargins(dp(10),0,0,0);controls.addView(fieldGroup("分钟",numberStepper(minute,0,59,1,"%02d 分",refresh)),minuteParams);sheet.addView(controls,margin(-1,-2,0,0,0,18));LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView cancel=button("取消",false);TextView done=button("选用这个时间",true);actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams doneParams=new LinearLayout.LayoutParams(0,dp(52),2);doneParams.setMargins(dp(10),0,0,0);actions.addView(done,doneParams);sheet.addView(actions);cancel.setOnClickListener(v->dialog.dismiss());done.setOnClickListener(v->{picked.accept(hour[0]*60+minute[0]);dialog.dismiss();});showBottomDialog(dialog,sheet);}
    private void animateValue(View view){if(!motionEnabled())return;view.setScaleX(.94f);view.setScaleY(.94f);view.animate().scaleX(1).scaleY(1).setDuration(180).setInterpolator(emphasized).start();}
    private String generatedTimePreview(int firstPeriod,int count,int startMinutes,int lessonMinutes,int breakMinutes){int safeCount=Math.max(0,Math.min(count,13-firstPeriod));StringBuilder result=new StringBuilder();for(int i=0;i<safeCount;i++){if(i>0)result.append('\n');int start=startMinutes+i*(lessonMinutes+breakMinutes);result.append(english?"Period ":"第").append(firstPeriod+i).append(english?"  ":"节  ").append(formatTimeRange(start,start+lessonMinutes));if(i==2&&safeCount>4){result.append("\n…\n");i=safeCount-2;}}return result.toString();}
    private void showBatchError(TextView error,String message){String localized=L(message);error.setText(localized);error.setVisibility(View.VISIBLE);error.announceForAccessibility(localized);}
    private String timeSlotKey(int day,int period){return KEY_TIME_SLOT_PREFIX+day+"_"+period;}
    private String timeSlot(int day,int period,String fallback){return prefs.getString(timeSlotKey(day,period),fallback);}
    private int[] parseTimeRange(String raw){try{String[] parts=raw.trim().split("[^0-9]+");if(parts.length>=4){int start=Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);int end=Integer.parseInt(parts[2])*60+Integer.parseInt(parts[3]);if(start>=0&&start<1440&&end>start&&end<1440)return new int[]{start,end};}}catch(Exception ignored){}return new int[]{8*60+50,9*60+30};}
    private String formatClock(int minutes){int safe=Math.max(0,Math.min(1439,minutes));return String.format(Locale.ROOT,"%02d:%02d",safe/60,safe%60);}
    private String formatTimeRange(int start,int end){return formatClock(start)+"–"+formatClock(end);}

    private Dialog bottomDialog(){Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);return dialog;}
    private LinearLayout sheet(String title,String subtitle){LinearLayout sheet=column();sheet.setPadding(dp(24),dp(14),dp(24),dp(28));sheet.setBackground(shape(surface,30,0,0));TextView handle=new TextView(this);handle.setBackground(shape(muted,3,0,0));LinearLayout handleWrap=new LinearLayout(this);handleWrap.setGravity(Gravity.CENTER);handleWrap.addView(handle,lp(36,5));sheet.addView(handleWrap,margin(-1,22,0,0,0,12));sheet.addView(label(title,27,text,true));sheet.addView(label(subtitle,14,muted,false),margin(-1,-2,0,5,0,18));return sheet;}
    private void showBottomDialog(Dialog dialog,View content){showBottomDialog(dialog,content,-2);}
    private void showBottomDialog(Dialog dialog,View content,int height){dialog.setContentView(content);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.getDecorView().setPadding(0,0,0,0);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowManager.LayoutParams p=w.getAttributes();p.width=WindowManager.LayoutParams.MATCH_PARENT;p.height=height;p.gravity=Gravity.BOTTOM;p.x=0;p.y=0;w.setAttributes(p);w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,height);}dialog.show();if(w!=null)w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,height);if(motionEnabled()){content.setAlpha(.72f);content.setTranslationY(dp(72));content.post(()->content.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(emphasized).start());}}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(L(hint));e.setText(value);e.setTextSize(16);e.setTextColor(text);e.setHintTextColor(muted);e.setSingleLine(true);e.setPadding(dp(16),0,dp(16),0);e.setBackground(ripple(surfaceHigh,15));return e;}
    private LinearLayout fieldGroup(String title,View field){LinearLayout group=column();group.addView(label(title,13,muted,true),margin(-1,-2,2,0,0,7));group.addView(field,lp(-1,54));return group;}
    private TextView button(String title,boolean filled){TextView b=label(title,15,filled?onPrimary:text,true);b.setGravity(Gravity.CENTER);b.setClickable(true);b.setFocusable(true);b.setBackground(ripple(filled?primary:surfaceHigh,18));b.setOnTouchListener((view,event)->{if(!motionEnabled()||!view.isEnabled())return false;if(event.getAction()==MotionEvent.ACTION_DOWN){view.animate().cancel();animateButtonRadius(view,18,28,130);view.animate().scaleX(.955f).scaleY(.91f).setDuration(130).setInterpolator(emphasized).start();}else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){view.animate().cancel();animateButtonRadius(view,28,18,260);view.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(emphasized).start();}return false;});return b;}
    private void animateButtonRadius(View view,float from,float to,long duration){if(!(view.getBackground() instanceof android.graphics.drawable.RippleDrawable ripple))return;android.graphics.drawable.Drawable content=ripple.getDrawable(0);if(!(content instanceof GradientDrawable fill))return;ValueAnimator radius=ValueAnimator.ofFloat(dp(from),dp(to));radius.setDuration(duration);radius.setInterpolator(emphasized);radius.addUpdateListener(value->fill.setCornerRadius((float)value.getAnimatedValue()));radius.start();}
    private LinearLayout iconTextButton(int iconRes,String title,int color){LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.HORIZONTAL);button.setGravity(Gravity.CENTER);button.setClickable(true);button.setFocusable(true);button.setBackground(ripple(surfaceHigh,18));button.setContentDescription(L(title));ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setColorFilter(color);button.addView(icon,lp(24,24));TextView textView=label(title,15,color,true);textView.setGravity(Gravity.CENTER_VERTICAL);button.addView(textView,margin(-2,-2,8,0,0,0));return button;}
    private ImageView iconButton(int icon,String desc){ImageView v=new ImageView(this);v.setImageResource(icon);v.setColorFilter(muted);v.setPadding(dp(8),dp(12),dp(8),dp(12));v.setContentDescription(L(desc));v.setBackground(ripple(Color.TRANSPARENT,20));v.setClickable(true);v.setFocusable(true);return v;}
    private View emptyState(String title,String copy){LinearLayout box=column();box.setGravity(Gravity.CENTER);box.setPadding(dp(26),dp(42),dp(26),dp(42));box.setBackground(shape(surfaceHigh,28,0,0));box.addView(label(title,23,text,true));TextView c=label(copy,15,muted,false);c.setGravity(Gravity.CENTER);box.addView(c,margin(-1,-2,0,8,0,0));return box;}

    private LinkedHashMap<Subject,List<Entry>> groupBySubject(List<Entry> source){LinkedHashMap<Subject,List<Entry>> grouped=new LinkedHashMap<>();for(Entry entry:source){Subject subject=subjectById(entry.subjectId);if(subject!=null)grouped.computeIfAbsent(subject,x->new ArrayList<>()).add(entry);}return grouped;}
    private int totalItems(LinkedHashMap<Subject,List<Entry>> grouped){int total=0;for(Subject subject:grouped.keySet())if(!subject.noItems)total+=subject.items.size();return total;}
    private int countMissingSubjects(LinkedHashMap<Subject,List<Entry>> grouped){int total=0;for(Subject subject:grouped.keySet())if(!subject.noItems&&subject.items.isEmpty())total++;return total;}
    private int countDoneItems(LinkedHashMap<Subject,List<Entry>> grouped){int done=0;for(Subject subject:grouped.keySet())if(!subject.noItems)for(String item:subject.items)if(isPacked(subject.id,item))done++;return done;}
    private List<DailyItem> dailyItemsFor(LocalDate date){List<DailyItem> out=new ArrayList<>();for(DailyItem item:dailyItems)if(item.permanent||date.equals(item.date))out.add(item);return out;}
    private int countDoneDailyItems(List<DailyItem> items,LocalDate date){int done=0;for(DailyItem item:items)if(isDailyChecked(date,item.id))done++;return done;}
    private int countDoneForChecklist(){LinkedHashMap<Subject,List<Entry>> grouped=groupBySubject(entriesFor(checklistDate.getDayOfWeek().getValue()-1));return countDoneItems(grouped)+countDoneDailyItems(dailyItemsFor(checklistDate),checklistDate);}
    private List<BagItem> packedItemsNotNeededFor(LinkedHashMap<Subject,List<Entry>> grouped){Set<String> needed=new HashSet<>();for(Subject subject:grouped.keySet())if(!subject.noItems)for(String item:subject.items)needed.add(packedKey(subject.id,item));List<BagItem> out=new ArrayList<>();for(Subject subject:subjects)if(!subject.noItems)for(String item:subject.items)if(isPacked(subject.id,item)&&!needed.contains(packedKey(subject.id,item)))out.add(new BagItem(subject,item));return out;}
    private String timesFor(List<Entry> source){StringBuilder b=new StringBuilder();for(Entry e:source){if(b.length()>0)b.append(" · ");b.append(english?"Period ":"第").append(e.period).append(english?" ":"节 ").append(e.time);}return b.toString();}
    private String subjectSummary(Subject subject){if(subject.noItems)return"无需携带物品";if(subject.items.isEmpty())return"尚未设置携带物";return joinItems(subject.items);}
    private String joinItems(List<String> items){return String.join(english?", ":"、",items);}
    private List<String> parseItems(String raw){LinkedHashSet<String> unique=new LinkedHashSet<>();for(String value:raw.split("[、,，;；\\n]+")){String item=value.trim();if(!item.isEmpty())unique.add(item);}return new ArrayList<>(unique);}
    private List<Entry> entriesFor(int day){List<Entry> out=new ArrayList<>();for(Entry entry:entries)if(entry.day==day)out.add(entry);out.sort(Comparator.comparingInt(e->e.period));return out;}
    private List<Entry> entriesForSubject(String id){List<Entry> out=new ArrayList<>();for(Entry e:entries)if(e.subjectId.equals(id))out.add(e);return out;}
    private Subject subjectById(String id){for(Subject s:subjects)if(s.id.equals(id))return s;return null;}
    private String checkKey(LocalDate date,String subjectId,String item){return date+":"+subjectId+":"+b64(item.trim());}
    private String packedKey(String subjectId,String item){return "packed:"+subjectId+":"+b64(item.trim());}
    private boolean isPacked(String subjectId,String item){String key=packedKey(subjectId,item);if(checks.contains(key))return true;String suffix=":"+subjectId+":"+b64(item.trim());for(String saved:checks)if(saved.endsWith(suffix))return true;return false;}
    private void setPacked(String subjectId,String item,boolean packed){String key=packedKey(subjectId,item);String suffix=":"+subjectId+":"+b64(item.trim());checks.removeIf(saved->saved.equals(key)||saved.endsWith(suffix));if(packed)checks.add(key);}
    private String dailyCheckKey(LocalDate date,String id){return"daily:"+date+":"+id;}
    private boolean isDailyChecked(LocalDate date,String id){return dailyChecks.contains(dailyCheckKey(date,id));}
    private void setDailyChecked(LocalDate date,String id,boolean checked){String key=dailyCheckKey(date,id);if(checked)dailyChecks.add(key);else dailyChecks.remove(key);}
    private boolean hasDailyChecksFor(LocalDate date){if(date==null)return false;String prefix="daily:"+date+":";for(String saved:dailyChecks)if(saved.startsWith(prefix))return true;return false;}
    private void updateClearChecksButton(){if(clearChecksButton==null)return;boolean enabled=!checks.isEmpty()||hasDailyChecksFor(checklistDate);clearChecksButton.setEnabled(enabled);clearChecksButton.setAlpha(enabled?1f:.45f);clearChecksButton.setContentDescription(L(enabled?"清空已选择的物品":"当前没有已选择的物品"));}
    private boolean motionEnabled(){try{return Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;}catch(Exception ignored){return true;}}
    private Vibrator deviceVibrator(){if(Build.VERSION.SDK_INT>=31){VibratorManager manager=(VibratorManager)getSystemService(VIBRATOR_MANAGER_SERVICE);return manager==null?null:manager.getDefaultVibrator();}return(Vibrator)getSystemService(VIBRATOR_SERVICE);}
    private void vibrateTap(boolean strong){if(!prefs.getBoolean(AppText.KEY_HAPTICS,true))return;Vibrator vibrator=deviceVibrator();if(vibrator==null||!vibrator.hasVibrator())return;if(Build.VERSION.SDK_INT>=29)vibrator.vibrate(VibrationEffect.createPredefined(strong?VibrationEffect.EFFECT_CLICK:VibrationEffect.EFFECT_TICK));else vibrator.vibrate(VibrationEffect.createOneShot(strong?35:22,VibrationEffect.DEFAULT_AMPLITUDE));}

    private void loadData(){String subjectRaw=prefs.getString(KEY_SUBJECTS,"");String entryRaw=prefs.getString(KEY_ENTRIES,"");if(!subjectRaw.isEmpty()){for(String line:subjectRaw.split("\\n")){Subject subject=Subject.decode(line);if(subject!=null)subjects.add(subject);}for(String line:entryRaw.split("\\n")){Entry entry=Entry.decode(line);if(entry!=null)entries.add(entry);}}else migrateLegacyData();String dailyRaw=prefs.getString(KEY_DAILY_ITEMS,"");if(!dailyRaw.isEmpty())for(String line:dailyRaw.split("\\n")){DailyItem item=DailyItem.decode(line);if(item!=null)dailyItems.add(item);}checks.addAll(prefs.getStringSet(KEY_CHECKS,new HashSet<>()));dailyChecks.addAll(prefs.getStringSet(KEY_DAILY_CHECKS,new HashSet<>()));migratePackedChecks();pruneDailyData();}
    private void pruneDailyData(){LocalDate today=LocalDate.now();boolean itemsChanged=dailyItems.removeIf(item->!item.permanent&&item.date!=null&&item.date.isBefore(today));boolean checksChanged=dailyChecks.removeIf(saved->{try{String[] parts=saved.split(":",3);return parts.length<3||LocalDate.parse(parts[1]).isBefore(today.minusDays(1));}catch(Exception e){return true;}});if(itemsChanged)saveDailyItems();if(checksChanged)saveDailyChecks();}
    private void migratePackedChecks(){Set<String> migrated=new HashSet<>();LocalDate oldest=LocalDate.now().minusDays(1);for(String saved:checks){if(saved.startsWith("packed:")){migrated.add(saved);continue;}int split=saved.indexOf(':');if(split<=0)continue;try{LocalDate date=LocalDate.parse(saved.substring(0,split));if(!date.isBefore(oldest))migrated.add("packed:"+saved.substring(split+1));}catch(Exception ignored){}}if(!migrated.equals(checks)){checks.clear();checks.addAll(migrated);prefs.edit().putStringSet(KEY_CHECKS,new HashSet<>(checks)).apply();}}
    private void migrateLegacyData(){String legacy=prefs.getString(KEY_LEGACY_COURSES,"");if(legacy.isEmpty())return;LinkedHashMap<String,Subject> byName=new LinkedHashMap<>();for(String line:legacy.split("\\n")){LegacyCourse old=LegacyCourse.decode(line);if(old==null)continue;Subject subject=byName.get(old.subject);if(subject==null){subject=new Subject(UUID.randomUUID().toString(),old.subject,parseItems(old.items),false);byName.put(old.subject,subject);subjects.add(subject);}else if(!old.items.trim().isEmpty()){LinkedHashSet<String> merged=new LinkedHashSet<>(subject.items);merged.addAll(parseItems(old.items));subject.items=new ArrayList<>(merged);}entries.add(new Entry(old.id,subject.id,old.day,old.period,old.time));}saveAll();}
    private void saveAll(){StringBuilder subjectData=new StringBuilder();for(Subject s:subjects){if(subjectData.length()>0)subjectData.append('\n');subjectData.append(s.encode());}StringBuilder entryData=new StringBuilder();for(Entry e:entries){if(entryData.length()>0)entryData.append('\n');entryData.append(e.encode());}prefs.edit().putString(KEY_SUBJECTS,subjectData.toString()).putString(KEY_ENTRIES,entryData.toString()).apply();TomorrowWidgetProvider.refreshAll(this);}
    private void saveChecks(){prefs.edit().putStringSet(KEY_CHECKS,new HashSet<>(checks)).apply();TomorrowWidgetProvider.refreshAll(this);}
    private void saveDailyItems(){StringBuilder data=new StringBuilder();for(DailyItem item:dailyItems){if(data.length()>0)data.append('\n');data.append(item.encode());}prefs.edit().putString(KEY_DAILY_ITEMS,data.toString()).apply();TomorrowWidgetProvider.refreshAll(this);}
    private void saveDailyChecks(){prefs.edit().putStringSet(KEY_DAILY_CHECKS,new HashSet<>(dailyChecks)).apply();TomorrowWidgetProvider.refreshAll(this);}
    private String chineseWeek(DayOfWeek day){return L(new String[]{"星期一","星期二","星期三","星期四","星期五","星期六","星期日"}[day.getValue()-1]);}
    private String formatDate(LocalDate date){return date.format(DateTimeFormatter.ofPattern(english?"MMM d":"M月d日",english?Locale.ENGLISH:Locale.SIMPLIFIED_CHINESE));}
    private String L(String value){return AppText.translate(this,value);}
    private String T(String chinese,String englishText){return AppText.t(this,chinese,englishText);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(L(value));v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    private GradientDrawable shape(int color,int radius,int stroke,int strokeColor){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke>0)d.setStroke(dp(stroke),strokeColor);return d;}
    private android.graphics.drawable.RippleDrawable ripple(int color,int radius){return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((primary&0x00FFFFFF)|0x28000000),shape(color,radius,0,0),null);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private LinearLayout.LayoutParams margin(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=lp(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private static String b64(String s){return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP|Base64.URL_SAFE);}
    private static String un64(String s){return new String(Base64.decode(s,Base64.NO_WRAP|Base64.URL_SAFE),StandardCharsets.UTF_8);}

    interface MinutesPicked{void accept(int minutes);}
    interface StringPicked{void accept(String value);}
    static class BagItem{Subject subject;String item;BagItem(Subject subject,String item){this.subject=subject;this.item=item;}}
    static class DailyItem{String id,name;boolean permanent;LocalDate date;DailyItem(String id,String name,boolean permanent,LocalDate date){this.id=id;this.name=name;this.permanent=permanent;this.date=date;}String encode(){return b64(id)+"|"+b64(name)+"|"+(permanent?"1":"0")+"|"+(date==null?"":date);}static DailyItem decode(String line){try{String[] p=line.split("\\|",-1);boolean permanent="1".equals(p[2]);LocalDate date=p[3].isEmpty()?null:LocalDate.parse(p[3]);return new DailyItem(un64(p[0]),un64(p[1]),permanent,date);}catch(Exception e){return null;}}}
    static class Subject{String id,name;List<String> items;boolean noItems;Subject(String id,String name,List<String> items,boolean noItems){this.id=id;this.name=name;this.items=items;this.noItems=noItems;}String encode(){return b64(id)+"|"+b64(name)+"|"+(noItems?"1":"0")+"|"+b64(String.join("\u001F",items));}static Subject decode(String line){try{String[] p=line.split("\\|",-1);List<String> items=new ArrayList<>();String raw=un64(p[3]);if(!raw.isEmpty())for(String x:raw.split("\u001F",-1))items.add(x);return new Subject(un64(p[0]),un64(p[1]),items,"1".equals(p[2]));}catch(Exception e){return null;}}}
    static class Entry{String id,subjectId,time;int day,period;Entry(String id,String subjectId,int day,int period,String time){this.id=id;this.subjectId=subjectId;this.day=day;this.period=period;this.time=time;}String encode(){return b64(id)+"|"+b64(subjectId)+"|"+day+"|"+period+"|"+b64(time);}static Entry decode(String line){try{String[] p=line.split("\\|",-1);return new Entry(un64(p[0]),un64(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),un64(p[4]));}catch(Exception e){return null;}}}
    static class LegacyCourse{String id,subject,time,items;int day,period;LegacyCourse(String id,int day,int period,String subject,String time,String items){this.id=id;this.day=day;this.period=period;this.subject=subject;this.time=time;this.items=items;}static LegacyCourse decode(String line){try{String[] p=line.split("\\|",-1);return new LegacyCourse(un64(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),un64(p[3]),un64(p[4]),un64(p[5]));}catch(Exception e){return null;}}}
}
