#!/bin/sh
# Код оценки, форматирования и сбора ленты берётся из личного парсера как есть:
# поведение модели и вид уведомлений одинаковы в обоих ботах, а улучшение в
# парсере доезжает сюда одной командой. Сам парсер приватный — в нём личные
# cookies и ключи, — поэтому сюда копируются только эти модули.
set -e
src="${1:-$HOME/IdeaProjects/freelance_parser/app}"
for f in __init__.py llm.py prefilter.py insights.py notifier.py; do
    cp "$src/$f" fp/
done
cp "$src/feed_export.py" collector/
cp "$src/sources/base.py" "$src/sources/kwork.py" collector/sources/
echo "fp/ и collector/ обновлены из $src"
