"""Checks the live web site after a deploy (docs/ on main, built by GitHub Pages a minute or two after a push).

usage: python tools/check_site.py [--wait]

For every page tools/gen_site.py writes: the address answers, its <title> is the page's, it has a meta description and the social
preview picture (og:image); the sitemap lists them all, robots.txt and the picture answer. --wait looks again every 15 s for up
to 5 minutes, for a push that Pages has not built yet. Needs the network, so it is not among the tests. Exit status 1 on a problem.
"""
import re
import sys
import time
import urllib.error
import urllib.request

import gen_site


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "pairfree-site-check"}), timeout=30) as r:
            return r.status, r.read().decode("utf-8", "replace") if "text" in r.headers.get("Content-Type", "") or "xml" in r.headers.get("Content-Type", "") else ""
    except urllib.error.HTTPError as e:
        return e.code, ""


def problems():
    out = []
    pages = {rel: text for rel, text in gen_site.build().items() if rel != "README.md"}
    addresses = []
    for rel, text in pages.items():
        front = text.split("---")[1]
        title = re.search(r'^title: "(.+)"$', front, re.M).group(1)
        permalink = re.search(r"^permalink: (\S+)$", front, re.M).group(1)
        url = gen_site.SITE + permalink.rstrip("/") + "/" if permalink != "/" else gen_site.SITE + "/"
        addresses.append(url)
        status, html = get(url)
        if status != 200:
            out.append(f"{url}: HTTP {status}")
            continue
        if f"<title>{title}" not in html.replace("&amp;", "&"):
            out.append(f"{url}: its <title> is not '{title}' (not rebuilt yet?)")
        if 'name="description"' not in html:
            out.append(f"{url}: no meta description")
        if 'property="og:image"' not in html:
            out.append(f"{url}: no og:image")
    status, sitemap = get(gen_site.SITE + "/sitemap.xml")
    if status != 200:
        out.append(f"sitemap.xml: HTTP {status}")
    else:
        for url in addresses:
            if f"<loc>{url}</loc>" not in sitemap:
                out.append(f"sitemap.xml does not list {url}")
    for path in ("robots.txt", "assets/social-preview.png"):
        status, _ = get(f"{gen_site.SITE}/{path}")
        if status != 200:
            out.append(f"{path}: HTTP {status}")
    return out


def main():
    deadline = time.time() + (300 if "--wait" in sys.argv else 0)
    while True:
        found = problems()
        if not found or time.time() >= deadline:
            break
        time.sleep(15)
    for line in found:
        print("PROBLEM:", line)
    print("the live site is as the repository says" if not found else f"{len(found)} problems")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
