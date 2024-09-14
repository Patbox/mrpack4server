package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.Logger;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class FileDownloader {
    private final List<DownloadableEntry> entries = new ArrayList<>();
    public void request(Path out, String path, long fileSize, @Nullable String sha512, List<URI> downloads) {
        request(out, path, path, fileSize, sha512, downloads);
    }
    public void request(Path out, String path, String displayName, long fileSize, @Nullable String sha512, List<URI> downloads) {
        this.entries.add(new DownloadableEntry(out, path, displayName, fileSize, sha512, downloads));
    }

    public List<String> downloadFiles(Map<String, String> hashes) throws InterruptedException, ExecutionException {
        if (this.entries.isEmpty()) {
            return List.of();
        }

        Logger.info("Downloading modpack requested files...");

        var list = new ArrayList<HttpClient>();
        var list2 = new ArrayList<CompletableFuture>();
        this.entries.sort(Comparator.comparingLong(DownloadableEntry::fileSize));

        var failedDownloads = new ArrayList<String>();

        for (var i = 0; i < 5; i++) {
            list.add(Utils.createHttpClient());
        }

        int i = 0;

        for (var entry : entries) {
            list2.add(list.get(i++ % list.size()).sendAsync(
                    Utils.createGetRequest(entry.downloads.get(0)),
                    HttpResponse.BodyHandlers.ofInputStream()
            ).thenAccept(x -> {
                var hash = Utils.handleDownloadedFile(entry.out, x.body(), entry.displayName, entry.fileSize, entry.sha512);
                if (hash != null) {
                    synchronized (hashes) {
                        hashes.put(entry.path, HexFormat.of().formatHex(hash));
                    }
                } else {
                    synchronized (failedDownloads) {
                        failedDownloads.add(entry.displayName);
                    }
                }
            }));
        }

        CompletableFuture.allOf(list2.toArray(new CompletableFuture[0])).get();
        Logger.info("Finished downloading modpack requested files!");
        return failedDownloads;
    }

    public record DownloadableEntry(Path out, String path, String displayName, long fileSize, @Nullable String sha512, List<URI> downloads) {}
}
