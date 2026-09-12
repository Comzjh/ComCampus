import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 发布签名只从本机 keystore.properties 读取，文件已加入 .gitignore。
val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.xmu.course"
    compileSdk = 35
        buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.xmu.course"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "0.6.3"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    signingConfigs {
        create("release") {
            releaseKeystoreProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }?.let {
                storeFile = rootProject.file(it)
            }
            storePassword = releaseKeystoreProperties.getProperty("storePassword")
            keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
            keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

// Compose 测试 Manifest 由官方建议挂载在 debugImplementation；本项目的 JVM/Robolectric 测试
// 统一运行 debug variant，release unit test 只重复同一套用例，因此禁用。
tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
    enabled = false
}

// 防止误把未签名 APK 当作正式包发布；缺少本地签名文件时直接给出明确错误。
tasks.matching { it.name == "assembleRelease" }.configureEach {
    doFirst {
        val storePath = releaseKeystoreProperties.getProperty("storeFile")
        val store = storePath?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
        val requiredKeys = listOf("storePassword", "keyAlias", "keyPassword")
        if (store == null || !store.exists() || requiredKeys.any { releaseKeystoreProperties.getProperty(it).isNullOrBlank() }) {
            throw GradleException(
                "缺少 release 签名配置。请复制 keystore.properties.example，生成本地 keystore 后再执行 assembleRelease。",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Widget: Jetpack Glance AppWidget。
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // 数据层：Phase 3/4 启用（依赖先行声明，避免反复改构建文件）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.jsoup)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
}




