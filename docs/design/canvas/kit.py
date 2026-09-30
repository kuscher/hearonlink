"""Tiny HTML kit for the HearOn Link design canvas (.dc.html artboards)."""
import json

FONT = "/_blob/6a8e0bb3a701fd95067570c96ed94928"

LIGHT = dict(
    page="#F5F9F8", chrome="#E8F0EE", card="#FFFFFF", card2="#EFF5F4",
    text="#161D1C", text2="#3E4947", text3="#56625F", line="#C4D0CD",
    primary="#00696B", onPrimary="#FFFFFF", pc="#B2EEEE", onPc="#002021",
    sc="#D5E6E3", onSc="#102220", no="#9C4230", noC="#FFDBD2", onNoC="#3B0900",
    ok="#1E6B38", track="#DCE3E1", faint="#9AA6A3",
)
DARK = dict(
    page="#0F1514", chrome="#161E1D", card="#1C2625", card2="#223030",
    text="#DDE4E3", text2="#BAC9C6", text3="#A3B1AE", line="#3A4745",
    primary="#6FD6D7", onPrimary="#003738", pc="#004F51", onPc="#B2EEEE",
    sc="#2E4644", onSc="#CDE8E5", no="#FFB4A2", noC="#7E2B1B", onNoC="#FFDBD2",
    ok="#8BD8A0", track="#2B3634", faint="#6B7876",
)

P = {  # 24-unit stroke icons (paths/shapes, no <svg> wrapper)
    "down": '<path d="M7 10l5 5 5-5"/>',
    "right": '<path d="M9 6l6 6-6 6"/>',
    "back": '<path d="M19 12H5"/><path d="M11 6l-6 6 6 6"/>',
    "more": '<circle cx="12" cy="5" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.6" fill="currentColor" stroke="none"/><circle cx="12" cy="19" r="1.6" fill="currentColor" stroke="none"/>',
    "tune": '<path d="M4 7h9M18 7h2M4 17h3M12 17h8"/><circle cx="15.5" cy="7" r="2.3"/><circle cx="9.5" cy="17" r="2.3"/>',
    "bolt": '<path d="M13 3L6 13h5l-1 8 7-10h-5z" fill="currentColor" stroke="none"/>',
    "head": '<circle cx="12" cy="8.5" r="4"/><path d="M5 20c1.4-3.6 4-5.2 7-5.2s5.6 1.6 7 5.2"/>',
    "ear": '<path d="M7.5 9.5a4.5 4.5 0 0 1 9 0c0 3.2-3.4 4-3.4 7a2.6 2.6 0 0 1-5.1.6"/><path d="M10.5 9.5a1.5 1.5 0 0 1 3 0"/>',
    "hold": '<rect x="9" y="3" width="6" height="12" rx="3"/><path d="M12 15v6"/><path d="M8 19h8"/>',
    "info": '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r="1.1" fill="currentColor" stroke="none"/>',
    "check": '<path d="M5 12.5l4.5 4.5L19 7"/>',
    "close": '<path d="M7 7l10 10M17 7L7 17"/>',
    "play": '<path d="M8 5.5v13l10.5-6.5z" fill="currentColor" stroke="none"/>',
    "call": '<path d="M6 3.5h3.2l1.6 4.4-2.2 1.4a11 11 0 0 0 5.6 5.6l1.4-2.2 4.4 1.6v3.2a2 2 0 0 1-2.1 2A16 16 0 0 1 4 5.6a2 2 0 0 1 2-2.1z"/>',
    "bt": '<path d="M7 7.5l10 9-5 4.5V3l5 4.5-10 9"/>',
    "edit": '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
    "open": '<path d="M14 4h6v6"/><path d="M20 4l-9 9"/><path d="M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
    "bell": '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 0 0 4 0"/>',
    "tiles": '<rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="7" height="7" rx="2"/><rect x="13" y="13" width="7" height="7" rx="2"/>',
    "wave": '<path d="M3 12h2M7 8v8M11 5v14M15 8v8M19 11v2"/>',
    "volume": '<path d="M4 9.5v5h3.5L12 18V6L7.5 9.5z"/><path d="M15.5 9a4 4 0 0 1 0 6"/>',
    "shield": '<path d="M12 3l7 3v5.5c0 4.2-3 7.6-7 9.5-4-1.9-7-5.3-7-9.5V6z"/>',
    "wifi": '<path d="M3.5 9.5a12 12 0 0 1 17 0"/><path d="M6.5 12.8a7.6 7.6 0 0 1 11 0"/><circle cx="12" cy="17" r="1.6" fill="currentColor" stroke="none"/>',
    "moon": '<path d="M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z"/>',
    "search": '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
    "lock": '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"/>',
    # listening modes: one "sound field" family
    "m_off": '<circle cx="12" cy="12" r="7.5"/><path d="M5.5 18.5l13-13"/>',
    "m_tr": '<circle cx="12" cy="12" r="2.6" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="8" stroke-dasharray="2.2 2.8"/>',
    "m_ad": '<circle cx="12" cy="12" r="8"/><path d="M12 4a8 8 0 0 1 0 16z" fill="currentColor" stroke="none"/>',
    "m_nc": '<circle cx="12" cy="12" r="4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="8"/>',
}

