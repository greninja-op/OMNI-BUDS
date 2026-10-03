"""Repository-level invariants for the device bridge, checked from the source text.

These are the constraints that a functional test cannot show: "standard library only", "never a
listening socket beyond loopback", "never `shell=True`", "no emoji", "no placeholder body standing in
for behaviour". They are checked by parsing the files with `ast`, so they hold for code that is never
executed by any test here as well as for code that is.

The scan is scoped to the modules this workstream owns, listed in `OWNED_MODULES`. `bridge/deploy.py`
belongs to a separate workstream and is deliberately not asserted on from here: a green run should
mean "these files satisfy these rules", not "somebody else's file happens to parse".
"""

from __future__ import annotations

import ast
import sys
import unittest
from pathlib import Path

_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

_ROOT = _HERE.parent
_LOCAL_TOP_LEVEL = {"bridge", "support", "verify_suite"}

OWNED_MODULES: tuple[str, ...] = (
    "bridge/__init__.py",
    "bridge/errors.py",
    "bridge/adb.py",
    "bridge/capture.py",
    "bridge/hierarchy.py",
    "bridge/input_actions.py",
    "bridge/envelope.py",
    "bridge/ws_server.py",
    "bridge/gateway.py",
    "verify_suite.py",
    "tests/support.py",
)

DEPENDENCY_MANIFEST_NAMES: tuple[str, ...] = (
    "requirements.txt",
    "requirements-dev.txt",
    "pyproject.toml",
    "setup.py",
    "setup.cfg",
    "Pipfile",
    "poetry.lock",
)


def _source(relative: str) -> str:
    path = _ROOT / relative
    if not path.is_file():
        raise AssertionError("expected module is missing: " + str(path))
    return path.read_text(encoding="utf-8")


def _tree(relative: str) -> ast.Module:
    return ast.parse(_source(relative), filename=relative)


def _top_level_imports(tree: ast.Module) -> set[str]:
    found: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                found.add(alias.name.split(".")[0])
        elif isinstance(node, ast.ImportFrom):
            if node.level:  # a relative import inside the package is local by definition
                continue
            if node.module:
                found.add(node.module.split(".")[0])
    return found


class StandardLibraryOnlyTest(unittest.TestCase):
    def test_every_import_is_standard_library_or_local(self) -> None:
        offenders: dict[str, set[str]] = {}
        for relative in OWNED_MODULES:
            foreign = _top_level_imports(_tree(relative)) - _LOCAL_TOP_LEVEL - set(sys.stdlib_module_names)
            if foreign:
                offenders[relative] = foreign
        self.assertEqual(offenders, {}, "these imports are not in the standard library")

    def test_no_dependency_manifest_appears_in_the_bridge_tree(self) -> None:
        found = [path.name for path in _ROOT.rglob("*") if path.name in DEPENDENCY_MANIFEST_NAMES]
        self.assertEqual(found, [], "the bridge declares no third-party dependencies")

    def test_the_bridge_does_not_depend_on_a_websocket_library(self) -> None:
        for relative in OWNED_MODULES:
            text = _source(relative)
            for forbidden in ("import websockets", "from websockets", "import fastapi", "from fastapi"):
                self.assertNotIn(forbidden, text, relative)
            self.assertNotIn("import lxml", text, relative)
            self.assertNotIn("from PIL", text, relative)


class SubprocessDisciplineTest(unittest.TestCase):
    def test_no_subprocess_call_uses_a_shell(self) -> None:
        offenders: list[str] = []
        for relative in OWNED_MODULES:
            for node in ast.walk(_tree(relative)):
                if not isinstance(node, ast.Call):
                    continue
                callee = node.func
                name = getattr(callee, "attr", None) or getattr(callee, "id", "")
                if name not in {"run", "Popen", "call", "check_output", "check_call"}:
                    continue
                for keyword in node.keywords:
                    if keyword.arg == "shell" and getattr(keyword.value, "value", True) is not False:
                        offenders.append(relative + " line " + str(node.lineno) + " enables a shell")
                if isinstance(name, str) and name in {"call", "run", "Popen", "check_output", "check_call"}:
                    first = node.args[0] if node.args else None
                    if isinstance(first, ast.Constant) and isinstance(first.value, str):
                        offenders.append(relative + " line " + str(node.lineno) + " passes a string command")
        self.assertEqual(offenders, [], "every subprocess call must be an argv list with shell=False")

    def test_no_shell_module_call_is_enabled(self) -> None:
        """Structural, not textual: the prose is allowed to name the thing the code forbids.

        `bridge/adb.py` documents why there is no `sh -c`, so a substring scan would flag its own
        explanation. What matters is that no call site passes `shell=True` or a string command.
        """
        for relative in OWNED_MODULES:
            for node in ast.walk(_tree(relative)):
                if not isinstance(node, ast.Call):
                    continue
                name = getattr(node.func, "attr", "") or getattr(node.func, "id", "")
                if name not in {"run", "Popen", "call", "check_output", "check_call"}:
                    continue
                for keyword in node.keywords:
                    if keyword.arg == "shell":
                        self.assertIs(
                            getattr(keyword.value, "value", "non-literal"),
                            False,
                            relative + " line " + str(node.lineno) + " enables a local shell",
                        )

    def test_command_strings_are_built_in_one_module_only(self) -> None:
        """`adb` argv strings appear nowhere in the package outside `bridge/adb.py`.

        Scoped to `bridge/`: the rule is about production code that can reach the phone. Test
        scaffolding has to name command fragments in order to script them, and `verify_suite.py`
        drives those scaffolds.
        """
        for relative in OWNED_MODULES:
            if not relative.startswith("bridge/") or relative == "bridge/adb.py":
                continue
            tree = _tree(relative)
            for node in ast.walk(tree):
                if isinstance(node, ast.Constant) and isinstance(node.value, str):
                    self.assertNotIn(
                        node.value,
                        {"exec-out", "uiautomator", "screencap", "getprop", "dumpsys"},
                        relative + " must not build an ADB command string",
                    )


