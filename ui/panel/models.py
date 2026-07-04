from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class SparkJob:
    folder_name: str
    manifest_path: Path
    manifest: dict[str, Any]
    name: str
    namespace: str
    image: str


@dataclass(frozen=True)
class SparkApplicationState:
    exists: bool
    phase: str
    driver_pod: str | None
    ui_service: str | None

