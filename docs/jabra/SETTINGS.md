# Jabra per-firmware settings reference

Derived from the APK-bundled settings forms. These are device/firmware capability snapshots, not blanket brand-wide promises. Each row gives the app setting, target GNP address, command, fixed selector bytes and option codes. All numbers in option and selector columns are hex.

A selector position is relative to the subcommand payload (position 1 follows the subcommand byte). A shared value position must be modified in the last read payload while preserving its other bytes. Addresses matter: headset settings commonly target 4, while DSP settings can target 8.

The JSON files preserve GUIDs, exact schema layouts, dependencies and special-handler flags. Names below are factual setting/option identifiers, not copied vendor descriptions. `[disabled]` marks an explicit false form enable flag. Empty/metadata widgets are retained for research and are not writable UI controls.

## Jabra Elite 10 — 11846_01-92_1.2.4

Reported capability flags: music_equalizer, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, customersupport, googlebistosupport, onheaddetection, spotifytap, spatialsoundformediasupport, spatialsoundheadtrackingformediasupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 8 Active — 11847_01-91_1.2.4

Reported capability flags: music_equalizer, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, customersupport, googlebistosupport, onheaddetection, spotifytap, spatialsoundformediasupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 10 Gen 2 — 11865_01-8F_1.0.8

Reported capability flags: music_equalizer, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, customersupport, googlebistosupport, onheaddetection, spotifytap, spatialsoundformediasupport, spatialsoundheadtrackingformediasupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| HearThrough_for_mono | 04 / 13/F0 |  | Off=00, On=01 |
| auto_volume_adjustment | 04 / 13/F1 |  | Off=00, On=01 |
| wind_noise_reduction | 04 / 13/EF | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 10 Gen 2 — 11865_01-8F_1.1.4

Reported capability flags: music_equalizer, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, customersupport, googlebistosupport, onheaddetection, spotifytap, spatialsoundformediasupport, spatialsoundheadtrackingformediasupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| HearThrough_for_mono | 04 / 13/F0 |  | Off=00, On=01 |
| auto_volume_adjustment | 04 / 13/F1 |  | Off=00, On=01 |
| wind_noise_reduction | 04 / 13/EF | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 8 Active Gen 2 — 11866_01-8E_1.0.8

Reported capability flags: music_equalizer, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, customersupport, googlebistosupport, onheaddetection, spotifytap, spatialsoundformediasupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| HearThrough_for_mono | 04 / 13/F0 |  | Off=00, On=01 |
| auto_volume_adjustment | 04 / 13/F1 |  | Off=00, On=01 |
| wind_noise_reduction | 04 / 13/EF | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 65e — 13655_01-51_2.25.0

Reported capability flags: remotemmmisupport, music_equalizer, settingschangenotification, voiceassistant, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Tone_setting | 08 / 13/38 | 1=01 | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Voice_guidance | 08 / 13/3A |  | On=01, Off=00 |
| SideTone | 08 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level_+6dB=06, SideTone_level_+3dB=03, SideTone_level_0dB_(Nominal)=00, SideTone_level_-3dB=FD, SideTone_level_-6dB=FA, SideTone_level_-9dB=F7 |
| Device_vibration | 08 / 13/58 |  | On=01, Off=00 |
| ANC | 08 / 13/87 |  | On=01, Off=00 |
| DEDICATED_CALL | 08 / 13/88 |  | On=01, Off=00 |
| Device_Name | 08 / 13/56 |  | read/metadata or device-defined |
| HearThrough | 08 / 13/76 | value at 1 | Off=00, On=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 65t — 9342_01-4C_2.40.1

Reported capability flags: music_equalizer, earbud_interconnection_status, remotemmmisupport, voiceassistant, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headphone_readouts | 08 / 13/3A |  | Off=, On= |
| Incoming_call_ID | 08 / 13/77 |  | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level5=04, HearThrough_level4=02, HearThrough_level3=00, HearThrough_level2=FE, HearThrough_level1=FC, HearThrough_level0=FA |
| SideTone | 08 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=04, SideTone_level4=02, SideTone_level3=00, SideTone_level2=FE, SideTone_level1=FC, SideTone_level0=FA |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Control_for_pause_and_resume_music | 08 / 13/83 |  | Enable=00, Disable=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 45e — 9347_01-57_1.28.0

