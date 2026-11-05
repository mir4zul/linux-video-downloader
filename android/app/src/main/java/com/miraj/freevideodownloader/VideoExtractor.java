package com.miraj.freevideodownloader;

import android.content.Context;
import android.content.ContentValues;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import dev.ffmpegkit_maintained.ytdlp.YtDlp;
import dev.ffmpegkit_maintained.ytdlp.YtDlpException;
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest;
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;

final class VideoExtractor {
    private VideoExtractor() { }

    static void initialize(Context context) throws YtDlpException {
        clearTemporaryFiles(context.getCacheDir());
        YtDlp.init(context.getApplicationContext());
        if (!Python.isStarted()) Python.start(new AndroidPlatform(context.getApplicationContext()));
    }

    private static void clearTemporaryFiles(File cache) {
        File[] workDirs = cache.listFiles((directory, name) -> name.startsWith("fvd-"));
        if (workDirs == null) return;
        for (File workDir : workDirs) deleteRecursively(workDir);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    static Analysis analyze(String pageUrl) throws Exception {
        PyObject ytDlp = Python.getInstance().getModule("yt_dlp");
        PyObject downloader = ytDlp.get("YoutubeDL").call();
        PyObject result = downloader.callAttr("extract_info", pageUrl, false);
        String json = Python.getInstance().getModule("json").callAttr("dumps", result).toString();
        JSONObject info = new JSONObject(json);
        JSONArray formats = info.optJSONArray("formats");
        Map<Integer, Choice> byHeight = new TreeMap<>(Collections.reverseOrder());
        Map<Integer, JSONObject> byHeightFormat = new TreeMap<>(Collections.reverseOrder());
        Map<Integer, JSONObject> videoOnly = new TreeMap<>(Collections.reverseOrder());
        JSONObject audioOnly = null;
        boolean hasAudioVideoFormat = false;
        if (formats == null) return new Analysis(info.optString("title", "Video"), new ArrayList<>());
        for (int i = 0; i < formats.length(); i++) {
            JSONObject format = formats.optJSONObject(i);
            if (format == null || format.optBoolean("has_drm")) continue;
            String videoCodec = format.optString("vcodec", "none");
            String audioCodec = format.optString("acodec", "none");
            String mediaUrl = format.optString("url", "");
            if (!isHttpUrl(mediaUrl)) continue;
            if (!"none".equals(videoCodec) && !"none".equals(audioCodec)) hasAudioVideoFormat = true;
            if (!"none".equals(videoCodec) && !isDevicePlayable(format, videoCodec)) continue;

            if ("none".equals(videoCodec) && !"none".equals(audioCodec)) {
                if (audioOnly == null || format.optInt("abr", 0) > audioOnly.optInt("abr", 0)) audioOnly = format;
                continue;
            }
            if ("none".equals(audioCodec) && !"none".equals(videoCodec)) {
                int h = qualityHeight(format);
                JSONObject previous = videoOnly.get(h);
                if (previous == null || preferFormat(format, previous)) videoOnly.put(h, format);
                continue;
            }
            if ("none".equals(videoCodec) || "none".equals(audioCodec)) continue;

            int height = qualityHeight(format);
            int bitrate = format.optInt("tbr", 0);
            String ext = format.optString("ext", "mp4").toLowerCase(Locale.ROOT);
            int fps = format.optInt("fps", 0);
            String resolution = height > 0 ? height + "p" : format.optString("resolution", "Video");
            String label = resolution + (height == 2160 ? " (4K)" : height == 1440 ? " (2K)" : "")
                    + (fps > 0 ? " · " + fps + " fps" : "") + " · " + ext.toUpperCase(Locale.ROOT);
            Choice choice = new Choice(info.optString("title", "Video"), label, mediaUrl, null, ext, height, bitrate,
                    format.optJSONObject("http_headers"), null, null, null, null, ext, null);
            Choice previous = byHeight.get(height);
            if (previous == null || preferFormat(format, byHeightFormat.get(height))) {
                byHeight.put(height, choice);
                byHeightFormat.put(height, format);
            }
        }

        if (audioOnly != null) {
            for (Map.Entry<Integer, JSONObject> entry : videoOnly.entrySet()) {
                int height = entry.getKey();
                JSONObject video = entry.getValue();
                if (byHeight.containsKey(height)) continue;
                String vcodec = video.optString("vcodec", "").toLowerCase(Locale.ROOT);
                String acodec = audioOnly.optString("acodec", "").toLowerCase(Locale.ROOT);
                String outputExt;
                if ((vcodec.startsWith("avc1") || vcodec.startsWith("av01")) && acodec.startsWith("mp4a")) outputExt = "mp4";
                else if ((vcodec.startsWith("vp09") || vcodec.startsWith("vp9") || vcodec.startsWith("vp8"))
                        && (acodec.startsWith("opus") || acodec.startsWith("vorbis"))
                        && Build.VERSION.SDK_INT >= 29) outputExt = "webm";
                else continue;

                int fps = video.optInt("fps", 0);
                String resolution = height > 0 ? height + "p" : "Video";
                String label = resolution + (height == 2160 ? " (4K)" : height == 1440 ? " (2K)" : "")
                        + (fps > 0 ? " · " + fps + " fps" : "") + " · " + outputExt.toUpperCase(Locale.ROOT);
                byHeight.put(height, new Choice(info.optString("title", "Video"), label,
                        video.optString("url"), audioOnly.optString("url"), outputExt,
                        height, video.optInt("tbr", 0) + audioOnly.optInt("abr", 0),
                        video.optJSONObject("http_headers"), audioOnly.optJSONObject("http_headers"), pageUrl,
                        video.optString("format_id"), audioOnly.optString("format_id"),
                        video.optString("ext", "mp4"), audioOnly.optString("ext", "m4a")));
            }
        }
        List<Choice> choices = new ArrayList<>(byHeight.values());
        if (audioOnly != null || hasAudioVideoFormat) {
            String audioFormatId = audioOnly == null ? "bestaudio" : audioOnly.optString("format_id", "bestaudio");
            String sourceExt = audioOnly == null ? "m4a" : audioOnly.optString("ext", "m4a");
            JSONObject audioHeaders = audioOnly == null ? null : audioOnly.optJSONObject("http_headers");
            choices.add(new Choice(info.optString("title", "Audio"), "Audio only · MP3", null, null, "mp3", 0,
                    audioOnly == null ? 0 : audioOnly.optInt("abr", 0), null, audioHeaders, pageUrl,
                    null, audioFormatId, null, sourceExt));
        }
        return new Analysis(info.optString("title", "Video"), choices);
    }

    private static boolean isDevicePlayable(JSONObject format, String codec) {
        String normalized = codec.toLowerCase(Locale.ROOT);
        String mime;
        if (normalized.startsWith("avc1") || normalized.startsWith("h264")) mime = "video/avc";
        else if (normalized.startsWith("hev1") || normalized.startsWith("hvc1") || normalized.startsWith("hevc")) mime = "video/hevc";
        else if (normalized.startsWith("av01")) mime = "video/av01";
        else if (normalized.startsWith("vp09") || normalized.startsWith("vp9")) mime = "video/x-vnd.on2.vp9";
        else if (normalized.startsWith("vp8")) mime = "video/x-vnd.on2.vp8";
        else return false;
        int width = format.optInt("width", 0);
        int height = qualityHeight(format);
        if (width <= 0 || height <= 0) return false;
        try {
            MediaFormat requested = MediaFormat.createVideoFormat(mime, width, height);
            return new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(requested) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean preferFormat(JSONObject candidate, JSONObject current) {
        if (current == null) return true;
        int candidateRank = codecRank(candidate.optString("vcodec", ""));
        int currentRank = codecRank(current.optString("vcodec", ""));
        return candidateRank != currentRank
                ? candidateRank > currentRank
                : candidate.optInt("tbr", 0) > current.optInt("tbr", 0);
    }

    private static int codecRank(String codec) {
        String value = codec.toLowerCase(Locale.ROOT);
        if (value.startsWith("avc1") || value.startsWith("h264")) return 5;
        if (value.startsWith("hev1") || value.startsWith("hvc1") || value.startsWith("hevc")) return 4;
        if (value.startsWith("vp09") || value.startsWith("vp9")) return 3;
        if (value.startsWith("av01")) return 2;
        if (value.startsWith("vp8")) return 1;
        return 0;
    }

    private static boolean isHttpUrl(String value) {
        Uri uri = Uri.parse(value);
        return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
    }

    private static int qualityHeight(JSONObject format) {
        int width = format.optInt("width", 0);
        int height = format.optInt("height", 0);
        return width > 0 && height > width ? width : height;
    }

    static Uri downloadAndMerge(Context context, Choice choice, Progress progress) throws Exception {
        File work = new File(context.getCacheDir(), "fvd-" + stableKey(choice.pageUrl + "|" + choice.videoFormatId + "|" + choice.audioFormatId));
        if (!work.exists() && !work.mkdirs()) throw new IllegalStateException("Could not prepare temporary storage.");
        File merged = new File(work, "merged." + choice.extension);
        File video = downloadSegment(work, choice.pageUrl, choice.videoFormatId, "video", choice.videoSourceExtension, 0, progress);
        File audio = downloadSegment(work, choice.pageUrl, choice.audioFormatId, "audio", choice.audioSourceExtension, 50, progress);
        mux(video, audio, merged, choice.extension);
        Uri saved = saveToDownloads(context, merged, safeName(choice.title) + "-" + System.currentTimeMillis() + "." + choice.extension,
                choice.extension.equals("webm") ? "video/webm" : "video/mp4");
        File[] leftovers = work.listFiles();
        if (leftovers != null) for (File leftover : leftovers) leftover.delete();
        work.delete();
        if (progress != null) progress.onProgress(100);
        return saved;
    }

    static Uri downloadAudioAsMp3(Context context, Choice choice, Progress progress) throws Exception {
        File work = new File(context.getCacheDir(), "fvd-" + stableKey(choice.pageUrl + "|audio|" + choice.audioFormatId));
        if (!work.exists() && !work.mkdirs()) throw new IllegalStateException("Could not prepare temporary storage.");
        File audio = downloadSegment(work, choice.pageUrl, choice.audioFormatId, "audio", choice.audioSourceExtension, 0, progress);
        File mp3 = new File(work, "audio.mp3");
        if (progress != null) progress.onProgress(75);
        String command = "-hide_banner -loglevel error -y -threads 1 -i " + quoteArgument(audio.getAbsolutePath())
                + " -vn -c:a libmp3lame -q:a 2 " + quoteArgument(mp3.getAbsolutePath());
        com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.execute(command);
        if (!ReturnCode.isSuccess(session.getReturnCode()) || !mp3.isFile() || mp3.length() == 0) {
            String detail = session.getOutput();
            if (detail == null || detail.trim().isEmpty()) detail = session.getFailStackTrace();
            throw new IllegalStateException(detail == null || detail.trim().isEmpty()
                    ? "Could not convert this audio to MP3. Try another source."
                    : "Could not convert this audio to MP3: " + detail.trim());
        }
        Uri saved = saveToDownloads(context, mp3, safeName(choice.title) + "-" + System.currentTimeMillis() + ".mp3", "audio/mpeg");
        File[] leftovers = work.listFiles();
        if (leftovers != null) for (File leftover : leftovers) leftover.delete();
        work.delete();
        if (progress != null) progress.onProgress(100);
        return saved;
    }

    private static File downloadSegment(File work, String pageUrl, String formatId, String prefix,
                                        String extension, int base, Progress progress) throws Exception {
        File target = new File(work, prefix + "." + extension);
        if (target.isFile() && target.length() > 0) return target;
        YtDlpRequest request = new YtDlpRequest(pageUrl)
                .addOption("-f", formatId)
                .addOption("--no-playlist")
                .setOutputTemplate(new File(work, prefix + ".%(ext)s").getAbsolutePath());
        YtDlpResponse response = YtDlp.execute(request, (percent, eta, line) -> {
            if (progress != null) progress.onProgress(base + Math.min(49, (int) (percent / 2)));
        });
        if (!response.isSuccess()) {
            String detail = response.getErrorOutput();
            throw new IllegalStateException(detail == null || detail.trim().isEmpty()
                    ? "The network interrupted the download. Tap Download again to retry; partial data is kept when possible."
                    : detail.trim());
        }
        File[] outputs = work.listFiles((directory, name) -> name.startsWith(prefix + ".") && !name.endsWith(".part") && !name.endsWith(".ytdl"));
        if (outputs != null) for (File output : outputs) if (output.isFile() && output.length() > 0) return output;
        throw new IllegalStateException("yt-dlp finished without creating the expected media stream.");
    }

    private static String stableKey(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 8; i++) key.append(String.format(Locale.ROOT, "%02x", bytes[i]));
        return key.toString();
    }

    private static void mux(File video, File audio, File output, String extension) throws Exception {
        StringBuilder command = new StringBuilder()
                .append("-hide_banner -loglevel error -y -threads 1 ")
                .append("-i ").append(quoteArgument(video.getAbsolutePath())).append(' ')
                .append("-i ").append(quoteArgument(audio.getAbsolutePath())).append(' ')
                .append("-map 0:v:0 -map 1:a:0 -c copy ");
        if ("mp4".equalsIgnoreCase(extension)) command.append("-movflags +faststart ");
        command.append(quoteArgument(output.getAbsolutePath()));

        com.arthenica.ffmpegkit.FFmpegSession session = FFmpegKit.execute(command.toString());
        if (!ReturnCode.isSuccess(session.getReturnCode())) {
            String detail = session.getOutput();
            if (detail == null || detail.trim().isEmpty()) detail = session.getFailStackTrace();
            throw new IllegalStateException(detail == null || detail.trim().isEmpty()
                    ? "Could not combine the video and audio streams. Try a different quality or source format."
                    : "Could not combine the video and audio streams: " + detail.trim());
        }
        if (!output.isFile() || output.length() == 0) {
            throw new IllegalStateException("The streams were downloaded, but the combined video file is empty.");
        }
    }

    private static String quoteArgument(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static Uri saveToDownloads(Context context, File file, String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri target = context.getContentResolver().insert(collection, values);
            if (target == null) throw new IllegalStateException("Could not create the output file in Downloads.");
            try (InputStream in = new FileInputStream(file); OutputStream out = context.getContentResolver().openOutputStream(target)) {
                if (out == null) throw new IllegalStateException("Could not open the output file.");
                byte[] buffer = new byte[128 * 1024];
                int count;
                while ((count = in.read(buffer)) >= 0) if (count > 0) out.write(buffer, 0, count);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Downloads.IS_PENDING, 0);
            context.getContentResolver().update(target, ready, null, null);
            return target;
        }
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File target = new File(directory, name);
        try (InputStream in = new FileInputStream(file); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = in.read(buffer)) >= 0) if (count > 0) out.write(buffer, 0, count);
        }
        return Uri.fromFile(target);
    }

