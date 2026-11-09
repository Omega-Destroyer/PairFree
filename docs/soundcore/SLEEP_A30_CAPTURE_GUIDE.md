# Sleep A30: recording an audio transfer for PairFree

Thank you for helping investigate custom audio uploads. We need a recording of the **official
Soundcore app transferring its own sounds to your Sleep A30**. You do not need to install an
experimental app or try uploading your own files yet.

These steps are for an **Android phone**. If you use an iPhone, let us know before starting;
its logging procedure is different. Menu names on Android vary by phone manufacturer.

## Before starting

- Charge the earbuds and case, and have both earbuds connected to the Soundcore app.
- Note your current local sound/mix so you can restore it afterwards. **The A30 holds one local
  file; each transfer in this test replaces it.**
- If PairFree is installed, use Android **Settings → Apps → PairFree → Force stop** for this
  recording so it does not compete with Soundcore for the connection.
- If practical, disconnect other Bluetooth accessories for the short recording and avoid calls
  or unrelated Bluetooth playback during it. Keep playback volume low and comfortable.
- Open the Soundcore Audio Library once beforehand so the sounds you plan to use are available.
  Follow any normal app prompts about the position of the earbuds or case during transfers.

Please record these details separately; no serial number or Bluetooth address is needed:

```text
Phone manufacturer/model:
Android version:
Soundcore app version:
Exact product name (Sleep A30 or Sleep A30 Special):
Left/right earbud firmware shown by the app:
Case firmware, if shown:
Recording date and local time zone:
Current local sound before testing:
```

## 1. Turn on Bluetooth recording

1. Open Android **Settings → About phone** and find **Build number**. On some phones it is
   under **Software information**. Tap it seven times and enter your phone's unlock code if
   asked. This enables Developer options.
2. Return to Settings and search for **Developer options**.
3. Find **Bluetooth HCI snoop log** and select **Enabled**. If your phone offers **Full** and
   **Filtered**, use **Full** for this test; filtering may remove the transfer payload.
4. Turn Bluetooth **off**, wait about five seconds, then turn it **on**. Logging needs the
   Bluetooth restart to take effect. Leave your earbuds paired; do not forget or reset them.
5. Open Soundcore, reconnect the A30, and wait for its normal connected screen.

Android documents the [Bluetooth logging setting and restart](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging).
If the setting is missing or will not stay enabled, send us your phone model and Android version
instead of installing additional logging tools.

## 2. Capture a single-sound transfer

Keep a short note of the phone's clock time for each action, ideally including seconds. Pause
about **10 seconds between actions**, except while a transfer is running: let that finish fully.

1. Note the time and open the A30's **Audio Library → White Noise**. Some versions call this
   library **Soundscape**. Make sure you are in **Bluetooth mode** before starting the transfer.
2. Choose **one sound only**, such as Rain, and note its exact displayed name and its mix-volume
   slider position. If the app has no percentages, a description such as “about one-third” is fine.
3. Tap the **up-arrow/download/transfer icon**, then confirm **Transfer** or **Download**.
   Note the time you confirmed it.
4. Keep Soundcore open until it reports success. Note the completion time, its exact message,
   and whether it showed a size, duration or other limit. If it fails, note the error and stop
   this run rather than repeatedly retrying.
5. After success, wait 10 seconds. Switch to **Local mode** in Soundcore, listen briefly and
   note whether the transferred sound plays. Then switch back to **Bluetooth mode** and wait
   another 10 seconds.
6. Immediately save the Android bug report as described in section 4. Label this recording
   **A — single white-noise sound**. Save it before doing the second transfer so a long log
   cannot overwrite the first transfer.

## 3. Capture a changed mix

This second recording helps identify which parts describe the sound and which carry the audio.
Keep Bluetooth HCI logging enabled while doing it.

1. Return to **Audio Library → White Noise** and select the **same sound as before**, then add
   **one different sound** to it, for example Rain plus Wind. Note both exact names and both
   mix-volume slider positions.