Reported capability flags: music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headphone_readouts | 08 / 13/3A |  | Off=, On= |
| Incoming_call_ID | 08 / 13/77 |  | Off=00, On=01 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level_+6dB=06, SideTone_level_+3dB=03, SideTone_level_0dB_(Nominal)=00, SideTone_level_-3dB=FD, SideTone_level_-6dB=FA, SideTone_level_-9dB=F7 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite Active 65t — 9353_01-56_2.40.1

Reported capability flags: music_equalizer, step_rate, earbud_interconnection_status, remotemmmisupport, voiceassistant, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headphone_readouts | 08 / 13/3A |  | Off=, On= |
| Incoming_call_ID | 08 / 13/77 |  | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level5=04, HearThrough_level4=02, HearThrough_level3=00, HearThrough_level2=FE, HearThrough_level1=FC, HearThrough_level0=FA |
| SideTone | 08 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=04, SideTone_level4=02, SideTone_level3=00, SideTone_level2=FE, SideTone_level1=FC, SideTone_level0=FA |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Control_for_pause_and_resume_music | 08 / 13/83 |  | Enable=00, Disable=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 85h — 9355_01-50_1.6.3

Reported capability flags: music_equalizer, remotemmmisupport, onheaddetection, settingschangenotification, audiostreaming, voiceassistant, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| In-callaudio | 04 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Accept_reject_by_voice | 04 / 13/95 |  | on=01, off=00 |
| ANC_hearthrough | 04 / 13/87 |  | Off=, NoiseCancelling=, Surroundings_only=, Music_surroundings= |
| HearThrough_level | 04 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| SideTone | 04 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 04 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| Voice_guidance | 04 / 13/3A |  | Off=00, On=01 |
| Mute_reminder | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Head_detection | 04 / 13/92 |  | Off=, On= |
| Hold_call_when_removed | 04 / 13/8F | 1=00 | Off=00, On=01 |
| Auto_answer_call | 04 / 13/91 | 1=01 | Off=00, On=01 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=0 |
| Language | 04 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Firmware_version | 04 / 02/03 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |

## Jabra Evolve 65e — 9358_01-58_2.29.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, voiceassistant, settingschangenotification, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Audio_protection | 08 / 13/01, 15/0C |  | Audio_protection_Basic_PeakStop_protection=00, Audio_protection_IntelliTone_average_daily_audio_exposure=03, Audio_protection_Australian_acoustic_safety_guideline_(G.616)=05 |
| Auto_answer_call | 08 / 13/86 | 1=00 | On=01, Off=00 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Device_Name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| Mute_reminder | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| SideTone | 08 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| Headphone_readouts | 08 / 13/3A |  | Off=00, On=01 |
| vibration | 08 / 13/58 |  | On=01, Incoming_call=02, Off=00 |
| Prioritize_bluetooth_device_with_call | 08 / 13/88 |  | On=01, Off=00 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Optimize_for_Microsoft_Teams | 08 / 13/97 |  | Off=00, On=01 |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite Active 45e — 9362_01-5A_2.22.0

Reported capability flags: music_equalizer, remotemmmisupport, voiceassistant, settingschangenotification, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Voice_guidance | 08 / 13/3A |  | Off=00, On=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve 65t — 9367_01-5D_2.40.1

Reported capability flags: music_equalizer, earbud_interconnection_status, remotemmmisupport, voiceassistant, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Equalizer_mode_for_calls | 04 / 13/15 |  | Equalizer_mode_for_calls_Normal=00, Equalizer_mode_for_calls_Bass=01, Equalizer_mode_for_calls_Treble=02 |
| SideTone | 04 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 04 / 13/7C | value at 2 | SideTone_level5=04, SideTone_level4=02, SideTone_level3=00, SideTone_level2=FE, SideTone_level1=FC, SideTone_level0=FA |
| Voice_guidance | 04 / 13/3A |  | Off=00, On=01 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |
| HearThrough | 04 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 04 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 04 / 13/81 |  | HearThrough_level_6=04, HearThrough_level_5=02, HearThrough_level_4=00, HearThrough_level_3=FE, HearThrough_level_2=FC, HearThrough_level_1=FA |
| Incoming_call_ID | 04 / 13/77 |  | On=00, Off=01 |
| DEDICATED_CALL | 04 / 13/88 |  | On=01, Off=00 |
| Control_for_pause_and_resume_music | 04 / 13/83 |  | On=01, Off=00 |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Optimize_for_Microsoft_Teams | 04 / 13/97 |  | Off=00, On=01 |
| Firmware_version | 04 / 02/03 |  | read/metadata or device-defined |
| Language | 04 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411 |
| Part_number | 04 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 65 — 9379_01-63_2.6.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Auto_resume_audio_by_motion_detection | 08 / 13/8E | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 45h — 9381_01-65_2.3.9

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, customersupport, mysound.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Mute_reminder | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Power_Off_Timer | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Equalizer_mode_for_calls | 08 / 13/15 |  | Equalizer_mode_for_calls_Normal=00, Equalizer_mode_for_calls_Bass=01, Equalizer_mode_for_calls_Treble=02 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 75t — 9383_01-60_2.0.0

