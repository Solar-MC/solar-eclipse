package com.solarmc.eclipse.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class BytecodeFixerVisitor extends ClassVisitor {

    private String className;

    public BytecodeFixerVisitor(ClassWriter writer) {
        super(Opcodes.ASM9, writer);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (className.contains("lunar") && !((access & Opcodes.ACC_ABSTRACT) > 0 || (access & Opcodes.ACC_NATIVE) > 0)) {
            return new BytecodeFixerMethodVisitor(this, descriptor, superVisitor);
        }
        return superVisitor;
    }


    static class BytecodeFixerMethodVisitor extends MethodVisitor {

        private boolean codeVisited;

        public BytecodeFixerMethodVisitor(BytecodeFixerVisitor classVisitor, String descriptor, MethodVisitor parent) {
            super(classVisitor.api, parent);
        }

        @Override
        public void visitCode() {
            codeVisited = true;
            super.visitCode();
        }

        @Override
        public void visitEnd() {
            if (!codeVisited) {
                visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/solarmc/eclipse/bytecode/BytecodeFixer",
                        "handleError",
                        "()Ljava/lang/RuntimeException;",
                        false
                );
                visitInsn(Opcodes.ATHROW);
                visitMaxs(2, 0);
            }
            super.visitEnd();
        }
    }
}
