#!/usr/bin/env python3
"""Import hydrus.pl/modele.php into a normalized SQLite DB + thumbnail images."""

from __future__ import annotations

import argparse
import concurrent.futures
import re
import sqlite3
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin

SOURCE_URL = "http://hydrus.pl/modele.php"
BASE_URL = "http://hydrus.pl/"
USER_AGENT = "BigFredHydrusImport/1.0 (+https://github.com/dcc-bigfred)"

# Epoch subdivisions used when expanding ranges like "III c - IV a".
EPOCH_LETTERS = ("A", "B", "C")

DATE_FULL_RE = re.compile(r"^(\d{1,2})\.(\d{1,2})\.(\d{4})$")
DATE_YEAR_RE = re.compile(r"^(\d{4})$")
STAN_YEAR_RE = re.compile(r"stan:\s*(\d{4})", re.IGNORECASE)
YEAR_ANY_RE = re.compile(r"(19|20)\d{2}")
# Roman numerals typically used for model epochs (I…XII is plenty).
EPOCH_TOKEN_RE = re.compile(
    r"^(m{0,3})(cm|cd|d?c{0,3})(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})\s*([abc])?$",
    re.IGNORECASE,
)


@dataclass
class RawRow:
    manufacturer_raw: str
    catalog_number: str
    thumb_url: str | None
    scale: str
    release_raw: str
    vehicle_kind: str
    type: str
    vehicle_number: str
    carrier: str
    assignment: str
    revision_raw: str
    epoch_raw: str
    livery: str
    order_index: int


@dataclass
class ParsedRow:
    manufacturer: str
    catalog_number: str
    thumb_url: str | None
    scale: str
    release_date: str | None
    release_date_precision: str | None
    vehicle_kind: str
    type: str
    vehicle_number: str
    carrier: str
    assignment: str
    revision_date: str | None
    revision_date_precision: str | None
    epochs: list[str] = field(default_factory=list)
    livery: str = ""
    order_index: int = 0

    def search_blob(self) -> str:
        parts = [
            self.manufacturer,
            self.catalog_number,
            self.scale,
            self.release_date or "",
            self.vehicle_kind,
            self.type,
            self.vehicle_number,
            self.carrier,
            self.assignment,
            self.revision_date or "",
            " ".join(self.epochs),
            self.livery,
        ]
        return " ".join(p for p in parts if p).casefold()


class TableParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.rows: list[list[str]] = []
        self.thumb_urls: list[str | None] = []
        self._row: list[str] | None = None
        self._cell: list[str] | None = None
        self._in_td = False
        self._current_thumb: str | None = None
        self._row_thumbs: list[str | None] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_d = {k: v or "" for k, v in attrs}
        if tag == "tr":
            self._row = []
            self._row_thumbs = []
            self._current_thumb = None
        elif tag == "td":
            self._cell = []
            self._in_td = True
            self._current_thumb = None
        elif tag == "br" and self._in_td and self._cell is not None:
            self._cell.append(" ")
        elif tag == "img" and self._in_td:
            src = attrs_d.get("src", "")
            if src.startswith("modele/_") and src.endswith(".jpg"):
                self._current_thumb = src

    def handle_endtag(self, tag: str) -> None:
        if tag == "td" and self._row is not None and self._cell is not None:
            text = re.sub(r"\s+", " ", "".join(self._cell)).strip()
            self._row.append(text)
            self._row_thumbs.append(self._current_thumb)
            self._in_td = False
            self._cell = None
            self._current_thumb = None
        elif tag == "tr" and self._row is not None:
            if self._row and self._row[0] != "Producent" and len(self._row) >= 13:
                self.rows.append(self._row)
                thumb = self._row_thumbs[2] if len(self._row_thumbs) > 2 else None
                self.thumb_urls.append(thumb)
            self._row = None
            self._row_thumbs = []

    def handle_data(self, data: str) -> None:
        if self._in_td and self._cell is not None:
            self._cell.append(data)


