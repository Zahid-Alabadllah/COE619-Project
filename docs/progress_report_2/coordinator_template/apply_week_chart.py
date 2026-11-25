"""Apply the approved PR1-consistent chart without changing other report content."""
from pathlib import Path
import sys
import zipfile
import hashlib
from copy import deepcopy
from lxml import etree

if len(sys.argv) > 1 and sys.argv[1] == "--assemble-pdf":
    from pypdf import PdfReader, PdfWriter
    cover, revised, destination = map(Path, sys.argv[2:5])
    original_pdf, revised_pdf = PdfReader(cover), PdfReader(revised)
    if len(revised_pdf.pages) != 3:
        raise ValueError("PR2 must retain three pages")
    writer = PdfWriter()
    writer.add_page(original_pdf.pages[0])
    writer.add_page(revised_pdf.pages[1])
    writer.add_page(revised_pdf.pages[2])
    with destination.open("wb") as stream:
        writer.write(stream)
    raise SystemExit(0)

base = Path(__file__).resolve().parent
assets = etree.parse(str(base / "week_chart_source.xml")).getroot()
pr1 = Path(__file__).resolve().parents[4] / "Course_Documents/04_Progress_Report_I/PR1_202045160.docx"
if hashlib.sha256(pr1.read_bytes()).hexdigest() != assets.get("pr1_sha256"):
    raise ValueError("PR1 changed: recheck phase-window consistency before regenerating PR2")
source, output = map(Path, sys.argv[1:3])
ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
with zipfile.ZipFile(source) as archive:
    parts = [(entry, archive.read(entry.filename)) for entry in archive.infolist()]
root = etree.fromstring(dict((entry.filename, data) for entry, data in parts)["word/document.xml"])
tables = root.findall(".//w:body/w:tbl", ns)
tables[3].getparent().replace(tables[3], deepcopy(assets.find("chart")[0]))
cell = tables[4].findall("w:tr", ns)[1].find("w:tc", ns)
cell.getparent().replace(cell, deepcopy(assets.find("explanation")[0]))
for paragraph in root.findall(".//w:p", ns):
    if "".join(paragraph.xpath(".//w:t/text()", namespaces=ns)).startswith("Accomplishment Chart:"):
        paragraph.getparent().replace(paragraph, deepcopy(assets.find("caption")[0]))
        break
else:
    raise ValueError("Original accomplishment caption not found")
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for entry, data in parts:
        if entry.filename == "word/document.xml":
            data = etree.tostring(root, encoding="UTF-8", xml_declaration=True, standalone=True)
        archive.writestr(entry, data)
