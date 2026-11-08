# KernelFlasher Miuix

基于 [CuriousNom/KernelFlasher](https://github.com/CuriousNom/KernelFlasher) 的 Android 内核管理应用，使用 Miuix 0.9.4 界面，支持内核刷写、备份、恢复和更新源管理。

设备与插槽信息使用 Miuix 卡片，刷写、备份、恢复、更新和重启页面采用统一的标题栏与操作行。界面跟随系统明暗模式，支持下拉刷新、复制数据、日志查看，以及可滚动页面到顶／底的触觉反馈。页面切换与返回手势使用原生 Miuix 导航。

## 上游项目

- 直接上游：[CuriousNom/KernelFlasher](https://github.com/CuriousNom/KernelFlasher)。
- 原始项目：[capntrips/KernelFlasher](https://github.com/capntrips/KernelFlasher)。
- 界面组件库：[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)。

## 下载与构建

在 [GitHub Actions](https://github.com/lgernierO/KernelFlasher-Miuix/actions) 的构建结果中直接下载 `KernelFlasher-miuix-release.apk` 或 `KernelFlasher-miuix-debug.apk`。两个版本使用同一签名，可互相覆盖安装。

Release 默认启用 R8 代码压缩、优化与混淆，并移除未使用的资源。混淆映射生成于 `app/build/outputs/mapping/release/mapping.txt`。

本地使用 JDK 21 和 Android SDK 37 构建：

```sh
bash gradlew :app:assembleDebug :app:assembleRelease
```

CI 使用仓库 Secrets：`SIGNING_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。密钥仅在构建时还原到临时目录。
