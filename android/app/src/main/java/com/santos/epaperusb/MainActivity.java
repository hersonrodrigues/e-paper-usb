package com.santos.epaperusb;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import com.santos.epaperusb.usb.UsbController;

public final class MainActivity extends ComponentActivity {
    private static final int INK = 0xff202a25, MUTED = 0xff617067, GREEN = 0xff28634c, PAPER = 0xfff5f4ef;
    private AppModel model;
    private ImageView preview;
    private TextView placeholder, photoStatus, usbStatus, badge, sendRequirement;
    private Button choose, chart, rotate, connect, send, cancel, reset, export;
    private RadioButton whole, crop;
    private Switch dither;
    private ProgressBar progress;
    private boolean rendering;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> { if (uri != null) model.select(uri); });
    private final ActivityResultLauncher<String> logDocument = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain") {
        @Override public android.content.Intent createIntent(android.content.Context context, String input) {
            return super.createIntent(context, input).putExtra(android.content.Intent.EXTRA_LOCAL_ONLY, true);
        }
    }, uri -> model.exportLog(uri));

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        model = new ViewModelProvider(this).get(AppModel.class);
        buildUi();
        model.image.observe(this, value -> render());
        model.usbState.observe(this, value -> render());
        model.notice.observe(this, message -> {
            if (message != null) { Toast.makeText(this, message.resolve(this), Toast.LENGTH_LONG).show(); model.notice.setValue(null); }
        });
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(PAPER);
        LinearLayout root = column(); root.setPadding(dp(22), dp(16), dp(22), dp(24)); scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets system = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(system.left, system.top, system.right, system.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        TextView eyebrow = text(getString(R.string.eyebrow), 11, GREEN); eyebrow.setLetterSpacing(.17f); root.addView(eyebrow);
        TextView title = text(getString(R.string.app_name), 32, INK); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        addText(root, getString(R.string.subtitle), 16, MUTED, 4);
        LinearLayout tags = new LinearLayout(this); tags.setGravity(Gravity.CENTER_VERTICAL); margin(root, tags, 14);
        int[] colorIds = {R.string.black, R.string.white, R.string.yellow, R.string.red};
        int[] colors = {INK, MUTED, 0xff8a7200, 0xffb43d34};
        for (int i = 0; i < colorIds.length; i++) {
            if (i == 2) { tags = row(); margin(root, tags, 2); }
            TextView label = text((i == 1 ? "○ " : "● ") + getString(colorIds[i]), 12, colors[i]);
            tags.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        }

        LinearLayout photo = card(root, 18); section(photo, "1", getString(R.string.section_photo));
        LinearLayout photoActions = row(); photo.addView(photoActions);
        choose = button(getString(R.string.choose_photo), true); choose.setTag("choose"); weighted(photoActions, choose);
        chart = button(getString(R.string.color_test), false); chart.setTag("chart"); weighted(photoActions, chart);
        choose.setOnClickListener(v -> picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        chart.setOnClickListener(v -> model.pattern(new String[]{getString(R.string.black), getString(R.string.white), getString(R.string.yellow), getString(R.string.red), getString(R.string.test_top), getString(R.string.test_bottom)}));
        FrameLayout frame = new FrameLayout(this) {
            @Override protected void onMeasure(int width, int height) { super.onMeasure(width, MeasureSpec.makeMeasureSpec(Math.round(MeasureSpec.getSize(width) * .6f), MeasureSpec.EXACTLY)); }
        };
        frame.setBackground(shape(Color.WHITE, 0, 0xffdedfd6)); margin(photo, frame, 12);
        preview = new ImageView(this); preview.setTag("preview"); preview.setContentDescription(getString(R.string.preview_description)); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        placeholder = text(getString(R.string.preview_placeholder), 15, MUTED); placeholder.setGravity(Gravity.CENTER); frame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
        photoStatus = text("", 12, MUTED); margin(photo, photoStatus, 9);
        RadioGroup fit = new RadioGroup(this); fit.setOrientation(RadioGroup.HORIZONTAL); margin(photo, fit, 8);
        whole = new RadioButton(this); whole.setText(R.string.fit_whole); whole.setTextSize(13); whole.setId(View.generateViewId()); whole.setTag("whole");
        crop = new RadioButton(this); crop.setText(R.string.fit_crop); crop.setTextSize(13); crop.setId(View.generateViewId()); crop.setTag("crop");
        fit.addView(whole, new RadioGroup.LayoutParams(0, -2, 1)); fit.addView(crop, new RadioGroup.LayoutParams(0, -2, 1));
        fit.setOnCheckedChangeListener((g, id) -> { if (!rendering) model.options(id == crop.getId(), model.dither, model.turns); });
        LinearLayout adjustments = column(); photo.addView(adjustments);
        rotate = button(getString(R.string.rotate), false); rotate.setTag("rotate"); adjustments.addView(rotate, new LinearLayout.LayoutParams(-1, -2));
        dither = new Switch(this); dither.setText(R.string.dither); dither.setTextSize(13); dither.setTag("dither"); dither.setMinHeight(dp(48));
        adjustments.addView(dither, new LinearLayout.LayoutParams(-1, -2));
        rotate.setOnClickListener(v -> model.options(model.crop, model.dither, model.turns + 1));
        dither.setOnCheckedChangeListener((v, on) -> { if (!rendering) model.options(model.crop, on, model.turns); });
        addText(photo, getString(R.string.fit_help), 12, MUTED, 5);

        LinearLayout usb = card(root, 14); section(usb, "2", getString(R.string.section_usb));
        badge = text("", 11, GREEN); badge.setTypeface(null, Typeface.BOLD); usb.addView(badge);
        usbStatus = text("", 14, INK); usbStatus.setTag("usbStatus"); margin(usb, usbStatus, 8);
        connect = button(getString(R.string.connect_usb), false); connect.setTag("connect"); margin(usb, connect, 10);
        connect.setOnClickListener(v -> {
            if (model.usb.state().phase() == UsbController.Phase.READY) model.usb.disconnect(); else model.usb.connect();
        });
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(24); margin(usb, progress, 8);
        send = button(getString(R.string.send), true); send.setTag("send"); margin(usb, send, 6); send.setOnClickListener(v -> model.send());
        sendRequirement = text("", 12, MUTED); sendRequirement.setTag("sendRequirement"); margin(usb, sendRequirement, 4);
        cancel = button(getString(R.string.cancel_send), false); cancel.setTag("cancel"); usb.addView(cancel); cancel.setOnClickListener(v -> model.usb.cancel());
        reset = button(getString(R.string.reset_done), false); usb.addView(reset);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(getString(R.string.reset_title))
            .setMessage(getString(R.string.reset_confirm))
            .setNegativeButton(getString(R.string.back), null).setPositiveButton(getString(R.string.reset_yes), (dialog, which) -> model.usb.confirmPhysicalReset()).show());
        addText(usb, getString(R.string.profile_note), 12, MUTED, 10);
        export = button(getString(R.string.export_log), false); margin(root, export, 14);
        export.setOnClickListener(v -> logDocument.launch("epaper-diagnostic.txt"));
        TextView help = text(getString(R.string.help_link, BuildConfig.VERSION_NAME), 14, GREEN); help.setPadding(0, dp(12), 0, dp(14)); help.setGravity(Gravity.CENTER); root.addView(help);
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(getString(R.string.help_title))
            .setMessage(R.string.help_body)
            .setPositiveButton(getString(R.string.got_it), null).show());
        setContentView(scroll); scroll.requestApplyInsets(); render();
    }
    private void render() {
        if (preview == null) return;
        rendering = true;
        AppModel.ImageState image = model.image.getValue(); UsbController.State state = model.usb.state();
        boolean sending = state.phase() == UsbController.Phase.SENDING, editing = !sending && !image.processing();
        preview.setImageBitmap(image.preview()); placeholder.setVisibility(image.preview() == null ? View.VISIBLE : View.GONE);
        photoStatus.setText(image.message().resolve(this)); whole.setChecked(!model.crop); crop.setChecked(model.crop); dither.setChecked(model.dither);
        choose.setEnabled(editing); chart.setEnabled(editing); whole.setEnabled(editing); crop.setEnabled(editing); dither.setEnabled(editing);
        rotate.setEnabled(editing && image.prepared() != null);
        usbStatus.setText(state.message().resolve(this));
        badge.setText(switch (state.phase()) {
            case READY -> getString(R.string.badge_ready); case SENDING -> getString(R.string.badge_sending); case RESET_REQUIRED -> getString(R.string.badge_reset);
            case PERMISSION, CONNECTING, CLOSING -> getString(R.string.badge_connecting); default -> getString(R.string.badge_waiting);
        });
        connect.setText(state.phase() == UsbController.Phase.READY ? getString(R.string.disconnect_usb)
            : state.phase() == UsbController.Phase.PERMISSION ? getString(R.string.check_permission) : getString(R.string.connect_usb));
        connect.setEnabled(state.phase() == UsbController.Phase.READY || state.phase() == UsbController.Phase.DISCONNECTED
            || state.phase() == UsbController.Phase.PERMISSION);
        send.setEnabled(state.phase() == UsbController.Phase.READY && editing && image.prepared() != null);
        send.setText(sending ? getString(R.string.sending) : getString(R.string.send));
        sendRequirement.setText(sending ? getString(R.string.need_foreground)
            : image.processing() ? getString(R.string.need_processing)
            : image.prepared() == null ? getString(R.string.need_image)
            : state.phase() != UsbController.Phase.READY ? getString(R.string.need_usb)
            : getString(R.string.ready_to_send));
        cancel.setVisibility(sending ? View.VISIBLE : View.GONE); reset.setVisibility(state.phase() == UsbController.Phase.RESET_REQUIRED ? View.VISIBLE : View.GONE);
        progress.setVisibility(sending || state.blocks() > 0 ? View.VISIBLE : View.GONE); progress.setProgress(state.blocks());
        progress.setIndeterminate(sending && state.blocks() == 0); export.setEnabled(!sending);
        if (sending) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        rendering = false;
    }
    @Override protected void onResume() {
        super.onResume();
        if (model != null) model.usb.recheckPendingPermission();
    }
    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && model != null) {
            UsbController.Phase phase = model.usb.state().phase();
            if (phase == UsbController.Phase.READY || phase == UsbController.Phase.SENDING || phase == UsbController.Phase.CONNECTING) model.usb.disconnect();
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private void addText(LinearLayout parent, String value, int size, int color, int top) { margin(parent, text(value, size, color), top); }
    private void margin(LinearLayout parent, View child, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(child, p); }
    private void weighted(LinearLayout parent, View child) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(2), 0, dp(2), 0); parent.addView(child, p); }
    private LinearLayout card(LinearLayout root, int top) { LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(shape(Color.WHITE, 18, 0xffe2e4dc)); margin(root, card, top); return card; }
    private void section(LinearLayout parent, String number, String title) { TextView heading = text(number + "   " + title, 18, INK); heading.setTypeface(null, Typeface.BOLD); heading.setPadding(0, 0, 0, dp(14)); parent.addView(heading); }
    private GradientDrawable shape(int color, int radius, int stroke) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); drawable.setStroke(dp(1), stroke); return drawable; }
    private Button button(String title, boolean primary) {
        Button button = new Button(this); button.setText(title); button.setTextSize(13); button.setAllCaps(false); button.setSingleLine(false); button.setMinHeight(dp(48));
        int[][] states = {new int[]{-android.R.attr.state_enabled}, new int[]{}};
        button.setTextColor(new ColorStateList(states, new int[]{0xff859087, primary ? Color.WHITE : GREEN}));
        button.setBackgroundTintList(new ColorStateList(states, new int[]{0xffe6e9e3, primary ? GREEN : 0xffedf2ed}));
        return button;
    }
}
