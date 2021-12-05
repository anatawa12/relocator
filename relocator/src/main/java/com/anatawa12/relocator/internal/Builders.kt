package com.anatawa12.relocator.internal

import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.classes.TypePath
import com.anatawa12.relocator.classes.TypeReference
import com.anatawa12.relocator.diagnostic.Location
import com.anatawa12.relocator.reference.ClassReference
import com.anatawa12.relocator.reference.withLocation
import org.objectweb.asm.*
import kotlin.properties.Delegates
import org.objectweb.asm.TypePath as ASMTypePath

internal object Builders {
    class ClassBuilder : ClassVisitor(Opcodes.ASM9) {
        var classFile: ClassFile? = null
        // TODO: module support

        private var version: Int by Delegates.notNull()
        private var access: Int by Delegates.notNull()
        private lateinit var name: String
        private var signature: String? = null
        private var superName: ClassReference? = null
        private lateinit var interfaces: List<ClassReference>
        private var sourceFile: String? = null
        private var sourceDebug: String? = null
        private var outerClass: ClassReference? = null
        private var outerMethod: String? = null
        private var outerMethodDesc: String? = null
        private val visibleAnnotations = mutableListOf<ClassAnnotation>()
        private val invisibleAnnotations = mutableListOf<ClassAnnotation>()
        private val visibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val invisibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val innerClasses = mutableListOf<ClassInnerClass>()
        private var nestHostClass: ClassReference? = null
        private val nestMembers = mutableListOf<ClassReference>()
        private val permittedSubclasses = mutableListOf<ClassReference>()
        private val methods = mutableListOf<ClassMethod>()
        private val fields = mutableListOf<ClassField>()
        private val recordFields = mutableListOf<ClassRecordField>()

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
            this.version = version
            this.access = access
            this.name = name
            this.signature = signature
            this.superName = superName?.let(::ClassReference)
            this.interfaces = interfaces?.map(::ClassReference).orEmpty()
            this.location = Location.Class(name)
        }

        override fun visitSource(source: String?, debug: String?) {
            sourceFile = source
            sourceDebug = debug
        }

        override fun visitModule(name: String?, access: Int, version: String?): ModuleVisitor {
            // TODO module support
            error("visitModule not yet supported")
        }

        override fun visitNestHost(nestHost: String) {
            nestHostClass = ClassReference(nestHost)
        }

