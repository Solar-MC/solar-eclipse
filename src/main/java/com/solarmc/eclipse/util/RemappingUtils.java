package com.solarmc.eclipse.util;

import com.solarmc.eclipse.bytecode.SolarClassRemapper;
import com.solarmc.eclipse.util.lunar.PatchInfo;
import net.fabricmc.tinyremapper.*;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsWriter;
import org.cadixdev.lorenz.model.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public class RemappingUtils {

    public static byte[] remap(PatchInfo info, byte[] originalBytecode) {
        Remapper remapper = new SolarClassRemapper(info);
        ClassWriter writer = new ClassWriter(0);
        ClassRemapper classRemapper = new ClassRemapper(writer, remapper);
        new ClassReader(originalBytecode).accept(classRemapper, ClassReader.SKIP_DEBUG);
        return writer.toByteArray();
    }

    public static MappingSet toMappingSet(Map<String, String> classMappings) {
        MappingSet mappings = MappingSet.create();
        for (String obf : classMappings.keySet()) {
            String mapped = classMappings.get(obf);
            mappings.getOrCreateClassMapping(obf).setDeobfuscatedName(mapped);
        }
        return mappings;
    }

    public static void writeMappings(MappingSet mappings, Path path, String from, String to) {
        try (Writer writer = new StringWriter()) {
            new TinyWriter(writer, from, to).write(mappings);
            Files.writeString(path, writer.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write tiny mappings", e);
        }
    }

    public static void createAndWriteMappingSet(Map<String, String> classMappings, Path path, String from, String to) {
        MappingSet set = toMappingSet(classMappings);
        writeMappings(set, path, from, to);
    }

    public static void remapJar(Path mappingPath, String from, String to, Path inJar, Path outJar) {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createTinyMappingProvider(mappingPath, from, to))
                .ignoreConflicts(true)
                .fixPackageAccess(true)
                .build();

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outJar).build()) {
            outputConsumer.addNonClassFiles(inJar, NonClassCopyMode.FIX_META_INF, remapper);
            remapper.readInputs(inJar);
            remapper.apply(outputConsumer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remap jar", e);
        }
    }

    private static class TinyWriter extends TextMappingsWriter {
        private final String from;
        private final String to;

        public TinyWriter(Writer writer, String from, String to) {
            super(writer);
            this.from = from;
            this.to = to;
        }

        @Override
        public void write(MappingSet mappings) {
            writer.println("tiny\t2\t0\t" + from + "\t" + to);

            iterateClasses(mappings, classMapping -> {
                writer.println("c\t" + classMapping.getFullObfuscatedName() + "\t" + classMapping.getFullDeobfuscatedName());

                for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
                    fieldMapping.getType().ifPresent(fieldType -> writer.println("\tf\t" + fieldType + "\t" + fieldMapping.getObfuscatedName() + "\t" + fieldMapping.getDeobfuscatedName()));
                }

                for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
                    writer.println("\tm\t" + methodMapping.getSignature().getDescriptor() + "\t" + methodMapping.getObfuscatedName() + "\t" + methodMapping.getDeobfuscatedName());
                }
            });
        }

        private static void iterateClasses(MappingSet mappings, Consumer<ClassMapping<?, ?>> consumer) {
            for (TopLevelClassMapping classMapping : mappings.getTopLevelClassMappings()) {
                iterateClass(classMapping, consumer);
            }
        }

        private static void iterateClass(ClassMapping<?, ?> classMapping, Consumer<ClassMapping<?, ?>> consumer) {
            consumer.accept(classMapping);

            for (InnerClassMapping innerClassMapping : classMapping.getInnerClassMappings()) {
                iterateClass(innerClassMapping, consumer);
            }
        }
    }
}
