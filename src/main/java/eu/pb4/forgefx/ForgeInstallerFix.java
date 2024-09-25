package eu.pb4.forgefx;

import java.util.Set;

public class ForgeInstallerFix extends ByteSwappingFix {
    public ForgeInstallerFix(ClassLoader loader) {
        super(loader, Set.of("net/minecraftforge/installer/SimpleInstaller"),
                Pair.of("java/lang/System", "eu/pb4/forgefx/S")
        );
    }
}
