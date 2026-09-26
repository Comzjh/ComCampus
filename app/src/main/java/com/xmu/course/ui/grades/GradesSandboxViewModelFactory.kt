package com.xmu.course.ui.grades

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xmu.course.XmuCourseApplication
import com.xmu.course.di.GradesDependencies

/** 在组合根把本地教务缓存注入学业模拟。 */
class GradesSandboxViewModelFactory(
    private val dependencies: GradesDependencies,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GradesSandboxViewModel::class.java)) {
            "Unsupported ViewModel: " + modelClass.name
        }
        return GradesSandboxViewModel(
            academicStore = dependencies.academicCompletionStore,
            gradeStore = dependencies.jwGradeStore,
            policyStore = dependencies.academicGpaPolicyStore,
        ) as T
    }

    companion object {
        fun fromApplication(application: Application): GradesSandboxViewModelFactory {
            val app = application as? XmuCourseApplication
                ?: error("GradesSandboxViewModel requires XmuCourseApplication")
            return GradesSandboxViewModelFactory(app.appContainer.grades)
        }
    }
}