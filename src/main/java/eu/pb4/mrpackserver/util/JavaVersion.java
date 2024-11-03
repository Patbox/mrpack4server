package eu.pb4.mrpackserver.util;

public interface JavaVersion {
    boolean IS_JAVA_9 = Runtime.version().feature() >= 9;
    boolean IS_JAVA_16 = Runtime.version().feature() >= 16;
    boolean IS_JAVA_17 = Runtime.version().feature() >= 17;
    boolean IS_JAVA_21 = Runtime.version().feature() >= 21;
}
