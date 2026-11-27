# HeyMelody remote capability expansion

Derived protocol facts from HeyMelody 117.6.6 `p026b8/a` and PollCommandManager.G. Research only. Bit zero is the least significant bit of the first byte after response status. A set bit expands to every command below; the table is not a safe-to-send allowlist and does not replace the model UI/firmware predicates. See [PROTOCOL.md](PROTOCOL.md).

| Bit | Permitted wire commands (hex) |
|---|---|
| 0 | `0105` |
| 1 | `0106` |
| 2 | `0107` |
| 3 | `0108`, `0401`, `0416` |
| 4 | `0109` |
| 5 | `0400` |
| 6 | `0402` |
| 7 | `0403` |
| 8 | `010C`, `0404` |
| 9 | `0405` |
| 10 | `0406`, `010F` |
| 11 | `0407` |
| 12 | none |
| 13 | `0408` |
| 14 | `0409` |
| 15 | none |
| 16 | none |
| 17 | `0114` |
| 18 | none |
| 19 | `040E`, `040D`, `0115`, `0116` |
| 20 | `040F` |
| 21 | `0410`, `0119` |
| 22 | `0205` |
| 23 | `0F00` |
| 24 | none |
| 25 | `0118`, `0411` |
| 26 | `011A`, `0412` |
| 27 | `011C`, `0413` |
| 28 | none |
| 29 | `0112`, `040B` |
| 30 | `011E`, `011F`, `0415` |
| 31 | `040D` |
| 32 | none |
| 33 | `0121`, `0417` |
| 34 | `0122`, `0418` |
| 35 | `0419` |
| 36 | `011D`, `0414` |
| 37 | `0123`, `041A` |
| 38 | `0124`, `041B` |
| 39 | `0125`, `041C`, `0127`, `041D`, `041F` |
| 40 | `0421`, `0023`, `0024`, `0022`, `0126`, `0129` |
| 41 | `EF01` |
| 42 | `EF02` |
| 43 | `EF03`, `041E` |
| 44 | `0420` |
| 45 | `001C` |
| 46 | none |
| 47 | `0422`, `012A` |
| 48 | `EF04` |
| 49 | `0423`, `012B` |
| 50 | none |
| 51 | `0424` |
| 52 | `EF06` |
| 53 | none |
| 54 | none |
| 55 | `0425`, `012E`, `0426` |
| 56 | `012F` |
| 57 | `0427`, `0130` |
| 58 | `0131`, `0428` |
| 59 | `0429`, `0132` |
| 60 | `0014` |
| 61 | `042D`, `0133` |
| 62 | `042E` |
| 63 | `EF07` |
| 64 | `EF08`, `0136`, `0433` |
| 65 | `EF09` |
| 66 | `0431`, `0134` |
| 67 | `0432`, `0135`, `0435` |
| 68 | `0117` |

The SDK always permits `0100 0101 0102 0103 0104 0106 010B 010D 0F00 0F03 0F04`. Debug, private data, firmware and provisioning must still be excluded. A capability bit has no universal UI label just because one of its permitted command numbers resembles another family's command.
