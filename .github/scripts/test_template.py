"""仅验证模板配置和产物契约，不冒充插件功能测试。"""

import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from project_config import load_config
from check_artifact import check_artifact
from release_notes import render_notes


class TemplateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.config = json.loads(Path("plugin.json").read_text())

    def load(self, **changes):
        path = self.root / "plugin.json"
        path.write_text(json.dumps({**self.config, **changes}))
        return load_config(path)

    def jar(self, version=None, entry=True):
        path = self.root / f"{self.config['pluginName']}-{self.config['version']}.jar"
        with zipfile.ZipFile(path, "w") as jar:
            jar.writestr("plugin.yml", f"name: '{self.config['pluginName']}'\nversion: '{version or self.config['version']}'\nmain: com.phcraft.plugin.Main\n")
            if entry:
                jar.writestr("com/phcraft/plugin/Main.class", b"fixture")
        return path

    def test_default_configuration_builds_without_releasing(self):
        self.assertFalse(self.load(releaseEnabled=False)["releaseEnabled"])

    def test_placeholder_cannot_be_released(self):
        with self.assertRaises(ValueError):
            self.load(pluginName="ExamplePlugin", releaseEnabled=True)

    def test_bad_configuration_is_rejected(self):
        for changes in [{"version": "01.0.0"}, {"pluginName": "../bad"},
                        {"releaseEnabled": "false"}, {"javaVersion": True},
                        {"compatibility": "Paper\nrelease_enabled=true"}]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                self.load(**changes)

    def test_valid_jar(self):
        expected = self.jar()
        self.assertEqual(expected, check_artifact(self.config, self.config["version"], self.root))

    def test_mismatched_version(self):
        self.jar(version="9.9.9")
        with self.assertRaises(ValueError):
            check_artifact(self.config, self.config["version"], self.root)

    def test_missing_entry_class(self):
        self.jar(entry=False)
        with self.assertRaises(ValueError):
            check_artifact(self.config, self.config["version"], self.root)

    def test_extra_jar(self):
        self.jar()
        (self.root / "extra.jar").touch()
        with self.assertRaises(ValueError):
            check_artifact(self.config, self.config["version"], self.root)

    def test_download_name_comes_from_plugin_not_repository(self):
        text = render_notes([], "PhCraft-Team/my-plugin", "0.1.0", "测试环境", "1", plugin_name="MyPlugin")
        self.assertIn("/MyPlugin-0.1.0.jar", text)
        self.assertNotIn("/my-plugin-0.1.0.jar", text)


if __name__ == "__main__":
    unittest.main()
