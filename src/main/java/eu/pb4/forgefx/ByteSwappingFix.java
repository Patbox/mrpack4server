package eu.pb4.forgefx;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Set;

public class ByteSwappingFix implements ClassFileTransformer {
    private final ClassLoader targetLoader;
    private final Pair[] replacements;
    private final Set<String> classes;

    public ByteSwappingFix(ClassLoader loader, Set<String> classes, Pair... replacements) {
        this.targetLoader = loader;
        this.classes = classes;
        this.replacements = replacements;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (loader != this.targetLoader || !this.classes.contains(className)) {
            return null;
        }

        byte[] out = null;

        for (int i = 0; i < classfileBuffer.length; i++) {
            for (var rep : this.replacements) {
                var found = true;
                for (int c = 0; c < rep.from.length; c++) {
                    if (classfileBuffer[i + c] != rep.from[c]) {
                        found = false;
                        break;
                    }
                }

                if (found) {
                    if (out == null) {
                        out = Arrays.copyOf(classfileBuffer, classfileBuffer.length);
                    }
                    System.arraycopy(rep.to, 0, out, i, rep.to.length);
                }
            }
        }

        return out;
    }

    public record Pair(byte[] from, byte[] to) {
        public static Pair of(String from, String to) {
            return new Pair(from.getBytes(StandardCharsets.UTF_8), to.getBytes(StandardCharsets.UTF_8));
        }
        public Pair {
            assert from.length == to.length;
        }
    }
}
