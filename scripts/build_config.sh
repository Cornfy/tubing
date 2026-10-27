#!/usr/bin/env bash
# 从本地内核仓库更新构建锁定文件，或输出经过检查的 Actions 环境变量。
set -euo pipefail

ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
LOCK="$ROOT/build-config.json"
TEMP_LOCK=''
trap '[[ -z "$TEMP_LOCK" ]] || rm -f -- "$TEMP_LOCK"' EXIT

fail() {
    printf '构建配置错误：%s\n' "$*" >&2
    exit 1
}

usage() {
    cat <<'HELP'
用法：
  scripts/build_config.sh update [v1.14.x] [--core-dir 路径] [--core-ref 分支或提交]
  scripts/build_config.sh env

update 默认读取相邻 sing-box 仓库的 feat/subtool-template 分支。
省略版本时，从该分支最近的 v1.14.x tag 推导。
env 检查 SFA 基线与版本，成功后输出 Actions 环境变量。
脚本不会 fetch、切换分支、提交或推送。
HELP
}

validate() {
    jq -e '
        type == "object" and
        (keys == ["core_base_tag", "core_ref", "sfa_base_ref"]) and
        ([.[] | type == "string"] | all) and
        (.core_base_tag | test("^v1\\.14\\.[0-9]+$")) and
        (.core_ref | test("^[0-9a-f]{40}$")) and
        (.sfa_base_ref | test("^[0-9a-f]{40}$"))
    ' "$1" >/dev/null || fail '锁定文件必须包含 v1.14.x 稳定 tag 和两个完整提交哈希'
}

update() {
    local tag='' core_dir="$ROOT/../sing-box" core_ref='feat/subtool-template'
    local revision mode object_type android_ref entry_path
    while (($#)); do
        case "$1" in
            --core-dir|--core-ref)
                (($# >= 2)) || fail "$1 缺少参数"
                [[ -n "$2" && "$2" != --* ]] || fail "$1 缺少参数"
                if [[ "$1" == --core-dir ]]; then core_dir=$2; else core_ref=$2; fi
                shift 2
                ;;
            -*) fail "未知选项：$1" ;;
            *)
                [[ -z "$tag" ]] || fail '只能指定一个目标 tag'
                tag=$1
                shift
                ;;
        esac
    done
    revision=$(git -C "$core_dir" rev-parse --verify --end-of-options "${core_ref}^{commit}") || fail '无法解析内核分支或提交'
    if [[ -z "$tag" ]]; then
        tag=$(git -C "$core_dir" describe --tags --match 'v1.14.*' --abbrev=0 "$revision") || fail '请先在内核仓库获取稳定 tag'
    fi
    [[ "$tag" =~ ^v1\.14\.[0-9]+$ ]] || fail '目标必须为 v1.14.x 稳定 tag'
    # 显式使用 refs/tags，避免同名分支被误认为上游稳定 tag。
    git -C "$core_dir" merge-base --is-ancestor "refs/tags/$tag" "$revision" || fail '内核提交未包含目标稳定 tag'
    read -r mode object_type android_ref entry_path < <(git -C "$core_dir" ls-tree "refs/tags/$tag" clients/android)
    [[ "$mode" == 160000 && "$object_type" == commit && "$entry_path" == clients/android ]] || fail '稳定 tag 中缺少 Android 子模块引用'

    # 校验成功后原子替换，失败时保留原来的锁定文件。
    TEMP_LOCK=$(mktemp "$ROOT/.build-config.XXXXXX")
    jq -n --arg tag "$tag" --arg core "$revision" --arg android "$android_ref" \
        '{core_base_tag: $tag, core_ref: $core, sfa_base_ref: $android}' > "$TEMP_LOCK"
    validate "$TEMP_LOCK"
    mv -f -- "$TEMP_LOCK" "$LOCK"
    TEMP_LOCK=''
    printf '已更新 build-config.json：%s，内核 %.9s，SFA %.9s\n' "$tag" "$revision" "$android_ref"
    printf '请确认定制内核提交已推送，并将 SFA 定制提交迁移至上述 SFA 基线。\n'
}

environment() {
    validate "$LOCK"
    local tag android_ref version
    tag=$(jq -r '.core_base_tag' "$LOCK")
    android_ref=$(jq -r '.sfa_base_ref' "$LOCK")
    git -C "$ROOT" merge-base --is-ancestor "$android_ref" HEAD || \
        fail '当前 SFA 分支尚未基于锁定的稳定提交，请先完成 rebase，并确保该提交已获取'
    version=$(sed -n 's/^VERSION_NAME=//p' "$ROOT/version.properties" | tr -d '\r')
    [[ "$version" == "${tag#v}" ]] || fail "客户端版本为 $version，锁定版本为 ${tag#v}；请先迁移到对应 SFA 稳定基线"
    # 完成全部校验后才输出；不 source 文件，不执行锁定文件内容。
    jq -r '"CORE_BASE_TAG=\(.core_base_tag)", "CORE_REF=\(.core_ref)", "SFA_BASE_REF=\(.sfa_base_ref)"' "$LOCK"
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
esac
command -v git >/dev/null || fail '未找到 git'
command -v jq >/dev/null || fail '未找到 jq'
case "${1:-}" in
    update) shift; update "$@" ;;
    env) (($# == 1)) || fail 'env 不接受额外参数'; environment ;;
    *) usage >&2; exit 1 ;;
esac
