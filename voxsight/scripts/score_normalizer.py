#!/usr/bin/env python3
"""
score_normalizer.py — VoxSight Sheet Music Preprocessor

Comprehensive preprocessing pipeline for camera-captured sheet music:
1. Adaptive background division — removes shadows, lighting gradients, screen backlighting
2. Automatic deskewing — levels tilted staff lines via projection profile variance
3. Dark border trimming — removes surrounding desk/screen edges
4. Resolution normalization — scales to safe Audiveris bounds (<=18MP, <=3300px max dim)
5. Explicit format handling — never fails on extensionless files
"""

import sys
import shutil
import math
from pathlib import Path

# Explicitly ensure PIL JPEG plugin is registered
import PIL.JpegImagePlugin
from PIL import Image, ImageFilter

try:
    import numpy as np
    HAS_NUMPY = True
except ImportError:
    HAS_NUMPY = False

MAX_SAFE_PIXELS = 18_000_000  # Audiveris ceiling is 20,000,000 px
TARGET_MAX_DIMENSION = 3300   # Standard 300 DPI A4/Letter max pixel dimension


def _trim_dark_borders(img: Image.Image, threshold_ratio: float = 0.45) -> Image.Image:
    """
    Removes dark borders (desk, screen bezel, etc.) around the actual page content.
    Uses row/column mean brightness to find the bright page rectangle.
    """
    if not HAS_NUMPY:
        return img

    gray = img.convert("L")
    arr = np.array(gray, dtype=np.float32)
    h, w = arr.shape

    # Skip if image is very small
    if h < 100 or w < 100:
        return img

    row_means = np.mean(arr, axis=1)
    col_means = np.mean(arr, axis=0)

    overall_mean = np.mean(arr)
    threshold = overall_mean * threshold_ratio

    valid_rows = np.where(row_means > threshold)[0]
    valid_cols = np.where(col_means > threshold)[0]

    if len(valid_rows) < 50 or len(valid_cols) < 50:
        return img  # Can't determine page boundaries reliably

    top, bottom = int(valid_rows[0]), int(valid_rows[-1])
    left, right = int(valid_cols[0]), int(valid_cols[-1])

    # Only crop if we'd remove at least 3% from any side
    margin_threshold = 0.03
    if (top / h < margin_threshold and (h - bottom) / h < margin_threshold and
            left / w < margin_threshold and (w - right) / w < margin_threshold):
        return img  # No significant dark borders detected

    # Add a small safety margin (1% of dimension)
    pad_y = max(5, int(h * 0.01))
    pad_x = max(5, int(w * 0.01))
    top = max(0, top - pad_y)
    bottom = min(h - 1, bottom + pad_y)
    left = max(0, left - pad_x)
    right = min(w - 1, right + pad_x)

    cropped = img.crop((left, top, right + 1, bottom + 1))
    cw, ch = cropped.size
    print(f"[ScoreNormalizer] Trimmed dark borders: ({left},{top})-({right},{bottom}) => {cw}x{ch}")
    return cropped