def fetch_html(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return resp.read().decode("utf-8", errors="replace")


def normalize_manufacturer(raw: str) -> str:
    """Primary manufacturer = text before '[' or '|' co-producer markers."""
    text = raw.strip()
    if not text:
        return "Unknown"
    primary = re.split(r"\s*[\[|]\s*", text, maxsplit=1)[0].strip()
    primary = primary.strip("|").strip()
    return primary or text


def parse_date(raw: str) -> tuple[str | None, str | None]:
    text = raw.strip()
    if not text:
        return None, None
    m = DATE_FULL_RE.match(text)
    if m:
        d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
        try:
            return date(y, mo, d).isoformat(), "day"
        except ValueError:
            pass
    m = DATE_YEAR_RE.match(text)
    if m:
        y = int(m.group(1))
        return f"{y:04d}-01-01", "year"
    m = STAN_YEAR_RE.search(text)
    if m:
        y = int(m.group(1))
        return f"{y:04d}-01-01", "year"
    m = YEAR_ANY_RE.search(text)
    if m:
        y = int(m.group(0))
        return f"{y:04d}-01-01", "year"
    return None, None


def roman_to_int(roman: str) -> int | None:
    s = roman.upper()
    if not s or not re.fullmatch(r"[IVXLCDM]+", s):
        return None
    values = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}
    total = 0
    prev = 0
    for ch in reversed(s):
        val = values[ch]
        if val < prev:
            total -= val
        else:
            total += val
            prev = val
    return total if total > 0 else None


def int_to_roman(n: int) -> str:
    if n <= 0:
        raise ValueError(n)
    parts = (
        (1000, "M"),
        (900, "CM"),
        (500, "D"),
        (400, "CD"),
        (100, "C"),
        (90, "XC"),
        (50, "L"),
        (40, "XL"),
        (10, "X"),
        (9, "IX"),
        (5, "V"),
        (4, "IV"),
        (1, "I"),
    )
    out: list[str] = []
    rest = n
    for value, glyph in parts:
        while rest >= value:
            out.append(glyph)
            rest -= value
    return "".join(out)


def normalize_epoch_token(token: str) -> str | None:
    t = token.strip().casefold()
    t = t.replace("–", "-").replace("—", "-")
    t = re.sub(r"\s+", " ", t)
    m = EPOCH_TOKEN_RE.match(t)
    if not m:
        return None
    roman = "".join(g.upper() for g in m.groups()[:4] if g)
    if not roman or roman_to_int(roman) is None:
        return None
    letter = (m.group(5) or "").upper()
    if letter:
        return f"{roman}_{letter}"
    return roman


def epoch_rank(code: str) -> tuple[int, int] | None:
    """Sort key: (roman_int, letter_index). Bare roman → letter_index 0; A/B/C → 1/2/3."""
    if "_" in code:
        roman, letter = code.split("_", 1)
        letter_idx = EPOCH_LETTERS.index(letter) + 1 if letter in EPOCH_LETTERS else 0
    else:
        roman, letter_idx = code, 0
    value = roman_to_int(roman)
    if value is None:
        return None
    return value, letter_idx


def expand_epoch_range(start: str, end: str) -> list[str] | None:
    r0 = epoch_rank(start)
    r1 = epoch_rank(end)
    if r0 is None or r1 is None:
        return None
    if r0 > r1:
        r0, r1 = r1, r0
        start, end = end, start

    roman0, letter0 = r0
    roman1, letter1 = r1
    # Bare endpoints participate as lettered span bounds when the other side is lettered.
    if letter0 == 0 and letter1 > 0:
        letter0 = 1
    if letter1 == 0 and letter0 > 0:
        letter1 = len(EPOCH_LETTERS)

    sequence: list[str] = []
    for roman_n in range(roman0, roman1 + 1):
        roman = int_to_roman(roman_n)
        if letter0 == 0 and letter1 == 0 and roman0 == roman1:
            sequence.append(roman)
            continue
        for li, letter in enumerate(EPOCH_LETTERS, start=1):
            if roman_n == roman0 and li < letter0:
                continue
            if roman_n == roman1 and li > letter1:
                continue
            sequence.append(f"{roman}_{letter}")
    return sequence or None


def parse_epochs(raw: str) -> list[str]:
    text = raw.strip()
    if not text:
        return []
    text = text.replace("–", "-").replace("—", "-")
    if "-" in text:
        parts = [p.strip() for p in text.split("-", 1)]
        if len(parts) == 2:
            start = normalize_epoch_token(parts[0])
            end = normalize_epoch_token(parts[1])
            if start and end:
                expanded = expand_epoch_range(start, end)
                if expanded:
                    return expanded
    single = normalize_epoch_token(text)
    return [single] if single else []


