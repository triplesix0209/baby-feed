package triplesix.babyfeed;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import com.google.android.gms.auth.api.identity.*;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    SyncSession.Ticket authorizationSession = DriveSync.session();
    Store store;
    LinearLayout root;
    boolean createFolder;
    boolean syncBusy = false;
    boolean selectingDriveFolder = false;
    boolean folderLoading = false, folderPickerOpen = false;
    AlertDialog infoPopup;
    Button infoChange, infoClose;
    TextView infoFolderName, infoFolderId;
    LinearLayout infoStatus;
    AlertDialog feedPopup;

    void dismissFeedPopup() {
        if (feedPopup != null) {
            feedPopup.dismiss();
            feedPopup = null;
        }
    }

    void showFeedPopup(AlertDialog popup) {
        dismissFeedPopup();
        feedPopup = popup;
        popup.setOnDismissListener(
                d -> {
                    if (feedPopup == popup) feedPopup = null;
                });
        popup.show();
    }

    final Handler uiHandler = new Handler(Looper.getMainLooper());
    final Runnable refreshUi = this::render;
    final java.util.concurrent.ExecutorService io = Executors.newSingleThreadExecutor();

    String date(long t) {
        return new SimpleDateFormat("HH:mm · dd/MM/yyyy", I18n.locale(this)).format(new Date(t));
    }

    Runnable countdownTick;

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    public void onCreate(Bundle b) {
        Appearance.applyTheme(this);
        super.onCreate(b);
        store = new Store(this);
        if (b != null) tab = b.getInt("tab", 0);
        SyncWorker.schedule(this);
    }

    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("tab", tab);
    }

    public void onResume() {
        super.onResume();
        Reminders.restore(this);
        refreshSyncUi();
        handleWidgetAction();
    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    void handleWidgetAction() {
        String action = getIntent().getStringExtra("widgetAction");
        if (action == null) return;
        dismissFeedPopup();
        String id = getIntent().getStringExtra("widgetEvent");
        getIntent().removeExtra("widgetAction");
        tab = 0;
        render();
        deleteMode = false;
        selectedFeeds.clear();
        if (action.equals("report")) {
            tab = 1;
            reportDay = java.time.LocalDate.now();
            render();
            return;
        }
        if (action.equals("record")) {
            reportDay = java.time.LocalDate.now();
            recordPast();
            return;
        }
        if (action.equals("vitamin")) {
            recordPopup(java.time.LocalDate.now(), true);
            return;
        }
        if (action.equals("sync")) {
            tab = 2;
            render();
            if (store.prefs().getBoolean("linked", false)) {
                createFolder = false;
                authorize();
            }
            return;
        }
        if (action.equals("more")) {
            widgetActions();
            return;
        }
        if (action.equals("open")) return;
        JSONObject current = store.latest();
        if (action.equals("schedule")) {
            if (current == null || current.optString("kind").equals("cancelled")) schedule();
            else
                message(
                        I18n.text(
                                this,
                                R.string
                                        .the_schedule_has_changed_please_use_the_current_schedule_0));
            return;
        }
        if (current == null || !current.optString("id").equals(id)) {
            message(
                    I18n.text(
                            this,
                            R.string
                                    .this_feed_has_changed_the_widget_was_updated_please_check_the_cur_1));
            return;
        }
        if (action.equals("prepare")
                && Arrays.asList("scheduled", "finished").contains(current.optString("kind")))
            prepare(current);
        if (action.equals("finish") && current.optString("kind").equals("prepared"))
            finishFeed(current);
        if (action.equals("edit")
                && Arrays.asList("scheduled", "finished").contains(current.optString("kind")))
            schedule();
        if (action.equals("cancel") && !current.optString("kind").equals("cancelled"))
            cancelFeed(current);
        if (action.equals("snooze") && current.optString("kind").equals("prepared")) {
            Reminders.silence(this);
            Reminders.snooze(this, id);
            message(I18n.text(this, R.string.we_will_ask_again_in_10_minutes_2));
        }
    }

    void widgetActions() {
        JSONObject current = store.latest();
        boolean empty = current == null || current.optString("kind").equals("cancelled"),
                drinking = !empty && current.optString("kind").equals("prepared");
        List<String> labels = new ArrayList<>(), actions = new ArrayList<>();
        labels.add(
                empty
                        ? I18n.text(this, R.string.schedule_a_feed_3)
                        : drinking
                                ? I18n.text(this, R.string.remind_me_in_10_minutes_4)
                                : I18n.text(this, R.string.change_time_amount_5));
        actions.add(empty ? "schedule" : drinking ? "snooze" : "edit");
        if (!empty) {
            labels.add(I18n.text(this, R.string.cancel_reminders_6));
            actions.add("cancel");
        }
        labels.add(I18n.text(this, R.string.record_an_earlier_feed_7));
        actions.add("record");
        labels.add(I18n.text(this, R.string.view_report_8));
        actions.add("report");
        labels.add(I18n.text(this, R.string.sync_9));
        actions.add("sync");
        String event = empty ? "" : current.optString("id");
        new AlertDialog.Builder(this)
                .setTitle(I18n.text(this, R.string.feed_actions_10))
                .setItems(
                        labels.toArray(new String[0]),
                        (d, which) -> {
                            getIntent()
                                    .putExtra("widgetAction", actions.get(which))
                                    .putExtra("widgetEvent", event);
                            handleWidgetAction();
                        })
                .setNegativeButton(I18n.text(this, R.string.close_11), null)
                .show();
    }

    void cancelFeed(JSONObject event) {
        dismissFeedPopup();
        showFeedPopup(
                ui.confirmationPopup(
                        this,
                        I18n.text(this, R.string.cancel_schedule_14),
                        R.drawable.ic_calendar,
                        event.optString("kind").equals("prepared")
                                ? I18n.text(
                                        this,
                                        R.string.cancel_this_feeding_session_and_its_reminders_12)
                                : I18n.text(this, R.string.cancel_the_next_feed_13),
                        I18n.text(this, R.string.cancel_schedule_14),
                        I18n.text(this, R.string.keep_schedule_16),
                        () -> {
                            JSONObject current = store.latest();
                            if (current == null
                                    || !current.optString("id").equals(event.optString("id"))) {
                                render();
                                message(
                                        I18n.text(
                                                this,
                                                R.string
                                                        .this_feed_has_changed_please_check_again_before_cancelling_15));
                                return;
                            }
                            saveIfCurrent(store.event("cancelled"), event.optString("id"));
                        }));
    }

    public void onPause() {
        super.onPause();
        uiHandler.removeCallbacks(refreshUi);
        if (countdownTick != null) uiHandler.removeCallbacks(countdownTick);
    }

    protected void onDestroy() {
        dismissFeedPopup();
        closeProviderPopup();
        if (infoPopup != null) infoPopup.dismiss();
        if (tipPopup != null) tipPopup.dismiss();
        if (settingsPopup != null) settingsPopup.dismiss();
        io.shutdown();
        super.onDestroy();
    }

    TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(43, 49, 58));
        t.setPadding(0, 12, 0, 12);
        root.addView(t);
        return t;
    }

    void button(String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        root.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> action.run());
    }

    void message(String m) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setMessage(I18n.error(this, m))
                .setPositiveButton(I18n.text(this, R.string.close_11), null)
                .show();
    }

    FeedUi ui;
    ScrollView screenScroll;
    int screenScrollY;
    int renderedTab = -1;
    int tab = 0;
    boolean deleteMode = false;
    final Set<String> selectedFeeds = new HashSet<>();
    TextView deleteCount;
    Button confirmDelete;
    java.time.LocalDate reportDay = java.time.LocalDate.now();

    List<JSONObject> feeds(java.time.LocalDate day) {
        return store.feeds(day);
    }

    void render() {
        if (isFinishing() || isDestroyed()) return;
        int scrollY =
                tab != renderedTab
                        ? 0
                        : screenScroll != null && screenScroll.isLaidOut()
                                ? screenScroll.getScrollY()
                                : screenScrollY;
        screenScrollY = scrollY;
        uiHandler.removeCallbacks(refreshUi);
        if (countdownTick != null) uiHandler.removeCallbacks(countdownTick);
        ui = new FeedUi(this);
        LinearLayout screen = ui.column();
        if (tab == 2) screen.setBackground(ui.settingsBackground());
        else screen.setBackgroundColor(ui.BG);
        PlatformUi.applySystemInsets(screen);
        Appearance.styleWindow(this, ui);
        LinearLayout header = ui.row();
        header.setPadding(ui.dp(24), ui.dp(18), ui.dp(24), ui.dp(18));
        TextView title =
                ui.label(
                        tab == 0
                                ? I18n.text(this, R.string.feeds_17)
                                : tab == 1
                                        ? I18n.text(this, R.string.report_18)
                                        : I18n.text(this, R.string.settings_162),
                        25,
                        ui.INK,
                        true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        screen.addView(header);
        ScrollView scroll = new ScrollView(this);
        screenScroll = scroll;
        renderedTab = tab;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root = ui.column();
        root.setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(24));
        scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tab == 0) home();
        else if (tab == 1) report();
        else settings();
        LinearLayout nav = ui.row();
        nav.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(12));
        nav.setBackgroundColor(ui.SURFACE);
        if (deleteMode) {
            LinearLayout skip = ui.column(), confirm = ui.column();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.rightMargin = ui.dp(12);
            nav.addView(skip, p);
            nav.addView(confirm, new LinearLayout.LayoutParams(0, -2, 1));
            ui.button(
                    skip,
                    I18n.text(this, R.string.cancel_19),
                    false,
                    () -> {
                        deleteMode = false;
                        selectedFeeds.clear();
                        render();
                    });
            confirmDelete =
                    ui.button(
                            confirm,
                            I18n.text(this, R.string.confirm_20),
                            true,
                            this::confirmDeletion);
            updateDeleteCount();
        } else {
            String[] names = {
                I18n.text(this, R.string.feeds_17),
                I18n.text(this, R.string.report_18),
                I18n.text(this, R.string.settings_162)
            };
            for (int n = 0; n < 3; n++) {
                final int page = n;
                TextView item = ui.label(names[n], 14, tab == n ? ui.ACCENT : ui.MUTED, tab == n);
                item.setGravity(Gravity.CENTER);
                item.setPadding(ui.dp(8), ui.dp(16), ui.dp(8), ui.dp(16));
                if (tab == n) item.setBackground(ui.shape(ui.SOFT, 18, false));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
                p.leftMargin = ui.dp(3);
                p.rightMargin = ui.dp(3);
                nav.addView(item, p);
                item.setOnClickListener(
                        v -> {
                            tab = page;
                            render();
                        });
            }
        }
        screen.addView(nav);
        setContentView(screen);
        scroll.post(
                () -> {
                    if (screenScroll == scroll) scroll.scrollTo(0, scrollY);
                });
        screen.requestApplyInsets();
        refreshInfo();
        refreshProviderPopup();
    }

    void updateDeleteCount() {
        if (deleteCount != null)
            deleteCount.setText(
                    getResources()
                            .getQuantityString(
                                    R.plurals.selected_count,
                                    selectedFeeds.size(),
                                    selectedFeeds.size()));
        if (confirmDelete != null)
            confirmDelete.setText(
                    selectedFeeds.isEmpty()
                            ? I18n.text(this, R.string.confirm_20)
                            : I18n.text(this, R.string.confirm_23) + selectedFeeds.size() + ")");
    }

    void confirmDeletion() {
        if (selectedFeeds.isEmpty()) {
            message(I18n.text(this, R.string.select_at_least_one_feed_to_delete_24));
            return;
        }
        try {
            Set<String> milk = new HashSet<>(), vitamins = new HashSet<>();
            for (String key : selectedFeeds)
                if (key.startsWith("vitamin:")) vitamins.add(key.substring("vitamin:".length()));
                else milk.add(key);
            store.deleteRecords(milk, vitamins);
            deleteMode = false;
            selectedFeeds.clear();
            SyncWorker.now(this);
            render();
        } catch (Exception error) {
            message(error.getMessage());
        }
    }

    void home() {
        ui.text(
                root,
                I18n.text(this, R.string.a_little_reminder_a_little_peace_of_mind_25),
                14,
                ui.MUTED,
                false);
        ui.gap(root, 12);
        permissions();
        JSONObject latest = store.latest();
        boolean empty = latest == null || latest.optString("kind").equals("cancelled"),
                prepared = !empty && latest.optString("kind").equals("prepared");
        LinearLayout hero = ui.card(root, ui.SOFT);
        LinearLayout topline = ui.row();
        TextView tag =
                ui.label(
                        prepared
                                ? I18n.text(this, R.string.baby_is_feeding_26)
                                : I18n.text(this, R.string.next_feed_27),
                        11,
                        ui.ACCENT,
                        true);
        tag.setLetterSpacing(.12f);
        topline.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
        FeedUi.Bottle bottle = new FeedUi.Bottle(this);
        topline.addView(bottle, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)));
        hero.addView(topline);
        if (empty) {
            ui.text(hero, I18n.text(this, R.string.ready_for_the_first_feed_28), 27, ui.INK, true);
            ui.text(
                    hero,
                    I18n.text(
                            this,
                            R.string.choose_a_time_and_amount_we_will_handle_the_reminders_29),
                    15,
                    ui.MUTED,
                    false);
            ui.button(hero, I18n.text(this, R.string.schedule_a_feed_30), true, this::schedule);
        } else if (prepared) {
            long untilCheck = latest.optLong("preparedAt") + 1800000 - System.currentTimeMillis();
            if (untilCheck > 0) uiHandler.postDelayed(refreshUi, untilCheck);
            ui.amount(hero, Amounts.ml(latest.optInt("ml"), I18n.locale(this)), 40, ui.ACCENT);
            ui.text(
                    hero,
                    I18n.text(this, R.string.prepared_at_31) + date(latest.optLong("preparedAt")),
                    14,
                    ui.INK,
                    false);
            ui.text(
                    hero,
                    I18n.text(this, R.string.check_again_at_32)
                            + new SimpleDateFormat("HH:mm", I18n.locale(this))
                                    .format(new Date(latest.optLong("preparedAt") + 1800000)),
                    14,
                    ui.MUTED,
                    false);
            ui.button(
                    hero,
                    I18n.text(this, R.string.baby_has_finished_33),
                    true,
                    () -> finishFeed(latest));
            if (System.currentTimeMillis() >= latest.optLong("preparedAt") + 1800000)
                ui.button(
                        hero,
                        I18n.text(this, R.string.remind_me_in_10_minutes_4),
                        false,
                        () -> {
                            Reminders.silence(this);
                            Reminders.snooze(this, latest.optString("id"));
                            message(I18n.text(this, R.string.we_will_ask_again_in_10_minutes_2));
                        });
        } else {
            LinearLayout clock = ui.row();
            TextView time =
                    ui.label(
                            new SimpleDateFormat("HH:mm", I18n.locale(this))
                                    .format(new Date(latest.optLong("nextAt"))),
                            48,
                            ui.INK,
                            true);
            clock.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
            TextView remaining = ui.label("", 14, ui.ACCENT, true);
            clock.addView(remaining);
            hero.addView(clock);
            ui.gap(hero, 12);
            long nextAt = latest.optLong("nextAt");
            countdownTick =
                    () -> {
                        remaining.setText(
                                I18n.countdown(this, nextAt - System.currentTimeMillis()));
                        if (nextAt > System.currentTimeMillis())
                            uiHandler.postDelayed(countdownTick, 1000);
                    };
            countdownTick.run();
            ui.text(
                    hero,
                    new SimpleDateFormat("EEEE, dd/MM/yyyy", I18n.locale(this))
                            .format(new Date(nextAt)),
                    14,
                    ui.MUTED,
                    false);
            ui.text(
                    hero,
                    Amounts.ml(latest.optInt("ml"), I18n.locale(this))
                            + I18n.text(this, R.string.milk_34),
                    23,
                    ui.ACCENT,
                    true);
            ui.gap(hero, 8);
            ui.button(
                    hero, I18n.text(this, R.string.milk_prepared_35), true, () -> prepare(latest));
            ui.button(hero, I18n.text(this, R.string.change_time_amount_5), false, this::schedule);
        }
        if (!empty) {
            TextView cancel =
                    ui.text(
                            hero,
                            I18n.text(this, R.string.cancel_reminders_6),
                            13,
                            ui.MUTED,
                            false);
            cancel.setGravity(Gravity.CENTER);
            cancel.setMinHeight(ui.dp(48));
            cancel.setPadding(0, ui.dp(16), 0, ui.dp(8));
            cancel.setOnClickListener(v -> cancelFeed(latest));
        }
        LinearLayout heading = ui.row();
        heading.addView(
                ui.label(I18n.text(this, R.string.today_36), 21, ui.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView more = ui.label(I18n.text(this, R.string.view_report_37), 13, ui.ACCENT, true);
        more.setPadding(0, ui.dp(12), 0, ui.dp(12));
        heading.addView(more);
        more.setOnClickListener(
                v -> {
                    tab = 1;
                    reportDay = java.time.LocalDate.now();
                    render();
                });
        root.addView(heading);
        ui.gap(root, 12);
        JSONObject todayVitamin = store.vitamin(java.time.LocalDate.now());
        stats(
                root,
                feeds(java.time.LocalDate.now()),
                todayVitamin != null && todayVitamin.optBoolean("taken"));
        ui.gap(root, 20);
        long midnight =
                java.time.LocalDate.now()
                        .plusDays(1)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
        uiHandler.postDelayed(refreshUi, Math.max(1, midnight - System.currentTimeMillis()));
        ui.iconButton(
                root,
                I18n.text(this, R.string.record_38),
                true,
                R.drawable.ic_pencil,
                () -> {
                    reportDay = java.time.LocalDate.now();
                    recordPast();
                });
    }

    void permissions() {
        boolean notifications =
                Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                                != android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean exact =
                Build.VERSION.SDK_INT >= 31
                        && !getSystemService(AlarmManager.class).canScheduleExactAlarms();
        boolean fullscreen =
                Build.VERSION.SDK_INT >= 34
                        && !getSystemService(NotificationManager.class).canUseFullScreenIntent();
        if (!notifications && !exact && !fullscreen) return;
        LinearLayout box = ui.card(root, Color.rgb(255, 242, 200));
        ui.text(
                box,
                I18n.text(this, R.string.enable_feeding_reminders_39),
                16,
                Color.rgb(139, 88, 12),
                true);
        if (notifications)
            ui.yellowOutline(
                    ui.button(
                            box,
                            I18n.text(this, R.string.allow_notifications_40),
                            false,
                            () ->
                                    requestPermissions(
                                            new String[] {
                                                android.Manifest.permission.POST_NOTIFICATIONS
                                            },
                                            9)));
        if (exact) {
            ui.text(
                    box,
                    I18n.text(
                            this, R.string.allow_exact_alarms_to_help_reminders_arrive_on_time_41),
                    13,
                    Color.rgb(139, 105, 43),
                    false);
            ui.yellowOutline(
                    ui.button(
                            box,
                            I18n.text(this, R.string.allow_exact_alarms_42),
                            false,
                            () ->
                                    startActivity(
                                            new Intent(
                                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                    Uri.parse("package:" + getPackageName())))));
        }
        if (fullscreen)
            ui.yellowOutline(
                    ui.button(
                            box,
                            I18n.text(this, R.string.allow_full_screen_alarms_43),
                            false,
                            () ->
                                    startActivity(
                                            new Intent(
                                                    Settings
                                                            .ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                                    Uri.parse("package:" + getPackageName())))));
    }

    void stats(LinearLayout parent, List<JSONObject> list, boolean vitaminTaken) {
        long drank = 0, left = 0;
        for (JSONObject e : list) {
            drank += e.optInt("consumed");
            left += e.optInt("left");
        }
        LinearLayout row = ui.row();
        ui.stat(
                row,
                I18n.text(this, R.string.consumed_44),
                Amounts.ml(drank, I18n.locale(this)),
                false,
                ui.GREEN,
                ui.GREEN_SOFT);
        ui.stat(
                row,
                I18n.text(this, R.string.record_tab_vitamin),
                I18n.text(this, vitaminTaken ? R.string.vitamin_yes : R.string.vitamin_no),
                false,
                ui.ACCENT,
                ui.SOFT,
                "K2D3");
        ui.stat(
                row,
                I18n.text(this, R.string.discarded_46),
                Amounts.ml(left, I18n.locale(this)),
                true,
                ui.RED,
                ui.RED_SOFT);
        parent.addView(row);
        ui.fitStats(row);
    }

    void report() {
        JSONObject vitamin = store.vitamin(reportDay);
        boolean vitaminTaken = vitamin != null && vitamin.optBoolean("taken");
        if (!deleteMode) {
            LinearLayout actions = ui.row();
            LinearLayout add = ui.column(), remove = ui.column();
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1);
            a.rightMargin = ui.dp(10);
            actions.addView(add, a);
            ui.iconButton(
                    add,
                    I18n.text(this, R.string.record_38),
                    true,
                    R.drawable.ic_pencil,
                    this::recordPast);
            if (!feeds(reportDay).isEmpty() || vitaminTaken) {
                actions.addView(remove, new LinearLayout.LayoutParams(0, -2, 1));
                ui.redOutline(
                        ui.button(
                                remove,
                                I18n.text(this, R.string.delete_47),
                                false,
                                () -> {
                                    deleteMode = true;
                                    selectedFeeds.clear();
                                    render();
                                }));
            }
            root.addView(actions);
            ui.gap(root, 16);
        } else {
            LinearLayout actions = ui.card(root, ui.SOFT);
            ui.text(actions, I18n.text(this, R.string.select_feeds_to_delete_48), 17, ui.INK, true);
            deleteCount =
                    ui.text(
                            actions,
                            getResources()
                                    .getQuantityString(
                                            R.plurals.selected_count,
                                            selectedFeeds.size(),
                                            selectedFeeds.size()),
                            13,
                            ui.MUTED,
                            false);
        }
        LinearLayout frame = ui.card(root, ui.SURFACE);
        frame.setBackground(ui.shape(ui.SURFACE, 28, false));
        LinearLayout selector = ui.row();
        TextView prev = ui.label("‹", 30, ui.ACCENT, true),
                next =
                        ui.label(
                                "›",
                                30,
                                reportDay.isBefore(java.time.LocalDate.now()) ? ui.ACCENT : ui.LINE,
                                true);
        prev.setGravity(Gravity.CENTER);
        next.setGravity(Gravity.CENTER);
        selector.addView(prev, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(56)));
        TextView day =
                ui.label(
                        reportDay.format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        ("en".equals(I18n.language(this))
                                                ? "MMM dd, yyyy"
                                                : "dd 'thg' MM, yyyy"),
                                        I18n.locale(this))),
                        18,
                        ui.ACCENT,
                        true);
        day.setGravity(Gravity.CENTER);
        day.setPadding(ui.dp(4), ui.dp(14), ui.dp(4), ui.dp(14));
        day.setBackground(ui.shape(ui.SOFT, 18, false));
        selector.addView(day, new LinearLayout.LayoutParams(0, -2, 1));
        selector.addView(next, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(56)));
        frame.addView(selector);
        prev.setContentDescription(I18n.text(this, R.string.previous_day_49));
        next.setContentDescription(I18n.text(this, R.string.next_day_50));
        prev.setOnClickListener(
                v -> {
                    reportDay = reportDay.minusDays(1);
                    render();
                });
        next.setOnClickListener(
                v -> {
                    if (reportDay.isBefore(java.time.LocalDate.now())) {
                        reportDay = reportDay.plusDays(1);
                        render();
                    }
                });
        day.setOnClickListener(
                v -> {
                    DatePickerDialog picker =
                            new FeedPickers.DateDialog(
                                    this,
                                    (p, y, m, d) -> {
                                        reportDay = java.time.LocalDate.of(y, m + 1, d);
                                        render();
                                    },
                                    reportDay.getYear(),
                                    reportDay.getMonthValue() - 1,
                                    reportDay.getDayOfMonth());
                    picker.getDatePicker().setMaxDate(System.currentTimeMillis());
                    picker.show();
                });
        ui.gap(frame, 18);
        List<JSONObject> list = feeds(reportDay);
        stats(frame, list, vitaminTaken);
        ui.gap(frame, 18);
        if (list.isEmpty() && !vitaminTaken) {
            ui.text(frame, I18n.text(this, R.string.no_data_51), 18, ui.MUTED, false)
                    .setGravity(Gravity.CENTER);
            return;
        }
        LinearLayout report = ui.card(frame, ui.SOFT);
        List<JSONObject> history = new ArrayList<>(list);
        if (vitaminTaken) history.add(vitamin);
        history.sort(
                Comparator.comparingLong(
                                (JSONObject e) ->
                                        e.optLong(
                                                VitaminRecords.isVitamin(e)
                                                        ? "takenAt"
                                                        : "preparedAt"))
                        .reversed()
                        .thenComparing(e -> e.optString("id")));
        for (int n = 0; n < history.size(); n++) {
            JSONObject e = history.get(n);
            boolean isVitamin = VitaminRecords.isVitamin(e);
            if (n > 0) ui.divider(report);
            LinearLayout row = ui.row();
            TextView icon = ui.label("●", 18, isVitamin ? ui.ACCENT : ui.GREEN, true);
            row.addView(icon, new LinearLayout.LayoutParams(ui.dp(28), -2));
            TextView time =
                    ui.label(
                            new SimpleDateFormat("HH:mm", I18n.locale(this))
                                    .format(
                                            new Date(
                                                    e.optLong(
                                                            isVitamin ? "takenAt" : "preparedAt"))),
                            22,
                            ui.INK,
                            true);
            row.addView(time, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(
                    ui.label(
                            isVitamin
                                    ? I18n.text(this, R.string.vitamin_title)
                                    : Amounts.ml(e.optInt("consumed"), I18n.locale(this)),
                            isVitamin ? 16 : 22,
                            ui.INK,
                            true));
            if (deleteMode) {
                String key = isVitamin ? "vitamin:" + e.optString("day") : e.optString("feed");
                CheckBox choose = new CheckBox(this);
                choose.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.ACCENT));
                choose.setContentDescription(
                        I18n.text(this, R.string.select_feed_56)
                                + (isVitamin ? I18n.text(this, R.string.vitamin_title) + " " : "")
                                + time.getText());
                choose.setChecked(selectedFeeds.contains(key));
                row.addView(choose);
                choose.setOnCheckedChangeListener(
                        (b, checked) -> {
                            if (checked) selectedFeeds.add(key);
                            else selectedFeeds.remove(key);
                            updateDeleteCount();
                        });
                row.setOnClickListener(v -> choose.setChecked(!choose.isChecked()));
            }
            report.addView(row);
            ui.gap(report, 8);
            TextView details =
                    ui.text(
                            report,
                            isVitamin
                                    ? I18n.text(this, R.string.vitamin_taken)
                                    : I18n.text(this, R.string.prepared_161)
                                            + Amounts.ml(e.optInt("preparedMl"), I18n.locale(this))
                                            + I18n.text(this, R.string.discarded_57)
                                            + Amounts.ml(e.optInt("left"), I18n.locale(this)),
                            12,
                            ui.MUTED,
                            false);
            if (deleteMode) details.setOnClickListener(v -> row.performClick());
        }
    }

    void saveVitamin(java.time.LocalDate day, boolean taken, long at) {
        try {
            save(
                    store.event(VitaminRecords.KIND)
                            .put("day", day.toString())
                            .put("taken", taken)
                            .put("takenAt", at));
        } catch (org.json.JSONException error) {
            throw new IllegalStateException(error);
        }
    }

    void settings() {
        ui.section(root, I18n.text(this, R.string.settings_display));
        boolean editable = !syncBusy && !folderLoading && !folderPickerOpen;
        ui.settingRow(
                        root,
                        I18n.text(this, R.string.language_163),
                        "en".equals(I18n.language(this)) ? "English" : "Tiếng Việt",
                        R.drawable.ic_settings_language,
                        this::chooseLanguage)
                .setTag("language-setting");
        ui.settingRow(
                        root,
                        I18n.text(this, R.string.settings_appearance),
                        appearanceLabel(),
                        R.drawable.ic_settings_appearance,
                        this::chooseAppearance)
                .setTag("appearance-setting");
        root.findViewWithTag("language-setting").setEnabled(editable);
        root.findViewWithTag("appearance-setting").setEnabled(editable);
        ui.section(root, I18n.text(this, R.string.settings_notifications));
        vitaminReminderSettings();
        sleepHoursSettings();
        ui.section(root, I18n.text(this, R.string.settings_widget));
        ui.settingRow(
                root,
                I18n.text(this, R.string.widget_settings),
                null,
                R.drawable.ic_settings_widget,
                this::chooseWidget);
        ui.section(root, I18n.text(this, R.string.sync_9));
        for (SyncProvider provider : SyncProvider.values()) providerRow(provider);
        ui.section(root, I18n.text(this, R.string.settings_tips));
        ui.settingRow(
                root,
                I18n.text(this, R.string.care_for_baby_together_58),
                null,
                R.drawable.ic_settings_tip,
                this::family);
        ui.section(root, I18n.text(this, R.string.settings_info));
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
        ui.settingRow(
                root,
                I18n.text(this, R.string.app_version_164),
                version,
                R.drawable.ic_settings_info,
                null);
    }

    void sleepHoursSettings() {
        LinearLayout card = ui.card(root, ui.SURFACE);
        card.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        LinearLayout row = ui.row();
        card.addView(row);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_settings_sleep);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(ui.ACCENT));
        icon.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        icon.setBackground(ui.shape(ui.SURFACE, 20, true));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38));
        iconSize.rightMargin = ui.dp(13);
        row.addView(icon, iconSize);
        row.addView(
                ui.label(I18n.text(this, R.string.sleep_hours), 16, ui.INK, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setContentDescription(I18n.text(this, R.string.sleep_hours));
        toggle.setMinHeight(ui.dp(48));
        toggle.setChecked(store.prefs().getBoolean(SleepHours.ENABLED, false));
        row.addView(toggle);
        LinearLayout times = ui.row();
        LinearLayout.LayoutParams timesSize = new LinearLayout.LayoutParams(-1, -2);
        timesSize.topMargin = ui.dp(12);
        timesSize.bottomMargin = ui.dp(8);
        card.addView(times, timesSize);
        for (boolean start : new boolean[] {true, false}) {
            LinearLayout field = ui.column();
            field.setGravity(Gravity.CENTER);
            field.setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(12));
            field.setBackground(
                    new android.graphics.drawable.RippleDrawable(
                            android.content.res.ColorStateList.valueOf(0x229DC2FF),
                            ui.shape(ui.SOFT, 16, false),
                            null));
            TextView label =
                    ui.label(
                            I18n.text(
                                    this,
                                    start ? R.string.sleep_hours_start : R.string.sleep_hours_end),
                            13,
                            ui.MUTED,
                            false);
            label.setGravity(Gravity.CENTER);
            field.addView(label, new LinearLayout.LayoutParams(-1, -2));
            TextView time = ui.label(sleepTimeLabel(start), 25, ui.ACCENT, true);
            time.setGravity(Gravity.CENTER);
            time.setPadding(0, ui.dp(6), 0, 0);
            field.addView(time, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (start) size.rightMargin = ui.dp(10);
            times.addView(field, size);
            field.setContentDescription(
                    I18n.text(this, start ? R.string.sleep_hours_start : R.string.sleep_hours_end)
                            + " "
                            + sleepTimeLabel(start));
            field.setFocusable(true);
            field.setOnClickListener(v -> chooseSleepTime(start));
        }
        times.setVisibility(toggle.isChecked() ? View.VISIBLE : View.GONE);
        toggle.setOnCheckedChangeListener(
                (button, enabled) -> {
                    store.prefs().edit().putBoolean(SleepHours.ENABLED, enabled).apply();
                    times.setVisibility(enabled ? View.VISIBLE : View.GONE);
                    if (Reminders.isSleeping(this)) Reminders.silence(this);
                    VitaminReminders.restore(this);
                });
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        row.setFocusable(true);
        TextView description =
                ui.text(
                        card,
                        I18n.text(this, R.string.sleep_hours_description),
                        13,
                        ui.MUTED,
                        false);
        description.setPadding(0, ui.dp(6), 0, 0);
    }

    int sleepTime(boolean start) {
        return store.prefs()
                .getInt(
                        start ? SleepHours.START : SleepHours.END,
                        start ? SleepHours.DEFAULT_START : SleepHours.DEFAULT_END);
    }

    String sleepTimeLabel(boolean start) {
        int minutes = sleepTime(start);
        return String.format(I18n.locale(this), "%02d:%02d", minutes / 60, minutes % 60);
    }

    void chooseSleepTime(boolean start) {
        int current = sleepTime(start);
        TimePickerDialog picker =
                new FeedPickers.TimeDialog(
                        this,
                        (view, hour, minute) -> {
                            int selected = hour * 60 + minute;
                            if (selected == sleepTime(!start)) {
                                message(I18n.text(this, R.string.sleep_hours_different_times));
                                return;
                            }
                            store.prefs()
                                    .edit()
                                    .putInt(start ? SleepHours.START : SleepHours.END, selected)
                                    .apply();
                            if (Reminders.isSleeping(this)) Reminders.silence(this);
                            VitaminReminders.restore(this);
                            render();
                        },
                        current / 60,
                        current % 60,
                        true);
        picker.setTitle(
                I18n.text(this, start ? R.string.sleep_hours_start : R.string.sleep_hours_end));
        picker.show();
    }

    void vitaminReminderSettings() {
        LinearLayout card = ui.card(root, ui.SURFACE);
        card.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        LinearLayout row = ui.row();
        card.addView(row);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_vitamin);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(ui.ACCENT));
        icon.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        icon.setBackground(ui.shape(ui.SURFACE, 20, true));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38));
        iconSize.rightMargin = ui.dp(13);
        row.addView(icon, iconSize);
        row.addView(
                ui.label(I18n.text(this, R.string.vitamin_reminder_time), 16, ui.INK, false),
                new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setContentDescription(I18n.text(this, R.string.vitamin_reminder_time));
        toggle.setMinHeight(ui.dp(48));
        toggle.setChecked(store.prefs().getBoolean(VitaminReminders.ENABLED, false));
        row.addView(toggle);
        LinearLayout timeBox = ui.column();
        card.addView(timeBox);
        int current = VitaminReminders.time(store.prefs());
        Button time =
                ui.button(
                        timeBox,
                        String.format(I18n.locale(this), "%02d:%02d", current / 60, current % 60),
                        false,
                        () -> {});
        time.setTextSize(25);
        time.setContentDescription(
                I18n.text(this, R.string.vitamin_reminder_time) + " " + time.getText());
        time.setOnClickListener(
                v -> {
                    int value = VitaminReminders.time(store.prefs());
                    TimePickerDialog picker =
                            new FeedPickers.TimeDialog(
                                    this,
                                    (view, hour, minute) -> {
                                        store.prefs()
                                                .edit()
                                                .putInt(VitaminReminders.TIME, hour * 60 + minute)
                                                .apply();
                                        VitaminReminders.cancelNotification(this);
                                        VitaminReminders.restore(this);
                                        render();
                                    },
                                    value / 60,
                                    value % 60,
                                    true);
                    picker.setTitle(I18n.text(this, R.string.vitamin_reminder_time));
                    picker.show();
                });
        timeBox.setVisibility(toggle.isChecked() ? View.VISIBLE : View.GONE);
        toggle.setOnCheckedChangeListener(
                (button, enabled) -> {
                    store.prefs().edit().putBoolean(VitaminReminders.ENABLED, enabled).apply();
                    timeBox.setVisibility(enabled ? View.VISIBLE : View.GONE);
                    VitaminReminders.restore(this);
                    if (enabled
                            && Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        requestPermissions(
                                new String[] {android.Manifest.permission.POST_NOTIFICATIONS}, 9);
                });
        row.setFocusable(true);
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        TextView description =
                ui.text(
                        card,
                        I18n.text(this, R.string.vitamin_reminder_description),
                        13,
                        ui.MUTED,
                        false);
        description.setPadding(0, ui.dp(8), 0, 0);
    }

    String appearanceLabel() {
        String mode = Appearance.mode(this);
        return I18n.text(
                this,
                mode.equals("light")
                        ? R.string.appearance_light
                        : mode.equals("dark")
                                ? R.string.appearance_dark
                                : R.string.appearance_system);
    }

    AlertDialog settingsPopup;

    void chooseLanguage() {
        String[] codes = {"vi", "en"};
        settingsPopup =
                ui.choicePopup(
                        this,
                        I18n.text(this, R.string.language_163),
                        R.drawable.ic_settings_language,
                        new String[] {"Tiếng Việt", "English"},
                        "en".equals(I18n.language(this)) ? 1 : 0,
                        which -> {
                            if (codes[which].equals(I18n.language(this))) return;
                            store.prefs().edit().putString("language", codes[which]).apply();
                            NextFeedWidget.updateAll(this);
                            recreate();
                        });
    }

    void chooseAppearance() {
        String[] modes = {"light", "dark", "system"};
        String[] labels = {
            I18n.text(this, R.string.appearance_light),
            I18n.text(this, R.string.appearance_dark),
            I18n.text(this, R.string.appearance_system)
        };
        settingsPopup =
                ui.choicePopup(
                        this,
                        I18n.text(this, R.string.settings_appearance),
                        R.drawable.ic_settings_appearance,
                        labels,
                        Arrays.asList(modes).indexOf(Appearance.mode(this)),
                        which -> {
                            if (modes[which].equals(Appearance.mode(this))) return;
                            store.prefs().edit().putString("appearance", modes[which]).apply();
                            recreate();
                        });
    }

    void chooseWidget() {
        int[] ids = NextFeedWidget.ids(this);
        if (ids.length == 0) {
            message(I18n.text(this, R.string.widget_add_hint));
            return;
        }
        if (ids.length == 1) {
            openWidgetSettings(ids[0]);
            return;
        }
        String[] labels = new String[ids.length];
        for (int n = 0; n < ids.length; n++) labels[n] = getString(R.string.widget_number, n + 1);
        new AlertDialog.Builder(this)
                .setTitle(I18n.text(this, R.string.widget_settings))
                .setItems(labels, (d, which) -> openWidgetSettings(ids[which]))
                .setNegativeButton(I18n.text(this, R.string.close_11), null)
                .show();
    }

    void openWidgetSettings(int id) {
        startActivity(
                new Intent(this, WidgetSettingsActivity.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
    }

    AlertDialog tipPopup;

    void family() {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(12));
        LinearLayout header = ui.row();
        header.setPadding(0, 0, 0, ui.dp(12));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_settings_tip);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(ui.ACCENT));
        icon.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        icon.setBackground(ui.shape(ui.SOFT, 16, false));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38));
        iconSize.rightMargin = ui.dp(12);
        header.addView(icon, iconSize);
        header.addView(
                ui.label(I18n.text(this, R.string.care_for_baby_together_58), 19, ui.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView close = ui.label("×", 26, ui.MUTED, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(I18n.text(this, R.string.close_11));
        close.setBackground(ui.shape(ui.BG, 16, false));
        header.addView(close, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        close.setOnClickListener(
                v -> {
                    if (tipPopup != null) tipPopup.dismiss();
                });
        content.addView(header);
        LinearLayout body = ui.column();
        ScrollView scroll = ui.popupScroll();
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, ui.dp(4));
        scroll.addView(body);
        content.addView(scroll);
        LinearLayout introduction = ui.card(body, ui.SOFT);
        ui.text(
                introduction,
                I18n.text(
                        this,
                        R.string
                                .family_members_can_use_different_email_accounts_select_the_same_g_62),
                14,
                ui.INK,
                false);
        syncGuideStep(
                body,
                1,
                I18n.text(this, R.string.step_1_connect_on_the_first_device_63),
                I18n.text(
                        this,
                        R.string
                                .in_baby_feed_tap_connect_under_google_drive_and_choose_your_googl_64));
        syncGuideStep(
                body,
                2,
                I18n.text(this, R.string.step_2_share_the_folder_with_your_family_65),
                I18n.text(
                        this,
                        R.string
                                .tap_next_to_google_drive_to_see_the_folder_name_open_google_drive_66));
        syncGuideStep(
                body,
                3,
                I18n.text(this, R.string.step_3_select_the_shared_folder_on_the_second_device_67),
                I18n.text(
                        this,
                        R.string
                                .open_baby_feed_on_the_other_device_tap_connect_and_choose_the_inv_68));
        syncGuideStep(
                body,
                4,
                I18n.text(this, R.string.step_4_sync_both_devices_69),
                I18n.text(
                        this,
                        R.string
                                .connect_to_the_internet_and_tap_sync_on_the_first_device_then_on__70));
        LinearLayout note = ui.card(body, ui.SOFT);
        ui.text(
                note,
                I18n.text(this, R.string.choose_the_correct_shared_folder_71),
                15,
                ui.ACCENT,
                true);
        ui.text(
                note,
                I18n.text(
                        this,
                        R.string
                                .two_folders_named_baby_feed_may_be_different_folders_avoid_select_72),
                14,
                ui.INK,
                false);
        ui.text(
                note,
                I18n.text(
                        this,
                        R.string
                                .use_the_latest_baby_feed_version_on_all_devices_after_setup_the_a_73),
                14,
                ui.INK,
                false);
        tipPopup = ui.popup(this, content);
    }

    void syncGuideStep(LinearLayout parent, int step, String title, String instructions) {
        LinearLayout card = ui.card(parent, ui.BG), heading = ui.row();
        heading.setGravity(Gravity.TOP);
        TextView number = ui.label(Amounts.number(step, I18n.locale(this)), 14, ui.ACCENT, true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(ui.shape(ui.SOFT, 12, false));
        LinearLayout.LayoutParams badge = new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30));
        badge.rightMargin = ui.dp(10);
        heading.addView(number, badge);
        heading.addView(
                ui.label(title.replaceFirst("^\\d+\\.\\s*", ""), 15, ui.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(heading);
        ui.gap(card, 10);
        ui.text(card, instructions, 14, ui.MUTED, false);
    }

    enum SyncProvider {
        GOOGLE_DRIVE("Google Drive", R.drawable.ic_google_drive);
        final String title;
        final int icon;

        SyncProvider(String title, int icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    AlertDialog providerPopup;
    LinearLayout providerPopupContent;
    SyncProvider popupProvider;

    void providerRow(SyncProvider provider) {
        boolean linked = store.prefs().getBoolean("linked", false);
        ui.settingRow(
                        root,
                        provider.title,
                        I18n.text(
                                this,
                                linked ? R.string.connected_provider : R.string.not_connected_78),
                        provider.icon,
                        () -> {
                            LinearLayout content = ui.column();
                            ScrollView scroll = ui.popupScroll();
                            scroll.addView(content);
                            providerPopupContent = content;
                            popupProvider = provider;
                            providerDetails(content, provider);
                            providerPopup = ui.popup(this, scroll);
                            AlertDialog popup = providerPopup;
                            popup.setOnDismissListener(
                                    d -> {
                                        if (providerPopup == popup) {
                                            providerPopup = null;
                                            providerPopupContent = null;
                                            popupProvider = null;
                                        }
                                    });
                        })
                .setTag(provider);
    }

    /** Drive callbacks update the existing settings views without replacing the scrolled page. */
    void refreshSyncUi() {
        if (isFinishing() || isDestroyed()) return;
        if (tab != 2 || renderedTab != 2 || root == null) {
            render();
            return;
        }
        boolean editable = !syncBusy && !folderLoading && !folderPickerOpen;
        root.findViewWithTag("language-setting").setEnabled(editable);
        root.findViewWithTag("appearance-setting").setEnabled(editable);
        boolean linked = store.prefs().getBoolean("linked", false);
        String status =
                I18n.text(this, linked ? R.string.connected_provider : R.string.not_connected_78);
        for (SyncProvider provider : SyncProvider.values()) {
            View row = root.findViewWithTag(provider);
            if (row != null) {
                TextView value = row.findViewWithTag("setting-value");
                if (value != null && !status.contentEquals(value.getText())) value.setText(status);
            }
        }
        refreshInfo();
        refreshProviderPopup();
    }

    void closeProviderPopup() {
        if (providerPopup != null) providerPopup.dismiss();
        providerPopup = null;
        providerPopupContent = null;
        popupProvider = null;
    }

    void refreshProviderPopup() {
        if (providerPopup == null || !providerPopup.isShowing() || providerPopupContent == null)
            return;
        providerPopupContent.removeAllViews();
        providerDetails(providerPopupContent, popupProvider);
    }

    void providerDetails(LinearLayout parent, SyncProvider provider) {
        boolean linked = store.prefs().getBoolean("linked", false);
        LinearLayout entry = ui.card(parent, ui.SURFACE), heading = ui.row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(provider.icon);
        if (!linked) {
            android.graphics.ColorMatrix gray = new android.graphics.ColorMatrix();
            gray.setSaturation(0);
            icon.setColorFilter(new android.graphics.ColorMatrixColorFilter(gray));
            icon.setAlpha(.55f);
        }
        icon.setContentDescription(provider.title);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
        iconSize.rightMargin = ui.dp(14);
        heading.addView(icon, iconSize);
        ((LinearLayout.LayoutParams) entry.getLayoutParams()).bottomMargin = 0;
        LinearLayout labels = ui.column();
        ui.text(labels, provider.title, 19, ui.INK, true);
        long last = store.prefs().getLong("lastSync", 0);
        heading.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView info = ui.label("ⓘ", 25, ui.ACCENT, false);
        info.setGravity(Gravity.CENTER);
        info.setContentDescription(I18n.text(this, R.string.about_74) + provider.title);
        if (linked) heading.addView(info, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        info.setOnClickListener(
                v -> {
                    closeProviderPopup();
                    providerInfo(provider);
                });
        entry.addView(heading);
        info.setEnabled(!syncBusy);
        info.setAlpha(syncBusy ? .5f : 1f);
        syncStatusRow(entry);
        LinearLayout actions = ui.row(), link = ui.column(), refresh = ui.column();
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, -2, 1);
        first.rightMargin = linked ? ui.dp(10) : 0;
        actions.addView(link, first);
        Button connect =
                ui.button(
                        link,
                        linked
                                ? I18n.text(this, R.string.disconnect_75)
                                : I18n.text(this, R.string.connect_76),
                        !linked,
                        () -> {
                            if (!linked) {
                                createFolder = true;
                                authorize();
                                return;
                            }
                            closeProviderPopup();
                            settingsPopup =
                                    ui.confirmationPopup(
                                            this,
                                            I18n.text(this, R.string.disconnect_75),
                                            R.drawable.ic_google_drive,
                                            I18n.text(
                                                    this,
                                                    R.string
                                                            .disconnect_google_drive_on_this_device_your_history_will_be_kept_77),
                                            I18n.text(this, R.string.disconnect_75),
                                            () -> {
                                                DriveSync.disconnect(this);
                                                refreshSyncUi();
                                            });
                        });
        if (linked) ui.redOutline(connect);
        connect.setEnabled(!syncBusy);
        connect.setAlpha(syncBusy ? .5f : 1f);
        if (linked) {
            actions.addView(refresh, new LinearLayout.LayoutParams(0, -2, 1));
            Button now =
                    ui.button(
                            refresh,
                            syncBusy ? "" : I18n.text(this, R.string.sync_9),
                            true,
                            () -> {
                                createFolder = false;
                                authorize();
                            });
            now.setEnabled(!syncBusy);
            if (syncBusy) {
                now.setContentDescription(I18n.text(this, R.string.syncing_79));
                refresh.removeView(now);
                FrameLayout loading = new FrameLayout(this);
                LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
                margin.topMargin = ui.dp(8);
                refresh.addView(loading, margin);
                loading.addView(now, new FrameLayout.LayoutParams(-1, ui.dp(50)));
                ProgressBar spinner = new ProgressBar(this);
                spinner.setIndeterminateTintList(
                        android.content.res.ColorStateList.valueOf(ui.ON_ACCENT));
                FrameLayout.LayoutParams centered =
                        new FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER);
                loading.addView(spinner, centered);
            }
        }
        entry.addView(actions);
    }

    void syncStatusRow(LinearLayout parent) {
        boolean linked = store.prefs().getBoolean("linked", false);
        long last = store.prefs().getLong("lastSync", 0);
        String raw = store.prefs().getString("syncStatus", "");
        boolean success = linked && !syncBusy && last > 0 && raw.equals("Đã đồng bộ");
        String status;
        int color;
        if (syncBusy) {
            status = I18n.text(this, linked ? R.string.syncing_81 : R.string.drive_connecting);
            color = ui.ACCENT;
        } else if (raw.equals("Liên kết đã hủy")) {
            status = I18n.text(this, R.string.google_drive_permission_not_granted_128);
            color = ui.MUTED;
        } else if (!linked && raw.equals("Liên kết chưa thành công")) {
            status = I18n.text(this, R.string.drive_connection_failed);
            color = ui.RED;
        } else if (!linked) {
            status = I18n.text(this, R.string.not_connected_78);
            color = ui.MUTED;
        } else if (success) {
            status = I18n.text(this, R.string.synced_80);
            color = ui.GREEN;
        } else if (raw.isEmpty()
                || raw.equals("Chưa đồng bộ")
                || raw.equals("Đã ngắt liên kết")
                || raw.equals("Chưa liên kết")) {
            status = I18n.text(this, R.string.not_synced_82);
            color = android.graphics.Color.rgb(170, 110, 27);
        } else {
            status = I18n.text(this, R.string.sync_failed_84);
            color = ui.RED;
        }
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        TextView label = ui.label(status, 12, color, true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        if (success) {
            TextView time =
                    ui.label(
                            I18n.text(this, R.string.at_85)
                                    + new SimpleDateFormat("HH:mm · dd/MM/yyyy", I18n.locale(this))
                                            .format(new Date(last)),
                            11,
                            ui.MUTED,
                            false);
            row.addView(time);
        }
        parent.addView(row);
    }

    void providerInfo(SyncProvider provider) {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(20));
        LinearLayout heading = ui.row();
        ImageView icon = new ImageView(this);
        icon.setImageResource(provider.icon);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
        p.rightMargin = ui.dp(14);
        heading.addView(icon, p);
        heading.addView(ui.label(provider.title, 22, ui.INK, true));
        content.addView(heading);
        ui.gap(content, 24);
        AlertDialog popup = new AlertDialog.Builder(this).setView(content).create();
        infoPopup = popup;
        LinearLayout folder = ui.card(content, ui.SOFT);
        ui.text(folder, I18n.text(this, R.string.storage_folder_86), 11, ui.ACCENT, true);
        LinearLayout folderRow = ui.row();
        TextView folderName =
                ui.label(
                        store.prefs()
                                .getString(
                                        "folderName",
                                        I18n.text(this, R.string.no_folder_selected_87)),
                        22,
                        ui.INK,
                        true);
        folderRow.addView(folderName, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout action = ui.column();
        LinearLayout.LayoutParams width = new LinearLayout.LayoutParams(ui.dp(64), -2);
        width.leftMargin = ui.dp(10);
        folderRow.addView(action, width);
        infoFolderName = folderName;
        Button change =
                ui.button(
                        action,
                        I18n.text(this, R.string.change_88),
                        true,
                        () -> {
                            selectingDriveFolder = true;
                            authorize();
                        });
        infoChange = change;
        folder.addView(folderRow);
        String folderId = store.prefs().getString("folder", "");
        ui.gap(folder, 8);
        infoFolderId = ui.text(folder, folderId, 12, ui.MUTED, false);
        infoFolderId.setTextIsSelectable(true);
        infoStatus = ui.column();
        content.addView(infoStatus);
        ui.gap(content, 12);
        infoClose = ui.button(content, I18n.text(this, R.string.close_11), false, popup::dismiss);
        popup.setCanceledOnTouchOutside(false);
        popup.show();
        refreshInfo();
        if (popup.getWindow() != null)
            popup.getWindow().setBackgroundDrawable(ui.shape(ui.SURFACE, 26, false));
    }

    void refreshInfo() {
        if (infoPopup == null || !infoPopup.isShowing()) return;
        boolean locked = folderLoading || folderPickerOpen || syncBusy;
        infoChange.setEnabled(!locked);
        infoClose.setEnabled(!locked);
        infoChange.setAlpha(locked ? .45f : 1f);
        infoClose.setAlpha(locked ? .45f : 1f);
        infoPopup.setCancelable(!locked);
        infoPopup.setCanceledOnTouchOutside(false);
        infoFolderName.setText(
                store.prefs()
                        .getString("folderName", I18n.text(this, R.string.no_folder_selected_87)));
        String id = store.prefs().getString("folder", "");
        infoFolderId.setText(id);
        infoFolderId.setVisibility(id.isEmpty() ? View.GONE : View.VISIBLE);
        infoStatus.removeAllViews();
        if (folderLoading)
            ui.text(infoStatus, I18n.text(this, R.string.loading_folders_89), 12, ui.ACCENT, true);
        else syncStatusRow(infoStatus);
    }

    void chooseDriveFolder(String token, List<JSONObject> folders, SyncSession.Ticket session) {
        if (isFinishing() || isDestroyed()) return;
        folderLoading = false;
        folderPickerOpen = true;
        refreshSyncUi();
        new DriveFolderPicker(
                        this,
                        folders,
                        target -> {
                            try {
                                SyncSession.Ticket selectedSession =
                                        session.commit(
                                                () -> {
                                                    DriveSync.selectFolder(this, target, true);
                                                    return DriveSync.session();
                                                });
                                syncBusy = true;
                                refreshSyncUi();
                                io.execute(
                                        () -> {
                                            try {
                                                DriveSync.sync(this, token, selectedSession);
                                                runOnUiThread(
                                                        () -> {
                                                            syncBusy = false;
                                                            refreshSyncUi();
                                                        });
                                            } catch (Exception error) {
                                                syncError(error, selectedSession);
                                            }
                                        });
                            } catch (Exception error) {
                                syncError(error, session);
                            }
                        })
                .onDismiss(
                        () -> {
                            folderPickerOpen = false;
                            refreshSyncUi();
                        })
                .show();
    }

    void syncError(Exception e, SyncSession.Ticket session) {
        if (e instanceof SyncSession.Cancelled) {
            runOnUiThread(
                    () -> {
                        syncBusy = false;
                        folderLoading = false;
                        refreshSyncUi();
                    });
            return;
        }
        try {
            session.commit(
                    () -> {
                        store.prefs().edit().putString("syncStatus", e.getMessage()).apply();
                        return null;
                    });
        } catch (Exception cancelled) {
            runOnUiThread(
                    () -> {
                        syncBusy = false;
                        folderLoading = false;
                        refreshSyncUi();
                    });
            return;
        }
        runOnUiThread(
                () -> {
                    if (isFinishing() || isDestroyed()) return;
                    syncBusy = false;
                    folderLoading = false;
                    folderPickerOpen = false;
                    refreshSyncUi();
                    if (e instanceof DriveSync.FolderUnavailable) {
                        new AlertDialog.Builder(this)
                                .setTitle(I18n.text(this, R.string.unable_to_sync_90))
                                .setMessage(
                                        I18n.error(this, e.getMessage())
                                                + I18n.text(
                                                        this,
                                                        R.string
                                                                .your_history_on_this_device_is_unchanged_91))
                                .setPositiveButton(
                                        I18n.text(this, R.string.choose_folder_92),
                                        (d, w) -> {
                                            selectingDriveFolder = true;
                                            authorize();
                                        })
                                .setNegativeButton(I18n.text(this, R.string.close_11), null)
                                .show();
                    } else
                        message(
                                I18n.text(this, R.string.sync_unsuccessful_93)
                                        + I18n.error(this, e.getMessage()));
                });
    }

    LinearLayout form() {
        LinearLayout l = ui.column();
        l.setPadding(ui.dp(24), ui.dp(12), ui.dp(24), ui.dp(12));
        return l;
    }

    EditText field(LinearLayout l, String label, String value, boolean number) {
        if (label.endsWith(" (ml)"))
            return unitField(l, label.substring(0, label.length() - 5), value, "ml");
        ui.text(l, label, 13, ui.MUTED, true);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(ui.INK);
        e.setTextSize(24);
        e.setSingleLine(true);
        e.setInputType(
                number
                        ? android.text.InputType.TYPE_CLASS_NUMBER
                        : android.text.InputType.TYPE_CLASS_TEXT);
        e.setSelectAllOnFocus(true);
        e.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        e.setBackground(ui.shape(ui.SOFT, 16, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = ui.dp(20);
        l.addView(e, p);
        return e;
    }

    EditText unitField(LinearLayout l, String label, String value, String unit) {
        if (label != null) ui.text(l, label, 13, ui.MUTED, true);
        LinearLayout box = ui.row();
        box.setBackground(ui.shape(ui.SOFT, 16, false));
        box.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextColor(ui.INK);
        input.setTextSize(24);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, ui.dp(14), ui.dp(8), ui.dp(14));
        input.setContentDescription(label == null ? unit : label);
        box.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(ui.label(unit, 15, ui.MUTED, false));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = ui.dp(20);
        l.addView(box, p);
        return input;
    }

    void timeFields(LinearLayout l, Calendar selected) {
        ui.text(l, I18n.text(this, R.string.time_baby_finished_feeding_94), 13, ui.MUTED, true);
        LinearLayout row = ui.row(), dayBox = ui.column(), timeBox = ui.column();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.rightMargin = ui.dp(10);
        row.addView(dayBox, p);
        row.addView(timeBox, new LinearLayout.LayoutParams(0, -2, 1));
        l.addView(row);
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM/yyyy", I18n.locale(this)),
                hourFormat = new SimpleDateFormat("HH:mm", I18n.locale(this));
        Button day = ui.button(dayBox, dayFormat.format(selected.getTime()), false, () -> {}),
                time = ui.button(timeBox, hourFormat.format(selected.getTime()), false, () -> {});
        day.setOnClickListener(
                v -> {
                    DatePickerDialog picker =
                            new FeedPickers.DateDialog(
                                    this,
                                    (dp, y, m, d) -> {
                                        selected.set(y, m, d);
                                        day.setText(dayFormat.format(selected.getTime()));
                                    },
                                    selected.get(Calendar.YEAR),
                                    selected.get(Calendar.MONTH),
                                    selected.get(Calendar.DAY_OF_MONTH));
                    picker.getDatePicker().setMaxDate(System.currentTimeMillis());
                    picker.show();
                });
        time.setOnClickListener(
                v ->
                        new FeedPickers.TimeDialog(
                                        this,
                                        (tp, h, m) -> {
                                            selected.set(Calendar.HOUR_OF_DAY, h);
                                            selected.set(Calendar.MINUTE, m);
                                            selected.set(Calendar.SECOND, 0);
                                            selected.set(Calendar.MILLISECOND, 0);
                                            time.setText(hourFormat.format(selected.getTime()));
                                        },
                                        selected.get(Calendar.HOUR_OF_DAY),
                                        selected.get(Calendar.MINUTE),
                                        true)
                                .show());
        ui.gap(l, 20);
    }

    void dialog(String title, int icon, LinearLayout form, Runnable save) {
        dismissFeedPopup();
        showFeedPopup(ui.formPopup(this, title, icon, form, save));
    }

    int ml(EditText e) {
        int value = Integer.parseInt(e.getText().toString());
        FeedRules.consumed(value, 0);
        return value;
    }

    void schedule() {
        JSONObject current = store.latest();
        if (current != null && current.optString("kind").equals("prepared")) {
            message(
                    I18n.text(
                            this,
                            R.string.the_schedule_has_changed_please_use_the_current_schedule_0));
            return;
        }
        String expectedId = current == null ? "" : current.optString("id");
        LinearLayout l = form();
        EditText amount =
                field(
                        l,
                        I18n.text(this, R.string.amount_to_prepare_ml_97),
                        store.prefs().getString("ml", "120"),
                        true);
        Calendar selected = Calendar.getInstance();
        selected.add(Calendar.MINUTE, 5);
        ui.text(l, I18n.text(this, R.string.reminder_date_and_time_98), 13, ui.MUTED, true);
        LinearLayout row = ui.row();
        row.setBaselineAligned(false);
        l.addView(row);
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", I18n.locale(this)),
                timeFormat = new SimpleDateFormat("HH:mm", I18n.locale(this));
        Button day =
                ui.button(
                        row,
                        getString(R.string.date_label, dateFormat.format(selected.getTime())),
                        false,
                        () -> {});
        Button time =
                ui.button(
                        row,
                        getString(R.string.time_label, timeFormat.format(selected.getTime())),
                        false,
                        () -> {});
        // Match the taller button when the date wraps on narrow screens or larger text.
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0, -1, 1);
        dateParams.topMargin = ui.dp(8);
        dateParams.rightMargin = ui.dp(10);
        day.setLayoutParams(dateParams);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, -1, 1);
        timeParams.topMargin = ui.dp(8);
        time.setLayoutParams(timeParams);
        day.setOnClickListener(
                v ->
                        new FeedPickers.DateDialog(
                                        this,
                                        (dp, y, m, d) -> {
                                            selected.set(y, m, d);
                                            day.setText(
                                                    getString(
                                                            R.string.date_label,
                                                            dateFormat.format(selected.getTime())));
                                        },
                                        selected.get(Calendar.YEAR),
                                        selected.get(Calendar.MONTH),
                                        selected.get(Calendar.DAY_OF_MONTH))
                                .show());
        time.setOnClickListener(
                v ->
                        new FeedPickers.TimeDialog(
                                        this,
                                        (tp, h, min) -> {
                                            selected.set(Calendar.HOUR_OF_DAY, h);
                                            selected.set(Calendar.MINUTE, min);
                                            selected.set(Calendar.SECOND, 0);
                                            selected.set(Calendar.MILLISECOND, 0);
                                            time.setText(
                                                    getString(
                                                            R.string.time_label,
                                                            timeFormat.format(selected.getTime())));
                                        },
                                        selected.get(Calendar.HOUR_OF_DAY),
                                        selected.get(Calendar.MINUTE),
                                        true)
                                .show());
        dialog(
                I18n.text(this, R.string.schedule_a_feed_3),
                R.drawable.ic_calendar,
                l,
                () -> {
                    int a = ml(amount);
                    if (selected.getTimeInMillis() <= System.currentTimeMillis())
                        throw new IllegalArgumentException(
                                I18n.text(this, R.string.choose_a_future_time_101));
                    try {
                        if (!saveIfCurrent(
                                store.event("scheduled")
                                        .put("ml", a)
                                        .put("nextAt", selected.getTimeInMillis()),
                                expectedId)) return;
                        store.prefs().edit().putString("ml", "" + a).apply();
                    } catch (org.json.JSONException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    void recordPast() {
        recordPopup(reportDay, false);
    }

    void recordPopup(java.time.LocalDate initialDay, boolean vitaminSelected) {
        LinearLayout l = form();
        Calendar when = Calendar.getInstance();
        when.set(initialDay.getYear(), initialDay.getMonthValue() - 1, initialDay.getDayOfMonth());
        when.set(Calendar.SECOND, 0);
        when.set(Calendar.MILLISECOND, 0);
        RadioGroup tabs = new RadioGroup(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        tabs.setBackground(ui.shape(ui.SOFT, 18, false));
        l.addView(tabs, new LinearLayout.LayoutParams(-1, -2));
        ui.gap(l, 16);
        RadioButton milkTab = new RadioButton(this), vitaminTab = new RadioButton(this);
        for (RadioButton option : new RadioButton[] {milkTab, vitaminTab}) {
            option.setId(View.generateViewId());
            option.setButtonDrawable((android.graphics.drawable.Drawable) null);
            option.setGravity(Gravity.CENTER);
            option.setMinHeight(ui.dp(48));
            option.setTextSize(15);
            option.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
            tabs.addView(option, new RadioGroup.LayoutParams(0, -2, 1));
        }
        milkTab.setText(I18n.text(this, R.string.record_tab_milk));
        vitaminTab.setText(I18n.text(this, R.string.record_tab_vitamin));
        ui.text(l, I18n.text(this, R.string.when_baby_was_fed_102), 13, ui.MUTED, true);
        Button day =
                ui.button(
                        l,
                        getString(
                                R.string.date_label,
                                new SimpleDateFormat("dd/MM/yyyy", I18n.locale(this))
                                        .format(when.getTime())),
                        false,
                        () -> {});
        Button time =
                ui.button(
                        l,
                        getString(
                                R.string.time_label,
                                new SimpleDateFormat("HH:mm", I18n.locale(this))
                                        .format(when.getTime())),
                        false,
                        () -> {});
        time.setOnClickListener(
                v ->
                        new FeedPickers.TimeDialog(
                                        this,
                                        (p, h, m) -> {
                                            when.set(Calendar.HOUR_OF_DAY, h);
                                            when.set(Calendar.MINUTE, m);
                                            time.setText(
                                                    getString(
                                                            R.string.time_label,
                                                            new SimpleDateFormat(
                                                                            "HH:mm",
                                                                            I18n.locale(this))
                                                                    .format(when.getTime())));
                                        },
                                        when.get(Calendar.HOUR_OF_DAY),
                                        when.get(Calendar.MINUTE),
                                        true)
                                .show());
        ui.gap(l, 16);
        LinearLayout milkFields = ui.column();
        l.addView(milkFields);
        EditText
                amount =
                        field(
                                milkFields,
                                I18n.text(this, R.string.record_amount_consumed),
                                store.prefs().getString("ml", "120"),
                                true),
                left =
                        field(
                                milkFields,
                                I18n.text(this, R.string.amount_discarded_ml_106),
                                "0",
                                true);
        TextView warning = ui.text(l, "", 14, ui.RED, true);
        warning.setGravity(Gravity.CENTER);
        Runnable refresh =
                () -> {
                    boolean vitamin = vitaminTab.isChecked();
                    milkFields.setVisibility(vitamin ? View.GONE : View.VISIBLE);
                    for (RadioButton option : new RadioButton[] {milkTab, vitaminTab}) {
                        option.setTextColor(option.isChecked() ? ui.ON_ACCENT : ui.ACCENT);
                        option.setBackground(
                                ui.shape(option.isChecked() ? ui.ACCENT : ui.SOFT, 14, false));
                    }
                    java.time.LocalDate selected =
                            when.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    JSONObject existing = store.vitamin(selected);
                    boolean duplicate = vitamin && existing != null && existing.optBoolean("taken");
                    warning.setText(
                            duplicate ? I18n.text(this, R.string.vitamin_already_taken) : "");
                    warning.setVisibility(duplicate ? View.VISIBLE : View.GONE);
                    View saveButton = l.getRootView().findViewWithTag("form-save");
                    if (saveButton != null) {
                        saveButton.setEnabled(!duplicate);
                        saveButton.setAlpha(duplicate ? .4f : 1f);
                    }
                    View error = l.getRootView().findViewWithTag("form-error");
                    if (error != null) error.setVisibility(View.GONE);
                };
        tabs.setOnCheckedChangeListener((group, id) -> refresh.run());
        tabs.check((vitaminSelected ? vitaminTab : milkTab).getId());
        day.setOnClickListener(
                v -> {
                    DatePickerDialog picker =
                            new FeedPickers.DateDialog(
                                    this,
                                    (p, y, m, d) -> {
                                        when.set(y, m, d);
                                        day.setText(
                                                getString(
                                                        R.string.date_label,
                                                        new SimpleDateFormat(
                                                                        "dd/MM/yyyy",
                                                                        I18n.locale(this))
                                                                .format(when.getTime())));
                                        refresh.run();
                                    },
                                    when.get(Calendar.YEAR),
                                    when.get(Calendar.MONTH),
                                    when.get(Calendar.DAY_OF_MONTH));
                    picker.getDatePicker().setMaxDate(System.currentTimeMillis());
                    picker.show();
                });
        dialog(
                I18n.text(this, R.string.record_38),
                R.drawable.ic_pencil,
                l,
                () -> {
                    long at = when.getTimeInMillis();
                    boolean vitamin = vitaminTab.isChecked();
                    if (at > System.currentTimeMillis())
                        throw new IllegalArgumentException(
                                I18n.text(
                                        this,
                                        vitamin
                                                ? R.string.vitamin_future_time
                                                : R.string.choose_a_feeding_time_in_the_past_108));
                    java.time.LocalDate selected =
                            when.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    if (vitamin) {
                        JSONObject existing = store.vitamin(selected);
                        if (existing != null && existing.optBoolean("taken")) {
                            refresh.run();
                            throw new IllegalArgumentException(
                                    I18n.text(this, R.string.vitamin_already_taken));
                        }
                        reportDay = selected;
                        saveVitamin(selected, true, at);
                        return;
                    }
                    int consumed = Integer.parseInt(amount.getText().toString()),
                            remaining = Integer.parseInt(left.getText().toString()),
                            prepared = FeedRules.preparedFromConsumed(consumed, remaining);
                    try {
                        JSONObject e = store.event("recorded");
                        e.put("feed", e.getString("id"))
                                .put("preparedAt", at)
                                .put("finishedAt", at)
                                .put("ml", prepared)
                                .put("preparedMl", prepared)
                                .put("left", remaining)
                                .put("consumed", consumed);
                        reportDay =
                                when.toInstant()
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate();
                        save(e);
                    } catch (org.json.JSONException error) {
                        throw new IllegalStateException(error);
                    }
                });
        refresh.run();
    }

    void prepare(JSONObject scheduled) {
        LinearLayout l = form();
        EditText amount =
                field(
                        l,
                        I18n.text(this, R.string.actual_amount_prepared_ml_109),
                        "" + scheduled.optInt("ml"),
                        true);
        dialog(
                I18n.text(this, R.string.confirm_milk_prepared_110),
                R.drawable.ic_widget_bottle,
                l,
                () -> {
                    try {
                        if (!saveIfCurrent(
                                store.event("prepared")
                                        .put("ml", ml(amount))
                                        .put("preparedAt", System.currentTimeMillis()),
                                scheduled.optString("id"))) return;
                        getSystemService(NotificationManager.class).cancel(1);
                    } catch (org.json.JSONException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    void finishFeed(JSONObject feed) {
        LinearLayout l = form();
        Calendar finished = Calendar.getInstance();
        timeFields(l, finished);
        EditText left = field(l, I18n.text(this, R.string.amount_left_ml_111), "0", true);
        int savedMinutes = Integer.parseInt(store.prefs().getString("interval", "180"));
        ui.text(l, I18n.text(this, R.string.next_feed_after_112), 13, ui.MUTED, true);
        LinearLayout intervalRow = ui.row(), hourBox = ui.column(), minuteBox = ui.column();
        LinearLayout.LayoutParams hourParams = new LinearLayout.LayoutParams(0, -2, 1);
        hourParams.rightMargin = ui.dp(10);
        intervalRow.addView(hourBox, hourParams);
        intervalRow.addView(minuteBox, new LinearLayout.LayoutParams(0, -2, 1));
        l.addView(intervalRow);
        EditText
                hours =
                        unitField(
                                hourBox,
                                null,
                                "" + (savedMinutes / 60),
                                I18n.text(this, R.string.hours_113)),
                minute =
                        unitField(
                                minuteBox,
                                null,
                                "" + (savedMinutes % 60),
                                I18n.text(this, R.string.minutes_114));
        android.text.TextWatcher rememberInterval =
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    public void afterTextChanged(android.text.Editable text) {
                        try {
                            int interval =
                                    FeedRules.interval(
                                            Integer.parseInt(hours.getText().toString()),
                                            Integer.parseInt(minute.getText().toString()));
                            store.prefs().edit().putString("interval", "" + interval).apply();
                        } catch (IllegalArgumentException | ArithmeticException ignored) {
                        }
                    }
                };
        hours.addTextChangedListener(rememberInterval);
        minute.addTextChangedListener(rememberInterval);
        EditText nextMl =
                field(
                        l,
                        I18n.text(this, R.string.amount_for_next_feed_ml_115),
                        "" + feed.optInt("ml"),
                        true);
        dialog(
                I18n.text(this, R.string.record_finished_feed_116),
                R.drawable.ic_widget_bottle,
                l,
                () -> {
                    if (store.completed(feed.optString("id")))
                        throw new IllegalArgumentException(
                                I18n.text(this, R.string.this_feed_has_already_been_recorded_117));
                    int remaining = Integer.parseInt(left.getText().toString()),
                            consumed = FeedRules.consumed(feed.optInt("ml"), remaining),
                            minutes =
                                    FeedRules.interval(
                                            Integer.parseInt(hours.getText().toString()),
                                            Integer.parseInt(minute.getText().toString())),
                            amount = ml(nextMl);
                    long now = finished.getTimeInMillis();
                    if (now > System.currentTimeMillis())
                        throw new IllegalArgumentException(
                                I18n.text(
                                        this,
                                        R.string.the_finish_time_cannot_be_in_the_future_118));
                    if (now < feed.optLong("preparedAt"))
                        throw new IllegalArgumentException(
                                I18n.text(
                                        this,
                                        R.string
                                                .the_finish_time_must_be_at_or_after_preparation_119));
                    long next = FeedRules.next(now, minutes);
                    try {
                        if (!saveIfCurrent(
                                store.event("finished")
                                        .put("feed", feed.optString("id"))
                                        .put("preparedAt", feed.optLong("preparedAt"))
                                        .put("finishedAt", now)
                                        .put("preparedMl", feed.optInt("ml"))
                                        .put("consumed", consumed)
                                        .put("left", remaining)
                                        .put("ml", amount)
                                        .put("nextAt", next),
                                feed.optString("id"))) return;
                        store.prefs()
                                .edit()
                                .putString("interval", "" + minutes)
                                .putString("ml", "" + amount)
                                .apply();
                        getSystemService(NotificationManager.class).cancel(2);
                    } catch (org.json.JSONException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }

    boolean saveIfCurrent(JSONObject event, String expectedId) {
        if (!store.insertIfCurrent(event, expectedId)) {
            message(
                    I18n.text(
                            this,
                            R.string.the_schedule_has_changed_please_use_the_current_schedule_0));
            render();
            return false;
        }
        afterSave(event);
        return true;
    }

    void save(JSONObject e) {
        store.insert(e, false);
        afterSave(e);
    }

    void afterSave(JSONObject e) {
        if (!VitaminRecords.isVitamin(e)) {
            if (!Arrays.asList("recorded", "deleted").contains(e.optString("kind")))
                Reminders.silence(this);
            Reminders.restore(this);
        } else VitaminReminders.restore(this);
        SyncWorker.now(this);
        render();
    }

    void folderDialog() {
        if (!store.prefs().getString("folder", "").isEmpty()) {
            message(
                    I18n.text(
                            this,
                            R.string
                                    .disconnect_before_changing_families_local_data_will_be_uploaded_t_120));
            return;
        }
        LinearLayout l = form();
        EditText id =
                field(
                        l,
                        I18n.text(this, R.string.id_at_the_end_of_the_drive_folder_url_121),
                        "",
                        false);
        dialog(
                I18n.text(this, R.string.join_family_folder_122),
                R.drawable.ic_google_drive,
                l,
                () -> {
                    String f = id.getText().toString().trim();
                    if (!f.matches("[A-Za-z0-9_-]+"))
                        throw new IllegalArgumentException(
                                I18n.text(this, R.string.invalid_folder_id_123));
                    store.prefs().edit().putString("folder", f).apply();
                    createFolder = false;
                    authorize();
                });
    }

    void authorize() {
        if (syncBusy || folderLoading) return;
        authorizationSession = DriveSync.session();
        if (selectingDriveFolder) {
            folderLoading = true;
            refreshSyncUi();
        } else {
            syncBusy = true;
            refreshSyncUi();
        }
        Identity.getAuthorizationClient(this)
                .authorize(DriveSync.request())
                .addOnSuccessListener(
                        result -> {
                            if (isFinishing() || isDestroyed()) return;
                            try {
                                authorizationSession.check();
                            } catch (SyncSession.Cancelled cancelled) {
                                return;
                            }
                            if (result.hasResolution()) {
                                try {
                                    startIntentSenderForResult(
                                            result.getPendingIntent().getIntentSender(),
                                            42,
                                            null,
                                            0,
                                            0,
                                            0);
                                } catch (Exception e) {
                                    authorizationFailed(e);
                                }
                            } else authorized(result);
                        })
                .addOnFailureListener(this::authorizationFailed);
    }

    void authorizationFailed(Exception e) {
        if (isFinishing() || isDestroyed()) return;
        try {
            authorizationSession.check();
        } catch (SyncSession.Cancelled cancelled) {
            return;
        }
        syncBusy = false;
        folderLoading = false;
        selectingDriveFolder = false;
        String detail = e.getMessage() == null ? "" : e.getMessage();
        int code =
                e instanceof com.google.android.gms.common.api.ApiException
                        ? ((com.google.android.gms.common.api.ApiException) e).getStatusCode()
                        : -1;
        String explanation =
                code == 10
                        ? I18n.text(
                                this,
                                R.string
                                        .oauth_is_not_configured_correctly_create_an_android_oauth_client__124)
                        : I18n.text(
                                this,
                                R.string
                                        .google_has_not_granted_drive_access_check_the_android_oauth_clien_125);
        store.prefs().edit().putString("syncStatus", "Liên kết chưa thành công").apply();
        createFolder = false;
        refreshSyncUi();
        message(
                explanation
                        + (detail.isEmpty() ? "" : I18n.text(this, R.string.details_127) + detail));
    }

    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 42) {
            if (result == RESULT_OK)
                try {
                    authorized(
                            Identity.getAuthorizationClient(this)
                                    .getAuthorizationResultFromIntent(data));
                } catch (Exception e) {
                    authorizationFailed(e);
                }
            else {
                createFolder = false;
                selectingDriveFolder = false;
                folderLoading = false;
                syncBusy = false;
                store.prefs().edit().putString("syncStatus", "Liên kết đã hủy").apply();
                refreshSyncUi();
                Toast.makeText(
                                this,
                                I18n.text(this, R.string.google_drive_permission_not_granted_128),
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    void authorized(AuthorizationResult result) {
        if (isFinishing() || isDestroyed()) return;
        SyncSession.Ticket session = authorizationSession;
        try {
            session.check();
        } catch (SyncSession.Cancelled cancelled) {
            return;
        }
        if (result.getAccessToken() == null) {
            syncBusy = false;
            folderLoading = false;
            store.prefs().edit().putString("syncStatus", "Liên kết chưa thành công").apply();
            refreshSyncUi();
            message(I18n.text(this, R.string.google_drive_access_has_not_been_granted_129));
            return;
        }
        boolean chooseFolder = selectingDriveFolder;
        selectingDriveFolder = false;
        createFolder = false;
        syncBusy = !chooseFolder;
        folderLoading = chooseFolder;
        refreshSyncUi();
        io.execute(
                () -> {
                    try {
                        DriveSync.withSession(
                                session,
                                () -> {
                                    if (chooseFolder) {
                                        List<JSONObject> folders =
                                                DriveSync.folders(result.getAccessToken());
                                        runOnUiThread(
                                                () -> {
                                                    if (isFinishing() || isDestroyed()) return;
                                                    try {
                                                        session.check();
                                                    } catch (SyncSession.Cancelled cancelled) {
                                                        return;
                                                    }
                                                    syncBusy = false;
                                                    refreshSyncUi();
                                                    chooseDriveFolder(
                                                            result.getAccessToken(),
                                                            folders,
                                                            session);
                                                });
                                        return null;
                                    }
                                    JSONObject folder =
                                            DriveSync.folder(
                                                    result.getAccessToken(),
                                                    store.prefs().getString("folder", ""),
                                                    store.prefs()
                                                            .getBoolean("customFolder", false));
                                    session.commit(
                                            () -> {
                                                store.prefs()
                                                        .edit()
                                                        .putString("folder", folder.getString("id"))
                                                        .putString(
                                                                "folderName",
                                                                folder.getString("name"))
                                                        .putBoolean("linked", true)
                                                        .apply();
                                                SyncWorker.schedule(this);
                                                return null;
                                            });
                                    DriveSync.sync(this, result.getAccessToken(), session);
                                    runOnUiThread(
                                            () -> {
                                                syncBusy = false;
                                                refreshSyncUi();
                                            });
                                    return null;
                                });
                    } catch (Exception error) {
                        syncError(error, session);
                    }
                });
    }
}
