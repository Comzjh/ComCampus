package com.xmu.course.contracts.todo.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TodoFeatureIdentityReferenceContractTest {
    @Test
    fun featureIdentityCarriesCurrentLocalRepresentationWithoutChangingItsBoundary() {
        assertEquals(42L, TodoFeatureId(42L).value)
    }

    @Test
    fun courseReferenceKeepsSameNumericIdsDistinctBySource() {
        val local = TodoCourseReference.Local(7L)
        val external = TodoCourseReference.External(7L)

        assertEquals(7L, local.id)
        assertEquals(7L, external.id)
        assertEquals(true, local != external)
    }
}