def parse_raw_rows(html: str) -> list[RawRow]:
    parser = TableParser()
    parser.feed(html)
    out: list[RawRow] = []
    for i, (cells, thumb) in enumerate(zip(parser.rows, parser.thumb_urls)):
        out.append(
            RawRow(
                manufacturer_raw=cells[0],
                catalog_number=cells[1],
                thumb_url=thumb,
                scale=cells[3] or "H0",
                release_raw=cells[4],
                vehicle_kind=cells[5],
                type=cells[6],
                vehicle_number=cells[7],
                carrier=cells[8],
                assignment=cells[9],
                revision_raw=cells[10],
                epoch_raw=cells[11],
                livery=cells[12],
                order_index=i,
            )
        )
    return out


def to_parsed(row: RawRow) -> ParsedRow:
    release_date, release_prec = parse_date(row.release_raw)
    revision_date, revision_prec = parse_date(row.revision_raw)
    return ParsedRow(
        manufacturer=normalize_manufacturer(row.manufacturer_raw),
        catalog_number=row.catalog_number.strip(),
        thumb_url=row.thumb_url,
        scale=(row.scale or "H0").strip(),
        release_date=release_date,
        release_date_precision=release_prec,
        vehicle_kind=row.vehicle_kind.strip() or "unknown",
        type=row.type.strip(),
        vehicle_number=row.vehicle_number.strip(),
        carrier=row.carrier.strip(),
        assignment=row.assignment.strip(),
        revision_date=revision_date,
        revision_date_precision=revision_prec,
        epochs=parse_epochs(row.epoch_raw),
        livery=row.livery.strip(),
        order_index=row.order_index,
    )


def dedup_key(row: ParsedRow) -> tuple[str, str] | None:
    if not row.vehicle_number:
        return None
    return (row.manufacturer, row.vehicle_number)


def row_sort_key(row: ParsedRow) -> tuple:
    return (
        row.release_date or "",
        len(row.catalog_number),
        -row.order_index,
    )


def deduplicate(rows: list[ParsedRow]) -> tuple[list[ParsedRow], int]:
    best: dict[tuple[str, str], ParsedRow] = {}
    passthrough: list[ParsedRow] = []
    dropped = 0
    for row in rows:
        key = dedup_key(row)
        if key is None:
            passthrough.append(row)
            continue
        existing = best.get(key)
        if existing is None:
            best[key] = row
        else:
            dropped += 1
            if row_sort_key(row) > row_sort_key(existing):
                best[key] = row
    merged = passthrough + list(best.values())
    merged.sort(key=lambda r: r.order_index)
    return merged, dropped


def download_one(url_path: str, dest: Path, retries: int = 3) -> bool:
    url = urljoin(BASE_URL, url_path)
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            return True
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            if attempt + 1 >= retries:
                print(f"  WARN: failed {url_path}: {exc}", file=sys.stderr)
                return False
    return False


def download_images(
    rows: Iterable[ParsedRow], images_dir: Path, workers: int = 16
) -> dict[str, str]:
    """Returns map thumb_url -> relative asset path models/images/<name>."""
    unique: dict[str, Path] = {}
    for row in rows:
        if not row.thumb_url:
            continue
        name = Path(row.thumb_url).name
        unique[row.thumb_url] = images_dir / name

    to_fetch = [
        (url, dest)
        for url, dest in unique.items()
        if not dest.is_file() or dest.stat().st_size == 0
    ]
    print(f"Downloading {len(to_fetch)} thumbnails ({len(unique) - len(to_fetch)} cached)…")
    ok = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futs = {pool.submit(download_one, url, dest): url for url, dest in to_fetch}
        for fut in concurrent.futures.as_completed(futs):
            if fut.result():
                ok += 1
    print(f"Downloaded {ok}/{len(to_fetch)} thumbnails")

    mapping: dict[str, str] = {}
    for url, dest in unique.items():
        if dest.is_file() and dest.stat().st_size > 0:
            mapping[url] = f"models/images/{dest.name}"
    return mapping


