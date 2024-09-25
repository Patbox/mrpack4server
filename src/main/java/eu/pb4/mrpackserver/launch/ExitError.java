package eu.pb4.mrpackserver.launch;

public class ExitError extends Error {
    public final int code;

    public ExitError(int code) {
        this.code = code;
    }
}