package com.agy.ru;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import com.agy.util.AppFileProvider;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import org.json.JSONObject;

/** Native metadata and verified, user-confirmed APK installation for Hibou. */
public final class HibouUpdates {
    private static String read(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally { input.close(); }
    }
    private static JSONObject metadata(Context ctx) throws Exception {
        return new JSONObject(read(ctx.getAssets().open("web/build-info.json")));
    }
    public static String getAppInfo(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            JSONObject info = metadata(ctx);
            info.put("packageName", pi.packageName);
            info.put("versionName", pi.versionName);
            info.put("versionCode", pi.versionCode);
            return info.toString();
        } catch (Exception e) { return "{}"; }
    }
    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try { byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        } finally { in.close(); }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) out.append(String.format(Locale.US, "%02x", b & 255));
        return out.toString();
    }
    private static Set<String> signatures(PackageInfo info) {
        Set<String> out = new HashSet<String>();
        if (info.signatures != null) for (Signature sig : info.signatures) out.add(sig.toCharsString());
        return out;
    }
    public static String installUpdate(Context ctx) {
        try {
            File apk = new File(ctx.getFilesDir(), "updates/hibou-update.apk");
            File checksum = new File(ctx.getFilesDir(), "updates/hibou-update.sha256");
            if (!apk.isFile() || !checksum.isFile()) {
                apk = new File(ctx.getFilesDir(), "updates/leostryx-update.apk");
                checksum = new File(ctx.getFilesDir(), "updates/leostryx-update.sha256");
            }
            if (!apk.isFile() || !checksum.isFile()) return "error:missing_download";
            String expected = read(new FileInputStream(checksum)).trim();
            if (!expected.matches("[a-f0-9]{64}") || !expected.equals(sha256(apk))) return "error:checksum";
            PackageManager pm = ctx.getPackageManager();
            PackageInfo current = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo next = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            if (next == null || !current.packageName.equals(next.packageName)) return "error:package";
            if (next.versionCode <= current.versionCode) return "error:version";
            if (signatures(current).isEmpty() || !signatures(current).equals(signatures(next))) return "error:signature";
            ZipFile zip = new ZipFile(apk);
            try {
                JSONObject incoming = new JSONObject(read(zip.getInputStream(zip.getEntry("assets/web/build-info.json"))));
                if (!metadata(ctx).getString("variant").equals(incoming.getString("variant"))) return "error:variant";
            } finally { zip.close(); }
            if (Build.VERSION.SDK_INT >= 26 && !((Boolean)PackageManager.class.getMethod("canRequestPackageInstalls").invoke(pm)).booleanValue()) {
                Intent settings = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + ctx.getPackageName()));
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(settings);
                return "permission";
            }
            Uri uri = AppFileProvider.getUriForFile(ctx, apk);
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(install);
            return "installer";
        } catch (Exception e) { return "error:" + e.getClass().getSimpleName(); }
    }
}
