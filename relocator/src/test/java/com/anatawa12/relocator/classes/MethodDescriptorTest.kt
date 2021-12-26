package com.anatawa12.relocator.classes

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

internal class MethodDescriptorTest {
    @Test
    fun constructor() {
        MethodDescriptor(void).descriptor
            .`should be equal to`("()V")
        MethodDescriptor(string).descriptor
            .`should be equal to`("()L${"java/lang/String"};")
        MethodDescriptor(void, int, int).descriptor
            .`should be equal to`("(II)V")
        MethodDescriptor(void, string, int).descriptor
            .`should be equal to`("(L${"java/lang/String"};I)V")
    }

    @Test
    fun `arguments and returns`() {
        MethodDescriptor("()V").arguments
            .`should be equal to`(listOf())
        MethodDescriptor("(II)V").arguments
            .`should be equal to`(listOf(int, int))
        MethodDescriptor("(L${"java/lang/String"};I)V").arguments
            .`should be equal to`(listOf(string, int))
        MethodDescriptor("()V").returns
            .`should be equal to`(void)
        MethodDescriptor("()L${"java/lang/String"};").returns
            .`should be equal to`(string)
    }

    val void = TypeDescriptor("V")
    val int = TypeDescriptor("I")
    val string = TypeDescriptor("L${"java/lang/String"};")
}
