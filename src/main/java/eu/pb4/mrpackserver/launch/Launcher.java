package eu.pb4.mrpackserver.launch;

import eu.pb4.mrpackserver.util.InstrumentationCatcher;
import eu.pb4.mrpackserver.util.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public interface Launcher {
    static boolean launchExec(Path path, Function<ClassLoader, ClassFileTransformer> transformer, String... args) {
        return launchExec(getFromPath(path), transformer, args);
    }
    @Nullable
    static Launcher.Target getFromPath(Path path) {
        String mainClass = null;
        String launcherAgentClass = null;
        var jarUrl = new ArrayList<URL>();
        try {
            jarUrl.add(path.toUri().toURL());
        } catch (Throwable e) {
            Logger.error("Invalid path '%s'!", path);
            return null;
        }

        try (var jarFile = new JarFile(path.toFile())) {
            mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            launcherAgentClass = jarFile.getManifest().getMainAttributes().getValue("Launcher-Agent-Class");
            var classPath = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

            if (mainClass == null) {
                Logger.error("The '%s' is missing main class!!", path);
                return null;
            }

            if (classPath != null) {
                var tokenizer = new StringTokenizer(classPath, " ");
                while (tokenizer.hasMoreTokens()) {
                    var t = tokenizer.nextToken();
                    jarUrl.add(Path.of(t.replace("\n", "").replace("\r", "")).toUri().toURL());
                }
            }
            var fsProviders = new ArrayList<String>();
            for (var url : jarUrl) {
                try (var fs = FileSystems.newFileSystem(Path.of(url.toURI()))) {
                    var x = fs.getPath("META-INF/services/java.nio.file.spi.FileSystemProvider");
                    if (Files.exists(x)) {
                        fsProviders.addAll(Files.readAllLines(x));
                    }
                }
            }

            return new Target(jarUrl, mainClass, launcherAgentClass, fsProviders);
        } catch (Throwable e) {
            Logger.error("Failed to read data of '%s'!", path, e);
            return null;
        }
    }

    static boolean launchExec(Target target, Function<ClassLoader, ClassFileTransformer> transformer, String... args) {
        ClassFileTransformer transformer1 = null;
        try (var launchClassLoader = new URLClassLoader(target.classPath.toArray(URL[]::new))) {
            transformer1 = transformer.apply(launchClassLoader);
            if (transformer1 != null) {
                if (InstrumentationCatcher.exists()) {
                    InstrumentationCatcher.get().addTransformer(transformer1);
                } else {
                    transformer1 = null;
                    Logger.warn("The executed jar (%s) requires patches, but they can't be executed!", target);
                    Logger.warn("Update to Java 9 or newer to fix this! Through in some cases it's safe to ignore!");
                }
            }

            return execute(launchClassLoader, target, args, true);
        } catch (Throwable e) {
            Logger.error("Exception occurred while executing '%s' with arguments '%s'!", target, String.join(" ", args), e);
            return false;
        } finally {
            if (transformer1 != null) {
                InstrumentationCatcher.get().removeTransformer(transformer1);
            }
        }
    }

    private static boolean execute(ClassLoader loader, Target target, String[] args, boolean cleanup) throws Throwable {
        var fs = FileSystemProviderHijacker.addProviders(loader, target.fileSystemProviders);
        if (target.launcherAgentClass != null) {
            if (InstrumentationCatcher.exists()) {
                try {
                    var m = loader.loadClass(target.launcherAgentClass).getDeclaredMethod("agentmain", String.class, Instrumentation.class);
                    m.setAccessible(true);
                    var handle = MethodHandles.lookup().unreflect(m);
                    handle.invoke("", InstrumentationCatcher.get());
                } catch (Throwable e) {
                    Logger.error("Error occurred while invoking Launcher-Agent-Class!", e);
                }
            } else {
                Logger.warn("The executed jar (%s) requires Instrumentation, but it's not set up!");
                Logger.warn("Update to Java 9 or newer to fix this! Through in some cases it's safe to ignore!");
            }
        }

        try {
            var handle = MethodHandles.publicLookup().findStatic(loader.loadClass(target.mainClass), "main", MethodType.methodType(void.class, String[].class));
            handle.invoke((Object) args);
            if (cleanup) {
                FileSystemProviderHijacker.removeProviders(fs);
            }
            return true;
        } catch (ExitError exit) {
            return exit.code == 0;
        }
    }

    static void launchFinal(Target target, Function<ClassLoader, ClassFileTransformer> transformer, String... args) throws Throwable {
        ClassFileTransformer transformer1 = null;
        var launchClassLoader = new URLClassLoader(target.classPath.toArray(URL[]::new));
        transformer1 = transformer.apply(launchClassLoader);
        if (transformer1 != null) {
            if (InstrumentationCatcher.exists()) {
                InstrumentationCatcher.get().addTransformer(transformer1);
            } else {
                Logger.warn("The executed jar (%s) requires patches, but they can't be executed!", target);
                Logger.warn("Update to Java 9 or newer to fix this! Through in some cases it's safe to ignore!");
            }
        }

        execute(launchClassLoader, target, args, false);
    }

    static void launchFinalInject(Path jarPath, String... args) throws Throwable {
        launchFinalInject(Objects.requireNonNull(getFromPath(jarPath)), args);
    }

    static void launchFinalInject(Target target, String... args) throws Throwable {
        injectSystemClassPath(target.classPath);
        execute(ClassLoader.getSystemClassLoader(), target, args, false);
    }

    static void injectSystemClassPath(Collection<URL> classPath) throws Throwable {
        var systemClassLoader = ClassLoader.getSystemClassLoader();

        if (systemClassLoader instanceof URLClassLoader) { // This is true for Java 8
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            for (var url : classPath) {
                method.invoke(systemClassLoader, url);
            }
        } else if (InstrumentationCatcher.exists()) { // And this for Java 9+
            var instr = InstrumentationCatcher.get();
            for (var url : classPath) {
                var jar = new JarFile(Path.of(url.toURI()).toFile());
                instr.appendToSystemClassLoaderSearch(jar);
            }
        } else {
            throw new RuntimeException("Couldn't add files to classpath");
        }

        var currentClassPath = new StringBuilder(System.getProperty("java.class.path"));
        for (var url : classPath) {
            var path = Path.of(url.toURI()).toString();
            currentClassPath.append(File.pathSeparatorChar).append(path);
        }
        System.setProperty("java.class.path", currentClassPath.toString());
    }

    record Target(Collection<URL> classPath, String mainClass, @Nullable String launcherAgentClass, List<String> fileSystemProviders) {
        public static Target of(URL path, String mainClass) {
            return new Target(List.of(path), mainClass, null, List.of());
        }
    }
}
