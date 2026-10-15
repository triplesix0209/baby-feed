package triplesix.babyfeed;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Language is a local device preference, independent of shared feeding data. */
final class I18n {
    static String language(Context context) {
        return "en".equals(context.getSharedPreferences("settings", 0).getString("language", "vi"))
                ? "en"
                : "vi";
    }

    static Locale locale(Context context) {
        return "en".equals(language(context)) ? Locale.ENGLISH : new Locale("vi", "VN");
    }

    static Context wrap(Context context) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale(context));
        Appearance.configure(context, config);
        return context.createConfigurationContext(config);
    }

    static String text(Context context, int resource) {
        if (context.getResources().getConfiguration().getLocales().get(0).equals(locale(context)))
            return context.getString(resource);
        return wrap(context).getString(resource);
    }

    static String countdown(Context context, long remaining) {
        if (!"en".equals(language(context))) return FeedRules.countdown(remaining);
        if (remaining <= 0) return text(context, R.string.due_now_154);
        long minutes = (remaining + 59999) / 60000;
        return "("
                + (minutes >= 60 ? minutes / 60 + "h " : "")
                + (minutes % 60 > 0 ? minutes % 60 + "m " : "")
                + text(context, R.string.remaining_155);
    }

    static String error(Context context, String message) {
        if (message == null) return text(context, R.string.please_check_your_entries_96);
        // Domain rules remain usable in JVM tests without an Android dependency.
        int[] errors = {
            R.string.hours_cannot_be_negative_minutes_must_be_between_0_and_59_156,
            R.string.enter_an_interval_such_as_1h30p_2h_or_45p_157,
            R.string.minutes_must_be_less_than_60_for_example_1h30p_158,
            R.string.the_amount_left_must_be_between_zero_and_the_amount_prepared_159,
            R.string.the_interval_cannot_be_negative_160,
            R.string.error_invalid_data,
            R.string.error_invalid_record_type,
            R.string.error_no_feed_selected,
            R.string.error_missing_preparation_time,
            R.string.error_missing_next_feed_time,
            R.string.error_invalid_feed_result,
            R.string.error_folder_trashed,
            R.string.error_folder_missing,
            R.string.error_folder_read_only,
            R.string.error_folder_unavailable,
            R.string.error_multiple_folders,
            R.string.error_invalid_folder,
            R.string.error_no_family_folder,
            R.string.error_invalid_sync_device,
            R.string.error_reconnect_drive,
            R.string.error_drive_http_detail
        };
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(new Locale("vi", "VN"));
        Context vietnamese = context.createConfigurationContext(config);
        for (int id : errors)
            if (message.equals(vietnamese.getString(id))) return text(context, id);
        if (message.startsWith("Drive HTTP "))
            return message.replace(
                    vietnamese.getString(R.string.error_drive_http_detail),
                    text(context, R.string.error_drive_http_detail));
        return message;
    }

    private I18n() {}
}
