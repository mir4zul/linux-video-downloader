package com.miraj.freevideodownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 23, 36);
    private static final int PANEL = Color.rgb(27, 39, 58);
    private static final int TEXT = Color.rgb(233, 239, 248);
    private static final int MUTED = Color.rgb(164, 179, 201);
    private static final int ACCENT = Color.rgb(75, 220, 177);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DownloadManager downloads;
    private EditText urlInput;
    private LinearLayout historyList;
    private TextView analysis;
    private TextView qualityHint;
    private Spinner qualitySpinner;
    private Button videoModeButton;
    private Button audioModeButton;
    private Button downloadButton;
    private ProgressBar taskProgress;
    private final ExecutorService extractor = Executors.newSingleThreadExecutor();
    private List<VideoExtractor.Choice> choices = new ArrayList<>();
    private String selectedMode = "video";
    private String analyzedUrl = "";
    private volatile boolean engineReady;
    private volatile boolean engineStarting;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (renderHistory()) handler.postDelayed(this, 3000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        downloads = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        buildScreen();
        analysis.setText("Ready. Paste a public video link and tap Analyze link.");
        acceptSharedLink(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptSharedLink(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        if (renderHistory()) handler.postDelayed(refresh, 3000);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("Downloader");
        title.setTextColor(TEXT);
        title.setTextSize(25);
        title.setTypeface(null, 1);
        page.addView(title);
        TextView subtitle = label("Download public videos and audio in a few taps.");
        subtitle.setPadding(0, dp(6), 0, dp(18));
        page.addView(subtitle);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("Paste a public video page or media link");
        urlInput.setHintTextColor(MUTED);
        urlInput.setTextColor(TEXT);
        urlInput.setTextSize(15);
        urlInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        urlInput.setBackground(roundRect(PANEL, 12));
        page.addView(urlInput, matchWrap());

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.topMargin = dp(14);
        page.addView(modes, modeParams);
        videoModeButton = button("Video", ACCENT, BG);
        audioModeButton = button("Audio · MP3", PANEL, TEXT);
        modes.addView(videoModeButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams audioModeParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        audioModeParams.leftMargin = dp(8);
        modes.addView(audioModeButton, audioModeParams);
        audioModeButton.setVisibility(View.GONE);

        Button analyze = button("Analyze link", ACCENT, BG);
        LinearLayout.LayoutParams analyzeParams = matchWrap();
        analyzeParams.topMargin = dp(12);
        page.addView(analyze, analyzeParams);
        analysis = label("The app checks public links and lists downloadable qualities.");
        analysis.setPadding(0, dp(14), 0, dp(4));
        page.addView(analysis);

        qualityHint = label("Choose video quality");
        qualityHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams qualityHintParams = matchWrap();
        qualityHintParams.topMargin = dp(8);
        page.addView(qualityHint, qualityHintParams);
        qualitySpinner = new Spinner(this);
        qualitySpinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams qualityParams = matchWrap();
        qualityParams.topMargin = dp(8);
        page.addView(qualitySpinner, qualityParams);

        downloadButton = button("Download", ACCENT, BG);
        LinearLayout.LayoutParams downloadParams = matchWrap();
        downloadParams.topMargin = dp(12);
        page.addView(downloadButton, downloadParams);
        taskProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        taskProgress.setMax(100);
        taskProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = matchWrap();
        progressParams.topMargin = dp(8);
        page.addView(taskProgress, progressParams);

        TextView note = label("Only download content you have permission to save. Private, login-required, or DRM-protected media may not work.");
        note.setTextSize(13);
        note.setPadding(0, dp(10), 0, dp(22));
        page.addView(note);

        TextView historyTitle = new TextView(this);
        historyTitle.setText("Downloads & history");
        historyTitle.setTextColor(TEXT);
        historyTitle.setTextSize(19);
        historyTitle.setTypeface(null, 1);
        page.addView(historyTitle);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = matchWrap();
        listParams.topMargin = dp(12);
        page.addView(historyList, listParams);

        analyze.setOnClickListener(v -> inspectLink());
        downloadButton.setOnClickListener(v -> enqueueLink());
        videoModeButton.setOnClickListener(v -> setMode("video"));
        audioModeButton.setOnClickListener(v -> setMode("audio"));
        urlInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().trim().equals(analyzedUrl)) {
                    choices.clear();
                    analyzedUrl = "";
                    qualityHint.setVisibility(View.GONE);
                    qualitySpinner.setVisibility(View.GONE);
                    videoModeButton.setVisibility(View.GONE);
                    audioModeButton.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        setContentView(scroll);
    }

    private void inspectLink() {
        String value = urlInput.getText().toString().trim();
        Uri uri = validUri(value);
        if (uri == null) {
            analysis.setText("Enter a valid http:// or https:// link.");
            return;
        }
        choices.clear();
        analyzedUrl = "";
        qualityHint.setVisibility(View.GONE);
        qualitySpinner.setVisibility(View.GONE);
        taskProgress.setVisibility(View.GONE);
        videoModeButton.setVisibility(View.GONE);
        audioModeButton.setVisibility(View.GONE);
        if (!engineReady) {
            if (engineStarting) {
                analysis.setText("Video engine is starting. Please wait.");
                return;
            }
            engineStarting = true;
            analysis.setText("Starting video engine…");
            extractor.execute(() -> {
                try {
                    VideoExtractor.initialize(getApplicationContext());
                    engineReady = true;
                    engineStarting = false;
                    analyzeLinkInBackground(value);
                } catch (Exception e) {
                    engineStarting = false;
                    runOnUiThread(() -> analysis.setText("Video engine could not start. Check free storage and restart the app."));
                }
            });
        } else {
            analyzeLinkInBackground(value);
        }
        hideKeyboard();
    }

    private void analyzeLinkInBackground(String value) {
        runOnUiThread(() -> analysis.setText("Analyzing public link…"));
        extractor.execute(() -> {
            try {
                VideoExtractor.Analysis result = VideoExtractor.analyze(value);
                runOnUiThread(() -> showChoices(result));
            } catch (Exception e) {
                runOnUiThread(() -> analysis.setText("Could not analyze this link. Check your internet connection; the video may be private, login-required, or unsupported."));
            }
        });
    }

    private void showChoices(VideoExtractor.Analysis result) {
        choices.clear();
        choices.addAll(result.choices);
        analyzedUrl = urlInput.getText().toString().trim();
        if (choices.isEmpty()) {
            analysis.setText(result.title + " · No format supported by this phone was found. Try another source.");
            return;
        }
        analysis.setText(result.title + " · " + choices.size() + " quality options found.");
        boolean hasVideo = choices.stream().anyMatch(choice -> !choice.isAudioOnly());
        boolean hasAudio = choices.stream().anyMatch(VideoExtractor.Choice::isAudioOnly);
        videoModeButton.setVisibility(hasVideo ? View.VISIBLE : View.GONE);
        audioModeButton.setVisibility(hasAudio ? View.VISIBLE : View.GONE);
        setMode(hasVideo ? "video" : "audio");
        qualityHint.setVisibility(View.VISIBLE);
        qualitySpinner.setVisibility(View.VISIBLE);
    }

    private void setMode(String mode) {
        selectedMode = mode;
        boolean audio = "audio".equals(mode);
        videoModeButton.setBackground(roundRect(audio ? PANEL : ACCENT, 12));
        videoModeButton.setTextColor(audio ? TEXT : BG);
        audioModeButton.setBackground(roundRect(audio ? ACCENT : PANEL, 12));
        audioModeButton.setTextColor(audio ? BG : TEXT);
        qualityHint.setText(audio ? "Audio format · MP3" : "Choose video quality");
        List<VideoExtractor.Choice> visible = new ArrayList<>();
        for (VideoExtractor.Choice choice : choices) {
            if (choice.isAudioOnly() == audio) visible.add(choice);
        }
        List<String> labels = new ArrayList<>();
        for (VideoExtractor.Choice choice : visible) labels.add(choice.label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(adapter);
        qualitySpinner.setTag(visible);
    }

    private void enqueueLink() {
        String value = urlInput.getText().toString().trim();
        Uri uri = validUri(value);
        if (uri == null) {
            analysis.setText("Enter a valid http:// or https:// link first.");
            return;
        }
        if (!choices.isEmpty() && analyzedUrl.equals(value) && qualitySpinner.getVisibility() == View.VISIBLE) {
            @SuppressWarnings("unchecked")
            List<VideoExtractor.Choice> visible = (List<VideoExtractor.Choice>) qualitySpinner.getTag();
            if (visible != null && !visible.isEmpty()) {
                int index = Math.max(0, Math.min(qualitySpinner.getSelectedItemPosition(), visible.size() - 1));
                enqueueChoice(visible.get(index));
                return;
            }
        }
        if (!choices.isEmpty() && !analyzedUrl.equals(value)) {
            analysis.setText("Analyze this link first to refresh its available formats.");
            return;
        }
        String path = uri.getLastPathSegment();
        if (extension(path).isEmpty()) {
            analysis.setText("This link does not look like a direct media file. Try a URL ending in .mp4, .webm, .mkv, .mp3, or another media extension.");
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(fileName(uri));
            request.setDescription("Downloader · Saving to Downloads");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName(uri));
            long id = downloads.enqueue(request);
            Set<String> ids = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("download_ids", Collections.emptySet()));
            ids.add(Long.toString(id));
            getPreferences(MODE_PRIVATE).edit().putStringSet("download_ids", ids).apply();
            analysis.setText("Download added to the Android download queue.");
            refreshHistoryIfNeeded();
            hideKeyboard();
        } catch (RuntimeException e) {
            analysis.setText("Could not start this download. Check the link and available storage.");
        }
    }

    private void enqueueChoice(VideoExtractor.Choice choice) {
        taskProgress.setVisibility(View.VISIBLE);
        taskProgress.setProgress(0);
        if (choice.isAudioOnly()) {
            analysis.setText("Downloading audio and converting to MP3… 0%");
            extractor.execute(() -> {
                try {
                    Uri saved = VideoExtractor.downloadAudioAsMp3(getApplicationContext(), choice, percent ->
                            runOnUiThread(() -> {
                                taskProgress.setProgress(percent);
                                analysis.setText("Downloading audio · " + percent + "%");
                            }));
                    Set<String> savedItems = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("merged_downloads", Collections.emptySet()));
                    String name = safeFileName(choice.title) + ".mp3";
                    savedItems.add(saved.toString() + "|" + name);
                    getPreferences(MODE_PRIVATE).edit().putStringSet("merged_downloads", savedItems).apply();
                    runOnUiThread(() -> {
                        taskProgress.setVisibility(View.GONE);
                        analysis.setText("Audio download complete · MP3");
                        renderHistory();
                    });
                } catch (Exception e) {
                    String message = e.getMessage();
                    runOnUiThread(() -> {
                        taskProgress.setVisibility(View.GONE);
                        analysis.setText("Could not create MP3. " + (message == null ? "Try another source." : message));
                    });
                }
            });
            return;
        }
        if (choice.audioUrl != null) {
            analysis.setText("Downloading video and audio streams… 0%");
            extractor.execute(() -> {
                try {
                    Uri saved = VideoExtractor.downloadAndMerge(getApplicationContext(), choice, percent ->
                            runOnUiThread(() -> {
                                taskProgress.setProgress(percent);
                                analysis.setText("Downloading video · " + percent + "%");
                            }));
                    Set<String> savedItems = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("merged_downloads", Collections.emptySet()));
                    savedItems.add(saved.toString() + "|" + safeFileName(choice.title) + "." + choice.extension);
                    getPreferences(MODE_PRIVATE).edit().putStringSet("merged_downloads", savedItems).apply();
                    runOnUiThread(() -> {
                        taskProgress.setVisibility(View.GONE);
                        analysis.setText("Download complete · " + choice.label);
                        renderHistory();
                    });
                } catch (Exception e) {
                    String message = e.getMessage();
                    runOnUiThread(() -> {
                        taskProgress.setVisibility(View.GONE);
                        analysis.setText("Could not download this format. " + (message == null ? "Try another quality or link." : message));
                    });
                }
            });
            return;
        }
        try {
            Uri mediaUri = validUri(choice.mediaUrl);
            if (mediaUri == null) throw new IllegalArgumentException("The source returned an invalid media URL.");
            DownloadManager.Request request = new DownloadManager.Request(mediaUri);
            request.setTitle(choice.title + " · " + choice.label);
            request.setDescription("Downloader · Saving to Downloads");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            if (choice.headers != null) {
                java.util.Iterator<String> names = choice.headers.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    request.addRequestHeader(name, choice.headers.optString(name));
                }
            }
            String fileName = safeFileName(choice.title) + "-" + System.currentTimeMillis() + "." + choice.extension;
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = downloads.enqueue(request);
            Set<String> ids = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("download_ids", Collections.emptySet()));
            ids.add(Long.toString(id));
            getPreferences(MODE_PRIVATE).edit().putStringSet("download_ids", ids).apply();
            analysis.setText("Download started · " + choice.label);
            refreshHistoryIfNeeded();
        } catch (RuntimeException e) {
            analysis.setText("Could not start this download. " + e.getMessage());
        }
    }

    @Override protected void onDestroy() {
        extractor.shutdownNow();
        super.onDestroy();
    }

    private void refreshHistoryIfNeeded() {
        handler.removeCallbacks(refresh);
        if (renderHistory()) handler.postDelayed(refresh, 3000);
    }

    private boolean renderHistory() {
        if (historyList == null) return false;
        historyList.removeAllViews();
        boolean activeDownload = false;
        Set<String> ids = getPreferences(MODE_PRIVATE).getStringSet("download_ids", Collections.emptySet());
        ArrayList<Long> ordered = new ArrayList<>();
        for (String value : ids) {
            try { ordered.add(Long.parseLong(value)); } catch (NumberFormatException ignored) { }
        }
        ordered.sort(Collections.reverseOrder());
        for (long id : ordered) {
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            try (Cursor cursor = downloads.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) continue;
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED) activeDownload = true;
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                addDownloadCard(id, name, status, total, soFar);
            } catch (RuntimeException ignored) {
                // A user may remove an item from Android's system download history.
            }
        }
        for (String item : getPreferences(MODE_PRIVATE).getStringSet("merged_downloads", Collections.emptySet())) {
            int split = item.indexOf('|');
            if (split > 0) addMergedHistoryCard(Uri.parse(item.substring(0, split)), item.substring(split + 1));
        }
        if (historyList.getChildCount() == 0) historyList.addView(label("No downloads yet."));
        return activeDownload;
    }

    private void addMergedHistoryCard(Uri uri, String name) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundRect(PANEL, 12));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(10);
        historyList.addView(card, cardParams);
        TextView title = label(name);
        title.setTextColor(TEXT);
        title.setTypeface(null, 1);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title);
        card.addView(label("Completed"));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        card.addView(actions);
        Button open = smallButton("Open");
        open.setOnClickListener(v -> {
            try {
                String type = URLConnection.guessContentTypeFromName(name);
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, type == null ? "video/*" : type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (RuntimeException e) {
                Toast.makeText(this, "No app can open this file, or it was removed.", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(open);
        Button remove = smallButton("Remove");
        remove.setOnClickListener(v -> {
            try { getContentResolver().delete(uri, null, null); } catch (RuntimeException ignored) { }
            Set<String> items = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("merged_downloads", Collections.emptySet()));
            items.remove(uri.toString() + "|" + name);
            getPreferences(MODE_PRIVATE).edit().putStringSet("merged_downloads", items).apply();
            renderHistory();
        });
        actions.addView(remove);
    }

    private void addDownloadCard(long id, String name, int status, long total, long soFar) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundRect(PANEL, 12));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(10);
        historyList.addView(card, cardParams);

        TextView title = label(name == null || name.isEmpty() ? "Download" : name);
        title.setTextColor(TEXT);
        title.setTypeface(null, 1);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(title);
        TextView state = label(statusText(status, total, soFar));
        state.setPadding(0, dp(5), 0, dp(7));
        card.addView(state);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(total > 0 ? (int) Math.min(100, soFar * 100 / total) : 0);
        card.addView(progress, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        card.addView(actions);
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            Button open = smallButton("Open");
            open.setOnClickListener(v -> {
                try {
                    Uri fileUri = downloads.getUriForDownloadedFile(id);
                    if (fileUri == null) throw new IllegalStateException("File is missing");
                    String type = URLConnection.guessContentTypeFromName(name);
                    Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(fileUri, type == null ? "*/*" : type)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } catch (RuntimeException e) {
                    Toast.makeText(this, "No app can open this file, or it was removed.", Toast.LENGTH_SHORT).show();
                }
            });
            actions.addView(open);
        }
        Button cancel = smallButton(status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING ? "Cancel" : "Remove");
        cancel.setOnClickListener(v -> {
            if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                downloads.remove(id);
            }
            Set<String> ids = new HashSet<>(getPreferences(MODE_PRIVATE).getStringSet("download_ids", Collections.emptySet()));
            ids.remove(Long.toString(id));
            getPreferences(MODE_PRIVATE).edit().putStringSet("download_ids", ids).apply();
            renderHistory();
        });
        actions.addView(cancel);
    }

    private String statusText(int status, long total, long soFar) {
        switch (status) {
            case DownloadManager.STATUS_PENDING: return "Waiting to start";
            case DownloadManager.STATUS_RUNNING:
                return total > 0 ? String.format(Locale.ROOT, "Downloading · %d%% · %s / %s", soFar * 100 / total, size(soFar), size(total)) : "Downloading · " + size(soFar);
            case DownloadManager.STATUS_PAUSED: return "Paused by Android · " + pauseReason();
            case DownloadManager.STATUS_SUCCESSFUL: return "Completed · " + size(total);
            case DownloadManager.STATUS_FAILED: return "Download failed · " + failureReason();
            default: return "Queued";
        }
    }

    private String pauseReason() { return "waiting for network or storage"; }
    private String failureReason() { return "check the link and try again"; }
    private String size(long bytes) { return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0); }

    private Uri validUri(String value) {
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) ? uri : null;
        } catch (RuntimeException e) { return null; }
    }

    private String extension(String path) {
        if (path == null) return "";
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("mp4|webm|mkv|mov|avi|flv|ts|m4v|mp3|m4a|aac|opus|ogg|wav|flac") ? ext : "";
    }

    private String fileName(Uri uri) {
        String name = uri.getLastPathSegment();
        try { name = URLDecoder.decode(name == null ? "download" : name, StandardCharsets.UTF_8.name()); }
        catch (Exception ignored) { }
        name = name.replaceAll("[^\\p{L}\\p{N}._() -]", "_").replaceAll("_+", "_").replaceAll("\\s+", " ").trim();
        name = name.replaceAll("^[._ -]+|[._ -]+$", "");
        return name.isEmpty() ? "download" : name;
    }

    private String safeFileName(String value) {
        String name = value == null ? "video" : value.replaceAll("[^\\p{L}\\p{N}._() -]", "_")
                .replaceAll("_+", "_").replaceAll("\\s+", " ").trim();
        name = name.replaceAll("^[._ -]+|[._ -]+$", "");
        if (name.isEmpty()) name = "video";
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    private void acceptSharedLink(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && urlInput != null) {
            int start = shared.indexOf("http");
            urlInput.setText(start >= 0 ? shared.substring(start).split("\\s")[0] : shared);
            inspectLink();
        }
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(14);
        return view;
    }

    private Button button(String text, int background, int foreground) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(null, 1);
        b.setBackground(roundRect(background, 12));
        return b;
    }

    private Button smallButton(String text) {
        Button b = button(text, Color.rgb(48, 65, 91), TEXT);
        b.setTextSize(13);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(42));
        p.leftMargin = dp(8);
        b.setLayoutParams(p);
        return b;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }
}
