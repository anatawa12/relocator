package com.anatawa12.relocator.builder

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
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
        val classes = resolver.getSymbolsWithAnnotation(BuildBuilder::class.qualifiedName!!)
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
        val buildBuilder = classDecl.getAnnotationsByType(BuildBuilder::class).single()

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

        val params = ctor.parameters.map(BuildingParameterSpec::create)

        val builderType = if (buildBuilder.fqname.isNotEmpty()) {
            ClassName(buildBuilder.fqname.substringBeforeLast('.', ""),
                buildBuilder.fqname.substringAfterLast('.'))
        } else {
            ClassName(ctor.packageName.asString(), "${classDecl.simpleName.asString()}Builder")
        }

        val type = TypeSpec.classBuilder(builderType).also { type ->
            type.addModifiers(KModifier.ABSTRACT)
            val builderCtor = FunSpec.constructorBuilder()

            for (param in params) {
                when (param) {
                    is StaticArgParameterSpec -> {
                        builderCtor.addParameter(param.name, param.type)
                        builderCtor.addCode("this.%N = %N\n", param.name, param.name)
                        type.addProperty(param.name, param.type, KModifier.PRIVATE)
                    }
                    is SimpleBuildingParameterSpec -> {
                        type.addFunction(FunSpec.builder(param.funName)
                            .returns(builderType)
                            .addParameter(param.name, param.type)
                            .addCode("this.%N = %N\n", param.name, param.name)
                            .addCode("return this\n")
                            .build())
                        type.addProperty(PropertySpec.builder(param.name, param.type.copy(nullable = true))
                            .addModifiers(KModifier.PRIVATE)
                            .mutable(true)
                            .initializer("null")
                            .build())
                    }
                    is ListBuildingParameterSpec -> {
                        val mutableType = cnMutableList.parameterizedBy(param.elementType)
                        val collectionType = cnCollection.parameterizedBy(param.elementType)
                        type.addFunction(FunSpec.builder(param.addName)
                            .addParameter(param.name, param.elementType)
                            .returns(builderType)
                            .addCode("this.%N.add(%N)\n", param.name, param.name)
                            .addCode("return this\n")
                            .build())
                        type.addFunction(FunSpec.builder(param.addAllName)
                            .addParameter(param.name, collectionType)
                            .returns(builderType)
                            .addCode("this.%N.addAll(%N)\n", param.name, param.name)
                            .addCode("return this\n")
                            .build())
                        type.addProperty(PropertySpec.builder(param.name, mutableType, KModifier.PRIVATE)
                            .initializer("mutableListOf()")
                            .build())}
                }
            }

            type.primaryConstructor(builderCtor.build())

            type.addFunction(FunSpec.builder("build").also { funSpec ->
                funSpec.returns(classDecl.toClassName())
                funSpec.addCode("return buildInternal(\n")
                for (parameter in ctor.parameters) {
                    val paramType = parameter.type.toTypeName()
                    if (parameter.isAnnotationPresent(StaticBuilderArg::class)
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

            type.addFunction(FunSpec.builder("buildInternal").also { funSpec ->
                funSpec.returns(classDecl.toClassName())
                funSpec.addModifiers(KModifier.ABSTRACT, KModifier.INTERNAL)
                for (parameter in ctor.parameters) {
                    funSpec.addParameter(parameter.name!!.asString(), parameter.type.toTypeName())
                }
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
}

val cnList = ClassName("kotlin.collections", "List")
val cnMutableList = ClassName("kotlin.collections", "MutableList")
val cnCollection = ClassName("kotlin.collections", "Collection")

private fun TypeName.isList(): Boolean {
    contract { 
        returns(true) implies (this@isList is ParameterizedTypeName)
    }
    return this is ParameterizedTypeName && (this.rawType == cnList || this.rawType == cnMutableList)
}

internal sealed class BuildingParameterSpec(val name: String, val type: TypeName) {
    companion object {
        fun create(param: KSValueParameter): BuildingParameterSpec {
            val funName = param.getAnnotationsByType(BuilderFunName::class).firstOrNull()

            val typeName = param.type.toTypeName()
            val name = funName?.fieldName?.ifEmpty { null } ?: param.name!!.asString()

            if (param.isAnnotationPresent(StaticBuilderArg::class))
                return StaticArgParameterSpec(name, typeName)

            val listArg = param.getAnnotationsByType(BuilderListArg::class).firstOrNull()
            if (listArg?.value ?: typeName.isList()) {
                typeName as ParameterizedTypeName
                val elementType = typeName.typeArguments.single()
                val pascalName = name[0].uppercaseChar() + name.substring(1)
                return ListBuildingParameterSpec(
                    name,
                    typeName,
                    elementType,
                    funName?.name?.ifEmpty { null } ?: "add${pascalName.removeSuffix("s")}",
                    funName?.addAll?.ifEmpty { null } ?: "add$pascalName",
                )
            } else {
                return SimpleBuildingParameterSpec(
                    name,
                    typeName,
                    funName?.name?.ifEmpty { null } ?: name,
                )
            }
        }
    }
}
internal class StaticArgParameterSpec(name: String, type: TypeName) : BuildingParameterSpec(name, type)
internal class SimpleBuildingParameterSpec(name: String, type: TypeName, val funName: String) : BuildingParameterSpec(name, type)
internal class ListBuildingParameterSpec(name: String, type: TypeName, val elementType: TypeName, val addName: String, val addAllName: String) : BuildingParameterSpec(name, type)
