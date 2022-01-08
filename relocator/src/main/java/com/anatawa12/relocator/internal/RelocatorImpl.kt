package com.anatawa12.relocator.internal

import com.anatawa12.relocator.ReferenceCollector
import com.anatawa12.relocator.ReferencesCollectContext
import com.anatawa12.relocator.Relocator
import com.anatawa12.relocator.classes.*
import com.anatawa12.relocator.diagnostic.*
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_CLASS
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_FIELD
import com.anatawa12.relocator.internal.BasicDiagnostics.UNRESOLVABLE_METHOD
import com.anatawa12.relocator.plugin.*
import com.anatawa12.relocator.reference.*
import com.anatawa12.relocator.reference.withLocation
import com.anatawa12.relocator.reflect.ReflectionMappingContainer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.objectweb.asm.Opcodes.ACC_NATIVE
import org.objectweb.asm.Opcodes.ACC_VARARGS
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import com.anatawa12.relocator.plugin.AnnotationLocation as AnnLoc
import com.anatawa12.relocator.plugin.TypeAnnotationLocation as TAnnLoc

internal class RelocatingEnvironment(val relocator: Relocator) {
    lateinit var refers: ReferencesClassPath
    lateinit var embeds: EmbeddableClassPath
    lateinit var roots: EmbeddableClassPath
    lateinit var classpath: CombinedClassPath
    val diagnosticHandler = InternalDiagnosticHandlerWrapper(relocator.diagnosticHandler, relocator.suppression)
    private val collectors = listOf<ReferenceCollector>(
        DefaultCollector,
    )
    lateinit var classes: MutableList<ClassFile>
    // the queue to remove members requested by ClassRelocator
    val removeQueue = ConcurrentLinkedQueue<() -> Unit>()

    val mapping: RelocationMapping = RelocationMapping(relocator.relocateMapping)
     lateinit var relocators: List<ClassRelocator>

    suspend fun run(): Unit = coroutineScope {
        val timer = Timer(relocator.debugMode)

        val preContext = PreClassRelocatorPluginContextImpl()
        for (plugin in relocator.plugins.values) plugin.preApply(preContext)
        val pluginContext = ClassRelocatorPluginContextImpl()
        for (plugin in relocator.plugins.values) plugin.apply(pluginContext)

        relocators = pluginContext.buildClassRelocators()

        timer.end("loadPlugins")

        refers = ReferencesClassPath(relocator.referPath, relocator.debugMode) {
            computeReferencesForLibrary()
        }
        embeds = EmbeddableClassPath(relocator.embedPath, relocator.debugMode)
        roots = EmbeddableClassPath(relocator.rootPath, relocator.debugMode)

        timer.end("construct")
        listOf(
            launch { refers.init() },
            launch { embeds.init() },
            launch { roots.init() },
        ).forEach { it.join() }

        checkNoErrors()
        timer.end("init")

        classpath = CombinedClassPath(listOf(roots, embeds, refers))
        val computeReferenceEnv = ComputeReferenceEnvironment(
            relocator.keepRuntimeInvisibleAnnotation,
            relocator.reflectionMap,
            classpath,
            diagnosticHandler,
        )

        // first step: computeReferences
        (embeds.classes + roots.classes).map {
            launch { it.computeReferences(computeReferenceEnv) }
        }.forEach { it.join() }

        checkNoErrors()
        timer.end("computeReferences")

        // second step: collect references
        // collect all references for methods/classes.
        collectReferences()
        timer.end("collectReferences")

        checkNoErrors()

        // third step: relocation

        listUpClasses()
        timer.end("listUpClasses")

        relocateClasses()
        runRemoveQueue()
        timer.end("relocateClasses")

        // forth step: make a jar.
        // make a jar with relocation

    }

    private fun checkNoErrors() {
        if (diagnosticHandler.errorCount != 0)
            throw ErrorFoundException()
    }

    private suspend fun collectReferences() = TaskQueue {
        val context = ReferencesCollectContextImpl(
            roots,
            classpath,
            this,
            diagnosticHandler,
        )
        for (collector in collectors) {
            start { collector.apply { context.collect() } }
        }
    }

    private suspend fun listUpClasses() = TaskQueue {
        classes = (embeds.classes + roots.classes).filter { it.included }.toMutableList()
        for (classFile in classes) {
            start {
                classFile.fields.removeIf { !it.included }
                classFile.methods.removeIf { !it.included }
            }
        }
    }

