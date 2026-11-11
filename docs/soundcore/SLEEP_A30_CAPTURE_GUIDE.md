# Sleep A30: recording an audio transfer for PairFree

Thank you for helping investigate custom audio uploads. We need a recording of the **official
Soundcore app transferring its own sounds to your Sleep A30**. Use Soundcore's normal transfer
controls; PairFree will filter the resulting report on your phone.

Use a **PairFree build with Soundcore transfer diagnostics** for this recording. Older builds
omit information needed for this investigation; the maintainer will provide a suitable build.

These steps are for an **Android phone**. If you use an iPhone, let us know before starting;
its logging procedure is different. Menu names on Android vary by phone manufacturer.

## Before starting

- Charge the earbuds and case, and have both earbuds connected to the Soundcore app.
- Note your current local sound/mix so you can restore it afterwards. **The A30 holds one local
  file; each transfer in this test replaces it.**
- In PairFree, set its status notification for the A30 to **None**, so Soundcore can use the
  connection while PairFree is in the background.
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
5. In PairFree, open the A30 Device tab and **Record from the official app** to mark the start
   of the recording. Then leave PairFree for Soundcore, reconnect the A30 and wait for its normal
   connected screen. This start marker keeps the transfer included even if Local mode reconnects
   the earbuds afterwards.

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
Keep Bluetooth HCI logging enabled while doing it. After saving/sending A, close PairFree's
finished recording view and reopen **Record from the official app** to mark a fresh start, then
return to Soundcore.

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

## 4. Import the report into PairFree

1. Go to Android **Settings → Developer options → Take bug report** (sometimes **Bug report**).
   Choose **Full** if offered, then start the report.
2. Wait for the notification saying the report is ready. Report generation can take several
   minutes. Tap the notification and **share the bug report to PairFree**.
3. If PairFree is not offered as a share target, save the ZIP on your phone. In PairFree's
   **Record from the official app** screen, tap **Choose a bug report** and select it. Android
   may list it under **Bug reports** in the file picker's menu.
4. PairFree reads the report locally on your phone and produces its filtered text report.
   It includes Soundcore transfer diagnostics while leaving out raw audio, filenames, identifiers
   and known private traffic. You do not need to extract any files or send us the Android ZIP.
5. Check that the result lists Soundcore messages, then use **Send on GitHub** or **Share** to
   copy/send **PairFree's text report**, labeled A or B, together with your action notes. If the
   GitHub issue opens empty, paste the copied text into it before submitting.

Android describes [generating a bug report and sharing it](https://developer.android.com/studio/debug/bug-report).
If PairFree reports that no Bluetooth log or Soundcore messages were found, tell us your phone
model, Android version and logging setting. Keep the ZIP on your phone for now; do not attach it
as a workaround. If the export contains only the last reconnection, try **Show all** and export
again, noting that you widened the recording.

## 5. Send the filtered report and finish

Post **PairFree's filtered text report** with the version information and action notes. You can
share that report in the public issue. **Do not attach the original Android bug-report ZIP or a
raw Bluetooth snoop file.** PairFree performs the filtering on your phone; only the text report
you choose to submit leaves it.

The new diagnostics retain unknown packet sizes, short previews and timing, and recognize the
checksum-validated container structure studied in the app when present. An unfamiliar command
is not automatically labeled as an upload. Filenames, the container's identifier and audio
contents are omitted; known alarm schedules and sleep-history messages are also withheld.

After both reports have finished generating, set **Bluetooth HCI snoop log → Disabled** and
restart Bluetooth once more. Restore your preferred local sound using Soundcore if desired.
You can delete the Android reports after successfully importing them into PairFree.

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

Review the filtered report: it contains no raw HCI commands/events, keys or complete upload
payload. Unknown Soundcore command bodies retain at most four preview bytes; unframed data on
an already identified Soundcore channel has sizes/timing only. Recognized container headers
export technical fields after their CRC is verified, without their name/identifier/audio.
Bulk packets are summarized with counts, sizes and first/last times to keep reports manageable.

Check that each capture covers connection setup, transfer progress and the completion reply.
The exporter cannot identify an arbitrary new transfer protocol, an unidentified separate
channel, or a different container from this first analysis. Use the diagnostics to refine its
source-backed capture rules as more of the app is understood. Do not infer capacity or custom
file acceptance from a successful normal transfer. Do not request raw logs as the routine path.
