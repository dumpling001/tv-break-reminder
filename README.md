# 看电视定时休息（安卓电视盒子 + 手机投屏）

孩子看动画片，每隔 N 分钟（默认 20）电视自动暂停，全屏播放一段"休息提醒视频"（家长自己拍：去阳台、上厕所、喝水、看远处），家长在遥控器上按 3 次确认键结束休息，视频自动继续，重新计时。

## 当时的环境

- 老款夏普电视 + 腾讯极光电视盒子（Android 10，创维定制系统）；平时用手机投屏（腾讯视频等）看动画片
- 电脑：Intel Mac，Android Studio；手机和盒子、Mac 在同一个 Wi-Fi
- App 包名：`com.example.tvbreak`，最低 API 23，Kotlin，没有任何第三方库

## 仓库内容

- TVBreak 安卓项目（App，代码按你电脑上当前版本为准）
- `tvbreak_server.py`：手机网页控制器（计时 + 网页 + adb）
- `tvbreak.sh`：早期的命令行计时脚本（可选，功能已被控制器取代）
- `README.md`：本文档

## 最终方案

```
手机（浏览器）→ Mac 上的控制器 tvbreak_server.py（网页 + 计时 + adb）
   └─ 到点 → adb 启动盒子上的 TVBreak（全屏 Activity）
        └─ 盖住投屏画面，投屏自动暂停，循环播放 res/raw/rest_all.mp4
             └─ 家长遥控器按 3 次确认键 → App 退出 → 投屏自动继续
                  └─ 脚本检测到 App 已退出 → 重新计时
```

关键设计：盒子系统只允许前台 App 运行，所以计时放在 Mac 上，盒子上的 App 只负责"被叫出来时显示视频"。

## 从零复现的流程

1. **盒子**：设置→通用→"安装未知应用"确认可用；设置→关于，连续点"版本号"7 次进入开发者模式；打开"USB 调试"；在网络设置里查盒子 IP（建议在路由器里给盒子固定 IP）。
2. **Mac**：装 Android Studio；`adb` 在 `~/Library/Android/sdk/platform-tools/`。终端运行 `adb connect <盒子IP>:5555`，显示 `connected` 即可（盒子上可能弹出授权，选"始终允许"）。
3. **项目**：新建 Empty Activity，名称 TVBreak，最低 API 23（新版没有 Language 选项，默认 Kotlin）。
4. **App 逻辑**（MainActivity）：全屏 `VideoView` 循环播放 `res/raw/rest_all.mp4`；遥控器确认键按 3 次退出；屏蔽返回键；`LoudnessEnhancer` 增强音量（盒子上是否生效未确认）；视频出错或缺失时"嘀"一声。
5. **Manifest**：activity 的主题设为 `@android:style/Theme.Black.NoTitleBar.Fullscreen`（避免白屏）；加 `LEANBACK_LAUNCHER` 分类。早期的悬浮窗方案（OverlayService 和相关权限）已废弃，可删除。
6. **视频**：横屏，MP4（H.264 + AAC），720p，四段拼成一个，建议不超过 40 秒；文件名 `rest_all.mp4`，放 `app/src/main/res/raw/`。文件名只能小写字母、数字、下划线，同名不同后缀会冲突。
7. **构建**：Build → Generate App Bundles or APKs → Generate APKs（或 Gradle 里运行 `assembleDebug`）；产物 `app/build/outputs/apk/debug/app-debug.apk`。
8. **安装**：`adb install -r app-debug.apk`。
9. **计时**：推荐用手机网页控制器，见下一节。早期的命令行版 `tvbreak.sh`（`caffeinate -i ~/tvbreak.sh 20`，逻辑相同，没有网页）也能用，两者不要同时运行。
10. **测试时**：脚本参数设成 1 分钟；或直接 `adb -s <盒子IP>:5555 shell am start -n com.example.tvbreak/.MainActivity` 手动触发。

## 手机网页控制器（tvbreak_server.py）

Mac 上运行的小程序，只用 Python 3 标准库，同时做三件事：给手机提供控制网页、负责计时、通过 adb 控制盒子。

**使用**

1. Mac 终端运行：`caffeinate -i python3 tvbreak_server.py`（`caffeinate` 防止 Mac 休眠；第一次运行若弹出防火墙提示，选允许）
2. 查 Mac 的局域网 IP：`ipconfig getifaddr en0`（没有输出就试 `en1`）
3. 手机连同一个 Wi-Fi，浏览器打开 `http://<Mac的IP>:8765`，可以“添加到主屏幕”

**页面功能**：状态（未运行 / 运行中 / 休息中）和倒计时；间隔分钟数（1 到 180，默认 20）；按钮：开始、停止、现在就休息、结束休息。

**配置**（文件开头的常量）：`ADB`（adb 路径）、`BOX`（盒子 IP:端口，IP 变了要改）、`PKG`（App 包名）、`PORT`（默认 8765）。

