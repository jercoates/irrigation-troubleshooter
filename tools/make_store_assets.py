"""Renders store/icon-512.png and store/feature-graphic-1024x500.png (same drop+bolt mark as the app icon)."""
from PIL import Image, ImageDraw, ImageFont
import math, os

OUT = os.path.join(os.path.dirname(__file__), "..", "store")
BLUE, WHITE, AMBER = (31, 81, 130), (255, 255, 255), (242, 169, 0)

def bez(p0, p1, p2, p3, n=40):
    return [tuple((1-t)**3*a + 3*(1-t)**2*t*b + 3*(1-t)*t**2*c + t**3*d for a, b, c, d in zip(p0, p1, p2, p3))
            for t in (i/n for i in range(n+1))]

def drop_points():  # 108-unit design space, matches ic_launcher_foreground.xml
    pts = bez((54, 24), (54, 24), (36, 46), (36, 60))
    pts += [(54 + 18*math.cos(math.radians(a)), 60 + 18*math.sin(math.radians(a))) for a in range(180, -1, -5)][::-1][::-1]
    pts = bez((54, 24), (54, 24), (36, 46), (36, 60)) + \
          [(54 + 18*math.cos(math.radians(a)), 60 + 18*math.sin(math.radians(a))) for a in range(180, 361, 5)][:0] + \
          [(54 + 18*math.cos(math.radians(a)), 60 + 18*math.sin(math.radians(a))) for a in range(180, -1, -5)] + \
          bez((72, 60), (72, 46), (54, 24), (54, 24))
    return pts
BOLT = [(57, 46), (45, 63), (53, 63), (49, 76), (63, 58), (55, 58)]

def mark(draw, ox, oy, scale):
    tf = lambda pts: [(ox + x*scale, oy + y*scale) for x, y in pts]
    draw.polygon(tf(drop_points()), fill=WHITE)
    draw.polygon(tf(BOLT), fill=AMBER)

os.makedirs(OUT, exist_ok=True)

# 512x512 icon (Play applies its own mask; full-bleed square, no transparency)
S = 4
img = Image.new("RGB", (512*S, 512*S), BLUE)
mark(ImageDraw.Draw(img), 0, 0, 512*S/108)
img.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, "icon-512.png"))

# 1024x500 feature graphic
W, H = 1024, 500
img = Image.new("RGB", (W*S, H*S), BLUE)
d = ImageDraw.Draw(img)
sc = 4.2*S
mark(d, 70*S - 20*sc, 250*S - 54*sc, sc)
def font(size):
    for f in ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try: return ImageFont.truetype(f, size*S)
        except OSError: pass
    return ImageFont.load_default()
d.text((400*S, 150*S), "Irrigation", font=font(64), fill=WHITE)
d.text((400*S, 228*S), "Troubleshooter", font=font(64), fill=WHITE)
d.text((404*S, 335*S), "Step-by-step electrical diagnosis. Works offline.", font=font(26), fill=(200, 220, 240))
img.resize((W, H), Image.LANCZOS).save(os.path.join(OUT, "feature-graphic-1024x500.png"))
print("ok")