Reported capability flags: music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, customersupport, mysound, uiconfigurablebuttons, ambiencemodes, ffancsupport, loopmodessupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| DEDICATED_CALL | 04 / 13/88 |  | On=01, Off=00 |
| Voice_guidance | 04 / 13/3A |  | Off=00, On=01 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level5=04, HearThrough_level4=02, HearThrough_level3=00, HearThrough_level2=FE, HearThrough_level1=FC, HearThrough_level0=FA |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=04, SideTone_level4=02, SideTone_level3=00, SideTone_level2=FE, SideTone_level1=FC, SideTone_level0=FA |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Control_for_pause_and_resume_music | 08 / 13/83 |  | Enable=00, Disable=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 65 — 9395_01-64_2.6.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Auto_resume_audio_by_motion_detection | 08 / 13/8E | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 65 — 9397_01-64_2.6.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Auto_resume_audio_by_motion_detection | 08 / 13/8E | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 65 — 9399_01-63_2.6.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Auto_resume_audio_by_motion_detection | 08 / 13/8E | 1=00 | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 85 — 9401_01-67_1.3.8

Reported capability flags: music_equalizer, remotemmmisupport, onheaddetection, settingschangenotification, voiceassistant, manualbusylightsupport, busylightsupport, loggingsupport, customersupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=01, Off=00 |
| Head_detection | 04 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 04 / 13/8F | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=0 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Boom_arm_guidance | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| ANC_hearthrough | 04 / 13/87 |  | NoiseCancelling=, Surroundings_only=, Music_surroundings=, Off= |
| HearThrough_level | 04 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| Mute_reminder | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 04 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Earcup_microphone | 04 / 13/AC |  | On=01, Off=00 |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 85 — 9403_01-67_1.3.8

Reported capability flags: music_equalizer, remotemmmisupport, onheaddetection, settingschangenotification, voiceassistant, manualbusylightsupport, busylightsupport, loggingsupport, customersupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=01, Off=00 |
| Head_detection | 04 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 04 / 13/8F | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=0 |
| Boom_arm_guidance | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| ANC_hearthrough | 04 / 13/87 |  | NoiseCancelling=, Surroundings_only=, Music_surroundings=, Off= |
| HearThrough_level | 04 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| Mute_reminder | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 04 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Earcup_microphone | 04 / 13/AC |  | On=01, Off=00 |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |

## Jabra Elite Active 75t — 9420_01-61_2.0.0

Reported capability flags: music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, customersupport, mysound, uiconfigurablebuttons, ambiencemodes, ffancsupport, loopmodessupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| DEDICATED_CALL | 04 / 13/88 |  | On=01, Off=00 |
| Voice_guidance | 04 / 13/3A |  | Off=00, On=01 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level5=04, HearThrough_level4=02, HearThrough_level3=00, HearThrough_level2=FE, HearThrough_level1=FC, HearThrough_level0=FA |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=04, SideTone_level4=02, SideTone_level3=00, SideTone_level2=FE, SideTone_level1=FC, SideTone_level0=FA |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Control_for_pause_and_resume_music | 08 / 13/83 |  | Enable=00, Disable=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 85t — 9421_01-68_2.0.0

