package cc.lubeiluchen.rhinelab;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 网页里「导出日志」的落盘端。
 *
 * 走 MediaStore 的 Downloads 集合，写进 Documents/RhineLab/：这条路径不需要任何权限
 * （应用只写自己创建的文件；读别人的才要）。所以 APK 里不需要目录选择器，
 * 也不要 MANAGE_EXTERNAL_STORAGE —— 代价是只能导出，不能读回外部改动。
 *
 * 页面通过 window.RhineLabExport.saveLog(name, text) 调用，返回写入后的相对路径，
 * 失败返回 "error: ..."。方法跑在 WebView 的 JavaBridge 线程上，不碰视图。
 */
public class ExportBridge {

    private static final String FOLDER = "RhineLab";
    private static final String MIME = "text/markdown";

    private final Context context;

    ExportBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    @JavascriptInterface
    public String saveLog(String name, String text) {
        String safe = sanitize(name);
        if (safe.isEmpty()) return "error: bad file name";
        // 优先 Documents/RhineLab（用户要的"自己的文件夹"）；个别机型只允许标准子目录，
        // 那就退回 Download/RhineLab，而不是让导出直接失败。
        String first = write(Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER, safe, text);
        if (first != null) return first;
        String second = write(Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER, safe, text);
        return second != null ? second : "error: 写入失败，见应用日志";
    }

    /** @return 相对路径（如 Documents/RhineLab/x.md），失败返回 null。 */
    private String write(String relativePath, String name, String text) {
        ContentResolver resolver = context.getContentResolver();
        try {
            // 同名旧文件（自己写的）先删掉，否则系统会给新文件加 " (1)" 后缀。
            // 别人写的同名文件删不掉，那是 SecurityException —— 单独吃掉，不能连累导出。
            try {
                resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[] {name, withTrailingSlash(relativePath)});
            } catch (Throwable error) {
                Trace.log("export: pre-delete skipped: " + error);
            }

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, MIME);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, withTrailingSlash(relativePath));
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Trace.log("export: insert returned null for " + relativePath);
                return null;
            }
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) {
                    Trace.log("export: openOutputStream returned null");
                    resolver.delete(uri, null, null);
                    return null;
                }
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            Trace.log("export: wrote " + relativePath + "/" + name + " " + text.length() + " chars");
            return relativePath + "/" + name;
        } catch (Throwable error) {
            Trace.log("export failed: " + error);
            return null;
        }
    }

    /** 文件名只允许我们自己拼的 yyyy-MM-dd.md，多余的路径分隔符一律去掉。 */
    private static String sanitize(String name) {
        if (name == null) return "";
        String trimmed = name.trim().replaceAll("[\\\\/]", "").replaceAll("\\.\\.", "");
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static String withTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }
}
