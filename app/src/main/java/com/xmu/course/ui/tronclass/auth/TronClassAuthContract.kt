package com.xmu.course.ui.tronclass.auth

import android.app.Activity

const val TRONCLASS_COURSES_URL = "https://lnt.xmu.edu.cn/user/courses"
const val TRONCLASS_SESSION_COOKIE_NAME = com.xmu.course.data.tronclass.auth.TRONCLASS_SESSION_COOKIE_NAME
const val EXTRA_CLEAR_TRONCLASS_AUTH = "com.xmu.course.extra.CLEAR_TRONCLASS_AUTH"
const val EXTRA_VERIFIED_SESSION_COOKIE_NAME = "com.xmu.course.extra.VERIFIED_SESSION_COOKIE_NAME"

const val RESULT_SESSION_UNAVAILABLE = Activity.RESULT_FIRST_USER + 1
const val RESULT_CLEAR_TRONCLASS_AUTH_FAILED = Activity.RESULT_FIRST_USER + 2