class LoopbackBindingTest(unittest.TestCase):
    def test_the_only_bind_call_is_to_the_loopback_constant(self) -> None:
        tree = _tree("bridge/ws_server.py")
        binds = [
            node
            for node in ast.walk(tree)
            if isinstance(node, ast.Call) and getattr(node.func, "attr", "") == "bind"
        ]
        self.assertEqual(len(binds), 1)
        rendered = ast.unparse(binds[0])
        self.assertEqual(rendered, "listener.bind((LOOPBACK_HOST, self._requested_port))")

    def test_no_bridge_module_names_a_wildcard_bind_address(self) -> None:
        """Scoped to `bridge/`: tests and the self-check name `0.0.0.0` precisely to refuse it.

        The production rule is that no address other than `LOOPBACK_HOST` is ever passed to `bind`,
        which `test_the_only_bind_call_is_to_the_loopback_constant` proves structurally.
        """
        for relative in OWNED_MODULES:
            if not relative.startswith("bridge/"):
                continue
            for node in ast.walk(_tree(relative)):
                if isinstance(node, ast.Constant) and isinstance(node.value, str):
                    if node.value == "0.0.0.0":
                        self.assertIn(
                            relative,
                            ("bridge/ws_server.py",),
                            relative + " names a wildcard bind address",
                        )
                        # Whether it could ever reach a socket is answered by the structural bind
                        # test above; this one only keeps the string out of the other modules.

    def test_create_connection_is_guarded_by_the_loopback_check(self) -> None:
        source = _source("bridge/ws_server.py")
        self.assertIn("if host != LOOPBACK_HOST", source)
        self.assertIn("refusing to open a device-bridge client socket", source)


class NoPlaceholderBehaviourTest(unittest.TestCase):
    def test_no_function_or_class_body_is_only_a_pass_or_ellipsis(self) -> None:
        offenders: list[str] = []
        for relative in OWNED_MODULES:
            for node in ast.walk(_tree(relative)):
                if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
                    if len(node.body) == 1 and isinstance(node.body[0], (ast.Pass, ast.Ellipsis)):
                        offenders.append(relative + " line " + str(node.lineno) + ": " + node.name)
        self.assertEqual(offenders, [], "a placeholder body standing in for behaviour is a defect")

    def test_no_stub_markers_in_the_shipped_code(self) -> None:
        for relative in OWNED_MODULES:
            source = _source(relative)
            for marker in ("TODO(", "FIXME", "XXX", "NotImplementedError"):
                self.assertNotIn(marker, source, relative)


class OutputHygieneTest(unittest.TestCase):
    def test_every_owned_file_is_pure_ascii(self) -> None:
        """No emoji anywhere, per the project's zero-emoji standard.

        Checking ASCII is stronger than checking for emoji ranges: it also rules out the lookalike
        punctuation that ends up pasted into protocol strings and log lines.
        """
        offenders: list[str] = []
        for relative in OWNED_MODULES:
            raw = (_ROOT / relative).read_bytes()
            for offset, byte in enumerate(raw):
                if byte > 127:
                    offenders.append(relative + " byte offset " + str(offset))
                    break
        self.assertEqual(offenders, [], "non-ASCII bytes found")

    def test_readme_is_ascii_too(self) -> None:
        readme = _ROOT / "README.md"
        if not readme.is_file():
            self.skipTest("README.md is written after these modules; verify_suite covers it too")
        self.assertEqual(
            [offset for offset, byte in enumerate(readme.read_bytes()) if byte > 127],
            [],
        )

    def test_no_module_writes_a_frame_to_disk(self) -> None:
        """Screen bytes may only live in memory: no file API in the capture path."""
        for relative in ("bridge/capture.py", "bridge/hierarchy.py", "bridge/envelope.py"):
            tree = _tree(relative)
            names = {
                getattr(node, "attr", "")
                for node in ast.walk(tree)
                if isinstance(node, ast.Attribute)
            } | {
                getattr(node, "id", "")
                for node in ast.walk(tree)
                if isinstance(node, ast.Name)
            }
            for forbidden in ("open", "mkdir", "write_bytes", "NamedTemporaryFile", "mkstemp"):
                self.assertNotIn(forbidden, names, relative + " must not touch the filesystem")


if __name__ == "__main__":
    unittest.main()
