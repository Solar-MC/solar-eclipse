package com.solarmc.eclipse.bytecode;

import com.solarmc.eclipse.util.lunar.PatchInfo;
import org.objectweb.asm.commons.Remapper;

public class SolarClassRemapper extends Remapper {

    private final PatchInfo info;

    public SolarClassRemapper(PatchInfo info) {
        this.info = info;
    }

    @Override
    public String map(String internalName) {
        String unmappedName = internalName.replace('/', '.');
        String mappedName = info.classMappings.getOrDefault(unmappedName, unmappedName);

        if (!unmappedName.equals(mappedName)) {
            return mappedName.replace(".", "/");
        }
        return internalName;
    }
}
