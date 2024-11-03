package eu.pb4.mrpackserver.util;

import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class InstrumentationCatcher {
    private static Instrumentation instrumentation;
    public static void agentmain(String arg, Instrumentation instrumentation) {
        premain(arg, instrumentation);
    }

    public static void premain(String arg, Instrumentation instrumentation) {
        InstrumentationCatcher.instrumentation = instrumentation;
    }

    public static void open(String module, String pkg) {
        if (instrumentation != null && JavaVersion.IS_JAVA_9) {
            instrumentation.redefineModule(
                    ModuleLayer.boot().findModule(module).orElseThrow(),
                    Collections.emptySet(),
                    Collections.emptyMap(),
                    Map.of(pkg, Set.of(InstrumentationCatcher.class.getModule())),
                    Collections.emptySet(),
                    Collections.emptyMap()
            );
        }
    }

    public static boolean exists() {
        return instrumentation != null;
    }

    public static Instrumentation get() {
        return instrumentation;
    }
}
