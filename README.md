# ComCampus

厦门大学学生的 Android 校园学习应用，围绕课表、学业、畅课与待办，采用本地优先设计。

[![Latest release](https://img.shields.io/github/v/release/Comzjh/ComCampus?display_name=tag&sort=semver)](https://github.com/Comzjh/ComCampus/releases/latest)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-blue.svg)](https://www.gnu.org/licenses/gpl-3.0.html)
[GitHub 仓库](https://github.com/Comzjh/ComCampus)

## 功能

### 课表

- 在厦大教务官方 WebView 中导入课表，也支持本地 HTML 和手动添加。
- 按周查看课程，处理单双周、连堂与时间冲突。
- 管理课程、学期、跳课状态以及背景、字号、网格和深浅主题。

### 学业

- 查看本学期学业概况、历史成绩与培养方案信息。
- 手动刷新教务数据，并在本机缓存最近一次结果。
- 提供 GPA 设置与学业模拟；模拟值不会写回教务成绩。

### 畅课与待办

- 在厦大统一身份认证 WebView 中登录畅课，同步课程。
- 将畅课作业和考试事项导入待办；支持关联课程与本地手动待办。
- 可在设置中控制作业导入和前台自动刷新；自动刷新最多每小时一次，不使用后台定时任务。
- 待办支持编辑、完成、删除、截止时间和逾期状态；目前不提供系统通知或推送提醒。

### Widget、主题与教程

- 提供今日课程、下一节课和待办 Widget。
- 支持浅色/深色模式、课表背景与显示设置，并内置使用教程和校园服务入口。

## 下载与更新

- [下载最新稳定版](https://github.com/Comzjh/ComCampus/releases/latest)；当前公开版本为 **v0.9.1**（versionCode 91）。
- Android 8.0（API 26）及以上。
- 应用默认在启动时每 24 小时最多检查一次 GitHub Releases。发现新版后会下载 APK 并校验 SHA-256，再交给 Android 系统安装器；安装仍由用户确认。
- 包名：com.comcampus.app。

## 隐私与数据

- 应用没有 ComCampus 自建服务端。教务和畅课请求直接发送到对应学校服务；学校系统会按其自身规则处理这些请求。
- 课表、待办和学业缓存保存在本机应用数据中。畅课登录后所需的最小会话信息使用 Android Keystore 加密保存在本机；应用不保存明文密码。
- Android 系统数据备份已关闭。
- 自动更新会访问 GitHub API 检查版本；发现新版后从 GitHub Releases 下载并校验 APK，再交给 Android 系统安装器由用户确认。

## 开发构建

环境要求：JDK 17、Android Studio（AGP 8.7+）和 Android SDK 35。

macOS / Linux：

~~~bash
bash ./gradlew :app:testDebugUnitTest
bash ./gradlew :app:assembleDebug
~~~

Windows：

~~~bat
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
~~~

Release 构建需自行配置签名。将 keystore.properties.example 复制为本机的 keystore.properties，再填写自己的签名文件路径与口令。签名文件和本机配置不得提交到仓库。

~~~bash
bash ./gradlew :app:assembleRelease
~~~

## 许可

当前源码按 **GNU GPL-3.0-only** 许可，详见 [LICENSE](LICENSE)。GPL 允许商业使用；分发本项目或其衍生版本时，必须遵守许可证要求。此前按 MIT 发布的历史版本继续按其随附许可处理。第三方依赖和资源仍适用各自的许可或权利说明。
