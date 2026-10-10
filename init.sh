#!/usr/bin/env bash
# init.sh —— 从模板初始化新项目：改 groupId / artifactId / 模块前缀 / 基础包
# 用法：在仓库根目录执行 ./init.sh（Windows 请用 Git Bash 或 WSL）
set -euo pipefail

OLD_GROUP="io.github.gusforge"
OLD_PKG="io.github.gusforge.foundry"
OLD_ARTIFACT="foundry-template"
OLD_SVC="foundry"

read -rp "groupId (e.g. io.github.gusforge): " GROUP_ID
read -rp "root artifactId (e.g. order-parent): " ARTIFACT
read -rp "module prefix (e.g. order): " SVC
read -rp "base package (e.g. io.github.gusforge.order): " PKG

if [[ "$PKG" == "$OLD_PKG" && "$SVC" == "$OLD_SVC" ]]; then
  echo "nothing to change" >&2
  exit 1
fi

# 根 artifactId 不能与任何模块名相同，否则 Maven reactor 报 duplicated
for m in dao service web start; do
  if [[ "$ARTIFACT" == "$SVC-$m" ]]; then
    echo "root artifactId '$ARTIFACT' collides with module '$SVC-$m'; pick another (e.g. $SVC-parent)" >&2
    exit 1
  fi
done

OLD_PKG_PATH="${OLD_PKG//.//}"
NEW_PKG_PATH="${PKG//.//}"

# 1. 移动包目录。
#    只删旧叶子目录，再清理空的父目录。不要 rm -rf 包路径的第一段（io/）：
#    新包路径通常也在 io/ 底下，会把刚复制好的文件一起删掉。
if [[ "$PKG" != "$OLD_PKG" ]]; then
  for module in */src/main/java */src/test/java; do
    [[ -d "$module/$OLD_PKG_PATH" ]] || continue
    mkdir -p "$module/$NEW_PKG_PATH"
    cp -r "$module/$OLD_PKG_PATH/." "$module/$NEW_PKG_PATH/"
    rm -rf "$module/$OLD_PKG_PATH"
    find "$module" -mindepth 1 -type d -empty -delete
  done
fi

# 2. 替换文本。顺序重要：长的、具体的先替换，否则会被短的吃掉。
#    排除 .git、target、docs（设计文档保持原样）和本脚本。
find . -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' \
       -o -name '*.sql' -o -name '*.md' -o -name 'Dockerfile' \) \
  -not -path './.git/*' -not -path '*/target/*' -not -path './docs/*' -print0 \
| xargs -0 sed -i.bak \
    -e "s|$OLD_PKG|$PKG|g" \
    -e "s|$OLD_ARTIFACT|$ARTIFACT|g" \
    -e "s|$OLD_SVC-|$SVC-|g" \
    -e "s|$OLD_GROUP|$GROUP_ID|g"

find . -name '*.bak' -delete

# 3. 重命名模块目录
for d in ${OLD_SVC}-*; do
  [[ -d "$d" ]] && mv "$d" "${d/${OLD_SVC}-/${SVC}-}"
done

echo "done. next: mvn -B verify"
