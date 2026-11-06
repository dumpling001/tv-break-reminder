#!/usr/bin/env python3
"""手机网页控制器：在 Mac 上运行，手机浏览器打开 http://<Mac的IP>:8765 控制看电视休息提醒。
只用 Python 标准库。用法：caffeinate -i python3 tvbreak_server.py
"""
import json
import os
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
BOX = "192.168.x.x:5555"  # 盒子的 IP:端口，IP 变了要改这里
PKG = "com.example.tvbreak"  # App 包名
PORT = 8765

state = {"running": False, "interval": 20, "next": 0.0, "in_break": False}
lock = threading.Lock()


def adb(*args):
    try:
        r = subprocess.run([ADB, *args], capture_output=True, text=True, timeout=20)
        return r.stdout.strip()
    except Exception:
        return ""


def run_break():
    adb("connect", BOX)
    adb("-s", BOX, "shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(10)
    for _ in range(120):  # 最多等 10 分钟，App 退出后系统会很快清掉进程
        if not adb("-s", BOX, "shell", "pidof", PKG):
            break
        time.sleep(5)
    with lock:
        state["in_break"] = False
        if state["running"]:
            state["next"] = time.time() + state["interval"] * 60


def trigger_break():
    with lock:
        if state["in_break"]:
            return
        state["in_break"] = True
    threading.Thread(target=run_break, daemon=True).start()


def worker():
    while True:
        time.sleep(1)
        with lock:
            due = state["running"] and not state["in_break"] and time.time() >= state["next"]
        if due:
            trigger_break()


PAGE = """<!doctype html>
<html lang="zh"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>看电视休息</title>
<style>
body{font-family:sans-serif;max-width:420px;margin:0 auto;padding:20px;text-align:center}
#t{font-size:56px;margin:20px 0}
button{display:block;width:100%;font-size:22px;padding:16px;margin:12px 0;border:0;border-radius:12px;background:#2e7d32;color:#fff}
button.r{background:#c62828}
button.g{background:#546e7a}
input{font-size:22px;width:80px;text-align:center}
</style></head><body>
<h2>看电视休息</h2>
<div id="s">...</div>
<div id="t">--:--</div>
<p>间隔 <input id="m" type="number" value="20" min="1" max="180"> 分钟</p>
<button onclick="go('start')">开始</button>
<button class="r" onclick="go('stop')">停止</button>
<button class="g" onclick="go('breaknow')">现在就休息</button>
<button class="g" onclick="go('endbreak')">结束休息</button>
<script>
async function go(a){
  await fetch('/'+a+'?min='+document.getElementById('m').value,{method:'POST'});
  tick();
}
async function tick(){
  var s=document.getElementById('s'),t=document.getElementById('t');
  try{
    var r=await (await fetch('/status')).json();
    s.textContent=r.in_break?'休息中':(r.running?'运行中':'未运行');
    if(r.remaining==null){t.textContent='--:--';}
    else{var n=Math.round(r.remaining);t.textContent=String(Math.floor(n/60)).padStart(2,'0')+':'+String(n%60).padStart(2,'0');}
  }catch(e){s.textContent='连不上电脑';t.textContent='--:--';}
}
setInterval(tick,1000);tick();
</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def reply(self, body, ctype):
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", ctype + "; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/status"):
            with lock:
                remaining = None
                if state["running"] and not state["in_break"]:
                    remaining = max(0, state["next"] - time.time())
                body = json.dumps({"running": state["running"],
                                   "in_break": state["in_break"],
                                   "remaining": remaining})
            self.reply(body, "application/json")
        else:
            self.reply(PAGE, "text/html")

    def do_POST(self):
        url = urlparse(self.path)
        action = url.path.strip("/")
        try:
            minutes = int(parse_qs(url.query).get("min", ["20"])[0])
        except ValueError:
            minutes = 20
        minutes = max(1, min(minutes, 180))
        if action == "start":
            with lock:
                state.update(running=True, interval=minutes, next=time.time() + minutes * 60)
        elif action == "stop":
            with lock:
                state["running"] = False
        elif action == "breaknow":
            trigger_break()
        elif action == "endbreak":
            # 等同遥控器按 3 次确认键（keycode 23）
            adb("-s", BOX, "shell", "input", "keyevent", "23", "23", "23")
        self.reply("{}", "application/json")


if __name__ == "__main__":
    threading.Thread(target=worker, daemon=True).start()
    print("控制页面：http://<Mac的IP>:%d （Ctrl+C 停止）" % PORT)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
