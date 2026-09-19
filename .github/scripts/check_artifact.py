"""检查实际 JAR 的名称、版本、插件名和入口类。"""

import os
import re
import sys
import zipfile
from pathlib import Path
from project_config import load_config


def check_artifact(config, gradle_version, directory=Path("build/libs")):
    if gradle_version != config["version"]:
        raise ValueError("Gradle 版本与 plugin.json 不一致")
    expected = directory / f"{config['pluginName']}-{config['version']}.jar"
    jars = list(directory.glob("*.jar"))
    if jars != [expected]:
        raise ValueError(f"必须生成唯一主 JAR：{expected}，实际为 {jars}")
    with zipfile.ZipFile(expected) as jar:
        descriptor = jar.read("plugin.yml").decode("utf-8")
        fields = {}
        for key in ("name", "version", "main"):
            match = re.search(rf"^{key}:\s*([^\r\n]+)", descriptor, re.M)
            if not match:
                raise ValueError(f"JAR 的 plugin.yml 缺少 {key}")
            fields[key] = match[1].strip().strip("\"'")
        if fields["name"] != config["pluginName"] or fields["version"] != config["version"]:
            raise ValueError("JAR 内嵌插件名或版本与 plugin.json 不一致")
        if fields["main"].replace(".", "/") + ".class" not in jar.namelist():
            raise ValueError("JAR 中不存在 plugin.yml 声明的入口类")
    return expected


if __name__ == "__main__":
    config = load_config()
    path = check_artifact(config, sys.argv[1])
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"version={config['version']}\npath={path}\nname={path.name}\n")
