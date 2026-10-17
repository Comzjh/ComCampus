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
- 重新进入应用时课表回到当前教学周，自建课表可设置开学日期。
- 支持课前30分钟本地通知，包含课程、开始时间和已有地点；默认静默显示，在设置页主动开启所需权限；声音与震动由用户自行开启。

### 学业

- 查看本学期学业概况、历史成绩与培养方案信息。
- 手动刷新教务数据，并在本机缓存最近一次结果。
- 提供 GPA 设置与学业模拟；模拟值不会写回教务成绩。

### 畅课与待办

- 在厦大统一身份认证 WebView 中登录畅课，同步课程。
- 将畅课作业和考试事项导入待办；支持关联课程与本地手动待办。
- 可在设置中控制作业导入与自动刷新；前台检查最多每小时一次，待办Widget另有每小时联网后台计划及手动刷新入口，自动同步继续遵守开关和已有登录态。
- 待办支持编辑、完成、删除、截止时间和逾期状态；待办截止通知暂未提供。已有畅课条目继续更新，新出现的历史条目保留首次导入限制。

### Widget、主题与教程

- 提供今日课程、下一节课和待办 Widget。
- 支持浅色/深色模式、课表背景与显示设置，并内置使用教程和校园服务入口。

## 下载与更新

- [下载最新稳定版](https://github.com/Comzjh/ComCampus/releases/latest)；当前公开版本为 **v0.9.8**（versionCode 98）。
- Android 8.0（API 26）及以上。
- 应用默认在启动时每 24 小时最多检查一次 GitHub Releases。发现新版后会下载 APK 并校验 SHA-256，再交给 Android 系统安装器；安装仍由用户确认。
- 包名：com.comcampus.app。

## 隐私与数据

- 应用没有 ComCampus 自建服务端。教务和畅课请求直接发送到对应学校服务；学校系统会按其自身规则处理这些请求。
- 课表、待办和学业缓存保存在本机应用数据中。畅课登录后所需的最小会话信息使用 Android Keystore 加密保存在本机；应用不保存明文密码。
- Android 系统数据备份已关闭。
- 自动更新会访问 GitHub API 检查版本；发现新版后从 GitHub Releases 下载并校验 APK，再交给 Android 系统安装器由用户确认。

## 常见问题

另有[纯文本版 FAQ](FAQ.txt)。

### ComCampus 是厦门大学官方应用吗？

不是。ComCampus 是面向厦大学生的独立开源项目，不代表厦门大学。

### 其他学校的同学可以使用或借鉴吗？

可以按 [GNU GPL-3.0-only](LICENSE) 许可研究、修改和分发源码，并遵守许可要求。当前教务和畅课接入针对厦门大学的系统；其他学校需要适配本校的数据来源与解析规则，直接安装不保证可以登录或导入。

### 可以直接导入其他学校的课表吗？

目前不保证兼容其他学校的教务页面或导出格式。本地 HTML 导入也取决于文件格式是否受支持；不兼容时可以手动添加课程。

### 如何检查和安装更新？

可以在应用的“设置 → 应用更新”中手动点“检查更新”。开启自动检查后，应用启动时每 24 小时最多检查一次。也可以从 [GitHub Releases](https://github.com/Comzjh/ComCampus/releases/latest) 下载最新稳定版 APK。应用下载更新后会校验 SHA-256，最后由 Android 安装器完成安装，仍需你确认。

### 如何提交 Bug 或功能建议？

请到 [GitHub Issues](https://github.com/Comzjh/ComCampus/issues) 新建 issue，并提供应用版本、Android 版本和设备型号、复现步骤、实际结果与预期结果。截图或日志请先删去个人信息和敏感内容；不要提交密码、Cookie、CAS Ticket、Token、学号、真实课表或成绩。

### 课表、成绩和待办会上传到 ComCampus 服务器吗？

ComCampus 没有自建服务器，课表、成绩缓存和待办保存在本机。导入或刷新教务、畅课数据时，请求会直接发送到对应学校服务；检查和下载更新会访问 GitHub。

### 应用会保存我的登录密码吗？

登录在学校提供的官方 WebView 页面中完成，应用不保存明文密码。畅课登录所需的最小会话信息会使用 Android Keystore 加密保存在本机；请勿在问题反馈中附上任何登录凭据。

### 没有网络时还能使用吗？

已经保存在本机的课表、待办和学业缓存可供离线查看。登录学校系统、刷新数据和检查或下载更新需要网络。

### 如何切换浅色和深色模式？

应用跟随 Android 系统的浅色/深色主题。请在手机系统设置的“显示”中切换深色主题；不同厂商的菜单名称可能不同。应用“设置 → 外观 → 跟随系统色彩”控制的是 Android 12 及以上的动态配色，不负责切换浅色或深色模式。

### 畅课作业会自动进入待办吗？为什么没有提醒？

在“设置 → 畅课同步”开启“自动导入畅课作业”后，畅课同步时会把作业加入待办。自动刷新同时用于前台检查和待办Widget的每小时联网计划；实际后台执行受Android调度限制，手动刷新仍需要已有登录态。待办截止通知暂未提供。课程可在“设置 → 上课提醒”启用课前30分钟通知，按红点提示主动授权；自建课表还需设置正确的开学日期。

### 换手机或卸载后，本机数据会自动恢复吗？

不会。课程、待办和学业缓存保存在本机，且 Android 系统数据备份已关闭；换机不会自动迁移这些数据，卸载应用会删除本地数据。卸载或清除应用数据前，请先确认需要保留的内容。

### 可以修改、分发或商用吗？

当前源码采用 GNU GPL-3.0-only，允许商业使用；分发本项目或其衍生版本时需要遵守该许可证。第三方依赖和资源仍适用各自的许可。

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
