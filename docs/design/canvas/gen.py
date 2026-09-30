import json, datetime
import boards
import boards_more
from boards import BOARDS, OUT

idx = {
    "v": 3, "attachments": {}, "createdOnFiles": {"v": 1, "at": "2026-09-30T18:18:21Z"},
    "title": "HearOn Link design", "launch": {"view": "canvas"}, "pages": [],
    "boards": BOARDS, "order": list(BOARDS), "notes": boards_more.NOTES, "designSystems": [],
}
(OUT / "canvas.json").write_text(json.dumps(idx, indent=1))
print("\n".join(f"{k}: {v['w']}x{v['h']} @ {v['x']},{v['y']}" for k, v in BOARDS.items()))
