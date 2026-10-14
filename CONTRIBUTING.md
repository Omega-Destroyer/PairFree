# Contributing

## The most useful thing: a device report

Every model needs one person to run the probe on it. See the README for how.
Then open an issue with the "Device report" template and paste the output.

A report is most useful when it includes:

- The model name as printed on the box
- The Bluetooth name the earbuds show when pairing
- The firmware version the official app shows, if you know it
- The full probe output, unedited
- Your computer's operating system

Reports land in `data/soundpeats/reports/` as `<firmware code>_<version>.txt` and the model is
added to `data/soundpeats/devices.json`.

## Trying a setting

The probe can send one SET command so you can check that a setting takes effect:

```
python tools/probe.py --mac AA:BB:CC:DD:EE:FF --set 0311 02
```

Command numbers and values are in `data/soundpeats/commands.json`. Stick to things you can hear
or see and undo, like ANC mode or game mode. Do not send `0305` (factory reset) or
`0316` (reboot) unless you mean it.

## Code

- Read `docs/soundpeats/PROTOCOL.md` first.
- Take command numbers from `data/soundpeats/commands.json`. Do not retype them.
- Any code that builds or parses frames needs a test that runs
  `data/soundpeats/test_vectors.json`.
- Run `python -m unittest discover -s tools -v` before you push.

## What does not belong here

- Decompiled code from the official app
- Vendor SDKs or code copied from them
- Firmware images or APKs
- The key the official app uses to talk to its server

Write from the protocol description. If the description is missing something, open
an issue and say what you found and how.
