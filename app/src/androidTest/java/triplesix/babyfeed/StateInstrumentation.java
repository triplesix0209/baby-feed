package triplesix.babyfeed;

import android.app.Instrumentation;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.*;
import android.os.Bundle;

import org.json.*;

import java.util.UUID;

/** Tests isolated databases, never the family's real history or reminders. */
public class StateInstrumentation extends Instrumentation {
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    public void onStart() {
        Bundle result = new Bundle();
        String prefix = "test-" + UUID.randomUUID() + "-";
        Context context =
                new ContextWrapper(getTargetContext()) {
                    public Context getApplicationContext() {
                        return this;
                    }

                    public java.io.File getDatabasePath(String name) {
                        return super.getDatabasePath(prefix + name);
                    }

                    public SQLiteDatabase openOrCreateDatabase(
                            String n, int m, SQLiteDatabase.CursorFactory f) {
                        return super.openOrCreateDatabase(prefix + n, m, f);
                    }

                    public SQLiteDatabase openOrCreateDatabase(
                            String n,
                            int m,
                            SQLiteDatabase.CursorFactory f,
                            DatabaseErrorHandler h) {
                        return super.openOrCreateDatabase(prefix + n, m, f, h);
                    }

                    public SharedPreferences getSharedPreferences(String n, int m) {
                        return super.getSharedPreferences(prefix + n, m);
                    }
                };
        try (Store s = new Store(context)) {
            JSONObject scheduled =
                    s.event("scheduled").put("created", 1000).put("ml", 120).put("nextAt", 2000);
            s.insert(scheduled, false);
            JSONObject recorded =
                    s.event("recorded")
                            .put("created", 3000)
                            .put("feed", "past-feed")
                            .put("ml", 120)
                            .put("preparedMl", 120)
                            .put("left", 20)
                            .put("consumed", 100)
                            .put("preparedAt", 500)
                            .put("finishedAt", 600);
            s.insert(recorded, false);
            check(
                    s.latest().optString("id").equals(scheduled.optString("id")),
                    "Backfill must not replace active schedule");
            JSONObject deleted =
                    s.event("deleted")
                            .put("created", 4000)
                            .put("feeds", new JSONArray().put("past-feed"));
            s.insert(deleted, false);
            check(s.deletedFeeds().contains("past-feed"), "Deletion must hide selected feed");
            check(
                    s.latest().optString("id").equals(scheduled.optString("id")),
                    "Deletion must not change active schedule");
            s.insert(deleted, true);
            check(s.all(false).size() == 3, "Sync re-import must be idempotent");
            java.time.LocalDate vitaminDay = java.time.LocalDate.of(2026, 10, 15);
            JSONObject vitamin =
                    s.event(VitaminRecords.KIND)
                            .put("created", 4100)
                            .put("day", vitaminDay.toString())
                            .put("taken", true)
                            .put("takenAt", 4000);
            s.insert(vitamin, false);
            check(s.vitamin(vitaminDay).optBoolean("taken"), "Vitamin status must persist by day");
            check(
                    s.vitamin(vitaminDay.plusDays(1)) == null,
                    "Vitamin status must reset for a new day");
            check(
                    s.latest().optString("id").equals(scheduled.optString("id")),
                    "Vitamin status must not replace active schedule");
            JSONObject undo =
                    s.event(VitaminRecords.KIND)
                            .put("created", 4200)
                            .put("day", vitaminDay.toString())
                            .put("taken", false)
                            .put("takenAt", 0);
            s.insert(undo, false);
            check(!s.vitamin(vitaminDay).optBoolean("taken"), "Undo must persist");
            check(s.feeds(vitaminDay).isEmpty(), "Vitamin must not count as a milk feed");
            check(
                    s.latest().optString("id").equals(scheduled.optString("id")),
                    "Undo must preserve active schedule");
            s.prune(
                    new java.util.HashSet<>(
                            java.util.Arrays.asList(
                                    vitamin.optString("id"), undo.optString("id"))));
            s.insert(vitamin, false);
            s.deleteRecords(
                    java.util.Collections.emptySet(),
                    java.util.Collections.singleton(vitaminDay.toString()));
            check(
                    !s.vitamin(vitaminDay).optBoolean("taken"),
                    "Vitamin-only deletion must clear daily status");
            JSONObject restored =
                    s.event(VitaminRecords.KIND)
                            .put("day", vitaminDay.toString())
                            .put("taken", true)
                            .put("takenAt", 4000);
            restored.put("created", s.vitamin(vitaminDay).optLong("created") + 1);
            s.insert(restored, false);
            JSONObject otherFeed =
                    new JSONObject(recorded.toString())
                            .put("id", UUID.randomUUID().toString())
                            .put("feed", "mixed-delete-feed");
            s.insert(otherFeed, false);
            long before = s.all(false).size();
            try {
                s.deleteRecords(
                        java.util.Collections.singleton("mixed-delete-feed"),
                        java.util.Collections.singleton("invalid-day"));
                throw new AssertionError("Invalid batch must fail");
            } catch (java.time.format.DateTimeParseException expected) {
            }
            check(
                    s.all(false).size() == before
                            && !s.deletedFeeds().contains("mixed-delete-feed"),
                    "Failed batch must roll back all deletions");
            s.deleteRecords(
                    java.util.Collections.singleton("mixed-delete-feed"),
                    java.util.Collections.singleton(vitaminDay.toString()));
            check(
                    s.deletedFeeds().contains("mixed-delete-feed")
                            && !s.vitamin(vitaminDay).optBoolean("taken"),
                    "Mixed selection must delete milk and vitamin together");
            check(
                    s.latest().optString("id").equals(scheduled.optString("id")),
                    "Mixed deletion must preserve active feeding schedule");
            JSONObject cancelled = s.event("cancelled").put("created", 5000);
            s.insert(cancelled, false);
            check(
                    s.latest().optString("kind").equals("cancelled"),
                    "Cancellation must supersede active schedule");
            check(
                    FeedRules.duration("0p") == 0 && FeedRules.next(1000, 0) == 1000,
                    "Immediate next feed must be allowed");
            check(
                    WidgetSettings.refreshMinutes(context, 101) == 5,
                    "Widget refresh defaults to five minutes");
            WidgetSettings.save(context, 101, 10, 65, "dark");
            WidgetSettings.save(context, 102, 1, 0, "light");
            check(
                    WidgetSettings.refreshMinutes(context, 101) == 10
                            && WidgetSettings.transparency(context, 101) == 70
                            && WidgetSettings.appearance(context, 101).equals("dark"),
                    "Widget settings must persist with ten-percent steps");
            check(
                    WidgetSettings.refreshMinutes(context, 102) == 1
                            && WidgetSettings.transparency(context, 102) == 0
                            && WidgetSettings.appearance(context, 102).equals("light"),
                    "Widget settings must remain independent");
            for (int percent = -10; percent <= 110; percent++)
                check(
                        WidgetSettings.transparencyStep(percent) % 10 == 0,
                        "Transparency must use ten-percent steps");
            WidgetSettings.remove(context, 101);
            check(
                    WidgetSettings.refreshMinutes(context, 101) == 5,
                    "Deleting widget removes its preferences");
            java.util.concurrent.atomic.AtomicReference<Throwable> layoutFailure =
                    new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(
                    () -> {
                        try {
                            for (String language : new String[] {"vi", "en"})
                                for (String appearance : new String[] {"light", "dark", "system"}) {
                                    context.getSharedPreferences("settings", 0)
                                            .edit()
                                            .putString("language", language)
                                            .putString("appearance", appearance)
                                            .commit();
                                    Context localized = I18n.wrap(context);
                                    FeedUi palette = new FeedUi(localized);
                                    android.app.Dialog freshWindow =
                                            new android.app.Dialog(
                                                    localized,
                                                    palette.dark
                                                            ? R.style.AppTheme_Dark
                                                            : R.style.AppTheme);
                                    check(
                                            freshWindow.getWindow().peekDecorView() == null,
                                            "Regression must style a window before content exists");
                                    PlatformUi.styleBars(
                                            freshWindow.getWindow(), palette.BG, palette.dark);
                                    check(
                                            freshWindow.getWindow().peekDecorView() != null,
                                            "System bar styling must initialize the window decor");
                                    if (!appearance.equals("system"))
                                        check(
                                                palette.dark == appearance.equals("dark"),
                                                "Explicit appearance must override system mode");
                                    else
                                        check(
                                                palette.dark == Appearance.dark(context),
                                                "System appearance must follow current system"
                                                        + " mode");
                                    check(
                                            localized
                                                    .getResources()
                                                    .getConfiguration()
                                                    .getLocales()
                                                    .get(0)
                                                    .getLanguage()
                                                    .equals(language),
                                            "Language must survive appearance changes");
                                    check(
                                            contrast(palette.INK, palette.SURFACE) >= 4.5,
                                            "Settings text must remain readable on cards");
                                    check(
                                            contrast(palette.ON_ACCENT, palette.ACCENT) >= 4.5,
                                            "Primary buttons must remain readable in both modes");
                                    android.widget.LinearLayout settings = palette.column();
                                    for (int label :
                                            new int[] {
                                                R.string.settings_display,
                                                R.string.settings_widget,
                                                R.string.sync_9,
                                                R.string.settings_tips,
                                                R.string.settings_info
                                            })
                                        palette.section(settings, localized.getString(label));
                                    java.util.concurrent.atomic.AtomicBoolean clicked =
                                            new java.util.concurrent.atomic.AtomicBoolean();
                                    android.widget.LinearLayout row =
                                            palette.settingRow(
                                                    settings,
                                                    localized.getString(
                                                            R.string.settings_appearance),
                                                    localized.getString(R.string.appearance_system),
                                                    R.drawable.ic_settings_appearance,
                                                    () -> clicked.set(true));
                                    settings.measure(
                                            android.view.View.MeasureSpec.makeMeasureSpec(
                                                    palette.dp(280),
                                                    android.view.View.MeasureSpec.EXACTLY),
                                            android.view.View.MeasureSpec.makeMeasureSpec(
                                                    palette.dp(900),
                                                    android.view.View.MeasureSpec.AT_MOST));
                                    settings.layout(
                                            0,
                                            0,
                                            settings.getMeasuredWidth(),
                                            settings.getMeasuredHeight());
                                    check(
                                            row.getHeight() >= palette.dp(48),
                                            "Settings rows must have a usable touch target");
                                    row.performClick();
                                    check(clicked.get(), "Settings row must activate its action");
                                }
                            for (int systemMode :
                                    new int[] {
                                        android.content.res.Configuration.UI_MODE_NIGHT_NO,
                                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                                    }) {
                                android.content.res.Configuration config =
                                        new android.content.res.Configuration(
                                                context.getResources().getConfiguration());
                                config.uiMode =
                                        (config.uiMode
                                                        & ~android.content.res.Configuration
                                                                .UI_MODE_NIGHT_MASK)
                                                | systemMode;
                                context.getSharedPreferences("settings", 0)
                                        .edit()
                                        .putString("appearance", "system")
                                        .commit();
                                Appearance.configure(context, config);
                                check(
                                        (config.uiMode
                                                        & android.content.res.Configuration
                                                                .UI_MODE_NIGHT_MASK)
                                                == systemMode,
                                        "System choice must preserve either system appearance");
                            }
                            FeedUi ui = new FeedUi(context);
                            android.widget.LinearLayout status = ui.row();
                            status.addView(ui.label("Đã đồng bộ", 12, ui.GREEN, true));
                            ui.fitStats(status);
                            status.measure(
                                    android.view.View.MeasureSpec.makeMeasureSpec(
                                            900, android.view.View.MeasureSpec.EXACTLY),
                                    android.view.View.MeasureSpec.makeMeasureSpec(
                                            200, android.view.View.MeasureSpec.AT_MOST));
                            status.layout(0, 0, 900, status.getMeasuredHeight());
                            status.getViewTreeObserver().dispatchOnGlobalLayout();
                            check(status.getChildCount() == 1, "Non-stat row must remain intact");
                            android.widget.RemoteViews widget =
                                    new android.widget.RemoteViews(
                                            context.getPackageName(), R.layout.widget_next_feed);
                            widget.setTextViewText(R.id.widget_countdown, "(3h 44p tới)");
                            android.view.View inflated =
                                    widget.apply(context, new android.widget.FrameLayout(context));
                            check(
                                    inflated.findViewById(R.id.widget_action) != null,
                                    "Widget action must inflate");
                            check(
                                    inflated.findViewById(R.id.widget_countdown)
                                            instanceof android.widget.TextView,
                                    "Widget countdown must inflate");
                            for (int width : new int[] {180, 250})
                                for (int height : new int[] {150, 180, 220}) {
                                    widget.setViewVisibility(
                                            R.id.widget_extra, android.view.View.VISIBLE);
                                    widget.setViewVisibility(
                                            R.id.widget_date,
                                            height < 180
                                                    ? android.view.View.GONE
                                                    : android.view.View.VISIBLE);
                                    int padding = ui.dp(height < 180 ? 6 : 8);
                                    widget.setViewPadding(
                                            R.id.widget_root, padding, padding, padding, padding);
                                    for (String mode : new String[] {"light", "dark", "system"})
                                        for (int transparency = 0;
                                                transparency <= 100;
                                                transparency += 10) {
                                            NextFeedWidget.applyAppearance(
                                                    context, widget, mode, transparency);
                                            android.view.View layout =
                                                    widget.apply(
                                                            context,
                                                            new android.widget.FrameLayout(
                                                                    context));
                                            layout.measure(
                                                    android.view.View.MeasureSpec.makeMeasureSpec(
                                                            ui.dp(width),
                                                            android.view.View.MeasureSpec.EXACTLY),
                                                    android.view.View.MeasureSpec.makeMeasureSpec(
                                                            ui.dp(height),
                                                            android.view.View.MeasureSpec.EXACTLY));
                                            layout.layout(0, 0, ui.dp(width), ui.dp(height));
                                            android.view.View
                                                    action =
                                                            layout.findViewById(R.id.widget_action),
                                                    edit = layout.findViewById(R.id.widget_edit),
                                                    cancel =
                                                            layout.findViewById(R.id.widget_cancel);
                                            check(
                                                    action.getHeight() >= ui.dp(40),
                                                    "Primary action must remain large at "
                                                            + width
                                                            + "x"
                                                            + height);
                                            android.graphics.Rect bounds =
                                                    new android.graphics.Rect();
                                            cancel.getDrawingRect(bounds);
                                            ((android.view.ViewGroup) layout)
                                                    .offsetDescendantRectToMyCoords(cancel, bounds);
                                            check(
                                                    bounds.bottom <= ui.dp(height) - padding,
                                                    "Footer must fit inside widget");
                                            check(
                                                    edit.getTop() >= 0 && edit.getHeight() > 0,
                                                    "Edit action must be visible");
                                            check(
                                                    ((android.widget.TextView) edit).getMaxLines()
                                                            == 1,
                                                    "Edit label must stay on one line");
                                            check(
                                                    ((android.widget.ImageView)
                                                                            layout.findViewById(
                                                                                    R.id
                                                                                            .widget_background))
                                                                    .getImageAlpha()
                                                            == WidgetSettings.backgroundAlpha(
                                                                    transparency),
                                                    "RemoteViews must apply background"
                                                            + " transparency");
                                            check(
                                                    ((android.widget.TextView) action)
                                                                    .getCurrentTextColor()
                                                            == WidgetSettings.palette(context, mode)
                                                                    .ON_ACCENT,
                                                    "Background transparency must preserve themed"
                                                            + " action text");
                                            android.graphics.drawable.Drawable bottle =
                                                    ((android.widget.ImageView)
                                                                    layout.findViewById(
                                                                            R.id.widget_bottle))
                                                            .getDrawable();
                                            android.graphics.Bitmap icon =
                                                    android.graphics.Bitmap.createBitmap(
                                                            48,
                                                            48,
                                                            android.graphics.Bitmap.Config
                                                                    .ARGB_8888);
                                            bottle.setBounds(0, 0, 48, 48);
                                            bottle.draw(new android.graphics.Canvas(icon));
                                            check(
                                                    android.graphics.Color.alpha(
                                                                    icon.getPixel(0, 0))
                                                            == 0,
                                                    "Bottle must have transparent corners");
                                            icon.recycle();
                                        }
                                }
                        } catch (Throwable failure) {
                            layoutFailure.set(failure);
                        }
                    });
            if (layoutFailure.get() != null) throw layoutFailure.get();
            verifyReviewRegressions(context);
            if (getTargetContext().getPackageName().endsWith(".reviewtests")) verifyDrivePopup();
            result.putString(
                    "stream",
                    "PASS: atomic stale-form rejection + validated imports + isolated state checks"
                            + " + bilingual appearance and settings rows + sync layout regression +"
                            + " expanded/compact widget RemoteViews"
                            + (getTargetContext().getPackageName().endsWith(".reviewtests")
                                    ? " + Drive popup progress/results and scroll retention"
                                    : "")
                            + "\n");
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("stream", "FAIL: " + e + "\n");
            finish(0, result);
        } finally {
            getTargetContext().deleteDatabase(prefix + "feeds.db");
            getTargetContext().deleteSharedPreferences(prefix + "settings");
            getTargetContext().deleteSharedPreferences(prefix + "widgets");
        }
    }

