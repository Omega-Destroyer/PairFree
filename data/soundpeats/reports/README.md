# Device reports

Raw output from real earbuds: `tools/probe.py`, PairFree's device report or its Record
from PeatsAudio report, or earbud frames taken from a phone's Bluetooth log. The README's
[Tested earbuds](../../../README.md#tested-earbuds) table is built from these.

A probe or device report is one file per model and firmware version, named
`<firmware code>_<version>.txt`, for example `S99_1.2.3.txt`. The firmware code is the
first field of the `firmware_version` line. Captures of particular features get a name
that says what they hold, for example `S5_S2_fit_drain_leaudio.txt`.

Paste the output unedited. Add a short header with the model name, the Bluetooth
name, the date and the operating system or phone it came from. Replace the last three
bytes of any Bluetooth address with `XX`.
