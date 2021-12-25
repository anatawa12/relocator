package com.anatawa12.relocator.builder

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.contracts.contract

@Suppress("unused")
@AutoService(SymbolProcessorProvider::class)
class SymbolProcessorProviderImpl : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return SymbolProcessorImpl(environment)
    }
}

class SymbolProcessorImpl(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    val logger = environment.logger
    val generator = environment.codeGenerator

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val classes = resolver.getSymbolsWithAnnotation(BuildBuilder)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.containingFile != null }
        for (classDecl in classes) processClass(classDecl)
        return emptyList()
    }

    private var errors = false
    private fun addError(message: String, symbol: KSNode? = null) {
        logger.error(message, symbol)
        errors = true
    }

    fun checkErrors(): Boolean {
        if (errors) {
            errors = false
            return true
        }
        return false
    }

    private fun processClass(classDecl: KSClassDeclaration) {
        if (classDecl.typeParameters.isNotEmpty())
            addError("the class has type parameters", classDecl)
        if (classDecl.parentDeclaration != null)
            addError("the class is inner class", classDecl)

        val ctor = classDecl.primaryConstructor
        if (ctor == null) {
            checkErrors()
            logger.error("no primary constructor", classDecl)
            return
        } else {
            // constructor test
            if (ctor.typeParameters.isNotEmpty())
                addError("constructor has type parameters", classDecl)
            for (parameter in ctor.parameters) {
                if (parameter.name == null)
                    addError("parameter without name", parameter)
            }
        }

        if (checkErrors()) return

        val staticArgs = ctor.parameters.filter { it.isAnnotationPresent(StaticBuilderArg) }
        val dynamicArgs = ctor.parameters.filter { !it.isAnnotationPresent(StaticBuilderArg) }
        val builderType = ClassName(ctor.packageName.asString(), "${classDecl.simpleName.asString()}Builder")

        val type = TypeSpec.classBuilder(builderType).also { type ->
            type.primaryConstructor(FunSpec.constructorBuilder().also { builderCtor ->
                for (staticArg in staticArgs) {
                    val name = staticArg.name!!.asString()
                    val typeName = staticArg.type.toTypeName()
                    builderCtor.addParameter(name, typeName)
                    builderCtor.addCode("this.%N = %N\n", name, name)
                    type.addProperty(name, typeName, KModifier.PRIVATE)
                }
            }.build())

            for (dynamicArg in dynamicArgs) {
                val name = dynamicArg.name!!.asString()
                val pascalName = name[0].uppercase() + name.substring(1)
                val typeName = dynamicArg.type.toTypeName()
                if (typeName.isList()) {
                    val mutableType = MutableList.parameterizedBy(typeName.typeArguments)
                    val collectionType = Collection.parameterizedBy(typeName.typeArguments)
                    val pascalSingleName = when {
                        pascalName.endsWith("s") -> pascalName.removeSuffix("s")
                        else -> pascalName
                    }
                    val element = typeName.typeArguments.single()
                    type.addFunction(FunSpec.builder("add$pascalSingleName")
                        .addParameter(name, element)
                        .returns(builderType)
                        .addCode("this.%N.add(%N)\n", name, name)
                        .addCode("return this\n")
                        .build())
                    type.addFunction(FunSpec.builder("add$pascalName")
                        .addParameter(name, collectionType)
                        .returns(builderType)
                        .addCode("this.%N.addAll(%N)\n", name, name)
                        .addCode("return this\n")
                        .build())
                    type.addProperty(PropertySpec.builder(name, mutableType, KModifier.PRIVATE)
                        .initializer("mutableListOf()")
                        .build())
                } else {
                    type.addFunction(FunSpec.builder("with$pascalName")
                        .returns(builderType)
                        .addParameter(name, typeName)
                        .addCode("this.%N = %N\n", name, name)
                        .addCode("return this\n")
                        .build())
                    type.addProperty(PropertySpec.builder(name, typeName.copy(nullable = true), KModifier.PRIVATE)
                        .mutable(true)
                        .initializer("null")
                        .build())
                }
            }

            type.addFunction(FunSpec.builder("build").also { funSpec ->
                funSpec.addCode("return %T(\n", classDecl.toClassName())
                for (parameter in ctor.parameters) {
                    val paramType = parameter.type.toTypeName()
                    if (parameter.isAnnotationPresent(StaticBuilderArg)
                        || paramType.isList()
                        || paramType.isNullable) {
                        funSpec.addCode("%N, \n", parameter.name!!.asString())
                    } else {
                        funSpec.addCode("%N ?: error(%S), \n",
                            parameter.name!!.asString(),
                            "no value for ${parameter.name} specified")
                    }
                }
                funSpec.addCode(")\n")
            }.build())
        }.build()

        generator.createNewFile(Dependencies(true, classDecl.containingFile!!),
            builderType.packageName,
            builderType.simpleName)
            .bufferedWriter()
            .use {
                FileSpec.builder(builderType.packageName, builderType.simpleName)
                    .addType(type)
                    .build()
                    .writeTo(it)
            }
    }

    companion object {
        val BuildBuilder = BuildBuilder::class.qualifiedName!!
        val StaticBuilderArg = StaticBuilderArg::class.qualifiedName!!
        val List = ClassName("kotlin.collections", "List")
        val MutableList = ClassName("kotlin.collections", "MutableList")
        val Collection = ClassName("kotlin.collections", "Collection")
    }
}

private fun KSAnnotated.isAnnotationPresent(qname: String) =
    annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == qname }

private fun TypeName.isList(): Boolean {
    contract { 
        returns(true) implies (this@isList is ParameterizedTypeName)
    }
    return this is ParameterizedTypeName &&
            (this.rawType == SymbolProcessorImpl.List || this.rawType == SymbolProcessorImpl.MutableList)
}
