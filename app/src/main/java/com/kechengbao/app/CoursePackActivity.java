package com.kechengbao.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CoursePackActivity extends Activity {
    private static final String PREFS = "course_pack_data_v1";
    private static final String KEY_LEGACY_COURSES = "courses";
    private static final String KEY_SUBJECTS = "subjects_v2";
    private static final String KEY_ENTRIES = "entries_v2";
    private static final String KEY_CHECKS = "item_checks_v2";
    private static final String[] DAYS = {"星期一", "星期二", "星期三", "星期四", "星期五"};

    private final List<Subject> subjects = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> checks = new HashSet<>();
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final FrameLayout[] navIndicators = new FrameLayout[3];
    private final ImageView[] navIcons = new ImageView[3];
    private final TextView[] navLabels = new TextView[3];
    private SharedPreferences prefs;
    private FrameLayout root, contentHost;
    private View completionOverlay;
    private TextView fab, tomorrowStatus, tomorrowProgressLabel, clearChecksButton;
    private LinearLayout tomorrowProgressRow;
    private int selectedDay, selectedTab = 1, tomorrowTotalItems, tomorrowMissingSubjects;
    private LocalDate checklistDate;
    private int pageTransitionId;
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
        showTomorrow(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> { if (selectedTab != 1) showTomorrow(true); else finishAfterTransition(); });
        }
    }

    @Override public void onBackPressed() {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            if (selectedTab != 1) showTomorrow(true); else super.onBackPressed();
        }
    }

    @Override protected void onResume(){
        super.onResume();
        TomorrowWidgetProvider.refreshAll(this);
    }

    private void resolveColors() {
        primary = systemColor(dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xFFD0BCFF : 0xFF6750A4);
        onPrimary = dark ? 0xFF27184A : Color.WHITE;
        primaryContainer = systemColor(dark ? "system_accent1_700" : "system_accent1_100", dark ? 0xFF4F378B : 0xFFEADDFF);
        onPrimaryContainer = systemColor(dark ? "system_accent1_100" : "system_accent1_900", dark ? 0xFFEADDFF : 0xFF21005D);
        secondary = systemColor(dark ? "system_accent2_700" : "system_accent2_100", dark ? 0xFF4A4458 : 0xFFE8DEF8);
        onSecondary = dark ? 0xFFE8DEF8 : 0xFF21182C;
        tertiaryContainer = systemColor(dark ? "system_accent3_700" : "system_accent3_100", dark ? 0xFF633B48 : 0xFFFFD8E4);
        onTertiaryContainer = systemColor(dark ? "system_accent3_100" : "system_accent3_900", dark ? 0xFFFFD8E4 : 0xFF31111D);
        bg = dark ? 0xFF151218 : 0xFFFFF8FF; surface = dark ? 0xFF211F26 : 0xFFFFF8FF;
        surfaceHigh = dark ? 0xFF2B2930 : 0xFFF4EFF7; text = dark ? 0xFFEAE0EC : 0xFF1D1B20;
        muted = dark ? 0xFFCAC4D0 : 0xFF625B71; outline = dark ? 0xFF49454F : 0xFFE3DDE7;
        success = dark ? 0xFF9BD4A8 : 0xFF2F6B3D;
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
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(0, bars.top, 0, bars.bottom); return insets;
        });
        LinearLayout shell = column(); contentHost = new FrameLayout(this);
        shell.addView(contentHost, new LinearLayout.LayoutParams(-1, 0, 1)); shell.addView(buildBottomBar(), lp(-1, 80));
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        fab = label("", 14, onPrimary, true); fab.setGravity(Gravity.CENTER); fab.setPadding(dp(18), 0, dp(20), 0);
        fab.setBackground(ripple(primary, 18)); fab.setElevation(dp(6));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(154), dp(56), Gravity.END | Gravity.BOTTOM);
        fp.setMargins(0, 0, dp(20), dp(94)); root.addView(fab, fp); setContentView(root);
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
        LinearLayout item = column(); item.setGravity(Gravity.CENTER); item.setContentDescription(desc); item.setClickable(true);
        item.setFocusable(true); item.setBackground(ripple(Color.TRANSPARENT, 18));
        FrameLayout indicator = new FrameLayout(this); ImageView image = new ImageView(this); image.setImageResource(icon); image.setColorFilter(muted);
        indicator.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER)); item.addView(indicator, lp(58, 32));
        TextView label = label(title, 12, muted, true); label.setGravity(Gravity.CENTER); item.addView(label, margin(-1, 20, 0, 2, 0, 0));
        navIndicators[index] = indicator; navIcons[index] = image; navLabels[index] = label; return item;
    }

    private void showTomorrow(boolean animate) { if (selectedTab == 1 && animate) return; int old=selectedTab; selectedTab=1; swapPage(buildTomorrowPage(), old>1, animate); updateChrome(); }
    private void showSchedule(boolean animate) { if (selectedTab == 0 && animate) return; int old=selectedTab; selectedTab=0; swapPage(buildSchedulePage(), old>0, animate); updateChrome(); }
    private void showSubjects(boolean animate) { if (selectedTab == 2 && animate) return; int old=selectedTab; selectedTab=2; swapPage(buildSubjectsPage(), old>2, animate); updateChrome(); }

    private void updateChrome() {
        for (int i=0;i<3;i++) { boolean active=selectedTab==i; navIndicators[i].setBackground(shape(active?secondary:Color.TRANSPARENT,18,0,0)); navIcons[i].setColorFilter(active?onSecondary:muted); navLabels[i].setTextColor(active?onSecondary:muted); }
        if (selectedTab==1) fab.setVisibility(View.GONE); else {
            fab.setVisibility(View.VISIBLE); String title=selectedTab==0?"排一节课":"新建科目"; fab.setText(title); fab.setContentDescription(title);
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

    private View buildTomorrowPage() {
        LocalDate today=LocalDate.now(); checklistDate=resolveChecklistDate();
        List<Entry> dayEntries=entriesFor(checklistDate.getDayOfWeek().getValue()-1); LinkedHashMap<Subject,List<Entry>> grouped=groupBySubject(dayEntries);
        tomorrowTotalItems=totalItems(grouped); tomorrowMissingSubjects=countMissingSubjects(grouped); int done=countDoneItems(grouped);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); LinearLayout page=column(); page.setPadding(dp(20),dp(22),dp(20),dp(112));
        String focus=checklistDate.equals(today)?"今天":checklistDate.equals(today.plusDays(1))?"明天":"下一上课日";
        TextView title=label(focus+" · "+chineseWeek(checklistDate.getDayOfWeek()),32,text,true); title.setLetterSpacing(-.02f); page.addView(title);
        page.addView(label(checklistDate.format(DateTimeFormatter.ofPattern("M月d日"))+" · "+dayEntries.size()+" 节课",15,muted,false),margin(-1,-2,0,4,0,24));
        LinearLayout hero=column();hero.setPadding(dp(20),dp(14),dp(20),dp(18));hero.setBackground(shape(primaryContainer,30,0,0));
        LinearLayout heroHeading=new LinearLayout(this);heroHeading.setOrientation(LinearLayout.HORIZONTAL);heroHeading.setGravity(Gravity.CENTER_VERTICAL);heroHeading.addView(label("书包进度",13,onPrimaryContainer,true),new LinearLayout.LayoutParams(0,dp(40),1));clearChecksButton=label("清空已选",13,onPrimaryContainer,true);clearChecksButton.setGravity(Gravity.CENTER);clearChecksButton.setPadding(dp(13),0,dp(13),0);clearChecksButton.setBackground(ripple(secondary,16));clearChecksButton.setOnClickListener(v->showClearChecksDialog());updateClearChecksButton();heroHeading.addView(clearChecksButton,lp(-2,36));hero.addView(heroHeading);
        tomorrowStatus=label(progressStatus(tomorrowTotalItems,done,tomorrowMissingSubjects),26,onPrimaryContainer,true);hero.addView(tomorrowStatus,margin(-1,-2,0,6,0,0));
        tomorrowProgressRow=new LinearLayout(this);tomorrowProgressRow.setOrientation(LinearLayout.HORIZONTAL);if(tomorrowTotalItems>0){buildProgressSegments(tomorrowProgressRow,tomorrowTotalItems,done);hero.addView(tomorrowProgressRow,margin(-1,10,0,16,0,0));}
        tomorrowProgressLabel=label(progressLabel(tomorrowTotalItems,done,tomorrowMissingSubjects),13,onPrimaryContainer,false);hero.addView(tomorrowProgressLabel,margin(-1,-2,0,10,0,0));page.addView(hero,margin(-1,-2,0,0,0,22));
        if(dayEntries.isEmpty())page.addView(emptyState("还没有课程","先到课表页排课。"));else{page.addView(label("按物品准备",20,text,true));page.addView(label("已装物品会自动沿用，拿出书包后再清空",13,muted,false),margin(-1,-2,0,4,0,12));for(Map.Entry<Subject,List<Entry>> group:grouped.entrySet())page.addView(subjectChecklist(group.getKey(),group.getValue()),margin(-1,-2,0,0,0,12));}
        scroll.addView(page);return scroll;
    }

    private void showClearChecksDialog(){
        if(checks.isEmpty())return;
        Dialog dialog=bottomDialog();LinearLayout sheet=sheet("清空所有已选物品？","书包里的已装状态会全部恢复为未装，科目、携带物和课程表不会被删除。");LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);TextView cancel=button("取消",false);TextView confirm=button("确认清空",true);confirm.setTextColor(Color.WHITE);confirm.setBackground(ripple(dark?0xFF8C1D18:0xFFBA1A1A,18));actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams confirmParams=new LinearLayout.LayoutParams(0,dp(52),1);confirmParams.setMargins(dp(12),0,0,0);actions.addView(confirm,confirmParams);sheet.addView(actions);cancel.setOnClickListener(v->dialog.dismiss());confirm.setOnClickListener(v->{checks.clear();saveChecks();dialog.dismiss();showTomorrow(false);});showBottomDialog(dialog,sheet);
    }

    private LocalDate resolveChecklistDate(){
        LocalDate candidate=LocalTime.now().isBefore(LocalTime.of(18,0))?LocalDate.now():LocalDate.now().plusDays(1);
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
        FrameLayout target=new FrameLayout(this);target.setContentDescription((checked?"取消勾选 ":"勾选 ")+item);target.setClickable(true);target.setFocusable(true);target.setBackground(ripple(Color.TRANSPARENT,24));ImageView state=new ImageView(this);setItemCheckVisual(state,checked);target.addView(state,new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));row.addView(target,lp(48,48));
        TextView itemLabel=label(item,15,checked?muted:text,false);itemLabel.setPaintFlags(checked?itemLabel.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG:itemLabel.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);row.addView(itemLabel,new LinearLayout.LayoutParams(0,-1,1));
        target.setOnClickListener(v->toggleItemCheck(subject,item,row,target,state,itemLabel));row.setOnClickListener(v->target.performClick());row.setClickable(true);return row;
    }

    private void toggleItemCheck(Subject subject,String item,View row,View target,ImageView state,TextView itemLabel){
        LinkedHashMap<Subject,List<Entry>> grouped=groupBySubject(entriesFor(checklistDate.getDayOfWeek().getValue()-1));int beforeDone=countDoneItems(grouped);
        boolean checked=!isPacked(subject.id,item);setPacked(subject.id,item,checked);saveChecks();updateClearChecksButton();target.setContentDescription((checked?"取消勾选 ":"勾选 ")+item);setItemCheckVisual(state,checked);itemLabel.setTextColor(checked?muted:text);itemLabel.setPaintFlags(checked?itemLabel.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG:itemLabel.getPaintFlags()&~Paint.STRIKE_THRU_TEXT_FLAG);
        if(checked)target.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        if(motionEnabled()){state.setScaleX(.62f);state.setScaleY(.62f);state.setAlpha(.35f);state.animate().scaleX(1.16f).scaleY(1.16f).alpha(1).setDuration(140).setInterpolator(emphasized).withEndAction(()->state.animate().scaleX(1).scaleY(1).setDuration(170).setInterpolator(emphasized).start()).start();row.animate().scaleX(.985f).scaleY(.985f).setDuration(80).withEndAction(()->row.animate().scaleX(1).scaleY(1).setDuration(190).setInterpolator(emphasized).start()).start();}refreshTomorrowProgress(true);
        int afterDone=countDoneItems(grouped);if(checked&&tomorrowTotalItems>0&&tomorrowMissingSubjects==0&&beforeDone<tomorrowTotalItems&&afterDone==tomorrowTotalItems)root.postDelayed(this::showCompletionCelebration,260);
    }

    private void showCompletionCelebration(){
        if(completionOverlay!=null)return;
        Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,45,55,65,55,110},new int[]{0,90,0,150,0,220},-1));
        FrameLayout overlay=new FrameLayout(this);completionOverlay=overlay;overlay.setClickable(true);overlay.setFocusable(true);overlay.setContentDescription("全部装好了");overlay.setBackgroundColor(dark?0xEE111318:0xEEDFE6FF);root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=column();center.setGravity(Gravity.CENTER);ImageView badge=new ImageView(this);badge.setImageResource(R.drawable.ic_check);badge.setColorFilter(Color.WHITE);badge.setPadding(dp(28),dp(28),dp(28),dp(28));badge.setBackground(shape(success,64,0,0));center.addView(badge,lp(128,128));TextView title=label("书包收好啦",34,dark?Color.WHITE:0xFF17213B,true);title.setGravity(Gravity.CENTER);center.addView(title,margin(-2,-2,0,24,0,0));TextView copy=label("这次要带的物品已经全部装好",16,dark?0xFFDDE2F2:0xFF44506A,false);copy.setGravity(Gravity.CENTER);center.addView(copy,margin(-2,-2,0,8,0,0));overlay.addView(center,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        int[] colors={primary,success,secondary,0xFFFFB95C,0xFFFF7A8A};for(int i=0;i<30;i++){View dot=new View(this);int size=dp(6+(i%4)*2);dot.setBackground(shape(colors[i%colors.length],size,0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(size,size,Gravity.CENTER);overlay.addView(dot,p);double angle=Math.PI*2*i/30d;float distance=dp(150+(i%6)*34);dot.setAlpha(0f);dot.setScaleX(.3f);dot.setScaleY(.3f);if(motionEnabled())dot.animate().alpha(i%3==0?.35f:.9f).scaleX(1).scaleY(1).translationX((float)Math.cos(angle)*distance).translationY((float)Math.sin(angle)*distance).rotation(160+(i%5)*55).setStartDelay((i%8)*22L).setDuration(720).setInterpolator(emphasized).withEndAction(()->dot.animate().alpha(0).translationYBy(dp(40)).setDuration(420).start()).start();}
        if(motionEnabled()){overlay.setAlpha(0);overlay.animate().alpha(1).setDuration(180).start();center.setScaleX(.55f);center.setScaleY(.55f);center.setAlpha(0);center.animate().scaleX(1.05f).scaleY(1.05f).alpha(1).setDuration(480).setInterpolator(emphasized).withEndAction(()->center.animate().scaleX(1).scaleY(1).setDuration(180).start()).start();}
        Runnable dismiss=()->{if(completionOverlay!=overlay)return;if(motionEnabled())overlay.animate().alpha(0).setDuration(240).withEndAction(()->{root.removeView(overlay);completionOverlay=null;}).start();else{root.removeView(overlay);completionOverlay=null;}};overlay.setOnClickListener(v->dismiss.run());overlay.postDelayed(dismiss,motionEnabled()?2100:1000);
    }

    private void setItemCheckVisual(ImageView state,boolean checked){state.setPadding(dp(5),dp(5),dp(5),dp(5));if(checked){state.setImageResource(R.drawable.ic_check);state.setColorFilter(dark?0xFF14371D:Color.WHITE);state.setBackground(shape(success,14,0,0));}else{state.setImageDrawable(null);state.setBackground(shape(surfaceHigh,14,2,primary));}}
    private void refreshTomorrowProgress(boolean animate){LinkedHashMap<Subject,List<Entry>> grouped=groupBySubject(entriesFor(checklistDate.getDayOfWeek().getValue()-1));int done=countDoneItems(grouped);tomorrowStatus.setText(progressStatus(tomorrowTotalItems,done,tomorrowMissingSubjects));tomorrowProgressLabel.setText(progressLabel(tomorrowTotalItems,done,tomorrowMissingSubjects));if(tomorrowProgressRow!=null)for(int i=0;i<tomorrowProgressRow.getChildCount();i++){View segment=tomorrowProgressRow.getChildAt(i);segment.setBackground(shape(i<done?primary:secondary,4,0,0));if(animate&&motionEnabled()){segment.setScaleY(.55f);segment.animate().scaleY(1).setDuration(220).setInterpolator(emphasized).start();}}}
    private String progressStatus(int total,int done,int missing){if(total==0&&missing>0)return"先设置携带物";if(total==0)return"无需准备物品";if(done==total&&missing==0)return"全部装好了";return"还差 "+(total-done)+" 件";}
    private String progressLabel(int total,int done,int missing){if(missing>0)return missing+" 个科目尚未设置 · "+done+" / "+total+" 已装好";return total==0?"这些科目都确认无需携带物品":done+" / "+total+" 已装好";}
    private void buildProgressSegments(LinearLayout row,int total,int done){for(int i=0;i<total;i++){View segment=new View(this);segment.setBackground(shape(i<done?primary:secondary,4,0,0));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(8),1);if(i>0)p.setMargins(dp(5),0,0,0);row.addView(segment,p);}row.setContentDescription("已装好 "+done+" 件，共 "+total+" 件");}

    private View buildSchedulePage(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=column();page.setPadding(dp(20),dp(22),dp(20),dp(112));TextView title=label("本周课表",32,text,true);title.setLetterSpacing(-.02f);page.addView(title);page.addView(label("课表只安排科目，携带物统一在科目库管理",14,muted,false),margin(-1,-2,0,5,0,18));page.addView(buildDaySelector(),margin(-1,52,0,0,0,22));List<Entry> day=entriesFor(selectedDay);LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(label(DAYS[selectedDay],22,text,true),new LinearLayout.LayoutParams(0,dp(40),1));TextView count=label(day.size()+" 节课",13,onPrimaryContainer,true);count.setGravity(Gravity.CENTER);count.setBackground(shape(primaryContainer,16,0,0));heading.addView(count,lp(70,34));page.addView(heading,margin(-1,40,0,0,0,10));if(day.isEmpty())page.addView(emptyState("这天没有课","点“排一节课”，从科目库选择科目。"));else page.addView(scheduleList(day));scroll.addView(page);return scroll;}
    private View buildDaySelector(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));row.setBackground(shape(surfaceHigh,22,0,0));for(int i=0;i<DAYS.length;i++){final int day=i;TextView chip=label(String.valueOf("一二三四五".charAt(i)),14,i==selectedDay?onPrimaryContainer:muted,true);chip.setGravity(Gravity.CENTER);chip.setContentDescription(DAYS[i]);chip.setBackground(ripple(i==selectedDay?primaryContainer:Color.TRANSPARENT,18));chip.setOnClickListener(v->{if(selectedDay!=day){boolean back=day<selectedDay;selectedDay=day;swapPage(buildSchedulePage(),back,true);}});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(44),1);if(i>0)params.setMargins(dp(2),0,0,0);row.addView(chip,params);}return row;}
    private View scheduleList(List<Entry> list){LinearLayout group=column();group.setBackground(shape(surfaceHigh,24,0,0));group.setClipToOutline(true);for(int i=0;i<list.size();i++){Entry entry=list.get(i);group.addView(scheduleRow(entry,subjectById(entry.subjectId)));if(i<list.size()-1){View divider=new View(this);divider.setBackgroundColor(outline);group.addView(divider,margin(-1,1,72,0,16,0));}}return group;}
    private View scheduleRow(Entry entry,Subject subject){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(8),dp(10));row.setMinimumHeight(dp(84));row.setBackground(ripple(surfaceHigh,0));TextView period=label(String.valueOf(entry.period),15,onPrimaryContainer,true);period.setGravity(Gravity.CENTER);period.setBackground(shape(primaryContainer,14,0,0));row.addView(period,margin(48,48,0,0,10,0));LinearLayout body=column();String name=subject==null?"已删除的科目":subject.name;body.addView(label(name,17,text,true));body.addView(label(entry.time,12,primary,true),margin(-1,-2,0,3,0,0));TextView summary=label(subject==null?"请重新选择科目":subjectSummary(subject),13,subject!=null&&!subject.noItems&&subject.items.isEmpty()?primary:muted,false);summary.setMaxLines(2);body.addView(summary,margin(-1,-2,0,2,0,0));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=iconButton(R.drawable.ic_chevron_right,"编辑这节课");row.addView(arrow,lp(40,48));row.setContentDescription("编辑第 "+entry.period+" 节 "+name);row.setOnClickListener(v->showScheduleEditor(entry));arrow.setOnClickListener(v->showScheduleEditor(entry));row.setClickable(true);return row;}

    private View buildSubjectsPage(){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=column();page.setPadding(dp(20),dp(22),dp(20),dp(112));TextView title=label("科目库",32,text,true);title.setLetterSpacing(-.02f);page.addView(title);page.addView(label("每个科目只设置一次携带物，所有课表自动同步",14,muted,false),margin(-1,-2,0,5,0,22));if(subjects.isEmpty())page.addView(emptyState("还没有科目","先新建科目和需要携带的物品，再去课表排课。"));else{LinearLayout list=column();list.setBackground(shape(surfaceHigh,24,0,0));list.setClipToOutline(true);for(int i=0;i<subjects.size();i++){list.addView(subjectRow(subjects.get(i)));if(i<subjects.size()-1){View divider=new View(this);divider.setBackgroundColor(outline);list.addView(divider,margin(-1,1,72,0,16,0));}}page.addView(list);}scroll.addView(page);return scroll;}
    private View subjectRow(Subject subject){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(8),dp(12));row.setBackground(ripple(surfaceHigh,0));TextView token=label(subject.name.isEmpty()?"科":subject.name.substring(0,1),16,onPrimaryContainer,true);token.setGravity(Gravity.CENTER);token.setBackground(shape(primaryContainer,14,0,0));row.addView(token,margin(48,48,0,0,12,0));LinearLayout body=column();body.addView(label(subject.name,17,text,true));TextView summary=label(subjectSummary(subject),13,!subject.noItems&&subject.items.isEmpty()?primary:muted,false);summary.setMaxLines(3);body.addView(summary,margin(-1,-2,0,4,0,0));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=iconButton(R.drawable.ic_chevron_right,"编辑 "+subject.name);row.addView(arrow,lp(40,48));row.setOnClickListener(v->showSubjectEditor(subject));arrow.setOnClickListener(v->showSubjectEditor(subject));row.setClickable(true);return row;}

    private void showSubjectEditor(Subject existing){boolean editing=existing!=null;Dialog dialog=bottomDialog();LinearLayout sheet=sheet("科目设置",editing?"修改一次，所有课表中的该科目都会同步":"先建立科目，再把它排进课表");EditText name=input("例如：数学",editing?existing.name:"");boolean[] noItems={editing&&existing.noItems};List<EditText> itemInputs=new ArrayList<>();LinearLayout itemFields=column();if(editing&&!existing.items.isEmpty()){for(String item:existing.items)addItemInputRow(itemFields,itemInputs,item);}else addItemInputRow(itemFields,itemInputs,"");LinearLayout addItem=iconTextButton(R.drawable.ic_add,"添加一件物品",primary);addItem.setOnClickListener(v->addItemInputRow(itemFields,itemInputs,""));LinearLayout itemGroup=column();itemGroup.addView(label("默认携带物",13,muted,true),margin(-1,-2,2,0,0,7));itemGroup.addView(itemFields);itemGroup.addView(addItem,margin(-1,48,0,8,0,0));LinearLayout noItemsRow=toggleRow("无需携带物品",noItems);setItemEditorEnabled(itemFields,addItem,!noItems[0]);noItemsRow.setOnClickListener(v->{noItems[0]=!noItems[0];updateToggleRow(noItemsRow,noItems[0]);setItemEditorEnabled(itemFields,addItem,!noItems[0]);});sheet.addView(fieldGroup("科目名称",name),margin(-1,-2,0,0,0,12));sheet.addView(itemGroup,margin(-1,-2,0,0,0,10));sheet.addView(noItemsRow,margin(-1,52,0,0,0,18));LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);if(editing){int useCount=entriesForSubject(existing.id).size();TextView delete=button(useCount==0?"删除":"删除 · "+useCount+" 节",false);delete.setTextColor(dark?0xFFFFB4AB:0xFFBA1A1A);delete.setOnClickListener(v->{subjects.remove(existing);entries.removeIf(e->e.subjectId.equals(existing.id));saveAll();dialog.dismiss();showSubjects(false);});actions.addView(delete,margin(118,52,0,0,10,0));}TextView save=button("保存科目",true);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));sheet.addView(actions);save.setOnClickListener(v->{String subjectName=name.getText().toString().trim();if(subjectName.isEmpty()){name.setError("请填写科目名称");return;}Subject target=editing?existing:new Subject(UUID.randomUUID().toString(),subjectName,new ArrayList<>(),false);target.name=subjectName;target.noItems=noItems[0];target.items=noItems[0]?new ArrayList<>():collectItemInputs(itemInputs);if(!editing)subjects.add(target);saveAll();dialog.dismiss();if(selectedTab==2)showSubjects(false);else if(selectedTab==0)showSchedule(false);else showTomorrow(false);});showBottomDialog(dialog,sheet);}

    private void addItemInputRow(LinearLayout container,List<EditText> inputs,String value){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);EditText field=input("例如：课本",value);row.addView(field,new LinearLayout.LayoutParams(0,dp(54),1));ImageView remove=iconButton(R.drawable.ic_remove,"移除这件物品");row.addView(remove,margin(48,48,8,3,0,3));container.addView(row,margin(-1,-2,0,0,0,8));inputs.add(field);remove.setOnClickListener(v->{if(inputs.size()==1){field.setText("");field.requestFocus();return;}inputs.remove(field);container.removeView(row);});
    }
    private void setItemEditorEnabled(LinearLayout container,View addItem,boolean enabled){container.setEnabled(enabled);container.setAlpha(enabled?1f:.38f);addItem.setEnabled(enabled);addItem.setAlpha(enabled?1f:.38f);for(int i=0;i<container.getChildCount();i++){View row=container.getChildAt(i);row.setEnabled(enabled);if(row instanceof LinearLayout)for(int n=0;n<((LinearLayout)row).getChildCount();n++)((LinearLayout)row).getChildAt(n).setEnabled(enabled);}}
    private List<String> collectItemInputs(List<EditText> inputs){LinkedHashSet<String> unique=new LinkedHashSet<>();for(EditText input:inputs){String value=input.getText().toString().trim();if(!value.isEmpty())unique.add(value);}return new ArrayList<>(unique);}

    private void showScheduleEditor(Entry existing){if(subjects.isEmpty()){showSubjectEditor(null);return;}boolean editing=existing!=null;Dialog dialog=bottomDialog();LinearLayout sheet=sheet(editing?"编辑排课":"排一节课","从科目库选择科目，这里只设置上课时间");Subject initial=editing?subjectById(existing.subjectId):subjects.get(0);if(initial==null)initial=subjects.get(0);String[] subjectId={initial.id};TextView subjectField=label(initial.name,16,text,false);subjectField.setPadding(dp(16),0,dp(16),0);subjectField.setBackground(ripple(surfaceHigh,15));subjectField.setCompoundDrawablesWithIntrinsicBounds(0,0,R.drawable.ic_chevron_right,0);subjectField.setClickable(true);subjectField.setOnClickListener(v->showSubjectPicker(subjectId,subjectField));int[] day={editing?existing.day:selectedDay};View daySelector=buildEditorDaySelector(day);EditText period=input("第几节（1–12）",editing?String.valueOf(existing.period):"1");period.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);EditText time=input("上课时间",editing?existing.time:"08:50–09:30");sheet.addView(fieldGroup("选择科目",subjectField),margin(-1,-2,0,0,0,12));sheet.addView(fieldGroup("星期",daySelector),margin(-1,-2,0,0,0,12));LinearLayout timing=new LinearLayout(this);timing.setOrientation(LinearLayout.HORIZONTAL);timing.addView(fieldGroup("节次",period),new LinearLayout.LayoutParams(0,-2,1));timing.addView(fieldGroup("上课时间",time),margin(0,-2,10,0,0,0));((LinearLayout.LayoutParams)timing.getChildAt(1).getLayoutParams()).weight=2;sheet.addView(timing,margin(-1,-2,0,0,0,18));LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);if(editing){TextView delete=button("移除这节",false);delete.setTextColor(dark?0xFFFFB4AB:0xFFBA1A1A);delete.setOnClickListener(v->{entries.remove(existing);saveAll();dialog.dismiss();showSchedule(false);});actions.addView(delete,margin(118,52,0,0,10,0));}TextView save=button("保存排课",true);actions.addView(save,new LinearLayout.LayoutParams(0,dp(52),1));sheet.addView(actions);save.setOnClickListener(v->{int p;try{p=Math.max(1,Math.min(12,Integer.parseInt(period.getText().toString())));}catch(Exception e){period.setError("请输入 1–12");return;}Entry target=editing?existing:new Entry(UUID.randomUUID().toString(),subjectId[0],day[0],p,time.getText().toString().trim());target.subjectId=subjectId[0];target.day=day[0];target.period=p;target.time=time.getText().toString().trim();if(!editing)entries.add(target);selectedDay=day[0];saveAll();dialog.dismiss();showSchedule(false);});showBottomDialog(dialog,sheet);}

    private void showSubjectPicker(String[] selectedId,TextView field){Dialog picker=bottomDialog();LinearLayout sheet=sheet("选择科目","携带物会自动使用科目库中的设置");ScrollView scroll=new ScrollView(this);LinearLayout list=column();for(Subject subject:subjects){TextView row=label(subject.name,16,text,true);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(ripple(subject.id.equals(selectedId[0])?primaryContainer:surfaceHigh,14));row.setContentDescription("选择 "+subject.name);row.setOnClickListener(v->{selectedId[0]=subject.id;field.setText(subject.name);picker.dismiss();});row.setClickable(true);list.addView(row,margin(-1,-2,0,0,0,8));}scroll.addView(list);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showBottomDialog(picker,sheet,(int)(getResources().getDisplayMetrics().heightPixels*.72f));}
    private LinearLayout toggleRow(String title,boolean[] state){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setBackground(ripple(surfaceHigh,16));ImageView icon=new ImageView(this);row.addView(icon,lp(28,28));TextView titleView=label(title,15,text,true);LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-1,1);titleParams.setMargins(dp(12),0,0,0);row.addView(titleView,titleParams);updateToggleRow(row,state[0]);row.setClickable(true);return row;}
    private void updateToggleRow(LinearLayout row,boolean checked){ImageView icon=(ImageView)row.getChildAt(0);setItemCheckVisual(icon,checked);row.setContentDescription((checked?"已选择 ":"未选择 ")+((TextView)row.getChildAt(1)).getText());}
    private View buildEditorDaySelector(int[] selected){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));row.setBackground(shape(surfaceHigh,18,0,0));TextView[] choices=new TextView[DAYS.length];for(int i=0;i<DAYS.length;i++){final int day=i;TextView choice=label(String.valueOf("一二三四五".charAt(i)),14,i==selected[0]?onPrimaryContainer:muted,true);choice.setGravity(Gravity.CENTER);choice.setContentDescription(DAYS[i]);choice.setBackground(ripple(i==selected[0]?primaryContainer:Color.TRANSPARENT,15));choice.setOnClickListener(v->{selected[0]=day;for(int n=0;n<choices.length;n++){choices[n].setTextColor(n==day?onPrimaryContainer:muted);choices[n].setBackground(ripple(n==day?primaryContainer:Color.TRANSPARENT,15));}});choices[i]=choice;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)params.setMargins(dp(2),0,0,0);row.addView(choice,params);}return row;}

    private Dialog bottomDialog(){Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);return dialog;}
    private LinearLayout sheet(String title,String subtitle){LinearLayout sheet=column();sheet.setPadding(dp(24),dp(14),dp(24),dp(28));sheet.setBackground(shape(surface,30,0,0));TextView handle=new TextView(this);handle.setBackground(shape(muted,3,0,0));LinearLayout handleWrap=new LinearLayout(this);handleWrap.setGravity(Gravity.CENTER);handleWrap.addView(handle,lp(36,5));sheet.addView(handleWrap,margin(-1,22,0,0,0,12));sheet.addView(label(title,27,text,true));sheet.addView(label(subtitle,14,muted,false),margin(-1,-2,0,5,0,18));return sheet;}
    private void showBottomDialog(Dialog dialog,View content){showBottomDialog(dialog,content,-2);}
    private void showBottomDialog(Dialog dialog,View content,int height){dialog.setContentView(content);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.getDecorView().setPadding(0,0,0,0);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);WindowManager.LayoutParams p=w.getAttributes();p.width=WindowManager.LayoutParams.MATCH_PARENT;p.height=height;p.gravity=Gravity.BOTTOM;p.x=0;p.y=0;w.setAttributes(p);w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,height);if(motionEnabled())w.setWindowAnimations(R.style.Animation_KeChengBao_Sheet);}dialog.show();}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextSize(16);e.setTextColor(text);e.setHintTextColor(muted);e.setSingleLine(true);e.setPadding(dp(16),0,dp(16),0);e.setBackground(ripple(surfaceHigh,15));return e;}
    private LinearLayout fieldGroup(String title,View field){LinearLayout group=column();group.addView(label(title,13,muted,true),margin(-1,-2,2,0,0,7));group.addView(field,lp(-1,54));return group;}
    private TextView button(String title,boolean filled){TextView b=label(title,15,filled?onPrimary:text,true);b.setGravity(Gravity.CENTER);b.setClickable(true);b.setFocusable(true);b.setBackground(ripple(filled?primary:surfaceHigh,18));return b;}
    private LinearLayout iconTextButton(int iconRes,String title,int color){LinearLayout button=new LinearLayout(this);button.setOrientation(LinearLayout.HORIZONTAL);button.setGravity(Gravity.CENTER);button.setClickable(true);button.setFocusable(true);button.setBackground(ripple(surfaceHigh,18));button.setContentDescription(title);ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setColorFilter(color);button.addView(icon,lp(24,24));TextView textView=label(title,15,color,true);textView.setGravity(Gravity.CENTER_VERTICAL);button.addView(textView,margin(-2,-2,8,0,0,0));return button;}
    private ImageView iconButton(int icon,String desc){ImageView v=new ImageView(this);v.setImageResource(icon);v.setColorFilter(muted);v.setPadding(dp(8),dp(12),dp(8),dp(12));v.setContentDescription(desc);v.setBackground(ripple(Color.TRANSPARENT,20));v.setClickable(true);v.setFocusable(true);return v;}
    private View emptyState(String title,String copy){LinearLayout box=column();box.setGravity(Gravity.CENTER);box.setPadding(dp(26),dp(42),dp(26),dp(42));box.setBackground(shape(surfaceHigh,28,0,0));box.addView(label(title,23,text,true));TextView c=label(copy,15,muted,false);c.setGravity(Gravity.CENTER);box.addView(c,margin(-1,-2,0,8,0,0));return box;}

    private LinkedHashMap<Subject,List<Entry>> groupBySubject(List<Entry> source){LinkedHashMap<Subject,List<Entry>> grouped=new LinkedHashMap<>();for(Entry entry:source){Subject subject=subjectById(entry.subjectId);if(subject!=null)grouped.computeIfAbsent(subject,x->new ArrayList<>()).add(entry);}return grouped;}
    private int totalItems(LinkedHashMap<Subject,List<Entry>> grouped){int total=0;for(Subject subject:grouped.keySet())if(!subject.noItems)total+=subject.items.size();return total;}
    private int countMissingSubjects(LinkedHashMap<Subject,List<Entry>> grouped){int total=0;for(Subject subject:grouped.keySet())if(!subject.noItems&&subject.items.isEmpty())total++;return total;}
    private int countDoneItems(LinkedHashMap<Subject,List<Entry>> grouped){int done=0;for(Subject subject:grouped.keySet())if(!subject.noItems)for(String item:subject.items)if(isPacked(subject.id,item))done++;return done;}
    private String timesFor(List<Entry> source){StringBuilder b=new StringBuilder();for(Entry e:source){if(b.length()>0)b.append(" · ");b.append("第").append(e.period).append("节 ").append(e.time);}return b.toString();}
    private String subjectSummary(Subject subject){if(subject.noItems)return"无需携带物品";if(subject.items.isEmpty())return"尚未设置携带物";return joinItems(subject.items);}
    private String joinItems(List<String> items){return String.join("、",items);}
    private List<String> parseItems(String raw){LinkedHashSet<String> unique=new LinkedHashSet<>();for(String value:raw.split("[、,，;；\\n]+")){String item=value.trim();if(!item.isEmpty())unique.add(item);}return new ArrayList<>(unique);}
    private List<Entry> entriesFor(int day){List<Entry> out=new ArrayList<>();for(Entry entry:entries)if(entry.day==day)out.add(entry);out.sort(Comparator.comparingInt(e->e.period));return out;}
    private List<Entry> entriesForSubject(String id){List<Entry> out=new ArrayList<>();for(Entry e:entries)if(e.subjectId.equals(id))out.add(e);return out;}
    private Subject subjectById(String id){for(Subject s:subjects)if(s.id.equals(id))return s;return null;}
    private String checkKey(LocalDate date,String subjectId,String item){return date+":"+subjectId+":"+b64(item.trim());}
    private String packedKey(String subjectId,String item){return "packed:"+subjectId+":"+b64(item.trim());}
    private boolean isPacked(String subjectId,String item){String key=packedKey(subjectId,item);if(checks.contains(key))return true;String suffix=":"+subjectId+":"+b64(item.trim());for(String saved:checks)if(saved.endsWith(suffix))return true;return false;}
    private void setPacked(String subjectId,String item,boolean packed){String key=packedKey(subjectId,item);String suffix=":"+subjectId+":"+b64(item.trim());checks.removeIf(saved->saved.equals(key)||saved.endsWith(suffix));if(packed)checks.add(key);}
    private void updateClearChecksButton(){if(clearChecksButton==null)return;boolean enabled=!checks.isEmpty();clearChecksButton.setEnabled(enabled);clearChecksButton.setAlpha(enabled?1f:.45f);clearChecksButton.setContentDescription(enabled?"清空已选择的物品":"当前没有已选择的物品");}
    private boolean motionEnabled(){try{return Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;}catch(Exception ignored){return true;}}

    private void loadData(){String subjectRaw=prefs.getString(KEY_SUBJECTS,"");String entryRaw=prefs.getString(KEY_ENTRIES,"");if(!subjectRaw.isEmpty()){for(String line:subjectRaw.split("\\n")){Subject subject=Subject.decode(line);if(subject!=null)subjects.add(subject);}for(String line:entryRaw.split("\\n")){Entry entry=Entry.decode(line);if(entry!=null)entries.add(entry);}}else migrateLegacyOrSeed();checks.addAll(prefs.getStringSet(KEY_CHECKS,new HashSet<>()));migratePackedChecks();}
    private void migratePackedChecks(){Set<String> migrated=new HashSet<>();LocalDate oldest=LocalDate.now().minusDays(1);for(String saved:checks){if(saved.startsWith("packed:")){migrated.add(saved);continue;}int split=saved.indexOf(':');if(split<=0)continue;try{LocalDate date=LocalDate.parse(saved.substring(0,split));if(!date.isBefore(oldest))migrated.add("packed:"+saved.substring(split+1));}catch(Exception ignored){}}if(!migrated.equals(checks)){checks.clear();checks.addAll(migrated);prefs.edit().putStringSet(KEY_CHECKS,new HashSet<>(checks)).apply();}}
    private void migrateLegacyOrSeed(){String legacy=prefs.getString(KEY_LEGACY_COURSES,"");if(legacy.isEmpty())seedData();else{LinkedHashMap<String,Subject> byName=new LinkedHashMap<>();for(String line:legacy.split("\\n")){LegacyCourse old=LegacyCourse.decode(line);if(old==null)continue;Subject subject=byName.get(old.subject);if(subject==null){subject=new Subject(UUID.randomUUID().toString(),old.subject,parseItems(old.items),false);byName.put(old.subject,subject);subjects.add(subject);}else if(!old.items.trim().isEmpty()){LinkedHashSet<String> merged=new LinkedHashSet<>(subject.items);merged.addAll(parseItems(old.items));subject.items=new ArrayList<>(merged);}entries.add(new Entry(old.id,subject.id,old.day,old.period,old.time));}}saveAll();}
    private void seedData(){addDay(0,new String[][]{{"综合科学","08:50–09:30"},{"历史","09:35–10:15"},{"地理","10:20–11:00"},{"人工智能","11:05–11:45"},{"中文阅读","11:50–12:30"},{"英语","14:00–14:40"},{"数学","15:00–15:40"},{"数学保底","15:45–16:25"}});addDay(1,new String[][]{{"英语","08:50–09:30"},{"体育","09:35–10:15"},{"体育","10:20–11:00"},{"公民","11:05–11:45"},{"公民","11:50–12:30"},{"数学","14:00–14:40"},{"班务","15:00–15:40"},{"班务","15:45–16:25"}});addDay(2,new String[][]{{"综合科学","08:50–09:30"},{"综合科学","09:35–10:15"},{"中文","10:20–11:00"},{"资讯科技","11:05–11:45"},{"历史","11:50–12:30"},{"地理","14:00–14:40"},{"数学","15:00–15:40"},{"英语保底","15:45–16:25"}});addDay(3,new String[][]{{"英语","08:50–09:30"},{"中文","09:35–10:15"},{"中文","10:20–11:00"},{"英语阅读与听力","11:05–11:45"},{"综合科学","11:50–12:30"},{"数学","14:00–14:40"},{"音乐","15:00–15:40"},{"余暇活动","15:45–17:10"}});addDay(4,new String[][]{{"英语","08:50–09:30"},{"英语","09:35–10:15"},{"数学实践与阅读","10:20–11:00"},{"视觉艺术","11:05–11:45"},{"应用科学与科技","11:50–12:30"},{"中文","14:00–14:40"},{"数学","15:00–15:40"}});}
    private void addDay(int day,String[][] values){for(int i=0;i<values.length;i++){Subject subject=null;for(Subject candidate:subjects)if(candidate.name.equals(values[i][0])){subject=candidate;break;}if(subject==null){subject=new Subject(UUID.randomUUID().toString(),values[i][0],new ArrayList<>(),false);subjects.add(subject);}entries.add(new Entry("seed-"+day+"-"+(i+1),subject.id,day,i+1,values[i][1]));}}
    private void saveAll(){StringBuilder subjectData=new StringBuilder();for(Subject s:subjects){if(subjectData.length()>0)subjectData.append('\n');subjectData.append(s.encode());}StringBuilder entryData=new StringBuilder();for(Entry e:entries){if(entryData.length()>0)entryData.append('\n');entryData.append(e.encode());}prefs.edit().putString(KEY_SUBJECTS,subjectData.toString()).putString(KEY_ENTRIES,entryData.toString()).apply();TomorrowWidgetProvider.refreshAll(this);}
    private void saveChecks(){prefs.edit().putStringSet(KEY_CHECKS,new HashSet<>(checks)).apply();TomorrowWidgetProvider.refreshAll(this);}
    private String chineseWeek(DayOfWeek day){return new String[]{"星期一","星期二","星期三","星期四","星期五","星期六","星期日"}[day.getValue()-1];}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView label(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    private GradientDrawable shape(int color,int radius,int stroke,int strokeColor){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke>0)d.setStroke(dp(stroke),strokeColor);return d;}
    private android.graphics.drawable.RippleDrawable ripple(int color,int radius){return new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((primary&0x00FFFFFF)|0x28000000),shape(color,radius,0,0),null);}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private LinearLayout.LayoutParams margin(int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=lp(w,h);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(float n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
    private static String b64(String s){return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP|Base64.URL_SAFE);}
    private static String un64(String s){return new String(Base64.decode(s,Base64.NO_WRAP|Base64.URL_SAFE),StandardCharsets.UTF_8);}

    static class Subject{String id,name;List<String> items;boolean noItems;Subject(String id,String name,List<String> items,boolean noItems){this.id=id;this.name=name;this.items=items;this.noItems=noItems;}String encode(){return b64(id)+"|"+b64(name)+"|"+(noItems?"1":"0")+"|"+b64(String.join("\u001F",items));}static Subject decode(String line){try{String[] p=line.split("\\|",-1);List<String> items=new ArrayList<>();String raw=un64(p[3]);if(!raw.isEmpty())for(String x:raw.split("\u001F",-1))items.add(x);return new Subject(un64(p[0]),un64(p[1]),items,"1".equals(p[2]));}catch(Exception e){return null;}}}
    static class Entry{String id,subjectId,time;int day,period;Entry(String id,String subjectId,int day,int period,String time){this.id=id;this.subjectId=subjectId;this.day=day;this.period=period;this.time=time;}String encode(){return b64(id)+"|"+b64(subjectId)+"|"+day+"|"+period+"|"+b64(time);}static Entry decode(String line){try{String[] p=line.split("\\|",-1);return new Entry(un64(p[0]),un64(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3]),un64(p[4]));}catch(Exception e){return null;}}}
    static class LegacyCourse{String id,subject,time,items;int day,period;LegacyCourse(String id,int day,int period,String subject,String time,String items){this.id=id;this.day=day;this.period=period;this.subject=subject;this.time=time;this.items=items;}static LegacyCourse decode(String line){try{String[] p=line.split("\\|",-1);return new LegacyCourse(un64(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]),un64(p[3]),un64(p[4]),un64(p[5]));}catch(Exception e){return null;}}}
}
