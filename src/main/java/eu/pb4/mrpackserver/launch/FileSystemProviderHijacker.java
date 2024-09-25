package eu.pb4.mrpackserver.launch;

import eu.pb4.mrpackserver.util.InstrumentationCatcher;
import eu.pb4.mrpackserver.util.Logger;

import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface FileSystemProviderHijacker {
    static List<FileSystemProvider> addProviders(ClassLoader loader, List<String> providers) {
        if (providers.isEmpty()) {
            return List.of();
        }

        var list = new ArrayList<FileSystemProvider>();

        for (var className : providers) {
            try {
                var clazz = loader.loadClass(className);
                list.add((FileSystemProvider) clazz.getConstructor().newInstance());
            } catch (Throwable ignored) {}
        }
        var prov = new ArrayList<>(FileSystemProvider.installedProviders());
        prov.addAll(list);

        InstrumentationCatcher.open("java.base", "java.nio.file.spi");

        try {
            var field = FileSystemProvider.class.getDeclaredField("installedProviders");
            field.setAccessible(true);
            field.set(null, Collections.unmodifiableList(prov));
            field.setAccessible(false);
        } catch (Throwable e) {
            Logger.error("Failed to add FileSystemProviders!", e);
            return List.of();
        }
        return list;
    }

    static void removeProviders(List<FileSystemProvider> providers) {
        if (providers.isEmpty()) {
            return;
        }

        var prov = new ArrayList<>(FileSystemProvider.installedProviders());
        prov.removeAll(providers);

        try {
            var field = FileSystemProvider.class.getDeclaredField("installedProviders");
            field.setAccessible(true);
            field.set(null, Collections.unmodifiableList(prov));
            field.setAccessible(false);
        } catch (Throwable e) {
            Logger.error("Failed to remove FileSystemProviders!", e);
        }
    }
}