def _adaptive_background_normalize(img: Image.Image) -> Image.Image:
    """
    Adaptive background division: removes shadows and uneven lighting.
    Estimates the local paper background by downscaling + max filter + blur,
    then divides the original by the background to produce uniform white paper
    with dark notation preserved.
    """
    if not HAS_NUMPY:
        return img

    gray = img.convert("L")
    w, h = gray.size
    arr = np.array(gray, dtype=np.float32)

    # Estimate background: downscale, max filter (to capture paper brightness), blur
    small_w = max(1, w // 8)
    small_h = max(1, h // 8)
    small = gray.resize((small_w, small_h), Image.Resampling.BILINEAR)

    # MaxFilter captures the brightest (paper) values locally
    # GaussianBlur smooths the background estimate
    filter_size = max(3, min(15, small_w // 10) | 1)  # Must be odd
    bg_small = small.filter(ImageFilter.MaxFilter(size=filter_size))
    bg_small = bg_small.filter(ImageFilter.GaussianBlur(radius=max(3, filter_size)))

    bg = np.array(
        bg_small.resize((w, h), Image.Resampling.BILINEAR),
        dtype=np.float32
    )
    bg = np.maximum(bg, 1.0)  # Prevent division by zero

    # Divide: normalized = (pixel / background) * 255
    normalized = np.clip((arr / bg) * 255.0, 0, 255).astype(np.uint8)

    result = Image.fromarray(normalized, mode="L")
    print(f"[ScoreNormalizer] Applied adaptive background normalization")
    return result


def _deskew(img: Image.Image, max_angle: float = 4.0, steps: int = 33) -> Image.Image:
    """
    Automatic deskewing via projection profile variance.
    Rotates the image by the angle that maximizes horizontal line alignment
    (highest row-projection variance = best-aligned staff lines).
    """
    if not HAS_NUMPY:
        return img

    gray = img.convert("L")
    w, h = gray.size

    # Work on a small version for speed
    scale = min(1.0, 600.0 / max(w, h))
    sw, sh = max(1, int(w * scale)), max(1, int(h * scale))
    small = gray.resize((sw, sh), Image.Resampling.BILINEAR)

    best_angle = 0.0
    max_var = -1.0

    for i in range(steps):
        test_angle = -max_angle + i * (2.0 * max_angle / (steps - 1))
        rotated = small.rotate(test_angle, resample=Image.Resampling.BILINEAR, fillcolor=255)
        arr = 255.0 - np.array(rotated, dtype=np.float32)
        # Row sum projection: highest variance = staff lines aligned horizontally
        proj = np.sum(arr, axis=1)
        var = float(np.var(proj))
        if var > max_var:
            max_var = var
            best_angle = test_angle

    if abs(best_angle) < 0.15:
        print(f"[ScoreNormalizer] Skew angle ~0 degrees (detected {best_angle:.2f}), no rotation needed")
        return img

    print(f"[ScoreNormalizer] Detected skew angle: {best_angle:.2f} degrees, applying deskew rotation")
    fill = 255 if img.mode == "L" else (255, 255, 255)
    deskewed = img.rotate(best_angle, resample=Image.Resampling.BILINEAR,
                          expand=True, fillcolor=fill)
    return deskewed


def _determine_output_format(input_path: str, output_path: str):
    """Determine PIL save format and kwargs from output path, with safe fallbacks."""
    lower = output_path.lower()
    if lower.endswith(".png"):
        return {"format": "PNG"}
    elif lower.endswith(".pdf"):
        return {"format": "PDF", "resolution": 300.0}
    elif lower.endswith((".jpg", ".jpeg")):
        return {"format": "JPEG", "quality": 95}
    else:
        # No recognizable extension: check input, default to JPEG
        lower_in = input_path.lower()
        if lower_in.endswith(".png"):
            return {"format": "PNG"}
        return {"format": "JPEG", "quality": 95}


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
            resolution=300.0,
            format="PDF"
        )
        print(f"[ScoreNormalizer] Successfully saved normalized PDF to: {output_path}")
        return True

    return False


def normalize_image(input_path: str, output_path: str) -> bool:
    """
    Full preprocessing pipeline for camera-captured sheet music images:
    1. Trim dark borders (desk/screen edges)
    2. Adaptive background normalization (remove shadows & lighting)
    3. Deskew (level staff lines)
    4. Resize to safe Audiveris bounds
    """
    try:
        with Image.open(input_path) as img:
            img = img.convert("RGB")
            w, h = img.size
            print(f"[ScoreNormalizer] Input image: {w}x{h} ({w*h:,} px)")

            # Step 1: Trim dark borders
            img = _trim_dark_borders(img)

            # Step 2: Adaptive background normalization (shadow & lighting removal)
            img = _adaptive_background_normalize(img)

            # Step 3: Deskew
            img = _deskew(img)

            # Step 4: Resize if needed
            w, h = img.size
            if (w * h) > MAX_SAFE_PIXELS or max(w, h) > TARGET_MAX_DIMENSION + 200:
                scale = TARGET_MAX_DIMENSION / max(w, h)
                new_w = int(w * scale)
                new_h = int(h * scale)
                img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
                print(f"[ScoreNormalizer] Resized to {new_w}x{new_h}")

            # Step 5: Save with explicit format
            save_kwargs = _determine_output_format(input_path, output_path)
            img.save(output_path, **save_kwargs)

            final_w, final_h = img.size
            print(f"[ScoreNormalizer] Final output: {final_w}x{final_h} => {output_path}")
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
