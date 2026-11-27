# Huawei and Honor audio models

The supplied AI Life 11.0.2.305 APK lists eight audio products in its bundled catalogue and includes headset code for two more. The [generated device table](../devices/huawei.md) adds models identified by public protocol research and Huawei's current documentation. Neither catalogue presence nor an independent capture is a PairFree hardware test. [Huawei's AI Life availability notes](https://consumer.huawei.com/pt/support/content/pt-pt15926101/) · [Current earbud guide](https://consumer.huawei.com/au/support/content/en-gb00759656/)

| Product ID | Model and app route | PairFree controls | Remaining vendor behavior |
| --- | --- | --- | --- |
| 004O | FreeBuds 3 / Shark (CM-SHK00, CM-SHK09) | Battery, ANC and nine-position adjustment, double tap, reported voice language | Phone wear integration |
| ZA02 | FreeBuds 3i / Walrus (WAL-CT025) | Battery, double tap, long-press noise cycle | Huawei phone wear API and direct ANC control evidence |
| ZB02 | HONOR FlyPods 3 / Walrus | Battery, double tap, long-press noise cycle | Huawei phone wear API and direct ANC control evidence |
| ZB04 | HONOR Magic Earbuds / Walrus | Battery, double tap, long-press noise cycle | Huawei phone wear API and direct ANC control evidence |
| ZA04 | FreeLace Pro / Nile | Battery, legacy and modern ANC, power button, hold cycle and language | Firmware differences in side reporting and availability |
| ZA01 | FreeGO Speaker / CM530 | Battery | Speaker settings and host integration |
| ZC01 | HUAWEI Eyewear / Perrier (CMG0-PER) | Glasses and case battery, voice language | Gesture guide and firmware workflow |
| ZC02 | HUAWEI Eyewear II / Fiji (FIJ-CG020) | Glasses battery, language, double tap, swipe, hold, greetings, wear, power saving and intelligent volume | Phone-specific recording and execution of gestures assigned to translation or voice memo |
| ZA06 | FreeBuds Studio / Roc (M0001) | Battery, ANC, wear, preset EQ | APK gesture, touch lock and sound-quality variants |
| ZA07 | FreeBuds Pro / Mermaid (T0003 / T0003C) | Battery, ANC, swipe, hold, language and multipoint | APK gesture and noise variants, recording and other phone integrations |

The eight catalogue rows came from the union of main_help.json, main_help_global.json, main_help_oversea_cloud.json and localProfile. Studio and Pro are linked by ModelPbiMap and the product identities Huawei publishes. The Walrus fallback token ZA0*undefine* maps to offering OFFE00113769 but the archive supplies no catalogue record connecting it to a shipping Bluetooth name. [Huawei model identifiers](https://consumer.huawei.com/jp/support/content/ja-jp15938700/) · [Legacy model names](https://consumer.huawei.com/cn/support/content/zh-cn15984866/)

[data/huawei/devices.json](../../data/huawei/devices.json) holds the exact model profiles, implemented settings and source flags. OpenFreebuds at 0b4f14c supplied the profile-level ANC, EQ and gesture variants. LibreBuds c7b0833, HuaweiPods e2cf07e and FreeBuddies supplied additional model and packet evidence. Classic transport is resolved by service UUID. For models that need unusual channels or a different control endpoint, a physical connection report remains necessary. [OpenFreebuds](https://github.com/melianmiko/OpenFreebuds/tree/0b4f14c0b500d491f0fb4ea38f2984ec334e3649) · [LibreBuds vectors](https://github.com/librebuds/librebuds/tree/c7b0833/test-vectors) · [HuaweiPods coverage](https://github.com/Nshpiter/HuaweiPods/blob/e2cf07e/docs/support/index.md) · [FreeBuddies captures](https://github.com/IAL32/freebuddies/blob/main/app/README.md)

Huawei's current support guides also describe adaptive awareness, extended effects, audio connection centre, fit tests, spatial audio, case tones, reminders, configurable head actions and cloud finding. The [completeness registry](../../data/completeness.json) tracks the remaining model and feature work. [AI Life earbud cards](https://consumer.huawei.com/au/support/content/en-gb15828842/) · [Huawei EQ coverage](https://consumer.huawei.com/sg/support/content/en-gb15868545/)
