"""从已合并 PR 生成中文发布说明；不调用模型，不改动 Release。"""

import json
import os
import re
import subprocess
from pathlib import Path


PREFIX = re.compile(r"^(feat|fix|docs|refactor|test|build|ci|chore|perf|revert)(?:\([^\n)]+\))?(!)?:\s*")
VERSION_TAG = re.compile(r"^v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$")
IMPORT_TITLE = re.compile(r"^(?:chore: (?:prepare .+ repository|establish .+ baseline)|initial commit)$", re.I)
EMPTY_IMPACT = {"", "无", "无变化", "不涉及", "无需"}


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def section(body, heading):
    body = re.sub(r"<!--.*?-->", "", body or "", flags=re.S)
    lines, active = [], False
    for line in body.splitlines():
        if line.strip() == "## " + heading:
            active = True
            continue
        if active and re.match(r"^#{1,2}\s", line):
            break
        if active:
            lines.append(line)
    return "\n".join(lines).strip()


def items(text):
    result = []
    for line in text.splitlines():
        line = re.sub(r"^\s*(?:[-*+]\s+|\d+[.)]\s+)", "", line).strip()
        if line:
            result.append(line)
    return result


def clean_title(title):
    return re.sub(r"\s+\(#\d+\)$", "", PREFIX.sub("", title)).strip()


def upgrade_notes(body):
    result = []
    for line in items(section(body, "升级影响")):
        value = re.split(r"[：:]", line, maxsplit=1)[-1].strip().rstrip("。.").strip()
        if value not in EMPTY_IMPACT:
            result.append(line)
    return result


def collect_changes(repository, sha, version, legacy_notes=None, base_branch="main"):
    tags = command("git", "tag", "--merged", sha, "--sort=-version:refname").splitlines()
    previous = next((tag for tag in tags if VERSION_TAG.fullmatch(tag) and tag != "v" + version), None)
    revision = previous + ".." + sha if previous else sha
    commits = command("git", "rev-list", "--reverse", "--first-parent", revision).splitlines()
    changes, seen = [], set()
    for commit in commits:
        candidates = json.loads(command("gh", "api", f"repos/{repository}/commits/{commit}/pulls", "--paginate", "--slurp"))
        prs = [pr for page in candidates for pr in page
               if pr.get("merged_at") and pr.get("base", {}).get("ref") == base_branch]
        pr = next((pr for pr in prs if pr.get("merge_commit_sha") == commit), prs[0] if prs else None)
        legacy = (legacy_notes or {}).get(commit, {})
        if pr:
            if pr["number"] in seen:
                continue
            seen.add(pr["number"])
            changes.append({"title": legacy.get("title", pr["title"]),
                            "body": legacy.get("body", pr.get("body") or ""),
                            "url": pr["html_url"], "label": "#" + str(pr["number"])})
        else:
            title = command("git", "log", "-1", "--format=%s", commit)
            if (not previous and IMPORT_TITLE.fullmatch(title)
                    and not command("git", "log", "-1", "--format=%P", commit)):
                continue
            title = legacy.get("title", title)
            if not re.search(r"[\u4e00-\u9fff]", title):
                title = "其他改动"
            changes.append({"title": title, "body": legacy.get("body", ""),
                            "url": f"https://github.com/{repository}/commit/{commit}", "label": "详情"})
    return changes, previous


def render_notes(changes, repository, version, environment, run_id, previous=None, plugin_name=None):
    lines, upgrades = ["## 更新", ""], []
    for change in changes:
        title = clean_title(change["title"])
        match = PREFIX.match(change["title"])
        breaking = bool(match and match.group(2))
        link = f"[{change['label']}]({change['url']})"
        summary = items(section(change["body"], "发布说明"))
        if not summary or not all(re.search(r"[\u4e00-\u9fff]", item) for item in summary):
            summary = [title]
        for item in summary:
            warning = "**不兼容变更：**" if breaking else ""
            lines.append(f"- {warning}{item.rstrip('。')}（{link}）。")
        impacts = upgrade_notes(change["body"])
        upgrades.extend(f"- {impact.rstrip('。')}（{link}）。" for impact in impacts)
        if breaking and not impacts:
            upgrades.append(f"- {title}涉及不兼容变更，升级前请检查 {link}。")
    if not changes:
        lines.append("首次发布。功能与安装方法见 [使用说明](https://github.com/" + repository + "/blob/v" + version + "/README.md)。" if not previous else "本次没有可列出的新增改动。")
    if upgrades:
        lines.extend(["", "## 升级提醒", "", *upgrades])
    jar = (plugin_name or repository.rsplit("/", 1)[-1]) + "-" + version + ".jar"
    url = "https://github.com/" + repository
    history = url + "/compare/" + previous + "...v" + version if previous else url + "/commits/v" + version
    lines.extend(["", environment, "",
                  f"下载：[{jar}]({url}/releases/download/v{version}/{jar})。停服并备份插件和数据后替换旧 JAR。", "",
                  f"[全部改动]({history}) · [构建记录]({url}/actions/runs/{run_id})", ""])
    return "\n".join(lines)


def main():
    repository, version = os.environ["GITHUB_REPOSITORY"], os.environ["VERSION"]
    legacy = json.loads(os.environ.get("RELEASE_LEGACY_NOTES", "{}"))
    changes, previous = collect_changes(repository, os.environ["GITHUB_SHA"], version, legacy, os.environ.get("DEFAULT_BRANCH", "main"))
    notes = render_notes(changes, repository, version, os.environ["RELEASE_ENVIRONMENT"],
                         os.environ["GITHUB_RUN_ID"], previous, os.environ.get("PLUGIN_NAME"))
    destination = Path(os.environ["RUNNER_TEMP"]) / "release-notes.md"
    destination.write_text(notes, encoding="utf-8")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"path={destination}\n")


if __name__ == "__main__":
    main()
