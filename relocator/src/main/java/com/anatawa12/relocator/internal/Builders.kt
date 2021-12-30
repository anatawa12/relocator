package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.classes.TypePath
import com.anatawa12.relocator.classes.TypeReference
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.withLocation
import org.objectweb.asm.*
import org.objectweb.asm.TypePath as ASMTypePath

internal object Builders {
    class ClassBuilder : ClassVisitor(Opcodes.ASM9) {
        var classFile: ClassFile? = null
        // TODO: module support
        private lateinit var builder: ClassFileBuilder

        private val unknownAttributes = mutableListOf<String>()

        private lateinit var location: Location.Class

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            this.location = Location.Class(name)
            builder = ClassFile.Builder(version, access, name)
            builder.signature(signature?.let(ClassSignature::parse))
            builder.superName(superName?.let(::ClassReference)?.withLocation(location))
            interfaces?.forEach { builder.addInterface(ClassReference(it).withLocation(location)) }
        }

        override fun visitSource(source: String?, debug: String?) {
            builder.sourceFile(source)
            builder.sourceDebug(debug)
        }

        override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor {
            // TODO module support
            error("visitModule not yet supported")
        }

        override fun visitNestHost(nestHost: String) {
            builder.nestHostClass(ClassReference(nestHost).withLocation(location))
        }

