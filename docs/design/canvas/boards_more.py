import math
from kit import *
from boards import board, desktop, device_pane, settings_column, group, chips_row, CYCLE, OUT

NOTES = {}


def note(id, x, y, text, kind=None, w=300, maxW=None, color=None, size=None):
    n = {"x": x, "y": y, "text": text}
    if kind:
        n["kind"] = kind
    else:
        n["w"] = w
    if maxW:
        n["maxW"] = maxW
    if color:
        n["fill"] = color
    if size:
        n["size"] = size
    NOTES[id] = n


def blob(n, amp, R=100, cx=0, cy=0, steps=180):
    """M3 Expressive-style shape: a circle with n soft lobes (cookie / clover)."""
    pts = []
    for i in range(steps):
        a = 2 * math.pi * i / steps
        r = R * (1 + amp * math.cos(n * a))
        pts.append(f"{cx + r * math.sin(a):.1f} {cy - r * math.cos(a):.1f}")
    return "M" + " L".join(pts) + " Z"


def trace(kind, w=300, h=64, color="#000"):
    """Polyline of a motion trace: 'nod' wiggles, 'flat' barely moves, 'shake' swings."""
    pts = []
    for i in range(61):
        x = w * i / 60
        p = i / 60
        if kind == "nod":
            y = h / 2 + (h * 0.38 * math.sin(p * 2 * math.pi * 2.2) * math.exp(-((p - 0.62) ** 2) / 0.03) if p > 0.35 else 0) + 1.5 * math.sin(p * 17)
        elif kind == "shake":
            y = h / 2 + (h * 0.4 * math.sin(p * 2 * math.pi * 3) * math.exp(-((p - 0.6) ** 2) / 0.025) if p > 0.3 else 0) + 1.5 * math.sin(p * 13)
        else:
            y = h / 2 + 2.2 * math.sin(p * 9) + 1.2 * math.sin(p * 23)
        pts.append(f"{x:.1f},{y:.1f}")
    return (f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" aria-hidden="true"><path d="M0 {h/2}H{w}" stroke="{color}" stroke-opacity="0.25" stroke-dasharray="3 4"/>'
            f'<polyline points="{" ".join(pts)}" fill="none" stroke="{color}" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>')


def result_shape(t, kind, size=360):
    if kind == "yes":
        path, fill, fg, icon, word = blob(9, 0.055, 100), t["pc"], t["onPc"], "check", "Yes"
    elif kind == "no":
        path, fill, fg, icon, word = blob(4, 0.13, 96), t["noC"], t["onNoC"], "close", "No"
    else:
        path, fill, fg, icon, word = blob(1, 0, 100), t["card2"], t["text2"], "head", ""
    inner = (f'<div style="display: flex; flex-direction: column; align-items: center; gap: 4px; color: {fg}">{ic(icon, int(size * 0.26), 2.4)}'
             f'<span style="font-size: {int(size * 0.2)}px; line-height: 1; font-weight: 760; letter-spacing: -0.02em">{word}</span></div>') if word else \
            (f'<div style="display: flex; flex-direction: column; align-items: center; gap: 10px; color: {fg}">{ic("head", int(size * 0.3), 1.6)}</div>')
    ring = "" if word else f'<circle cx="0" cy="0" r="116" fill="none" stroke="{t["primary"]}" stroke-width="3" stroke-dasharray="4 10" stroke-linecap="round"/>'
    return (f'<div style="position: relative; width: {size}px; height: {size}px; display: flex; align-items: center; justify-content: center">'
            f'<svg width="{size}" height="{size}" viewBox="-125 -125 250 250" aria-hidden="true" style="position: absolute; left: 0; top: 0">{ring}<path d="{path}" fill="{fill}"/></svg>'
            f'<div style="position: relative">{inner}</div></div>')


DEMO_JS = """class Component extends DCLogic {
state = { phase: null, yes: 3, no: 1 };
renderVals() {
const phase = this.state.phase ?? this.props.state ?? '%s';
const next = { listen: 'yes', yes: 'no', no: 'listen' }[phase];
return {
  isListen: phase === 'listen', isYes: phase === 'yes', isNo: phase === 'no',
  yes: this.state.yes, no: this.state.no,
  cycle: () => this.setState({ phase: next, yes: this.state.yes + (next === 'yes' ? 1 : 0), no: this.state.no + (next === 'no' ? 1 : 0) }),
};
}
}"""
DEMO_PROPS = lambda d: {"state": {"editor": "enum", "options": ["listen", "yes", "no"], "default": d}}


