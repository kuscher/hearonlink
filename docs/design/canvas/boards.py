import json, pathlib, datetime
from kit import *

OUT = pathlib.Path(__file__).parent / "project"
OUT.mkdir(exist_ok=True)
BOARDS = {}


def board(name, title, x, y, w, h, html, interactive=False):
    (OUT / name).write_text(html)
    BOARDS[name] = dict(x=x, y=y, w=w, h=h, title=title, **({"is_interactive": True} if interactive else {}))


def chips_row(t, items):
    return '<span style="display: flex; flex-wrap: wrap; gap: 8px; padding-top: 8px">' + "".join(chip(t, l, on) for l, on in items) + "</span>"


CYCLE = [("Off", False), ("Transparency", True), ("Adaptive", True), ("Noise Cancellation", True)]


def settings_column(t, width=680, demo_href="Demo.dc.html"):
    return (f'<div style="width: {width}px; display: flex; flex-direction: column; gap: 26px">'
            + group(t, [
                dict(title="Press and hold switches between", sub=chips_row(t, CYCLE), min_h=96, pad="16px 20px"),
                dict(title="Noise Cancellation with one AirPod", sub="Keep noise control on when you wear only one", trail=switch(t, False, "Noise Cancellation with one AirPod")),
            ], "Noise control")
            + group(t, [
                dict(title="Head gestures", sub="Nod to accept a call, shake your head to decline", trail=switch(t, True, "Head gestures"), lead="head"),
                dict(title="Try it", sub="See your nods and shakes live", lead="play", href=demo_href),
            ], "Head gestures")
            + group(t, [
                dict(title="Pause when you take an AirPod out", sub="Plays again when you put it back in", trail=switch(t, True, "Automatic ear detection"), lead="ear"),
            ], "Ear detection")
            + group(t, [
                dict(title="Left AirPod", sub="Noise control", lead="hold", href=True),
                dict(title="Right AirPod", sub="Noise control", lead="hold", href=True),
                dict(title="Swipe the stem to change volume", trail=switch(t, True, "Swipe to change volume"), lead="volume"),
            ], "Press and hold")
            + "</div>")


def device_pane(t, width=452, sel="tr", holes=False, show_adaptive=None):
    adaptive = ""
    if show_adaptive:
        adaptive = (f'<sc-if value="{{{{isAdaptive}}}}" hint-placeholder-val="{{{{false}}}}"><div style="padding: 4px 4px 0">'
                    + slider(t, 50, "Less noise", "More noise", "Adaptive audio level") + "</div></sc-if>")
    return (f'<aside aria-label="AirPods" style="width: {width}px; flex-shrink: 0; background: {t["chrome"]}; display: flex; flex-direction: column; gap: 22px; padding: 4px 28px 28px; box-sizing: border-box">'
            f'<div style="display: flex; justify-content: center; padding-top: 6px">{pods_svg(t)}</div>'
            + battery_trio(t)
            + f'<div style="display: flex; flex-direction: column; gap: 10px"><div style="font-size: 14px; font-weight: 650; color: {t["text2"]}">Listening mode</div>'
            + mode_group(t, sel, holes=holes) + adaptive + "</div>"
            + group(t, [
                dict(title="Conversation awareness", sub="Turns media down when you speak", trail=switch(t, True, "Conversation awareness")),
                dict(title="Personalized volume", sub="Adjusts to your surroundings", trail=switch(t, False, "Personalized volume")),
            ])
            + "</aside>")


def desktop(t, header, body, w=1280, h=800):
    return (f'<div style="width: {w}px; height: {h}px; display: flex; flex-direction: column; background: {t["page"]}; color: {t["text"]}; overflow: hidden">'
            + caption(t)
            + f'<div style="height: 48px; flex-shrink: 0; display: flex; align-items: center; gap: 8px; background: {t["chrome"]}; padding: 0 8px">{header}</div>'
            + f'<div style="flex-grow: 1; display: flex; min-height: 0">{body}</div></div>')


def home_header(t):
    return (device_switch(t) + '<div style="flex-grow: 1"></div>'
            + iconbtn(t, "tune", "PodLink settings") + iconbtn(t, "more", "More"))


# ---------------------------------------------------------------- desktop home
t = LIGHT
board("Main.dc.html", "Googlebook · home (light)", 0, 0, 1280, 800, page(
    "PodLink on a Googlebook", 1280, 800,
    desktop(t, home_header(t),
            device_pane(t, sel="tr", holes=True, show_adaptive=True)
            + f'<main style="flex-grow: 1; min-width: 0; overflow: hidden; display: flex; justify-content: center; padding: 20px 40px">{settings_column(t)}</main>'),
    script=mode_script("tr")), interactive=True)

# ---------------------------------------------------------------- desktop, dark, subpage open
t = DARK
sub_header = (iconbtn(t, "back", "Back") + f'<div style="font-size: 15px; font-weight: 680; padding-left: 4px">Head gestures</div>'
              + '<div style="flex-grow: 1"></div>' + device_switch(t) + iconbtn(t, "more", "More"))
gest_page = (f'<div style="width: 680px; display: flex; flex-direction: column; gap: 26px">'
             + f'<div style="display: flex; align-items: center; gap: 24px; padding: 8px 4px 0">'
               f'<div style="width: 96px; height: 96px; flex-shrink: 0; border-radius: 32px; background: {t["pc"]}; color: {t["onPc"]}; display: flex; align-items: center; justify-content: center">{ic("head", 48, 1.8)}</div>'
               f'<div style="display: flex; flex-direction: column; gap: 6px"><div style="font-size: 26px; line-height: 32px; font-weight: 720">Answer with a nod</div>'
               f'<div style="font-size: 15px; line-height: 22px; color: {t["text2"]}">When a call rings, nod to accept it or shake your head to decline. PodLink listens to head motion only while a call rings or the demo is open.</div></div></div>'
             + group(t, [
                 dict(title="Head gestures", trail=switch(t, True, "Head gestures")),
                 dict(title="Nod", sub="Accept the call", lead="check"),
                 dict(title="Shake", sub="Decline the call", lead="close"),
             ])
             + group(t, [
                 dict(title="Sensitivity", sub='<span style="display: flex; gap: 3px; padding-top: 10px">'
                      + "".join(f'<button aria-pressed="{"true" if k == "Normal" else "false"}" style="flex: 1 1 0; height: 40px; border: none; border-radius: {"999px" if k == "Normal" else r}; background: {t["primary"] if k == "Normal" else t["sc"]}; color: {t["onPrimary"] if k == "Normal" else t["onSc"]}; font-size: 14px; font-weight: 600">{k}</button>'
                                for k, r in [("Gentle", "999px 10px 10px 999px"), ("Normal", "10px"), ("Firm", "10px 999px 999px 10px")]) + "</span>",
                      min_h=100, pad="16px 20px"),
             ])
             + f'<div style="display: flex; gap: 12px">{tonal_btn(t, "Try it", "play", href="Demo.dc.html", filled=True)}</div>'
             + "</div>")
board("Detail.dc.html", "Googlebook · a subpage (dark)", 1360, 0, 1280, 800, page(
    "PodLink head gestures page, dark", 1280, 800,
    desktop(t, sub_header,
            device_pane(t, sel="nc")
            + f'<main style="flex-grow: 1; min-width: 0; overflow: hidden; display: flex; justify-content: center; padding: 20px 40px">{gest_page}</main>')))