    static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    /** Launch only in the isolated test app; never change the family's Drive settings. */
    private void verifyDrivePopup() {
        MainActivity activity =
                (MainActivity)
                        startActivitySync(
                                new Intent(getTargetContext(), MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            runOnMainSync(
                    () -> {
                        activity.store
                                .prefs()
                                .edit()
                                .putBoolean("linked", false)
                                .putString("syncStatus", "Chưa liên kết")
                                .apply();
                        activity.tab = 2;
                        activity.render();
                    });
            waitForIdleSync();
            runOnMainSync(() -> activity.screenScroll.scrollTo(0, 10000));
            waitForIdleSync();
            int[] position = new int[1];
            android.view.View[] background = new android.view.View[2];
            android.app.AlertDialog[] popup = new android.app.AlertDialog[1];
            runOnMainSync(
                    () -> {
                        position[0] = activity.screenScroll.getScrollY();
                        background[0] = activity.screenScroll;
                        background[1] = activity.root;
                        check(position[0] > 0, "Settings regression must begin below the top");
                        // Tap the actual settings row, then the Connect button. Mark the request
                        // busy first so authorize() cannot open Google UI or access the network.
                        ((android.view.View) findText(activity.root, "Google Drive").getParent())
                                .performClick();
                        popup[0] = activity.providerPopup;
                        activity.syncBusy = true;
                        findText(
                                        activity.providerPopupContent,
                                        I18n.text(activity, R.string.connect_76))
                                .performClick();
                        check(
                                activity.providerPopup == popup[0] && popup[0].isShowing(),
                                "Connect must keep the Drive popup open");
                        activity.refreshSyncUi();
                    });
            waitForIdleSync();
            runOnMainSync(
                    () -> {
                        check(
                                activity.screenScroll.getScrollY() == position[0],
                                "Drive progress must preserve settings scroll position");
                        check(
                                activity.screenScroll == background[0]
                                        && activity.root == background[1],
                                "Drive progress must not replace the background views");
                        check(
                                !activity.root.findViewWithTag("language-setting").isEnabled(),
                                "Appearance controls must stay locked during Drive requests");
                        findText(
                                activity.providerPopupContent,
                                I18n.text(activity, R.string.drive_connecting));
                        activity.syncBusy = false;
                        activity.store
                                .prefs()
                                .edit()
                                .putString("syncStatus", "Liên kết chưa thành công")
                                .apply();
                        activity.refreshSyncUi();
                        findText(
                                activity.providerPopupContent,
                                I18n.text(activity, R.string.drive_connection_failed));
                        activity.onActivityResult(42, android.app.Activity.RESULT_CANCELED, null);
                        activity.onResume();
                        findText(
                                activity.providerPopupContent,
                                I18n.text(
                                        activity,
                                        R.string.google_drive_permission_not_granted_128));
                        activity.store
                                .prefs()
                                .edit()
                                .putBoolean("linked", true)
                                .putLong("lastSync", System.currentTimeMillis())
                                .putString("syncStatus", "Đã đồng bộ")
                                .apply();
                        activity.refreshSyncUi();
                        findText(
                                activity.providerPopupContent,
                                I18n.text(activity, R.string.synced_80));
                        findText(activity.root, I18n.text(activity, R.string.connected_provider));
                        check(
                                activity.root.findViewWithTag("language-setting").isEnabled(),
                                "Appearance controls must unlock after Drive completes");
                        check(
                                activity.providerPopup == popup[0] && popup[0].isShowing(),
                                "Drive results must remain visible in the original popup");
                    });
            waitForIdleSync();
            runOnMainSync(
                    () -> {
                        check(
                                activity.screenScroll == background[0]
                                        && activity.root == background[1],
                                "Drive results and resume must keep the same background views");
                        check(
                                activity.screenScroll.getScrollY() == position[0],
                                "Consecutive Drive result updates must preserve scroll"
                                        + " position");
                    });
        } finally {
            runOnMainSync(
                    () -> {
                        activity.store
                                .prefs()
                                .edit()
                                .putBoolean("linked", false)
                                .putString("syncStatus", "Chưa liên kết")
                                .remove("lastSync")
                                .apply();
                        activity.finish();
                    });
        }
    }

    private static android.widget.TextView findText(android.view.View view, String text) {
        android.widget.TextView found = findTextOrNull(view, text);
        check(found != null, "Missing visible UI text: " + text);
        return found;
    }

    private static android.widget.TextView findTextOrNull(android.view.View view, String text) {
        if (view instanceof android.widget.TextView
                && ((android.widget.TextView) view).getText().toString().equals(text))
            return (android.widget.TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                android.widget.TextView found = findTextOrNull(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void verifyReviewRegressions(Context context) throws Exception {
        try (Store ui = new Store(context);
                Store sync = new Store(context)) {
            JSONObject original = ui.latest();
            JSONObject scheduled = ui.event("scheduled").put("ml", 120).put("nextAt", 2000);
            check(
                    ui.insertIfCurrent(scheduled, original.getString("id")),
                    "Current form must save");
            JSONObject cancelled =
                    sync.event("cancelled").put("created", scheduled.getLong("created") + 1);
            sync.importEvents(java.util.Collections.singletonList(cancelled));

            JSONObject stalePrepared = ui.event("prepared").put("ml", 120).put("preparedAt", 1000);
            check(
                    !ui.insertIfCurrent(stalePrepared, scheduled.getString("id")),
                    "Stale preparation must not override imported cancellation");
            JSONObject staleSchedule = ui.event("scheduled").put("ml", 150).put("nextAt", 3000);
            check(
                    !ui.insertIfCurrent(staleSchedule, scheduled.getString("id")),
                    "Stale schedule editor must not override imported state");
            JSONObject staleFinished =
                    ui.event("finished")
                            .put("feed", scheduled.getString("id"))
                            .put("ml", 120)
                            .put("preparedMl", 120)
                            .put("left", 20)
                            .put("consumed", 100)
                            .put("preparedAt", 1000)
                            .put("finishedAt", 2000)
                            .put("nextAt", 3000);
            check(
                    !ui.insertIfCurrent(staleFinished, scheduled.getString("id")),
                    "Stale completion must not create a new schedule");
            check(
                    ui.latest().getString("id").equals(cancelled.getString("id")),
                    "Rejected forms must leave current state intact");

            int before = ui.all(false).size();
            JSONObject valid = ui.event("cancelled");
            JSONObject invalid = ui.event("prepared").put("ml", 120).put("preparedAt", 0);
            try {
                sync.importEvents(java.util.Arrays.asList(valid, invalid));
                throw new AssertionError("Invalid import must fail");
            } catch (IllegalArgumentException expected) {
                check(ui.all(false).size() == before, "Invalid batch must not partially commit");
            }

            JSONObject sameMillisecond =
                    ui.event("scheduled")
                            .put("created", cancelled.getLong("created"))
                            .put("ml", 120)
                            .put("nextAt", 4000);
            check(
                    ui.insertIfCurrent(sameMillisecond, cancelled.getString("id")),
                    "Current state transition must save");
            check(
                    ui.latest().getString("id").equals(sameMillisecond.getString("id")),
                    "Same-millisecond transition must supersede previous event");
        }
    }

    static double contrast(int first, int second) {
        double a = android.graphics.Color.luminance(first),
                b = android.graphics.Color.luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }
}