**实现要点**

- 接口：GET `/` 返回页面，GET `/status` 返回状态 JSON；POST `/start?min=N`、`/stop`、`/breaknow`、`/endbreak`
- 后台线程每秒检查一次，到点就 `adb shell am start` 启动 TVBreak；之后每 5 秒用 `adb shell pidof` 检查进程，进程消失（App 退出后系统会很快清掉它）就认为休息结束，重新计时；最多等 10 分钟，防止卡住
- “结束休息”是 `adb shell input keyevent 23 23 23`，等同遥控器确认键按 3 次
- “现在就休息”不改变运行状态：正在运行则休息结束后继续倒计时，未运行则只休息一次
- 每次休息前先 `adb connect`，所以盒子断线后能自动重连

**注意**：没有登录，同一 Wi-Fi 下的人都能打开页面并控制盒子；Mac 必须开着。

## 踩过的坑

| 现象 | 原因 | 解决 |
|---|---|---|
| 客服说盒子没有开发者选项 | 客服不了解，其实被隐藏 | "版本号"连点 7 次 |
| 开发者选项里没有"网络调试"开关 | 厂商没放出开关 | `adb connect IP:5555` 仍然能连上 |
| 悬浮窗权限设置页被删，App 提示"没有悬浮窗权限设置页面" | 定制系统删了页面 | `adb shell appops set <包名> SYSTEM_ALERT_WINDOW allow`；重装 App 会重置授权 |
| 悬浮窗 App 一退到后台，到点没有任何动静 | 日志 `app not allow running background` → `force stop app`：系统在 App 离开前台不到 1 秒就强杀 | 标准白名单（RUN_IN_BACKGROUND、deviceidle whitelist）和前台服务都无效；放弃盒子上后台计时，改由 Mac 用 adb 把 App 拉到前台 |
| 出现 `Premature EOF` | SDK 下载中途断网 | 自动重试后成功，无需处理 |
| Build 菜单里没有 Build APK | 新版改名 | Build → Generate App Bundles or APKs；或 Gradle 的 assembleDebug |
| Android Studio 新建项目没有 Language，最低只有 API 23 | 新版默认 Kotlin | 直接用 API 23 |
| 盒子不念中文（TextToSpeech 无声） | 盒子没有中文语音引擎 | 改用自己录音，最终改为视频自带声音 |
| 浅绿护眼背景在强光下刺眼 | 浅色高亮度 | 黑底 + 浅绿文字 |
| 提醒出现前白屏几秒 | 每次都是冷启动（日志 Displayed 约 3 秒），系统用主题的白色窗口背景顶着 | 主题改成黑色全屏；约 3 秒黑屏无法消除 |
| 四路视频同时播放 | 四个人声混在一起；老盒子同时解码有风险 | 拼成一个视频 |
| Kotlin 编译：`le.enabled = true` / `tts.language = ...` 报错 | 这类 setter 返回 int，不能用属性语法赋值 | 改成 `setEnabled(true)`、`setLanguage(...)` 方法调用 |
| 视频放不出来 | 手机默认可能录成 HEVC | 用 H.264；iPhone 设置→相机→格式→最兼容；QuickTime 导出 720p 或 ffmpeg 转码 |
| 视频声音偏小 | 源视频音量低；盒子上音频增强是否生效不确定 | 以源文件音量为准，剪辑时调大或 ffmpeg `-af volume=2` |

## 已验证 / 未验证

- 已验证：adb 联网连接；App 前台时显示和声音正常；adb 启动 App 后盖住投屏、投屏自动暂停；按 3 次确认键后投屏自动恢复、不用重投；计时脚本循环；黑底全屏单视频；黑屏替代白屏；手机网页控制器实测可用
- 未验证：音频增强在盒子上是否生效；盒子重启后 5555 端口是否仍可连；用盒子自带 App（非投屏）看视频时的表现

## 已知限制

- Mac 必须开着，并和盒子在同一个 Wi-Fi；盒子 IP 变了要改脚本
- 孩子按遥控器主页键能退出休息画面（系统键，无法屏蔽），遥控器最好由家长保管
- 每次提醒出现前有约 3 秒黑屏（冷启动）
- 计时从点“开始”算起，不跟随视频暂停；关掉终端或 Mac 休眠，计时就停止
- 手机网页控制器没有登录，同一 Wi-Fi 下的人都能打开

## 给 AI 的上下文（下次继续时先读这里）

- 项目是上面的方案，代码在本仓库；不要再提悬浮窗或盒子后台计时，已证明在这台盒子上走不通
- 用户习惯分步骤操作，需要解释每个报错的含义
- 手机网页控制器已实测可用，计时和控制都以它为准
- 可能的后续：让控制器随 Mac 开机自动启动；给盒子固定 IP；让 App 从外部存储读取视频（换视频不用重新编译）；帮朋友在其他电视盒子上复现（先做可行性检查）