def stage(t, size):
    cap = lambda s: f'<div style="font-size: 17px; line-height: 24px; color: {t["text2"]}; text-align: center">{s}</div>'
    return (f'<button aria-label="Simulate the next gesture" onClick="{{{{cycle}}}}" style="border: none; background: transparent; padding: 0; display: flex; flex-direction: column; align-items: center; gap: 18px; color: inherit">'
            f'<sc-if value="{{{{isListen}}}}" hint-placeholder-val="{{{{false}}}}">{result_shape(t, "listen", size)}{cap("Nod or shake your head")}</sc-if>'
            f'<sc-if value="{{{{isYes}}}}" hint-placeholder-val="{{{{true}}}}">{result_shape(t, "yes", size)}{cap("Nod detected")}</sc-if>'
            f'<sc-if value="{{{{isNo}}}}" hint-placeholder-val="{{{{false}}}}">{result_shape(t, "no", size)}{cap("Shake detected")}</sc-if>'
            f'</button>')


def motion_card(t, w=300, h=56):
    lab = lambda s: f'<div style="font-size: 13px; font-weight: 600; color: {t["text2"]}">{s}</div>'
    tr = lambda k1, k2: (f'<div style="display: flex; flex-direction: column; gap: 4px">{lab("Up and down")}{trace(k1, w, h, t["primary"])}</div>'
                         f'<div style="display: flex; flex-direction: column; gap: 4px">{lab("Left and right")}{trace(k2, w, h, t["no"])}</div>')
    return (f'<div style="display: flex; flex-direction: column; gap: 12px; padding: 18px 20px; background: {t["card"]}; border-radius: 22px">'
            f'<div style="font-size: 15.5px; font-weight: 600">Live head motion</div>'
            f'<sc-if value="{{{{isListen}}}}" hint-placeholder-val="{{{{false}}}}">{tr("flat", "flat")}</sc-if>'
            f'<sc-if value="{{{{isYes}}}}" hint-placeholder-val="{{{{true}}}}">{tr("nod", "flat")}</sc-if>'
            f'<sc-if value="{{{{isNo}}}}" hint-placeholder-val="{{{{false}}}}">{tr("flat", "shake")}</sc-if></div>')


def tally(t):
    cell = lambda word, hole, bg, fg: (f'<div style="flex: 1 1 0; display: flex; flex-direction: column; gap: 2px; padding: 14px 18px; border-radius: 22px; background: {bg}; color: {fg}">'
                                       f'<span style="font-size: 13px; font-weight: 600">{word}</span><span style="font-size: 34px; line-height: 40px; font-weight: 760; font-feature-settings: \'tnum\'">{{{{{hole}}}}}</span></div>')
    return f'<div style="display: flex; gap: 8px">{cell("Nods", "yes", t["pc"], t["onPc"])}{cell("Shakes", "no", t["noC"], t["onNoC"])}</div>'


def segmented(t, items, sel, h=40):
    n = len(items)
    out = ""
    for i, k in enumerate(items):
        on = k == sel
        r = "999px" if on else ("999px 10px 10px 999px" if i == 0 else "10px 999px 999px 10px" if i == n - 1 else "10px")
        out += (f'<button aria-pressed="{"true" if on else "false"}" style="flex: 1 1 0; height: {h}px; border: none; border-radius: {r}; background: {t["primary"] if on else t["sc"]}; '
                f'color: {t["onPrimary"] if on else t["onSc"]}; font-size: 14px; font-weight: 600">{k}</button>')
    return f'<div role="group" style="display: flex; gap: 3px">{out}</div>'


# ---------------------------------------------------------------- head-gesture demo, desktop
t = LIGHT
hdr = (iconbtn(t, "back", "Back") + '<div style="font-size: 15px; font-weight: 680; padding-left: 4px">Try head gestures</div>'
       + '<div style="flex-grow: 1"></div>' + status_dot(t, "Reading head motion") + '<div style="width: 12px"></div>' + text_btn(t, "Done"))
