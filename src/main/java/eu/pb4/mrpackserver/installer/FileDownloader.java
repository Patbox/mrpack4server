package eu.pb4.mrpackserver.installer;

import eu.pb4.mrpackserver.util.Constants;
import eu.pb4.mrpackserver.util.DownloadMeta;
import eu.pb4.mrpackserver.util.HashData;
import eu.pb4.mrpackserver.util.Utils;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class FileDownloader {
    private final List<DownloadableEntry> entries = new ArrayList<>();

    @Nullable
    private DownloadMeta downloadMeta = null;

    public void request(Path out, String path, long fileSize, @Nullable HashData hashData, List<URI> downloads) {
        request(out, path, path, fileSize, hashData, downloads);
    }

    public void request(Path out, String path, String displayName, long fileSize, @Nullable HashData hashData, List<URI> downloads) {
        this.entries.add(new DownloadableEntry(out, path, displayName, fileSize, hashData, downloads));
    }

    public void setDownloadMeta(@Nullable DownloadMeta downloadMeta) {
        this.downloadMeta = downloadMeta;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public List<DownloadableEntry> downloadFiles(Map<String, HashData> hashes) throws InterruptedException, ExecutionException {
        if (this.entries.isEmpty()) {
            return List.of();
        }

        var clients = new ArrayList<HttpClient>();
        var requests = new ArrayList<CompletableFuture<?>>();
        this.entries.sort(Comparator.comparingLong(DownloadableEntry::fileSize));

        var failedDownloads = new ArrayList<DownloadableEntry>();

        for (var i = 0; i < Constants.DOWNLOAD_PARRALEL_CLIENTS; i++) {
            clients.add(Utils.createHttpClient());
        }

        int i = 0;

        for (var entry : List.copyOf(entries)) {
            requests.add(clients.get(i++ % clients.size()).sendAsync(
                    Utils.createGetRequest(entry.downloads.get(0), this.downloadMeta),
                    HttpResponse.BodyHandlers.ofInputStream()
            ).thenAccept(x -> {
                var fileSize = entry.fileSize;
                if (fileSize == -1) {
                    var headerSize = x.headers().firstValue("Content-Length");
                    if (headerSize.isPresent()) {
                        try {
                            fileSize = Long.parseLong(headerSize.get());
                        } catch (Throwable e) {
                            // ignored
                        }
                    }
                }

                var hash = Utils.handleDownloadedFile(entry.out, x.body(), entry.displayName, fileSize, entry.hashData);
                if (hash != null) {
                    synchronized (hashes) {
                        hashes.put(entry.path, hash);
                        this.entries.remove(entry);
                    }
                } else {
                    synchronized (failedDownloads) {
                        failedDownloads.add(entry);
                    }
                }
            }).exceptionally(x -> {
                if (x != null) {
                    synchronized (failedDownloads) {
                        failedDownloads.add(entry);
                    }
                }
                return null;
            }));
        }

        CompletableFuture.allOf(requests.toArray(new CompletableFuture[0])).get();
        return failedDownloads;
    }

    public record DownloadableEntry(Path out, String path, String displayName, long fileSize, @Nullable HashData hashData, List<URI> downloads) {}
}
