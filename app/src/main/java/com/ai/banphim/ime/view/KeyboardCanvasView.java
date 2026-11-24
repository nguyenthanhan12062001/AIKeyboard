package com.ai.banphim.ime.view;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.ai.banphim.ime.engine.KeyboardLanguage.KeyboardLayoutType;
import com.ai.banphim.ime.view.KeyboardLayoutSpec.KeyAction;
import com.ai.banphim.ime.view.KeyboardLayoutSpec.KeySpec;
import com.ai.banphim.ime.view.KeyboardLayoutSpec.KeyboardPage;
import com.ai.banphim.ime.view.KeyboardLayoutSpec.ShiftState;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance Java Custom View/Canvas keyboard surface modeled after Google Gboard.
 * Handles key geometry, press feedback, floating preview popups, long-press alternates,
 * spacebar horizontal swipe language switching, and accelerating Backspace repeat.
 */
public class KeyboardCanvasView extends View {

    /**
     * Callback interface notifying the host IME service of key presses, gestures, and layout changes.
     */
    public interface OnKeyboardActionListener {
        /** Invoked when a character key or long-press alternate is committed. */
        void onCharacter(String text);
        /** Invoked on Backspace press or accelerating repeat; {@code wordDelete} deletes a full word. */
        void onBackspace(boolean wordDelete);
        /** Invoked when the Enter/Action key is tapped. */
        void onEnter();
        /** Invoked when the Spacebar is tapped. */
        void onSpace();
        /** Invoked when the Spacebar is long-pressed to open the language switcher. */
        void onSpaceLongPress();
        /** Invoked when swiping horizontally across the Spacebar ({@code +1} next, {@code -1} previous). */
        void onSwipeSpaceLanguage(int direction);
        /** Invoked when Shift is tapped; {@code doubleTap} locks Caps Lock. */
        void onShiftToggle(boolean doubleTap);
        /** Invoked when the Emoji key is tapped. */
        void onOpenEmoji();
        /** Invoked when switching between QWERTY, Symbols, and Numeric pages. */
        void onPageChanged(KeyboardPage newPage);
        /** Invoked before key actions to trigger haptic or audio feedback with action type. */
        void onKeyFeedback(KeyAction action);
    }

    /** Measured key specification paired with its screen-space hit rectangle and row index. */
    private static final class PlacedKey {
        final KeySpec spec;
        final RectF bounds;
        final int rowIndex;

        PlacedKey(KeySpec spec, RectF bounds, int rowIndex) {
            this.spec = spec;
            this.bounds = bounds;
            this.rowIndex = rowIndex;
        }
    }

    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specialTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private final RectF tempRect = new RectF();
    private final GradientDrawable previewBgDrawable = new GradientDrawable();
    private final GradientDrawable alternatesBgDrawable = new GradientDrawable();
    private final GradientDrawable alternateSelectedBgDrawable = new GradientDrawable();

    private final List<PlacedKey> placedKeys = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final ExecutorService PHOTO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int MAX_BG_PHOTO_DIM_PX = 1080;

    private KeyboardThemePalette palette;
    @Nullable private Bitmap photoBitmap = null;
    @Nullable private String currentPhotoUri = null;
    private int photoRequestToken = 0;
    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect photoSrcRect = new Rect();
    private final Rect photoDstRect = new Rect();
    private KeyboardPage currentPage = KeyboardPage.QWERTY;
    private KeyboardLayoutType layoutType = KeyboardLayoutType.QWERTY_VIET;
    private ShiftState shiftState = ShiftState.OFF;
    private boolean showNumberRow = false;
    private String spaceLabel = "AI Keyboard";
    private int imeOptions = EditorInfo.IME_ACTION_NONE;
    private float heightScale = 1.0f;
    private boolean floatingMode = false;

    // Cached pixel dimensions and FontMetrics offsets to avoid allocations/lookups per draw/touch
    private float pxKeyRadius;
    private float pxKeyShadowOffset;
    private float pxIconStrokeWidth;
    private float pxCharSizeLarge;
    private float pxCharOffsetLarge;
    private float pxCharSizeMed;
    private float pxCharOffsetMed;
    private float pxCharSizeSmall;
    private float pxCharOffsetSmall;
    private float pxSpecialCompactSize;
    private float pxSpecialCompactOffset;
    private float pxSpecialNormalSize;
    private float pxSpecialNormalOffset;
    private float pxSpaceCompactSize;
    private float pxSpaceCompactOffset;
    private float pxSpaceNormalSize;
    private float pxSpaceNormalOffset;
    private float pxHintCompactSize;
    private float pxHintNormalSize;
    private float pxMinCharWMed;
    private float pxMinCharHMed;
    private float pxMinCharWSmall;
    private float pxMinCharHSmall;
    private float pxMinHintH;
    private float pxCompactKeyH;
    private float pxIconBaseH;
    private float pxHintRightPad;
    private float pxHintTopCompact;
    private float pxHintTopNormal;
    private float pxKeyHitThresholdSq;
    private float pxSpaceSwipeThreshold;
    private float pxDp1;
    private float pxDp2;
    private float pxDp3;
    private float pxDp3_3;
    private float pxDp4;
    private float pxDp4_2;
    private float pxDp4_5;
    private float pxDp4_8;
    private float pxDp5_5;
    private float pxDp7;
    private float pxDp7_5;
    private float pxDp8;
    private float pxDp8_5;
    private float pxDp10_5;
    private float pxDp1_2;