side = (f'<aside style="width: 380px; flex-shrink: 0; background: {t["chrome"]}; display: flex; flex-direction: column; gap: 14px; padding: 12px 24px 24px; box-sizing: border-box">'
        + motion_card(t, 332) + tally(t)
        + f'<div style="display: flex; flex-direction: column; gap: 10px; padding: 18px 20px; background: {t["card"]}; border-radius: 22px"><div style="font-size: 15.5px; font-weight: 600">Sensitivity</div>{segmented(t, ["Gentle", "Normal", "Firm"], "Normal")}</div>'
        + f'<div style="font-size: 13px; line-height: 19px; color: {t["text2"]}; padding: 0 6px">Head motion stops when you leave this screen.</div></aside>')
board("Demo.dc.html", "Googlebook · head-gesture demo (click the shape)", 0, 1400, 1280, 800, page(
    "HearOn Link head-gesture demo", 1280, 800,
    desktop(t, hdr, f'<main style="flex-grow: 1; display: flex; align-items: center; justify-content: center">{stage(t, 400)}</main>' + side),
    props=DEMO_PROPS("yes"), script=DEMO_JS % "yes"), interactive=True)

# ---------------------------------------------------------------- phones
PH = 844


def phone(t, top, body, bg=None):
    return (f'<div style="width: 390px; height: {PH}px; display: flex; flex-direction: column; background: {bg or t["page"]}; color: {t["text"]}; overflow: hidden">'
            f'<div style="height: 64px; flex-shrink: 0; display: flex; align-items: center; gap: 4px; padding: 0 4px 0 6px">{top}</div>'
            f'<div style="flex-grow: 1; min-height: 0; overflow: hidden; display: flex; flex-direction: column; gap: 20px; padding: 4px 16px 24px">{body}</div></div>')


def phone_top_home(t, status="Connected", on=True):
    return (f'<div style="flex-grow: 1; display: flex; flex-direction: column; align-items: flex-start; padding-left: 2px">'
            f'<button aria-label="Choose AirPods" style="display: flex; align-items: center; gap: 2px; height: 32px; padding: 0 6px 0 8px; border: none; border-radius: 999px; background: transparent; color: {t["text"]}">'
            f'<span style="font-size: 20px; font-weight: 700">AirPods Pro</span><span style="display: flex; color: {t["text2"]}">{ic("down", 20)}</span></button>'
            f'<div style="padding-left: 8px">{status_dot(t, status, on)}</div></div>'
            + iconbtn(t, "tune", "HearOn Link settings", 48) + iconbtn(t, "more", "More", 48))


NAV = [dict(title="Noise control", sub="Press and hold, one-AirPod mode", lead="m_nc", href="PhonePage.dc.html"),
       dict(title="Head gestures", sub="On · Nod to answer calls, shake to decline", lead="head", href="PhoneDemo.dc.html"),
       dict(title="Ear detection", sub="Pause when you take one out", lead="ear", href=True),
       dict(title="Press and hold", sub="Both AirPods: noise control", lead="hold", href=True),
       dict(title="About these AirPods", sub="Name, model, firmware", lead="info", href=True)]

t = LIGHT
home_body = (f'<div style="display: flex; justify-content: center">{pods_svg(t, 240)}</div>'
             + battery_trio(t, big=24)
             + f'<div style="display: flex; flex-direction: column; gap: 10px">{mode_group(t, holes=True, h=76, font=11.5)}'
             + f'<sc-if value="{{{{isAdaptive}}}}" hint-placeholder-val="{{{{false}}}}"><div style="padding: 4px 4px 0">{slider(t, 50, "Less noise", "More noise", "Adaptive audio level")}</div></sc-if></div>'
             + group(t, [dict(title="Conversation awareness", trail=switch(t, True, "Conversation awareness")),
                         dict(title="Personalized volume", trail=switch(t, False, "Personalized volume"))])
             + group(t, NAV))
board("PhoneHome.dc.html", "Phone · home (tap the modes)", 0, 2800, 390, PH, page(
    "HearOn Link on a phone", 390, PH, phone(t, phone_top_home(t), home_body), script=mode_script("nc")), interactive=True)

sub_top = lambda t, title: (iconbtn(t, "back", "Back", 48) + f'<div style="flex-grow: 1; font-size: 20px; font-weight: 700; padding-left: 2px">{title}</div>' + iconbtn(t, "more", "More", 48))
check = lambda t, on: (f'<span role="checkbox" aria-checked="{"true" if on else "false"}" style="width: 22px; height: 22px; flex-shrink: 0; box-sizing: border-box; border-radius: 6px; display: flex; align-items: center; justify-content: center; '
                       + (f'background: {t["primary"]}; color: {t["onPrimary"]}">{ic("check", 16, 3)}' if on else f'border: 2px solid {t["text3"]}">') + "</span>")