SCHEMA_SQL = """
CREATE TABLE models (
  id INTEGER PRIMARY KEY,
  manufacturer TEXT NOT NULL,
  catalog_number TEXT NOT NULL,
  image_path TEXT,
  scale TEXT NOT NULL,
  release_date TEXT,
  release_date_precision TEXT,
  vehicle_kind TEXT NOT NULL,
  type TEXT,
  vehicle_number TEXT,
  carrier TEXT,
  assignment TEXT,
  revision_date TEXT,
  revision_date_precision TEXT,
  livery TEXT,
  search_blob TEXT NOT NULL
);

CREATE TABLE model_epochs (
  model_id INTEGER NOT NULL REFERENCES models(id),
  epoch TEXT NOT NULL,
  PRIMARY KEY (model_id, epoch)
);

CREATE UNIQUE INDEX ux_manufacturer_vehicle_number
  ON models(manufacturer, vehicle_number)
  WHERE vehicle_number IS NOT NULL AND vehicle_number != '';

CREATE INDEX ix_models_filters
  ON models(manufacturer, scale, vehicle_kind, carrier, revision_date);
CREATE INDEX ix_epochs ON model_epochs(epoch);

CREATE VIRTUAL TABLE models_fts USING fts5(
  search_blob, content='models', content_rowid='id'
);
"""


def write_db(rows: list[ParsedRow], image_map: dict[str, str], db_path: Path) -> None:
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(db_path)
    try:
        conn.executescript(SCHEMA_SQL)
        version = int(datetime.now(timezone.utc).strftime("%Y%m%d"))
        conn.execute(f"PRAGMA user_version = {version}")

        insert_sql = """
            INSERT INTO models (
              manufacturer, catalog_number, image_path, scale,
              release_date, release_date_precision, vehicle_kind, type,
              vehicle_number, carrier, assignment, revision_date,
              revision_date_precision, livery, search_blob
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """
        for row in rows:
            image_path = image_map.get(row.thumb_url) if row.thumb_url else None
            cur = conn.execute(
                insert_sql,
                (
                    row.manufacturer,
                    row.catalog_number,
                    image_path,
                    row.scale,
                    row.release_date,
                    row.release_date_precision,
                    row.vehicle_kind,
                    row.type,
                    row.vehicle_number or None,
                    row.carrier or None,
                    row.assignment or None,
                    row.revision_date,
                    row.revision_date_precision,
                    row.livery or None,
                    row.search_blob(),
                ),
            )
            model_id = cur.lastrowid
            for epoch in row.epochs:
                conn.execute(
                    "INSERT OR IGNORE INTO model_epochs (model_id, epoch) VALUES (?, ?)",
                    (model_id, epoch),
                )

        conn.execute("INSERT INTO models_fts(models_fts) VALUES('rebuild')")
        conn.commit()
    finally:
        conn.close()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parent / "out",
        help="Output directory for models.db and images/",
    )
    ap.add_argument("--workers", type=int, default=16)
    ap.add_argument("--skip-images", action="store_true")
    args = ap.parse_args()

    out_dir: Path = args.out
    images_dir = out_dir / "images"
    out_dir.mkdir(parents=True, exist_ok=True)
    images_dir.mkdir(parents=True, exist_ok=True)

    print(f"Fetching {SOURCE_URL}…")
    html = fetch_html(SOURCE_URL)
    raw_rows = parse_raw_rows(html)
    print(f"Parsed {len(raw_rows)} HTML rows")

    parsed = [to_parsed(r) for r in raw_rows]
    deduped, dropped = deduplicate(parsed)
    print(f"After dedup (manufacturer, vehicle_number): {len(deduped)} rows ({dropped} dropped)")

    if args.skip_images:
        image_map: dict[str, str] = {}
        for row in deduped:
            if row.thumb_url:
                name = Path(row.thumb_url).name
                image_map[row.thumb_url] = f"models/images/{name}"
    else:
        image_map = download_images(deduped, images_dir, workers=args.workers)

    db_path = out_dir / "models.db"
    write_db(deduped, image_map, db_path)
    size_mb = db_path.stat().st_size / 1e6
    img_count = sum(1 for _ in images_dir.glob("*.jpg"))
    print(f"Wrote {db_path} ({size_mb:.1f} MB), {img_count} images in {images_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
