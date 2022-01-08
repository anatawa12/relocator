package com.anatawa12.relocator.classes;

import com.anatawa12.relocator.internal.DescriptorSignatures;
import com.anatawa12.relocator.internal.InternalAccessorKt;
import com.anatawa12.relocator.internal.RelocationMappingPrimitiveMarker;
import kotlin.annotations.jvm.Mutable;
import kotlin.annotations.jvm.ReadOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ClassSignature implements RelocationMappingPrimitiveMarker {
    @ReadOnly
    private final @NotNull List<@NotNull TypeParameter> typeParameters;
    private final @NotNull TypeSignature superClass;
    @ReadOnly
    private final @NotNull List<? extends @NotNull TypeSignature> superInterfaces;
    private @Nullable String signature;

    private ClassSignature(
            final @ReadOnly @NotNull List<@NotNull TypeParameter> typeParameters,
            final @NotNull TypeSignature superClass,
            final @ReadOnly @NotNull List<? extends @NotNull TypeSignature> superInterfaces,
            final @Nullable String signature) {
        this.typeParameters = typeParameters;
        this.superClass = superClass;
        this.superInterfaces = superInterfaces;
        this.signature = signature;
    }

    public static @NotNull ClassSignature parse(@NotNull String signature) {
        Objects.requireNonNull(signature, "signature must not null");
        return DescriptorSignatures.parseClassSignature(signature);
    }

    public @NotNull String getSignature() {
        if (signature != null) return signature;
        StringBuilder builder = new StringBuilder();

        DescriptorSignatures.appendParams(builder, this.typeParameters);
        builder.append(superClass);
        for (final TypeSignature typeSignature : this.superInterfaces)
            builder.append(typeSignature);
        return signature = builder.toString();
    }

    @ReadOnly
    public @NotNull List<? extends @NotNull TypeParameter> getTypeParameters() {
        return typeParameters;
    }

    public @NotNull TypeSignature getSuperClass() {
        return superClass;
    }

    @ReadOnly
    public @NotNull List<? extends @NotNull TypeSignature> getSuperInterfaces() {
        return superInterfaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return getSignature().equals(((ClassSignature) o).getSignature());
    }

    @Override
    public int hashCode() {
        return getSignature().hashCode();
    }

    @Override
    public String toString() {
        return getSignature();
    }

    public static final class Builder {
        @Mutable
        private final @NotNull ArrayList<@NotNull TypeParameter> typeParameters = new ArrayList<>();
        private @Nullable TypeSignature superClass = null;
        @Mutable
        private final @NotNull ArrayList<TypeSignature> superInterfaces = new ArrayList<>();
        private volatile boolean building = true;

        private void checkBuilding() {
            if (!building) throw new IllegalStateException("this builder has been built");
        }

        public @NotNull Builder addTypeParam(final @NotNull TypeParameter parameter) {
            Objects.requireNonNull(parameter, "parameter must not null");
            checkBuilding();
            this.typeParameters.add(parameter);
            return this;
        }

        public @NotNull Builder superClass(final @NotNull TypeSignature superClass) {
            Objects.requireNonNull(superClass, "superClass must not null");
            checkBuilding();
            if (this.superClass != null) throw new IllegalStateException("return type has been specified");
            if (superClass.getKind() != TypeSignature.Kind.Class)
                throw new IllegalArgumentException(superClass.getKind() + " is not allowed for super class");
            this.superClass = superClass;
            return this;
        }

        public @NotNull Builder addInterface(final @NotNull TypeSignature superInterface) {
            Objects.requireNonNull(superInterface, "superInterface must not null");
            if (superInterface.getKind() != TypeSignature.Kind.Class)
                throw new IllegalArgumentException(superInterface.getKind() + " is not allowed for super interface");
            checkBuilding();
            this.superInterfaces.add(superInterface);
            return this;
        }

        public @NotNull ClassSignature build() {
            return buildInternal(null);
        }

        private @NotNull ClassSignature buildInternal(final @Nullable String signature) {
            if (superClass == null)
                throw new IllegalStateException("super class is not specified");
            building = false;

            typeParameters.trimToSize();
            superInterfaces.trimToSize();

            return new ClassSignature(Collections.unmodifiableList(typeParameters),
                    superClass,
                    Collections.unmodifiableList(superInterfaces), 
                    signature);
        }

        static {
            InternalAccessorKt.classSignatureBuilderBuildInternal = Builder::buildInternal;
            InternalAccessorKt.newClassSignature = ClassSignature::new;
        }
    }
}
