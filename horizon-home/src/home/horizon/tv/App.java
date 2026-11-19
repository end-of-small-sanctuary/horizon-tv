package home.horizon.tv;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/** Tracks which Horizon Home screen is in front, so the remote's settings button can open Settings over it. */
public class App extends Application {
    /** The Horizon Home screen currently in front, or null when another app is in front. */
    static Activity resumed;

    /**
     * Set while Settings, opened by the settings button over a Horizon Home screen, is in front;
     * the next press then closes it instead of opening another copy on top.
     */
    static Class<? extends Activity> settingsOver;

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { resumed = a; settingsOver = null; }
            @Override public void onActivityPaused(Activity a) { if (resumed == a) resumed = null; }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { if (resumed == a) resumed = null; }
        });
    }
}
