package home.horizon.tv;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every installed app. OK opens it; the red button adds or removes it from the home row. */
public class AppsActivity extends Activity {
    private static final int COLUMNS = 6;
    private final Map<String, Apps.App> byTag = new HashMap<>();
    private final Map<String, TextView> marks = new HashMap<>();

    private int px(float v) { return Ui.px(this, v); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);
        col.setPaddingRelative(px(96), px(60), px(96), 0);

        TextView title = Ui.text(this, 52, Ui.TEXT, "sans-serif-light");
        title.setText(T.t("All apps"));
        col.addView(title);
        TextView hint = Ui.text(this, 24, Ui.MUTED, "sans-serif");
        hint.setText(T.t("Hold OK (or press the menu button) to add an app to the home row, or take it off."));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = px(10);
        hlp.bottomMargin = px(24);
        col.addView(hint, hlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPaddingRelative(0, px(16), 0, px(60));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        grid.setClipChildren(false);
        scroll.addView(grid);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);

        List<String> favs = Apps.favs(this);
        List<Apps.App> apps = Apps.load(this);
        for (int i = 0; i < apps.size(); i++) {
            final Apps.App a = apps.get(i);
            FrameLayout t = new FrameLayout(this);
            t.setTag("app:" + a.pkg);
            byTag.put("app:" + a.pkg, a);
            t.setBackground(Ui.glass(this, 20));
            Ui.focusable(t);
            Ui.lift(t, 1.07f, null);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (a.banner != null) {
                img.setImageDrawable(a.banner);
                inner.addView(img, new LinearLayout.LayoutParams(px(200), px(112)));
            } else {
                img.setImageDrawable(a.icon);
                inner.addView(img, new LinearLayout.LayoutParams(px(72), px(72)));
                TextView name = Ui.text(this, 20, Ui.TEXT, "sans-serif");
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setGravity(Gravity.CENTER);
                name.setText(a.label);
                LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(px(200), -2);
                nlp.topMargin = px(10);
                inner.addView(name, nlp);
            }
            t.addView(inner, new FrameLayout.LayoutParams(-1, -1));
            TextView mark = Ui.text(this, 18, 0xFF11151B, "sans-serif-medium");
            mark.setText(T.t("ON HOME"));
            mark.setBackground(Ui.round(0xFFF4F6FA, px(6)));
            mark.setPaddingRelative(px(8), px(4), px(8), px(4));
            mark.setVisibility(favs.contains(a.pkg) ? View.VISIBLE : View.GONE);
            FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            mlp.topMargin = px(10);
            mlp.setMarginEnd(px(10));
            t.addView(mark, mlp);
            marks.put(a.pkg, mark);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Ui.open(AppsActivity.this, a.intent); }
            });
            t.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { toggle(v); return true; }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(248);
            lp.height = px(156);
            boolean last = i % COLUMNS == COLUMNS - 1;
            lp.setMargins(0, 0, last ? 0 : px(24), px(24));
            grid.addView(t, lp);
        }
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (Ui.isOptionsKey(e.getKeyCode())) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) toggle(getCurrentFocus());
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void toggle(View f) {
        Apps.App a = f == null ? null : byTag.get(f.getTag());
        if (a == null) return;
        List<String> favs = Apps.favs(this);
        boolean on = favs.contains(a.pkg);
        if (on) favs.remove(a.pkg); else favs.add(a.pkg);
        Apps.saveFavs(this, favs);
        marks.get(a.pkg).setVisibility(on ? View.GONE : View.VISIBLE);
        Ui.toast(this, on ? T.f("%s taken off the home row", a.label) : T.f("%s added to the home row", a.label));
    }
}
