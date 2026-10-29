"""生成演示用的模板封面与示例上传图片（全部为程序绘制，无第三方版权素材）。

用法: python3 scripts/generate_demo_images.py
依赖: Pillow
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
COVER_DIR = os.path.join(ROOT, "backend", "src", "main", "resources", "static", "demo", "covers")
SAMPLE_DIR = os.path.join(ROOT, "samples")

W, H = 600, 800


def gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h), top)
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (w, y)], fill=c)
    return img


def mountains(img, color, base, amp, seed):
    rnd = random.Random(seed)
    d = ImageDraw.Draw(img)
    w, h = img.size
    pts = [(0, h)]
    x = 0
    while x <= w:
        pts.append((x, base - rnd.randint(0, amp)))
        x += rnd.randint(60, 140)
    pts += [(w, base - rnd.randint(0, amp)), (w, h)]
    d.polygon(pts, fill=color)


def sun(img, cx, cy, r, color):
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([cx - r * 2, cy - r * 2, cx + r * 2, cy + r * 2], fill=color + (70,))
    glow = glow.filter(ImageFilter.GaussianBlur(r // 2))
    img.paste(glow, (0, 0), glow)
    ImageDraw.Draw(img).ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


def heart(d, cx, cy, s, color):
    pts = []
    for i in range(100):
        t = math.pi * 2 * i / 100
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * s, cy - y * s))
    d.polygon(pts, fill=color)


def paw(d, cx, cy, s, color):
    d.ellipse([cx - 2.2 * s, cy - 1.2 * s, cx + 2.2 * s, cy + 2.4 * s], fill=color)
    for dx, dy in [(-2.6, -2.4), (-0.9, -3.6), (0.9, -3.6), (2.6, -2.4)]:
        d.ellipse([cx + dx * s - 0.9 * s, cy + dy * s - 1.1 * s, cx + dx * s + 0.9 * s, cy + dy * s + 1.1 * s], fill=color)


def snowflakes(d, w, h, n, seed):
    rnd = random.Random(seed)
    for _ in range(n):
        x, y, r = rnd.randint(0, w), rnd.randint(0, h), rnd.randint(2, 6)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255))


def cover(name, draw_fn):
    img = draw_fn()
    os.makedirs(COVER_DIR, exist_ok=True)
    img.convert("RGB").save(os.path.join(COVER_DIR, f"{name}.jpg"), quality=85)


def c_postcard():
    img = gradient(W, H, (255, 183, 120), (120, 90, 200))
    sun(img, 420, 260, 70, (255, 236, 170))
    mountains(img, (90, 70, 150), 560, 160, 1)
    mountains(img, (50, 40, 100), 660, 120, 2)
    d = ImageDraw.Draw(img)
    d.rectangle([20, 20, W - 20, H - 20], outline=(255, 255, 255), width=6)
    return img


def c_collage():
    img = Image.new("RGB", (W, H), (30, 32, 40))
    palette = [((90, 200, 250), (20, 90, 160)), ((250, 200, 120), (220, 90, 80)), ((140, 220, 160), (30, 120, 90))]
    boxes = [(20, 20, 290, 390), (310, 20, 580, 390), (20, 410, 580, 780)]
    for i, b in enumerate(boxes):
        tile = gradient(b[2] - b[0], b[3] - b[1], *palette[i])
        mountains(tile, tuple(max(0, c - 60) for c in palette[i][1]), tile.size[1] - 40, 90, i + 5)
        img.paste(tile, (b[0], b[1]))
    return img


def c_pet_portrait():
    img = gradient(W, H, (250, 230, 210), (230, 180, 150))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([60, 80, W - 60, H - 120], radius=30, outline=(150, 100, 60), width=14)
    d.ellipse([170, 220, 430, 500], fill=(255, 255, 255))
    d.ellipse([150, 200, 250, 320], fill=(245, 245, 245))
    d.ellipse([350, 200, 450, 320], fill=(245, 245, 245))
    d.ellipse([240, 320, 270, 350], fill=(40, 40, 40))
    d.ellipse([330, 320, 360, 350], fill=(40, 40, 40))
    d.ellipse([283, 380, 317, 405], fill=(60, 40, 40))
    paw(d, 300, 610, 14, (150, 100, 60))
    return img


def c_pet_sticker():
    img = gradient(W, H, (200, 240, 255), (160, 200, 250))
    d = ImageDraw.Draw(img)
    d.ellipse([110, 180, 490, 560], fill=(255, 255, 255))
    paw(d, 300, 400, 34, (255, 140, 160))
    for i in range(6):
        paw(d, 80 + i * 90, 700, 8, (255, 255, 255))
    return img


def c_wedding():
    img = gradient(W, H, (40, 40, 48), (10, 10, 14))
    d = ImageDraw.Draw(img)
    snowflakes(d, W, H, 80, 3)
    d.ellipse([170, 220, 290, 340], fill=(230, 230, 230))
    d.polygon([(150, 760), (230, 340), (310, 760)], fill=(245, 245, 245))
    d.ellipse([310, 200, 420, 310], fill=(200, 200, 200))
    d.polygon([(300, 760), (365, 310), (430, 760)], fill=(70, 70, 80))
    return img


def c_love_card():
    img = gradient(W, H, (255, 200, 215), (240, 120, 160))
    d = ImageDraw.Draw(img)
    rnd = random.Random(9)
    for _ in range(14):
        heart(d, rnd.randint(40, W - 40), rnd.randint(40, H - 40), rnd.uniform(0.8, 2), (255, 235, 240))
    heart(d, 300, 400, 11, (220, 40, 90))
    return img


def c_art_poster():
    img = Image.new("RGB", (W, H), (245, 240, 230))
    d = ImageDraw.Draw(img)
    rnd = random.Random(11)
    colors = [(230, 80, 60), (40, 90, 180), (250, 200, 50), (30, 30, 30), (60, 160, 120)]
    for _ in range(18):
        x, y = rnd.randint(-50, W), rnd.randint(-50, H)
        s = rnd.randint(60, 220)
        c = rnd.choice(colors)
        if rnd.random() < 0.5:
            d.ellipse([x, y, x + s, y + s], fill=c)
        else:
            d.rectangle([x, y, x + s, y + s // 2], fill=c)
    return img


def c_holiday():
    img = gradient(W, H, (20, 60, 50), (10, 30, 25))
    d = ImageDraw.Draw(img)
    snowflakes(d, W, H, 120, 5)
    d.polygon([(300, 150), (130, 600), (470, 600)], fill=(40, 140, 80))
    d.rectangle([275, 600, 325, 680], fill=(110, 70, 40))
    rnd = random.Random(7)
    for _ in range(14):
        x, y = rnd.randint(200, 400), rnd.randint(300, 580)
        d.ellipse([x - 9, y - 9, x + 9, y + 9], fill=rnd.choice([(230, 50, 50), (250, 210, 60), (255, 255, 255)]))
    d.regular_polygon((300, 150, 30), 5, fill=(250, 210, 60))
    return img


def samples():
    os.makedirs(SAMPLE_DIR, exist_ok=True)
    specs = [
        ("beach.jpg", lambda: (lambda im: (sun(im, 300, 200, 60, (255, 240, 200)), mountains(im, (20, 120, 170), 520, 30, 4), im)[-1])(gradient(800, 600, (130, 200, 255), (250, 220, 170)))),
        ("mountain.jpg", lambda: (lambda im: (mountains(im, (80, 90, 120), 420, 200, 8), mountains(im, (40, 50, 70), 540, 120, 9), im)[-1])(gradient(800, 600, (255, 170, 120), (120, 130, 200)))),
        ("city.jpg", lambda: _city()),
        ("pet.png", lambda: (lambda im: (paw(ImageDraw.Draw(im), 300, 330, 40, (255, 255, 255)), im)[-1])(gradient(600, 600, (255, 190, 150), (240, 130, 120)))),
        ("couple.png", lambda: (lambda im: (heart(ImageDraw.Draw(im), 300, 300, 12, (230, 40, 90)), im)[-1])(gradient(600, 600, (255, 230, 235), (255, 180, 200)))),
    ]
    for name, fn in specs:
        img = fn().convert("RGB")
        path = os.path.join(SAMPLE_DIR, name)
        if name.endswith(".png"):
            img.save(path, optimize=True)
        else:
            img.save(path, quality=88)
    # 反例：GIF 类型不被接受；PNG 超过 2MB 用于测试「宠物贴纸」的大小限制
    gradient(200, 200, (100, 100, 100), (200, 200, 200)).save(os.path.join(SAMPLE_DIR, "invalid-type.gif"))
    noise = Image.effect_noise((1100, 1100), 100).convert("RGB")
    noise.save(os.path.join(SAMPLE_DIR, "too-large-for-sticker.png"))


def _city():
    im = gradient(800, 600, (20, 20, 60), (90, 60, 120))
    d = ImageDraw.Draw(im)
    rnd = random.Random(12)
    x = 0
    while x < 800:
        w, h = rnd.randint(40, 90), rnd.randint(150, 420)
        d.rectangle([x, 600 - h, x + w, 600], fill=(25, 25, 40))
        for wy in range(600 - h + 10, 590, 22):
            for wx in range(x + 6, x + w - 10, 16):
                if rnd.random() < 0.5:
                    d.rectangle([wx, wy, wx + 7, wy + 10], fill=(250, 220, 120))
        x += w + rnd.randint(2, 10)
    return im


if __name__ == "__main__":
    for name, fn in [
        ("demo-postcard", c_postcard), ("travel-collage", c_collage), ("pet-portrait", c_pet_portrait),
        ("pet-sticker", c_pet_sticker), ("wedding-album", c_wedding), ("love-card", c_love_card),
        ("art-poster", c_art_poster), ("holiday-card", c_holiday),
    ]:
        cover(name, fn)
    samples()
    print("done")
