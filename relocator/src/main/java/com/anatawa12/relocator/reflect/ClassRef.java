package com.anatawa12.relocator.reflect;

import com.anatawa12.relocator.internal.InternalAccessorKt;
import org.jetbrains.annotations.NotNull;

public class ClassRef {
    private final com.anatawa12.relocator.internal.ClassRef param;

    static {
        InternalAccessorKt.publicToInternalClassRef = (self) -> self.param;
    }

    private ClassRef(com.anatawa12.relocator.internal.ClassRef param) {
        this.param = param;
    }

    public static @NotNull ClassRef named(@NotNull StringRef nameRef) {
        return new ClassRef(com.anatawa12.relocator.internal.ClassRef.named(nameRef));
    }

    public static @NotNull ClassRef named(@NotNull String name) {
        return new ClassRef(com.anatawa12.relocator.internal.ClassRef.named(StringRef.constant(name)));
    }

    public static @NotNull ClassRef descriptor(@NotNull StringRef descriptorRef) {
        return new ClassRef(com.anatawa12.relocator.internal.ClassRef.descriptor(descriptorRef));
    }

    public static @NotNull ClassRef param(int index) {
        if (index == -1) return thisParam;
        return new ClassRef(com.anatawa12.relocator.internal.ClassRef.param(index));
    }

    /** The class reference to primitive {@code void} */
    public static final @NotNull ClassRef VOID = new ClassRef(com.anatawa12.relocator.internal.ClassRef.VOID);
    /** The class reference to primitive {@code byte} */
    public static final @NotNull ClassRef BYTE = new ClassRef(com.anatawa12.relocator.internal.ClassRef.BYTE);
    /** The class reference to primitive {@code char} */
    public static final @NotNull ClassRef CHAR = new ClassRef(com.anatawa12.relocator.internal.ClassRef.CHAR);
    /** The class reference to primitive {@code double} */
    public static final @NotNull ClassRef DOUBLE = new ClassRef(com.anatawa12.relocator.internal.ClassRef.DOUBLE);
    /** The class reference to primitive {@code float} */
    public static final @NotNull ClassRef FLOAT = new ClassRef(com.anatawa12.relocator.internal.ClassRef.FLOAT);
    /** The class reference to primitive {@code int} */
    public static final @NotNull ClassRef INT = new ClassRef(com.anatawa12.relocator.internal.ClassRef.INT);
    /** The class reference to primitive {@code long} */
    public static final @NotNull ClassRef LONG = new ClassRef(com.anatawa12.relocator.internal.ClassRef.LONG);
    /** The class reference to primitive {@code short} */
    public static final @NotNull ClassRef SHORT = new ClassRef(com.anatawa12.relocator.internal.ClassRef.SHORT);
    /** The class reference to primitive {@code boolean} */
    public static final @NotNull ClassRef BOOLEAN = new ClassRef(com.anatawa12.relocator.internal.ClassRef.BOOLEAN);

    /** The class reference to this receiver */
    public static final @NotNull ClassRef thisParam = new ClassRef(com.anatawa12.relocator.internal.ClassRef.thisParam);

    @Override
    public String toString() {
        return param.toString();
    }
}
