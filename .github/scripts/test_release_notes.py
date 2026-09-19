"""发布文案与历史收集规则的离线测试，不创建 Tag 或 Release。"""

import json
import subprocess
import unittest
from unittest.mock import patch

import release_notes as notes


REPO = "PhCraft-Team/PhilosHeads"
SHA = "a" * 40
PR = {"title": "fix(config): 修复重载后价格未生效", "body": "", "url": "https://github.com/" + REPO + "/pull/2", "label": "#2"}


class RenderTests(unittest.TestCase):
    def render(self, changes, previous=None):
        return notes.render_notes(changes, REPO, "1.1.1", "环境：Paper API 1.21.5、Java 21。收费功能需要 Vault 和经济插件。", "123", previous)

    def test_title_without_commit_prefix(self):
        text = self.render([PR])
        self.assertIn("修复重载后价格未生效", text)
        self.assertNotIn("fix(config):", text)

    def test_summary_overrides_title(self):
        text = self.render([{**PR, "body": "## 发布说明\n\n- 修改价格后使用 /goods reload 即可生效。\n- 旧配置继续可用。\n\n## 实际测试\n内部测试日志"}])
        self.assertIn("修改价格后使用 /goods reload 即可生效（", text)
        self.assertNotIn("修复重载后价格未生效", text)
        self.assertNotIn("内部测试日志", text)

    def test_empty_summary_uses_title(self):
        body = "## 发布说明\n<!-- 写一到三条变化。\n不要填模板。 -->\n\n## 实际测试\n测试通过"
        self.assertIn("修复重载后价格未生效", self.render([{**PR, "body": body}]))

    def test_english_summary_falls_back_without_losing_upgrade_warning(self):
        body = "## 发布说明\n- Improve reload behavior.\n## 升级影响\n- 配置文件：升级前备份 config.yml。"
        text = self.render([{**PR, "body": body}])
        self.assertIn("修复重载后价格未生效", text)
        self.assertNotIn("Improve reload behavior", text)
        self.assertIn("升级前备份 config.yml", text)

    def test_one_english_item_also_uses_chinese_title(self):
        body = "## 发布说明\n- 修复配置读取。\n- Improve output."
        text = self.render([{**PR, "body": body}])
        self.assertIn("修复重载后价格未生效", text)
        self.assertNotIn("Improve output", text)

    def test_chinese_summary_can_contain_technical_identifiers(self):
        body = "## 发布说明\n- 修复 config.yml 重载，兼容 Vault Economy。"
        self.assertIn("修复 config.yml 重载，兼容 Vault Economy", self.render([{**PR, "body": body}]))

    def test_none_impacts_do_not_create_heading(self):
        body = "## 升级影响\n- 配置文件：无\n- 权限：无变化。\n- 命令：不涉及\n- API：\n- 玩家或世界数据：无。"
        self.assertNotIn("## 升级提醒", self.render([{**PR, "body": body}]))

    def test_required_migration_is_kept(self):
        body = "## 升级影响\n- 配置文件：需将 old-price 改为 price。\n- 玩家或世界数据：升级前备份 data.yml；降级需恢复旧文件。"
        text = self.render([{**PR, "body": body}])
        self.assertIn("## 升级提醒", text)
        self.assertIn("需将 old-price 改为 price", text)
        self.assertIn("降级需恢复旧文件", text)

    def test_negative_but_substantive_instruction_is_kept(self):
        self.assertEqual(notes.upgrade_notes("## 升级影响\n- 升级及回滚步骤：无需迁移，但必须停服后替换文件。"), ["升级及回滚步骤：无需迁移，但必须停服后替换文件。"])

    def test_breaking_change_is_not_lost_with_prefix(self):
        text = self.render([{**PR, "title": "feat(api)!: 调整公开接口"}])
        self.assertIn("**不兼容变更：**", text)
        self.assertIn("升级前请检查", text)

    def test_breaking_change_preserves_upgrade_instructions(self):
        text = self.render([{**PR, "title": "feat!: 调整配置结构", "body": "## 升级影响\n- 配置文件：先备份 config.yml。"}])
        self.assertIn("先备份 config.yml", text)
        self.assertNotIn("升级前请检查", text)

    def test_no_repeated_build_metadata_or_template_prose(self):
        text = self.render([PR])
        for phrase in ["完整主线提交历史", "## 部署说明", "## 构建信息", "详细变更见提交页面", "本流程不会"]:
            self.assertNotIn(phrase, text)
        self.assertIn("PhilosHeads-1.1.1.jar", text)
        self.assertIn("停服并备份插件和数据", text)
        self.assertIn("Paper API 1.21.5、Java 21", text)

    def test_links_are_pinned_to_version(self):
        self.assertIn("/commits/v1.1.1", self.render([PR]))
        self.assertIn("/compare/v1.1.0...v1.1.1", self.render([PR], "v1.1.0"))

    def test_crlf_comments_and_same_level_boundary(self):
        self.assertEqual(notes.section("## 发布说明\r\n<!-- 隐藏 -->\r\n- 修复价格。\r\n## 测试\r\n不应进入", "发布说明"), "- 修复价格。")

    def test_shell_like_text_is_only_text(self):
        text = self.render([{**PR, "body": "## 发布说明\n- 示例 $(printf danger) 只是文字。"}])
        self.assertIn("$(printf danger)", text)

    def test_empty_initial_release_points_to_usage(self):
        text = self.render([])
        self.assertIn("首次发布", text)
        self.assertIn("/blob/v1.1.1/README.md", text)


