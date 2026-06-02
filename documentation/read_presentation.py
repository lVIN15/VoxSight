import os
from pypdf import PdfReader

pdf_path = "Voxsight MVP Presentation.pptx.pdf"
output_path = "presentation_text.txt"

print(f"Extracting text from {pdf_path}...")
try:
    reader = PdfReader(pdf_path)
    with open(output_path, "w", encoding="utf-8") as f:
        for i, page in enumerate(reader.pages):
            text = page.extract_text()
            f.write(f"=== PAGE {i+1} ===\n")
            if text:
                f.write(text)
            f.write("\n\n")
    print(f"Extraction successful! Written to {output_path}")
except Exception as e:
    print(f"Error: {e}")