MODES = [("off", "Off", "m_off"), ("tr", "Transparency", "m_tr"), ("ad", "Adaptive", "m_ad"), ("nc", "Noise Cancellation", "m_nc")]


def ic(name, size=20, sw=2):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
            f'stroke-width="{sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{P[name]}</svg>')


def page(title, w, h, body, props=None, script=None, lang="en"):
    pr = {"$preview": {"width": w, "height": h}}
    if props:
        pr.update(props)
    js = script or "class Component extends DCLogic {\nrenderVals() {\nreturn {};\n}\n}"
    data = json.dumps(pr, ensure_ascii=False).replace("&", "&amp;").replace("'", "&#39;")
    return f'''<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<style>
@font-face{{font-family:"HearOn Sans";src:url({FONT}) format("truetype");font-weight:300 800;font-display:block}}
body{{margin:0;font-family:"HearOn Sans",system-ui,sans-serif}}
button{{font-family:inherit;cursor:pointer}}
a{{color:inherit}}
</style>
</helmet>
{body}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{data}'>
{js}
</script>
</body>
</html>
'''


def caption(t):
    """The system caption bar, painted the header colour (drawn faint: it belongs to the OS)."""
    return (f'<div aria-hidden="true" style="height: 36px; flex-shrink: 0; display: flex; align-items: center; justify-content: space-between; background: {t["chrome"]}">'
            f'<div style="display: flex; align-items: center; gap: 6px; padding-left: 10px; opacity: 0.8"><div style="width: 18px; height: 18px; border-radius: 999px; background: {t["primary"]}"></div>'
            f'<div style="color: {t["faint"]}; display: flex">{ic("down", 16)}</div></div>'
            f'<div style="display: flex; gap: 2px; color: {t["faint"]}; padding-right: 6px">'
            + "".join(f'<div style="width: 36px; height: 32px; display: flex; align-items: center; justify-content: center">{s}</div>'
                      for s in ['<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 12h12"/></svg>',
                                '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="6" y="6" width="12" height="12" rx="1.5"/></svg>',
                                '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M7 7l10 10M17 7L7 17"/></svg>'])
            + '</div></div>')


def iconbtn(t, name, label, size=40, color=None):
    return (f'<button aria-label="{label}" title="{label}" style="width: {size}px; height: {size}px; border: none; border-radius: 999px; background: transparent; '
            f'color: {color or t["text2"]}; display: flex; align-items: center; justify-content: center; padding: 0; flex-shrink: 0">{ic(name, 20)}</button>')


