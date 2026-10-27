# 更新 APK 构建引用

版本引用保存在仓库根目录 `build-config.json`，workflow 自动读取，不需要手改 YAML。
脚本依赖 Bash、Git 和 jq；不需要额外的 Shell 测试框架。

在 SFA-tubing 中运行：

```bash
# 默认读取相邻 sing-box 仓库的 feat/subtool-template 分支。
bash scripts/build_config.sh update v1.14.4

# 也可省略版本，从该内核分支最近的 v1.14.x tag 推导。
bash scripts/build_config.sh update

# 自定义内核仓库位置或引用。
bash scripts/build_config.sh update v1.14.4 --core-dir ../sing-box --core-ref feat/subtool-template
```

执行前，需要在内核仓库获取目标稳定 tag，并把自己的订阅解析补丁迁移到该版本。脚本只读取本地 Git 对象并更新锁定文件，不 fetch、不修改任何分支、不提交或推送。

脚本从内核稳定 tag 的 `clients/android` 子模块取得对应 SFA 基线，将定制内核分支解析为完整提交哈希，并检查该内核提交包含目标稳定 tag。锁定文件不使用移动分支名，因此验证与正式发布使用相同源码。

更新锁定文件后，将 SFA 定制提交 rebase 到 `sfa_base_ref`，并确认定制内核提交已推送到自己的内核远端。然后检查：

```bash
bash scripts/build_config.sh env
bash scripts/test_build_config.sh
git diff -- build-config.json
```

CI 在下载构建依赖前检查 SFA 基线和客户端版本；检出内核后，再检查内核祖先关系及 Android 子模块引用。Go 版本继续由客户端 `version.properties` 读取，客户端/内核展示版本由锁定 tag、提交数与哈希生成。

提交时应一起包含 SFA 基线迁移和锁定文件更新。保留现有发布行为：推送 `release/template` 会发布；手动运行默认只验证，只有在发布分支勾选 `publish` 才发布。
