package com.anatawa12.relocator.classes;

import com.anatawa12.relocator.internal.DescriptorSignatures;
import com.anatawa12.relocator.internal.InternalAccessorKt;
import com.anatawa12.relocator.internal.TypeKind;
import kotlin.annotations.jvm.ReadOnly;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@SuppressWarnings("StaticInitializerReferencesSubClass")
public abstract class TypeSignature {
    @NotNull
    abstract String getSignature();

    private final int dimensions;

    private TypeSignature(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return Objects.equals(getSignature(), ((TypeSignature) o).getSignature());
    }

    @Override
    public final int hashCode() {
        return getSignature().hashCode();
    }

    @Override
    public String toString() {
        return getSignature();
    }

    public abstract @NotNull Kind getKind();

    public static final @NotNull TypeSignature VOID = new Primitive("V");
    public static final @NotNull TypeSignature BYTE = new Primitive("B");
    public static final @NotNull TypeSignature CHAR = new Primitive("C");
    public static final @NotNull TypeSignature DOUBLE = new Primitive("D");
    public static final @NotNull TypeSignature FLOAT = new Primitive("F");
    public static final @NotNull TypeSignature INT = new Primitive("I");
    public static final @NotNull TypeSignature LONG = new Primitive("J");
    public static final @NotNull TypeSignature SHORT = new Primitive("S");
    public static final @NotNull TypeSignature BOOLEAN = new Primitive("Z");

    static {
        InternalAccessorKt.newSimpleTypeSignature = Simple::new;
        InternalAccessorKt.classBuilderBuildInternal = ClassBuilder::buildInternal;
    }

    public static @NotNull TypeSignature parse(@NotNull String signature) {
        Objects.requireNonNull(signature, "signature must not null");
        return DescriptorSignatures.parseTypeSignature(signature, TypeKind.Voidable);
    }

    public static @NotNull TypeSignature argumentOf(@NotNull String name) {
        Objects.requireNonNull(name, "name must not null");
        DescriptorSignatures.parseSimpleName(name, "type argument name");
        return new Simple('T' + name + ';', 0);
    }

    public static @NotNull TypeSignature classOf(@NotNull String internalName) {
        Objects.requireNonNull(internalName, "internalName must not null");
        DescriptorSignatures.parseClassInternalName(internalName);
        return new ForClass(singletonList(new ForClass.Element(internalName, emptyList())), null, 0);
    }

    public final int getArrayDimensions() {
        return dimensions;
    }

    public @NotNull String getRootClassName() {
        throw new IllegalStateException("this is not signature of class");
    }

    public int getInnerClassCount() {
        throw new IllegalStateException("this is not signature of class");
    }

    /**
     * @param index The index of inner class. This will start with 1.
     * @return The inner name of the inner class.
     */
    public @NotNull String getInnerClassName(int index) {
        throw new IllegalStateException("this is not signature of class");
    }

    /**
     * @param index The index of class. 0 means root class and 1 or grater means inner class.
     * @return the list of TypeArgument. This list can't be modified.
     */
    @ReadOnly
    public @NotNull List<? extends @NotNull TypeArgument> getTypeArguments(int index) {
        throw new IllegalStateException("this is not signature of class");
    }

    public abstract @NotNull TypeSignature array(int dimension);

    public static final class ClassBuilder {
        public ClassBuilder(final @NotNull String internalName) {
            Objects.requireNonNull(internalName, "internalName must not null");
            DescriptorSignatures.parseClassInternalName(internalName);
            check(elements.isEmpty() && name == null, "base class name has been specified");
            name = internalName;
        }

        private final @NotNull ArrayList<ForClass.@NotNull Element> elements = new ArrayList<>();
        private @Nullable String name = null;
        private @Nullable ArrayList<@NotNull TypeArgument> args = null;
        private volatile boolean building = true;

        private void checkBuilding() {
            if (!building) throw new IllegalStateException("this builder has been built");
        }

        @org.jetbrains.annotations.Contract("false, _ -> fail")
        private static void check(final boolean check, final @NotNull String error) {
            if (!check)
                throw new IllegalStateException(error);
        }

        private void finishClass() {
            checkBuilding();
            check(name != null, "base class name is not specified yet");
            final @NotNull String name = this.name;
            final @Nullable ArrayList<@NotNull TypeArgument> args = this.args;
            if (args == null) {
                elements.add(new ForClass.Element(name, CollectionsKt.emptyList()));
            } else {
                args.trimToSize();
                elements.add(new ForClass.Element(name, Collections.unmodifiableList(args)));
                this.args = null;
            }
        }

        public @NotNull ClassBuilder innerClassName(final @NotNull String innerName) {
            Objects.requireNonNull(innerName, "innerName must not null");
            DescriptorSignatures.parseSimpleName(innerName, "inner class name");
            finishClass();
            this.name = innerName;
            return this;
        }

        private @NotNull ClassBuilder addTypeArg(final @NotNull TypeArgument arg) {
            checkBuilding();
            check(name != null, "the class accepts type argument is not specified yet");
            @Nullable ArrayList<@NotNull TypeArgument> args = this.args;
            if (args == null) args = this.args = new ArrayList<>();
            args.add(arg);
            return this;
        }

