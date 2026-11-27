# Soundcore 3.3.0: the Cmm2 command table with what each command does

Read from Soundcore 3.3.0 (`com.oceanwing.soundcore`, the newest app that is not packed; decompiled outside the repo, 2026-11-12): `devicecmd/manager/k/g.java` holds the 123 command prefixes (`08 EE 00 00 00 <group> <command>`, so PairFree's two-byte id is group and command), `k/d.java` (`Cmm2BtDispatch`) names each one's callback, and `k/h.java` (`Cmm2CmdService`) builds the writes. A write's body is what the table says (booleans are one byte, 1 on); the earbuds answer with the same two bytes and the changed fields.

The A3953 section of `PROTOCOL.md` uses this table. Commands in `data/soundcore/commands.json` are the ones PairFree sends; the rest are listed here as the vendor app has them, so nothing is "not read".

| Id | Constant | Callback in the app | Built by |
|---|---|---|---|
| 01 01 | x |  | a0() |
| 01 02 | y | getDeviceTwsStatusCallback | F1() |
| 01 03 | z | getDeviceBatteryCallback | x0() |
| 01 04 | A | getDeviceChargingCallback | D0() |
| 01 05 | B |  |  |
| 01 10 | N |  | k1() |
| 01 11 | H | getDeviceGameSwitchCallback |  |
| 01 12 | L | getMicrophoneCallback |  |
| 01 13 | M | getSuperHearingCallback |  |
| 01 14 | W | getCurModeCallback |  |
| 01 15 | a0 | getPlayPauseCallback | b0() |
| 01 7f | c0 | getDeviceSoundQualityCallBack | f1() |
| 01 81 | D | setMoreWearDetectionCallback | J0(boolean z) |
| 01 83 | E | setMoreTouchToneCallback | C0(boolean z) |
| 01 84 | C | setMoreSideToneCallback | r(boolean z) |
| 01 86 | F | setMoreAutoPowerOffCallback | v(boolean z, int i) |
| 01 87 | G | setDeviceGameModeCallback | U0(boolean z) |
| 01 8c | e0 | setInEarToneCallback | F0(boolean z) |
| 01 8d | f0 | setPromptsCallback | l1(int i) |
| 01 8f | g0 | setPowerOffCallback | B0(boolean z) |
| 01 a1 | I | setSuperHearingCallback | u(boolean z) |
| 01 a2 | J | setCallMixCallback | T(boolean z) |
| 01 a3 | K | setMicrophoneCallback | q0(boolean z) |
| 01 a4 | V | setSleepMusicIdCallback | x1(int i) |
| 01 a6 | O | syncTimeCallback | T0(int i) |
| 01 a7 | P | setAlarmClockCallback | L0(int[] iArr) |
| 01 a9 | Q | setCurModeCallback | t1(int i) |
| 01 aa | R | setSmartPlaySwitchCallback | W0(boolean z) |
| 01 ab | S | setCustomPlayTimeCallback | n0(int i) |
| 01 ac | T | setAdaptiveNoiseSwitchCallback | I0(boolean z) |
| 01 ad | U | setAbnormalSoundRemindCallback |  |
| 01 ae | h0 | setAmbientSoundNoticeSwitchCallback | E0(boolean z) |
| 01 af | i0 | setPowerOnBatteryNoticeSwitchCallback | t(boolean z) |
| 01 b0 | X | resetSleepTimeCallback |  |
| 01 b1 | Y | setVolumeCallback | R(boolean z, int i) |
| 01 b2 | Z | setPlayPauseCallback | H0(boolean z) |
| 01 b3 | b0 | setPlayCountdownTime | t0(int i) |
| 01 ff | d0 | setSoundQualityCallBack | A(boolean z) |
| 02 01 | j0 | getEqDetailsCallback |  |
| 02 06 | p0 | get3DEffectCallback |  |
| 02 81 | k0 | setEqDetailsCallback |  |
| 02 83 | l0 | setEqDetailsCallback | Q0(), o1(String str) |
| 02 84 | m0 | setDeviceBassUpCallback | q(boolean z) |
| 02 85 | n0 | setStereoExpansionCallback | N(boolean z, int i) |
| 02 86 | o0 | set3DEffectCallback | j1(boolean z) |
| 02 87 | q0 | setFullBandEqCallback |  |
| 03 81 | H0 | setHearIdSwitchCallbackCallback | n1(boolean z, boolean z2) |
| 03 82 | I0 | setHearIdDetailsCallback | h0(boolean z, int i, int i2, EQWaveValueBean eQWaveValueBean, EQWaveValueBean eQWaveValueB |
| 03 83 | J0 | startOrCancelHearIdTestCallback | V(boolean z) |
| 03 84 | K0 | resetHearIdDataCallback | Z(boolean z) |
| 03 85 | L0 | setHearIdAdvanceCallback | w(boolean z, int i, int i2, EQWaveValueBean eQWaveValueBean, EQWaveValueBean eQWaveValueBe |
| 03 86 | M0 |  | o(boolean z, boolean z2, boolean z3, boolean z4, int i, int i2, EQWaveValueBean eQWaveValu |
| 03 87 | N0 |  | o(boolean z, boolean z2, boolean z3, boolean z4, int i, int i2, EQWaveValueBean eQWaveValu |
| 03 88 | O0 | startOrCancelPersonalAncCallback | D(boolean z, int i, int i2) |
| 03 89 | P0 | resetPersonalAncCallback | S0() |
| 04 81 | r0 | setCustomBtnDetailsCallback | y(boolean z, int i, int i2, int i3) |
| 04 82 | s0 | resetCustomBtnCallback | L1(int i), r0() |
| 04 83 | t0 | setCustomBtnResponseCallback | M0(boolean z, int i, boolean z2, boolean z3) |
| 04 84 | u0 | setOldCustomBtnCallback |  |
| 04 85 | v0 | setCustomPressSensitivityCallback | G(int i) |
| 05 01 | w0 |  | K1() |
| 05 02 | z0 | getDeviceReportLogCallback |  |
| 05 81 | x0 | deleteUserDataCallback | e0() |
| 05 82 | y0 |  | R0(boolean z) |
| 06 01 | A0 | getAncDetailsCallback | w1() |
| 06 02 | C0 | getCustomBtnAncSelectedCallback |  |
| 06 81 | B0 | setAncDetailsCallback | q1(A3951ANC a3951anc) |
| 06 82 | D0 | setCustomBtnAncSelectedCallback | d1(boolean z, boolean z2, boolean z3, boolean z4, boolean z5, boolean z6) |
| 06 83 | E0 |  | g0(boolean z, int i) |
| 07 02 | g1 | getEarsIsInChargingCases | i0() |
| 09 01 | F0 | getWearFitDetectionCallback | s1(int i) |
| 09 02 | G0 | getWearStatusCallback |  |
| 0a 81 | Q0 | setVoiceControlCallback | P(boolean z, int i) |
| 0a 82 | R0 | setDeviceSuperVolumeCallback | J1(boolean z) |
| 0b 01 | S0 |  | L() |
| 0b 81 | T0 | disconnectMacDeviceCallback | x(String str) |
| 0b 82 | U0 | connectMacDeviceCallback | m1(String str) |
| 0b 83 | V0 | delMacDeviceFromHistoryCallback |  |
| 0b 84 | W0 | setSupportTwoCnnSwitchCallback | k0(boolean z) |
| 0b 85 | X0 | makeDeviceSearchedStatusCallback | J() |
| 0d 01 | w1 | getDailyCareBaseInfoCallback |  |
| 0d 03 | E1 | getNeckPostureRealTimeDataCallback |  |
| 0d 04 | F1 | getNeckPostureHistoryDataCallback | G1() |
| 0d 82 | x1 | setDailyCareHRDetectionSwitchCallback | Q(boolean z) |
| 0d 83 | y1 | setDailyCareSittingPostureSwitchCallback | s0(boolean z) |
| 0d 84 | z1 | setDCSedentaryReminderSwitchCallback | d0(boolean z) |
| 0d 85 | A1 | setDCHrAbnormalAlarmSwitchCallback |  |
| 0d 86 | B1 | setDCHrDetectionReportSwitchCallback |  |
| 0d 87 | D1 | setNeckPostureReportStatusCallback | X0(boolean z) |
| 0d 88 | C1 | startAllCalibrateCallback | C(boolean z, int i) |
| 0d 89 | G1 | deleteNeckPostureHistoryDataCallback | Q0() |
| 0e 01 | u1 | getLightEffectCallback |  |
| 0e 81 | v1 | setLightEffectCallback | O(int i, int i2) |
| 0f 01 | m1 | getDongleInfoCallback | s() |
| 0f 02 | n1 | getDongleLPWRSwitch | A0() |
| 0f 81 | o1 | editDongleNameCallback | m0(String str) |
| 0f 82 | p1 | selectDongleDeviceCallback | M(int i) |
| 0f 83 | q1 | disconnectDongleCallback | Y0() |
| 0f 84 | r1 | removeDongleCallback | o1(String str) |
| 0f 85 | s1 | connectDongleDeviceCallback | N0(String str) |
| 0f 86 | t1 | setDongleLPWRSwitch | setDongleLPWRSwitch(boolean z) |
| 10 01 | H1 | getSpatialAudioStatusCallback |  |
| 10 81 | I1 | setSpatialAudioStatusCallback | Z0(boolean z, int i, int i2) |
| 10 82 | J1 | setLowBatteryAlertSwitchCallback | p1(boolean z) |
| 10 83 | K1 | setAmbientSoundPromptSwitchCallback | P0(boolean z) |
| 10 84 | L1 | startInEarCalibrateCallback |  |
| 10 85 | M1 | setMediaModeSwitchCallback | K(boolean z) |
| 10 86 | N1 | setLeakySwitchCallback | y1(boolean z) |
| 10 87 | O1 | setThreeDEffectModeCallback |  |
| 10 89 | P1 | setFindDeviceCallback | B(boolean z, boolean z2) |
| 20 03 | l1 | getHearProtectCurDbCallBack | I1() |
| 20 81 | j1 | setHearProtectUpdateFrequencyCallBack | H(int i) |
| 20 82 | k1 | setHearProtectMaxValueCallBack | e1(boolean z, int i) |
| 20 84 | h1 | getWearCheckTestCallBack | l0(boolean z) |
| 20 86 | i1 | setHearProtectCallBack | w0(boolean z) |
| ff 01 | f1 | getBetaDataCollectionCallback | D1() |
| ff 03 | Z0 | getIrSensorDataCallBack |  |
| ff 04 | a1 |  | h1() |
| ff 05 | c1 | notifyCloseChargingBoxCallBack |  |
| ff 06 | d1 | notifyCloseChargingBoxAndDisCallBack |  |
| ff 81 | e1 | setBetaDataCollectionSwitchCallback | W(boolean z) |
| ff 83 | Y0 | setIrSensorSwitchCallBack | b1(boolean z) |
| ff 84 | b1 | setBetaDoubleClickStatusCallBack | S(int i) |
