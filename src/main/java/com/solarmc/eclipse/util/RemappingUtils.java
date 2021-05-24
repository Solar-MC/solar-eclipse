package com.solarmc.eclipse.util;

import com.solarmc.eclipse.bytecode.SolarClassRemapper;
import com.solarmc.eclipse.util.lunar.PatchInfo;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.TextMappingsWriter;
import org.cadixdev.lorenz.model.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
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

    public static MappingSet createAndWriteMappingSet(Map<String, String> classMappings, Path path, String from, String to) {
        MappingSet set = toMappingSet(classMappings);
        writeMappings(set, path, from, to);
        return set;
    }

    public static void remapJar(MappingSet mappingSet, String from, String to, Path inJar, Path outJar) {
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(out -> toTinyRemapperFormat(out, mappingSet))
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

    private static void toTinyRemapperFormat(IMappingProvider.MappingAcceptor out, MappingSet mappingSet) {
        iterateClasses(mappingSet, classMapping -> {
            String owner = classMapping.getFullObfuscatedName();
            out.acceptClass(owner, classMapping.getFullDeobfuscatedName());

            for (MethodMapping methodMapping : classMapping.getMethodMappings()) {
                IMappingProvider.Member method = new IMappingProvider.Member(owner, methodMapping.getObfuscatedName(), methodMapping.getObfuscatedDescriptor());
                out.acceptMethod(method, methodMapping.getDeobfuscatedName());
                for (MethodParameterMapping parameterMapping : methodMapping.getParameterMappings()) {
                    out.acceptMethodArg(method, parameterMapping.getIndex(), parameterMapping.getDeobfuscatedName());
                }
            }

            for (FieldMapping fieldMapping : classMapping.getFieldMappings()) {
                out.acceptField(new IMappingProvider.Member(owner, fieldMapping.getObfuscatedName(), fieldMapping.getType().get().toString()), fieldMapping.getDeobfuscatedName());
            }
        });
    }

    /**
     * Iterates through all the {@link TopLevelClassMapping} and {@link InnerClassMapping} in a {@link MappingSet}
     *
     * @param mappings The mappings
     * @param consumer The consumer of the {@link ClassMapping}
     */
    public static void iterateClasses(MappingSet mappings, Consumer<ClassMapping<?, ?>> consumer) {
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
                    fieldMapping.getType().ifPresent(fieldType -> {
                        writer.println("\tf\t" + fieldType + "\t" + fieldMapping.getObfuscatedName() + "\t" + fieldMapping.getDeobfuscatedName());
                    });
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