page_body = (f'<div style="font-size: 15px; line-height: 22px; color: {t["text2"]}; padding: 0 4px">Press and hold a stem to switch between the modes you tick. Pick at least two.</div>'
             + group(t, [dict(title=name, lead=icon, trail=check(t, on)) for (key, name, icon), (_, on) in zip(MODES, CYCLE)], "Press and hold switches between")
             + group(t, [dict(title="Noise Cancellation with one AirPod", trail=switch(t, False, "Noise Cancellation with one AirPod")),
                         dict(title="Adaptive audio", sub=slider(t, 50, "Less noise", "More noise", "Adaptive audio level"), min_h=96, pad="14px 20px")], "More"))
board("PhonePage.dc.html", "Phone · Noise control page", 470, 2800, 390, PH, page(
    "HearOn Link noise control page", 390, PH, phone(t, sub_top(t, "Noise control"), page_body)))

t = DARK
demo_body = (f'<div style="display: flex; justify-content: center; padding-top: 6px">{stage(t, 300)}</div>'
             + tally(t) + motion_card(t, 318, 44))
board("PhoneDemo.dc.html", "Phone · head-gesture demo (dark)", 940, 2800, 390, PH, page(
    "HearOn Link head-gesture demo on a phone", 390, PH,
    phone(t, iconbtn(t, "back", "Back", 48) + f'<div style="flex-grow: 1; font-size: 20px; font-weight: 700; padding-left: 2px">Try head gestures</div>' + text_btn(t, "Done"), demo_body),
    props=DEMO_PROPS("no"), script=DEMO_JS % "no"), interactive=True)

t = LIGHT
away_body = (f'<div style="display: flex; justify-content: center">{pods_svg(t, 240, in_case=True)}</div>'
             + f'<div style="display: flex; flex-direction: column; gap: 6px; opacity: 0.6">{battery_trio(t, (("Left", 80, False, "12:40"), ("Case", 55, False, "12:40"), ("Right", 78, False, "12:40")), big=24)}</div>'
             + f'<div style="display: flex; flex-direction: column; gap: 14px; padding: 20px; background: {t["card"]}; border-radius: 22px">'
               f'<div style="font-size: 17px; line-height: 24px; font-weight: 650">Not connected</div>'
               f'<div style="font-size: 14.5px; line-height: 21px; color: {t["text2"]}">Open the case near your phone, or connect in Bluetooth settings. Battery levels are from 12:40.</div>'
               f'<div style="display: flex">{tonal_btn(t, "Bluetooth settings", "open")}</div></div>'
             + f'<div style="opacity: 0.45">{group(t, NAV[:3])}</div>')
board("PhoneAway.dc.html", "Phone · not connected", 1410, 2800, 390, PH, page(
    "HearOn Link when the AirPods are away", 390, PH, phone(t, phone_top_home(t, "Not connected", False), away_body)))

# ---------------------------------------------------------------- Quick Settings: tile + panel
t = LIGHT


def tile(t, icon, label, sub, state="on", w=196):
    if state == "on":
        bg, fg, sfg, ibg = t["primary"], t["onPrimary"], t["onPrimary"], "rgba(255,255,255,0.18)"
    elif state == "off":
        bg, fg, sfg, ibg = t["card"], t["text"], t["text2"], t["card2"]
    else:
        bg, fg, sfg, ibg = t["card2"], t["faint"], t["faint"], "transparent"
    return (f'<button aria-pressed="{"true" if state == "on" else "false"}" style="width: {w}px; height: 72px; flex-shrink: 0; border: none; border-radius: 22px; background: {bg}; color: {fg}; display: flex; align-items: center; gap: 12px; padding: 0 14px; box-sizing: border-box; text-align: left">'
            f'<span style="width: 40px; height: 40px; border-radius: 14px; background: {ibg}; display: flex; align-items: center; justify-content: center; flex-shrink: 0">{ic(icon, 22)}</span>'
            f'<span style="display: flex; flex-direction: column; gap: 1px; min-width: 0"><span style="font-size: 14.5px; font-weight: 640; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{label}</span>'
            f'<span style="font-size: 12.5px; color: {sfg}; white-space: nowrap; overflow: hidden; text-overflow: ellipsis">{sub}</span></span></button>')


