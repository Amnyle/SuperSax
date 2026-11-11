package app.supersax.domain.backup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import kotlin.jvm.functions.Function0;

/** The backup zip: backup.json plus recordings/ and imports/ files. */
public final class BackupZip {
    private BackupZip() {}

    public static final String JSON_ENTRY = "backup.json";
    public static final String RECORDINGS_DIR = "recordings/";
    public static final String IMPORTS_DIR = "imports/";

    /** Plain copy loop (InputStream.transferTo needs API 33). */
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    /** Receives each file entry of a backup as it is read. */
    public interface FileSink {
        void accept(String name, InputStream stream) throws IOException;
    }

    /** Writes the zip. files maps entry names (with folder prefix) to stream openers. */
    public static void write(OutputStream out, BackupData data, Map<String, ? extends Function0<? extends InputStream>> files) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(JSON_ENTRY));
            zip.write(BackupCodec.INSTANCE.encode(data).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, ? extends Function0<? extends InputStream>> e : files.entrySet()) {
                String name = e.getKey();
                if (!name.startsWith(RECORDINGS_DIR) && !name.startsWith(IMPORTS_DIR)) {
                    throw new IllegalArgumentException("Unexpected entry " + name);
                }
                zip.putNextEntry(new ZipEntry(name));
                try (InputStream in = e.getValue().invoke()) {
                    copy(in, zip);
                }
                zip.closeEntry();
            }
        }
    }

    /**
     * Reads and validates the zip. File entries are passed to onFile (name, stream) as they come;
     * the data is decoded (and its version checked) at the end. Path traversal entries are refused.
     */
    public static BackupData read(InputStream input, FileSink onFile) throws IOException, BackupVersionException {
        String text = null;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName();
                if (name.contains("..") || name.startsWith("/")) throw new IllegalArgumentException("Unsafe entry " + name);
                if (name.equals(JSON_ENTRY)) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    copy(zip, buf);
                    text = buf.toString(StandardCharsets.UTF_8.name());
                } else if (!e.isDirectory() && (name.startsWith(RECORDINGS_DIR) || name.startsWith(IMPORTS_DIR))) {
                    onFile.accept(name, zip);
                }
                zip.closeEntry();
            }
        }
        if (text == null) throw new BackupVersionException(-1, BackupVersionException.Reason.MISSING);
        return BackupCodec.INSTANCE.decode(text);
    }
}
