#!/usr/bin/env python3
"""Generates tools/android-stubs.jar containing minimal Android API stubs for kotlinc."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent

def build_stubs():
    stubs_jar = ROOT / 'tools' / 'android-stubs.jar'
    with tempfile.TemporaryDirectory() as d:
        src = Path(d) / 'src'
        classes = Path(d) / 'classes'
        classes.mkdir(parents=True, exist_ok=True)
        
        # Package directories
        for pkg in [
            'android/content/pm', 'android/content/res', 'android/net', 'android/os',
            'android/app', 'android/provider', 'android/view', 'android/widget',
            'android/graphics', 'android/graphics/drawable', 'android/util',
            'org/json', 'com/agy/util'
        ]:
            (src / pkg).mkdir(parents=True, exist_ok=True)
        
        (src / 'org/json/JSONObject.java').write_text(
            'package org.json;\n'
            'public class JSONObject {\n'
            '    public JSONObject() {}\n'
            '    public JSONObject(String json) throws Exception {}\n'
            '    public JSONObject put(String name, Object val) throws Exception { return this; }\n'
            '    public JSONObject put(String name, int val) throws Exception { return this; }\n'
            '    public JSONObject put(String name, boolean val) throws Exception { return this; }\n'
            '    public String getString(String name) throws Exception { return ""; }\n'
            '    public String optString(String name, String def) { return def; }\n'
            '    public String optString(String name) { return ""; }\n'
            '    public Object get(String name) throws Exception { return null; }\n'
            '    public Object opt(String name) { return null; }\n'
            '    public boolean has(String name) { return false; }\n'
            '    public String toString() { return "{}"; }\n'
            '    public String toString(int indentSpaces) { return "{}"; }\n'
            '    public JSONObject optJSONObject(String name) { return null; }\n'
            '    public boolean optBoolean(String name, boolean def) { return def; }\n'
            '    public boolean optBoolean(String name) { return false; }\n'
            '}\n'
        )
        
        (src / 'android/content/res/AssetManager.java').write_text(
            'package android.content.res;\n'
            'import java.io.InputStream;\n'
            'public class AssetManager {\n'
            '    public InputStream open(String filename) throws Exception { return null; }\n'
            '}\n'
        )
        (src / 'android/util/DisplayMetrics.java').write_text(
            'package android.util;\n'
            'public class DisplayMetrics {\n'
            '    public float density = 1.0f;\n'
            '    public float scaledDensity = 1.0f;\n'
            '    public int widthPixels = 1080;\n'
            '    public int heightPixels = 2400;\n'
            '}\n'
        )
        (src / 'android/util/TypedValue.java').write_text(
            'package android.util;\n'
            'public class TypedValue {\n'
            '    public static final int COMPLEX_UNIT_DIP = 1;\n'
            '    public static final int COMPLEX_UNIT_SP = 2;\n'
            '    public static float applyDimension(int unit, float value, DisplayMetrics metrics) { return value * (metrics != null ? metrics.density : 1.0f); }\n'
            '}\n'
        )
        (src / 'android/content/res/Resources.java').write_text(
            'package android.content.res;\n'
            'import android.util.DisplayMetrics;\n'
            'public class Resources {\n'
            '    public DisplayMetrics getDisplayMetrics() { return new DisplayMetrics(); }\n'
            '    public static Resources getSystem() { return new Resources(); }\n'
            '}\n'
        )
        (src / 'android/content/pm/Signature.java').write_text(
            'package android.content.pm;\n'
            'public class Signature {\n'
            '    public String toCharsString() { return ""; }\n'
            '}\n'
        )
        (src / 'android/content/pm/PackageInfo.java').write_text(
            'package android.content.pm;\n'
            'public class PackageInfo {\n'
            '    public String packageName;\n'
            '    public String versionName;\n'
            '    public int versionCode;\n'
            '    public Signature[] signatures;\n'
            '}\n'
        )
        (src / 'android/content/pm/PackageManager.java').write_text(
            'package android.content.pm;\n'
            'public abstract class PackageManager {\n'
            '    public static final int GET_SIGNATURES = 64;\n'
            '    public static final int PERMISSION_GRANTED = 0;\n'
            '    public static final int PERMISSION_DENIED = -1;\n'
            '    public abstract PackageInfo getPackageInfo(String packageName, int flags) throws Exception;\n'
            '    public abstract PackageInfo getPackageArchiveInfo(String archiveFilePath, int flags);\n'
            '    public abstract int checkPermission(String permName, String pkgName);\n'
            '}\n'
        )
        (src / 'android/net/Uri.java').write_text(
            'package android.net;\n'
            'public class Uri {\n'
            '    public static Uri parse(String uriString) { return null; }\n'
            '}\n'
        )
        (src / 'android/os/Build.java').write_text(
            'package android.os;\n'
            'public class Build {\n'
            '    public static class VERSION {\n'
            '        public static final int SDK_INT = 34;\n'
            '    }\n'
            '}\n'
        )
        (src / 'android/os/Environment.java').write_text(
            'package android.os;\n'
            'public class Environment {\n'
            '    public static boolean isExternalStorageManager() { return true; }\n'
            '}\n'
        )
        (src / 'android/provider/Settings.java').write_text(
            'package android.provider;\n'
            'public class Settings {\n'
            '    public static final String ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS";\n'
            '}\n'
        )
        (src / 'android/app/NotificationManager.java').write_text(
            'package android.app;\n'
            'public class NotificationManager {\n'
            '    public boolean areNotificationsEnabled() { return true; }\n'
            '}\n'
        )
        (src / 'android/content/Intent.java').write_text(
            'package android.content;\n'
            'import android.net.Uri;\n'
            'public class Intent {\n'
            '    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;\n'
            '    public static final int FLAG_GRANT_READ_URI_PERMISSION = 1;\n'
            '    public static final String ACTION_VIEW = "android.intent.action.VIEW";\n'
            '    public Intent() {}\n'
            '    public Intent(String action) {}\n'
            '    public Intent(String action, Uri uri) {}\n'
            '    public Intent addFlags(int flags) { return this; }\n'
            '    public Intent setDataAndType(Uri data, String type) { return this; }\n'
            '    public Intent setData(Uri data) { return this; }\n'
            '    public Uri getData() { return null; }\n'
            '    public Intent putExtra(String name, String value) { return this; }\n'
            '}\n'
        )
        (src / 'android/content/Context.java').write_text(
            'package android.content;\n'
            'import java.io.File;\n'
            'import android.content.pm.PackageManager;\n'
            'import android.content.res.AssetManager;\n'
            'import android.content.res.Resources;\n'
            'public abstract class Context {\n'
            '    public static final String NOTIFICATION_SERVICE = "notification";\n'
            '    public static final String CLIPBOARD_SERVICE = "clipboard";\n'
            '    public abstract String getPackageName();\n'
            '    public abstract PackageManager getPackageManager();\n'
            '    public abstract File getFilesDir();\n'
            '    public abstract AssetManager getAssets();\n'
            '    public abstract Resources getResources();\n'
            '    public abstract void startActivity(Intent intent);\n'
            '    public abstract int checkSelfPermission(String permission);\n'
            '    public abstract Object getSystemService(String name);\n'
            '}\n'
        )
        (src / 'android/content/ClipData.java').write_text(
            'package android.content;\n'
            'public class ClipData {\n'
            '    public static ClipData newPlainText(CharSequence label, CharSequence text) { return new ClipData(); }\n'
            '}\n'
        )
        (src / 'android/content/ClipboardManager.java').write_text(
            'package android.content;\n'
            'public class ClipboardManager {\n'
            '    public void setPrimaryClip(ClipData clip) {}\n'
            '}\n'
        )
        (src / 'android/widget/Toast.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'public class Toast {\n'
            '    public static final int LENGTH_SHORT = 0;\n'
            '    public static final int LENGTH_LONG = 1;\n'
            '    public static Toast makeText(Context context, CharSequence text, int duration) { return new Toast(); }\n'
            '    public void show() {}\n'
            '}\n'
        )
        (src / 'android/graphics/Color.java').write_text(
            'package android.graphics;\n'
            'public class Color {\n'
            '    public static final int BLACK = 0xFF000000;\n'
            '    public static final int WHITE = 0xFFFFFFFF;\n'
            '    public static final int TRANSPARENT = 0;\n'
            '    public static int parseColor(String colorString) { return 0; }\n'
            '    public static int rgb(int red, int green, int blue) { return 0; }\n'
            '    public static int argb(int alpha, int red, int green, int blue) { return 0; }\n'
            '}\n'
        )
        (src / 'android/graphics/Typeface.java').write_text(
            'package android.graphics;\n'
            'public class Typeface {\n'
            '    public static final Typeface DEFAULT = null;\n'
            '    public static final Typeface DEFAULT_BOLD = null;\n'
            '    public static final Typeface SANS_SERIF = null;\n'
            '    public static final Typeface MONOSPACE = null;\n'
            '    public static final int NORMAL = 0;\n'
            '    public static final int BOLD = 1;\n'
            '    public static Typeface create(String familyName, int style) { return null; }\n'
            '}\n'
        )
        (src / 'android/graphics/drawable/Drawable.java').write_text(
            'package android.graphics.drawable;\n'
            'public abstract class Drawable {}\n'
        )
        (src / 'android/graphics/drawable/ColorDrawable.java').write_text(
            'package android.graphics.drawable;\n'
            'public class ColorDrawable extends Drawable {\n'
            '    public ColorDrawable(int color) {}\n'
            '}\n'
        )
        (src / 'android/R.java').write_text(
            'package android;\n'
            'public final class R {\n'
            '    public static final class id {\n'
            '        public static final int content = 0x01020002;\n'
            '    }\n'
            '}\n'
        )
        (src / 'android/graphics/drawable/GradientDrawable.java').write_text(
            'package android.graphics.drawable;\n'
            'public class GradientDrawable extends Drawable {\n'
            '    public static final int RECTANGLE = 0;\n'
            '    public static final int OVAL = 1;\n'
            '    public static final int LINEAR_GRADIENT = 0;\n'
            '    public static final int RADIAL_GRADIENT = 1;\n'
            '    public enum Orientation { TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR }\n'
            '    public GradientDrawable() {}\n'
            '    public GradientDrawable(Orientation orientation, int[] colors) {}\n'
            '    public int getShape() { return 0; }\n'
            '    public void setShape(int shape) {}\n'
            '    public float getCornerRadius() { return 0f; }\n'
            '    public void setCornerRadius(float radius) {}\n'
            '    public void setCornerRadii(float[] radii) {}\n'
            '    public void setColor(int argb) {}\n'
            '    public void setStroke(int width, int color) {}\n'
            '    public int getGradientType() { return 0; }\n'
            '    public void setGradientType(int gradient) {}\n'
            '    public float getGradientRadius() { return 0f; }\n'
            '    public void setGradientRadius(float gradientRadius) {}\n'
            '}\n'
        )
        (src / 'android/view/Gravity.java').write_text(
            'package android.view;\n'
            'public class Gravity {\n'
            '    public static final int NO_GRAVITY = 0;\n'
            '    public static final int TOP = 48;\n'
            '    public static final int BOTTOM = 80;\n'
            '    public static final int LEFT = 3;\n'
            '    public static final int RIGHT = 5;\n'
            '    public static final int CENTER_VERTICAL = 16;\n'
            '    public static final int CENTER_HORIZONTAL = 1;\n'
            '    public static final int CENTER = 17;\n'
            '    public static final int START = 0x00800003;\n'
            '    public static final int END = 0x00800005;\n'
            '}\n'
        )
        (src / 'android/view/ViewPropertyAnimator.java').write_text(
            'package android.view;\n'
            'public class ViewPropertyAnimator {\n'
            '    public ViewPropertyAnimator alpha(float value) { return this; }\n'
            '    public ViewPropertyAnimator translationY(float value) { return this; }\n'
            '    public ViewPropertyAnimator translationX(float value) { return this; }\n'
            '    public ViewPropertyAnimator scaleX(float value) { return this; }\n'
            '    public ViewPropertyAnimator scaleY(float value) { return this; }\n'
            '    public ViewPropertyAnimator setDuration(long duration) { return this; }\n'
            '    public ViewPropertyAnimator withEndAction(Runnable runnable) { return this; }\n'
            '    public ViewPropertyAnimator start() { return this; }\n'
            '}\n'
        )
        (src / 'android/view/View.java').write_text(
            'package android.view;\n'
            'import android.content.Context;\n'
            'import android.graphics.drawable.Drawable;\n'
            'public class View {\n'
            '    public static final int VISIBLE = 0;\n'
            '    public static final int INVISIBLE = 4;\n'
            '    public static final int GONE = 8;\n'
            '    public interface OnClickListener { void onClick(View v); }\n'
            '    public View(Context context) {}\n'
            '    public Context getContext() { return null; }\n'
            '    public void setVisibility(int visibility) {}\n'
            '    public int getVisibility() { return 0; }\n'
            '    public Drawable getBackground() { return null; }\n'
            '    public void setBackground(Drawable background) {}\n'
            '    public void setBackgroundColor(int color) {}\n'
            '    public void setLayoutParams(ViewGroup.LayoutParams params) {}\n'
            '    public ViewGroup.LayoutParams getLayoutParams() { return null; }\n'
            '    public void setOnClickListener(OnClickListener l) {}\n'
            '    public ViewPropertyAnimator animate() { return new ViewPropertyAnimator(); }\n'
            '    public void setPadding(int left, int top, int right, int bottom) {}\n'
            '    public float getTranslationY() { return 0f; }\n'
            '    public void setTranslationY(float translationY) {}\n'
            '    public float getTranslationX() { return 0f; }\n'
            '    public void setTranslationX(float translationX) {}\n'
            '    public float getAlpha() { return 1f; }\n'
            '    public void setAlpha(float alpha) {}\n'
            '    public float getScaleX() { return 1f; }\n'
            '    public void setScaleX(float scaleX) {}\n'
            '    public float getScaleY() { return 1f; }\n'
            '    public void setScaleY(float scaleY) {}\n'
            '    public Object getTag() { return null; }\n'
            '    public void setTag(Object tag) {}\n'
            '    public <T extends View> T findViewWithTag(Object tag) { return null; }\n'
            '    public <T extends View> T findViewById(int id) { return null; }\n'
            '    public ViewGroup getParent() { return null; }\n'
            '    public void bringToFront() {}\n'
            '    public boolean post(Runnable action) { return true; }\n'
            '    public boolean postDelayed(Runnable action, long delayMillis) { return true; }\n'
            '}\n'
        )
        (src / 'android/view/ViewGroup.java').write_text(
            'package android.view;\n'
            'import android.content.Context;\n'
            'public abstract class ViewGroup extends View {\n'
            '    public ViewGroup(Context context) { super(context); }\n'
            '    public void addView(View child) {}\n'
            '    public void addView(View child, LayoutParams params) {}\n'
            '    public void removeView(View child) {}\n'
            '    public void removeAllViews() {}\n'
            '    public static class LayoutParams {\n'
            '        public static final int MATCH_PARENT = -1;\n'
            '        public static final int WRAP_CONTENT = -2;\n'
            '        public int width;\n'
            '        public int height;\n'
            '        public LayoutParams(int width, int height) { this.width = width; this.height = height; }\n'
            '        public LayoutParams(LayoutParams source) {}\n'
            '    }\n'
            '    public static class MarginLayoutParams extends LayoutParams {\n'
            '        public int leftMargin, topMargin, rightMargin, bottomMargin;\n'
            '        public MarginLayoutParams(int width, int height) { super(width, height); }\n'
            '        public MarginLayoutParams(LayoutParams source) { super(source); }\n'
            '        public void setMargins(int left, int top, int right, int bottom) {}\n'
            '    }\n'
            '}\n'
        )
        (src / 'android/view/Window.java').write_text(
            'package android.view;\n'
            'import android.graphics.drawable.Drawable;\n'
            'public class Window {\n'
            '    public void setBackgroundDrawable(Drawable drawable) {}\n'
            '    public void setLayout(int width, int height) {}\n'
            '    public void setGravity(int gravity) {}\n'
            '    public View getDecorView() { return null; }\n'
            '    public <T extends View> T findViewById(int id) { return null; }\n'
            '}\n'
        )
        (src / 'android/app/Activity.java').write_text(
            'package android.app;\n'
            'import android.content.Context;\n'
            'import android.view.View;\n'
            'import android.view.Window;\n'
            'public class Activity extends Context {\n'
            '    public String getPackageName() { return ""; }\n'
            '    public android.content.pm.PackageManager getPackageManager() { return null; }\n'
            '    public java.io.File getFilesDir() { return null; }\n'
            '    public android.content.res.AssetManager getAssets() { return null; }\n'
            '    public android.content.res.Resources getResources() { return null; }\n'
            '    public void startActivity(android.content.Intent intent) {}\n'
            '    public int checkSelfPermission(String permission) { return 0; }\n'
            '    public Object getSystemService(String name) { return null; }\n'
            '    public <T extends View> T findViewById(int id) { return null; }\n'
            '    public void runOnUiThread(Runnable action) { if (action != null) action.run(); }\n'
            '    public void setContentView(View view) {}\n'
            '    public void finish() {}\n'
            '    public boolean isFinishing() { return false; }\n'
            '    public boolean isDestroyed() { return false; }\n'
            '    public Window getWindow() { return new Window(); }\n'
            '}\n'
        )
        (src / 'android/app/Dialog.java').write_text(
            'package android.app;\n'
            'import android.content.Context;\n'
            'import android.view.View;\n'
            'import android.view.Window;\n'
            'public class Dialog {\n'
            '    public Dialog(Context context) {}\n'
            '    public Dialog(Context context, int themeResId) {}\n'
            '    public void setContentView(View view) {}\n'
            '    public void show() {}\n'
            '    public void dismiss() {}\n'
            '    public boolean isShowing() { return false; }\n'
            '    public Window getWindow() { return new Window(); }\n'
            '}\n'
        )
        (src / 'android/widget/FrameLayout.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'import android.view.ViewGroup;\n'
            'public class FrameLayout extends ViewGroup {\n'
            '    public FrameLayout(Context context) { super(context); }\n'
            '    public static class LayoutParams extends ViewGroup.MarginLayoutParams {\n'
            '        public int gravity = -1;\n'
            '        public LayoutParams(int width, int height) { super(width, height); }\n'
            '        public LayoutParams(int width, int height, int gravity) { super(width, height); this.gravity = gravity; }\n'
            '        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }\n'
            '    }\n'
            '}\n'
        )
        (src / 'android/widget/LinearLayout.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'import android.view.ViewGroup;\n'
            'public class LinearLayout extends ViewGroup {\n'
            '    public static final int HORIZONTAL = 0;\n'
            '    public static final int VERTICAL = 1;\n'
            '    public LinearLayout(Context context) { super(context); }\n'
            '    public int getOrientation() { return 0; }\n'
            '    public void setOrientation(int orientation) {}\n'
            '    public int getGravity() { return 0; }\n'
            '    public void setGravity(int gravity) {}\n'
            '    public static class LayoutParams extends ViewGroup.MarginLayoutParams {\n'
            '        public float weight;\n'
            '        public int gravity = -1;\n'
            '        public LayoutParams(int width, int height) { super(width, height); }\n'
            '        public LayoutParams(int width, int height, float weight) { super(width, height); this.weight = weight; }\n'
            '        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }\n'
            '    }\n'
            '}\n'
        )
        (src / 'android/widget/TextView.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'import android.graphics.Typeface;\n'
            'import android.view.View;\n'
            'public class TextView extends View {\n'
            '    public TextView(Context context) { super(context); }\n'
            '    public void setText(CharSequence text) {}\n'
            '    public CharSequence getText() { return ""; }\n'
            '    public void setTextColor(int color) {}\n'
            '    public float getTextSize() { return 14f; }\n'
            '    public void setTextSize(float size) {}\n'
            '    public void setTextSize(int unit, float size) {}\n'
            '    public Typeface getTypeface() { return null; }\n'
            '    public void setTypeface(Typeface tf) {}\n'
            '    public int getGravity() { return 0; }\n'
            '    public void setGravity(int gravity) {}\n'
            '    public void setMaxLines(int maxLines) {}\n'
            '    public void setSingleLine(boolean singleLine) {}\n'
            '    public void setLines(int lines) {}\n'
            '}\n'
        )
        (src / 'android/widget/Button.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'public class Button extends TextView {\n'
            '    public Button(Context context) { super(context); }\n'
            '}\n'
        )
        (src / 'android/widget/ProgressBar.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'import android.view.View;\n'
            'public class ProgressBar extends View {\n'
            '    public ProgressBar(Context context) { super(context); }\n'
            '    public void setIndeterminate(boolean indeterminate) {}\n'
            '    public boolean isIndeterminate() { return true; }\n'
            '    public void setMax(int max) {}\n'
            '    public void setProgress(int progress) {}\n'
            '}\n'
        )
        (src / 'android/widget/ImageView.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'import android.graphics.drawable.Drawable;\n'
            'import android.view.View;\n'
            'public class ImageView extends View {\n'
            '    public ImageView(Context context) { super(context); }\n'
            '    public void setImageDrawable(Drawable drawable) {}\n'
            '}\n'
        )
        (src / 'android/widget/ScrollView.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'public class ScrollView extends FrameLayout {\n'
            '    public ScrollView(Context context) { super(context); }\n'
            '}\n'
        )
        (src / 'android/widget/EditText.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'public class EditText extends TextView {\n'
            '    public EditText(Context context) { super(context); }\n'
            '    public CharSequence getHint() { return ""; }\n'
            '    public void setHint(CharSequence hint) {}\n'
            '    public void setInputType(int type) {}\n'
            '}\n'
        )
        (src / 'android/widget/Switch.java').write_text(
            'package android.widget;\n'
            'import android.content.Context;\n'
            'public class Switch extends TextView {\n'
            '    public interface OnCheckedChangeListener {\n'
            '        void onCheckedChanged(Switch buttonView, boolean isChecked);\n'
            '    }\n'
            '    public Switch(Context context) { super(context); }\n'
            '    public void setChecked(boolean checked) {}\n'
            '    public boolean isChecked() { return false; }\n'
            '    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {}\n'
            '}\n'
        )
        (src / 'com/agy/util/AppFileProvider.java').write_text(
            'package com.agy.util;\n'
            'import android.content.Context;\n'
            'import android.net.Uri;\n'
            'import java.io.File;\n'
            'public class AppFileProvider {\n'
            '    public static Uri getUriForFile(Context context, File file) { return null; }\n'
            '}\n'
        )
        
        java_files = [str(p) for p in src.glob('**/*.java')]
        subprocess.run(['javac', '-d', str(classes)] + java_files, check=True)
        subprocess.run(['jar', 'cf', str(stubs_jar), '-C', str(classes), '.'], check=True)
        print(f"Generated {stubs_jar} ({stubs_jar.stat().st_size} bytes)")

if __name__ == '__main__':
    build_stubs()