qs_panel = (f'<div style="width: 440px; display: flex; flex-direction: column; gap: 14px; padding: 18px; box-sizing: border-box; background: {t["chrome"]}; border-radius: 28px">'
            f'<div style="font-size: 13px; font-weight: 650; color: {t["text2"]}; padding: 0 4px">Quick Settings</div>'
            f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px">'
            + tile(t, "wifi", "Internet", "Home", "on", 198) + tile(t, "bt", "Bluetooth", "AirPods Pro", "on", 198)
            + tile(t, "m_nc", "Noise Cancellation", "AirPods Pro · 100%", "on", 198) + tile(t, "moon", "Do Not Disturb", "Off", "off", 198)
            + "</div></div>")
states = (f'<div style="display: flex; flex-direction: column; gap: 10px">'
          f'<div style="font-size: 13px; font-weight: 650; color: {t["text2"]}">The HearOn Link tile</div>'
          + tile(t, "m_nc", "Noise Cancellation", "AirPods Pro · 100%", "on", 230)
          + tile(t, "m_tr", "Transparency", "AirPods Pro · 100%", "on", 230)
          + tile(t, "m_ad", "Adaptive", "AirPods Pro · 100%", "on", 230)
          + tile(t, "m_off", "Listening mode", "Off", "off", 230)
          + tile(t, "m_nc", "AirPods", "Not connected", "na", 230) + "</div>")
panel = (f'<div role="dialog" aria-label="AirPods Pro" style="width: 400px; display: flex; flex-direction: column; gap: 18px; padding: 24px; box-sizing: border-box; background: {t["page"]}; border-radius: 28px; box-shadow: 0 8px 28px rgba(0,40,40,0.16)">'
         f'<div style="display: flex; align-items: center; justify-content: space-between"><div style="display: flex; flex-direction: column; gap: 2px"><div style="font-size: 22px; font-weight: 720">AirPods Pro</div>{status_dot(t)}</div>'
         f'</div>'
         + battery_trio(t, (("Left", 100, False, "In ear"), ("Case", 72, True, "Charging"), ("Right", 100, False, "In ear")), big=20)
         + mode_group(t, "nc", h=68, font=11)
         + group(t, [dict(title="Conversation awareness", trail=switch(t, True, "Conversation awareness"), min_h=56)])
         + f'<div style="display: flex; justify-content: space-between">{text_btn(t, "Open HearOn Link")}{tonal_btn(t, "Done", h=40)}</div></div>')
board("QS.dc.html", "Quick Settings tile and its panel", 1360, 1400, 1280, 800, page(
    "HearOn Link in Quick Settings", 1280, 800,
    f'<div style="width: 1280px; height: 800px; display: flex; align-items: flex-start; gap: 48px; padding: 56px; box-sizing: border-box; background: {t["page"]}; color: {t["text"]}">'
    + qs_panel + states + panel + "</div>"))

# ---------------------------------------------------------------- notifications
def notif(t, title, text, actions, sub="HearOn Link · AirPods Pro", w=460, extra=""):
    acts = "".join(f'<button style="height: 36px; padding: 0 12px; border: none; border-radius: 999px; background: {t["sc"] if i == 0 else "transparent"}; color: {t["onSc"] if i == 0 else t["primary"]}; font-size: 13.5px; font-weight: 620">{a}</button>' for i, a in enumerate(actions))
    return (f'<article style="width: {w}px; display: flex; flex-direction: column; gap: 10px; padding: 16px 18px 12px; box-sizing: border-box; background: {t["card"]}; border-radius: 24px; box-shadow: 0 1px 3px rgba(0,40,40,0.10)">'
            f'<div style="display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: {t["text2"]}"><span style="width: 22px; height: 22px; border-radius: 99px; background: {t["primary"]}; color: {t["onPrimary"]}; display: flex; align-items: center; justify-content: center">{ic("m_nc", 14)}</span>{sub}</div>'
            f'<div style="display: flex; flex-direction: column; gap: 3px"><div style="font-size: 15.5px; font-weight: 650">{title}</div><div style="font-size: 14px; line-height: 20px; color: {t["text2"]}">{text}</div></div>'
            f'{extra}<div style="display: flex; gap: 6px">{acts}</div></article>')


