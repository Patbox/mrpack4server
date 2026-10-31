package eu.pb4.mrpackserver.installer;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import javax.net.ssl.SSLSession;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FileDownloaderTest {
    @Test
    void rejectsHttpErrorsAndHtmlResponses() {
        assertFalse(FileDownloader.isSuccessfulResponse(response(404, "text/html")));
        assertFalse(FileDownloader.isSuccessfulResponse(response(200, "text/html; charset=utf-8")));
    }

    private static HttpResponse<Object> response(int status, String contentType) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<Object>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (x, y) -> true);
            }

            @Override
            public Object body() {
                return null;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://example.test/");
            }

            @Override
            public Version version() {
                return Version.HTTP_1_1;
            }
        };
    }
}
