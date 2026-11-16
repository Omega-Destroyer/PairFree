# Jabra Sound+ model inventory

Research date: 2026-11-09. App definitions are evidence of what this APK knows, not proof that a product is sold, supported by PairFree, or tested on hardware.

55 definition files: 49 marked production (34 distinct display names), five other product leads and a developer board. The developer board reuses PID 9355; never identify it instead of Elite 85h. UC/MS, mono and Secure Audio variants remain separate.

48 files select GNP; seven select GAIA. All 42 production GNP PIDs have local firmware settings snapshots: 46 snapshots in total. GAIA discovers its vendor features from the device.

## Production definitions

| Device | PID (decimal / hex) | Definition variant | Transport | Cached firmware | MySound | MyControls onboarding |
|---|---|---|---|---|---|---|
| Jabra Elite 10 | 11846 / `2E46` | stella | GNP | 01-92 / 1.2.4 | no | yes |
| Jabra Elite 8 Active | 11847 / `2E47` | pelican | GNP | 01-91 / 1.2.4 | no | yes |
| Jabra Elite 4 | 11848 / `2E48` | swan | GAIA | device feature discovery | no | no |
| Jabra Elite 10 Gen 2 | 11865 / `2E59` | stella_refresh | GNP | 01-8F / 1.0.8, 01-8F / 1.1.4 | no | yes |
| Jabra Elite 8 Active Gen 2 | 11866 / `2E5A` | pelican_refresh | GNP | 01-8E / 1.0.8 | no | yes |
| Jabra Elite 65e | 13655 / `3557` | ivy | GNP | 01-51 / 2.25.0 | no | no |
| Jabra Elite 65t | 9342 / `247E` | bezos | GNP | 01-4C / 2.40.1 | no | no |
| Jabra Elite 45e | 9347 / `2483` | jack | GNP | 01-57 / 1.28.0 | no | no |
| Jabra Elite Active 65t | 9353 / `2489` | bezosActive | GNP | 01-56 / 2.40.1 | no | no |
| Jabra Elite 85h | 9355 / `248B` | tor | GNP | 01-50 / 1.6.3 | no | no |
| Jabra Evolve 65e | 9358 / `248E` | poker | GNP | 01-58 / 2.29.0 | no | no |
| Jabra Elite Active 45e | 9362 / `2492` | lightning | GNP | 01-5A / 2.22.0 | no | no |
| Jabra Evolve 65t | 9367 / `2497` | bezosPro | GNP | 01-5D / 2.40.1 | no | no |
| Jabra Evolve2 65 | 9379 / `24A3` | charlesUC | GNP | 01-63 / 2.6.0 | no | no |
| Jabra Elite 45h | 9381 / `24A5` | emma | GNP | 01-65 / 2.3.9 | yes | no |
| Jabra Elite 75t | 9383 / `24A7` | titan | GNP | 01-60 / 2.0.0 | yes | no |
| Jabra Evolve2 65 | 9395 / `24B3` | charlesMonoUC | GNP | 01-64 / 2.6.0 | no | no |
| Jabra Evolve2 65 | 9397 / `24B5` | charlesMonoMS | GNP | 01-64 / 2.6.0 | no | no |
| Jabra Evolve2 65 | 9399 / `24B7` | charlesMS | GNP | 01-63 / 2.6.0 | no | no |
| Jabra Evolve2 85 | 9401 / `24B9` | lokiUC | GNP | 01-67 / 1.3.8 | no | no |
| Jabra Evolve2 85 | 9403 / `24BB` | lokiMS | GNP | 01-67 / 1.3.8 | no | no |
| Jabra Elite Active 75t | 9420 / `24CC` | titanActive | GNP | 01-61 / 2.0.0 | yes | no |
| Jabra Elite 85t | 9421 / `24CD` | vostok | GNP | 01-68 / 2.0.0 | yes | no |
| Jabra Evolve2 75 | 9433 / `24D9` | erasmusUC | GNP | 01-6D / 1.7.4, 01-6D / 1.8.5 | yes | no |
| Jabra Evolve2 75 | 9435 / `24DB` | erasmusMS | GNP | 01-6D / 1.7.4, 01-6D / 1.8.5 | yes | no |
| Jabra Elite 7 Pro | 9439 / `24DF` | astraPro | GNP | 01-6E / 2.2.0 | yes | yes |
| Jabra Elite 2 | 9441 / `24E1` | flamingo | GAIA | device feature discovery | no | no |
| Jabra Elite 3 | 9442 / `24E2` | peacock | GAIA | device feature discovery | no | no |
| Jabra Elite 4 Active | 9446 / `24E6` | toucan | GAIA | device feature discovery | no | no |
| Jabra Elite 7 Active | 9447 / `24E7` | astraActive | GNP | 01-6E / 2.2.0 | yes | yes |
| Jabra Speak2 75 | 9455 / `24EF` | samwiseUC | GNP | 08-0B / 1.1.6 | no | no |
| Jabra Speak2 75 | 9457 / `24F1` | samwiseMS | GNP | 08-0B / 1.1.6 | no | no |
| Jabra Elite 5 | 9458 / `24F2` | canary | GAIA | device feature discovery | no | yes |
| Jabra Evolve2 55 | 9459 / `24F3` | willowUC | GNP | 01-76 / 1.1.6 | yes | no |
| Jabra Evolve2 55 | 9461 / `24F5` | willowMS | GNP | 01-76 / 1.1.6 | yes | no |
| Jabra Evolve2 55 | 9462 / `24F6` | willowMonoUC | GNP | 01-77 / 1.1.6 | no | no |
| Jabra Evolve2 55 | 9464 / `24F8` | willowMonoMS | GNP | 01-77 / 1.1.6 | no | no |
| Jabra Evolve2 Buds | 9483 / `250B` | hazelUC | GNP | 01-80 / 1.1.0 | yes | yes |
| Jabra Evolve2 Buds | 9484 / `250C` | hazelMS | GNP | 01-80 / 1.1.0 | yes | yes |
| Jabra Evolve2 55 | 9490 / `2512` | willowSecureAudioUC | GNP | 01-76 / 1.1.6 | yes | no |
| Jabra Evolve2 55 | 9492 / `2514` | willowSecureAudioMS | GNP | 01-76 / 1.1.6 | yes | no |
| Jabra Elite 3 Active | 9493 / `2515` | woodpecker | GAIA | device feature discovery | no | no |
| Jabra Elite 7 Sport | 9494 / `2516` | comet | GNP | 01-83 / 2.3.0, 01-83 / 2.4.0 | yes | yes |
| Jabra Evolve2 65 Flex | 9495 / `2517` | cypressUC | GNP | 01-82 / 1.1.0 | yes | no |
| Jabra Evolve2 65 Flex | 9497 / `2519` | cypressMS | GNP | 01-82 / 1.1.0 | yes | no |
| Jabra Connect 5t | 9501 / `251D` | serin | GAIA | device feature discovery | no | yes |
| Jabra Evolve2 65 Flex | 9507 / `2523` | cypressSecureAudioUC | GNP | 01-82 / 1.1.0 | yes | no |
| Jabra Evolve2 65 Flex | 9509 / `2525` | cypressSecureAudioMS | GNP | 01-82 / 1.1.0 | yes | no |
| Jabra Elite Flex | 9520 / `2530` | palm | GNP | 01-82 / 1.2.12 | yes | no |

