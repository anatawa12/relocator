package com.anatawa12.relocator.builder

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class BuildBuilder(val fqname: String = "")

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class StaticBuilderArg

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class BuilderFunName(
    val name: String = "",
    val addAll: String = "",
    val fieldName: String = "",
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class BuilderListArg(val value: Boolean)