    private suspend fun relocateClasses() = TaskQueue {
        for (classFile in classes) {
            start {
                runRelocator(classes, classFile, ClassRelocator::relocate)
                classFile.fields.forEach { relocateField(classFile.fields, it) }
                classFile.methods.forEach { relocateMethod(classFile.methods, it) }
                classFile.recordFields.forEach { relocateRecordField(classFile.recordFields, it) }
                relocateAnnotations(classFile.visibleAnnotations, true, AnnLoc.Class(classFile),
                    ClassRelocator::relocate)
                relocateAnnotations(classFile.invisibleAnnotations, false, AnnLoc.Class(classFile),
                    ClassRelocator::relocate)
                relocateAnnotations(classFile.visibleTypeAnnotations, true, TAnnLoc.Class(classFile),
                    ClassRelocator::relocate)
                relocateAnnotations(classFile.invisibleTypeAnnotations, false, TAnnLoc.Class(classFile),
                    ClassRelocator::relocate)
            }
        }
    }

    private fun TaskQueue.relocateField(list: MutableCollection<ClassField>, field: ClassField) = start {
        runRelocator(list, field, ClassRelocator::relocate)
        relocateAnnotations(field.visibleAnnotations, true, AnnLoc.Field(field), ClassRelocator::relocate)
        relocateAnnotations(field.invisibleAnnotations, false, AnnLoc.Field(field), ClassRelocator::relocate)
        relocateAnnotations(field.visibleTypeAnnotations, true, TAnnLoc.Field(field), ClassRelocator::relocate)
        relocateAnnotations(field.invisibleTypeAnnotations, false, TAnnLoc.Field(field), ClassRelocator::relocate)
    }

    private fun TaskQueue.relocateMethod(list: MutableCollection<ClassMethod>, method: ClassMethod) = start {
        runRelocator(list, method, ClassRelocator::relocate)
        relocateAnnotations(method.visibleAnnotations, true, AnnLoc.Method(method), ClassRelocator::relocate)
        relocateAnnotations(method.invisibleAnnotations, false, AnnLoc.Method(method), ClassRelocator::relocate)
        relocateAnnotations(method.visibleTypeAnnotations, true, TAnnLoc.Method(method), ClassRelocator::relocate)
        relocateAnnotations(method.invisibleTypeAnnotations, false, TAnnLoc.Method(method), ClassRelocator::relocate)
        for ((i, visibleParameterAnnotationList) in method.visibleParameterAnnotations.withIndex())
            visibleParameterAnnotationList
                ?.let { relocateAnnotations(it, true, AnnLoc.Parameter(method, i), ClassRelocator::relocate) }
        for ((i, invisibleParameterAnnotationList) in method.invisibleParameterAnnotations.withIndex())
            invisibleParameterAnnotationList
                ?.let { relocateAnnotations(it, false, AnnLoc.Parameter(method, i), ClassRelocator::relocate) }
        method.classCode?.let { relocateClassCode(it) }
    }

    private fun TaskQueue.relocateClassCode(code: ClassCode) {
        for (insn in code.instructions) {
            relocateAnnotations(insn.visibleAnnotations, false, TAnnLoc.Insn(insn, code), ClassRelocator::relocate)
            relocateAnnotations(insn.invisibleAnnotations, true, TAnnLoc.Insn(insn, code), ClassRelocator::relocate)
        }
        for (tryCatchBlock in code.tryCatchBlocks) {
            relocateAnnotations(tryCatchBlock.visibleAnnotations, false,
                TAnnLoc.TryCatchBlock(tryCatchBlock, code), ClassRelocator::relocate)
            relocateAnnotations(tryCatchBlock.invisibleAnnotations, true,
                TAnnLoc.TryCatchBlock(tryCatchBlock, code), ClassRelocator::relocate)
        }
        relocateAnnotations(code.visibleLocalVariableAnnotations, false, code, ClassRelocator::relocate)
        relocateAnnotations(code.invisibleLocalVariableAnnotations, true, code, ClassRelocator::relocate)
    }

    private fun TaskQueue.relocateRecordField(
        list: MutableCollection<ClassRecordField>,
        field: ClassRecordField,
    ) = start {
        runRelocator(list, field, ClassRelocator::relocate)
        relocateAnnotations(field.invisibleAnnotations, false, AnnLoc.RecordField(field), ClassRelocator::relocate)
        relocateAnnotations(field.visibleAnnotations, true, AnnLoc.RecordField(field), ClassRelocator::relocate)
        relocateAnnotations(field.invisibleTypeAnnotations, false, TAnnLoc.RecordField(field), ClassRelocator::relocate)
        relocateAnnotations(field.visibleTypeAnnotations, true, TAnnLoc.RecordField(field), ClassRelocator::relocate)
    }

