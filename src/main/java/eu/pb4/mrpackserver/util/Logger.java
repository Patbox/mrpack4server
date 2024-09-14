package eu.pb4.mrpackserver.util;

import java.util.Arrays;

public interface Logger {
    static void info(String text, Object... objects) {
        System.out.printf(Constants.LOG_PREFIX + (text) + " %n", objects);
    }

    static void warn(String text, Object... objects) {
        System.out.printf(Constants.LOG_WARN_PREFIX + (text) + " %n", objects);
    }

    static void error(String text, Object... objects) {
        Throwable throwable = null;
        if (objects.length > 0 && objects[objects.length - 1] instanceof Throwable x) {
            objects = Arrays.copyOf(objects, objects.length - 1);
            throwable = x;
        }

        System.err.printf(Constants.LOG_ERROR_PREFIX + (text) + " %n", objects);
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