def status_dot(t, text="Connected", on=True):
    c = t["primary"] if on else t["faint"]
    return (f'<span style="display: flex; align-items: center; gap: 6px; font-size: 13px; color: {t["text2"]}; white-space: nowrap">'
            f'<span style="width: 8px; height: 8px; border-radius: 99px; background: {c}"></span>{text}</span>')


def device_switch(t, name="AirPods Pro", status="Connected", on=True, size=15):
    return (f'<button aria-label="Choose AirPods" style="display: flex; align-items: center; gap: 4px; height: 40px; padding: 0 8px 0 12px; border: none; border-radius: 999px; background: transparent; color: {t["text"]}">'
            f'<span style="font-size: {size}px; font-weight: 680; white-space: nowrap">{name}</span><span style="display: flex; color: {t["text2"]}">{ic("down", 18)}</span></button>'
            f'{status_dot(t, status, on)}')


def switch(t, on, label):
    if on:
        return (f'<button role="switch" aria-checked="true" aria-label="{label}" style="width: 52px; height: 32px; flex-shrink: 0; border: none; border-radius: 16px; background: {t["primary"]}; display: flex; align-items: center; justify-content: flex-end; padding: 0 4px; box-sizing: border-box">'
                f'<span style="width: 24px; height: 24px; border-radius: 12px; background: {t["onPrimary"]}; color: {t["primary"]}; display: flex; align-items: center; justify-content: center">{ic("check", 14, 3)}</span></button>')
    return (f'<button role="switch" aria-checked="false" aria-label="{label}" style="width: 52px; height: 32px; flex-shrink: 0; border: 2px solid {t["text3"]}; border-radius: 16px; background: {t["track"]}; display: flex; align-items: center; justify-content: flex-start; padding: 0 6px; box-sizing: border-box">'
            f'<span style="width: 16px; height: 16px; border-radius: 8px; background: {t["text3"]}"></span></button>')


def radius(i, n, big=22, small=6):
    if n == 1:
        return f"{big}px"
    if i == 0:
        return f"{big}px {big}px {small}px {small}px"
    if i == n - 1:
        return f"{small}px {small}px {big}px {big}px"
    return f"{small}px"


def row(t, title, sub=None, trail=None, lead=None, href=False, r="6px", pad="12px 20px", min_h=64, title_size=15.5):
    lead_html = (f'<span style="display: flex; color: {t["text2"]}; flex-shrink: 0">{ic(lead, 22)}</span>' if lead else "")
    sub_html = f'<span style="font-size: 13.5px; line-height: 19px; color: {t["text2"]}">{sub}</span>' if sub else ""
    trail_html = trail or ""
    if href and not trail:
        trail_html = f'<span style="display: flex; color: {t["text2"]}">{ic("right", 20)}</span>'
    inner = (f'{lead_html}<span style="flex-grow: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px">'
             f'<span style="font-size: {title_size}px; line-height: 21px; font-weight: 560; color: {t["text"]}">{title}</span>{sub_html}</span>{trail_html}')
    st = (f'display: flex; align-items: center; gap: 16px; min-height: {min_h}px; padding: {pad}; box-sizing: border-box; '
          f'background: {t["card"]}; border-radius: {r}; text-decoration: none; color: {t["text"]}')
    if href:
        return f'<a href="{href if isinstance(href, str) else "#"}" style="{st}">{inner}</a>'
    return f'<div style="{st}">{inner}</div>'


def group(t, rows, title=None, gap=2):
    """rows: list of dicts for row(); corners follow the Android 16 grouped-list shape."""
    n = len(rows)
    body = "".join(row(t, r=radius(i, n), **rw) for i, rw in enumerate(rows))
    head = (f'<div style="font-size: 14px; font-weight: 650; color: {t["primary"]}; padding: 0 20px 2px">{title}</div>' if title else "")
    return f'<section style="display: flex; flex-direction: column; gap: 8px">{head}<div style="display: flex; flex-direction: column; gap: {gap}px">{body}</div></section>'