2. Transfer that mix to the earbuds. Again note the confirmation and completion times and wait
   for the success message without leaving Soundcore.
3. Verify briefly in **Local mode**, return to **Bluetooth mode**, and note whether both sounds
   were audible.
4. Immediately save another bug report and label it **B — two-sound mix**.

If you only have time for one recording, **A is the priority**. If the app does not offer a mix
editor, skip B and describe what it shows. Screenshots of the sound selections and transfer
messages are helpful; crop out account details or serial numbers.

An optional later recording of a built-in **Snore Masking/Masking Soundscape** transfer would
let us compare that format with white noise. It is not required for the first submission.

## 4. Save the report and extract the Bluetooth log

1. Go to Android **Settings → Developer options → Take bug report** (sometimes **Bug report**).
   Choose **Full** if offered, then start the report.
2. Wait for the notification saying the report is ready. Tap it and save the ZIP to Files or
   transfer it to your own computer. Report generation can take several minutes.
3. Open/extract the ZIP on your phone or computer. Search its contents for **`btsnoop`**. The file
   is usually named **`btsnoop_hci.log`**, sometimes compressed as **`btsnoop_hci.log.gz`**. A common
   location inside the ZIP is `FS/data/misc/bluetooth/logs/`; other phones use a different path.
4. Keep that Bluetooth log for each recording, together with your notes. If more than one
   matching log exists, retain their original filenames and folder paths so we can identify
   which covers the transfer. You can place just those logs in a new ZIP labeled A or B.

Android describes [saving a bug report and its ZIP contents](https://developer.android.com/studio/debug/bug-report).
If you cannot find a `btsnoop` file, tell us your phone model, Android version and whether the
logging option was Enabled/Full. Some reports only include a reduced Bluetooth history, which
may omit audio-transfer data. Do not repeat all the tests or post the full report just to find out.

## 5. Send the recording and finish

Send **the extracted Bluetooth logs privately through a method agreed with the maintainer**,
plus the device/version information and action notes. Please do not attach the full Android
bug-report ZIP or a raw Bluetooth log to a public GitHub issue: these files can contain personal
diagnostics, device identifiers and Bluetooth security information. Extracting the Bluetooth
file reduces what is shared, but does not itself anonymize it.

For this particular investigation, PairFree's normal **Record from the official app** text
export is useful as an optional supplement, but **does not replace the Bluetooth file**: it
currently omits most unknown message payloads, including potentially the upload data we need.

After both reports have finished generating, set **Bluetooth HCI snoop log → Disabled** and
restart Bluetooth once more. Restore your preferred local sound using Soundcore if desired.
You can delete the saved reports/logs after the maintainer confirms receipt.

Example action notes (replace these with your own times and sound names):

```text
A — single white-noise sound
19:10:00 Soundcore connected; Bluetooth mode
19:10:15 Selected Rain, one source, mix slider about one-third
19:10:30 Confirmed Transfer
19:11:12 Success message: [exact wording]
19:11:25 Switched to Local; Rain played
19:11:40 Returned to Bluetooth mode
19:11:55 Started Android bug report

B — two-sound mix
19:20:00 Selected Rain + Wind; sliders about one-third each
19:20:15 Confirmed Transfer
19:20:58 Success message: [exact wording]
19:21:10 Switched to Local; heard both sounds
19:21:25 Returned to Bluetooth mode
19:21:40 Started Android bug report

App prompts about earbud/case placement:
Any errors or unexpected behavior:
Any file-size/duration information displayed:
```

## Maintainer handling

Before inspecting submitted logs, use a local parser to discard Bluetooth security commands,
events and key material without printing or retaining their contents. Work from the relevant
device's reassembled control/data traffic and retain packet boundaries and timestamps. Do not
commit the raw HCI log or full bug report; publish only reviewed, sanitized protocol facts.
Check that each capture includes connection setup, the complete upload and the completion
response. A successful normal upload helps establish framing and file format; it does not
alone establish the maximum capacity or acceptance of arbitrary custom audio.
