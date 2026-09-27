#!/usr/bin/env python
"""Topology of debcred: dbo.sp_debcred_empresa (one Sybase/COBIS T-SQL procedure).

Reads legacy/debcred/sp_debcred_empresa.sp and writes analysis/debcred/topology.json.
The procedure is one program, so its leaves are the functional blocks found by the
assessment (line ranges verified against the source on 2026-09-27), the ten external
procedures it calls (not in the tree) and the tables it touches (no DDL in the tree).
Edges are extracted from the source by regex within each block's range: `exec`/`execute`
targets -> call; `from`/`join` tables -> read; `update` tables -> write. Comment lines
(`--`, `/* ... */`) are stripped before matching so disabled code adds no edge.

Rerun:  python analysis/debcred/extract_topology.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "legacy" / "debcred" / "sp_debcred_empresa.sp"
OUT = Path(__file__).resolve().parent / "topology.json"
FILE = "sp_debcred_empresa.sp"

# Functional blocks: (id, name, start, end, domain). Ranges are 1-based, inclusive.
BLOCKS = [
    ("B0", "B0 Signature and declarations", 1, 164, "pre"),
    ("B1", "B1 Init and commission normalization", 168, 272, "pre"),
    ("B2", "B2 Concept resolution", 276, 314, "pre"),
    ("B3", "B3 Accounting configuration", 318, 396, "pre"),
    ("B4", "B4 begin tran + savepoint", 402, 408, "tx"),
    ("B5", "B5 Reference string and special causal", 414, 616, "tx"),
    ("B6", "B6 Debit note by account type", 624, 740, "tx"),
    ("B7", "B7 Notifications", 746, 1256, "tx"),
    ("B8", "B8 Accounting account (type 9)", 1260, 1322, "tx"),
    ("B9", "B9 Debit outcome and movement record", 1332, 1492, "tx"),
    ("B10", "B10 Commission debit", 1500, 1732, "tx"),
    ("B11", "B11 Second SWIFT commission", 1742, 1856, "tx"),
    ("B12", "B12 Order-state update", 1876, 2090, "tx"),
    ("B13", "B13 Commented ROLPAGO update", 2098, 2124, "tx"),
    ("B14", "B14 Success exit", 2130, 2136, "tx"),
    ("B15", "B15 lbl_error exit", 2140, 2170, "err"),
]
DOMAINS = {
    "pre": "Pre-transaction",
    "tx": "Inside the transaction",
    "err": "Error exit",
    "procs": "External procedures (Sybase, not in tree)",
    "data": "Data stores (Sybase, no DDL in tree)",
}
# Control flow between blocks (verified against the source: gotos, returns, guards).
FLOW = [
    ("B0", "B1"), ("B1", "B2"), ("B2", "B3"), ("B3", "B4"), ("B3", "B15"),
    ("B4", "B5"), ("B4", "B8"), ("B5", "B6"), ("B5", "B15"), ("B6", "B7"),
    ("B7", "B9"), ("B8", "B9"), ("B9", "B14"), ("B9", "B15"), ("B9", "B10"),
    ("B10", "B11"), ("B11", "B12"), ("B11", "B15"), ("B12", "B14"), ("B12", "B15"),
]
# Unqualified names resolve in the procedure's own database (inferred: db_biz_pagos).
DEFAULT_DB = "db_biz_pagos"
CATALOG_TABLES = {"ba_tabla", "ba_catalogo"}


def strip_comments(lines):
    out, in_block = [], False
    for ln in lines:
        s = ln
        if in_block:
            if "*/" in s:
                s = s[s.index("*/") + 2:]
                in_block = False
            else:
                out.append("")
                continue
        while "/*" in s:
            a = s.index("/*")
            if "*/" in s[a:]:
                b = s.index("*/", a) + 2
                s = s[:a] + " " + s[b:]
            else:
                s = s[:a]
                in_block = True
                break
        if "--" in s:
            s = s[: s.index("--")]
        out.append(s)
    return out


def qualify(name):
    name = name.lower()
    if ".." in name:
        db, obj = name.split("..", 1)
    elif "." in name:
        db, obj = name.split(".", 1)
        if obj.startswith("dbo."):
            obj = obj[4:]
    else:
        db, obj = DEFAULT_DB, name
    return db, obj


def main():
    raw = SRC.read_text(encoding="utf-8").splitlines()
    code = strip_comments(raw)
    exec_re = re.compile(r"\bexec(?:ute)?\s+(?:@\w+\s*=\s*)?([A-Za-z_][\w.]*)", re.I)
    read_re = re.compile(r"\b(?:from|join)\s+([A-Za-z_][\w.]*)", re.I)
    write_re = re.compile(r"\bupdate\s+([A-Za-z_][\w.]*)", re.I)

    leaves, edges, procs, tables = {}, [], {}, {}
    edge_set = set()

    def add_edge(s, t, kind, line):
        key = (s, t, kind)
        if key not in edge_set:
            edge_set.add(key)
            edges.append({"source": s, "target": t, "kind": kind, "line": line})

    for bid, name, a, b, dom in BLOCKS:
        seg = code[a - 1:b]
        loc = sum(1 for ln in raw[a - 1:b] if ln.strip() and not ln.strip().startswith(("--", "/*", "*")))
        leaves[bid] = {"id": bid, "name": name, "kind": "module", "language": "tsql-sybase",
                       "loc": max(loc, 1), "file": FILE, "lines": f"{a}-{b}", "domain": dom}
        for i, ln in enumerate(seg, start=a):
            for m in exec_re.finditer(ln):
                db, obj = qualify(m.group(1))
                pid = f"sp:{obj}"
                procs.setdefault(pid, {"id": pid, "name": obj, "kind": "module", "language": "tsql-sybase",
                                       "loc": 1, "file": f"(not in tree: {db}..{obj})", "domain": "procs",
                                       "database": db})
                add_edge(bid, pid, "call", i)
            for m in read_re.finditer(ln):
                db, obj = qualify(m.group(1))
                tid = f"ds:{db}..{obj}"
                tables.setdefault(tid, {"id": tid, "name": f"{db}..{obj}", "kind": "datastore", "domain": "data"})
                add_edge(bid, tid, "read", i)
            for m in write_re.finditer(ln):
                db, obj = qualify(m.group(1))
                tid = f"ds:{db}..{obj}"
                tables.setdefault(tid, {"id": tid, "name": f"{db}..{obj}", "kind": "datastore", "domain": "data"})
                add_edge(bid, tid, "write", i)
    for s, t in FLOW:
        add_edge(s, t, "call", 0)

    children = []
    for dkey in ("pre", "tx", "err"):
        children.append({"id": f"dom:{dkey}", "name": DOMAINS[dkey], "kind": "domain",
                         "children": [l for l in leaves.values() if l["domain"] == dkey]})
    children.append({"id": "dom:procs", "name": DOMAINS["procs"], "kind": "domain",
                     "children": sorted(procs.values(), key=lambda p: p["name"])})
    children.append({"id": "dom:data", "name": DOMAINS["data"], "kind": "domain",
                     "children": sorted(tables.values(), key=lambda t: t["name"])})
    for c in children:
        for l in c["children"]:
            l.pop("domain", None)

    inbound = {e["target"] for e in edges}
    dead = [bid for bid, *_ in BLOCKS if bid not in inbound and bid != "B0"]

    topo = {
        "system": "debcred: dbo.sp_debcred_empresa",
        "root": {"id": "sys", "name": "debcred", "kind": "system", "children": children},
        "edges": edges,
        "entryPoints": ["B0"],
        "deadEnds": dead,
        "observations": [
            "One program, one entry point: the COBIS callers (other procedures and channels, not in the tree) execute dbo.sp_debcred_empresa with 47 parameters; the return convention differs by exit path (B9 :1490, B10 :1700, B15 :2158/:2168).",
            "Ten external procedures carry every money movement; none is in the tree. sp_con_confcontable is called from three blocks (B3, B5), sp_grb_mov_y_frmpgo from two (B9, B10), sp_grb_comision from two (B10, B11): the call contracts are the single largest unknown.",
            "Every order read (B7, B9) and the order-state write (B12) fall back from db_biz_pagos to db_sat_his: orders are archived while still in flight, so two stores can hold the same order.",
            "The transaction opened in B4 has three ends: partial commit after a failed debit (B9), full rollback plus an autocommit write after a failed commission (B10), full rollback in B15. A Java port must reproduce all three over one JDBC connection to ASE (INTENT constraint 2).",
            "B7 (notifications, 510 lines) is the largest and most coupled block: 5 catalog reads, 3 account tables, 2 external procedures, and it overwrites the debit result (:1248) and an input amount (:886) that B9 then uses.",
            "B13 is commented out and two commit/return remnants (:1716-1730, :1844-1854) are unreachable; they are listed as observations, not ported.",
            "Unqualified names (sp_ndc_ahcc, sp_grb_*, pa_sat_pnotificacion, bp_total_orden, bp_detalle) are resolved here to db_biz_pagos by inference; confirm the procedure's home database with the bank.",
        ],
        "flows": [
            {"name": "Company pays a supplier from its current account",
             "persona": "Corporate treasurer of the paying company",
             "description": "A payment order (TRANSCLI) debits the company's current account, records the movement and commission, notifies the customer and moves the order to 'in transition'.",
             "steps": [
                 {"label": "Normalize commissions and read the per-transaction tariff", "nodes": ["B1", "sp:sp_con_comision"]},
                 {"label": "Resolve the accounting transaction code and causal", "nodes": ["B3", "sp:sp_con_confcontable"]},
                 {"label": "Open the transaction and build the debit reference", "nodes": ["B4", "B5"]},
                 {"label": "Debit the current account", "nodes": ["B6", "sp:sp_ndc_ahcc"]},
                 {"label": "Look up beneficiary and send the customer notification", "nodes": ["B7", "ds:db_biz_pagos..bp_detalle", "sp:sp_eventos"]},
                 {"label": "Record the movement and payment form", "nodes": ["B9", "sp:sp_grb_mov_y_frmpgo"]},
                 {"label": "Charge the commission", "nodes": ["B10", "sp:sp_grb_comision"]},
                 {"label": "Mark the order header 'T' and commit", "nodes": ["B12", "ds:db_biz_pagos..bp_total_orden", "B14"]},
             ]},
            {"name": "International SWIFT transfer with two commissions",
             "persona": "Corporate treasurer sending funds abroad",
             "description": "A TRANSWIFT order uses the SWIFT code as debit reference, charges the ordinary commission and a second SWIFT commission, and is exempt from the 'order header not found' error.",
             "steps": [
                 {"label": "Build the 'COD:<swift>' reference", "nodes": ["B5"]},
                 {"label": "Debit the account", "nodes": ["B6", "sp:sp_ndc_ahcc"]},
                 {"label": "Read beneficiary institution, with history fallback", "nodes": ["B7", "ds:db_biz_pagos..bp_orden", "ds:db_sat_his..bp_orden_his"]},
                 {"label": "Record the movement", "nodes": ["B9", "sp:sp_grb_mov_y_frmpgo"]},
                 {"label": "First commission", "nodes": ["B10", "sp:sp_grb_comision"]},
                 {"label": "Second SWIFT commission (tipoafec 16)", "nodes": ["B11", "sp:sp_grb_comision"]},
                 {"label": "Update the order header (exempt if no row) and commit", "nodes": ["B12", "B14"]},
             ]},
            {"name": "A debit is rejected and the rejection is kept on record",
             "persona": "Operations analyst reviewing failed payments",
             "description": "The account debit fails (insufficient funds, blocked account): the debit is undone to the savepoint, an error movement is written and committed, and the error code is returned; nothing else runs.",
             "steps": [
                 {"label": "Debit attempt fails in the account procedure", "nodes": ["B6", "sp:sp_ndc_ahcc"]},
                 {"label": "Roll back to the savepoint, status 'X'", "nodes": ["B9"]},
                 {"label": "Write the error movement", "nodes": ["B9", "sp:sp_grb_mov_y_frmpgo"]},
                 {"label": "Commit the error record and return the code", "nodes": ["B9", "B14"]},
             ]},
            {"name": "Commission fails after a successful debit",
             "persona": "Operations analyst and the audit team",
             "description": "The commission procedure fails: the whole transaction, debit included, is rolled back; then an error movement is written outside any transaction and the order stays 'I', so it can be re-run.",
             "steps": [
                 {"label": "Debit succeeds", "nodes": ["B6", "sp:sp_ndc_ahcc", "B9"]},
                 {"label": "Commission procedure returns an error", "nodes": ["B10", "sp:sp_grb_comision"]},
                 {"label": "Full rollback (REF44)", "nodes": ["B10"]},
                 {"label": "Error movement written in autocommit, return", "nodes": ["B10", "sp:sp_grb_mov_y_frmpgo"]},
             ]},
        ],
    }
    OUT.write_text(json.dumps(topo, indent=2, ensure_ascii=False), encoding="utf-8")

    n_leaves = sum(len(c["children"]) for c in children)
    print(f"wrote {OUT}")
    print(f"leaves: {n_leaves} ({len(leaves)} blocks, {len(procs)} external procedures, {len(tables)} tables)")
    print(f"edges: {len(edges)}  calls={sum(e['kind']=='call' for e in edges)} reads={sum(e['kind']=='read' for e in edges)} writes={sum(e['kind']=='write' for e in edges)}")
    print("entry:", topo["entryPoints"], " dead-end candidates:", dead)
    for p in sorted(procs.values(), key=lambda p: p["name"]):
        callers = sorted({e["source"] for e in edges if e["target"] == p["id"]})
        print(f"  proc {p['database']}..{p['name']:<24} <- {', '.join(callers)}")
    for t in sorted(tables.values(), key=lambda t: t["name"]):
        r = sorted({e["source"] for e in edges if e["target"] == t["id"] and e["kind"] == "read"})
        w = sorted({e["source"] for e in edges if e["target"] == t["id"] and e["kind"] == "write"})
        print(f"  table {t['name']:<34} read by {', '.join(r) or '-':<14} written by {', '.join(w) or '-'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
