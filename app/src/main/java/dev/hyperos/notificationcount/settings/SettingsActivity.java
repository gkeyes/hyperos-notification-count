package dev.hyperos.notificationcount.settings;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

import dev.hyperos.notificationcount.BuildConfig;
import dev.hyperos.notificationcount.R;
import dev.hyperos.notificationcount.core.NotificationType;

/** Exported only through the module-settings category; no launcher intent is declared. */
public final class SettingsActivity extends Activity {
    private final Map<NotificationType, Switch> switches = new EnumMap<>(NotificationType.class);
    private final Map<NotificationType, View> rows = new EnumMap<>(NotificationType.class);
    private final Consumer<SettingsStore.State> observer = this::render;
    private SettingsStore store;
    private TextView status;
    private TextView selected;
    private Button reset;
    private Button retry;
    private boolean rendering;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = ((ModuleApplication) getApplication()).getSettingsStore();
        LinearLayout root = column();
        root.setBackgroundColor(getColor(R.color.settings_background));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        Button back = button(R.string.settings_back);
        back.setOnClickListener(view -> finish());
        LinearLayout.LayoutParams backLayout = new LinearLayout.LayoutParams(-2, -2);
        backLayout.setMargins(dp(12), dp(4), 0, 0);
        root.addView(back, backLayout);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(8), dp(20), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView heading = text(getString(R.string.settings_title), 30, false);
        heading.setTypeface(null, Typeface.BOLD);
        content.addView(heading);
        content.addView(text(getString(R.string.settings_version, BuildConfig.VERSION_NAME), 13, true));

        LinearLayout guidance = card();
        guidance.addView(text(getString(R.string.settings_intro), 16, false));
        TextView overlap = text(getString(R.string.settings_overlap), 14, true);
        overlap.setPadding(0, dp(10), 0, 0);
        guidance.addView(overlap);
        content.addView(guidance, spaced(dp(20)));

        status = text("", 14, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status, spaced(dp(14)));
        selected = text("", 14, false);
        content.addView(selected, spaced(dp(8)));

        for (NotificationType type : NotificationType.values()) {
            int section = switch (type) {
                case FOCUS -> R.string.settings_section_focus;
                case PERSISTENT -> R.string.settings_section_ongoing;
                case HEADS_UP_PINNED -> R.string.settings_section_content;
                default -> 0;
            };
            if (section != 0) {
                TextView label = text(getString(section), 14, true);
                label.setTypeface(null, Typeface.BOLD);
                content.addView(label, spaced(dp(24)));
            }
            addFilter(content, type);
        }

        reset = button(R.string.settings_reset);
        reset.setOnClickListener(view -> store.reset());
        content.addView(reset, spaced(dp(24)));
        retry = button(R.string.settings_retry);
        retry.setOnClickListener(view -> store.retry());
        content.addView(retry, spaced(dp(8)));
        setContentView(root);
        root.requestApplyInsets();
        render(store.state());
    }

    @Override protected void onStart() {
        super.onStart();
        store.observe(observer);
    }

    @Override protected void onStop() {
        store.removeObserver(observer);
        super.onStop();
    }

    private void addFilter(LinearLayout content, NotificationType type) {
        int[] labels = labels(type);
        String title = getString(labels[0]);
        String description = getString(labels[1]);
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(80));
        LinearLayout copy = column();
        TextView name = text(title, 16, false);
        name.setTypeface(null, Typeface.BOLD);
        copy.addView(name);
        TextView detail = text(description, 13, true);
        detail.setPadding(0, dp(5), dp(10), 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setId(View.generateViewId());
        toggle.setTag(type);
        toggle.setShowText(false);
        toggle.setMinimumHeight(dp(48));
        toggle.setMinimumWidth(dp(48));
        toggle.setContentDescription(title + "。" + description);
        name.setLabelFor(toggle.getId());
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (!rendering) store.setExcluded(type, checked);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(view -> toggle.toggle());
        switches.put(type, toggle);
        rows.put(type, row);
        content.addView(row, spaced(dp(8)));
    }

    private void render(SettingsStore.State state) {
        rendering = true;
        boolean editable = state.status == SettingsStore.Status.READY;
        for (NotificationType type : NotificationType.values()) {
            Switch toggle = switches.get(type);
            toggle.setChecked((state.mask & type.bit) != 0);
            toggle.setEnabled(editable);
            rows.get(type).setEnabled(editable);
        }
        rendering = false;
        selected.setText(getString(R.string.settings_selected, Integer.bitCount(state.mask)));
        status.setText(switch (state.status) {
            case WAITING -> R.string.settings_waiting;
            case CONNECTING -> R.string.settings_connecting;
            case READY -> R.string.settings_ready;
            case SAVING -> R.string.settings_saving;
            case UNAVAILABLE -> R.string.settings_unavailable;
            case SAVE_FAILED -> R.string.settings_save_failed;
        });
        reset.setEnabled(editable && state.mask != 0);
        retry.setVisibility(state.status == SettingsStore.Status.READY
                || state.status == SettingsStore.Status.SAVING ? View.GONE : View.VISIBLE);
        retry.setEnabled(state.status != SettingsStore.Status.CONNECTING);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(getColor(R.color.settings_surface));
        background.setCornerRadius(dp(16));
        card.setBackground(background);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        return card;
    }

    private TextView text(String value, int size, boolean secondary) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(secondary ? R.color.settings_secondary : R.color.settings_text));
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private Button button(int label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setTextColor(getColor(R.color.settings_accent));
        button.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.settings_surface)));
        return button;
    }

    private LinearLayout.LayoutParams spaced(int top) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.topMargin = top;
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static int[] labels(NotificationType type) {
        return switch (type) {
            case FOCUS -> new int[]{R.string.filter_focus_title, R.string.filter_focus_description};
            case ISLAND_CONTENT -> new int[]{R.string.filter_island_title, R.string.filter_island_description};
            case UPDATABLE_FOCUS -> new int[]{R.string.filter_updatable_title, R.string.filter_updatable_description};
            case PROMOTED_ONGOING -> new int[]{R.string.filter_promoted_title, R.string.filter_promoted_description};
            case REQUEST_PROMOTION -> new int[]{R.string.filter_request_title, R.string.filter_request_description};
            case PERSISTENT -> new int[]{R.string.filter_persistent_title, R.string.filter_persistent_description};
            case ONGOING_EVENT -> new int[]{R.string.filter_ongoing_title, R.string.filter_ongoing_description};
            case NO_CLEAR -> new int[]{R.string.filter_no_clear_title, R.string.filter_no_clear_description};
            case FOREGROUND_SERVICE -> new int[]{R.string.filter_foreground_title, R.string.filter_foreground_description};
            case NOT_CLEARABLE -> new int[]{R.string.filter_not_clearable_title, R.string.filter_not_clearable_description};
            case HEADS_UP_PINNED -> new int[]{R.string.filter_pinned_title, R.string.filter_pinned_description};
            case MEDIA -> new int[]{R.string.filter_media_title, R.string.filter_media_description};
            case CALL -> new int[]{R.string.filter_call_title, R.string.filter_call_description};
            case SILENT -> new int[]{R.string.filter_silent_title, R.string.filter_silent_description};
            case FOLDED -> new int[]{R.string.filter_folded_title, R.string.filter_folded_description};
        };
    }
}
