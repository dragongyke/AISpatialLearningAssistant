# 空间 AI 学习助手

[English](README.md) | [简体中文](README_zh.md)

空间 AI 学习助手是一个面向 PICO 开发者的空间视频学习参考应用。用户在 Shared
Space 中选择公开课程，进入 Full Space 讲堂观看视频，随时通过语音提问，并获得与
视频时间点关联的 AI 解答和 Markdown 学习纪要。

![空间 AI 学习助手](design/posters/ai-spatial-learning-wide-2560x1440.jpg)

## Demo 能力

### Shared Space

- 内置 5 个可直接播放的 Stanford Engineering Everywhere 公开课程视频。
- 支持分类与文本搜索、横向 3.5 卡片宽度的课程轮播，并轻量探测视频源状态及重试。
- 使用独立的历史学习 WindowContainer，显示观看进度、最近学习时间、续播位置，
  并将每个课程与对应纪要关联。关闭后可通过课程主窗口右上角的“学习历史”按钮重新打开。
- 应用启动后直接进入课程目录，不再包含旧示例启动页。

### Full Space 学习

- 中央视频屏幕和共用播放器时间轴的 WebVTT 字幕；可按 500 ms 校准字幕提前/延后并重置。
- 左侧学习大纲、右侧 AI 助教、视频下方播控面板，以及身前独立的 3D 语音水晶球。
- 支持播放、暂停、进度跳转、播放速率、提问自动暂停，以及通过“继续”等意图恢复播放。
  普通问题中出现“继续”不会触发播放；课程播完后停止，不再自动循环。
- 底部拖动条支持注视高亮、坐标平滑、分区限位和松手吸附，并支持恢复布局和保存为默认布局。
  只有底部控制条可以发起面板拖动；Attachment UI 和 3D 碰撞区域共用手势捕获，
  松手、取消手势或离开 Stage 后释放，支持连续重复拖动。
- 大纲、AI 助教和播控面板使用放大的空间尺寸和字号，可见拖动条与面板共用排版，
  固定在内容下方，拖动碰撞区域仅覆盖控制条；播放控制行保持可见，播控辅助信息可滚动。
  视频拖动条位于字幕下方。水晶球使用独立的 3D 碰撞区域：短 pinch 发起提问，
  pinch 后移动可以拖动，拖动结束不会误触发提问，不再需要语音拖动条。
  凝视高亮与光核动画分别反馈可用、聆听、分析、回答和朗读状态。
  底座采用雕刻胡桃木与旧黄铜造型；等待回答时出现汇聚光丝和旋转符文，区别于待机与朗读。
- 进入场景和恢复布局时，左右面板中心与视频对齐，播控保持在字幕下方；
  根据完整面板和拖动条的最低边缘计算高度，至少离地 12 cm，仅在必要时抬高整体布局。
- 默认加载使用 Blender 制作的学院图书馆环境，包含双层藏书架、中央拱廊、阅览桌椅和烘焙光照。
  拱廊对面的实墙作为默认学习方向，视频默认贴合木框黑板；房间和黑板使用同一定位基准，
  视频仍可单独拖动，恢复布局会将视频归位至黑板。旧版布局升级时重置视频与语音入口位置，
  保留两侧面板偏好；
  可编辑模型及重建方法见[环境资产模块](lecture-hall-editor-asset/README.md)。
- 播控面板提供“查看现实”开关，可在虚拟讲堂和 PICO VST 透视之间切换。

### AI 学习辅助

- 豆包 Seed 基于当前播放进度、附近字幕、课程大纲和历史对话，流式输出更自然的助教回答；
  提供简洁、深入、举例和引导提问四种教学模式，并跟随提问语言回复。
- 交互回答关闭模型深度思考，并请求方舟 `fast` 服务档位；账号未开通时自动重试 `auto`，
  后续问题复用该选择。服务端也可能返回 `default` 档位。
  开始语音提问时后台预取当前视频帧，仅涉及画面的问题才将其与字幕送入同一次回答。
- 豆包在线语音识别支持中文和英文提问。
- APK 内置中文 Vosk 模型作为离线 ASR 备选，安装后不需要再次下载模型。
- 使用豆包 V3 双向流式语音合成，从回答的开头分句开始朗读；同一会话持续接收文本，
  通过一个带缓冲的 PCM 播放器连续播放，后续句子合成不再等待上一句播完。
  再次语音提问时，会中断视觉分析、文字生成、排队语音和当前朗读。
- Stage 显示 ASR 首个中间结果/最终结果、视觉分析、LLM 首字、TTS 首包音频和总耗时，
  以及提交问题到首个实际音频的耗时；后者从 ASR 最终结果或文字提交开始计算，
  不包含用户说话和 ASR 断句耗时，不代表延迟保证。
- 每门课程的 Markdown 学习笔记会在学习中持续更新，课程播完或退出 Stage 后由 AI 重新整理终稿，
  并可从对应历史记录直接打开应用内窗口进行搜索、编辑、复制、重新整理，或点击时间戳
  跳回视频对应位置。
- 实时记录与 AI 总结分别更新各自的受管区域，保留应用内人工编辑和原有未标记笔记；
  整理笔记不会阻塞语音提问。

## 使用流程

1. 启动应用，进入 Shared Space 课程目录。
2. 选择课程后进入 Full Space Stage，Shared Space 窗口移到后台。
3. 观看视频，在不理解的地方对语音水晶球做短 pinch 提问。
4. 应用可自动暂停视频并通过豆包处理问题；只有涉及当前画面的问题才增加视觉分析。
5. 用户可立即阅读流式文字，在完整回答生成前听取已完成句子，也可随时用新问题打断。
6. 在历史记录中打开实时草稿或终稿，进行搜索、编辑、复制、重新整理和时间戳跳转。
7. 退出 Stage 后恢复课程目录和历史学习窗口。

