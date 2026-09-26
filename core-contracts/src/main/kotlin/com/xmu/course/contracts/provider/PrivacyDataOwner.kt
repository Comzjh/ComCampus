package com.xmu.course.contracts.provider

/**
 * 隐私数据所有权契约：Provider 声明自己拥有哪些本地数据，并支持用户主动清除。
 *
 * 目标场景：未来"我的 → 数据管理"入口按 Provider 清除本地数据（如课表、待办、JW 数据）。
 * 约束：清除动作必须由用户显式触发；只影响声明方自己的数据范围；
 * 不得借此上传、回写或触发同步。
 *
 * 本轮仅定义契约；现有实现只提供只读计数，clearLocalData 由后续阶段接入。
 */
interface PrivacyDataOwner {

    /** 用户主动查询该 Provider 拥有的本地数据条数（只读，用于删除前的确认流程）。 */
    suspend fun countLocalData(): Int

    /** 用户主动清除该 Provider 拥有的全部本地数据。 */
    suspend fun clearLocalData()
}