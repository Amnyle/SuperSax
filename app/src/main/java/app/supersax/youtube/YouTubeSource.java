package app.supersax.youtube;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import org.jetbrains.annotations.Nullable;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

/**
 * YouTube search and audio download through NewPipeExtractor (blocking calls: run them off the main thread).
 * Only the audio track is fetched, in 1 MB ranges (a single large request is throttled by YouTube).
 */
public final class YouTubeSource {
    private YouTubeSource() {}

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0";
    private static final int CHUNK = 1 << 20;
    private static volatile boolean initialised;

    /** One search result. */
    public static final class Result {
        public final String title, uploader, url;
        public final long durationSec;
        public final @Nullable String thumbnail;

        Result(String title, String uploader, String url, long durationSec, @Nullable String thumbnail) {
            this.title = title; this.uploader = uploader; this.url = url; this.durationSec = durationSec; this.thumbnail = thumbnail;
        }

        public String getTitle() { return title; }
        public String getUploader() { return uploader; }
        public String getUrl() { return url; }
        public long getDurationSec() { return durationSec; }
        public @Nullable String getThumbnail() { return thumbnail; }
    }

    /** A downloaded audio file and its title. */
    public static final class Downloaded {
        public final File file;
        public final String title;
        Downloaded(File file, String title) { this.file = file; this.title = title; }
        public File getFile() { return file; }
        public String getTitle() { return title; }
    }

    /** Minimal HTTP client for the extractor. */
    private static final class HttpDownloader extends Downloader {
        @Override
        public Response execute(Request request) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(request.url()).openConnection();
            c.setRequestMethod(request.httpMethod());
            c.setConnectTimeout(15_000);
            c.setReadTimeout(30_000);
            c.setRequestProperty("User-Agent", USER_AGENT);
            for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
                for (String v : h.getValue()) c.addRequestProperty(h.getKey(), v);
            }
            byte[] data = request.dataToSend();
            if (data != null) {
                c.setDoOutput(true);
                try (OutputStream o = c.getOutputStream()) { o.write(data); }
            }
            int code = c.getResponseCode();
            String body = "";
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in != null) {
                try (InputStream s = in) { body = new String(readAll(s), StandardCharsets.UTF_8); }
            }
            String latest = c.getURL().toString();
            Map<String, List<String>> headers = c.getHeaderFields();
            c.disconnect();
            return new Response(code, c.getResponseMessage(), headers, body, latest);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    private static synchronized void init(String language) {
        if (initialised) return;
        String lang = language.startsWith("fr") ? "fr" : "en";
        NewPipe.init(new HttpDownloader(), new Localization(lang), new ContentCountry(lang.equals("fr") ? "FR" : "US"));
        initialised = true;
    }

    private static StreamingService youtube() { return ServiceList.YouTube; }

    /** Videos matching [query] (first page). */
    public static List<Result> search(String query, String language) throws Exception {
        init(language);
        StreamingService yt = youtube();
        SearchInfo info = SearchInfo.getInfo(yt, yt.getSearchQHFactory().fromQuery(query, Collections.singletonList(YoutubeSearchQueryHandlerFactory.VIDEOS), ""));
        List<Result> out = new ArrayList<>();
        for (InfoItem item : info.getRelatedItems()) {
            if (!(item instanceof StreamInfoItem)) continue;
            StreamInfoItem s = (StreamInfoItem) item;
            String thumb = null;
            List<Image> thumbs = s.getThumbnails();
            if (thumbs != null && !thumbs.isEmpty()) thumb = thumbs.get(0).getUrl();
            out.add(new Result(s.getName(), s.getUploaderName() == null ? "" : s.getUploaderName(), s.getUrl(), Math.max(0, s.getDuration()), thumb));
        }
        return out;
    }

    /** The best directly downloadable audio stream: progressive HTTP, M4A preferred, highest bitrate. */
    static @Nullable AudioStream pick(List<AudioStream> streams) {
        AudioStream best = null;
        for (AudioStream a : streams) {
            if (a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || !a.isUrl()) continue;
            if (best == null) { best = a; continue; }
            boolean m4a = a.getFormat() == MediaFormat.M4A, bestM4a = best.getFormat() == MediaFormat.M4A;
            if (m4a != bestM4a) { if (m4a) best = a; continue; }
            if (a.getAverageBitrate() > best.getAverageBitrate()) best = a;
        }
        return best;
    }

    /** Downloads the audio of the video at [url] into [dir]; [progress] gets 0..1. */
    public static Downloaded download(String url, File dir, String language, DoubleConsumer progress) throws Exception {
        init(language);
        StreamInfo info = StreamInfo.getInfo(youtube(), url);
        AudioStream audio = pick(info.getAudioStreams());
        if (audio == null) throw new IOException("No downloadable audio stream");
        MediaFormat f = audio.getFormat();
        String ext = f != null ? f.getSuffix() : "m4a";
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        String id = info.getId().replaceAll("[^A-Za-z0-9_-]", "_");
        File target = new File(dir, id + "." + ext);
        File part = new File(dir, id + "." + ext + ".part");
        String src = audio.getContent();
        long total = -1, done = 0;
        try (OutputStream out = new FileOutputStream(part)) {
            while (total < 0 || done < total) {
                HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
                c.setConnectTimeout(15_000);
                c.setReadTimeout(30_000);
                c.setRequestProperty("User-Agent", USER_AGENT);
                c.setRequestProperty("Range", "bytes=" + done + "-" + (done + CHUNK - 1));
                int code = c.getResponseCode();
                if (code != 206 && code != 200) { c.disconnect(); throw new IOException("HTTP " + code); }
                if (total < 0) {
                    String range = c.getHeaderField("Content-Range");
                    if (range != null && range.contains("/")) total = Long.parseLong(range.substring(range.lastIndexOf('/') + 1).trim());
                    else total = c.getContentLengthLong();
                }
                long before = done;
                try (InputStream in = c.getInputStream()) {
                    byte[] b = new byte[16384];
                    int n;
                    while ((n = in.read(b)) > 0) {
                        out.write(b, 0, n);
                        done += n;
                        if (total > 0) progress.accept(Math.min(1.0, (double) done / total));
                    }
                } finally {
                    c.disconnect();
                }
                if (code == 200) break; // the server ignored the range and sent everything
                if (done == before) throw new IOException("Download stalled");
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            throw e;
        }
        if (target.exists()) //noinspection ResultOfMethodCallIgnored
            target.delete();
        if (!part.renameTo(target)) throw new IOException("Cannot save " + target);
        progress.accept(1.0);
        return new Downloaded(target, info.getName());
    }
}
