package eu.pb4.forgefx;


import eu.pb4.mrpackserver.launch.ExitError;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.channels.Channel;
import java.util.Properties;

public class S {
    public static final InputStream in = System.in;
    public static final PrintStream out = System.out;
    public static final PrintStream err = System.err;

    public static void setIn(InputStream in) {
    }

    public static void setOut(PrintStream out) {
    }

    public static void setErr(PrintStream err) {
    }


    public static Console console() {
        return System.console();
    }


    public static Channel inheritedChannel() throws IOException {
        return System.inheritedChannel();
    }
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    public static void setSecurityManager(@SuppressWarnings("removal") SecurityManager sm) {
        System.setSecurityManager(sm);
    }

    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    public static SecurityManager getSecurityManager() {
        return System.getSecurityManager();
    }

    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    public static long nanoTime() {
        return System.nanoTime();
    }

    public static void arraycopy(Object src, int srcPos,
                                 Object dest, int destPos,
                                 int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
    }

    public static int identityHashCode(Object x) {
        return System.identityHashCode(x);
    }

    public static Properties getProperties() {
        return System.getProperties();
    }

    public static String lineSeparator() {
        return System.lineSeparator();
    }

    public static void setProperties(Properties props) {
        System.setProperties(props);
    }

    public static String getProperty(String key) {
        return System.getProperty(key);
    }

    public static String getProperty(String key, String def) {
        return System.getProperty(key, def);
    }

    public static String setProperty(String key, String value) {
        return System.setProperty(key, value);
    }

    public static String clearProperty(String key) {
        return System.clearProperty(key);
    }

    public static String getenv(String name) {
        return System.getenv(name);
    }

    public static java.util.Map<String, String> getenv() {
        return System.getenv();
    }

    //public static System.Logger getLogger(String name) {
    //  return System.getLogger(name);
    //}

    //public static System.Logger getLogger(String name, ResourceBundle bundle) {
    //    return System.getLogger(name, bundle);
    //}

    public static void exit(int status) {
        if (status != 0) {
            System.exit(status);
        } else {
            throw new ExitError(status);
        }
    }

    public static void gc() {
        System.gc();
    }

    public static void runFinalization() {
        //noinspection removal
        System.runFinalization();
    }

    public static void load(String filename) {
        System.load(filename);
    }

    public static void loadLibrary(String libname) {
        System.loadLibrary(libname);
    }

    public static String mapLibraryName(String libname) {
        return System.mapLibraryName(libname);
    };
}