## 技术栈

- Kotlin 与 Jetpack Compose
- PICO Spatial SDK BOM 6.0.0
- PICO Spatial UI WindowContainer、Stage、Entity 和 Asset Bundle API
- 基于 PICO CypressMediaPlayer 的网络视频播放
- 火山方舟豆包 Seed 流式对话与按需视觉模型 API
- 火山引擎豆包流式 ASR、V3 双向流式 WebSocket TTS 与 AudioTrack 连续播放
- Vosk Android 离线识别备选
- Koin 依赖注入

## 项目结构

| 路径 | 用途 |
| --- | --- |
| `app/` | Android 应用、空间 UI、播放、学习状态、AI、语音、字幕、历史记录和测试 |
| `lecture-hall-editor-asset/` | 项目自制大学讲堂的场景源码和打包模块 |
| `design/` | 应用图标、商店海报和详情页图片 |

Kotlin 命名空间继续使用 `com.pico.spatial.sample.spatialvideo`，因为项目从 PICO
Spatial Video 示例演进而来。最终安装包名为
`com.pico.spatial.sample.aispatiallearning`。

## 环境要求

- Windows 开发主机与 Android Studio
- Gradle 使用 JDK 17
- Android SDK 35
- PICO Spatial SDK、Spatial Gradle Plugin、Editor 和 Emulator 6.0.0
- 兼容 PICO OS 6 的设备或 PICO Emulator
- 在线课程和云端 AI 服务所需的网络连接

## 克隆项目

克隆前安装 [Git LFS](https://git-lfs.com/)。模型、贴图、图片、离线语音模型和
可直接使用的场景 Bundle 均通过 LFS 管理。

```powershell
git lfs install
git clone https://github.com/dragongyke/AISpatialLearningAssistant.git
cd AISpatialLearningAssistant
git lfs pull
```

请使用 Git 克隆而非 GitHub 的源码 ZIP 下载，确保必需的 LFS 资源完整下载。
仓库不包含云端 API 凭据或 APK。

## API 配置

云端凭据在构建时从 Gradle 属性或环境变量读取。用于本地测试时，可将以下内容写入
`%USERPROFILE%\.gradle\gradle.properties`：

```properties
VOLCENGINE_SPEECH_APP_ID=your-speech-app-id
VOLCENGINE_SPEECH_ACCESS_TOKEN=your-speech-access-token
ARK_API_KEY=your-ark-api-key
ARK_VISION_MODEL=your-vision-model-or-endpoint-id
ARK_TEXT_MODEL=your-text-model-or-endpoint-id
```

未配置 `ARK_TEXT_MODEL` 时，Demo 会复用 `ARK_VISION_MODEL` 处理文本回答。
当前使用 `volc.bigasr.sauc.duration` 作为 ASR Resource ID，并使用
`volc.service_type.10029` 作为 TTS Resource ID。云端配置缺失时会明确降级：
AI 助教使用本地字幕上下文生成备选回答，跳过方舟视觉分析，并在可用时使用内置中文
Vosk 模型识别语音。

凭据只能保存在上述用户级本地文件或环境变量中，不得写入受 Git 跟踪的源码、项目 Gradle
文件、文档或压缩包。Debug 构建会将本机凭据写入 `BuildConfig`，因此 Debug APK 也只能
私下测试，不得上传。Release 构建明确清空模型 Key、语音 Token 和语音 App ID；
正式分发前需要另行实现运行时凭据配置或可信服务端代理。

克隆后启用提交和推送检查（推送钩子保留 Git LFS 支持）：

```powershell
git config core.hooksPath .githooks
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-secrets.ps1 -SelfTest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check-secrets.ps1 -AllHistory
```

扫描只报告路径，不打印凭据原文，检查常见密钥格式及本机已配置的 Key；它不能替代服务端
安全扫描。若发生泄露，应立即撤销并轮换 Key：只删除最新文件并不能清除 Git 历史。

## 构建与验证

使用仓库中已打包的场景 Bundle 构建应用时，只配置应用模块即可，无需通过 PICO Editor
重新打包环境：

```powershell
.\gradlew.bat --configure-on-demand :app:spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

将 Debug APK 安装到已连接设备：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

修改大学讲堂场景后，需要安装匹配的 PICO Editor 6.0 工具，再重新构建 Asset Bundle。
仅安装更新版本的 Editor 不能满足资产模块的 `spatialToolsVersion = 6.0` 配置：

```powershell
.\gradlew.bat :lecture-hall-editor-asset:assembleDebug
Copy-Item lecture-hall-editor-asset\build\spatial\bundle\lecture-hall-editor-asset.bundle app\src\main\assets\lecture-hall-editor-asset.bundle
```

## 本地数据

观看进度、对话历史、布局变换和纪要元数据均为本地单用户数据。笔记先原子保存到应用内，
再通过 Android Storage Access Framework 或公共 `Documents/AI Spatial Learning` 目录导出。
导出不可用时，应用内笔记窗口仍可读取本地副本。卸载会删除应用私有副本，重要笔记应先导出。
当前版本没有账号系统和云同步。

## 当前范围

- 第一版不包含 Avatar 形象。
- 在线课程地址和云服务可能发生变化或暂时不可用。
- 方舟视觉理解依赖播放器能够成功提取当前视频流画面。
- 内置离线 ASR 仅支持中文，准确率低于在线服务。

## 许可证与内容

项目源码使用 [Apache License 2.0](LICENSE.txt)。公开课程视频、内置字幕、Vosk 模型和
第三方 SDK 分别遵循其自身条款，详见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