    @Nullable private OnKeyboardActionListener listener;
    @Nullable private PlacedKey activeKey = null;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private float downX = 0f;
    private boolean spaceSwipeHandled = false;
    private long lastShiftTapMs = 0L;
    private boolean longPressTriggered = false;
    private int backspaceRepeatCount = 0;

    @Nullable private PopupWindow previewPopup;
    @Nullable private TextView previewTextView;
    @Nullable private PopupWindow alternatesPopup;
    private final List<TextView> alternateViews = new ArrayList<>();
    private List<String> currentAlternates = new ArrayList<>();
    private int selectedAlternateIndex = 0;

    private final Runnable backspaceRepeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeKey != null && activeKey.spec.action == KeyAction.BACKSPACE) {
                backspaceRepeatCount++;
                boolean wordDelete = backspaceRepeatCount > 18;
                if (listener != null) {
                    listener.onKeyFeedback(KeyAction.BACKSPACE);
                    listener.onBackspace(wordDelete);
                }
                handler.postDelayed(this, wordDelete ? 95L : 55L);
            }
        }
    };

    private final Runnable longPressRunnable = () -> {
        if (activeKey == null) return;
        if (activeKey.spec.action == KeyAction.BACKSPACE) {
            longPressTriggered = true;
            backspaceRepeatCount = 0;
            handler.post(backspaceRepeatRunnable);
        } else if (activeKey.spec.action == KeyAction.SPACE) {
            longPressTriggered = true;
            dismissPreview();
            if (listener != null) {
                listener.onKeyFeedback(KeyAction.SPACE);
                listener.onSpaceLongPress();
            }
        } else if (activeKey.spec.action == KeyAction.CHARACTER && !activeKey.spec.alternates.isEmpty()) {
            longPressTriggered = true;
            dismissPreview();
            showAlternatesPopup(activeKey);
        }
    };

    /** Creates a keyboard surface view programmatically. */
    public KeyboardCanvasView(Context context) {
        this(context, null);
    }

    /** Creates a keyboard surface view with optional XML attributes. */
    public KeyboardCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    /** Initializes reusable paints and drawables outside the draw loop. */
    private void initPaints() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        specialTextPaint.setTextAlign(Paint.Align.CENTER);
        specialTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        hintPaint.setTextAlign(Paint.Align.RIGHT);
        hintPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);

        dotPaint.setStyle(Paint.Style.FILL);
        cacheMetrics();
    }

    private void cacheMetrics() {
        pxKeyRadius = dp(7f);
        pxKeyShadowOffset = dp(1.4f);
        pxIconStrokeWidth = dp(1.9f);
        iconPaint.setStrokeWidth(pxIconStrokeWidth);
        previewBgDrawable.setCornerRadius(dp(10f));
        alternatesBgDrawable.setCornerRadius(dp(12f));
        alternateSelectedBgDrawable.setCornerRadius(dp(8f));

        pxCharSizeLarge = sp(21f);
        textPaint.setTextSize(pxCharSizeLarge);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        pxCharOffsetLarge = -(fm.ascent + fm.descent) / 2f;

        pxCharSizeMed = sp(18f);
        textPaint.setTextSize(pxCharSizeMed);
        fm = textPaint.getFontMetrics();
        pxCharOffsetMed = -(fm.ascent + fm.descent) / 2f;

        pxCharSizeSmall = sp(15.5f);
        textPaint.setTextSize(pxCharSizeSmall);
        fm = textPaint.getFontMetrics();
        pxCharOffsetSmall = -(fm.ascent + fm.descent) / 2f;

        pxSpecialCompactSize = sp(12f);
        specialTextPaint.setTextSize(pxSpecialCompactSize);
        fm = specialTextPaint.getFontMetrics();
        pxSpecialCompactOffset = -(fm.ascent + fm.descent) / 2f;

        pxSpecialNormalSize = sp(14f);
        specialTextPaint.setTextSize(pxSpecialNormalSize);
        fm = specialTextPaint.getFontMetrics();
        pxSpecialNormalOffset = -(fm.ascent + fm.descent) / 2f;

        pxSpaceCompactSize = sp(11f);
        specialTextPaint.setTextSize(pxSpaceCompactSize);
        fm = specialTextPaint.getFontMetrics();
        pxSpaceCompactOffset = -(fm.ascent + fm.descent) / 2f;

        pxSpaceNormalSize = sp(12.5f);
        specialTextPaint.setTextSize(pxSpaceNormalSize);
        fm = specialTextPaint.getFontMetrics();
        pxSpaceNormalOffset = -(fm.ascent + fm.descent) / 2f;

        pxHintCompactSize = sp(8.5f);
        pxHintNormalSize = sp(10f);

        pxMinCharWMed = dp(34f);
        pxMinCharHMed = dp(40f);
        pxMinCharWSmall = dp(27f);
        pxMinCharHSmall = dp(32f);
        pxMinHintH = dp(28f);
        pxCompactKeyH = dp(38f);
        pxIconBaseH = dp(44f);
        pxHintRightPad = dp(3.5f);
        pxHintTopCompact = dp(9.5f);
        pxHintTopNormal = dp(12f);
        float hitRadius = dp(18f);
        pxKeyHitThresholdSq = hitRadius * hitRadius;
        pxSpaceSwipeThreshold = dp(42f);

        pxDp1 = dp(1f);
        pxDp1_2 = dp(1.2f);
        pxDp2 = dp(2f);
        pxDp3 = dp(3f);
        pxDp3_3 = dp(3.3f);
        pxDp4 = dp(4f);
        pxDp4_2 = dp(4.2f);
        pxDp4_5 = dp(4.5f);
        pxDp4_8 = dp(4.8f);
        pxDp5_5 = dp(5.5f);
        pxDp7 = dp(7f);
        pxDp7_5 = dp(7.5f);
        pxDp8 = dp(8f);
        pxDp8_5 = dp(8.5f);
        pxDp10_5 = dp(10.5f);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        cacheMetrics();
    }

    /** Registers the listener that handles committed keystrokes and navigation actions. */
    public void setOnKeyboardActionListener(@Nullable OnKeyboardActionListener listener) {
        this.listener = listener;
    }

    /** Applies a resolved theme palette and triggers a redraw if colors changed. */
    public void setPalette(KeyboardThemePalette palette) {
        this.palette = palette;
        setBackgroundColor(palette.getKeyboardBackground());
        invalidate();
    }

    /** Sets a personal photo background from URI asynchronously with safe downsampling. */
    public void setPhotoBackground(@Nullable String uriString, float opacity) {
        if (uriString == null || uriString.isEmpty()) {
            clearPhotoBackground();
            return;
        }
        int alpha = Math.round(Math.max(0.1f, Math.min(1.0f, opacity)) * 255f);
        if (photoPaint.getAlpha() != alpha) {
            photoPaint.setAlpha(alpha);
            invalidate();
        }
        if (uriString.equals(currentPhotoUri)) {
            return;
        }
        currentPhotoUri = uriString;
        final int token = ++photoRequestToken;
        final Context appContext = getContext().getApplicationContext();
        final Context safeCtx = appContext != null ? appContext : getContext();

        PHOTO_EXECUTOR.execute(() -> {
            Bitmap decoded = decodeDownsampledBitmap(safeCtx, uriString);
            handler.post(() -> {
                if (token == photoRequestToken && uriString.equals(currentPhotoUri)) {
                    photoBitmap = decoded;
                    invalidate();
                }
            });
        });
    }

    @Nullable
    private static Bitmap decodeDownsampledBitmap(Context context, String uriString) {
        try {
            Uri uri = Uri.parse(uriString);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsStream = context.getContentResolver().openInputStream(uri)) {
                if (boundsStream == null) return null;
                BitmapFactory.decodeStream(boundsStream, null, bounds);
            }
            int sampleSize = 1;
            while (bounds.outWidth / sampleSize > MAX_BG_PHOTO_DIM_PX
                || bounds.outHeight / sampleSize > MAX_BG_PHOTO_DIM_PX) {
                sampleSize *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream decodeStream = context.getContentResolver().openInputStream(uri)) {
                if (decodeStream == null) return null;
                return BitmapFactory.decodeStream(decodeStream, null, opts);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Clears custom photo background and restores solid background. */
    public void clearPhotoBackground() {
        if (currentPhotoUri == null && photoBitmap == null) return;
        photoRequestToken++;
        currentPhotoUri = null;
        photoBitmap = null;
        invalidate();
    }

    /**
     * Updates active page, language layout, shift state, Spacebar label, IME action icon, and scaling.
     */
    public void setKeyboardConfig(
        KeyboardPage page,
        KeyboardLayoutType layoutType,
        ShiftState shift,
        boolean showNumberRow,
        String spaceLabel,
        int imeOptions,
        float heightScale,
        boolean floatingMode
    ) {
        boolean layoutChanged =
            this.currentPage != page ||
            this.layoutType != layoutType ||
            this.showNumberRow != showNumberRow ||
            this.floatingMode != floatingMode ||
            Math.abs(this.heightScale - heightScale) > 0.01f;

        this.currentPage = page;
        this.layoutType = layoutType;
        this.shiftState = shift;
        this.showNumberRow = showNumberRow;
        this.spaceLabel = spaceLabel;
        this.imeOptions = imeOptions;
        this.heightScale = heightScale;
        this.floatingMode = floatingMode;

        if (layoutChanged) {
            requestLayout();
            layoutKeys(getWidth(), getHeight());
        }
        invalidate();
    }

    /** Returns the currently displayed keyboard page (QWERTY, Symbols, or Numeric). */
    public KeyboardPage getCurrentPage() {
        return currentPage;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        boolean landscape = isLandscape();
        boolean fiveRows = showNumberRow && currentPage == KeyboardPage.QWERTY;
        int rowCount = fiveRows ? 5 : 4;

        float baseRowDp;
        if (floatingMode) {
            baseRowDp = landscape ? (fiveRows ? 27f : 32f) : (fiveRows ? 35f : 42f);
        } else if (landscape) {
            baseRowDp = fiveRows ? 29f : 34f;
        } else {
            baseRowDp = fiveRows ? 43f : 52f;
        }
        float padTotalDp = (landscape || floatingMode) ? 9f : 14f;
        float totalDp = (rowCount * baseRowDp + padTotalDp) * heightScale;
        int desiredHeight = Math.round(dp(totalDp));

        int screenHeightPx = getResources().getDisplayMetrics().heightPixels;
        if (landscape && !floatingMode && screenHeightPx > 0) {
            desiredHeight = Math.min(desiredHeight, Math.round(screenHeightPx * 0.42f));
        } else if (floatingMode && screenHeightPx > 0) {
            desiredHeight = Math.min(desiredHeight, Math.round(screenHeightPx * 0.45f));
        }
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    private void layoutKeys(int width, int height) {
        placedKeys.clear();
        if (width <= 0 || height <= 0) return;

        List<List<KeySpec>> rows = KeyboardLayoutSpec.buildRows(currentPage, showNumberRow, layoutType);
        int rowCount = rows.size();
        boolean landscape = isLandscape();
        boolean compact = landscape || floatingMode;

        // Constrain overly wide tablet/landscape docked surfaces so keys remain thumb-friendly
        float maxRowWidth = (!floatingMode && width > dp(680f)) ? dp(640f) : width;
        float sideOffset = (width - maxRowWidth) / 2f;

        float padH = sideOffset + dp(floatingMode ? 4f : 5f);
        float padTop = dp(compact ? 4f : 6f);
        float padBottom = dp(compact ? 5f : 8f);
        float rowGap = dp(compact ? 4.5f : 7f);
        float colGap = dp(floatingMode ? 3.5f : 4.5f);

        float availableHeight = height - padTop - padBottom - (rowCount - 1) * rowGap;
        float rowHeight = Math.max(dp(22f), availableHeight / rowCount);

        for (int r = 0; r < rowCount; r++) {
            List<KeySpec> row = rows.get(r);
            float totalWeight = 0f;
            for (KeySpec k : row) totalWeight += k.weight;

            // Indent 9-key middle letter row only for 9-key rows
            float extraRowInset = 0f;
            if (currentPage == KeyboardPage.QWERTY && row.size() == 9 && row.get(0).action == KeyAction.CHARACTER) {
                extraRowInset = (width - 2f * padH) * 0.048f;
            }

            float rowWidth = width - 2f * padH - 2f * extraRowInset - (row.size() - 1) * colGap;
            float x = padH + extraRowInset;
            float y = padTop + r * (rowHeight + rowGap);

            for (KeySpec k : row) {
                float kw = rowWidth * (k.weight / totalWeight);
                placedKeys.add(new PlacedKey(k, new RectF(x, y, x + kw, y + rowHeight), r));
                x += kw + colGap;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (palette == null) return;

        if (photoBitmap != null && !photoBitmap.isRecycled()) {
            int bw = photoBitmap.getWidth();
            int bh = photoBitmap.getHeight();
            int vw = getWidth();
            int vh = getHeight();
            if (vw > 0 && vh > 0 && bw > 0 && bh > 0) {
                float scale = Math.max((float) vw / bw, (float) vh / bh);
                int scaledW = (int) (bw * scale);
                int scaledH = (int) (bh * scale);
                int left = (vw - scaledW) / 2;
                int top = (vh - scaledH) / 2;
                photoDstRect.set(left, top, left + scaledW, top + scaledH);
                photoSrcRect.set(0, 0, bw, bh);
                canvas.drawBitmap(photoBitmap, photoSrcRect, photoDstRect, photoPaint);
            }
        }

        float radius = pxKeyRadius;
        float shadowOffset = pxKeyShadowOffset;

        for (int i = 0, count = placedKeys.size(); i < count; i++) {
            PlacedKey pk = placedKeys.get(i);
            boolean isPressed = activeKey == pk;
            KeySpec spec = pk.spec;
            RectF rect = pk.bounds;

            // Shadow
            if (!isPressed && palette.getKeyShadow() != 0) {
                shadowPaint.setColor(palette.getKeyShadow());
                tempRect.set(rect.left, rect.top + shadowOffset, rect.right, rect.bottom + shadowOffset);
                canvas.drawRoundRect(tempRect, radius, radius, shadowPaint);
            }

            // Key background
            int bgColor;
            if (spec.action == KeyAction.ENTER) {
                bgColor = isPressed ? palette.getKeyPressedBackground() : palette.getEnterKeyBackground();
            } else if (spec.action == KeyAction.SHIFT && shiftState == ShiftState.CAPS_LOCK) {
                bgColor = palette.getEnterKeyBackground();
            } else if (isPressed) {
                bgColor = palette.getKeyPressedBackground();
            } else if (spec.action == KeyAction.CHARACTER || spec.action == KeyAction.SPACE) {
                bgColor = palette.getKeyBackground();
            } else {
                bgColor = palette.getSpecialKeyBackground();
            }

            keyPaint.setColor(bgColor);
            canvas.drawRoundRect(rect, radius, radius, keyPaint);

            // Content
            drawKeyContent(canvas, pk);
        }
    }

    private void drawKeyContent(Canvas canvas, PlacedKey pk) {
        KeySpec spec = pk.spec;
        RectF r = pk.bounds;
        float cx = r.centerX();
        float cy = r.centerY();
        float keyH = r.height();
        float iconScale = Math.max(0.72f, Math.min(1.0f, keyH / pxIconBaseH));
        boolean compactHeight = keyH < pxCompactKeyH;

        switch (spec.action) {
            case SHIFT: {
                int color = shiftState == ShiftState.CAPS_LOCK
                    ? palette.getEnterKeyText()
                    : (shiftState == ShiftState.SINGLE ? palette.getAccent() : palette.getKeyText());
                drawShiftIcon(canvas, cx, cy, color, iconScale);
                break;
            }
            case BACKSPACE: {
                drawBackspaceIcon(canvas, cx, cy, palette.getKeyText(), iconScale);
                break;
            }
            case ENTER: {
                drawEnterIcon(canvas, cx, cy, palette.getEnterKeyText(), iconScale);
                break;
            }
            case EMOJI_OR_GLOBE: {
                drawEmojiIcon(canvas, cx, cy, palette.getKeyText(), iconScale);
                break;
            }
            case SPACE: {
                specialTextPaint.setColor(palette.getKeyHintText());
                boolean useCompact = compactHeight || floatingMode;
                specialTextPaint.setTextSize(useCompact ? pxSpaceCompactSize : pxSpaceNormalSize);
                float textY = cy + (useCompact ? pxSpaceCompactOffset : pxSpaceNormalOffset);
                canvas.drawText(spaceLabel, cx, textY, specialTextPaint);
                break;
            }
            case MODE_SYMBOLS:
            case MODE_ALPHA:
            case MODE_SYMBOLS_MORE: {
                specialTextPaint.setColor(palette.getKeyText());
                boolean useCompact = compactHeight || floatingMode;
                specialTextPaint.setTextSize(useCompact ? pxSpecialCompactSize : pxSpecialNormalSize);
                float textY = cy + (useCompact ? pxSpecialCompactOffset : pxSpecialNormalOffset);
                canvas.drawText(spec.label, cx, textY, specialTextPaint);
                break;
            }
            case CHARACTER:
            default: {
                String label = (shiftState != ShiftState.OFF && currentPage == KeyboardPage.QWERTY)
                    ? spec.shiftLabel
                    : spec.label;
                float textSizePx;
                float textOffsetPx;
                if (r.width() < pxMinCharWSmall || keyH < pxMinCharHSmall) {
                    textSizePx = pxCharSizeSmall;
                    textOffsetPx = pxCharOffsetSmall;
                } else if (r.width() < pxMinCharWMed || keyH < pxMinCharHMed) {
                    textSizePx = pxCharSizeMed;
                    textOffsetPx = pxCharOffsetMed;
                } else {
                    textSizePx = pxCharSizeLarge;
                    textOffsetPx = pxCharOffsetLarge;
                }
                textPaint.setTextSize(textSizePx);
                textPaint.setColor(palette.getKeyText());
                canvas.drawText(label, cx, cy + textOffsetPx, textPaint);

                if (!spec.hint.isEmpty() && keyH >= pxMinHintH) {
                    hintPaint.setTextSize((compactHeight || floatingMode) ? pxHintCompactSize : pxHintNormalSize);
                    hintPaint.setColor(palette.getKeyHintText());
                    canvas.drawText(
                        spec.hint,
                        r.right - pxHintRightPad,
                        r.top + (compactHeight ? pxHintTopCompact : pxHintTopNormal),
                        hintPaint
                    );
                }
                break;
            }
        }
    }

    private void drawShiftIcon(Canvas canvas, float cx, float cy, int color, float scale) {
        float s = pxDp8_5 * scale;
        iconPath.reset();
        iconPath.moveTo(cx, cy - s);
        iconPath.lineTo(cx + s, cy + s * 0.1f);
        iconPath.lineTo(cx + s * 0.45f, cy + s * 0.1f);
        iconPath.lineTo(cx + s * 0.45f, cy + s * 0.85f);
        iconPath.lineTo(cx - s * 0.45f, cy + s * 0.85f);
        iconPath.lineTo(cx - s * 0.45f, cy + s * 0.1f);
        iconPath.lineTo(cx - s, cy + s * 0.1f);
        iconPath.close();

        iconPaint.setColor(color);
        if (shiftState == ShiftState.OFF) {
            iconPaint.setStyle(Paint.Style.STROKE);
        } else {
            iconPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        }
        canvas.drawPath(iconPath, iconPaint);
        iconPaint.setStyle(Paint.Style.STROKE);

        if (shiftState == ShiftState.CAPS_LOCK) {
            canvas.drawLine(cx - s * 0.55f, cy + s * 1.18f, cx + s * 0.55f, cy + s * 1.18f, iconPaint);
        }
    }

    private void drawBackspaceIcon(Canvas canvas, float cx, float cy, int color, float scale) {
        float w = pxDp10_5 * scale;
        float h = pxDp7 * scale;
        iconPath.reset();
        iconPath.moveTo(cx - w, cy);
        iconPath.lineTo(cx - w * 0.4f, cy - h);
        iconPath.lineTo(cx + w, cy - h);
        iconPath.lineTo(cx + w, cy + h);
        iconPath.lineTo(cx - w * 0.4f, cy + h);
        iconPath.close();

        iconPaint.setColor(color);
        iconPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(iconPath, iconPaint);

        float xSize = pxDp3_3 * scale;
        float xCx = cx + w * 0.25f;
        canvas.drawLine(xCx - xSize, cy - xSize, xCx + xSize, cy + xSize, iconPaint);
        canvas.drawLine(xCx + xSize, cy - xSize, xCx - xSize, cy + xSize, iconPaint);
    }

    private void drawEnterIcon(Canvas canvas, float cx, float cy, int color, float scale) {
        int action = imeOptions & EditorInfo.IME_MASK_ACTION;
        iconPaint.setColor(color);
        iconPaint.setStyle(Paint.Style.STROKE);

        if (action == EditorInfo.IME_ACTION_SEARCH) {
            float r = pxDp5_5 * scale;
            float offset2 = pxDp2 * scale;
            float offset7_5 = pxDp7_5 * scale;
            canvas.drawCircle(cx - offset2, cy - offset2, r, iconPaint);
            canvas.drawLine(cx + offset2, cy + offset2, cx + offset7_5, cy + offset7_5, iconPaint);
        } else if (action == EditorInfo.IME_ACTION_SEND) {
            float s = pxDp8 * scale;
            iconPath.reset();
            iconPath.moveTo(cx - s, cy - s * 0.7f);
            iconPath.lineTo(cx + s, cy);
            iconPath.lineTo(cx - s, cy + s * 0.7f);
            iconPath.lineTo(cx - s * 0.45f, cy);
            iconPath.close();
            canvas.drawPath(iconPath, iconPaint);
        } else {
            // Return arrow (or Done check)
            if (action == EditorInfo.IME_ACTION_DONE) {
                float d2 = pxDp2 * scale;
                float d5_5 = pxDp5_5 * scale;
                iconPath.reset();
                iconPath.moveTo(cx - pxDp7 * scale, cy);
                iconPath.lineTo(cx - d2, cy + d5_5);
                iconPath.lineTo(cx + pxDp8 * scale, cy - d5_5);
                canvas.drawPath(iconPath, iconPaint);
            } else {
                float w = pxDp7_5 * scale;
                float h = pxDp4_5 * scale;
                float arrowArm = pxDp4 * scale;
                iconPath.reset();
                iconPath.moveTo(cx + w, cy - h);
                iconPath.lineTo(cx + w, cy + h * 0.5f);
                iconPath.lineTo(cx - w, cy + h * 0.5f);
                canvas.drawPath(iconPath, iconPaint);
                // Arrow head
                iconPath.reset();
                iconPath.moveTo(cx - w + arrowArm, cy + h * 0.5f - arrowArm);
                iconPath.lineTo(cx - w, cy + h * 0.5f);
                iconPath.lineTo(cx - w + arrowArm, cy + h * 0.5f + arrowArm);
                canvas.drawPath(iconPath, iconPaint);
            }
        }
    }

    /** Draws the smiley emoji switcher icon using pre-allocated paints. */
    private void drawEmojiIcon(Canvas canvas, float cx, float cy, int color, float scale) {
        iconPaint.setColor(color);
        iconPaint.setStyle(Paint.Style.STROKE);
        float r = pxDp8_5 * scale;
        canvas.drawCircle(cx, cy, r, iconPaint);
        dotPaint.setColor(color);
        float eyeX = pxDp3 * scale;
        float eyeY = pxDp2 * scale;
        float eyeR = pxDp1_2 * scale;
        canvas.drawCircle(cx - eyeX, cy - eyeY, eyeR, dotPaint);
        canvas.drawCircle(cx + eyeX, cy - eyeY, eyeR, dotPaint);
        float smileW = pxDp4_2 * scale;
        tempRect.set(cx - smileW, cy - pxDp1 * scale, cx + smileW, cy + pxDp4_8 * scale);
        canvas.drawArc(tempRect, 15f, 150f, false, iconPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int idx = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Commit any previous active key immediately for fast multi-finger roll typing
                if (activeKey != null && !longPressTriggered && !spaceSwipeHandled) {
                    commitKeyTap(activeKey);
                    cancelPendingTimers();
                }
                activePointerId = event.getPointerId(idx);
                float x = event.getX(idx);
                float y = event.getY(idx);
                downX = x;
                spaceSwipeHandled = false;
                PlacedKey hit = findKeyAt(x, y);
                activeKey = hit;
                longPressTriggered = false;
                if (hit != null) {
                    if (hit.spec.action == KeyAction.CHARACTER && hit.spec.label.length() == 1) {
                        showKeyPreview(hit);
                    }
                    scheduleLongPress(hit);
                }
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                int pIdx = event.findPointerIndex(activePointerId);
                if (pIdx < 0) return true;
                float x = event.getX(pIdx);
                float y = event.getY(pIdx);

                if (alternatesPopup != null && alternatesPopup.isShowing() && activeKey != null) {
                    updateAlternateSelection(x, activeKey);
                    return true;
                }

                // Gboard-style spacebar horizontal swipe to switch language
                if (activeKey != null && activeKey.spec.action == KeyAction.SPACE && !longPressTriggered) {
                    float dx = x - downX;
                    if (!spaceSwipeHandled && Math.abs(dx) >= pxSpaceSwipeThreshold) {
                        spaceSwipeHandled = true;
                        cancelPendingTimers();
                        if (listener != null) {
                            listener.onKeyFeedback(KeyAction.SPACE);
                            listener.onSwipeSpaceLanguage(dx > 0 ? 1 : -1);
                        }
                    }
                    return true;
                }

                if (!longPressTriggered && activeKey != null && !activeKey.bounds.contains(x, y)) {
                    PlacedKey newHit = findKeyAt(x, y);
                    if (newHit != null && newHit != activeKey) {
                        cancelPendingTimers();
                        dismissPreview();
                        activeKey = newHit;
                        if (newHit.spec.action == KeyAction.CHARACTER) {
                            showKeyPreview(newHit);
                        }
                        scheduleLongPress(newHit);
                        invalidate();
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                if (event.getPointerId(idx) != activePointerId) return true;
                cancelPendingTimers();
                dismissPreview();

                if (alternatesPopup != null && alternatesPopup.isShowing()) {
                    if (!currentAlternates.isEmpty() && selectedAlternateIndex >= 0 && selectedAlternateIndex < currentAlternates.size()) {
                        String chosen = currentAlternates.get(selectedAlternateIndex);
                        if (listener != null) {
                            listener.onKeyFeedback(activeKey != null ? activeKey.spec.action : KeyAction.CHARACTER);
                            listener.onCharacter(applyShiftForAlternate(chosen));
                        }
                    }
                    dismissAlternatesPopup();
                } else if (activeKey != null && !longPressTriggered && !spaceSwipeHandled) {
                    commitKeyTap(activeKey);
                }

                activeKey = null;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                spaceSwipeHandled = false;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                cancelPendingTimers();
                dismissPreview();
                dismissAlternatesPopup();
                activeKey = null;
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void scheduleLongPress(PlacedKey hit) {
        if (hit.spec.action == KeyAction.BACKSPACE ||
            hit.spec.action == KeyAction.SPACE ||
            (hit.spec.action == KeyAction.CHARACTER && !hit.spec.alternates.isEmpty())) {
            handler.postDelayed(longPressRunnable, 380L);
        }
    }

    private void cancelPendingTimers() {
        handler.removeCallbacks(longPressRunnable);
        handler.removeCallbacks(backspaceRepeatRunnable);
    }

    private void commitKeyTap(PlacedKey pk) {
        if (listener == null) return;
        KeySpec spec = pk.spec;
        listener.onKeyFeedback(spec.action);

        switch (spec.action) {
            case CHARACTER: {
                listener.onCharacter(applyShiftForSpec(spec));
                break;
            }
            case SHIFT: {
                long now = System.currentTimeMillis();
                boolean doubleTap = (now - lastShiftTapMs) < 350L;
                lastShiftTapMs = now;
                listener.onShiftToggle(doubleTap);
                break;
            }
            case BACKSPACE:
                listener.onBackspace(false);
                break;
            case SPACE:
                listener.onSpace();
                break;
            case ENTER:
                listener.onEnter();
                break;
            case EMOJI_OR_GLOBE:
                listener.onOpenEmoji();
                break;
            case MODE_SYMBOLS:
                listener.onPageChanged(KeyboardPage.SYMBOLS_1);
                break;
            case MODE_SYMBOLS_MORE:
                listener.onPageChanged(KeyboardPage.SYMBOLS_2);
                break;
            case MODE_ALPHA:
                listener.onPageChanged(KeyboardPage.QWERTY);
                break;
        }
    }

    private String applyShiftForSpec(KeySpec spec) {
        if (shiftState != ShiftState.OFF && currentPage == KeyboardPage.QWERTY) {
            return spec.shiftLabel;
        }
        return spec.label;
    }

    private String applyShiftForAlternate(String ch) {
        if (shiftState != ShiftState.OFF && currentPage == KeyboardPage.QWERTY) {
            return ch.toUpperCase();
        }
        return ch;
    }

    @Nullable
    private PlacedKey findKeyAt(float x, float y) {
        for (int i = 0, count = placedKeys.size(); i < count; i++) {
            PlacedKey pk = placedKeys.get(i);
            if (pk.bounds.contains(x, y)) return pk;
        }
        // Nearest key fallback inside padding gaps
        PlacedKey nearest = null;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0, count = placedKeys.size(); i < count; i++) {
            PlacedKey pk = placedKeys.get(i);
            float dx = Math.max(0f, Math.max(pk.bounds.left - x, x - pk.bounds.right));
            float dy = Math.max(0f, Math.max(pk.bounds.top - y, y - pk.bounds.bottom));
            float dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                nearest = pk;
            }
        }
        return bestDist <= pxKeyHitThresholdSq ? nearest : null;
    }

    /** Displays a floating magnified character preview above the currently pressed key. */
    private void showKeyPreview(PlacedKey pk) {
        if (palette == null || getWindowToken() == null) return;
        if (previewTextView == null) {
            previewTextView = new TextView(getContext());
            previewTextView.setGravity(Gravity.CENTER);
            previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
            previewTextView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        previewBgDrawable.setColor(palette.getPopupBackground());
        previewBgDrawable.setStroke(Math.round(dp(1f)), palette.getKeyDivider());
        previewTextView.setBackground(previewBgDrawable);
        previewTextView.setTextColor(palette.getPopupText());
        previewTextView.setText(applyShiftForSpec(pk.spec));

        int w = Math.round(Math.max(pk.bounds.width() * 1.25f, dp(46f)));
        int h = Math.round(dp(56f));
        if (previewPopup == null) {
            previewPopup = new PopupWindow(previewTextView, w, h, false);
            previewPopup.setTouchable(false);
            previewPopup.setClippingEnabled(false);
        } else {
            previewPopup.setWidth(w);
            previewPopup.setHeight(h);
        }

        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0] + Math.round(pk.bounds.centerX() - w / 2f);
        int y = loc[1] + Math.round(pk.bounds.top - h - dp(4f));
        if (previewPopup.isShowing()) {
            previewPopup.update(x, y, w, h);
        } else {
            previewPopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
        }
    }

    private void dismissPreview() {
        if (previewPopup != null && previewPopup.isShowing()) {
            previewPopup.dismiss();
        }
    }

    /** Displays a long-press alternates popup strip for accented characters or symbols. */
    private void showAlternatesPopup(PlacedKey pk) {
        if (palette == null || getWindowToken() == null || pk.spec.alternates.isEmpty()) return;
        dismissAlternatesPopup();

        currentAlternates = new ArrayList<>(pk.spec.alternates);
        selectedAlternateIndex = 0;
        alternateViews.clear();

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Math.round(dp(4f));
        container.setPadding(pad, pad, pad, pad);

        alternatesBgDrawable.setColor(palette.getPopupBackground());
        alternatesBgDrawable.setStroke(Math.round(dp(1f)), palette.getKeyDivider());
        container.setBackground(alternatesBgDrawable);

        int itemW = Math.round(dp(40f));
        int itemH = Math.round(dp(44f));

        for (int i = 0; i < currentAlternates.size(); i++) {
            String alt = applyShiftForAlternate(currentAlternates.get(i));
            TextView tv = new TextView(getContext());
            tv.setText(alt);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
            tv.setLayoutParams(new LinearLayout.LayoutParams(itemW, itemH));
            styleAlternateItem(tv, i == 0);
            container.addView(tv);
            alternateViews.add(tv);
        }

        int totalW = itemW * currentAlternates.size() + pad * 2;
        int totalH = itemH + pad * 2;
        alternatesPopup = new PopupWindow(container, totalW, totalH, false);
        alternatesPopup.setTouchable(false);
        alternatesPopup.setClippingEnabled(false);

        int[] loc = new int[2];
        getLocationInWindow(loc);
        int x = loc[0] + Math.round(pk.bounds.centerX() - totalW / 2f);
        int minX = loc[0] + Math.round(dp(4f));
        int maxX = loc[0] + getWidth() - totalW - Math.round(dp(4f));
        if (maxX >= minX) {
            x = Math.max(minX, Math.min(x, maxX));
        }
        int y = loc[1] + Math.round(pk.bounds.top - totalH - dp(6f));
        alternatesPopup.showAtLocation(this, Gravity.NO_GRAVITY, x, y);
    }

    private void updateAlternateSelection(float touchX, PlacedKey anchor) {
        if (currentAlternates.isEmpty()) return;
        float itemW = dp(40f);
        float totalW = itemW * currentAlternates.size();
        float startX = Math.max(dp(6f), Math.min(anchor.bounds.centerX() - totalW / 2f, getWidth() - totalW - dp(6f)));
        int idx = (int) ((touchX - startX) / itemW);
        idx = Math.max(0, Math.min(idx, currentAlternates.size() - 1));
        if (idx != selectedAlternateIndex) {
            selectedAlternateIndex = idx;
            for (int i = 0; i < alternateViews.size(); i++) {
                styleAlternateItem(alternateViews.get(i), i == selectedAlternateIndex);
            }
        }
    }

    private void styleAlternateItem(TextView tv, boolean selected) {
        if (selected) {
            alternateSelectedBgDrawable.setColor(palette.getAccent());
            tv.setBackground(alternateSelectedBgDrawable);
            tv.setTextColor(palette.getEnterKeyText());
        } else {
            tv.setBackground(null);
            tv.setTextColor(palette.getPopupText());
        }
    }

    private void dismissAlternatesPopup() {
        if (alternatesPopup != null && alternatesPopup.isShowing()) {
            alternatesPopup.dismiss();
        }
        alternateViews.clear();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelPendingTimers();
        dismissPreview();
        dismissAlternatesPopup();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }
}
