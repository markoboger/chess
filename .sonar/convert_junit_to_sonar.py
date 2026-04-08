#!/usr/bin/env python3

from __future__ import annotations

import difflib
import os
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
OUTPUT = ROOT / ".sonar" / "test-exec.xml"
JUNIT_GLOB = "target/test-reports/TEST-*.xml"

TEST_ROOTS = [
    ROOT / "src" / "test" / "scala",
    ROOT / "core" / "src" / "test" / "scala",
    ROOT / "app" / "src" / "test" / "scala",
    ROOT / "data" / "src" / "test" / "scala",
    ROOT / "realtime" / "src" / "test" / "scala",
    ROOT / "seeder" / "src" / "test" / "scala",
    ROOT / "benchmark" / "src" / "test" / "scala",
    ROOT / "frontend" / "src",
]


def build_test_index() -> dict[str, list[Path]]:
    index: dict[str, list[Path]] = {}
    for test_root in TEST_ROOTS:
      if not test_root.exists():
          continue
      for path in test_root.rglob("*"):
          if path.is_file() and path.suffix in {".scala", ".ts", ".tsx", ".js", ".jsx"}:
              index.setdefault(path.stem, []).append(path)
    return index


def resolve_test_file(classname: str, suite_name: str, test_index: dict[str, list[Path]]) -> str:
    primary_symbol = classname or suite_name or "unknown"
    candidates = []
    for symbol in (classname, suite_name):
        if not symbol:
            continue
        stem = symbol.split(".")[-1]
        candidates.extend(test_index.get(stem, []))

    if not candidates:
        package_parts = primary_symbol.split(".")[:-1]
        stem = primary_symbol.split(".")[-1]
        for test_root in TEST_ROOTS:
            package_dir = test_root.joinpath(*package_parts)
            if not package_dir.exists():
                continue
            nearby = [p for p in package_dir.glob("*Spec.scala")]
            if nearby:
                best = max(
                    nearby,
                    key=lambda p: difflib.SequenceMatcher(None, p.stem, stem).ratio(),
                )
                return os.path.relpath(best, ROOT)

        return f"unresolved/{primary_symbol.replace('.', '/')}.scala"

    package_hint = primary_symbol.replace(".", os.sep)
    for candidate in candidates:
        normalized = str(candidate)
        if normalized.endswith(f"{package_hint}.scala") or normalized.endswith(f"{package_hint}.ts"):
            return os.path.relpath(candidate, ROOT)

    return os.path.relpath(candidates[0], ROOT)


def main() -> None:
    test_index = build_test_index()
    test_exec = ET.Element("testExecutions", version="1")

    junit_reports = sorted(ROOT.glob(f"**/{JUNIT_GLOB}"))

    for report in junit_reports:
        suite = ET.parse(report).getroot()
        suite_name = suite.attrib.get("name", "")
        cases_by_file: dict[str, list[ET.Element]] = {}

        for case in suite.findall("testcase"):
            classname = case.attrib.get("classname", suite_name)
            file_path = resolve_test_file(classname, suite_name, test_index)
            cases_by_file.setdefault(file_path, []).append(case)

        for file_path, cases in cases_by_file.items():
            file_node = ET.SubElement(test_exec, "file", path=file_path)
            for case in cases:
                duration_ms = int(float(case.attrib.get("time", "0")) * 1000)
                test_case = ET.SubElement(
                    file_node,
                    "testCase",
                    name=case.attrib.get("name", "unnamed"),
                    duration=str(duration_ms),
                )

                if case.find("failure") is not None:
                    failure = case.find("failure")
                    ET.SubElement(
                        test_case,
                        "failure",
                        message=failure.attrib.get("message", "failure"),
                    ).text = failure.text or ""
                elif case.find("error") is not None:
                    error = case.find("error")
                    ET.SubElement(
                        test_case,
                        "error",
                        message=error.attrib.get("message", "error"),
                    ).text = error.text or ""
                elif case.find("skipped") is not None:
                    ET.SubElement(test_case, "skipped")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(test_exec).write(OUTPUT, encoding="utf-8", xml_declaration=True)
    print(f"Wrote Sonar test execution report to {OUTPUT}")


if __name__ == "__main__":
    main()
