package com.xmu.course.data.academicimport.parser

/** 候选记录的解析来源事实，不代表课程状态、用户身份或学校规则。 */
enum class AcademicImportCandidateProvenance {
    COMPLETED_DETAIL,
    PROGRAM_REQUIREMENT,
    UNKNOWN_SECTION,
}