Reported capability flags: music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, mysound, uiconfigurablebuttons, ambiencemodes, loopmodessupport, sealingtest, amasupport, customersupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Dedicated_call | 04 / 13/88 |  | On=01, Off=00 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Control_for_pause_and_resume_music | 08 / 13/83 |  | Enable=00, Disable=01 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 75 — 9433_01-6D_1.7.4

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, ambiencemodes, ambiencemodesincall, mysound, amasupport, customersupport, onheaddetection, loopmodessupport, nativebluetooth, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| ANC_hearthrough | 08 / 13/87 |  | NoiseCancelling=, Surroundings_only=, Music_surroundings=, Off= |
| HT_Level | 08 / 13/BE |  / dedicated handler | HT_Level4=00, HT_Level3=01, HT_Level2=02, HT_Level1=03, HT_Level0=04 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Head_detection | 08 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 08 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| Earcup_microphones_enable | 08 / 13/AC |  | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 75 — 9433_01-6D_1.8.5

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, ambiencemodes, ambiencemodesincall, mysound, amasupport, customersupport, onheaddetection, loopmodessupport, nativebluetooth, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| ANC_hearthrough | 08 / 13/87 |  | NoiseCancelling=, Surroundings_only=, Music_surroundings=, Off= |
| HT_Level | 08 / 13/BE |  / dedicated handler | HT_Level4=00, HT_Level3=01, HT_Level2=02, HT_Level1=03, HT_Level0=04 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Head_detection | 08 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 08 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| Earcup_microphones_enable | 08 / 13/AC |  | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 75 — 9435_01-6D_1.7.4

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, ambiencemodes, ambiencemodesincall, mysound, amasupport, customersupport, onheaddetection, loopmodessupport, nativebluetooth, nativebluetoothteamscertified, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| ANC_hearthrough | 08 / 13/87 |  | NoiseCancelling=, Surroundings_only=, Music_surroundings=, Off= |
| HT_Level | 08 / 13/BE |  / dedicated handler | HT_Level4=00, HT_Level3=01, HT_Level2=02, HT_Level1=03, HT_Level0=04 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Head_detection | 08 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 08 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| Earcup_microphones_enable | 08 / 13/AC |  | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 75 — 9435_01-6D_1.8.5

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, ambiencemodes, ambiencemodesincall, mysound, amasupport, customersupport, onheaddetection, loopmodessupport, nativebluetooth, nativebluetoothteamscertified, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Answer_call_by_rotating_boom_arm | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_mute | 08 / 13/98 |  | On=01, Off=00 |
| Boom_arm_guidance | 08 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Audio_announcements | 08 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Audio_protection_setting | 08 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 08 / 13/3F |  | On=FF, Off=00 |
| Headset_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Headset_busylight | 08 / 13/39 |  | Off=00, On=01 |
| ANC_hearthrough | 08 / 13/87 |  | NoiseCancelling=, Surroundings_only=, Music_surroundings=, Off= |
| HT_Level | 08 / 13/BE |  / dedicated handler | HT_Level4=00, HT_Level3=01, HT_Level2=02, HT_Level1=03, HT_Level0=04 |
| Mute_reminder_tone | 08 / 13/1E, 15/0B |  | Off=00, On=14 |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Head_detection | 08 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 08 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| Earcup_microphones_enable | 08 / 13/AC |  | Off=00, On=01 |
| SideTone | 08 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 08 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Tone_setting | 08 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |
| Call_control_with_softphone | 08 / 13/4C |  | Off=, On= |
| Ringtone_in_headset | 08 / 13/13 | 1=00 | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Voice_announcements_language | 08 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 7 Pro — 9439_01-6E_2.2.0

Reported capability flags: mysound, sealingtest, music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, amasupport, customersupport, googlebistosupport, ffancsupport, onheaddetection.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level5=04, HearThrough_level4=02, HearThrough_level3=00, HearThrough_level2=FE, HearThrough_level1=FC, HearThrough_level0=FA |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 7 Active — 9447_01-6E_2.2.0

Reported capability flags: mysound, sealingtest, music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, amasupport, customersupport, googlebistosupport, ffancsupport, onheaddetection.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| Firmware_version | 08 / 02/03 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Speak2 75 — 9455_08-0B_1.1.6

