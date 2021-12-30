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

public final class MethodSignature {
    @ReadOnly
    private final @NotNull List<@NotNull TypeParameter> typeParameters;
    @ReadOnly
    private final @NotNull List<? extends @NotNull TypeSignature> valueParameters;
    private final @NotNull TypeSignature returns;
    @ReadOnly
    private final @NotNull List<? extends @NotNull TypeSignature> throwsTypes;
    private @Nullable String signature;

    private MethodSignature(
            final @ReadOnly @NotNull List<@NotNull TypeParameter> typeParameters,
            final @ReadOnly @NotNull List<? extends @NotNull TypeSignature> valueParameters,
            final @NotNull TypeSignature returns,
            final @ReadOnly @NotNull List<? extends @NotNull TypeSignature> throwsTypes,
            final @Nullable String signature) {
        this.typeParameters = typeParameters;
        this.valueParameters = valueParameters;
        this.returns = returns;
        this.throwsTypes = throwsTypes;
        this.signature = signature;
    }

    public static @NotNull MethodSignature parse(@NotNull String signature) {
        Objects.requireNonNull(signature, "signature must not null");
        return DescriptorSignatures.parseMethodSignature(signature);
    }

    public @NotNull String getSignature() {
        if (signature != null) return signature;
        StringBuilder builder = new StringBuilder();

        DescriptorSignatures.appendParams(builder, this.typeParameters);
        builder.append('(');
        for (TypeSignature valueParameter : this.valueParameters)
            builder.append(valueParameter);
        builder.append(')');
        builder.append(returns);
        for (final TypeSignature typeSignature : this.throwsTypes)
        builder.append('^').append(typeSignature);
        return signature = builder.toString();
    }

    @ReadOnly
    public @NotNull List<? extends @NotNull TypeParameter> getTypeParameters() {
        return typeParameters;
    }

    @ReadOnly
    public @NotNull List<? extends @NotNull TypeSignature> getValueParameters() {
        return valueParameters;
    }

    public @NotNull TypeSignature getReturns() {
        return returns;
    }

    @ReadOnly
    public @NotNull List<? extends @NotNull TypeSignature> getThrowsTypes() {
        return throwsTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return getSignature().equals(((MethodSignature) o).getSignature());
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
        @Mutable
        private final @NotNull ArrayList<@NotNull TypeSignature> valueParameters = new ArrayList<>();
        private @Nullable TypeSignature returns = null;
        @Mutable
        private final @NotNull ArrayList<TypeSignature> throwsTypes = new ArrayList<>();
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

        public @NotNull Builder addValueParam(final @NotNull TypeSignature parameter) {
            Objects.requireNonNull(parameter, "parameter must not null");
            if (parameter == TypeSignature.VOID) throw new IllegalArgumentException("void is not allowed for argument");
            checkBuilding();
            this.valueParameters.add(parameter);
            return this;
        }

        public @NotNull Builder returns(final @NotNull TypeSignature returns) {
            Objects.requireNonNull(returns, "returns must not null");
            checkBuilding();
            if (this.returns != null) throw new IllegalStateException("return type has been specified");
            this.returns = returns;
            return this;
        }

        public @NotNull Builder addThrows(final @NotNull TypeSignature throwsType) {
            Objects.requireNonNull(throwsType, "throwsType must not null");
            if (throwsType.getKind() == TypeSignature.Kind.Primitive)
                throw new IllegalArgumentException("primitive is not allowed for throws");
            checkBuilding();
            this.throwsTypes.add(throwsType);
            return this;
        }

        public @NotNull MethodSignature build() {
            return buildInternal(null);
        }

        private @NotNull MethodSignature buildInternal(final @Nullable String signature) {
            if (returns == null)
                throw new IllegalStateException("return type is not specified");
            building = false;

            typeParameters.trimToSize();
            valueParameters.trimToSize();
            throwsTypes.trimToSize();

            return new MethodSignature(typeParameters, valueParameters, returns, throwsTypes, signature);
        }

        static {
            InternalAccessorKt.methodSignatureBuilderBuildInternal = Builder::buildInternal;
            InternalAccessorKt.newMethodSignature = MethodSignature::new;
        }
    }
}
