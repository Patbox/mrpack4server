package eu.pb4.mrpackserver.util;

import java.util.Arrays;

public class Logger {

    private static boolean fullName = false;

    public static void useFullName() {
        fullName = true;
    }

    public static void useShortName() {
        fullName = false;
    }
    
    public static void info(String text, Object... objects) {
        System.out.printf((fullName ? Constants.LOG_PREFIX : Constants.LOG_PREFIX_SMALL) + (text) + " %n", objects);
    }

    public static void label(String text, Object... objects) {
        System.out.append((text + " ").formatted(objects));
    }

    public static void warn(String text, Object... objects) {
        System.out.printf((fullName ? Constants.LOG_WARN_PREFIX : Constants.LOG_WARN_PREFIX_SMALL) + (text) + " %n", objects);
    }

    public static void error(String text, Object... objects) {
        Throwable throwable = null;
        if (objects.length > 0 && objects[objects.length - 1] instanceof Throwable x) {
            objects = Arrays.copyOf(objects, objects.length - 1);
            throwable = x;
        }

        System.err.printf((fullName ? Constants.LOG_ERROR_PREFIX : Constants.LOG_ERROR_PREFIX_SMALL) + (text) + " %n", objects);
        if (throwable != null) {
            try {
                throwable.printStackTrace(System.err);
            } catch (Throwable e) {
                System.err.println("Failed to write exception! Using a fallback!");
                System.err.println("Class: " + throwable.getClass().getName());
                for (var traceElement : e.getStackTrace()) {
                    System.err.println("\tat " + traceElement);
                }
            }
        }
    }
}
