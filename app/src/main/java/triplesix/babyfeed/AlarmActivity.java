package triplesix.babyfeed;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class AlarmActivity extends Activity {
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(I18n.wrap(base));
    }

    protected void onCreate(Bundle state) {
        Appearance.applyTheme(this);
        super.onCreate(state);
        if (Reminders.isSleeping(this)) {
            Reminders.silence(this);
            finish();
            return;
        }
        PlatformUi.showAlarmWhenLocked(this);
        render();
    }

    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        render();
    }

    void render() {
        if (Reminders.isSleeping(this)) {
            Reminders.silence(this);
            finish();
            return;
        }
        try (Store s = new Store(this)) {
            if (s.latest() == null
                    || !s.latest().optString("id").equals(getIntent().getStringExtra("id"))) {
                finish();
                return;
            }
        }
        FeedUi ui = new FeedUi(this);
        Appearance.styleWindow(this, ui);
        LinearLayout content = ui.column();
        content.setGravity(Gravity.CENTER);
        content.setPadding(ui.dp(28), ui.dp(48), ui.dp(28), ui.dp(48));
        content.setBackgroundColor(ui.BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        FeedUi.Bottle bottle = new FeedUi.Bottle(this);
        content.addView(bottle, new LinearLayout.LayoutParams(ui.dp(112), ui.dp(112)));
        ui.gap(content, 32);
        TextView time =
                ui.text(
                        content,
                        new SimpleDateFormat("HH:mm", I18n.locale(this)).format(new Date()),
                        56,
                        ui.INK,
                        true);
        time.setGravity(Gravity.CENTER);
        boolean check = "check".equals(getIntent().getStringExtra("kind"));
        TextView title =
                ui.text(
                        content,
                        check
                                ? I18n.text(this, R.string.has_baby_finished_feeding_133)
                                : I18n.text(this, R.string.time_to_prepare_milk_134),
                        26,
                        ui.INK,
                        true);
        title.setGravity(Gravity.CENTER);
        TextView detail =
                ui.text(
                        content,
                        check
                                ? I18n.text(this, R.string.record_how_much_baby_drank_135)
                                : I18n.text(this, R.string.prepared_161)
                                        + Amounts.ml(
                                                getIntent().getIntExtra("ml", 0), I18n.locale(this))
                                        + I18n.text(this, R.string.for_baby_136),
                        18,
                        ui.ACCENT,
                        true);
        detail.setGravity(Gravity.CENTER);
        ui.gap(content, 32);
        ui.button(
                content,
                check
                        ? I18n.text(this, R.string.record_feed_137)
                        : I18n.text(this, R.string.open_feeding_app_138),
                true,
                () -> {
                    Reminders.silence(this);
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
        ui.button(
                content,
                I18n.text(this, R.string.remind_me_in_10_minutes_4),
                false,
                () -> {
                    Reminders.silence(this);
                    Reminders.snooze(this, getIntent().getStringExtra("id"));
                    finish();
                });
        ui.button(
                content,
                I18n.text(this, R.string.dismiss_alarm_139),
                false,
                () -> {
                    Reminders.silence(this);
                    finish();
                });
    }
}
