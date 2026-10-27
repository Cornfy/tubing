#!/usr/bin/env bash
# 用独立目录和 Git 替身检查失败边界，不访问远端、不创建提交。
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
TEST_DIR=$(mktemp -d "$ROOT/.build-config-test.XXXXXX")
trap 'rm -rf -- "$TEST_DIR"' EXIT
mkdir -p "$TEST_DIR/client/scripts" "$TEST_DIR/bin"
cp "$ROOT/scripts/build_config.sh" "$TEST_DIR/client/scripts/"
cat > "$TEST_DIR/bin/git" <<'GIT'
#!/usr/bin/env bash
set -euo pipefail
[[ "$1" == -C ]] && shift 2
case "$1" in
    rev-parse) printf '%040d\n' 1 ;;
    describe) printf 'v1.14.4\n' ;;
    merge-base) [[ "${FAIL_ANCESTRY:-0}" == 0 ]] ;;
    ls-tree) printf '160000 commit %040d\tclients/android\n' 2 ;;
    *) exit 1 ;;
esac
GIT
chmod +x "$TEST_DIR/bin/git"
export PATH="$TEST_DIR/bin:$PATH"
SCRIPT="$TEST_DIR/client/scripts/build_config.sh"
LOCK="$TEST_DIR/client/build-config.json"
COUNT=0
ok() { COUNT=$((COUNT + 1)); printf '通过：%s\n' "$1"; }
expect_failure() {
    if "$@" > "$TEST_DIR/output" 2> "$TEST_DIR/error"; then
        printf '预期失败但命令成功：%s\n' "$*" >&2
        exit 1
    fi
}

bash "$SCRIPT" update --core-dir "$TEST_DIR/core" >/dev/null
jq -e '.core_base_tag == "v1.14.4" and .core_ref == "0000000000000000000000000000000000000001" and .sfa_base_ref == "0000000000000000000000000000000000000002"' "$LOCK" >/dev/null
ok '从内核引用和子模块生成锁定文件'
cp "$LOCK" "$TEST_DIR/good.json"
expect_failure bash "$SCRIPT" update v1.15.0-alpha.1
cmp "$LOCK" "$TEST_DIR/good.json"
ok '拒绝 alpha 版本并保留旧文件'
expect_failure env FAIL_ANCESTRY=1 bash "$SCRIPT" update v1.14.4
cmp "$LOCK" "$TEST_DIR/good.json"
ok '内核祖先校验失败时保留旧文件'
expect_failure bash "$SCRIPT" update --core-dir
ok '拒绝缺失选项参数'
printf 'VERSION_NAME=1.14.4\r\n' > "$TEST_DIR/client/version.properties"
bash "$SCRIPT" env > "$TEST_DIR/output"
[[ $(wc -l < "$TEST_DIR/output") == 3 ]]
grep -qx 'CORE_BASE_TAG=v1.14.4' "$TEST_DIR/output"
ok '客户端基线匹配时输出三个变量，兼容 CRLF'
printf 'VERSION_NAME=1.14.3\n' > "$TEST_DIR/client/version.properties"
expect_failure bash "$SCRIPT" env
[[ ! -s "$TEST_DIR/output" ]]
ok '客户端版本不匹配时不输出变量'
printf 'VERSION_NAME=1.14.4\n' > "$TEST_DIR/client/version.properties"
expect_failure env FAIL_ANCESTRY=1 bash "$SCRIPT" env
[[ ! -s "$TEST_DIR/output" ]]
ok '客户端基线错误时不输出变量'
jq '.core_ref = "main"' "$TEST_DIR/good.json" > "$LOCK"
expect_failure bash "$SCRIPT" env
ok '拒绝未锁定的分支名'
printf '{invalid\n' > "$LOCK"
expect_failure bash "$SCRIPT" env
ok '拒绝损坏的 JSON'
printf '共通过 %s 项离线检查。\n' "$COUNT"
