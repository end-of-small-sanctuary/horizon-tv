package home.horizon.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Catches ONE remote button: the Xiaomi remote's settings/apps key, which Android 9 maps to ALL_APPS and
 * sends only to a preinstalled home app (so it did nothing after the Google launcher was disabled).
 * Every other key is passed through untouched. It never reads screen content.
 */
public class KeyService extends AccessibilityService {

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_ALL_APPS) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) {
            // A Horizon Home screen is in front: open Settings inside its task (like the gear button),
            // so that screen stays visible, dimmed, behind the panel.
            // During a film or channel: the same button opens the brightness slider instead.
            if (App.resumed instanceof PlayerActivity) {
                ((PlayerActivity) App.resumed).togglePicture();
                return true;
            }
            if (App.resumed != null) {
                Ui.openSettings(App.resumed);
                return true;
            }
            // Settings is already open over a Horizon Home screen: this press closes it (everything above that
            // screen is cleared), instead of stacking another Settings on top.
            if (App.settingsOver != null) {
                Class<? extends android.app.Activity> back = App.settingsOver;
                App.settingsOver = null;
                try {
                    startActivity(new Intent(this, back).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                } catch (RuntimeException ignored) {
                    // screen is gone
                }
                return true;
            }
            // Another app is in front: open Settings, reusing it if it's already open (never a second copy).
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (RuntimeException ignored) {
                // nothing to open
            }
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
