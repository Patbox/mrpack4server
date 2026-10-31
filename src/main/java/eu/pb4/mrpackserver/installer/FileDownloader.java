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
        this.entries.add(new DownloadableEntry(out, path, displayName, fileSize, hashData, downloads, false));
    }

    public void requestJar(Path out, String path, String displayName, long fileSize, @Nullable HashData hashData, List<URI> downloads) {
        this.entries.add(new DownloadableEntry(out, path, displayName, fileSize, hashData, downloads, true));
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
                if (!isSuccessfulResponse(x)) {
                    try {
                        x.body().close();
                    } catch (Exception ignored) {
                    }
                    synchronized (failedDownloads) {
                        failedDownloads.add(entry);
                    }
                    return;
                }

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

                var hash = Utils.handleDownloadedFile(entry.out, x.body(), entry.displayName, fileSize, entry.hashData, entry.jar);
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

    public record DownloadableEntry(Path out, String path, String displayName, long fileSize, @Nullable HashData hashData, List<URI> downloads, boolean jar) {}

    static boolean isSuccessfulResponse(HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300
                && response.headers().firstValue("Content-Type")
                .map(x -> !x.toLowerCase(Locale.ROOT).contains("text/html"))
                .orElse(true);
    }
}