        override fun visitOuterClass(owner: String, name: String?, descriptor: String?) {
            outerClass = ClassReference(owner)
            if (name != null && descriptor != null) {
                outerMethod = name
                outerMethodDesc = descriptor
            }
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location, 
                if (visible) visibleAnnotations else invisibleAnnotations)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location, 
            if (visible) visibleTypeAnnotations else invisibleTypeAnnotations)

        override fun visitAttribute(attribute: Attribute) {
            unknownAttributes.add(attribute.type)
        }

        override fun visitNestMember(nestMember: String) {
            nestMembers += ClassReference(nestMember)
        }

        override fun visitPermittedSubclass(permittedSubclass: String) {
            permittedSubclasses += ClassReference(permittedSubclass)
        }

        override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
            innerClasses += ClassInnerClass(access, ClassReference(name), outerName?.let(::ClassReference), innerName)
        }

        override fun visitRecordComponent(
            name: String,
            descriptor: String,
            signature: String?
        ): RecordComponentVisitor = RecordFieldBuilder(location, name, descriptor, signature) { recordFields += it }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor = FieldBuilder(location, access, name, descriptor, signature, value) { fields += it }

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
            methods += it
        }

        override fun visitEnd() {
            classFile = ClassFile(
                version,
                access,
                name,
                signature,
                superName,
                interfaces,
                sourceFile,
                sourceDebug,
                outerClass,
                outerMethod,
                outerMethodDesc,
                visibleAnnotations,
                invisibleAnnotations,
                visibleTypeAnnotations,
                invisibleTypeAnnotations,
                innerClasses,
                nestHostClass,
                nestMembers,
                permittedSubclasses,
                methods,
                fields,
                recordFields,
            ).withUnknownAttrs(unknownAttributes)
        }
    }

    class MethodBuilder(
        clazz: Location.Class?,
        private val access: Int,
        private val name: String,
        private val descriptor: String,
        private val signature: String?,
        private val exceptions: List<ClassReference>,
        private val location: Location.Method? = clazz?.let { Location.Method(clazz.name, name, descriptor) },
        private val insnBuilder: Insns.InsnBuilder = Insns.InsnBuilder(location),
        private val onEnd: (ClassMethod) -> Unit
    ) : MethodVisitor(Opcodes.ASM9, insnBuilder) {

        private val parameters = mutableListOf<ClassParameter>()
        private val visibleAnnotations = mutableListOf<ClassAnnotation>()
        private val invisibleAnnotations = mutableListOf<ClassAnnotation>()
        private val visibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val invisibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private var annotationDefault: AnnotationValue? = null
        private var visibleParameterAnnotations: Array<MutableList<ClassAnnotation>?>
        private var invisibleParameterAnnotations: Array<MutableList<ClassAnnotation>?>
        private val attrNames = mutableListOf<String>()

        init {
            val paramCount = Type.getArgumentTypes(descriptor).size
            visibleParameterAnnotations = arrayOfNulls(paramCount)
            invisibleParameterAnnotations = arrayOfNulls(paramCount)
        }

        override fun visitParameter(name: String, access: Int) {
            parameters.add(ClassParameter(name, access))
        }

        override fun visitAnnotationDefault(): AnnotationVisitor = AnnotationBuilder(location, { _, v -> v }) {
            annotationDefault = it.single()
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location,
                if (visible) visibleAnnotations else invisibleAnnotations)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location,
            if (visible) visibleTypeAnnotations else invisibleTypeAnnotations)

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
            onEnd(ClassMethod(
                access,
                name,
                descriptor,
                signature,
                exceptions,
                parameters,
                visibleAnnotations,
                invisibleAnnotations,
                visibleTypeAnnotations,
                invisibleTypeAnnotations,
                annotationDefault,
                Array(visibleParameterAnnotations.size) { visibleParameterAnnotations[it] },
                Array(invisibleParameterAnnotations.size) { invisibleParameterAnnotations[it] },
                insnBuilder.classCode,
            ).withUnknownAttrs(attrNames))
        }
    }

    class FieldBuilder(
        clazz: Location.Class?,
        private val access: Int,
        private val name: String,
        private val descriptor: String,
        private val signature: String?,
        value: Any?,
        private val onEnd: (ClassField) -> Unit,
    ) : FieldVisitor(Opcodes.ASM9) {
        val location = clazz?.let { Location.Field(clazz.name, name, descriptor) }

        private val value = value?.let { Insns.newConstant(it, location) }

        private val visibleAnnotations = mutableListOf<ClassAnnotation>()
        private val invisibleAnnotations = mutableListOf<ClassAnnotation>()
        private val visibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val invisibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val unknownAttributes = mutableListOf<String>()

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location,
                if (visible) visibleAnnotations else invisibleAnnotations)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location,
            if (visible) visibleTypeAnnotations else invisibleTypeAnnotations)

        override fun visitAttribute(attribute: Attribute) {
            unknownAttributes.add(attribute.type)
        }

        override fun visitEnd() {
            onEnd(ClassField(
                access,
                name,
                descriptor,
                signature,
                value,
                visibleAnnotations,
                invisibleAnnotations,
                visibleTypeAnnotations,
                invisibleTypeAnnotations,
            ).withUnknownAttrs(unknownAttributes))
        }
    }

    class RecordFieldBuilder(
        clazz: Location.Class?,
        private val name: String,
        private val descriptor: String,
        private val signature: String?,
        private val onEnd: (ClassRecordField) -> Unit,
    ) : RecordComponentVisitor(Opcodes.ASM9) {
        val location = clazz?.let { Location.RecordField(clazz.name, name, descriptor) }

        private val visibleAnnotations = mutableListOf<ClassAnnotation>()
        private val invisibleAnnotations = mutableListOf<ClassAnnotation>()
        private val visibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val invisibleTypeAnnotations = mutableListOf<ClassTypeAnnotation>()
        private val unknownAttributes = mutableListOf<String>()

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor =
            AnnotationBuilder.ofAnnotation(descriptor, location,
                if (visible) visibleAnnotations else invisibleAnnotations)

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: ASMTypePath?,
            descriptor: String,
            visible: Boolean
        ): AnnotationVisitor = AnnotationBuilder.ofTypeAnnotation(descriptor, typeRef, typePath, location,
            if (visible) visibleTypeAnnotations else invisibleTypeAnnotations)

        override fun visitAttribute(attribute: Attribute) {
            unknownAttributes.add(attribute.type)
        }

        override fun visitEnd() {
            onEnd(ClassRecordField(
                name,
                descriptor,
                signature,
                visibleAnnotations,
                invisibleAnnotations,
                visibleTypeAnnotations,
                invisibleTypeAnnotations,
            ).withUnknownAttrs(unknownAttributes))
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
            fun ofAnnotation(
                descriptor: String,
                location: Location?,
                onEnd: (ClassAnnotation) -> Unit,
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
            ) = ofAnnotation(descriptor, location) { addTo.add(it) }

            fun ofTypeAnnotation(
                descriptor: String,
                typeRef: Int,
                typePath: ASMTypePath?,
                location: Location?,
                addTo: MutableCollection<in ClassTypeAnnotation>,
            ) = ofAnnotation(descriptor, location) {
                addTo.add(ClassTypeAnnotation(TypeReference(typeRef), typePath?.let(::newTypePath), it))
            }

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
