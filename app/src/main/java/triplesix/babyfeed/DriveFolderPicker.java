package triplesix.babyfeed;

import android.app.*;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.util.*;
import java.util.function.Consumer;

/** Browses folder levels; read-only parents remain navigable. */
final class DriveFolderPicker {
    final Activity activity;
    final FeedUi ui;
    final List<JSONObject> folders;
    final Consumer<JSONObject> selected;
    final Map<String, JSONObject> byId = new HashMap<>();
    final List<JSONObject> visible = new ArrayList<>();
    String current = "";
    AlertDialog dialog;
    TextView path, empty, back;
    ListView list;
    Button use;
    Runnable dismissed = () -> {};

    DriveFolderPicker onDismiss(Runnable callback) {
        dismissed = callback;
        return this;
    }

    DriveFolderPicker(Activity activity, List<JSONObject> folders, Consumer<JSONObject> selected) {
        this.activity = activity;
        this.ui = new FeedUi(activity);
        this.folders = folders;
        this.selected = selected;
        for (JSONObject f : folders) byId.put(f.optString("id"), f);
    }

    String parent(JSONObject folder) {
        JSONArray parents = folder.optJSONArray("parents");
        String id = parents == null ? "" : parents.optString(0);
        return byId.containsKey(id) ? id : "";
    }

    void show() {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(20));
        ui.text(content, I18n.text(activity, R.string.choose_folder_92), 23, ui.INK, true);
        LinearLayout location = ui.row();
        location.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        location.setBackground(ui.shape(ui.SOFT, 14, false));
        back = ui.label("‹", 30, ui.ACCENT, true);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription(I18n.text(activity, R.string.go_to_parent_folder_130));
        location.addView(back, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(48)));
        back.setOnClickListener(
                v -> {
                    if (!current.isEmpty()) current = parent(byId.get(current));
                    refresh();
                });
        path = ui.label("Google Drive", 14, ui.INK, true);
        location.addView(path, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(location);
        ui.gap(content, 12);
        FrameLayout body = new FrameLayout(activity);
        list = new ListView(activity);
        list.setDividerHeight(0);
        body.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = ui.label(I18n.text(activity, R.string.no_subfolders_131), 14, ui.MUTED, false);
        empty.setGravity(Gravity.CENTER);
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        content.addView(body, new LinearLayout.LayoutParams(-1, ui.dp(280)));
        list.setOnItemClickListener(
                (p, v, index, id) -> {
                    current = visible.get(index).optString("id");
                    refresh();
                });
        dialog = new AlertDialog.Builder(activity).setView(content).create();
        dialog.setOnDismissListener(d -> dismissed.run());
        use =
                ui.button(
                        content,
                        I18n.text(activity, R.string.use_this_folder_132),
                        true,
                        () -> {
                            JSONObject target = byId.get(current);
                            if (target == null) return;
                            selected.accept(target);
                            dialog.dismiss();
                        });
        ui.button(content, I18n.text(activity, R.string.cancel_19), false, dialog::dismiss);
        refresh();
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(ui.shape(ui.SURFACE, 26, false));
    }

    void refresh() {
        visible.clear();
        for (JSONObject f : folders) if (parent(f).equals(current)) visible.add(f);
        visible.sort(Comparator.comparing(f -> f.optString("name"), String.CASE_INSENSITIVE_ORDER));
        path.setText(
                current.isEmpty()
                        ? "Google Drive"
                        : byId.get(current).optString("path", byId.get(current).optString("name")));
        back.setAlpha(current.isEmpty() ? .3f : 1f);
        back.setEnabled(!current.isEmpty());
        JSONObject folder = byId.get(current);
        boolean writable =
                folder != null
                        && folder.optJSONObject("capabilities") != null
                        && folder.optJSONObject("capabilities").optBoolean("canAddChildren");
        use.setEnabled(writable);
        use.setAlpha(writable ? 1f : .45f);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        List<String> names = new ArrayList<>();
        for (JSONObject f : visible) names.add(f.optString("name"));
        list.setAdapter(
                new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, names) {
                    public View getView(int position, View recycled, ViewGroup parent) {
                        LinearLayout row = ui.row();
                        row.setPadding(ui.dp(10), ui.dp(16), ui.dp(10), ui.dp(16));
                        TextView icon = ui.label("▣", 22, ui.ACCENT, true);
                        row.addView(icon, new LinearLayout.LayoutParams(ui.dp(36), -2));
                        TextView name = ui.label(getItem(position), 16, ui.INK, false);
                        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                        row.addView(ui.label("›", 24, ui.MUTED, false));
                        return row;
                    }
                });
        list.setSelection(0);
    }
}