Reported capability flags: pairinglistsupport, customersupport, music_equalizer, voiceassistant, productregistration, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Device_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Bluetooth_support | 08 / 13/63 | 1=00 | Off=, On= |
| Shared_use | 08 / 13/5E |  | Off=, On= |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Voice_guidance | 08 / 13/3A |  | Voice=01, Tone=00 |
| Voice_Indicator | 08 / 13/E3 |  | Off=00, On=03 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Sleep_delay | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Softphone_integration | 08 / 13/4C |  | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Programmable_button_tap_action | 08 / 13/27 | 1=00, 2=00 | PlayPause=, Start_voice_instruction=, Speed_dial=, No_function= |
| SpeedDial_setting | 08 / 13/2A |  | read/metadata or device-defined |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |
| Voice_guidance_language | 08 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412, Modern_Spanish=0C0A |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |

## Jabra Speak2 75 — 9457_08-0B_1.1.6

Reported capability flags: pairinglistsupport, customersupport, music_equalizer, voiceassistant, productregistration, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Device_name | 08 / 02/17, 13/56 |  | read/metadata or device-defined |
| Bluetooth_support | 08 / 13/63 | 1=00 | Off=, On= |
| Shared_use | 08 / 13/5E |  | Off=, On= |
| Pairing_list | 08 / 0D/28 |  | read/metadata or device-defined |
| Voice_guidance | 08 / 13/3A |  | Voice=01, Tone=00 |
| Voice_Indicator | 08 / 13/E3 |  | Off=00, On=03 |
| Auto_reject_call | 08 / 13/3C |  | On=01, Off=00 |
| Sleep_delay | 08 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Softphone_integration | 08 / 13/4C |  | Off=00, On=01 |
| Prioritize_Computer_Audio | 08 / 13/99 |  | Off=00, On=01 |
| Programmable_button_tap_action | 08 / 13/27 | 1=00, 2=00 | PlayPause=, Start_voice_instruction=, Speed_dial=, No_function= |
| SpeedDial_setting | 08 / 13/2A |  | read/metadata or device-defined |
| Windows_audio_device_name | 08 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 08 / 13/1D |  | Part_number_Default= |
| Voice_guidance_language | 08 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412, Modern_Spanish=0C0A |
| FWU_lock | 08 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 55 — 9459_01-76_1.1.6

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, amasupport, ambiencemodes, nativebluetooth, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | Off=01, On=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 55 — 9461_01-76_1.1.6

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, amasupport, ambiencemodes, nativebluetooth, nativebluetoothteamscertified, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | Off=01, On=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 55 — 9462_01-77_1.1.6

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, amasupport, ambiencemodes, nativebluetooth, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | Off=01, On=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 55 — 9464_01-77_1.1.6

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, amasupport, ambiencemodes, nativebluetooth, nativebluetoothteamscertified, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | Off=01, On=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 Buds — 9483_01-80_1.1.0

Reported capability flags: mysound, sealingtest, music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, amasupport, customersupport, googlebistosupport, ffancsupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 04 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=, HT_ANC_Off=, HT_Off= |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_Level4=00, HT_Level3=01, HT_Level2=02, HT_Level1=03, HT_Level0=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_Level4=00, ANC_Level3=01, ANC_Level2=02, ANC_Level1=03, ANC_Level0=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Voice=01, Audio_announcements_Tones=00, Audio_announcements_Off=02 |
| In-callaudio | 04 / 13/15 |  | Normal=00, Enhance_bass=01, Enhance_clarity=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Audio_protection | 04 / 13/01, 15/0C |  | Audio_protection_Basic_PeakStop_protection=00, Audio_protection_IntelliTone_average_daily_audio_exposure=03, Audio_protection_Australian_acoustic_safety_guideline_(G.616)=05 |
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Head_detection | 04 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 04 / 13/8F | 1=00 | Off=00, On=01 |
| Auto_answer_call | 04 / 13/91 | 1=01 | Off=00, On=01 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |
| Firmware_version | 04 / 02/03 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Language | 04 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |

## Jabra Evolve2 Buds — 9484_01-80_1.1.0

