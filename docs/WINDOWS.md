# PairFree for Windows

PairFree for Windows is one portable program, `PairFree.exe`, about 16 MB. There is no installer and nothing is written to the registry. It does what the
Android app does for your earbuds, in a window made for a mouse. It is new in PairFree 0.16.0: it was tried against recorded earbuds and virtual ones, and not yet by many people, so please report what does not work.

## Needs

- Windows 10 (version 1809 or later) or Windows 11, 64-bit. On a PC with an ARM processor it runs through Windows' own emulation.
- For Bluetooth control, pair your earbuds in Windows and connect them for audio. The Bose QC Ultra Headphones (2nd Gen) can also be controlled over USB-C.

## Starting it

Put `PairFree.exe` anywhere you like and run it. Its settings are one readable file, `PairFree.toml`, in the same folder; copy both to take your settings with
you, delete the file to start over. You may edit it while PairFree is closed. If the folder cannot be written (a protected folder), PairFree says so and keeps its
settings in memory for that session.

For the Bose QC Ultra Headphones (2nd Gen), connect a USB data cable, turn the headphones on, and select the **(USB)** entry in PairFree's sidebar.

Windows may show a blue **Windows protected your PC** window the first time, because the program is not signed with a paid certificate. Choose **More info**,
then **Run anyway**. Release notes list the file's SHA-256 checksum; check it if you like (`Get-FileHash PairFree.exe` in PowerShell).

## What it does

The same screens as the Android app, laid out for a window: **Home** (battery, noise control, quick switches), **Sound** (the equalizer, with profiles you can
save, import and export as files, and the hearing test), **Controls** (touch controls), **Device** (the earbuds' own settings, the fit and seal tests, a report to
paste into an issue) and **Settings**. The tray icon at the clock shows the battery and has a menu for noise control, disconnecting and exiting. Closing the window
can keep PairFree in the tray (you are asked the first time) and minimising can go to the tray too.

The looks are the Android app's. The one that follows Android's wallpaper colours follows Windows' accent colour and its dark or light mode here and is called **System**.

## Brands

Every brand the Android app has, except **AirPods and the Beats on Apple's chips**, which Windows cannot reach: their settings channel is a kind of Bluetooth
connection Windows gives programs no way to open, short of a driver of its own, and PairFree does not install drivers. Earbuds that speak Bluetooth LE
(JBL's, QCY's and some of Sennheiser's) use Windows' Bluetooth LE support. Each brand's page under `docs/devices/` describes device support.

## Updates

On the first run PairFree asks whether it may look for a newer version on GitHub. It asks only GitHub's release page (`api.github.com`) and the files of the
release it names, through Windows' own secure connection, and sends nothing about you or your earbuds. You can turn it off or press **Check now** in Settings.
A new version is downloaded, **checked against a signature made with the project's release key**, and only then swapped in: the running program renames itself
aside, the new one takes its place and starts, and your `PairFree.toml` is never touched. PairFree has no other network traffic.

## What it does not do

- It does not install drivers, change how Windows plays sound, or choose a Bluetooth codec for you. The codec (SBC, AAC, aptX) is chosen by Windows.
- It does not record from the vendors' apps (almost none of the brands has a Windows app).
- Screen readers cannot read its screens yet.

## For developers

`docs/DESKTOP.md` is the plan and its status. `PairFree.exe --le-probe AABBCCDDEEFF` writes `le-probe.txt` beside the program: what Windows can do with the Bluetooth LE
side of the device at that address.
