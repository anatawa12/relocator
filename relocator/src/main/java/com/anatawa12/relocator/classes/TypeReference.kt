@file:JvmName("TypeReferences")
@file:Suppress("DEPRECATION", "unused")

package com.anatawa12.relocator.classes

import com.anatawa12.relocator.internal.checkBits
import com.anatawa12.relocator.internal.checkBitsOrM1
import org.objectweb.asm.TypeReference as ATR

// TODO make constructor private
class TypeReference @Deprecated("This is unsafe") constructor(internal val value: Int)

fun newField() = newTypeReference(ATR.FIELD)
fun newMethodReturn() = newTypeReference(ATR.METHOD_RETURN)
fun newMethodReceiver() = newTypeReference(ATR.METHOD_RECEIVER)
fun newLocalVariable() = newTypeReference(ATR.LOCAL_VARIABLE)
fun newResourceVariable() = newTypeReference(ATR.RESOURCE_VARIABLE)
fun newInstanceof() = newTypeReference(ATR.INSTANCEOF)
fun newNew() = newTypeReference(ATR.NEW)
fun newConstructorReference() = newTypeReference(ATR.CONSTRUCTOR_REFERENCE)
fun newMethodReference() = newTypeReference(ATR.METHOD_REFERENCE)
fun newClassTypeParameter(paramIndex: Int) = newTypeParameterReference(ATR.CLASS_TYPE_PARAMETER, paramIndex)
fun newMethodTypeParameter(paramIndex: Int) = newTypeParameterReference(ATR.METHOD_TYPE_PARAMETER, paramIndex)

fun newClassTypeParameter(paramIndex: Int, boundIndex: Int) =
    newTypeParameterBoundReference(ATR.CLASS_TYPE_PARAMETER, paramIndex, boundIndex)
fun newMethodTypeParameter(paramIndex: Int, boundIndex: Int) =
    newTypeParameterBoundReference(ATR.METHOD_TYPE_PARAMETER, paramIndex, boundIndex)

fun newSuperTypeReference(itfIndex: Int) =
    TypeReference(ATR.newSuperTypeReference(itfIndex.checkBitsOrM1(16, "itfIndex")).value)
fun newFormalParameterReference(paramIndex: Int) =
    TypeReference(ATR.newFormalParameterReference(paramIndex.checkBits(8, "paramIndex")).value)
fun newExceptionReference(exceptionIndex: Int) =
    TypeReference(ATR.newExceptionReference(exceptionIndex.checkBits(16, "exceptionIndex")).value)
fun newTryCatchReference(tryCatchBlockIndex: Int) =
    TypeReference(ATR.newTryCatchReference(tryCatchBlockIndex.checkBits(16, "tryCatchBlockIndex")).value)

fun newCast(argIndex: Int) =
    newTypeArgumentReference(ATR.CAST, argIndex)
fun newConstructorInvocationTypeArgument(argIndex: Int) =
    newTypeArgumentReference(ATR.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, argIndex)
fun newMethodInvocationTypeArgument(argIndex: Int) =
    newTypeArgumentReference(ATR.METHOD_INVOCATION_TYPE_ARGUMENT, argIndex)
fun newConstructorReferenceTypeArgument(argIndex: Int) =
    newTypeArgumentReference(ATR.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, argIndex)
fun newMethodReferenceTypeArgument(argIndex: Int) =
    newTypeArgumentReference(ATR.METHOD_REFERENCE_TYPE_ARGUMENT, argIndex)

private fun newTypeReference(sort: Int) =
    TypeReference(ATR.newTypeReference(sort).value)

private fun newTypeParameterReference(sort: Int, paramIndex: Int) = 
    TypeReference(ATR.newTypeParameterReference(
        sort, 
        paramIndex.checkBits(8, "paramIndex")).value,
    )

private fun newTypeParameterBoundReference(sort: Int, paramIndex: Int, boundIndex: Int) =
    TypeReference(ATR.newTypeParameterBoundReference(
        sort, 
        paramIndex.checkBits(8, "paramIndex"), 
        boundIndex.checkBits(8, "boundIndex"),
    ).value)

private fun newTypeArgumentReference(sort: Int, argIndex: Int) =
    TypeReference(ATR.newTypeArgumentReference(sort, argIndex.checkBits(24, "argIndex")).value)
