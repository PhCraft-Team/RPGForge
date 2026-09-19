"""校验插件声明，为构建和发布提供同一份参数。"""

import json
import os
import re
from pathlib import Path

VERSION = re.compile(r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)")


def load_config(path="plugin.json"):
    config = json.loads(Path(path).read_text(encoding="utf-8"))
    patterns = {
        "pluginName": r"[A-Za-z][A-Za-z0-9_]{1,63}",
        "version": VERSION.pattern,
        "group": r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+",
        "gradleVersion": r"[1-9]\d*\.\d+(?:\.\d+)?",
        "paperVersion": r"[A-Za-z0-9._+-]+",
        "apiVersion": r"\d+\.\d+(?:\.\d+)?",
    }
    for key, pattern in patterns.items():
        value = config.get(key)
        if not isinstance(value, str) or not re.fullmatch(pattern, value):
            raise ValueError(f"plugin.json 的 {key} 格式无效")
    if type(config.get("javaVersion")) is not int or config["javaVersion"] < 21:
        raise ValueError("javaVersion 必须为不小于 21 的整数")
    if type(config.get("releaseEnabled")) is not bool:
        raise ValueError("releaseEnabled 必须为 true 或 false")
    environment = config.get("compatibility")
    if not isinstance(environment, str) or not environment.strip() or '\n' in environment or '\r' in environment:
        raise ValueError("compatibility 必须为单行兼容性说明")
    if config["releaseEnabled"] and config["pluginName"] == "ExamplePlugin":
        raise ValueError("启用发布前必须修改示例插件名 ExamplePlugin")
    return config


def main():
    c = load_config()
    outputs = {"plugin_name": c["pluginName"], "java_version": c["javaVersion"],
               "gradle_version": c["gradleVersion"], "environment": c["compatibility"],
               "release_enabled": str(c["releaseEnabled"]).lower()}
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as file:
        for key, value in outputs.items():
            file.write(f"{key}={value}\n")


if __name__ == "__main__":
    main()
