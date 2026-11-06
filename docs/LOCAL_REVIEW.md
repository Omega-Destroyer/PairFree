# New vendor drafts for local review

Prepared 2026-11-06 at the owner's request. Everything is local: no push, remote PR, merge, release or
version bump. `main` remains `ebe0dff` (PairFree 0.7.2). The working branch is `brand/cambridge`.

| Branch | Initial commit | What is implemented | Android integration |
|---|---|---|---|
| `brand/cambridge` | `b946869` | GAIA v1, battery, EQ presets/five gain rows, control toggles, languages, codec preference, Touch ambient, matching-ACK queue and redacted reports | Melomania 1+ / Touch only; draft |
| `brand/huawei` | `a82c634` | MBB/TLV codec, CRC, stream parser, battery read and value decoder | None |
| `brand/oneplus` | `259d1e8` | OPOv1 codec, numbered fragments, sequence keys, capability bits and raw-type battery decoder | None |
| `brand/audiotechnica` | `a802d2e` | Legacy GAIA, v3 messages, AT/RACE and BBPRO codecs, typed events/ACKs and read examples | None |
| `brand/skullcandy` | `fbdcf5f` | Bragi PPP/RPC/protobuf core, CRCs, request correlation, strict status and authentication-flag decoding | None |
| `research/new-vendor-protocols` | `cbac5cc` | Five-vendor APK analysis, source locators, model and command inventories | Documentation only |

The Cambridge branch also stops queued gain writes if bank selection or a gain write fails or times out.
Review the branch tip, including that follow-up, rather than only its initial commit.

Each implementation branch starts independently from `main` and carries its vendor's research notes.
The research snapshot is not a dependency to merge first: it overlaps those added protocol docs.
Shared Gradle/CLAUDE additions will need ordinary merge-conflict resolution when combining branches.
No vendor binaries, decompiled sources, credentials, decrypted catalogs or firmware are committed.
The pre-existing untracked `Sync.txt` was left alone.

## Review commands

```sh
git diff main...brand/cambridge
git diff main...brand/huawei
git diff main...brand/oneplus
git diff main...brand/audiotechnica
git diff main...brand/skullcandy
git show cbac5cc
```

For Cambridge, start with [implementation details](cambridge/PROTOCOL.md#8-local-implementation-draft-for-review)
and the [device page](devices/cambridge.md). On each other branch, read that vendor's
`docs/<vendor>/IMPLEMENTATION.md`; it lists exactly what remains before enabling models.

## Validation and limits

On **each** vendor branch, the full protocol suite, `:app:assembleDebug`, `:app:lintDebug` and all
133 Python tests passed. There are 23 new protocol test methods across the five branches, covering
analytical vectors and synthetic replies, framing boundaries, invalid lengths/checksums/status,
model or namespace restrictions and privacy/security behavior. Cambridge's device pages and generic
artwork were regenerated; its two models use the existing generic drawing. Builds ran in
`E:\Builds\PairFree`, after checking mirror-only files against their committed branch before switching.

No new vendor hardware was used. The tests establish code behavior against the documented source facts;
they do not establish a successful device connection, acoustic changes or firmware compatibility.
The four protocol-core branches must **not** be advertised as supported devices. Their model-specific
sessions, settings, EQ, controls, detection, transport selection and reports are still implementation work.
Skullcandy also needs genuine local trust establishment for models that require authentication.

For Cambridge, verify gain-bank behavior and device-side readback on hardware before release. Frequency-based
EQ profiles, phone-local vendor custom slots and newer Melomania Connect models are outside this draft.
High-performance/low-power audio switching is excluded because the vendor implements it as firmware
installation, as traced in `b3/d`. No firmware operation is implemented.

## Virtual-test follow-up

The Cambridge branch now includes original-vendor-app tests against virtual Touch and 1+ instances,
78 outgoing regression cases and a corrected custom-curve restore sequence. See
[the virtual-test record](cambridge/VIRTUAL_TESTING.md). This is a new evidence category, distinct from
hardware testing; subsequent branches must record their own actual runs rather than inherit this status.
