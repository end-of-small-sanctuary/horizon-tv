package home.horizon.tv;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Adds an IPTV playlist (Xtream Codes sign-in), or edits one when started with extra "id".
 * Typed on the TV by the user; stored only in this app's private storage.
 */
public class LoginActivity extends Activity {
    private Xtream editing;
    private EditText name;
    private EditText server;
    private EditText user;
    private EditText pass;
    private TextView status;
    private TextView button;
    private TextView sub;
    private TextView xtreamChip;
    private TextView m3uChip;
    private boolean busy;
    /** "xtream" or "m3u" (an M3U playlist link). */
    private String type = "xtream";

    private int px(float v) { return Ui.px(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPaddingRelative(px(64), px(56), px(64), px(56));
        panel.setBackground(Ui.round(0xFF141922, px(28)));
        root.addView(panel, new FrameLayout.LayoutParams(px(860), -2, Gravity.CENTER));

        String id = getIntent().getStringExtra("id");
        editing = id == null ? null : Xtream.byId(this, id);

        TextView title = Ui.text(this, 48, Ui.TEXT, "sans-serif-light");
        title.setText(editing == null ? T.t("Add an IPTV playlist") : T.f("Edit %s", editing.label()));
        panel.addView(title);
        sub = Ui.text(this, 24, Ui.MUTED, "sans-serif");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = px(10);
        slp.bottomMargin = px(22);
        panel.addView(sub, slp);

        // Playlist type: Xtream Codes (server + username + password) or an M3U playlist link.
        if (editing != null) type = editing.type;
        LinearLayout types = new LinearLayout(this);
        xtreamChip = chip("Xtream Codes");
        m3uChip = chip(T.t("M3U link"));
        types.addView(xtreamChip);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.setMarginStart(px(14));
        types.addView(m3uChip, clp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.bottomMargin = px(22);
        panel.addView(types, tlp);
        xtreamChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setType("xtream"); }
        });
        m3uChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setType("m3u"); }
        });

        name = field(panel, T.t("Name (optional, for example Home or Sports)"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, editing == null ? "" : editing.name);
        server = field(panel, T.t("Server address (for example http://example.com:8080)"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, editing == null ? "" : editing.server);
        user = field(panel, T.t("Username"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                editing == null ? "" : editing.user);
        pass = field(panel, T.t("Password"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "");

        TextView b = Ui.text(this, 28, 0xFF11151B, "sans-serif-medium");
        b.setGravity(Gravity.CENTER);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = Ui.round(0xFFFFFFFF, px(16));
        bg.addState(new int[] {android.R.attr.state_focused}, on);
        bg.addState(new int[0], Ui.round(0xFFC9CED6, px(16)));
        b.setBackground(bg);
        Ui.focusable(b);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { signIn(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, px(84));
        blp.topMargin = px(16);
        panel.addView(b, blp);
        button = b;

        status = Ui.text(this, 24, Ui.MUTED, "sans-serif");
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-1, -2);
        stlp.topMargin = px(20);
        panel.addView(status, stlp);

        setContentView(root);
        setType(type);
        // Adding: start on the playlist-type choice. Editing: start where a change is most likely.
        (editing == null ? xtreamChip : "m3u".equals(type) ? server : pass).requestFocus();
    }

    private TextView chip(String label) {
        TextView c = Ui.text(this, 24, Ui.TEXT, "sans-serif-medium");
        c.setText(label);
        c.setPaddingRelative(px(30), px(14), px(30), px(14));
        Ui.focusable(c);
        return c;
    }

    /** Switches the form between the two playlist types. */
    private void setType(String t) {
        type = t;
        boolean m3u = "m3u".equals(t);
        // The chosen type is filled with the wallpaper's accent colour; the other stays dark.
        // The white outline only shows where the remote's focus is, so "chosen" and "focused" never look alike.
        for (TextView c : new TextView[] {xtreamChip, m3uChip}) {
            boolean selected = (c == m3uChip) == m3u;
            int fill = selected ? Theme.accent : 0xFF1C222B;
            GradientDrawable focused = Ui.round(selected ? Theme.accent : 0xFF2A313C, px(30));
            focused.setStroke(px(4), 0xFFFFFFFF);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[] {android.R.attr.state_focused}, focused);
            bg.addState(new int[0], Ui.round(fill, px(30)));
            c.setBackground(bg);
            c.setTextColor(selected ? 0xFF11151B : 0xFF9AA3B0);
        }
        user.setVisibility(m3u ? View.GONE : View.VISIBLE);
        pass.setVisibility(m3u ? View.GONE : View.VISIBLE);
        server.setHint(m3u ? T.t("Playlist link (for example http://example.com/playlist.m3u)")
                : T.t("Server address (for example http://example.com:8080)"));
        sub.setText(m3u ? T.t("An M3U playlist link from your IPTV provider.")
                : editing == null ? T.t("Xtream Codes details from your IPTV provider.")
                : T.t("Leave the password empty to keep the saved one."));
        button.setText(editing != null ? T.t("Save") : m3u ? T.t("Add a playlist") : T.t("Sign in and add"));
    }

    /** M3U: download the list once to check it has channels or videos, then save it. */
    private void addM3u(final String link) {
        busy = true;
        status.setText(T.t("Loading the playlist..."));
        final Xtream x = new Xtream(editing == null ? null : editing.id, name.getText().toString(), link, "", "", "m3u8", "m3u");
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                String message;
                boolean ok = false;
                try {
                    JSONObject r = M3u.check(x);
                    if (r.optInt("count") == 0) {
                        message = T.t("This link didn't give a playlist with any channels or videos.");
                    } else {
                        message = T.f("Loaded: %d channels and videos", r.optInt("count"));
                        ok = true;
                    }
                } catch (Exception e) {
                    message = T.f("Could not reach the provider: %s", String.valueOf(e.getMessage()));
                } catch (OutOfMemoryError e) {
                    message = T.t("the list is too large for this device");
                }
                final boolean success = ok;
                final String text = message;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        status.setText(text);
                        if (success) {
                            Xtream saved = Xtream.put(LoginActivity.this, x, "m3u8");
                            if (editing == null) Xtream.setActive(LoginActivity.this, saved.id);
                            Ui.toast(LoginActivity.this, saved.label() + ": " + text);
                            finish();
                        } else {
                            button.requestFocus();
                        }
                    }
                });
            }
        });
    }

    private EditText field(LinearLayout panel, String hint, int type, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF6F7886);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(28));
        e.setSingleLine(true);
        e.setInputType(type);
        // Arabic/Urdu hints sit on the right; typed addresses and usernames (Latin) still read left to right.
        e.setTextDirection(T.rtl() ? View.TEXT_DIRECTION_FIRST_STRONG_RTL : View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        // Remote Up/Down always move to the field above/below (not along the text), so the form is easy to walk.
        e.setOnKeyListener(new View.OnKeyListener() {
            @Override public boolean onKey(View v, int code, android.view.KeyEvent ev) {
                if (ev.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                int dir = code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                        : code == android.view.KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : 0;
                if (dir == 0) return false;
                View next = v.focusSearch(dir);
                if (next != null) next.requestFocus(dir);
                return true;
            }
        });
        e.setText(value);
        e.setPaddingRelative(px(24), 0, px(24), 0);
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = Ui.round(0xFF232A35, px(14));
        on.setStroke(px(3), 0xFFFFFFFF);
        bg.addState(new int[] {android.R.attr.state_focused}, on);
        bg.addState(new int[0], Ui.round(0xFF1C222B, px(14)));
        e.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(84));
        lp.bottomMargin = px(18);
        panel.addView(e, lp);
        return e;
    }

    private void signIn() {
        if (busy) return;
        String s = server.getText().toString().trim();
        if ("m3u".equals(type)) {
            if (s.isEmpty()) {
                status.setText(T.t("Please fill in the playlist link."));
                return;
            }
            addM3u(s);
            return;
        }
        String u = user.getText().toString().trim();
        String p = pass.getText().toString();
        if (p.isEmpty() && editing != null) p = editing.pass;   // keep the saved password
        if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
            status.setText(T.t("Please fill in the server, username and password."));
            return;
        }
        busy = true;
        status.setText(T.t("Checking with your provider..."));
        final Xtream x = new Xtream(editing == null ? null : editing.id, name.getText().toString(), s, u, p, "m3u8");
        Ui.POOL.execute(new Runnable() {
            @Override public void run() {
                String message;
                boolean ok = false;
                String fmt = "m3u8";
                try {
                    JSONObject info = x.login();
                    JSONObject ui = info.optJSONObject("user_info");
                    if (ui == null || ui.optInt("auth", 0) != 1) {
                        message = T.t("The provider rejected these details. Check them and try again.");
                    } else {
                        JSONArray formats = ui.optJSONArray("allowed_output_formats");
                        if (formats != null && formats.length() > 0 && !formats.toString().contains("m3u8")) fmt = "ts";
                        String exp = ui.optString("exp_date", "");
                        String until = "";
                        try {
                            long t = Long.parseLong(exp) * 1000L;
                            until = T.f(", valid until %s", new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(t)));
                        } catch (NumberFormatException ignored) {
                            // no expiry given
                        }
                        message = T.f("Signed in (%1$s%2$s).", ui.optString("status", "Active"), until);
                        ok = true;
                    }
                } catch (Exception e) {
                    String m = String.valueOf(e.getMessage());
                    if (m.contains("answered 404") || m.contains("answered 401") || m.contains("answered 403")) {
                        // Many Xtream servers answer 404 (or 401/403) instead of auth=0 for a wrong login.
                        message = T.t("The provider didn't accept this username and password. They are case-sensitive - check them and try again.");
                    } else {
                        message = T.f("Could not reach the provider: %s", m);
                    }
                }
                final boolean success = ok;
                final String text = message;
                final String format = fmt;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        busy = false;
                        status.setText(text);
                        if (success) {
                            Xtream saved = Xtream.put(LoginActivity.this, x, format);
                            // A new playlist is put to use straight away; editing keeps the current choice.
                            if (editing == null) Xtream.setActive(LoginActivity.this, saved.id);
                            Ui.toast(LoginActivity.this, saved.label() + ": " + text);
                            finish();
                        } else {
                            button.requestFocus();
                        }
                    }
                });
            }
        });
    }
}
