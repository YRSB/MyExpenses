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

* `myExpenses-<versionName>-<tag>.apk` —— universal APK，包含全部 dynamic feature 模块，可直接安装
* `mapping-<tag>.txt` —— R8 mapping，用于反混淆崩溃栈
* 两者同时作为 workflow artifact 和 Release 资产保存，Release 已存在时用 `--clobber` 覆盖

Release 说明会自动带上 tag、版本号、版本码、commit、签名状态，
以及 `metadata/en-US/changelogs/<versionCode>.txt` 里的更新日志。

## 说明

* 只构建 `extern` flavor：`intern` 需要 `settings.gradle` 里当前被注释掉的 `:mlkit*` 模块，
  只有依赖 Google ML Kit 时才需要。
* `compileSdk 37` / build-tools 由 AGP 在接受 SDK license 后自动下载，无需在 workflow 里写死版本号。
* `fetch-depth: 1` 即可，因为 `build.gradle` 只用 `git show --no-patch` 打 `BUILD_DATE` 时间戳。