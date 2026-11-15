"""Makes kit-calendar's test tables from the National Astronomical Observatory's 暦要項 and the
Cabinet Office's list of national holidays (platform REQ-KOYOMI D-2, AC-1).

    python -I kit-calendar/tools/rekiyou_fixtures.py <folder>

<folder> holds yoko2016.pdf … yoko2024.pdf, s2025.html … s2027.html (二十四節気・雑節), p2025.html …
p2027.html (朔弦望) and syukujitsu.csv. Writes src/test/resources/rekiyou/:

- sekki.tsv, phases.tsv: every "month day hour minute" group on that year's 二十四節気 page and
  朔弦望 page, one per line (year, month, day, hour, minute). The PDFs' Japanese text doesn't come
  out of pdftotext, so the groups are kept without their names; a test looks a computed moment up
  among them.
- holidays.tsv: the holidays of 2016-2027 (date, name), in UTF-8.
"""

import html
import re
import subprocess
import sys
from pathlib import Path

src = Path(sys.argv[1])
out = Path(__file__).resolve().parent.parent / "src" / "test" / "resources" / "rekiyou"


def groups(text):
    nums = [int(x) for x in re.findall(r"\d+", text)]
    found = set()
    for i in range(len(nums) - 3):
        m, d, h, mi = nums[i:i + 4]
        if 1 <= m <= 12 and 1 <= d <= 31 and 0 <= h <= 23 and 0 <= mi <= 59:
            found.add((m, d, h, mi))
    return sorted(found)


def pdf_pages(path, mode):
    text = subprocess.run(
        ["pdftotext", mode, "-enc", "UTF-8", str(path), "-"],
        capture_output=True, encoding="utf-8", check=True,
    ).stdout
    return text.split("\f")


def html_text(path):
    raw = path.read_bytes().decode("shift_jis", errors="replace")
    return html.unescape(re.sub(r"<[^>]+>", " ", raw))


sekki, phases = [], []
for y in range(2016, 2025):
    # Both layouts: -layout sometimes runs two numbers together ("8 0" as "80"), -raw keeps them apart
    # but may order a page's columns differently; a group either gives is kept.
    for mode in ("-layout", "-raw"):
        pages = pdf_pages(src / f"yoko{y}.pdf", mode)
        # The 二十四節気 page has the longitudes 285, 300, 315, 330 and 345; 朔弦望 is the page after it.
        i = next(k for k, p in enumerate(pages) if all(re.search(rf"\b{n}\b", p) for n in (285, 300, 315, 330, 345)))
        sekki += [(y, *g) for g in groups(pages[i])]
        phases += [(y, *g) for g in groups(pages[i + 1])]
sekki, phases = sorted(set(sekki)), sorted(set(phases))
for y in range(2025, 2028):
    sekki += [(y, *g) for g in groups(html_text(src / f"s{y}.html"))]
    phases += [(y, *g) for g in groups(html_text(src / f"p{y}.html"))]

out.mkdir(parents=True, exist_ok=True)
for name, rows in (("sekki.tsv", sekki), ("phases.tsv", phases)):
    (out / name).write_text("".join("\t".join(map(str, r)) + "\n" for r in rows), encoding="utf-8", newline="\n")

lines = []
for line in (src / "syukujitsu.csv").read_bytes().decode("shift_jis").splitlines()[1:]:
    date, name = line.split(",", 1)
    y, m, d = map(int, date.split("/"))
    if 2016 <= y <= 2027:
        lines.append(f"{y:04d}-{m:02d}-{d:02d}\t{name.strip()}\n")
(out / "holidays.tsv").write_text("".join(lines), encoding="utf-8", newline="\n")
print(f"sekki {len(sekki)}, phases {len(phases)}, holidays {len(lines)}")
