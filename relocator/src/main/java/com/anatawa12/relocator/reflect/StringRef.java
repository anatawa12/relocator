package com.anatawa12.relocator.reflect;

import com.anatawa12.relocator.internal.InternalAccessorKt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

// TODO: rewrite in kotlin when static without companion came
public class StringRef {
    private final com.anatawa12.relocator.internal.StringRef param;

    static {
        InternalAccessorKt.publicToInternalStringRef = (self) -> self.param;
    }

    private StringRef(com.anatawa12.relocator.internal.StringRef param) {
        this.param = param;
    }

    public static StringRef param(int index) {
        if (index == -1) return thisParam;
        return new StringRef(com.anatawa12.relocator.internal.StringRef.param(index));
    }

    public static StringRef constant(@NotNull String value) {
        return new StringRef(com.anatawa12.relocator.internal.StringRef.constant(value));
    }

    public static StringRef joined(@NotNull StringRef @NotNull ... values) {
        return new StringRef(com.anatawa12.relocator.internal.StringRef.joined(values));
    }

    public static StringRef joined(@NotNull List<@NotNull StringRef> values) {
        return new StringRef(com.anatawa12.relocator.internal.StringRef.joined(values));
    }

    /** The string reference to this receiver */
    public static final @NotNull StringRef thisParam = new StringRef(com.anatawa12.relocator.internal.StringRef.thisParam);

    @Override
    public String toString() {
        return param.toString();
    }
}
