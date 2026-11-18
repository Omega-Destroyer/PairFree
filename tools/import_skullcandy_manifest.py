"""Writes data/skullcandy/devices.json from Skullcandy's own app (5.0.6): the product manifest the app bundles
(`assets/flutter_assets/assets/products/product-manifest.json`: 56 products with their ids, model names, connection types, firmware
versions with the features each shows, and the button actions), joined to the chip table (`data/skullcandy/chips.json`, from the app's Dart code)
and to the Bragi SDK's device configurations (`data/skullcandy/sdk_devices.json`).

    python tools/import_skullcandy_manifest.py PATH_TO_jadx_resources [--check]

The manifest's `productName` and the app's enum name are joined by the table below, which pairs each product id with the Dart enum name that
the app's own code gives it (the BLE and classic twins of one name share a name but not an enum value: the `RC` entries are the classic twins).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data" / "skullcandy"
MANIFEST = "assets/flutter_assets/assets/products/product-manifest.json"

# product id (hex, as the manifest writes it) -> the app's enum name (skdyproductmodel.dart)
ENUM = {
    "0x01": "crusherANC", "0x02": "crusherEvo", "0x30": "crusherEvoRC", "0x07": "indyANC", "0x04": "indyFuel", "0x0B": "indyFuelRC",
    "0x03": "indyEvo", "0x0A": "indyEvoRC", "0x05": "pushUltra", "0x08": "pushActive", "0x09": "grind", "0x63": "grindFuel",
    "0x16": "crusherANC2", "0x21": "rail", "0x15": "railANC", "0x1D": "method360ANC", "0x2D": "method540ANC", "0x0D": "jibTrueXT2",
    "0x0F": "dime2", "0x19": "dimeXT2", "0x0C": "seshANC", "0x12": "mod", "0x14": "riffWireless2", "0x11": "plyr",
    "0x23": "slyrProXbox", "0x24": "slyrPro", "0x26": "hanc", "0x25": "tanc", "0x1A": "iconANC", "0x1B": "dimeEvo",
    "0x06": "aviatorANC", "0x1E": "hesh360", "0x1F": "hesh540ANC", "0x29": "crusherPlyr", "0x2A": "crusherPlyrXbox",
    "0x2C": "crusher540Active", "0x31": "crusher540ActiveRC", "0x2F": "push720open", "0x2E": "icon180", "0x32": "push540Open",
    "0x736": "terrainMini", "0x737": "terrain", "0x738": "terrainXL", "0x33": "dime360", "0x36": "crusher720", "0x34": "crusher1080ANC",
    "0x37": "session360", "0x38": "session540", "0x39": "session720", "0x3A": "session900", "0x35": "smokinBuds3", "0x45": "dime540ANC",
    "0x42": "smokingBuds360Hanger", "0x41": "smokingBuds360Stick", "0x44": "crusher900ANC", "0x43": "push180Open",
}


# the SDK config of Grind Fuel answers to BLE model id 16 while the manifest gives it 0x63 (docs/skullcandy/PROTOCOL.md section 9): joined by name
SDK_BY_NAME = {"0x63": "GRIND_FUEL"}


def norm(pid):
    return "0x%02X" % int(pid, 16) if len(pid) <= 4 else pid


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        sys.exit(__doc__)
    manifest = json.loads((Path(args[0]) / MANIFEST).read_text(encoding="utf-8"))
    chips = {m["model"]: m["chip"] for m in json.loads((DATA / "chips.json").read_text(encoding="utf-8"))["models"]}
    sdk = json.loads((DATA / "sdk_devices.json").read_text(encoding="utf-8"))["devices"]
    sdk_by_id = {}
    for d in sdk:
        for i in d["ble_model_id"].get("modelId", []):
            sdk_by_id[i] = d["model"]["key"]
    products = []
    for p in manifest["allProducts"]:
        pid = norm(p["productId"])
        enum = ENUM[pid]
        firmwares = [{"version": f["version"], "features": f.get("featureManifest", [])} for f in p.get("firmwareVersions", [])]
        union = sorted({x for f in firmwares for x in f["features"]})
        entry = {
            "id": pid, "name": p["productName"], "enum": enum, "models": p["modelId"], "alternate_names": p.get("alternateProductNames") or [],
            "android": p["connectionTypeAndroid"], "chip": chips[enum], "sdk": sdk_by_id.get(int(pid, 16)) or SDK_BY_NAME.get(pid),
            "airoha_product_id": p.get("airohaProductId"), "ota_supported": p.get("otaSupported", False),
            "firmware": firmwares, "features": union,
        }
        if "defaultButtonSettings" in p:
            entry["default_buttons"] = p["defaultButtonSettings"]
        if "mappableButtonActions" in p:
            entry["mappable_buttons"] = p["mappableButtonActions"]
        products.append(entry)
    data = {"_about": "Skullcandy 5.0.6's product manifest joined to the app's chip table and the Bragi SDK's configurations, by tools/import_skullcandy_manifest.py. "
                      "`features` is the union over the firmware entries; `firmware` keeps each version's own list (the app shows the selected firmware's, not the union).",
            "unsupported_names": manifest.get("unsupportedProducts", []), "products": products}
    text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"
    out = DATA / "devices.json"
    if "--check" in sys.argv:
        sys.exit(0 if out.exists() and out.read_text(encoding="utf-8") == text else "data/skullcandy/devices.json is out of date")
    out.write_text(text, encoding="utf-8")
    print(out, len(products), "products")


if __name__ == "__main__":
    main()