def chip(t, label, on):
    if on:
        return (f'<button aria-pressed="true" style="height: 36px; display: flex; align-items: center; gap: 6px; padding: 0 14px 0 10px; border: none; border-radius: 10px; background: {t["sc"]}; color: {t["onSc"]}; font-size: 14px; font-weight: 560">'
                f'{ic("check", 16, 2.5)}{label}</button>')
    return (f'<button aria-pressed="false" style="height: 36px; display: flex; align-items: center; padding: 0 14px; border: 1px solid {t["line"]}; border-radius: 10px; background: transparent; color: {t["text2"]}; font-size: 14px; font-weight: 520">{label}</button>')


def pods_svg(t, w=250, case_open=True, in_case=False, dim=False):
    """Our own simple drawing of two earbuds and a case (no product renders)."""
    body = "#FFFFFF" if t is LIGHT else "#E9EEED"
    edge = "#BCC8C5" if t is LIGHT else "#8E9B98"
    mesh = "#D7E0DE" if t is LIGHT else "#B7C3C0"
    op = ' opacity="0.55"' if dim else ""
    def pod(cx, flip):
        s = -1 if flip else 1
        return (f'<g transform="translate({cx} 18) scale({s} 1) rotate(-10)">'
                f'<rect x="-8" y="26" width="16" height="64" rx="8" fill="{body}" stroke="{edge}" stroke-width="1.5"/>'
                f'<path d="M-2 88h4" stroke="{edge}" stroke-width="1.5" stroke-linecap="round"/>'
                f'<ellipse cx="0" cy="22" rx="21" ry="22" fill="{body}" stroke="{edge}" stroke-width="1.5"/>'
                f'<ellipse cx="-9" cy="18" rx="7.5" ry="8.5" fill="{mesh}"/></g>')
    case = (f'<rect x="{w/2-58}" y="70" width="116" height="92" rx="30" fill="{body}" stroke="{edge}" stroke-width="1.5"/>'
            f'<path d="M{w/2-57} 98h114" stroke="{edge}" stroke-width="1.5"/>'
            f'<circle cx="{w/2}" cy="124" r="3.2" fill="{t["ok"]}"/>')
    pods = "" if in_case else pod(w/2 - 92, False) + pod(w/2 + 92, True)
    return f'<svg width="{w}" height="170" viewBox="0 0 {w} 170" aria-hidden="true"{op}>{case}{pods}</svg>'


def meter(t, pct, w=None, h=8):
    col = t["no"] if pct <= 20 else t["primary"]
    return (f'<span style="display: block; height: {h}px; border-radius: 99px; background: {t["track"]}; overflow: hidden{"; width: " + str(w) + "px" if w else ""}">'
            f'<span style="display: block; height: {h}px; width: {pct}%; border-radius: 99px; background: {col}"></span></span>')


def battery_trio(t, vals=(("Left", 100, False, "In ear"), ("Case", 72, True, "Lid open"), ("Right", 100, False, "In ear")), big=26):
    cells = ""
    for label, pct, charging, note in vals:
        bolt = f'<span style="display: flex; color: {t["primary"]}">{ic("bolt", 16)}</span>' if charging else ""
        val = (f'<span style="display: flex; align-items: center; gap: 2px; font-size: {big}px; line-height: 32px; font-weight: 720; color: {t["text"]}; font-feature-settings: \'tnum\'">{pct}%{bolt}</span>'
               if pct is not None else f'<span style="font-size: {big}px; line-height: 32px; font-weight: 600; color: {t["faint"]}">–</span>')
        cells += (f'<div style="flex: 1 1 0; min-width: 0; display: flex; flex-direction: column; gap: 6px">'
                  f'<span style="font-size: 13px; font-weight: 600; color: {t["text2"]}">{label}</span>{val}'
                  f'{meter(t, pct or 0)}<span style="font-size: 12.5px; color: {t["text2"]}">{note}</span></div>')
    return f'<div style="display: flex; gap: 18px">{cells}</div>'


