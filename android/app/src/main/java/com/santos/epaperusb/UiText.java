package com.santos.epaperusb;

import android.content.Context;
import androidx.annotation.StringRes;

/** Resolve retained state against the CURRENT Activity locale, never the old locale. */
public final class UiText {
    private final int resourceId;
    private final Object[] args;
    private UiText(int resourceId, Object[] args) { this.resourceId = resourceId; this.args = args.clone(); }
    public static UiText of(@StringRes int resourceId, Object... args) { return new UiText(resourceId, args); }
    public static UiText join(UiText first, UiText second) { return new UiText(0, new Object[]{first, second}); }
    public int resourceId() { return resourceId; }
    public String resolve(Context context) {
        if (resourceId == 0) return ((UiText)args[0]).resolve(context) + "\n\n" + ((UiText)args[1]).resolve(context);
        return context.getString(resourceId, args);
    }
}