class CollectionTests(unittest.TestCase):
    def collect(self, commits=(SHA,), tags="", pages=None, titles=None, parents=None, legacy=None, fail=False):
        calls = []
        def fake(*args):
            calls.append(args)
            if args[:2] == ("git", "tag"):
                return tags
            if args[:2] == ("git", "rev-list"):
                return "\n".join(commits)
            if args[:2] == ("git", "log"):
                if "--format=%P" in args:
                    return (parents or {}).get(args[-1], "")
                return (titles or {}).get(args[-1], "docs: 补充配置说明")
            if args[:2] == ("gh", "api"):
                if fail:
                    raise subprocess.CalledProcessError(1, args)
                return json.dumps((pages or {}).get(args[2].split("/")[-2], [[]]))
            raise AssertionError(args)
        with patch.object(notes, "command", side_effect=fake):
            result = notes.collect_changes(REPO, SHA, "1.1.1", legacy)
        return result, calls

    def test_first_release_uses_full_history(self):
        (changes, previous), calls = self.collect(commits=("old", SHA))
        self.assertIsNone(previous)
        self.assertEqual(len(changes), 2)
        self.assertIn(("git", "rev-list", "--reverse", "--first-parent", SHA), calls)

    def test_skips_only_repository_imports_without_previous_tag(self):
        (changes, _), _ = self.collect(commits=("root", SHA), titles={"root": "chore: prepare PhilosHeads repository"})
        self.assertEqual(len(changes), 1)
        self.assertEqual(changes[0]["title"], "docs: 补充配置说明")

    def test_previous_tag_ignores_current_and_nonversion_tags(self):
        (_, previous), calls = self.collect(tags="archive\nv1.1.1\nv1.1.0\nv1.0.0")
        self.assertEqual(previous, "v1.1.0")
        self.assertIn(("git", "rev-list", "--reverse", "--first-parent", "v1.1.0.." + SHA), calls)

    def test_actual_philosign_baseline_import_is_skipped(self):
        (changes, _), _ = self.collect(commits=("root", SHA), titles={"root": "chore: establish PhiloSign 1.1.0 baseline"})
        self.assertEqual(len(changes), 1)
        self.assertNotIn("其他改动", changes[0]["title"])

    def test_nonroot_baseline_change_is_not_hidden(self):
        (changes, _), _ = self.collect(titles={SHA: "chore: establish new baseline"}, parents={SHA: "parent-sha"})
        self.assertEqual(len(changes), 1)
        self.assertTrue(changes[0]["url"].endswith(SHA))

    def test_legacy_summary_and_migration_are_pinned_to_commit(self):
        body = "## 升级影响\n- 玩家数据：回退前恢复完整备份。"
        (changes, _), _ = self.collect(commits=("old", SHA), titles={"old": "feat: old feature"},
                                      legacy={"old": {"title": "新增签到日历", "body": body}})
        self.assertEqual(changes[0]["title"], "新增签到日历")
        self.assertEqual(changes[0]["body"], body)
        self.assertEqual(changes[1]["title"], "docs: 补充配置说明")

    def test_paginated_prs_are_deduplicated_and_other_branches_excluded(self):
        pr = {"number": 2, "title": PR["title"], "body": "## 发布说明\n- 修复价格。", "html_url": PR["url"], "merged_at": "2026-09-01", "merge_commit_sha": SHA, "base": {"ref": "main"}}
        other = {**pr, "number": 9, "base": {"ref": "dev"}}
        (changes, _), _ = self.collect(commits=("old", SHA), pages={"old": [[other], [pr]], SHA: [[pr]]})
        self.assertEqual(len(changes), 1)
        self.assertEqual(changes[0]["label"], "#2")
        self.assertIn("修复价格", changes[0]["body"])

    def test_api_failure_does_not_silently_drop_changes(self):
        with self.assertRaises(subprocess.CalledProcessError):
            self.collect(fail=True)

    def test_legacy_translation_also_covers_an_old_merged_pr(self):
        pr = {"number": 1, "title": "docs: rewrite administrator README", "body": "", "html_url": PR["url"], "merged_at": "2026-08-30", "merge_commit_sha": SHA, "base": {"ref": "main"}}
        (changes, _), _ = self.collect(pages={SHA: [[pr]]}, legacy={SHA: {"title": "整理服主安装说明"}})
        self.assertEqual(changes[0]["title"], "整理服主安装说明")
        self.assertEqual(changes[0]["label"], "#1")

    def test_nonchinese_legacy_commit_remains_linked(self):
        (changes, _), _ = self.collect(titles={SHA: "fix: update behavior"})
        self.assertEqual(changes[0]["title"], "其他改动")
        self.assertTrue(changes[0]["url"].endswith(SHA))


if __name__ == "__main__":
    unittest.main()