    private static String safeName(String name) {
        String safe = name == null ? "video" : name.replaceAll("[^\\p{L}\\p{N}._() -]", "_")
                .replaceAll("_+", "_").replaceAll("\\s+", " ").trim();
        safe = safe.replaceAll("^[._ -]+|[._ -]+$", "");
        return safe.isEmpty() ? "video" : safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    interface Progress { void onProgress(int percent); }

    static final class Analysis {
        final String title;
        final List<Choice> choices;
        Analysis(String title, List<Choice> choices) { this.title = title; this.choices = choices; }
    }

    static final class Choice {
        final String title;
        final String label;
        final String mediaUrl;
        final String audioUrl;
        final String extension;
        final int height;
        final int bitrate;
        final JSONObject headers;
        final JSONObject audioHeaders;
        final String pageUrl;
        final String videoFormatId;
        final String audioFormatId;
        final String videoSourceExtension;
        final String audioSourceExtension;

        Choice(String title, String label, String mediaUrl, String audioUrl, String extension, int height, int bitrate,
               JSONObject headers, JSONObject audioHeaders, String pageUrl, String videoFormatId, String audioFormatId,
               String videoSourceExtension, String audioSourceExtension) {
            this.title = title;
            this.label = label;
            this.mediaUrl = mediaUrl;
            this.audioUrl = audioUrl;
            this.extension = extension;
            this.height = height;
            this.bitrate = bitrate;
            this.headers = headers;
            this.audioHeaders = audioHeaders;
            this.pageUrl = pageUrl;
            this.videoFormatId = videoFormatId;
            this.audioFormatId = audioFormatId;
            this.videoSourceExtension = videoSourceExtension;
            this.audioSourceExtension = audioSourceExtension;
        }

        boolean isAudioOnly() { return videoFormatId == null && audioFormatId != null; }
    }
}