Reported capability flags: mysound, sealingtest, music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, amasupport, customersupport, googlebistosupport, ffancsupport, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | On=, Off= |
| SideTone_level | 04 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=, HT_ANC_Off=, HT_Off= |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_Level4=00, HT_Level3=01, HT_Level2=02, HT_Level1=03, HT_Level0=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_Level4=00, ANC_Level3=01, ANC_Level2=02, ANC_Level1=03, ANC_Level0=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Voice=01, Audio_announcements_Tones=00, Audio_announcements_Off=02 |
| In-callaudio | 04 / 13/15 |  | Normal=00, Enhance_bass=01, Enhance_clarity=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Audio_protection | 04 / 13/01, 15/0C |  | Audio_protection_Basic_PeakStop_protection=00, Audio_protection_IntelliTone_average_daily_audio_exposure=03, Audio_protection_Australian_acoustic_safety_guideline_(G.616)=05 |
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Head_detection | 04 / 13/92 |  | Off=, On= |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Mute_call | 04 / 13/8F | 1=00 | Off=00, On=01 |
| Auto_answer_call | 04 / 13/91 | 1=01 | Off=00, On=01 |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_Sound_Modes=05, Action_Voice_Assistant=04, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_None=00 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_Accept_End=07, Action_Accept_Hold=08, Action_Reject=06, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C, Action_None=00 |
| Automatic_UI_Swap | 04 / 13/DB |  | Off=00, On=01 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_Teams=00 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_Sound_Modes=00 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00 |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_Accept_End=00 |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_Reject=00 |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00 |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_Mute=00 |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_End_Resume=00 |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |
| Firmware_version | 04 / 02/03 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Language | 04 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |

## Jabra Evolve2 55 — 9490_01-76_1.1.6

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, amasupport, ambiencemodes.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | Off=01, On=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 55 — 9492_01-76_1.1.6

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, amasupport, ambiencemodes.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection_setting | 04 / 13/01, 15/0C |  | Audio_protection_setting_Basic_protection=00, Audio_protection_setting_IntelliTone_average_daily_audio_exposure=03, Audio_protection_setting_Australian_acoustic_safety_guideline_G_616=05 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | Off=01, On=04 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Elite 7 Sport — 9494_01-83_2.3.0

Reported capability flags: mysound, sealingtest, music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, amasupport, customersupport, googlebistosupport, ffancsupport, onheaddetection.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Elite 7 Sport — 9494_01-83_2.4.0

Reported capability flags: mysound, sealingtest, music_equalizer, remotemmmisupport, settingschangenotification, voiceassistant, uiconfigurablebuttons, ambiencemodes, loopmodessupport, amasupport, customersupport, googlebistosupport, ffancsupport, onheaddetection.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Sleep_delay | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_off=0 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Voice_announcements_language | 04 / 13/08 |  | English_(US)=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Music_context_left_button_tap_action | 04 / 13/9A | 1=00, 2=00, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_double_tap_action | 04 / 13/9A | 1=00, 2=00, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_left_button_triple_tap_action | 04 / 13/9A | 1=00, 2=00, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_tap_action | 04 / 13/9A | 1=00, 2=01, 3=00 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_double_tap_action | 04 / 13/9A | 1=00, 2=01, 3=01 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Music_context_right_button_triple_tap_action | 04 / 13/9A | 1=00, 2=01, 3=02 | Action_None=00, Action_Play_Pause=01, Action_Next_Track=02, Action_Prev_Track=03, Action_Voice_Assistant=04, Action_Sound_Modes=05 |
| Call_incoming_context_left_button_tap_action | 04 / 13/9A | 1=01, 2=00, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_double_tap_action | 04 / 13/9A | 1=01, 2=00, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_left_button_triple_tap_action | 04 / 13/9A | 1=01, 2=00, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_tap_action | 04 / 13/9A | 1=01, 2=01, 3=00 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_double_tap_action | 04 / 13/9A | 1=01, 2=01, 3=01 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Call_incoming_context_right_button_triple_tap_action | 04 / 13/9A | 1=01, 2=01, 3=02 | Action_None=00, Action_Reject=06, Action_Accept_End=07, Action_Accept_Hold=08, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_tap_action | 04 / 13/9A | 1=02, 2=00, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_double_tap_action | 04 / 13/9A | 1=02, 2=00, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_left_button_triple_tap_action | 04 / 13/9A | 1=02, 2=00, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_tap_action | 04 / 13/9A | 1=02, 2=01, 3=00 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_double_tap_action | 04 / 13/9A | 1=02, 2=01, 3=01 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| Ongoing_call_context_right_button_triple_tap_action | 04 / 13/9A | 1=02, 2=01, 3=02 | Action_None=00, Action_End_Resume=09, Action_Hold_Resume=0A, Action_Mute=0B, Action_Sidetone=0C |
| SideTone | 08 / 13/7C | value at 1 | On=00, Off=01 |
| SideTone_level | 08 / 13/7C | value at 2 | SideTone_level5=06, SideTone_level4=03, SideTone_level3=00, SideTone_level2=FD, SideTone_level1=FA, SideTone_level0=F7 |
| In-callaudio | 08 / 13/15 |  | Enhance_clarity=02, Normal=00, Enhance_bass=01 |
| Head_detection | 08 / 13/92 |  | Off=00, On=01 |
| Auto_answer_call | 08 / 13/91 | 1=01 | Off=00, On=01 |
| Mute_call | 08 / 13/8F | 1=00 | Off=00, On=01 |
| HearThrough | 08 / 13/76 | value at 1 | Off=, On= |
| HearThrough_mode | 08 / 13/76 | value at 2 | Music_surroundings=00, Surroundings_only=01 |
| HearThrough_level | 08 / 13/81 |  | HearThrough_level6=F4, HearThrough_level5=F6, HearThrough_level4=F8, HearThrough_level3=FA, HearThrough_level2=FC, HearThrough_level1=FE, HearThrough_level0=00 |
| Firmware_version | 08 / 02/03, 28/01 |  | read/metadata or device-defined |
| Language | 08 / 13/08 |  | English=0409, German=0407, French=040C, Chinese=0804, Japanese=0411, Korean=0412 |
| Part_number | 08 / 13/1D |  | Part_number_Default= |

