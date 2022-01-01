package com.anatawa12.relocator.classes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class TypeArgument {
    private final @Nullable TypeSignature type;
    private final @NotNull TypeVariant variant;

    private TypeArgument(final @Nullable TypeSignature type, final @NotNull TypeVariant variant) {
        this.type = type;
        this.variant = variant;
    }

    /**
     * @return null for wildcard, non-null for 
     */
    public @Nullable TypeSignature getType() {
        return type;
    }

    public @NotNull TypeVariant getVariant() {
        return variant;
    }

    public static final @NotNull TypeArgument STAR = new TypeArgument(null, TypeVariant.Covariant);

    public static @NotNull TypeArgument of(final @NotNull TypeSignature type, final @NotNull TypeVariant variant) {
        Objects.requireNonNull(type, "type must not null");
        Objects.requireNonNull(variant, "variant must not null");
        if (type.getKind() == TypeSignature.Kind.Primitive)
            throw new IllegalArgumentException("primitive is not allowed for type");
        return new TypeArgument(type, variant);
    }
}
