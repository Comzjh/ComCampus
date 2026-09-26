package com.xmu.course.ui.academiccompletion

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xmu.course.XmuCourseApplication

/** 组合根注入 AcademicCompletionStore；UI 不自行构造存储。 */
class AcademicCompletionViewModelFactory(
    private val viewModel: AcademicCompletionViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AcademicCompletionViewModel::class.java)) {
            "Unsupported ViewModel: ${modelClass.name}"
        }
        return viewModel as T
    }

    companion object {
        fun fromApplication(application: Application): AcademicCompletionViewModelFactory {
            val app = application as? XmuCourseApplication
                ?: error("AcademicCompletionViewModel requires XmuCourseApplication")
            return AcademicCompletionViewModelFactory(
                AcademicCompletionViewModel(
                    store = app.appContainer.academicCompletionStore,
                ),
            )
        }
    }
}
