# XMU Course

厦门大学学生专属的本地课程表 Android App

## 简介

XMU Course 是面向厦门大学学生开发的 Android 本地课程表应用。

应用支持从厦门大学金智教务“我的课表”页面导入课程，在本地完成 HTML 解析并保存到 Room 数据库。项目采用本地优先设计，不依赖自建服务器；课程数据保存在用户设备本地。

教务登录发生在应用内 WebView 中，网络请求由教务页面完成。项目代码不包含上传用户课表的接口，也不保存厦大统一身份认证账号密码。

## 当前功能

- 厦门大学金智教务 WebView 导入
- 本地 HTML 课程解析
- Room 本地存储
- 周视图课表与当前周计算
- 单周、双周课程过滤
- 连堂课程显示
- 冲突课程自动分栏布局
- 课表、学期和课程管理
- 翘课模式：保留课程位置并降低显示透明度
- 背景、模糊、遮罩、字号、网格线等个性化设置
- 今日课程 Widget（4×2）
- 下一节课 Widget（2×2）与分钟级倒计时
- 使用教程与支持开发页面
- 支付宝、微信收款码查看、放大与保存到系统相册
- Material 3、浅色/深色模式

## 隐私与数据

- 课程和显示设置写入用户设备本地数据库与偏好设置。
- 项目不提供自建服务器，不包含上传课程数据的功能。
- 教务账号密码由 WebView 登录页面处理，应用课程数据层不保存账号密码。
- 使用公共设备时，请在离开前退出教务页面，并按需清理本机课表。

## 构建

环境要求：

- JDK 17+
- Android Studio（AGP 8.7+）
- Android SDK 35

Debug 构建：

```bash
./gradlew test
./gradlew assembleDebug
```

Release 构建需要本地签名文件。复制 `keystore.properties.example` 为 `keystore.properties`，填写本地 keystore 路径、alias 和密码。`keystore.properties`、`*.jks` 与 `*.keystore` 已加入 `.gitignore`，不要提交到仓库。

```bash
./gradlew clean assembleRelease
```

签名校验：

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

## 版本

当前版本：`0.6.2`（versionCode `26`）。

## License

MIT
