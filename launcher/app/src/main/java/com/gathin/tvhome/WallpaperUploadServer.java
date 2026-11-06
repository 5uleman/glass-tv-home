package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WallpaperUploadServer {
    private static final String TAG = "TvHome.UploadServer";

    public interface UploadListener {
        void onUploaded(File imageFile);
        default void onDeleted(File file) {}
        default void onSelected(File file) {}
        default void onSlideshowMode() {}
        default void onCleared() {}
    }

    public interface OnWallpaperUploadedListener extends UploadListener {}

    private final Context context;
    private final int port;
    private final UploadListener listener;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Thread serverThread;

    public WallpaperUploadServer(Context context, int port, UploadListener listener) {
        this.context = context.getApplicationContext();
        this.port = port;
        this.listener = listener;
    }

    public static String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            Collections.sort(interfaces, (a, b) -> {
                String an = a.getName().toLowerCase(Locale.ROOT);
                String bn = b.getName().toLowerCase(Locale.ROOT);
                if (an.startsWith("wlan") || an.startsWith("eth")) return -1;
                if (bn.startsWith("wlan") || bn.startsWith("eth")) return 1;
                return an.compareTo(bn);
            });
            for (NetworkInterface nif : interfaces) {
                if (nif.isLoopback() || !nif.isUp()) continue;
                List<InetAddress> addrs = Collections.list(nif.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("127.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error resolving IP: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    public synchronized boolean start() {
        if (running) return true;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(port));
            running = true;

            serverThread = new Thread(() -> {
                Log.i(TAG, "Server started on port " + port);
                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        new Thread(() -> handleClient(client), "upload-client").start();
                    } catch (Exception e) {
                        if (!running) break;
                        Log.w(TAG, "Accept error: " + e.getMessage());
                    }
                }
            }, "wallpaper-http-server");
            serverThread.setDaemon(true);
            serverThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start server on port " + port + ": " + e.getMessage());
            running = false;
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        Log.i(TAG, "Server stopped");
    }

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000); // 30 seconds timeout
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();

            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int prev3 = 0, prev2 = 0, prev1 = 0, b;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if (prev3 == '\r' && prev2 == '\n' && prev1 == '\r' && b == '\n') {
                    break;
                }
                prev3 = prev2;
                prev2 = prev1;
                prev1 = b;
            }

            String headerText = headerBuf.toString("UTF-8");
            String[] lines = headerText.split("\r\n");
            if (lines.length == 0 || lines[0].isEmpty()) {
                client.close();
                return;
            }

            String requestLine = lines[0];
            Log.d(TAG, "Request: " + requestLine);

            if (requestLine.startsWith("OPTIONS")) {
                String response = "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(response.getBytes("UTF-8"));
                out.flush();
            } else if (requestLine.startsWith("GET /thumb")) {
                String fileName = getQueryParam(requestLine, "file");
                serveThumbnail(fileName, out);
            } else if (requestLine.startsWith("GET /api/list")) {
                serveApiList(out);
            } else if (requestLine.startsWith("POST /api/select")) {
                String fileName = getQueryParam(requestLine, "file");
                handleApiSelect(fileName, out);
            } else if (requestLine.startsWith("POST /api/delete")) {
                String fileName = getQueryParam(requestLine, "file");
                handleApiDelete(fileName, out);
            } else if (requestLine.startsWith("POST /api/slideshow")) {
                handleApiSlideshow(out);
            } else if (requestLine.startsWith("POST /api/clear")) {
                handleApiClear(out);
            } else if (requestLine.startsWith("GET")) {
                byte[] pageBytes = getHtmlPage().getBytes("UTF-8");
                String response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: " + pageBytes.length + "\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(response.getBytes("UTF-8"));
                out.write(pageBytes);
                out.flush();
            } else if (requestLine.startsWith("POST /upload")) {
                long contentLength = 0;
                for (String line : lines) {
                    if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                        try {
                            contentLength = Long.parseLong(line.substring(15).trim());
                        } catch (Exception ignored) {}
                        break;
                    }
                }

                if (contentLength > 0 && contentLength < 60 * 1024 * 1024) { // Max 60 MB
                    File customDir = new File(context.getFilesDir(), "custom_wallpapers");
                    if (!customDir.exists()) customDir.mkdirs();

                    File targetFile = new File(customDir, "custom_" + System.currentTimeMillis() + ".jpg");
                    FileOutputStream fos = new FileOutputStream(targetFile);

                    byte[] buffer = new byte[8192];
                    long remaining = contentLength;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int r = in.read(buffer, 0, toRead);
                        if (r == -1) break;
                        fos.write(buffer, 0, r);
                        remaining -= r;
                    }
                    fos.flush();
                    fos.close();

                    String json = "{\"status\":\"ok\",\"file\":\"" + targetFile.getName() + "\"}";
                    byte[] jsonBytes = json.getBytes("UTF-8");
                    String response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json; charset=UTF-8\r\n" +
                            "Content-Length: " + jsonBytes.length + "\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Connection: close\r\n\r\n";
                    out.write(response.getBytes("UTF-8"));
                    out.write(jsonBytes);
                    out.flush();

                    if (listener != null && targetFile.exists() && targetFile.length() > 0) {
                        listener.onUploaded(targetFile);
                    }
                } else {
                    String error = "HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\nInvalid file size";
                    out.write(error.getBytes("UTF-8"));
                    out.flush();
                }
            } else {
                String notFound = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\nNot Found";
                out.write(notFound.getBytes("UTF-8"));
                out.flush();
            }

            out.close();
            in.close();
            client.close();
        } catch (Exception e) {
            Log.w(TAG, "Client handle exception: " + e.getMessage());
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private String getQueryParam(String requestLine, String param) {
        try {
            int qIdx = requestLine.indexOf('?');
            if (qIdx == -1) return "";
            int spIdx = requestLine.indexOf(' ', qIdx);
            String query = spIdx > qIdx ? requestLine.substring(qIdx + 1, spIdx) : requestLine.substring(qIdx + 1);
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals(param)) {
                    return URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void serveThumbnail(String fileName, OutputStream out) {
        try {
            File customDir = new File(context.getFilesDir(), "custom_wallpapers");
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "");
            File target = new File(customDir, safeName);
            if (!target.exists() || !target.isFile()) {
                String notFound = "HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n";
                out.write(notFound.getBytes("UTF-8"));
                out.flush();
                return;
            }

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(target.getAbsolutePath(), opts);

            int sample = 1;
            while (opts.outWidth / (sample * 2) >= 200 && opts.outHeight / (sample * 2) >= 150) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            Bitmap bmp = BitmapFactory.decodeFile(target.getAbsolutePath(), opts);
            if (bmp == null) {
                String err = "HTTP/1.1 500 Error\r\nConnection: close\r\n\r\n";
                out.write(err.getBytes("UTF-8"));
                out.flush();
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, baos);
            bmp.recycle();
            byte[] bytes = baos.toByteArray();

            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Cache-Control: max-age=3600\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(response.getBytes("UTF-8"));
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "Thumbnail error: " + e.getMessage());
        }
    }

    private void serveApiList(OutputStream out) {
        try {
            File customDir = new File(context.getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            JSONArray arr = new JSONArray();
            String activeSingle = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                    .getString("custom_single_file", "");
            boolean isSingleMode = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                    .getBoolean("custom_single_mode", false);

            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                SimpleDateFormat sdf = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());
                for (File f : files) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", f.getName());
                    double kb = f.length() / 1024.0;
                    if (kb > 1024) {
                        obj.put("size", String.format(Locale.ROOT, "%.1f MB", kb / 1024.0));
                    } else {
                        obj.put("size", String.format(Locale.ROOT, "%.0f KB", kb));
                    }
                    obj.put("date", sdf.format(new Date(f.lastModified())));
                    obj.put("active", isSingleMode && f.getName().equals(activeSingle));
                    arr.put(obj);
                }
            }

            byte[] jsonBytes = arr.toString().getBytes("UTF-8");
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: " + jsonBytes.length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(response.getBytes("UTF-8"));
            out.write(jsonBytes);
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "List API error: " + e.getMessage());
        }
    }

    private void handleApiSelect(String fileName, OutputStream out) {
        try {
            File customDir = new File(context.getFilesDir(), "custom_wallpapers");
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "");
            File target = new File(customDir, safeName);
            if (target.exists() && listener != null) {
                listener.onSelected(target);
            }
            sendSimpleJsonResponse(out, "{\"status\":\"ok\"}");
        } catch (Exception ignored) {}
    }

    private void handleApiDelete(String fileName, OutputStream out) {
        try {
            File customDir = new File(context.getFilesDir(), "custom_wallpapers");
            String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "");
            File target = new File(customDir, safeName);
            if (target.exists()) {
                target.delete();
                if (listener != null) listener.onDeleted(target);
            }
            sendSimpleJsonResponse(out, "{\"status\":\"ok\"}");
        } catch (Exception ignored) {}
    }

    private void handleApiSlideshow(OutputStream out) {
        try {
            if (listener != null) listener.onSlideshowMode();
            sendSimpleJsonResponse(out, "{\"status\":\"ok\"}");
        } catch (Exception ignored) {}
    }

    private void handleApiClear(OutputStream out) {
        try {
            File customDir = new File(context.getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            if (listener != null) listener.onCleared();
            sendSimpleJsonResponse(out, "{\"status\":\"ok\"}");
        } catch (Exception ignored) {}
    }

    private void sendSimpleJsonResponse(OutputStream out, String json) throws Exception {
        byte[] bytes = json.getBytes("UTF-8");
        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(response.getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
    }

    private String getHtmlPage() {
        return UploadPage.HTML;
    }
}
