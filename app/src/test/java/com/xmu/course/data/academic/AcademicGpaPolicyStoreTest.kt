package com.xmu.course.data.academic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xmu.course.domain.grades.OutsidePlanGpaPolicy
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcademicGpaPolicyStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("academic_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun defaultIsUnconfirmed() {
        assertEquals(OutsidePlanGpaPolicy.UNCONFIRMED, AcademicGpaPolicyStore(context).policy.value)
    }

    @Test
    fun policyRoundTripsThroughPersistence() {
        val store = AcademicGpaPolicyStore(context)
        store.setPolicy(OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN)
        assertEquals(OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN, store.policy.value)
        assertEquals(
            OutsidePlanGpaPolicy.EXCLUDE_OUTSIDE_PLAN,
            AcademicGpaPolicyStore(context).policy.value,
        )
    }

    @Test
    fun corruptedValueFallsBackToUnconfirmed() {
        context.getSharedPreferences("academic_prefs", Context.MODE_PRIVATE).edit()
            .putString("gpa_outside_plan_policy", "SOMETHING_ELSE")
            .apply()
        assertEquals(OutsidePlanGpaPolicy.UNCONFIRMED, AcademicGpaPolicyStore(context).policy.value)
    }
}