    private inline fun <A, L> TaskQueue.relocateAnnotations(
        annotations: MutableList<A>,
        visible: Boolean,
        location: L,
        crossinline relocate: ClassRelocator.(A, Boolean, L) -> RelocateResult
    ) {
        for (annotation in annotations) start {
            for (relocator in relocators) {
                when (relocator.relocate(annotation, visible, location)) {
                    RelocateResult.Continue -> continue
                    RelocateResult.Finish -> return@start
                    RelocateResult.Remove -> {
                        removeQueue.add { annotations.remove(annotation) }
                        return@start
                    }
                }
            }
        }
    }

    private inline fun <T> runRelocator(
        list: MutableCollection<T>,
        value: T,
        crossinline relocate: ClassRelocator.(T) -> RelocateResult,
    ) {
        for (relocator in relocators) {
            when (relocator.relocate(value)) {
                RelocateResult.Continue -> continue
                RelocateResult.Finish -> return
                RelocateResult.Remove -> {
                    removeQueue.add { list.remove(value) }
                    return
                }
            }
        }
        return
    }

    private fun runRemoveQueue() {
        removeQueue.forEach(Function0<Unit>::invoke)
    }

    private inner class PreClassRelocatorPluginContextImpl : PreClassRelocatorPluginContext {
        override val reflectionMap: ReflectionMappingContainer get() = relocator.reflectionMap
        override val suppression: SuppressionContainer get() = relocator.suppression
        override fun getPlugin(name: String): ClassRelocatorPlugin? = relocator.plugins[name]
    }

    private inner class ClassRelocatorPluginContextImpl : ClassRelocatorPluginContext {
        private val preFiltering = mutableListOf<ClassRelocator>(
        )
        private val languageProcessing = mutableListOf<ClassRelocator>(
            SimpleClassRelocator(mapping)
        )
        private val finalizing = mutableListOf<ClassRelocator>(
            StringClassRelocator(mapping)
        )

        override val relocationMapping: RelocationMapping get() = mapping
        override val diagnosticHandler: DiagnosticHandler get() = this@RelocatingEnvironment.diagnosticHandler

        override fun addClassRelocator(step: ClassRelocatorStep, relocator: ClassRelocator) {
            when (step) {
                ClassRelocatorStep.PreFiltering -> preFiltering.add(relocator)
                ClassRelocatorStep.LanguageProcessing -> languageProcessing.add(relocator)
                ClassRelocatorStep.Finalizing -> finalizing.add(relocator)
            }
        }

        override fun getPlugin(name: String): ClassRelocatorPlugin? = relocator.plugins[name]

        fun buildClassRelocators(): List<ClassRelocator> = preFiltering + languageProcessing + finalizing
    }
}

