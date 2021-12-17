package com.anatawa12.relocator.reflect;

import com.anatawa12.relocator.internal.InternalAccessorKt;
import org.jetbrains.annotations.NotNull;

public class MethodTypeRef {
    private final com.anatawa12.relocator.internal.MethodTypeRef param;

    static {
        InternalAccessorKt.publicToInternalMethodTypeRef = (self) -> self.param;
    }

    private MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef param) {
        this.param = param;
    }

    public static @NotNull MethodTypeRef partialDescriptor(@NotNull StringRef nameRef) {
        return new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.partialDescriptor(nameRef));
    }

    public static @NotNull MethodTypeRef fullDescriptor(@NotNull StringRef name) {
        return new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.fullDescriptor(name));
    }

    public static @NotNull MethodTypeRef fullDescriptor(@NotNull String name) {
        return new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.fullDescriptor(name));
    }

    public static @NotNull MethodTypeRef parameterTypes(int index) {
        if (index == -1) return thisParameterTypes;
        return new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.parameterTypes(index));
    }

    public static @NotNull MethodTypeRef parameterAndReturnTypes(int index, ClassRef returns) {
        if (index == -1)
            return new MethodTypeRef(
                    com.anatawa12.relocator.internal.MethodTypeRef.thisParametersAndReturnTypes(returns));
        return new MethodTypeRef(
                com.anatawa12.relocator.internal.MethodTypeRef.parameterAndReturnTypes(index, returns));
    }

    public static @NotNull MethodTypeRef param(int index) {
        if (index == -1) return thisParam;
        return new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.param(index));
    }

    /**
     * The parameter types only method type reference to this receiver
     */
    public static @NotNull MethodTypeRef thisParameterTypes =
            new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.thisParameterTypes);
    /**
     * The method type reference to this receiver
     */
    public static @NotNull MethodTypeRef thisParam =
            new MethodTypeRef(com.anatawa12.relocator.internal.MethodTypeRef.thisParam);

    @Override
    public String toString() {
        return param.toString();
    }
}
