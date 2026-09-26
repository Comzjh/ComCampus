package com.xmu.course.adapter.jw

import android.net.Uri
import com.xmu.course.contracts.campusservice.CampusServiceDescriptor
import com.xmu.course.contracts.campusservice.CampusServiceLauncher
import com.xmu.course.contracts.campusservice.CampusServiceProvider
import com.xmu.course.contracts.provider.ProviderCapability
import com.xmu.course.contracts.provider.ProviderDescriptor
import com.xmu.course.contracts.provider.SyncPolicy
import com.xmu.course.data.academicimport.AcademicImportFileSource
import com.xmu.course.data.academicimport.ContentResolverAcademicImportFileSource
import java.io.InputStream

/**
 * XMU JW Adapter：CampusServiceLauncher / JwGateway / JwDocumentProvider /
 * CampusServiceProvider 的当前实现。
 *
 * - 页面入口由组合根注入（导航仍归 app 组合根所有）；
 * - 服务清单与 openService 走 CampusServiceProvider，未实现服务明确拒绝；
 * - PDF 一次性来源复用 AcademicImportFileSource，不保存 PDF/URI；
 * - MANUAL_ONLY：不自动打开页面、不自动下载、不后台访问 JW。
 */
@Suppress("DEPRECATION")
class XmuJwAdapter(
    private val onOpenAcademicReport: () -> Unit,
    private val onOpenCertificate: () -> Unit = {},
    private val openDocumentStream: (Uri) -> InputStream?,
) : CampusServiceLauncher, JwGateway, JwDocumentProvider, CampusServiceProvider {

    override fun openAcademicReport() {
        onOpenAcademicReport()
    }

    override fun openDocumentPage() {
        onOpenAcademicReport()
    }

    override fun createOneShotPdfSource(event: JwAcademicPdfDownload): AcademicImportFileSource? {
        if (!event.isValidAcademicPdfDownload()) return null
        return ContentResolverAcademicImportFileSource(event.uri, openDocumentStream)
    }

    override fun descriptor(): ProviderDescriptor = ProviderDescriptor(
        id = JwCampusServices.PROVIDER_ID,
        capabilities = setOf(ProviderCapability.CAMPUS_SERVICE),
        syncPolicy = SyncPolicy.MANUAL_ONLY,
    )

    override fun services(): List<CampusServiceDescriptor> = listOf(
        CampusServiceDescriptor(
            serviceId = JwCampusServices.ACADEMIC_COMPLETION,
            capability = ProviderCapability.CAMPUS_SERVICE,
            displayKey = "campus_service.jw.academic_completion",
        ),
        CampusServiceDescriptor(
            serviceId = JwCampusServices.CERTIFICATE,
            capability = ProviderCapability.CAMPUS_SERVICE,
            displayKey = "campus_service.jw.certificate",
        ),
    )

    override fun openService(serviceId: String) {
        when (serviceId) {
            JwCampusServices.ACADEMIC_COMPLETION -> onOpenAcademicReport()
            JwCampusServices.CERTIFICATE -> onOpenCertificate()
            else -> throw IllegalArgumentException("unsupported campus service: $serviceId")
        }
    }
}