mini_meters = lambda t: ('<div style="display: flex; gap: 14px">' + "".join(
    f'<div style="flex: 1 1 0; display: flex; flex-direction: column; gap: 5px"><span style="font-size: 12.5px; color: {t["text2"]}">{l} · <b style="color: {t["text"]}; font-weight: 650">{v}%</b></span>{meter(t, v, h=6)}</div>'
    for l, v in (("Left", 100), ("Right", 100), ("Case", 72))) + "</div>")
nt = (f'<div style="width: 1280px; height: 560px; display: flex; gap: 40px; padding: 56px; box-sizing: border-box; background: {t["chrome"]}; color: {t["text"]}">'
      + '<div style="display: flex; flex-direction: column; gap: 14px">'
      + f'<div style="font-size: 13px; font-weight: 650; color: {t["text2"]}">While connected (silent, can be hidden)</div>'
      + notif(t, "Noise Cancellation", "", ["Transparency", "Adaptive", "Off"], extra=mini_meters(t))
      + f'<div style="font-size: 13px; font-weight: 650; color: {t["text2"]}; padding-top: 12px">Low battery</div>'
      + notif(t, "Left AirPod at 10%", "Charge soon. The right one is at 42%.", ["Dismiss"]) + "</div>"
      + '<div style="display: flex; flex-direction: column; gap: 14px">'
      + f'<div style="font-size: 13px; font-weight: 650; color: {t["text2"]}">Case opened nearby, not connected</div>'
      + notif(t, "Your AirPods Pro are nearby", "Left 98% · Right 97% · Case 64%", ["Connect", "Not now"], extra="") + "</div></div>")
board("Notify.dc.html", "Notifications", 2720, 1400, 1280, 560, page("HearOn Link notifications", 1280, 560, nt))

# ---------------------------------------------------------------- first run (desktop)
def step(t, n, title, text, state, action=""):
    if state == "done":
        dot = f'<span style="width: 32px; height: 32px; border-radius: 99px; background: {t["primary"]}; color: {t["onPrimary"]}; display: flex; align-items: center; justify-content: center; flex-shrink: 0">{ic("check", 18, 2.6)}</span>'
    elif state == "now":
        dot = f'<span style="width: 32px; height: 32px; border-radius: 12px; background: {t["pc"]}; color: {t["onPc"]}; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 15px; font-weight: 720">{n}</span>'
    else:
        dot = f'<span style="width: 32px; height: 32px; border-radius: 99px; border: 2px solid {t["line"]}; box-sizing: border-box; color: {t["text2"]}; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 14px; font-weight: 650">{n}</span>'
    body = (f'<div style="display: flex; flex-direction: column; gap: 4px; flex-grow: 1"><div style="font-size: 16px; line-height: 32px; font-weight: {"680" if state == "now" else "560"}; color: {t["text"] if state != "later" else t["text2"]}">{title}</div>'
            + (f'<div style="font-size: 14.5px; line-height: 21px; color: {t["text2"]}">{text}</div>' if text else "") + (f'<div style="display: flex; gap: 10px; padding-top: 10px">{action}</div>' if action else "") + "</div>")
    bg = f'background: {t["card"]}; border-radius: 22px; padding: 18px 20px' if state == "now" else "padding: 6px 20px"
    return f'<div style="display: flex; gap: 16px; {bg}">{dot}{body}</div>'


t = LIGHT
ob = (f'<div style="width: 560px; display: flex; flex-direction: column; gap: 10px">'
      f'<div style="display: flex; justify-content: center">{pods_svg(t, 230)}</div>'
      f'<div style="font-size: 30px; line-height: 36px; font-weight: 740; text-align: center; letter-spacing: -0.01em">Set up your AirPods</div>'
      f'<div style="font-size: 15.5px; line-height: 23px; color: {t["text2"]}; text-align: center; padding-bottom: 12px">A few quick steps. Nothing leaves this Googlebook.</div>'
      + step(t, 1, "Allow Nearby devices", "", "done")
      + step(t, 2, "Choose your AirPods", "Pick them from the system list. HearOn Link talks only to the AirPods you choose.", "now", tonal_btn(t, "Choose AirPods", "bt", filled=True))
      + step(t, 3, "Add the Quick Settings tile", "", "later")
      + step(t, 4, "Battery notification", "", "later")
      + f'<div style="display: flex; justify-content: center; padding-top: 8px">{text_btn(t, "Skip for now", t["text2"])}</div></div>')
