import os
from pypdf import PdfReader

with open("output_utf8.txt", "w", encoding="utf-8") as f:
    for file_name in ["2526-sem2-it332-51_Proposal.docx.pdf", "Capstone SDD.pdf"]:
        f.write(f"--- START OF {file_name} ---\n")
        try:
            reader = PdfReader(file_name)
            for i, page in enumerate(reader.pages):
                text = page.extract_text()
                if text:
                    f.write(f"Page {i+1}:\n{text}\n\n")
        except Exception as e:
            f.write(f"Error reading {file_name}: {e}\n")
        f.write(f"--- END OF {file_name} ---\n\n")
