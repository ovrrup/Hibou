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
        (src / 'android/content/pm').mkdir(parents=True, exist_ok=True)
        (src / 'android/content/res').mkdir(parents=True, exist_ok=True)
        (src / 'android/net').mkdir(parents=True, exist_ok=True)
        (src / 'android/os').mkdir(parents=True, exist_ok=True)
        (src / 'org/json').mkdir(parents=True, exist_ok=True)
        (src / 'com/agy/util').mkdir(parents=True, exist_ok=True)
        
        (src / 'android/app').mkdir(parents=True, exist_ok=True)
        (src / 'android/provider').mkdir(parents=True, exist_ok=True)
        
        (src / 'android/app/NotificationManager.java').write_text(
            'package android.app;\n'
            'public class NotificationManager {\n'
            '    public boolean areNotificationsEnabled() { return true; }\n'
            '}\n'
        )
        (src / 'android/provider/Settings.java').write_text(
            'package android.provider;\n'
            'public class Settings {\n'
            '    public static final String ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS";\n'
            '}\n'
        )
        (src / 'android/os/Environment.java').write_text(
            'package android.os;\n'
            'public class Environment {\n'
            '    public static boolean isExternalStorageManager() { return true; }\n'
            '}\n'
        )
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
            '}\n'
        )
        
        (src / 'android/content/res/AssetManager.java').write_text(
            'package android.content.res;\n'
            'import java.io.InputStream;\n'
            'public class AssetManager {\n'
            '    public InputStream open(String filename) throws Exception { return null; }\n'
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
            'public abstract class Context {\n'
            '    public static final String NOTIFICATION_SERVICE = "notification";\n'
            '    public abstract String getPackageName();\n'
            '    public abstract PackageManager getPackageManager();\n'
            '    public abstract File getFilesDir();\n'
            '    public abstract AssetManager getAssets();\n'
            '    public abstract void startActivity(Intent intent);\n'
            '    public abstract int checkSelfPermission(String permission);\n'
            '    public abstract Object getSystemService(String name);\n'
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
