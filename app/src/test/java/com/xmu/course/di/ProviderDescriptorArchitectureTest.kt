package com.xmu.course.di

import com.xmu.course.contracts.provider.ProviderDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 架构护栏：ProviderDescriptor 必须保持纯机器身份描述，
 * 禁止出现 displayName / icon / status 等 UI 或运行时状态字段。
 */
class ProviderDescriptorArchitectureTest {
    @Test
    fun descriptorKeepsPureMachineIdentityFields() {
        val fields = ProviderDescriptor::class.java.declaredFields
            .map { it.name }
            .filterNot { it == "serialVersionUID" || it.startsWith("$") }

        assertEquals(listOf("id", "capabilities", "syncPolicy"), fields)
    }
}