MySound is hearing personalization, separate from music EQ. All 49 production definitions enable custom music EQ. The onboarding flag is not proof that every button action is supported. Actual GNP button options are in [SETTINGS.md](SETTINGS.md); GAIA must return its feature and supported-action lists.

## Nonproduction entries

| App name | PID | Definition |
|---|---|---|
| Jabra Elite 4 Gen 2 | 11860 | 11860_florence_wl |
| Jabra Elite 4 Active Gen 2 | 11861 | 11861_florence_active |
| Jabra Fuxi Stereo Audio | 9522 | 9522_fuxiUC |
| Jabra Fuxi Stereo Data | 9523 | 9523_fuxiData |
| Jabra Fuxi Stereo Audio MS | 9524 | 9524_fuxiMS |
| Developer Board | 9355 | developer_board |

Do not expose these as supported products. Elite 7 Sport and Elite Flex are marked production by the APK; that flag alone does not establish retail availability. Resource-only names such as Boost/Drive are not extra device definitions.

## Validation categories

- APK researched: all 55 definitions and all four APK splits inspected; local cached data decoded for all 42 production GNP PIDs.
- Native encoder checked virtually: GNP framing, selected control payloads and music EQ writes, using synthetic input and a stub transport.
- Whole-app virtual device tested: none in this research pass.
- Real-device tested: none.

The encoder category applies to code paths, not to every model or firmware. No Jabra device is marked virtually tested in `devices.json`. Full flags, wear detection, assistant choices, Spotify, LE Audio gates and spatial capabilities are in [devices.json](../../data/jabra/research/devices.json).
