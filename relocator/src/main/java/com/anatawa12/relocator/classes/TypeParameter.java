package com.anatawa12.relocator.classes;

import com.anatawa12.relocator.internal.DescriptorSignatures;
import com.anatawa12.relocator.internal.InternalAccessorKt;
import kotlin.annotations.jvm.Mutable;
import kotlin.annotations.jvm.ReadOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TypeParameter {
    private final @NotNull String name;
    private final @Nullable TypeSignature classBound;

    @ReadOnly
    private final @NotNull List<? extends @NotNull TypeSignature> interfaceBounds;

    private TypeParameter(@NotNull String name, @Nullable TypeSignature classBound,
                          @ReadOnly @NotNull List<? extends @NotNull TypeSignature> interfaceBounds) {
        this.name = name;
        this.classBound = classBound;
        this.interfaceBounds = interfaceBounds;
    }

    public @NotNull String getName() {
        return name;
    }

    public @Nullable TypeSignature getClassBound() {
        return classBound;
    }

    @ReadOnly
    public @NotNull List<? extends @NotNull TypeSignature> getInterfaceBounds() {
        return interfaceBounds;
    }

    public static @NotNull TypeParameter of(@NotNull String name, @NotNull TypeSignature classBound) {
        Objects.requireNonNull(name, "name must not null");
        Objects.requireNonNull(classBound, "classBound must not null");
        DescriptorSignatures.parseSimpleName(name, "name of type parameter");
        if (classBound.getKind() == TypeSignature.Kind.Primitive)
            throw new IllegalArgumentException("primitive is not allowed for classBound");
        return new TypeParameter(name, classBound, Collections.emptyList());
    }

    public static @NotNull TypeParameter parse(@NotNull String signature) {
        Objects.requireNonNull(signature, "signature must not null");
        return DescriptorSignatures.parseTypeParameter(signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeParameter that = (TypeParameter) o;

        if (!name.equals(that.name)) return false;
        if (!Objects.equals(classBound, that.classBound)) return false;
        return interfaceBounds.equals(that.interfaceBounds);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (classBound != null ? classBound.hashCode() : 0);
        result = 31 * result + interfaceBounds.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append(':');
        if (classBound != null) builder.append(classBound);
        for (TypeSignature interfaceBound : interfaceBounds)
            builder.append(':').append(interfaceBound);
        return builder.toString();
    }

    public static final class Builder {
        private final @NotNull String name;
        private @Nullable TypeSignature classBound;
        @Mutable
        private @Nullable ArrayList<@NotNull TypeSignature> interfaceBounds = null;
        private volatile boolean building = true;

        public Builder(@NotNull String name) {
            Objects.requireNonNull(name, "name must not null");
            DescriptorSignatures.parseSimpleName(name, "name of type parameter");
            this.name = name;
        }

        private void checkBuilding() {
            if (!building)
                throw new IllegalStateException("this builder has benn built");
        }

        public @NotNull Builder classBound(@NotNull TypeSignature classBound) {
            Objects.requireNonNull(classBound, "classBound must not null");
            if (classBound.getKind() == TypeSignature.Kind.Primitive)
                throw new IllegalArgumentException("primitive is not allowed for classBound");
            checkBuilding();
            if (this.classBound != null) throw new IllegalStateException("classBound has been specified");
            this.classBound = classBound;
            return this;
        }

        public @NotNull Builder addInterfaceBound(TypeSignature interfaceBound) {
            Objects.requireNonNull(interfaceBound, "interfaceBound must not null");
            if (interfaceBound.getKind() == TypeSignature.Kind.Primitive)
                throw new IllegalArgumentException("primitive is not allowed for interfaceBound");
            checkBuilding();
            @Nullable ArrayList<@NotNull TypeSignature> interfaceBounds = this.interfaceBounds;
            if (interfaceBounds == null) interfaceBounds = this.interfaceBounds = new ArrayList<>(1);
            interfaceBounds.add(interfaceBound);
            return this;
        }

        public @NotNull TypeParameter build() {
            @Nullable ArrayList<@NotNull TypeSignature> interfaceBounds = this.interfaceBounds;
            if (interfaceBounds == null && classBound == null)
                throw new IllegalStateException("at least one bound must be specified");
            building = false;
            if (interfaceBounds != null) {
                interfaceBounds.trimToSize();
                return new TypeParameter(name, classBound, interfaceBounds);
            } else {
                return new TypeParameter(name, classBound, Collections.emptyList());
            }
        }

        static {
            InternalAccessorKt.newTypeParameter = TypeParameter::new;
        }
    }
}
