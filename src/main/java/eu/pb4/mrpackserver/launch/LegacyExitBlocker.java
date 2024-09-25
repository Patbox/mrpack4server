package eu.pb4.mrpackserver.launch;

import eu.pb4.mrpackserver.util.FlexVerComparator;

import java.security.Permission;

public class LegacyExitBlocker {
    @SuppressWarnings("removal")
    public static void run(Runnable runnable) {
        if (FlexVerComparator.compare(System.getProperty("java.version"), "16") < 0) {
            var x = System.getSecurityManager();
            System.setSecurityManager(new Preventer());
            runnable.run();
            System.setSecurityManager(x);
        } else {
            runnable.run();
        }
    }



    @SuppressWarnings("removal")
    private static final class Preventer extends SecurityManager {
        private int lastCode = -1;
        @Override
        public void checkExit(int status) {
            if (lastCode == -1) {
                lastCode = status;
                throw new ExitError(status);
            }

            throw new ExitError(lastCode);
        }

        @Override
        public void checkPermission(Permission perm) {}
    }
}