board("Onboarding.dc.html", "First run", 2720, 0, 1280, 800, page(
    "HearOn Link first run", 1280, 800,
    f'<div style="width: 1280px; height: 800px; display: flex; flex-direction: column; background: {t["page"]}; color: {t["text"]}; overflow: hidden">'
    + caption(t) + f'<main style="flex-grow: 1; display: flex; justify-content: center; padding-top: 16px">{ob}</main></div>'))

note("t-desk", -1360, -300, "HearOn Link on a Googlebook", kind="title1", maxW=5360)
note("t-os", 0, 1100, "Head gestures and the OS", kind="title1", maxW=4000)
note("t-phone", 0, 2500, "On a phone", kind="title1", maxW=1800)
note("n-qs", 2720, 2010, "Tile tap opens the panel (the subpage). Long-press opens HearOn Link. A setting can make a tap cycle the press-and-hold modes instead.", w=340, color="teal")
note("n-demo", 3100, 2010, "Click the shape in Play to step through listening → Yes → No. The Tweaks tab has the same states.", w=360, color="teal")

# ---------------------------------------------------------------- spec board
t = LIGHT
Y, N, P = "yes", "", "part"
MODELS = ["AirPods 2", "AirPods 3", "AirPods 4", "AirPods 4 ANC", "Pro", "Pro 2", "Pro 3", "Max"]
FEATS = [
    ("Battery: left, right, case", [Y] * 8),
    ("Ear detection", [Y, Y, Y, Y, Y, Y, Y, N]),
    ("Listening modes", [N, N, N, Y, P, Y, Y, P]),
    ("Adaptive audio", [N, N, N, Y, N, Y, Y, N]),
    ("Conversation awareness", [N, N, N, Y, N, Y, Y, N]),
    ("Personalized volume", [N, N, Y, Y, N, Y, Y, N]),
    ("Head gestures", [N, Y, Y, Y, N, Y, Y, N]),
    ("Swipe for volume", [N, N, N, N, N, Y, Y, N]),
    ("Hearing protection", [N, N, N, N, N, N, Y, N]),
]


def cell(v):
    if v == Y:
        return f'<td style="padding: 8px 6px; text-align: center; color: {t["primary"]}"><span style="display: inline-flex" aria-label="yes">{ic("check", 18, 2.6)}</span></td>'
    if v == P:
        return f'<td style="padding: 8px 6px; text-align: center; font-size: 12px; color: {t["text2"]}">no Adaptive</td>'
    return f'<td style="padding: 8px 6px; text-align: center; color: {t["faint"]}" aria-label="no">–</td>'


table = (f'<table style="border-collapse: collapse; width: 100%; font-size: 13.5px; background: {t["card"]}; border-radius: 18px; overflow: hidden">'
         f'<thead><tr><th style="text-align: left; padding: 10px 14px; font-size: 12px; color: {t["text2"]}; font-weight: 650">Shown when the AirPods report it</th>'
         + "".join(f'<th style="padding: 10px 6px; font-size: 12px; color: {t["text2"]}; font-weight: 650">{m}</th>' for m in MODELS) + "</tr></thead><tbody>"
         + "".join(f'<tr style="border-top: 1px solid {t["line"]}"><td style="padding: 8px 14px; font-weight: 560">{f}</td>' + "".join(cell(v) for v in vals) + "</tr>" for f, vals in FEATS)
         + "</tbody></table>")


def principle(title, text):
    return (f'<div style="display: flex; flex-direction: column; gap: 4px"><div style="font-size: 17px; font-weight: 700">{title}</div>'
            f'<div style="font-size: 14.5px; line-height: 21px; color: {t["text2"]}">{text}</div></div>')


def swatch(hexv, name, fg="#FFFFFF"):
    return (f'<div style="display: flex; flex-direction: column; gap: 6px; width: 104px"><div style="height: 56px; border-radius: 16px; background: {hexv}; border: 1px solid {t["line"]}"></div>'
            f'<div style="font-size: 12.5px; line-height: 16px"><b style="font-weight: 650">{name}</b><br><span style="color: {t["text2"]}">{hexv}</span></div></div>')