        public @NotNull ClassBuilder addWildcard() {
            return addTypeArg(TypeArgument.STAR);
        }

        public @NotNull ClassBuilder addTypeArgument(@NotNull TypeSignature type) {
            Objects.requireNonNull(type, "type must not null");
            return addTypeArg(TypeArgument.of(type, TypeVariant.Invariant));
        }

        public @NotNull ClassBuilder addTypeArgument(@NotNull TypeSignature type, @NotNull TypeVariant variant) {
            Objects.requireNonNull(type, "type must not null");
            Objects.requireNonNull(variant, "variant must not null");
            return addTypeArg(TypeArgument.of(type, variant));
        }

        public @NotNull TypeSignature build() {
            return buildInternal(null, 0);
        }

        public @NotNull TypeSignature build(int dimensions) {
            return buildInternal(null, dimensions);
        }

        private @NotNull TypeSignature buildInternal(String signature, int dimensions) {
            finishClass();
            building = false;
            assert !elements.isEmpty();
            elements.trimToSize();
            return new ForClass(elements, signature, dimensions);
        }
    }

    public enum Kind {
        Array,
        Primitive,
        TypeArgument,
        Class,
    }

    private static final class Primitive extends TypeSignature {
        private final @NotNull String signature;

        private Primitive(final @NotNull String signature) {
            super(0);
            this.signature = signature;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        @NotNull String getSignature() {
            return signature;
        }

        @Override
        public @NotNull Kind getKind() {
            return Kind.Primitive;
        }

        @Override
        public @NotNull TypeSignature array(int dimension) {
            if (dimension == 0) return this;
            StringBuilder builder = new StringBuilder(dimension + 1);
            for (int i = 0; i < dimension; i++) builder.append('[');
            builder.append(signature);
            return new Simple(builder.toString(), dimension);
        }
    }

    private static final class Simple extends TypeSignature {
        private final @NotNull String signature;

        private Simple(final @NotNull String signature, int dimensions) {
            super(dimensions);
            this.signature = signature;
        }

        @Override
        @NotNull String getSignature() {
            return signature;
        }

        @Override
        public @NotNull Kind getKind() {
            return signature.charAt(0) == '[' ? Kind.Array : Kind.TypeArgument;
        }

        @Override
        public @NotNull TypeSignature array(int dimension) {
            if (dimension == 0) return this;
            StringBuilder builder = new StringBuilder(dimension + signature.length());
            for (int i = 0; i < dimension; i++) builder.append('[');
            builder.append(signature);
            return new Simple(builder.toString(), getArrayDimensions() + dimension);
        }
    }

    private static final class ForClass extends TypeSignature {
        private final @NotNull List<@NotNull Element> elements;

        private ForClass(final @NotNull List<@NotNull Element> elements, final @Nullable String signature, int dimensions) {
            super(dimensions);
            this.elements = elements;
            this.signature = signature;
        }

        private static void appendArgs(final @NotNull StringBuilder builder, final @NotNull List<@NotNull TypeArgument> args) {
            Iterator<TypeArgument> iter = args.iterator();
            if (!iter.hasNext()) return;
            builder.append('<');
            do {
                TypeArgument arg = iter.next();
                if (arg.getType() == null) {
                    builder.append('*');
                } else {
                    switch (arg.getVariant()) {
                        case Covariant:
                            builder.append('+');
                            break;
                        case Contravariant:
                            builder.append('-');
                            break;
                        case Invariant:
                            break;
                    }
                    builder.append(arg.getType());
                }
            } while (iter.hasNext());
            builder.append('>');
        }

        private @Nullable String signature;

        @Override
        @NotNull String getSignature() {
            if (signature != null) return signature;
            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < getArrayDimensions(); i++)
                builder.append('[');
            builder.append('L');
            Iterator<Element> iter = elements.iterator();
            Element first = iter.next();
            builder.append(first.name);
            appendArgs(builder, first.args);
            while (iter.hasNext()) {
                Element elem = iter.next();
                builder.append('.');
                builder.append(elem.name);
                appendArgs(builder, elem.args);
            }
            builder.append(';');
            return signature = builder.toString();
        }

        @Override
        public @NotNull String getRootClassName() {
            return elements.get(0).name;
        }

        @Override
        public int getInnerClassCount() {
            return elements.size() - 1;
        }

        @Override
        public @NotNull String getInnerClassName(int index) {
            if (index == 0) throw new IndexOutOfBoundsException("Index 0 is not valid for inner class index.");
            return elements.get(index).name;
        }

        @Override
        @ReadOnly
        public @NotNull List<@NotNull TypeArgument> getTypeArguments(int index) {
            return elements.get(index).args;
        }

        @Override
        public @NotNull TypeSignature array(int dimension) {
            return new ForClass(elements, null, dimension);
        }

        @Override
        public @NotNull Kind getKind() {
            return getArrayDimensions() != 0 ? Kind.Array : Kind.Class;
        }

        final static class Element {
            final @NotNull String name;
            final @NotNull List<@NotNull TypeArgument> args;

            public Element(final @NotNull String name, final @NotNull List<@NotNull TypeArgument> args) {
                this.name = name;
                this.args = args;
            }
        }
    }
}
