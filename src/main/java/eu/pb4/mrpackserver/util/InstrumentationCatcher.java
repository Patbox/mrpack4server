package eu.pb4.mrpackserver.util;

import java.lang.instrument.Instrumentation;

public class InstrumentationCatcher {
    private static Instrumentation instrumentation;
    public static void agentmain(String arg, Instrumentation instrumentation) {
        InstrumentationCatcher.instrumentation = instrumentation;
    }

    public static boolean exists() {
        return instrumentation != null;
    }

    public static Instrumentation get() {
        return instrumentation;
    }
}