def mode_group(t, sel="nc", h=76, label=True, holes=False, font=12.5):
    """M3 Expressive connected button group. holes=True binds styles to renderVals() (interactive)."""
    out = ""
    for i, (key, name, icon) in enumerate(MODES):
        if holes:
            style = (f'flex: 1 1 0; min-width: 0; height: {h}px; border: none; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; padding: 0 4px; '
                     f'background: {{{{m_{key}.bg}}}}; color: {{{{m_{key}.fg}}}}; border-radius: {{{{m_{key}.r}}}}; font-size: {font}px; line-height: 15px; font-weight: 600; text-align: center')
            out += f'<button aria-pressed="{{{{m_{key}.pressed}}}}" onClick="{{{{m_{key}.pick}}}}" style="{style}">{ic(icon, 22)}{name}</button>'
        else:
            on = key == sel
            r = "999px" if on else ("999px 10px 10px 999px" if i == 0 else "10px 999px 999px 10px" if i == 3 else "10px")
            bg, fg = (t["primary"], t["onPrimary"]) if on else (t["sc"], t["onSc"])
            out += (f'<button aria-pressed="{"true" if on else "false"}" style="flex: 1 1 0; min-width: 0; height: {h}px; border: none; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; padding: 0 4px; '
                    f'background: {bg}; color: {fg}; border-radius: {r}; font-size: {font}px; line-height: 15px; font-weight: 600; text-align: center">{ic(icon, 22)}{name if label else ""}</button>')
    return f'<div role="group" aria-label="Listening mode" style="display: flex; gap: 3px">{out}</div>'


def mode_script(default="nc", t=LIGHT):
    return ("class Component extends DCLogic {\n"
            f"state = {{ mode: '{default}' }};\n"
            "renderVals() {\n"
            f"const on = {{ bg: '{t['primary']}', fg: '{t['onPrimary']}' }}, off = {{ bg: '{t['sc']}', fg: '{t['onSc']}' }};\n"
            "const keys = ['off', 'tr', 'ad', 'nc'];\n"
            "const edge = ['999px 10px 10px 999px', '10px', '10px', '10px 999px 999px 10px'];\n"
            "const out = {};\n"
            "keys.forEach((k, i) => {\n"
            "  const sel = this.state.mode === k;\n"
            "  out['m_' + k] = { bg: sel ? on.bg : off.bg, fg: sel ? on.fg : off.fg, r: sel ? '999px' : edge[i], pressed: sel ? 'true' : 'false', pick: () => this.setState({ mode: k }) };\n"
            "});\n"
            "out.isAdaptive = this.state.mode === 'ad';\n"
            "out.modeName = { off: 'Off', tr: 'Transparency', ad: 'Adaptive', nc: 'Noise Cancellation' }[this.state.mode];\n"
            "return out;\n}\n}")


def slider(t, pct, left, right, label):
    return (f'<div style="display: flex; flex-direction: column; gap: 8px">'
            f'<input type="range" aria-label="{label}" value="{pct}" min="0" max="100" style="width: 100%; accent-color: {t["primary"]}; margin: 0">'
            f'<div style="display: flex; justify-content: space-between; font-size: 12.5px; color: {t["text2"]}"><span>{left}</span><span>{right}</span></div></div>')


def tonal_btn(t, label, icon=None, href=None, filled=False, h=44):
    bg, fg = (t["primary"], t["onPrimary"]) if filled else (t["pc"], t["onPc"])
    inner = (ic(icon, 18) if icon else "") + label
    st = f'height: {h}px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 0 20px; border: none; border-radius: 999px; background: {bg}; color: {fg}; font-size: 14.5px; font-weight: 620; text-decoration: none; flex-shrink: 0'
    if href:
        return f'<a href="{href}" style="{st}">{inner}</a>'
    return f'<button style="{st}">{inner}</button>'


def text_btn(t, label, color=None):
    return f'<button style="height: 40px; padding: 0 14px; border: none; border-radius: 999px; background: transparent; color: {color or t["primary"]}; font-size: 14.5px; font-weight: 620">{label}</button>'
