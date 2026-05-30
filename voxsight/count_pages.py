import struct
import re

def get_pdf_pages(file_path):
    with open(file_path, "rb") as f:
        content = f.read()
    
    # Very basic PDF page counter (looks for /Type /Page)
    # This is not perfect but works for most standard PDFs
    num_pages = len(re.findall(b"/Type\s*/Page[^s]", content))
    return num_pages

file1 = r'C:\Users\LAGAMO\Documents\3rd year 2nd sem\Capstone\VoxSight\voxsight\uploads\1779809832650-Alleluia-Misa-Pasalamat-by-Arnold-Zamora_(1).pdf'
try:
    print("Alleluia pages:", get_pdf_pages(file1))
except Exception as e:
    print("Error 1:", e)

file2 = r'C:\Users\LAGAMO\Documents\3rd year 2nd sem\Capstone\VoxSight\voxsight\uploads\1779799163237-Song_20for_20St._20Therese_20-_20SATB_20G_20major_20EDITED_(1).pdf'
try:
    print("St. Therese pages:", get_pdf_pages(file2))
except Exception as e:
    print("Error 2:", e)
