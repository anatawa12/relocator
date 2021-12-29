package com.anatawa12.relocator.classes;

import com.anatawa12.relocator.internal.DescriptorSignatures;
import com.anatawa12.relocator.internal.InternalAccessorKt;
import kotlin.annotations.jvm.Mutable;
import kotlin.annotations.jvm.ReadOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    public static @NotNull TypeParameter parse(@NotNull String signature) {
        Objects.requireNonNull(signature, "signature must not null");
        return DescriptorSignatures.parseTypeParameter(signature);
    }

    public static final class Builder {
        private final @NotNull String name;
        private @Nullable TypeSignature classBound;
        @Mutable
        private final @NotNull ArrayList<@NotNull TypeSignature> interfaceBounds = new ArrayList<>();
        private volatile boolean building = true;

        public Builder(@NotNull String name) {
            Objects.requireNonNull(name, "name must not null");
            this.name = name;
        }

        private void checkBuilding() {
            if (!building)
                throw new IllegalStateException("this builder has benn built");
        }

        public @NotNull Builder classBound(TypeSignature classBound) {
            Objects.requireNonNull(classBound, "classBound must not null");
            checkBuilding();
            if (this.classBound != null) throw new IllegalStateException("classBound has been specified");
            this.classBound = classBound;
            return this;
        }

        public @NotNull Builder addInterfaceBound(TypeSignature interfaceBound) {
            Objects.requireNonNull(interfaceBound, "interfaceBound must not null");
            checkBuilding();
            this.interfaceBounds.add(interfaceBound);
            return this;
        }

        public @NotNull TypeParameter build() {
            if (interfaceBounds.isEmpty() && classBound == null)
                throw new IllegalStateException("at least one bound must be specified");
            building = false;
            interfaceBounds.trimToSize();
            return new TypeParameter(name, classBound, interfaceBounds);
        }
    }
}
