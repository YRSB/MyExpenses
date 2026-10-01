# CI：基于 tag 的构建与 Release

`.github/workflows/release.yml` 会在你推送 `r*` / `v*` tag 时自动构建 **extern release universal APK**，
并把它（连同 R8 mapping）作为资产上传到该 tag 的 GitHub Release。

## 用法

```bash
# 1. 先把版本号写进根目录 build.gradle（versionCode / versionName）
# 2. 提交后打 tag，tag 名即版本号，与上游约定保持一致
git commit -am "prepare release"
git tag r883
git push origin master
git push origin r883          # 这一步会触发 workflow
```

workflow 会自动把根目录 `build.gradle` 中的 `versionCode` 改写成 tag 里的数字（`r883` → `883`），
所以打 tag 前忘记改版本号也不会构建出错版本的包。

也可以在 Actions 页面手动触发（`workflow_dispatch`）：

| 输入项 | 说明 |
|---|---|
| `tag` | 要构建的 tag，留空则构建当前 ref |
| `sync_version_code` | 是否用 tag 覆盖 `build.gradle` 里的 `versionCode`，默认 `true` |
| `publish` | 是否创建/更新 Release，`false` 表示只构建验证 |
| `prerelease` | Release 是否标记为预发布 |

## 签名

Release 构建默认是**未签名**的（仓库里 `myExpenses/build.gradle` 的 `signingConfigs` 被注释掉了，
workflow 不改动该文件，而是在构建后用 `zipalign` + `apksigner` 签名）。
未签名的 APK 无法安装，所以正式发版请在仓库 Settings → Secrets 中添加：

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | keystore 文件的 base64 内容，生成方式：`base64 -w0 my-release.jks` |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥密码，留空则复用 keystore 密码 |

只要 `KEYSTORE_BASE64` 存在就会自动签名；不存在时会发一个带 warning 的未签名包（方便先验证流水线）。
workflow 每次运行结束都会执行 `apksigner verify --print-certs`，签名失败即构建失败。

## 产物

* `myExpenses-<versionName>-<tag>.apk` —— universal APK，包含全部 dynamic feature 模块，可直接安装；
  **它是 Release 上的唯一资产**，Release 已存在时用 `--clobber` 覆盖。
* `mapping-<tag>.txt` —— R8 mapping（约 170 MB），用于反混淆崩溃栈，只作为 workflow artifact 保存 30 天，
  不上传到 Release。

Release 说明只有标题、一张版本信息表（tag / 版本 / variant / commit / 是否签名）和
`metadata/en-US/changelogs/<versionCode>.txt` 里的更新日志。

## 说明

* 只构建 `extern` flavor：`intern` 需要 `settings.gradle` 里当前被注释掉的 `:mlkit*` 模块，
  只有依赖 Google ML Kit 时才需要。
* `fetch-depth: 1` 即可，因为 `build.gradle` 只用 `git show --no-patch` 打 `BUILD_DATE` 时间戳。
* 没有使用 `android-actions/setup-android`：它会尝试安装已被下线的 `tools` 包并直接失败；
  GitHub runner 已预装 SDK 与 cmdline-tools，workflow 只需找到 `sdkmanager`、接受 license，
  `compileSdk 37` 与 build-tools 交给 AGP 自动下载。

## 排错

| 现象 | 原因 / 处理 |
|---|---|
| `Warning: Failed to find package 'tools'` | `android-actions/setup-android` 的问题，已改用 runner 自带 SDK |
| `sdkmanager: command not found` | runner 不把 `cmdline-tools/*/bin` 加进 PATH，workflow 里显式查找；日志会打印实际使用的路径 |
| 找不到 `packageExternReleaseUniversalApk` | AGP 版本变了，workflow 会自动回退到 `assembleExternRelease`，并从 `outputs/apk` 里挑体积最大的那个（universal APK 最大） |
| 找不到 `platforms;android-37` | 只影响预装速度，AGP 会自己下载；日志里是 `::notice::` 而不是失败 |
| Release 里出现未签名 APK | `KEYSTORE_BASE64` 没配好，检查 Secrets 里四个 key 是否都在（`gh secret list`） |