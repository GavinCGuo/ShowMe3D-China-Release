# ShowMe3D 空间创作（国内封闭版）

面向 PICO 头显的空间创作与三维演示应用。支持空间白板、模型与图片导入、文字与画笔、分页演示、动画、标注、视点及内置示例。

本版本无需配置 API。云端 AI 助手与生成功能暂未开放，入口显示“即将开放”。内置资源库无人机已移除。

## 构建

需要 JDK 17、Android SDK 35。依赖通过 Gradle 中声明的 Maven 仓库获取。

```sh
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:assembleDebug :app:assembleRelease
```

此仓库为国内发布快照，所有运行资源直接包含在 Git 中，无需 Git LFS。第三方素材来源见 DINOSAUR_ASSET_SOURCES.md 与 THIRD_PARTY_UI_ASSETS.md。