## Jabra Evolve2 65 Flex — 9495_01-82_1.1.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, ambiencemodes, loopmodessupport, amasupport, nativebluetooth, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=F9, HT_ANC_Off=E4, HT_Off=F4 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | ActiveNoiseCancelling=04, HearThrough=02, Off=01 |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_LevelHighest=00, HT_LevelHigh=01, HT_LevelMedium=02, HT_LevelLow=03, HT_LevelLowest=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_LevelHighest=00, ANC_LevelHigh=01, ANC_LevelMedium=02, ANC_LevelLow=03, ANC_LevelLowest=04 |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection | 04 / 13/01, 15/0C |  | PeakStop=00, IntelliTone=03, G616=05 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 65 Flex — 9497_01-82_1.1.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, ambiencemodes, loopmodessupport, amasupport, nativebluetooth, nativebluetoothteamscertified, wukongsupport.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=F9, HT_ANC_Off=E4, HT_Off=F4 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | ActiveNoiseCancelling=04, HearThrough=02, Off=01 |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_LevelHighest=00, HT_LevelHigh=01, HT_LevelMedium=02, HT_LevelLow=03, HT_LevelLowest=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_LevelHighest=00, ANC_LevelHigh=01, ANC_LevelMedium=02, ANC_LevelLow=03, ANC_LevelLowest=04 |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection | 04 / 13/01, 15/0C |  | PeakStop=00, IntelliTone=03, G616=05 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 65 Flex — 9507_01-82_1.1.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, ambiencemodes, loopmodessupport, amasupport, nativebluetooth.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=F9, HT_ANC_Off=E4, HT_Off=F4 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | ActiveNoiseCancelling=04, HearThrough=02, Off=01 |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_LevelHighest=00, HT_LevelHigh=01, HT_LevelMedium=02, HT_LevelLow=03, HT_LevelLowest=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_LevelHighest=00, ANC_LevelHigh=01, ANC_LevelMedium=02, ANC_LevelLow=03, ANC_LevelLowest=04 |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection | 04 / 13/01, 15/0C |  | PeakStop=00, IntelliTone=03, G616=05 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Evolve2 65 Flex — 9509_01-82_1.1.0

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, ambiencemodes, loopmodessupport, amasupport, nativebluetooth, nativebluetoothteamscertified.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=F9, HT_ANC_Off=E4, HT_Off=F4 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | ActiveNoiseCancelling=04, HearThrough=02, Off=01 |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_LevelHighest=00, HT_LevelHigh=01, HT_LevelMedium=02, HT_LevelLow=03, HT_LevelLowest=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_LevelHighest=00, ANC_LevelHigh=01, ANC_LevelMedium=02, ANC_LevelLow=03, ANC_LevelLowest=04 |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection | 04 / 13/01, 15/0C |  | PeakStop=00, IntelliTone=03, G616=05 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |

## Jabra Elite Flex — 9520_01-82_1.2.12

Reported capability flags: music_equalizer, busylightsupport, remotemmmisupport, settingschangenotification, voiceassistant, loggingsupport, manualbusylightsupport, customersupport, mysound, ambiencemodes, loopmodessupport, amasupport, nativebluetooth.

| Setting | Address / command | Selectors / value position | Options |
|---|---|---|---|
| Headset_name | 04 / 02/17, 13/56 |  | read/metadata or device-defined |
| Sound_Mode_Loop | 04 / 13/BF |  / dedicated handler | HT_ANC=F9, HT_ANC_Off=E4, HT_Off=F4 |
| ANC_HT_Mode | 04 / 13/BE |  / dedicated handler | ActiveNoiseCancelling=, HearThrough=, Off= |
| HT_Mixed | 04 / 13/BE |  / dedicated handler | Off=00, On=01 |
| HT_Level | 04 / 13/BE |  / dedicated handler | HT_LevelHighest=00, HT_LevelHigh=01, HT_LevelMedium=02, HT_LevelLow=03, HT_LevelLowest=04 |
| ANC_Level | 04 / 13/BE |  / dedicated handler | ANC_LevelHighest=00, ANC_LevelHigh=01, ANC_LevelMedium=02, ANC_LevelLow=03, ANC_LevelLowest=04 |
| SideTone | 04 / 13/7C | value at 1 | Off=, On= |
| SideTone_level | 04 / 13/7C | value at 2 | Sidetone_6dB_(high)=06, Sidetone_3dB=03, Sidetone_0dB_(normal)=00, Sidetone_-3dB=FD, Sidetone_-6dB=FA, Sidetone_-9dB_(low)=F7 |
| Audio_protection | 04 / 13/01, 15/0C |  | PeakStop=00, IntelliTone=03, G616=05 |
| Auto_pause_resume_music | 04 / 13/8E | 1=00 | Off=00, On=01 |
| Audio_announcements | 04 / 13/3A |  | Audio_announcements_Tones=00, Audio_announcements_Voice=01, Audio_announcements_Off=02 |
| Tone_setting | 04 / 13/15 |  | Tone_setting_Normal=00, Tone_setting_Bass=01, Tone_setting_Treble=02 |
| Headset_busylight | 04 / 13/39 |  | Off=00, On=01 |
| Accept_incoming_call_on_undock | 04 / 13/45 |  | Off=00, On=01 |
| Answer_call_by_rotating_boom_arm | 04 / 13/98 |  | On=, Off= |
| Call_answered_by_boom_arm_audio_announcement | 04 / 13/BC |  | Sound_fx=00, Prompts=01, Off=02 |
| Boom_arm_mute | 04 / 13/98 |  | On=01, Off=00 |
| Mute_reminder_tone | 04 / 13/1E, 15/0B |  | Off=00, On=14 |
| Auto_reject_call | 04 / 13/3C |  | On=01, Off=00 |
| Auto_sleep | 04 / 13/90 |  | Sleep_1=03, Sleep_2=06, Sleep_3=0C, Sleep_4=18, Sleep_5=30, Sleep_6=48, Sleep_7=60, Sleep_off=00 |
| Button_sounds_setting | 04 / 13/3F |  | On=FF, Off=00 |
| Pairing_list | 04 / 0D/28 |  | read/metadata or device-defined |
| Call_control_with_softphone | 04 / 13/4C |  | Off=, On= |
| Prioritize_Computer_Audio | 04 / 13/99 |  | Off=00, On=01 |
| Ringtone_in_headset | 04 / 13/13 | 1=00 | Off=00, On=01 |
| Windows_audio_device_name | 04 / 02/15 |  | read/metadata or device-defined |
| Firmware_version | 04 / 02/03, 28/01 |  | read/metadata or device-defined |
| Part_number | 04 / 13/1D |  | Part_number_Default= |
| Voice_announcements_language | 04 / 13/08 |  | English=0409, Chinese=0804, German=0407, French=040C, Japanese=0411, Korean=0412 |
| FWU_lock | 04 / 0F/34 |  | On=01, Off=00 |
