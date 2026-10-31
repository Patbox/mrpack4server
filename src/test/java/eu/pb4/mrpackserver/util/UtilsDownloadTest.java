package eu.pb4.mrpackserver.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsDownloadTest {
    @Test
    void rejectsHtmlWhenJarValidationIsRequired() throws Exception {
        var directory = Files.createTempDirectory("mrpack4server-download-test");
        var destination = directory.resolve("installer.jar");
        Files.writeString(destination, "previous installer");

        assertFalse(Utils.handleDownloadedFile(
                destination,
                new ByteArrayInputStream("<html><body>404</body></html>".getBytes()),
                "Forge installer",
                -1,
                null,
                true
        ) != null);
        assertTrue(Files.exists(destination));
        assertTrue(Files.readString(destination).equals("previous installer"));
    }

    @Test
    void acceptsValidJarAndReplacesDestinationOnlyAfterValidation() throws Exception {
        var directory = Files.createTempDirectory("mrpack4server-download-test");
        var destination = directory.resolve("installer.jar");
        try (var output = Files.newOutputStream(directory.resolve("valid.jar"));
             var jar = new JarOutputStream(output)) {
        }

        var bytes = Files.readAllBytes(directory.resolve("valid.jar"));
        assertTrue(Utils.handleDownloadedFile(
                destination,
                new ByteArrayInputStream(bytes),
                "Forge installer",
                bytes.length,
                null,
                true
        ) != null);
        assertTrue(Files.exists(destination));
    }
}
