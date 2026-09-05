#!/usr/bin/env python3
"""
score_normalizer.py — VoxSight Sheet Music Preprocessor

Detects oversized PDFs (e.g. billboard/poster page dimensions like 41"x58")
or ultra-high-resolution images (> 18 Megapixels), and scales them down cleanly
to standard 300 DPI Letter/A4 dimensions (~2,300 x 3,300 px, ~7.7–8.5 MP)
so they stay well within Audiveris's 20,000,000 pixel engine ceiling.
"""

import sys
import shutil
from pathlib import Path

# Explicitly ensure PIL JPEG plugin is registered
import PIL.JpegImagePlugin
from PIL import Image

MAX_SAFE_PIXELS = 18_000_000  # Audiveris ceiling is 20,000,000 px
TARGET_MAX_DIMENSION = 3300   # Standard 300 DPI A4/Letter max pixel dimension


def normalize_pdf(input_path: str, output_path: str) -> bool:
    try:
        import pypdfium2 as pdfium
    except ImportError:
        print("[ScoreNormalizer] pypdfium2 not available, skipping PDF normalization.")
        return False

    pdf = pdfium.PdfDocument(input_path)
    page_count = len(pdf)
    if page_count == 0:
        return False

    # Check if any page is oversized at 300 DPI (scale = 300 / 72 = 4.1667)
    needs_downscale = False
    for page in pdf:
        w_pt, h_pt = page.get_size()
        w_px = int(w_pt * (300.0 / 72.0))
        h_px = int(h_pt * (300.0 / 72.0))
        if (w_px * h_px) > MAX_SAFE_PIXELS or max(w_px, h_px) > 4500:
            needs_downscale = True
            break

    if not needs_downscale:
        print(f"[ScoreNormalizer] PDF {input_path} is within safe dimensions. No normalization needed.")
        if input_path != output_path:
            shutil.copy2(input_path, output_path)
        return True

    print(f"[ScoreNormalizer] PDF {input_path} is oversized (> {MAX_SAFE_PIXELS:,} px). Normalizing {page_count} page(s)...")

    images = []
    for i, page in enumerate(pdf):
        w_pt, h_pt = page.get_size()
        dpi_scale = 300.0 / 72.0
        target_w = int(w_pt * dpi_scale)
        target_h = int(h_pt * dpi_scale)

        if target_w * target_h > MAX_SAFE_PIXELS or max(target_w, target_h) > TARGET_MAX_DIMENSION:
            factor = TARGET_MAX_DIMENSION / max(target_w, target_h)
            dpi_scale *= factor

        rendered_img = page.render(scale=dpi_scale).to_pil().convert("RGB")
        print(f"[ScoreNormalizer] Page {i+1}/{page_count}: {rendered_img.size[0]}x{rendered_img.size[1]} ({rendered_img.size[0]*rendered_img.size[1]:,} px)")
        images.append(rendered_img)

    if images:
        images[0].save(
            output_path,
            save_all=True,
            append_images=images[1:],
            resolution=300.0
        )
        print(f"[ScoreNormalizer] Successfully saved normalized PDF to: {output_path}")
        return True

    return False


def normalize_image(input_path: str, output_path: str) -> bool:
    try:
        with Image.open(input_path) as img:
            w, h = img.size
            if (w * h) <= MAX_SAFE_PIXELS and max(w, h) <= 4500:
                print(f"[ScoreNormalizer] Image {input_path} ({w}x{h}) is within safe bounds.")
                if input_path != output_path:
                    shutil.copy2(input_path, output_path)
                return True

            print(f"[ScoreNormalizer] Image {input_path} ({w}x{h} = {w*h:,} px) exceeds safe ceiling. Downscaling...")
            scale = TARGET_MAX_DIMENSION / max(w, h)
            new_w = int(w * scale)
            new_h = int(h * scale)
            resized = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
            resized.save(output_path, quality=95)
            print(f"[ScoreNormalizer] Successfully saved normalized image ({new_w}x{new_h}) to: {output_path}")
            return True
    except Exception as e:
        print(f"[ScoreNormalizer] Error normalizing image: {e}")
        return False


def main():
    if len(sys.argv) < 3:
        print("Usage: python score_normalizer.py <input_file> <output_file>")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2]

    if not Path(input_file).exists():
        print(f"[ScoreNormalizer] Input file does not exist: {input_file}")
        sys.exit(1)

    lower_name = input_file.lower()
    success = False
    if lower_name.endswith(".pdf"):
        success = normalize_pdf(input_file, output_file)
    else:
        success = normalize_image(input_file, output_file)

    if success:
        sys.exit(0)
    else:
        # Fallback: copy original if normalization could not proceed
        if input_file != output_file and Path(input_file).exists():
            shutil.copy2(input_file, output_file)
        sys.exit(0)


if __name__ == "__main__":
    main()
