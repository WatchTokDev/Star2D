#!/usr/bin/env python3
"""Builds a game from a Star2D PROJECT folder on a CI machine (GitHub Actions). Mirrors what "Export APK" does on the phone.

  python3 tools/ci/make_game.py zip --project . --out build/project.zip
  python3 tools/ci/make_game.py apk --project . --out build/game.apk [--name "My Game"] [--package com.me.game]
                                    [--version-name 1.0] [--version-code 1]
  python3 tools/ci/make_game.py jar --project . --out build/game.jar      (dex/scenes.dex -> plain jar, needs dex2jar on PATH as d2j-dex2jar)

The scenes are NOT compiled here: run the game once in the editor (it writes dex/scenes.dex) and commit that file.
Needs: python3 + pyzipper (pip install pyzipper), Java 17, Android build-tools (zipalign, apksigner) and the kxml2 jar for the manifest.
"""
import argparse, json, os, re, shutil, subprocess, sys, tempfile, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "app/src/main/assets")
PASSWORD = b"tulsgskdiensl626__Xxmoishs"
SKIP_TOP = {"Events", "java", "scripts", "log", "logs", ".git", ".github", "build", ".engine"}
KXML = "https://repo1.maven.org/maven2/net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0.jar"


def project_zip(project, out):
    import pyzipper
    project = os.path.abspath(project)
    root = os.path.basename(project) or "game"
    with pyzipper.AESZipFile(out, "w", compression=pyzipper.ZIP_STORED, encryption=pyzipper.WZ_AES) as z:
        z.setpassword(PASSWORD)
        z.setencryption(pyzipper.WZ_AES, nbits=256)
        for d, dirs, files in os.walk(project):
            rel = os.path.relpath(d, project)
            top = rel.split(os.sep)[0]
            if top in SKIP_TOP:
                dirs[:] = []
                continue
            dirs[:] = [x for x in dirs if not (rel == "." and x in SKIP_TOP)]
            for f in files:
                p = os.path.join(d, f)
                z.write(p, os.path.join(root, os.path.relpath(p, project)))
    return out


def orientation(project):
    try:
        cfg = os.path.join(project, "configs")
        for n in sorted(os.listdir(cfg)):
            if n.endswith(".json") and not n.startswith("__"):
                o = json.load(open(os.path.join(cfg, n))).get("or", "")
                return "1" if o == "portrait" else "0"
    except Exception:
        pass
    return "0"


def run(*a, **k):
    print("+", " ".join(a), flush=True)
    subprocess.check_call(list(a), **k)


def tool(name):
    p = shutil.which(name)
    if p:
        return p
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "/usr/local/lib/android/sdk"
    bt = os.path.join(sdk, "build-tools")
    if os.path.isdir(bt):
        for v in sorted(os.listdir(bt), reverse=True):
            c = os.path.join(bt, v, name)
            if os.path.exists(c):
                return c
    sys.exit("missing tool: " + name)


def build_apk(a):
    project = os.path.abspath(a.project)
    work = tempfile.mkdtemp(prefix="s2game")
    t = os.path.join(work, "t")
    zipfile.ZipFile(os.path.join(ASSETS, "files/template.zip")).extractall(t)
    pkg = a.package or "com.star4droid." + re.sub(r"[^a-z0-9]", "", os.path.basename(project).lower() or "game")
    if not re.match(r"^[a-z]", pkg.split(".")[-1]):
        pkg += "x"
    name = a.name or os.path.basename(project) or "Game"
    m = open(os.path.join(ASSETS, "java/manifest.xml"), encoding="utf-8").read()
    for i, v in enumerate([a.version_code, a.version_name, pkg, name, orientation(project)], 1):
        m = m.replace("%%%d$s" % i, str(v))
    open(os.path.join(work, "manifest.xml"), "w", encoding="utf-8").write(m)
    jar = os.path.join(work, "kxml2.jar")
    run("curl", "-sSfL", "-o", jar, KXML)
    run("java", "-cp", os.path.join(ROOT, "app/libs/lib_7.jar") + os.pathsep + jar,
        os.path.join(ROOT, "tools/ci/EncodeManifest.java"), os.path.join(work, "manifest.xml"), os.path.join(t, "AndroidManifest.xml"))
    os.makedirs(os.path.join(t, "assets"), exist_ok=True)
    project_zip(project, os.path.join(t, "assets/project.zip"))
    open(os.path.join(t, "assets/version"), "w").write(str(abs(hash(pkg + str(os.times()))) % 9999999))
    for d in ("icon",):
        folder = os.path.join(project, d)
        if os.path.isdir(folder):
            for f in sorted(os.listdir(folder)):
                if f.lower().endswith((".png", ".jpg", ".jpeg")):
                    os.makedirs(os.path.join(t, "res/drawable"), exist_ok=True)
                    shutil.copy(os.path.join(folder, f), os.path.join(t, "res/drawable/icon.jpg"))
                    break
    raw = os.path.join(work, "raw.apk")
    with zipfile.ZipFile(raw, "w", zipfile.ZIP_DEFLATED) as z:
        for d, _, files in os.walk(t):
            for f in files:
                p = os.path.join(d, f)
                rel = os.path.relpath(p, t).replace(os.sep, "/")
                # resources.arsc must be stored (uncompressed) and 4-byte aligned for Android 11+
                z.write(p, rel, compress_type=zipfile.ZIP_STORED if rel.endswith((".arsc", ".so", "project.zip")) else zipfile.ZIP_DEFLATED)
    aligned = os.path.join(work, "aligned.apk")
    run(tool("zipalign"), "-f", "-p", "4", raw, aligned)
    ks = os.path.join(work, "game.jks")
    if a.keystore and os.path.isfile(a.keystore):
        ks, pw, alias = a.keystore, a.store_pass, a.alias
    else:
        pw, alias = "android", "game"
        run("keytool", "-genkeypair", "-keystore", ks, "-storepass", pw, "-keypass", pw, "-alias", alias,
            "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Star2D Game")
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    run(tool("apksigner"), "sign", "--ks", ks, "--ks-pass", "pass:" + pw, "--ks-key-alias", alias, "--out", a.out, aligned)
    run(tool("apksigner"), "verify", a.out)
    print("APK:", a.out, "package", pkg)


def build_jar(a):
    dex = os.path.join(a.project, "dex/scenes.dex")
    if not os.path.isfile(dex):
        sys.exit("dex/scenes.dex not found: run the game once in the Star2D editor and commit dex/scenes.dex")
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    run("d2j-dex2jar.sh" if shutil.which("d2j-dex2jar.sh") else "d2j-dex2jar", "-f", "-o", a.out, dex)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["zip", "apk", "jar"])
    ap.add_argument("--project", default=".")
    ap.add_argument("--out", required=True)
    ap.add_argument("--name", default="")
    ap.add_argument("--package", default="")
    ap.add_argument("--version-name", default="1.0")
    ap.add_argument("--version-code", default="1")
    ap.add_argument("--keystore", default="")
    ap.add_argument("--store-pass", default="")
    ap.add_argument("--alias", default="")
    a = ap.parse_args()
    if a.cmd == "zip":
        os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
        project_zip(a.project, a.out)
    elif a.cmd == "apk":
        build_apk(a)
    else:
        build_jar(a)


if __name__ == "__main__":
    main()