def app_icon(bg, fg, size=132):
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 108 108" aria-label="HearOn Link icon draft" role="img">'
            f'<rect width="108" height="108" rx="30" fill="{bg}"/>'
            f'<g fill="none" stroke="{fg}" stroke-width="9" stroke-linecap="round" transform="rotate(-38 54 54)">'
            f'<rect x="20" y="40" width="46" height="28" rx="14"/><rect x="42" y="40" width="46" height="28" rx="14"/></g>'
            f'<g transform="rotate(-38 54 54)"><circle cx="34" cy="54" r="5.5" fill="{fg}"/><circle cx="74" cy="54" r="5.5" fill="{fg}"/></g></svg>')


spec = (f'<div style="width: 1280px; height: 1000px; box-sizing: border-box; padding: 48px 56px; display: flex; flex-direction: column; gap: 34px; background: {t["page"]}; color: {t["text"]}">'
        f'<div style="display: flex; align-items: flex-end; justify-content: space-between; gap: 40px">'
        f'<div style="display: flex; flex-direction: column; gap: 8px"><div style="font-size: 13px; font-weight: 700; letter-spacing: 0.08em; color: {t["primary"]}">DESIGN SPEC · DRAFT 1</div>'
        f'<div style="font-size: 46px; line-height: 50px; font-weight: 780; letter-spacing: -0.02em">HearOn Link</div>'
        f'<div style="font-size: 17px; line-height: 25px; color: {t["text2"]}; max-width: 640px">A calm companion for AirPods on Googlebooks and phones. One window, one tile, one notification. Material 3 Expressive where it counts: the listening-mode buttons and the Yes / No moment.</div></div>'
        f'<div style="display: flex; gap: 14px">{app_icon(t["primary"], "#FFFFFF")}{app_icon("#E8F0EE", t["primary"])}{app_icon("#0F1514", DARK["primary"])}</div></div>'
        f'<div style="display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 28px">'
        + principle("Quiet", "Nothing floats over the window. Feedback happens in place: a switch flips, a mode fills.")
        + principle("Honest", "Only what your AirPods report. AirPods 4 see a shorter page than Pro 3; root-only features never show.")
        + principle("Native", "Header under the system caption, Quick Settings tile, a silent notification, system pickers for devices and permissions.")
        + principle("Light touch", "No overlays, no accessibility service, no internet. Head motion only while you look at the demo or a call rings.")
        + "</div>"
        f'<div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 40px">'
        f'<div style="display: flex; flex-direction: column; gap: 14px"><div style="font-size: 14px; font-weight: 650; color: {t["primary"]}">Colour (wallpaper colours win when the device offers them)</div>'
        f'<div style="display: flex; gap: 12px; flex-wrap: wrap">{swatch(t["primary"], "Teal")}{swatch(t["pc"], "Teal container")}{swatch(t["sc"], "Button")}{swatch(t["noC"], "No")}{swatch(DARK["primary"], "Teal, dark")}</div></div>'
        f'<div style="display: flex; flex-direction: column; gap: 14px"><div style="font-size: 14px; font-weight: 650; color: {t["primary"]}">Type and shape</div>'
        f'<div style="display: flex; gap: 22px; align-items: center"><div style="font-size: 40px; line-height: 44px; font-weight: 760">Aa</div>'
        f'<div style="font-size: 14px; line-height: 21px; color: {t["text2"]}">Google Sans Flex (the system font). 26 / 720 headlines, 15.5 / 560 rows, 13.5 support text. Pills for actions, 22 px grouped cards, connected button groups, scalloped and four-lobed shapes for Yes and No.</div>'
        f'<div style="display: flex; gap: 8px; flex-shrink: 0"><svg width="54" height="54" viewBox="-125 -125 250 250" aria-hidden="true"><path d="{blob(9, 0.055, 100)}" fill="{t["pc"]}"/></svg><svg width="54" height="54" viewBox="-125 -125 250 250" aria-hidden="true"><path d="{blob(4, 0.13, 96)}" fill="{t["noC"]}"/></svg></div></div></div></div>'
        f'<div style="display: flex; flex-direction: column; gap: 12px"><div style="font-size: 14px; font-weight: 650; color: {t["primary"]}">Per model (from the AirPods\' own capability list; models per LibrePods\' research)</div>{table}</div>'
        + "</div>")
board("Spec.dc.html", "Spec: principles, look, icon, per-model features", -1360, 0, 1280, 1000, page("HearOn Link design spec", 1280, 1000, spec))
