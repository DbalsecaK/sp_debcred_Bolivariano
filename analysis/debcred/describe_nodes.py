#!/usr/bin/env python
"""Packets for, and merge of, per-node descriptions in analysis/debcred/topology.json.

  python describe_nodes.py packets            -> writes packets/<id>.txt (one per leaf)
  python describe_nodes.py merge <json file>  -> {"<id>": "<paragraph>", ...} merged into topology.json,
                                                 after checking that every number and identifier-like
                                                 token of a paragraph occurs in that node's packet.
A block's packet is up to 150 source lines of its range (blank lines removed) plus its map
connections; an external procedure's packet is its call sites; a table's packet is its
connections only. No file is written by any subagent: they return text, this script merges.
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SRC = ROOT / "legacy" / "debcred" / "sp_debcred_empresa.sp"
TOPO = HERE / "topology.json"
PK = HERE / "packets"


def leaves(topo):
    for dom in topo["root"]["children"]:
        for leaf in dom["children"]:
            yield dom["name"], leaf


def connections(topo, nid):
    names = {l["id"]: l["name"] for _, l in leaves(topo)}
    out = []
    for e in topo["edges"]:
        if e["source"] == nid:
            out.append(f"  -> {e['kind']} {names[e['target']]}" + (f" (line {e['line']})" if e.get("line") else ""))
        elif e["target"] == nid:
            out.append(f"  <- {e['kind']} from {names[e['source']]}" + (f" (line {e['line']})" if e.get("line") else ""))
    return out


def packets():
    topo = json.loads(TOPO.read_text(encoding="utf-8"))
    raw = SRC.read_text(encoding="utf-8").splitlines()
    PK.mkdir(exist_ok=True)
    for dom, leaf in leaves(topo):
        lines = [f"NODE {leaf['id']}: {leaf['name']}  [{leaf['kind']}, domain: {dom}]"]
        if leaf["kind"] == "module" and leaf.get("lines"):
            a, b = map(int, leaf["lines"].split("-"))
            src = [f"{i}: {raw[i-1].rstrip()}" for i in range(a, b + 1) if raw[i-1].strip()]
            lines.append(f"SOURCE legacy/debcred/sp_debcred_empresa.sp lines {a}-{b} ({len(src)} non-blank; first 150 shown):")
            lines += src[:150]
        elif leaf["kind"] == "module":
            lines.append(f"External procedure, not in the tree: {leaf.get('file')}. Call sites:")
            for e in topo["edges"]:
                if e["target"] == leaf["id"] and e.get("line"):
                    i = e["line"]
                    ctx = [f"{j}: {raw[j-1].rstrip()}" for j in range(i, min(i + 40, len(raw)) + 1) if raw[j-1].strip()][:18]
                    lines.append(f"-- called from {e['source']} at line {i}:")
                    lines += ctx
        else:
            lines.append("Data store (table), no DDL in the tree.")
        lines.append("CONNECTIONS:")
        lines += connections(topo, leaf["id"]) or ["  (none)"]
        (PK / f"{leaf['id'].replace(':', '_').replace('..', '_')}.txt").write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {len(list(leaves(topo)))} packets to {PK}")


TOKEN = re.compile(r"[A-Za-z_@#][A-Za-z0-9_.]*|\d+")
STOP = set("""a an and the of to in on for by from with as is are was were be been it its this that these those or not no
into then when if else than at one two three four five six seven eight nine ten first second third all any each
every both either neither same other another only also both still yet so such very own via per while after before
before during until whether where which who whom whose what how why can may might must shall should will would
does did do done has have had having being sets set reads read writes write calls call called caller callers
returns return returned records record recorded debit debits credit credits commission commissions order orders
account accounts company companies customer notification notifications procedure procedures table tables block
blocks line lines error errors code codes value values state stored money movement movements payment payments
transaction transactions savepoint rollback commit commits committed rolled back exit exits path paths branch
branches check checks lookup lookups service services channel channels session parameter parameters input inputs
output outputs variable variables result results row rows update updates updated history live fallback falls
falling flag flags type types amount amounts inside outside within between across through against without
because since again later earlier already never always usually mostly partly partial full fully external internal
tree none nothing something anything data store stores read-only unqualified qualified name names named naming
form forms header status total totals process processing processed number numbers key keys note notes step steps
means meaning used uses use using given passes passed passing keeps kept keep left leaves leaving over under
i ii iii""".split())


def merge(path):
    topo = json.loads(TOPO.read_text(encoding="utf-8"))
    descs = json.loads(Path(path).read_text(encoding="utf-8"))
    ok, bad = 0, {}
    for dom, leaf in leaves(topo):
        d = descs.get(leaf["id"])
        if not d:
            continue
        pkt = (PK / f"{leaf['id'].replace(':', '_').replace('..', '_')}.txt").read_text(encoding="utf-8").lower()
        # The system's own name was given to every subagent in its prompt, not in the packet.
        pkt += " dbo.sp_debcred_empresa sp_debcred_empresa"
        missing = []
        for tok in TOKEN.findall(d):
            t = tok.lower().strip(".")
            if t in STOP or len(t) < 3 and not t.isdigit():
                continue
            looks_ident = t.isdigit() or "_" in t or "@" in t or "." in t or t != t.lower() or re.match(r"^[a-z]+\d+$", t)
            if looks_ident and t not in pkt and t.replace("..", ".") not in pkt:
                missing.append(tok)
        words = len(d.split())
        if missing or not (55 <= words <= 90):
            bad[leaf["id"]] = {"words": words, "missing": missing}
        else:
            leaf["description"] = d
            ok += 1
    TOPO.write_text(json.dumps(topo, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"merged {ok} descriptions; rejected {len(bad)}")
    for k, v in bad.items():
        print(f"  {k}: words={v['words']} missing={v['missing']}")


if __name__ == "__main__":
    {"packets": packets, "merge": lambda: merge(sys.argv[2])}[sys.argv[1]]()
