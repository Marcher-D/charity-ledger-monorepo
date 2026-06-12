#!/usr/bin/env python3
"""Transaction soak test: hammer the stack with a realistic mix of money
actions from many users for a fixed duration, logging every submission so the
post-run audit can reconcile web balances against exact arithmetic.

Each action is chosen to be valid against the user's LIVE balance (fetched
right before acting), mirroring what real users can do through the UI.

Usage:
  python3 scripts/soak_test.py --tokens /tmp/soak_tokens.txt \
      --campaigns /tmp/soak_campaigns.txt --minutes 70 --pace 1.5 \
      --log /tmp/soak_log.csv

tokens file: one JWT per line. campaigns file: one campaign UUID per line.
"""
import argparse
import csv
import json
import random
import sys
import time
import urllib.request
import urllib.error

BASE = "http://localhost:3000"


def api(path, token=None, body=None, timeout=20):
    req = urllib.request.Request(BASE + path)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    if data:
        req.method = "POST"
    try:
        with urllib.request.urlopen(req, data=data, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            detail = e.read().decode()[:120]
        except Exception:
            detail = ""
        return e.code, {"error": detail}
    except Exception as e:
        return 0, {"error": str(e)[:120]}


def pick_action(profile, campaigns):
    avail = float(profile.get("availableBalance") or 0)
    staked = float(profile.get("stakedBalance") or 0)
    if avail < 20:
        return ("DEPOSIT", round(random.uniform(50, 150), 2), None)
    r = random.random()
    if r < 0.35:
        return ("DONATE", round(random.uniform(1, 10), 2), random.choice(campaigns))
    if r < 0.55:
        return ("DEPOSIT", round(random.uniform(20, 80), 2), None)
    if r < 0.75:
        return ("WITHDRAW", round(random.uniform(1, min(10, avail)), 2), None)
    if r < 0.90:
        return ("STAKE", round(random.uniform(5, min(20, avail)), 2), None)
    if staked >= 1:
        return ("UNSTAKE", round(random.uniform(1, min(10, staked)), 2), None)
    return ("DEPOSIT", round(random.uniform(20, 80), 2), None)


def submit(token, action, amount, campaign_id):
    paths = {
        "DEPOSIT": "/api/v1/transactions/deposit",
        "DONATE": "/api/v1/transactions/donate",
        "WITHDRAW": "/api/v1/transactions/withdraw",
        "STAKE": "/api/v1/transactions/stake",
        "UNSTAKE": "/api/v1/transactions/unstake",
    }
    body = {"amount": amount}
    if action == "DONATE":
        body["campaignId"] = campaign_id
        body["note"] = "soak"
    return api(paths[action], token=token, body=body)


def login(email, password):
    code, resp = api("/api/v1/auth/login", body={"email": email, "password": password})
    return resp.get("accessToken") if code == 200 else None


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--credentials", required=True,
                   help="file with one email per line; all share --password")
    p.add_argument("--password", default="Soak1234!")
    p.add_argument("--campaigns", required=True)
    p.add_argument("--minutes", type=float, default=70)
    p.add_argument("--pace", type=float, default=1.5)
    p.add_argument("--log", default="/tmp/soak_log.csv")
    args = p.parse_args()

    emails = [l.strip() for l in open(args.credentials) if l.strip()]
    campaigns = [l.strip() for l in open(args.campaigns) if l.strip()]
    # JWTs expire after 1h — login lazily and re-login on 401 so a long soak
    # never silently degrades into a loop of unauthorized no-ops.
    tokens = {}
    deadline = time.time() + args.minutes * 60
    counts, errors = {}, 0

    with open(args.log, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["ts", "user_idx", "action", "amount", "campaign", "http", "txid"])
        f.flush()
        n = 0
        while time.time() < deadline:
            idx = random.randrange(len(emails))
            if idx not in tokens:
                tokens[idx] = login(emails[idx], args.password)
            token = tokens[idx]
            if not token:
                tokens.pop(idx, None)
                errors += 1
                time.sleep(args.pace)
                continue
            code, profile = api("/api/v1/users/me", token=token)
            if code == 401:
                tokens.pop(idx, None)  # expired — re-login next time we pick them
                continue
            if code != 200:
                errors += 1
                if errors % 50 == 0:
                    print(f"[soak] {errors} errors so far (latest: profile fetch {code})", flush=True)
                time.sleep(args.pace)
                continue
            action, amount, camp = pick_action(profile, campaigns)
            code, resp = submit(token, action, amount, camp)
            txid = resp.get("txId", "")
            w.writerow([int(time.time()), idx, action, amount, camp or "", code, txid])
            if code in (200, 201):
                counts[action] = counts.get(action, 0) + 1
            else:
                errors += 1
                if code == 401:
                    tokens.pop(idx, None)
            n += 1
            if n % 100 == 0:
                f.flush()
                print(f"[soak] {n} submitted  ok={sum(counts.values())}  err={errors}  {counts}", flush=True)
            time.sleep(args.pace)

    print(f"[soak] DONE: {sum(counts.values())} accepted, {errors} rejected/errors")
    print(f"[soak] mix: {counts}")


if __name__ == "__main__":
    sys.exit(main())
