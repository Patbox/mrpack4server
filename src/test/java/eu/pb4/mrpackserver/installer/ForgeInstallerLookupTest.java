package eu.pb4.mrpackserver.installer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForgeInstallerLookupTest {
    @Test
    void resolvesRepeatedMinecraftSuffixForForge1710() {
        assertEquals(
                "1.7.10-10.13.4.1614-1.7.10",
                ForgeInstallerLookup.resolveCoordinate(
                        "1.7.10",
                        "10.13.4.1614",
                        List.of("1.7.10-10.13.4.1614-1.7.10")
                )
        );
    }

    @Test
    void resolvesStandardForgeCoordinateWithoutRepeatedSuffix() {
        assertEquals(
                "1.20.1-47.2.0",
                ForgeInstallerLookup.resolveCoordinate(
                        "1.20.1",
                        "47.2.0",
                        List.of("1.20.1-47.2.0")
                )
        );
    }

    @Test
    void rejectsUnknownForgeCoordinate() {
        assertNull(ForgeInstallerLookup.resolveCoordinate(
                "1.20.1",
                "47.2.0",
                List.of("1.20.1-47.1.0")
        ));
    }

    @Test
    void createsLegacyServerJarNameFromResolvedCoordinate() {
        assertEquals(
                "forge-1.7.10-10.13.4.1614-1.7.10-universal.jar",
                ForgeInstallerLookup.createServerJarName("1.7.10-10.13.4.1614-1.7.10")
        );
    }
}
