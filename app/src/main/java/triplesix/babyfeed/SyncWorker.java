package triplesix.babyfeed;

import android.content.Context;

import androidx.work.*;

import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    public SyncWorker(Context c, WorkerParameters p) {
        super(c, p);
    }

    public Result doWork() {
        try (Store s = new Store(getApplicationContext())) {
            if (s.prefs().getString("folder", "").isEmpty()
                    || !s.prefs().getBoolean("linked", false)) return Result.success();
            SyncSession.Ticket session = DriveSync.session();
            try {
                DriveSync.sync(
                        getApplicationContext(), DriveSync.token(getApplicationContext()), session);
                return Result.success();
            } catch (Exception e) {
                if (e instanceof SyncSession.Cancelled || isStopped()) {
                    return Result.success();
                }
                try {
                    session.commit(
                            () -> {
                                s.prefs().edit().putString("syncStatus", e.getMessage()).apply();
                                return null;
                            });
                } catch (Exception cancelled) {
                    return Result.success();
                }
                return Result.retry();
            }
        }
    }

    public static void cancel(Context c) {
        WorkManager.getInstance(c).cancelUniqueWork("drive-now");
        WorkManager.getInstance(c).cancelUniqueWork("drive-sync");
    }

    public static void schedule(Context c) {
        if (!c.getSharedPreferences("settings", 0).getBoolean("linked", false)) return;
        Constraints constraints =
                new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork(
                        "drive-sync",
                        ExistingPeriodicWorkPolicy.KEEP,
                        new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                                .setConstraints(constraints)
                                .build());
    }

    public static void now(Context c) {
        if (!c.getSharedPreferences("settings", 0).getBoolean("linked", false)) return;
        WorkManager.getInstance(c)
                .enqueueUniqueWork(
                        "drive-now",
                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setConstraints(
                                        new Constraints.Builder()
                                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                                .build())
                                .build());
    }
}