private class ReferencesCollectContextImpl(
    override val roots: EmbeddableClassPath,
    override val classpath: CombinedClassPath,
    private val queue: TaskQueue,
    private val addDiagnostic: DiagnosticHandler,
) : ReferencesCollectContext() {
    private val references = Collections.newSetFromMap<Reference>(ConcurrentHashMap())

    override fun runChildThread(run: ReferencesCollector) {
        queue.start { run.run { run() } }
    }

    override fun collectReferencesOf(reference: Reference, location: Location?) {
        if (!references.add(reference)) return
        if (reference.location == null)
            reference.withLocation(location ?: Location.None)
        queue.start {
            when (reference) {
                is ClassReference -> {
                    val rootClass = when {
                        reference.isArray() -> reference.arrayComponentType.tryAsClassReference() ?: return@start
                        else -> reference
                    }
                    collectReferencesOf(classpath.findClass(rootClass)
                        ?: return@start addDiagnostic(UNRESOLVABLE_CLASS(rootClass.name,
                            rootClass.location ?: Location.None)))
                }
                is FieldReference -> {
                    val field = classpath.findField(reference)
                        ?: return@start addDiagnostic(UNRESOLVABLE_FIELD(reference.owner.name,
                            reference.name, reference.descriptor.descriptor, reference.location ?: Location.None))
                    collectReferencesOf(field)
                }
                is PartialFieldReference -> {
                    val fields = classpath.findFields(reference)
                    if (fields.isEmpty())
                        return@start addDiagnostic(UNRESOLVABLE_FIELD(reference.owner.name,
                            reference.name, null, reference.location ?: Location.None))
                    fields.forEach { start { collectReferencesOf(it) } }
                }
                is RecordFieldReference -> {
                    val recordField = classpath.findRecordField(reference)
                        ?: return@start addDiagnostic(UNRESOLVABLE_FIELD(reference.owner.name,
                            reference.name, reference.descriptor.descriptor, reference.location ?: Location.None))
                    collectReferencesOf(recordField)
                }
                is MethodReference -> {
                    if (reference.owner.isArray() && isArrayMethod(reference))
                        return@start
                    if (isSignaturePolymorphicMethod(reference))
                        return@start
                    collectReferencesOf(classpath.findMethod(reference)
                        ?: return@start addDiagnostic(UNRESOLVABLE_METHOD(reference.owner.name,
                            reference.name, reference.descriptor.descriptor, reference.location ?: Location.None)))
                }
                is PartialMethodReference -> {
                    if (reference.owner.isArray() && isArrayMethod(reference))
                        return@start

                    val methods = classpath.findMethods(reference)
                    if (methods.isEmpty())
                        return@start addDiagnostic(UNRESOLVABLE_METHOD(reference.owner.name,
                            reference.name, reference.descriptor.descriptor, reference.location ?: Location.None))
                    methods.forEach { start { collectReferencesOf(it) } }
                }
                is TypelessMethodReference -> {
                    if (reference.owner.isArray() && isArrayMethod(reference))
                        return@start

                    val methods = classpath.findMethods(reference)
                    if (methods.isEmpty())
                        return@start addDiagnostic(UNRESOLVABLE_METHOD(reference.owner.name,
                            reference.name, null, reference.location ?: Location.None))
                    methods.forEach { start { collectReferencesOf(it) } }
                }
            }
        }
    }

    /**
     * Returns true if the reference is targeting to [Signature Polymorphic Methods].
     * 
     * [Signature Polymorphic Methods]: (https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-2.html#jvms-2.9.3)
     */
    private suspend fun isSignaturePolymorphicMethod(reference: MethodReference): Boolean {
        if (reference.owner.name == "java/lang/invoke/MethodHandle"
            || reference.owner.name == "java/lang/invoke/VarHandle") {
            val methodHandle = classpath.findClass(reference.owner.name) ?: return false
            val method = methodHandle.findMethod(reference.name,
                MethodDescriptor("([L${"java/lang/Object"};)L${"java/lang/Object"};")) ?: return false
            return method.access.hasFlag(ACC_VARARGS or ACC_NATIVE)
        }
        return false
    }

    private suspend fun isArrayMethod(reference: MethodReference): Boolean {
        val objectClass = classpath.findClass("java/lang/Object") ?: return false
        return objectClass.findMethod(reference) != null
    }

    private suspend fun isArrayMethod(reference: PartialMethodReference): Boolean {
        val objectClass = classpath.findClass("java/lang/Object") ?: return false
        return objectClass.findMethods(reference).isNotEmpty()
    }

    private suspend fun isArrayMethod(reference: TypelessMethodReference): Boolean {
        val objectClass = classpath.findClass("java/lang/Object") ?: return false
        return objectClass.findMethods(reference).isNotEmpty()
    }

    private fun collectReferencesOf(classFile: ClassFile) {
        classFile.included = true
        collectReferencesOf(classFile.allReferences, Location.Class(classFile.name))
    }

    private fun collectReferencesOf(field: ClassField) {
        field.included = true
        collectReferencesOf(field.allReferences, Location.Field(field))
    }

    private fun collectReferencesOf(method: ClassMethod) {
        method.included = true
        collectReferencesOf(method.allReferences, Location.Method(method))
    }

    private fun collectReferencesOf(record: ClassRecordField) {
        record.included = true
        collectReferencesOf(record.allReferences, Location.RecordField(record))
    }
}

private object DefaultCollector : ReferenceCollector {
    override fun ReferencesCollectContext.collect() {
        for (classFile in roots.classes) {
            collectReferencesOf(ClassReference(classFile.name))
            for (method in classFile.methods)
                collectReferencesOf(MethodReference(method))
            for (field in classFile.fields)
                collectReferencesOf(FieldReference(field))
        }
    }
}