        override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
            builder.outerClass(ClassReference(owner).withLocation(location))
            if (name != null && descriptor != null) {
                builder.outerMethod(name)
                builder.outerMethodDesc(MethodDescriptor(descriptor))
            }
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location, 
                if (visible) builder::addVisibleAnnotation else builder::addInvisibleAnnotation)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location, 
            if (visible) builder::addVisibleTypeAnnotation else builder::addInvisibleTypeAnnotation)

        override fun visitAttribute(attribute: Attribute) {
            unknownAttributes.add(attribute.type)
        }

        override fun visitNestMember(nestMember: String) {
            builder.addNestMember(ClassReference(nestMember).withLocation(location))
        }

        override fun visitPermittedSubclass(permittedSubclass: String) {
            builder.addPermittedSubclasse(ClassReference(permittedSubclass).withLocation(location))
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            builder.addInnerClasse(ClassInnerClass(
                access, 
                ClassReference(name).withLocation(location), 
                outerName?.let(::ClassReference)?.withLocation(location), 
                innerName))
        }

        override fun visitRecordComponent(
            name: String,
            descriptor: String,
            signature: String?
        ): RecordComponentVisitor = RecordFieldBuilder(location, name, descriptor, signature) {
            builder.addRecordField(it)
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor = FieldBuilder(location, access, name, descriptor, signature, value) {
            builder.addField(it)
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor = MethodBuilder(
            location,
            access,
            name,
            descriptor,
            signature,
            exceptions?.map(::ClassReference).orEmpty(),
        ) {
            builder.addMethod(it)
        }

        override fun visitEnd() {
            classFile = builder.build().withUnknownAttrs(unknownAttributes)
        }
    }

    class MethodBuilder(
        clazz: Location.Class?,
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: List<ClassReference>,
        private val location: Location.Method? = clazz?.let { Location.Method(clazz.name, name, MethodDescriptor(descriptor)) },
        private val insnBuilder: Insns.InsnBuilder = Insns.InsnBuilder(location),
        private val onEnd: (ClassMethod) -> Unit
    ) : MethodVisitor(Opcodes.ASM9, insnBuilder) {
        private val builder = ClassMethod.Builder(access, name, MethodDescriptor(descriptor))
            .signature(signature?.let(MethodSignature::parse))
            .addExceptions(exceptions)

        private var visibleParameterAnnotations: Array<MutableList<ClassAnnotation>?>
        private var invisibleParameterAnnotations: Array<MutableList<ClassAnnotation>?>
        private val attrNames = mutableListOf<String>()

        init {
            val paramCount = Type.getArgumentTypes(descriptor).size
            visibleParameterAnnotations = arrayOfNulls(paramCount)
            invisibleParameterAnnotations = arrayOfNulls(paramCount)
        }

        override fun visitParameter(name: String, access: Int) {
            builder.addParameter(ClassParameter(name, access))
        }

        override fun visitAnnotationDefault(): AnnotationVisitor = AnnotationBuilder(location, { _, v -> v }) {
            builder.annotationDefault(it.single())
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location,
                if (visible) builder::addVisibleAnnotation else builder::addInvisibleAnnotation)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location,
            if (visible) builder::addVisibleTypeAnnotation else builder::addInvisibleTypeAnnotation)

        override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
            if (visible)
                visibleParameterAnnotations = visibleParameterAnnotations.copyOf(parameterCount)
            else
                invisibleParameterAnnotations = invisibleParameterAnnotations.copyOf(parameterCount)
        }

        override fun visitParameterAnnotation(
            parameter: Int,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor {
            val parameters = (if (visible) visibleParameterAnnotations else invisibleParameterAnnotations)
            var annotations = parameters[parameter]
            if (annotations == null) {
                annotations = mutableListOf()
                parameters[parameter] = annotations
            }
            return AnnotationBuilder.ofAnnotation(descriptor, location, annotations)
        }

        override fun visitAttribute(attribute: Attribute) {
            attrNames.add(attribute.type)
        }

        // code will be proceeded by InsnBuilder

        override fun visitEnd() {
            onEnd(builder
                .classCode(insnBuilder.classCode)
                .visibleParameterAnnotations(visibleParameterAnnotations.copy())
                .invisibleParameterAnnotations(invisibleParameterAnnotations.copy())
                .build()
                .withUnknownAttrs(attrNames))
        }
    }

    class FieldBuilder(
        clazz: Location.Class?,
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?,
        private val onEnd: (ClassField) -> Unit,
    ) : FieldVisitor(Opcodes.ASM9) {
        val location = clazz?.let { Location.Field(clazz.name, name, TypeDescriptor(descriptor)) }

        private val builder = ClassField.Builder(access, name, TypeDescriptor(descriptor))
            .signature(signature?.let(TypeSignature::parse))
            .value(value?.let { Insns.newConstant(it, location) })

        private val unknownAttributes = mutableListOf<String>()

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location,
                if (visible) builder::addVisibleAnnotation else builder::addInvisibleAnnotation)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location,
            if (visible) builder::addVisibleTypeAnnotation else builder::addInvisibleTypeAnnotation)

        override fun visitAttribute(attribute: Attribute) {
            unknownAttributes.add(attribute.type)
        }

        override fun visitEnd() {
            onEnd(builder.build().withUnknownAttrs(unknownAttributes))
        }
    }

    class RecordFieldBuilder(
        clazz: Location.Class?,
        name: String,
        descriptor: String,
        signature: String?,
        private val onEnd: (ClassRecordField) -> Unit,
    ) : RecordComponentVisitor(Opcodes.ASM9) {
        val location = clazz?.let { Location.RecordField(clazz.name, name, TypeDescriptor(descriptor)) }

        private val builder = ClassRecordField.Builder(name, TypeDescriptor(descriptor))
            .signature(signature?.let(TypeSignature::parse))

        private val unknownAttributes = mutableListOf<String>()

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location,
                if (visible) builder::addVisibleAnnotation else builder::addInvisibleAnnotation)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location,
            if (visible) builder::addVisibleTypeAnnotation else builder::addInvisibleTypeAnnotation)

        override fun visitAttribute(attribute: Attribute) {
            unknownAttributes.add(attribute.type)
        }

        override fun visitEnd() {
            onEnd(builder.build().withUnknownAttrs(unknownAttributes))
        }
    }

    @Suppress("DEPRECATION")
    class AnnotationBuilder<T>(
        private val location: Location?,
        private val newValue: (String?, AnnotationValue) -> T,
        private val onEnd: (List<T>) -> Unit,
    ) : AnnotationVisitor(Opcodes.ASM9) {
        val values = mutableListOf<T>()

        override fun visit(name: String?, value: Any) {
            values.add(newValue(name, newAnnotationValue(value)))
        }

        override fun visitEnum(name: String?, descriptor: String, value: String) {
            values.add(newValue(name, AnnotationEnum(
                ClassReference(Type.getType(descriptor).internalName).withLocation(location), value)))
        }

        override fun visitAnnotation(name: String?, descriptor: String): AnnotationVisitor {
            return ofAnnotation(descriptor, location) { values.add(newValue(name, it)) }
        }

        override fun visitArray(name: String?): AnnotationVisitor {
            return ofArray(location) { values.add(newValue(name, it)) }
        }

        override fun visitEnd() {
            onEnd(values)
        }

        companion object {
            fun <T> ofAnnotation(
                descriptor: String,
                location: Location?,
                onEnd: (ClassAnnotation) -> T,
            ): AnnotationBuilder<KeyValuePair> {
                check(descriptor[0] == 'L') { "the type of annotation must be a class" }
                val annotationClass = ClassReference(descriptor.substring(1, descriptor.length - 1))
                    .withLocation(location)
                return AnnotationBuilder(location, { k, v -> KeyValuePair(k!!, v) }) {
                    onEnd(ClassAnnotation(annotationClass, it))
                }
            }

            fun ofAnnotation(
                descriptor: String,
                location: Location?,
                addTo: MutableCollection<in ClassAnnotation>,
            ) = ofAnnotation(descriptor, location) { addTo.add(it); }

            fun <T> ofTypeAnnotation(
                descriptor: String,
                typeRef: Int,
                typePath: ASMTypePath?,
                location: Location?,
                onEnd: (ClassTypeAnnotation) -> T,
            ) = ofAnnotation(descriptor, location) {
                onEnd(ClassTypeAnnotation(TypeReference(typeRef), typePath?.let(::newTypePath), it))
            }

            fun ofTypeAnnotation(
                descriptor: String,
                typeRef: Int,
                typePath: ASMTypePath?,
                location: Location?,
                addTo: MutableCollection<in ClassTypeAnnotation>,
            ) = ofTypeAnnotation(descriptor, typeRef, typePath, location, addTo::add)

            fun ofArray(
                location: Location?,
                onEnd: (AnnotationArray) -> Unit,
            ): AnnotationBuilder<AnnotationValue> {
                return AnnotationBuilder(location, { _, v -> v }) {
                    onEnd(AnnotationArray(it))
                }
            }
        }
    }

    fun newAnnotationValue(main: Any): AnnotationValue = when (main::class) {
        Byte::class -> AnnotationByte(main as Byte)
        Boolean::class -> AnnotationBoolean(main as Boolean)
        Char::class -> AnnotationChar(main as Char)
        Short::class -> AnnotationShort(main as Short)
        Int::class -> AnnotationInt(main as Int)
        Long::class -> AnnotationLong(main as Long)
        Float::class -> AnnotationFloat(main as Float)
        Double::class -> AnnotationDouble(main as Double)
        String::class -> AnnotationString(main as String)
        Type::class -> AnnotationClass((main as Type).descriptor)

        ByteArray::class -> AnnotationArray(main as ByteArray)
        BooleanArray::class -> AnnotationArray(main as BooleanArray)
        CharArray::class -> AnnotationArray(main as CharArray)
        ShortArray::class -> AnnotationArray(main as ShortArray)
        IntArray::class -> AnnotationArray(main as IntArray)
        LongArray::class -> AnnotationArray(main as LongArray)
        FloatArray::class -> AnnotationArray(main as FloatArray)
        DoubleArray::class -> AnnotationArray(main as DoubleArray)

        else -> error("unsupported annotation value: $main(typed ${main::class})")
    }

    fun newTypePath(path: ASMTypePath): TypePath = TypePath.Builder(path.length).apply {
        for (it in 0 until path.length) {
            when (path.getStep(it)) {
                ASMTypePath.ARRAY_ELEMENT -> array()
                ASMTypePath.INNER_TYPE -> inner()
                ASMTypePath.WILDCARD_BOUND -> wildcard()
                ASMTypePath.TYPE_ARGUMENT -> argument(path.getStepArgument(it))
            }
        }
    }.build()
}
