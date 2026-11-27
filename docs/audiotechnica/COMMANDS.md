# Audio-Technica command-number inventory

Protocol facts extracted from Connect 2.8.0 bytecode. Decimal constants were converted to hex; these are identifiers, not executable vendor code. See [PROTOCOL.md](PROTOCOL.md) before using them. Matching read/write numbers across families do not imply matching payloads. Rows include restricted operations to make exclusion explicit; catalog presence does not authorize sending them.


## Realtek vendor subcommands

| Command | Hex id | Restriction |
|---|---|---|
| `GET_CODEC_MODE` | `15` |  |
| `SET_CODEC_MODE` | `16` |  |
| `GET_STREAMING_CODEC` | `17` |  |
| `GET_AMBIENCE_MODE` | `1C` |  |
| `SET_AMBIENCE_MODE` | `1D` |  |
| `GET_VOICE_ASSISTANT` | `29` |  |
| `SET_VOICE_ASSISTANT` | `2A` |  |
| `GET_VOLUME_STEP` | `2F` |  |
| `SET_VOLUME_STEP` | `30` |  |
| `GET_VOLUME_LEVEL` | `31` |  |
| `SET_VOLUME_LEVEL` | `32` |  |
| `GET_VOLUME_BALANCE` | `34` |  |
| `SET_VOLUME_BALANCE` | `35` |  |
| `GET_MULTIPOINT_SETTING` | `36` |  |
| `SET_MULTIPOINT_SETTING` | `37` |  |
| `DEVICE_RESET` | `39` | Exclude from initial control implementation |
| `AV_REMOTE_CONTROL` | `3A` |  |
| `GET_AUDIO_PROMPT_CONTROL` | `3B` |  |
| `SET_AUDIO_PROMPT_CONTROL` | `3C` |  |
| `SAVE_EQ_PARAMETER` | `3D` |  |
| `GET_EQ_NICKNAME` | `3E` |  |
| `SET_EQ_NICKNAME` | `3F` |  |
| `GET_LOW_LATENCY_MODE` | `40` |  |
| `SET_LOW_LATENCY_MODE` | `41` |  |
| `GET_GFPS_MODEL_ID` | `4C` | Exclude from initial control implementation |
| `GET_NUMBER_OF_APP_CONNECTIONS` | `4D` | Private data or connection ownership; separate review |
| `GET_PAIRED_DEVICE_LIST` | `50` | Private data or connection ownership; separate review |
| `GET_PAIRED_DEVICE_INFO` | `51` | Private data or connection ownership; separate review |
| `CONNECT_TO_PAIRED_DEVICE` | `52` | Private data or connection ownership; separate review |
| `DISCONNECT_PAIRED_DEVICE` | `53` | Private data or connection ownership; separate review |
| `CHANGE_PROFILE_OF_CONNECTED_DEVICE` | `54` |  |
| `REMOVE_PAIRED_DEVICE` | `57` | Private data or connection ownership; separate review |
| `ENTER_PAIRING_MODE` | `59` |  |
| `CANCEL_PAIRING_MODE` | `5A` |  |
| `GET_APP_BD_ADDRESS` | `5B` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_TITLE` | `5C` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ALBUM` | `5D` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ARTIST` | `5E` | Private data or connection ownership; separate review |
| `GET_SIDETONE_SETTING` | `60` |  |
| `SET_SIDETONE_SETTING` | `61` |  |
| `GET_TT_STREAMING_LEVEL` | `65` |  |
| `SET_TT_STREAMING_LEVEL` | `66` |  |
| `GET_KEY_ASSIGN_SETTINGS` | `67` |  |
| `SET_KEY_ASSIGN_SETTINGS` | `68` |  |
| `GET_TOUCH_SENSITIVITY` | `69` |  |
| `SET_TOUCH_SENSITIVITY` | `6A` |  |
| `GET_PLAYING_MEDIA_STATE` | `6B` | Private data or connection ownership; separate review |
| `GET_PRODUCT_ID` | `6C` |  |
| `GET_BREDR_CONNECTION_STATE` | `6D` |  |
| `GET_AUTO_PLAY_PAUSE_MODE` | `6E` |  |
| `SET_AUTO_PLAY_PAUSE_MODE` | `6F` |  |
| `GET_EAR_DETECTION_POWER_OFF_TIMER` | `70` |  |
| `SET_EAR_DETECTION_POWER_OFF_TIMER` | `71` |  |
| `GET_EAR_DETECTION_MODE` | `72` |  |
| `SET_EAR_DETECTION_MODE` | `73` |  |
| `GET_SCO_LINK_STATE` | `76` |  |
| `GET_TALK_THROUGH_STATE` | `79` |  |
| `SET_TALK_THROUGH_STATE` | `7A` |  |
| `FACTORY_RESET` | `7B` | Exclude from initial control implementation |
| `GET_OPERATION_LOCK` | `7C` |  |
| `SET_OPERATION_LOCK` | `7D` |  |
| `GET_MULTI_TOUCH_INTERVAL` | `7E` |  |
| `SET_MULTI_TOUCH_INTERVAL` | `7F` |  |
| `START_TOUCH_TEST_MODE` | `80` | Exclude from initial control implementation |
| `CANCEL_TOUCH_TEST_MODE` | `82` | Exclude from initial control implementation |
| `GET_HT_LEVEL` | `83` |  |
| `SET_HT_LEVEL` | `84` |  |
| `GET_TT_EQ_INDEX` | `87` |  |
| `SET_TT_EQ_INDEX` | `88` |  |
| `GET_VP_ID` | `89` |  |
| `GET_FW_MODE` | `90` | Exclude from initial control implementation |
| `SET_FW_MODE` | `91` | Exclude from initial control implementation |
| `SET_GFPS_MODEL_ID` | `92` | Exclude from initial control implementation |
| `GET_GFPS_PUBLIC_KEY` | `93` | Exclude from initial control implementation |
| `SET_GFPS_PUBLIC_KEY` | `94` | Exclude from initial control implementation |
| `GET_GFPS_PRIVATE_KEY` | `95` | Exclude from initial control implementation |
| `SET_GFPS_PRIVATE_KEY` | `96` | Exclude from initial control implementation |
| `GET_APT_GAIN` | `97` |  |
| `SET_APT_GAIN` | `98` |  |
| `GET_TOUCH_SENSOR_REGISTER_VALUE` | `99` | Exclude from initial control implementation |
| `SET_TOUCH_SENSOR_REGISTER_VALUE` | `9A` | Exclude from initial control implementation |
| `NOTIFICATION_OF_TOUCH_SENSOR_REG_VALS` | `9C` |  |
| `GET_EQ_APP_PARAMETER_ID` | `A0` |  |
| `SET_EQ_APP_PARAMETER_ID` | `A1` |  |
| `GET_SECONDARY_RSSI` | `A6` |  |
| `GET_AMBIENÇE_MODE_CYCLE` | `A7` |  |
| `SET_AMBIENCE_MODE_CYCLE` | `A8` |  |
| `GET_DSP_CONFIG_IMAGE` | `A9` | Exclude from initial control implementation |
| `SET_DSP_CONFIG_IMAGE` | `AA` | Exclude from initial control implementation |
| `GET_PRODUCT_SETTINGS` | `AB` |  |
| `GET_AUTO_POWER_OFF_SETTING` | `AC` |  |
| `SET_AUTO_POWER_OFF_SETTING` | `AD` |  |
| `GET_BUDS_TIMER_PARAMETERS` | `C0` |  |
| `SET_BUDS_TIMER_PARAMETERS` | `C1` |  |
| `SET_BUDS_TIMER_STATE` | `C3` |  |
| `PLAY_BUDS_TIMER_SOUNDE` | `C4` |  |
| `GET_LLAPT_MIC_GAIN` | `C5` |  |
| `SET_LLAPT_MIC_GAIN` | `C6` |  |
| `GET_NOISE_CANCEL_TYPE` | `C7` |  |
| `SET_NOISE_CANCEL_TYPE` | `C8` |  |
| `GET_FORCE_SENSOR_SENSITIVITY` | `C9` |  |
| `SET_FORCE_SENSOR_SENSITIVITY` | `CA` |  |
| `GET_MULTI_FORCE_SENSOR_INTERVAL` | `CB` |  |
| `SET_MULTI_FORCE_SENSOR_INTERVAL` | `CC` |  |
| `GET_AUDIO_PROMPT_LEVEL` | `CD` |  |
| `SET_AUDIO_PROMPT_LEVEL` | `CE` |  |
| `GET_TUBE_AMP_SOUND` | `CF` |  |
| `SET_TUBE_AMP_SOUND` | `D0` |  |
| `GET_AUDIO_PATH_SELECT` | `D1` |  |
| `SET_AUDIO_PATH_SELECT` | `D2` |  |
| `NOTIFICATION_OF_AUDIO_PATH_SELECT` | `D3` |  |
| `GET_MUTE_AND_EXTERNAL_FOCUS` | `D4` |  |
| `NOTIFICATION_OF_MUTE_AND_EXTERNAL_FOCUS` | `D5` |  |
| `GET_COLOR_ID` | `D6` |  |
| `GET_LEA_BONDED_DEVICE_LIST` | `D7` | Private data or connection ownership; separate review |
| `GET_LEA_BONDED_DEVICE_INFO` | `D8` | Private data or connection ownership; separate review |
| `NOTIFICAION_OF_LEA_DEVICE_CONNECTION_STATE` | `D9` |  |
| `GET_BROADCAST_STATE` | `DA` |  |
| `NOTIFICATION_OF_BROADCAST_STATE` | `DB` |  |
| `GET_DEFAULT_HS_SCENE` | `DC` |  |
| `SET_DEFAULT_HS_SCENE` | `DD` |  |
| `GET_HS_SCENE_ROTATION_LIST` | `DE` |  |
| `SET_HS_SCENE_ROTATION_LIST` | `DF` |  |
| `GET_EAR_DETECTION_STATE` | `E0` |  |
| `NOTIFICATION_OF_EAR_DETECTION_STATE` | `E1` |  |
| `GET_EAR_DETECTION_SENSITIVITY` | `E2` |  |
| `SET_EAR_DETECTION_SENSITIVITY` | `E3` |  |
| `GET_EAR_DETECTION_OPERATION_LOCK` | `E4` |  |
| `SET_EAR_DETECTION_OPERATION_LOCK` | `E5` |  |
| `GET_EAR_DETECTION_HS_MUTE` | `E6` |  |
| `SET_EAR_DETECTION_HS_MUTE` | `E7` |  |
| `GET_CXD3784_REGISTER` | `E8` | Exclude from initial control implementation |
| `SET_CXD3784_REGISTER` | `E9` | Exclude from initial control implementation |
| `SET_SAVE_CXD3784_REGISTER` | `EA` | Exclude from initial control implementation |
| `GET_STREAMING_SAMPLING_FREQUENCY` | `EB` |  |
| `PLAY_AUDIO_PROMPT` | `EC` |  |
| `GET_LEA_MODE` | `ED` |  |
| `SET_LEA_MODE` | `EE` |  |
| `GET_HS_CURRENT_VOLUME` | `EF` |  |
| `SET_HS_CURRENT_VOLUME` | `F0` |  |
| `NOTIFICATION_OF_HS_CURRENT_VOLUME` | `F1` |  |
| `SET_ENTER_BATTERY_ISOLATION_MODE` | `F2` | Exclude from initial control implementation |
| `GET_LIMIT_SOUND_LEAKAGE` | `F5` |  |
| `SET_LIMIT_SOUND_LEAKAGE` | `F6` |  |
| `NOTIFICATION_OF_LIMIT_SOUND_LEAKAGE` | `F7` |  |
| `SET_COLOR_ID` | `F9` | Exclude from initial control implementation |

## GAIA v2 AT subcommands

| Command | Hex id | Restriction |
|---|---|---|
| `GET_KEY_ASSIGN_MODE` | `0C` |  |
| `SET_KEY_ASSIGN_MODE` | `0D` |  |
| `GET_CODEC_MODE` | `15` |  |
| `SET_CODEC_MODE` | `16` |  |
| `GET_STREAMING_CODEC` | `17` |  |
| `SET_BOOT_MODE` | `18` | Exclude from initial control implementation |
| `SET_DEVICE_NAME_1` | `19` |  |
| `SET_DEVICE_NAME_2` | `1A` |  |
| `SET_DEVICE_NAME_3` | `1B` |  |
| `GET_AMBIENCE_MODE` | `1C` |  |
| `SET_AMBIENCE_MODE` | `1D` |  |
| `SEND_AMBIENCE_MODE` | `1E` |  |
| `SEND_QUICK_HEAR_THROUGH_STATE` | `1F` |  |
| `GET_SLAVE_STATE` | `20` |  |
| `SEND_SLAVE_STATE` | `22` |  |
| `GET_SLAVE_FIRMWARE_VERSION` | `25` |  |
| `ENTER_SLAVE_TO_PAIRING_MODE` | `26` |  |
| `GET_DEVICE_NAME` | `27` |  |
| `GET_BATTERY_INFO` | `28` |  |
| `GET_VOICE_ASSISTANT` | `29` |  |
| `SET_VOICE_ASSISTANT` | `2A` |  |
| `GET_AUTO_POWER_OFF_TIMER` | `2C` |  |
| `SET_AUTO_POWER_OFF_TIMER` | `2D` |  |
| `GET_TWSPLUS_MODE` | `2E` |  |
| `GET_VOLUME_STEP` | `2F` |  |
| `SET_VOLUME_STEP` | `30` |  |
| `GET_VOLUME_LEVEL` | `31` |  |
| `SET_VOLUME_LEVEL` | `32` |  |
| `SEND_VOLUME_LEVEL` | `33` |  |
| `GET_VOLUME_BALANCE` | `34` |  |
| `SET_VOLUME_BALANCE` | `35` |  |
| `GET_MULTI_POINT_SETTING` | `36` |  |
| `SET_MULTI_POINT_SETTING` | `37` |  |
| `SEND_MULTI_POINT_SETTING` | `38` |  |
| `DEVICE_RESET` | `39` | Exclude from initial control implementation |
| `AV_REMOTE_CONTROL` | `3A` |  |
| `GET_AUDIO_PROMPT_CONTROL` | `3B` |  |
| `SET_AUDIO_PROMPT_CONTROL` | `3C` |  |
| `SAVE_EQ_PARAMETER` | `3D` |  |
| `GET_EQ_NICKNAME` | `3E` |  |
| `SET_EQ_NICKNAME` | `3F` |  |
| `GET_LOW_LATENCY_MODE` | `40` |  |
| `SET_LOW_LATENCY_MODE` | `41` |  |
| `SEND_LOW_LATENCY_MODE` | `42` |  |
| `GET_AUTO_PLAY_PAUSE_MODE` | `43` |  |
| `SET_AUTO_PLAY_PAUSE_MODE` | `44` |  |
| `GET_EAR_DETECTION_POWER_OFF_TIMER` | `45` |  |
| `SET_EAR_DETECTION_POWER_OFF_TIMER` | `46` |  |
| `GET_EAR_DETECTION_MODE` | `47` |  |
| `SET_EAR_DETECTION_MODE` | `48` |  |
| `SEND_EAR_DETECTION_MODE` | `49` |  |
| `GET_GFPS_MODEL_ID` | `4C` | Exclude from initial control implementation |
| `GET_BUILD_NUMBER` | `4E` |  |
| `GET_CALL_NOISE_CANCELLING` | `4F` |  |
| `SET_CALL_NOISE_CANCELLING` | `50` |  |
| `GET_PLAYING_MEDIA_STATE` | `51` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_TITLE` | `52` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ALBUM` | `53` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ARTIST` | `54` | Private data or connection ownership; separate review |
| `SEND_PLAYING_MEDIA_STATE` | `55` | Private data or connection ownership; separate review |
| `GET_PAIRED_DEVICE_LIST` | `56` | Private data or connection ownership; separate review |
| `GET_PAIRED_DEVICE_INFO` | `57` | Private data or connection ownership; separate review |
| `CONNECT_TO_PAIRED_DEVICE` | `58` | Private data or connection ownership; separate review |
| `DISCONNECT_PAIRED_DEVICE` | `59` | Private data or connection ownership; separate review |
| `CHANGE_PROFILE_OF_CONNECTED_DEVICE` | `5A` |  |
| `NOTIFICATION_OF_DEVICE_CONNECTION_STATE` | `5B` |  |
| `NOTIFICATION_OF_ACTIVE_DEVICE` | `5C` |  |
| `REMOVE_PAIRED_DEVICE` | `5D` | Private data or connection ownership; separate review |
| `NOTIFICATION_OF_REMOVED_PAIRED_DEVICE` | `5E` | Private data or connection ownership; separate review |
| `ENTER_PAIRING_MODE_FOR_MULTIPOINT` | `5F` |  |
| `CANCEL_PAIRING_MODE_FOR_MULTIPOINT` | `60` |  |
| `GET_APP_BD_ADDRESS` | `61` | Private data or connection ownership; separate review |
| `GET_CALL_STATE` | `62` |  |
| `NOTIFICATION_OF_CALL_STATE` | `63` |  |

## GAIA v3 basic (feature 70)

| Command | Hex id | Restriction |
|---|---|---|
| `GET_PRIMARY_FIRMWARE_VERSION` | `00` |  |
| `GET_BUILD_NUMBER` | `01` |  |
| `GET_SECONDARY_FIRMWARE_VERSION` | `02` |  |
| `GET_DEVICE_NAME` | `03` |  |
| `SET_DEVICE_NAME` | `04` |  |
| `GET_BATTERY_INFO` | `05` |  |
| `GET_STREAMING_CODEC` | `06` |  |
| `GET_CODEC_MODE` | `07` |  |
| `SET_CODEC_MODE` | `08` |  |
| `GET_KEY_ASSIGN_MODE` | `09` |  |
| `SET_KEY_ASSIGN_MODE` | `0A` |  |
| `GET_VOLUME_STEP` | `0B` |  |
| `SET_VOLUME_STEP` | `0C` |  |
| `GET_VOLUME_LEVEL` | `0D` |  |
| `SET_VOLUME_LEVEL` | `0E` |  |
| `GET_VOLUME_BALANCE` | `0F` |  |
| `SET_VOLUME_BALANCE` | `10` |  |
| `GET_LOW_LATENCY_MODE` | `11` |  |
| `SET_LOW_LATENCY_MODE` | `12` |  |
| `AV_REMOTE_CONTROL` | `13` |  |
| `GET_AUDIO_PROMPT_CONTROL` | `14` |  |
| `SET_AUDIO_PROMPT_CONTROL` | `15` |  |
| `GET_EAR_DETECTION_MODE` | `16` |  |
| `SET_EAR_DETECTION_MODE` | `17` |  |
| `GET_AUTO_PLAY_PAUSE_MODE` | `18` |  |
| `SET_AUTO_PLAY_PAUSE_MODE` | `19` |  |
| `GET_EAR_DETECTION_POWER_OFF_TIMER` | `1A` |  |
| `SET_EAR_DETECTION_POWER_OFF_TIMER` | `1B` |  |
| `ENTER_PAIRING_MODE` | `1C` |  |
| `GET_GFPS_MODEL_ID` | `1D` | Exclude from initial control implementation |
| `GET_FUNCTION_KEY_SETTING` | `1E` |  |
| `SET_FUNCTION_KEY_SETTING` | `1F` |  |
| `GET_TOUCH_SENSITIVITY` | `20` |  |
| `SET_TOUCH_SENSITIVITY` | `21` |  |
| `GET_PLAYING_MEDIA_TITLE` | `22` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ALBUM` | `23` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ARTIST` | `24` | Private data or connection ownership; separate review |
| `GET_LR_INFO` | `26` |  |
| `DEVICE_RESET` | `27` | Exclude from initial control implementation |
| `GET_PLAYING_MEDIA_STATE` | `28` | Private data or connection ownership; separate review |
| `GET_VOICE_ASSISTANT` | `29` |  |
| `SET_VOICE_ASSISTANT` | `2A` |  |
| `GET_CODEC_MODE_V2` | `2B` |  |
| `SET_CODEC_MODE_V2` | `2C` |  |
| `GET_KEY_ASSIGN_SETTINGS` | `2D` |  |
| `SET_KEY_ASSIGN_SETTINGS` | `2E` |  |
| `GET_ASSISTANT_SETTINGS` | `2F` |  |
| `SET_ASSISTANT_SETTINGS` | `30` |  |
| `GET_MULTI_CLICK_INTERVAL` | `31` |  |
| `SET_MULTI_CLICK_INTERVAL` | `32` |  |
| `GET_VP_ID` | `33` |  |

## GAIA v3 ANC (feature 71)

| Command | Hex id | Restriction |
|---|---|---|
| `GET_AMBIENCE_MODE` | `01` |  |
| `SET_AMBIENCE_MODE` | `02` |  |
| `START_NC_OPTIMIZATION` | `04` | Separate calibration/measurement review |
| `GET_NC_PERSONALIZATION_STATE` | `06` | Separate calibration/measurement review |
| `START_NC_PERSONALIZATION` | `07` | Separate calibration/measurement review |
| `RESET_NC_PERSONALIZATION` | `09` | Exclude from initial control implementation |
| `GET_QHT_MODE` | `0A` |  |
| `SET_QHT_MODE` | `0B` |  |
| `GET_QHT_STREAMING_MODE` | `0C` |  |
| `SET_QHT_STREAMING_MODE` | `0D` |  |
| `CANCEL_NC_PERSONALIZATION` | `0E` | Separate calibration/measurement review |
| `GET_AMBIENCE_MODE_CYCLE` | `0F` |  |
| `SET_AMBIENCE_MODE_CYCLE` | `10` |  |

## GAIA v3 EQ (feature 72)

| Command | Hex id | Restriction |
|---|---|---|
| `GET_EQ_STATE` | `01` |  |
| `SET_EQ_STATE` | `02` |  |
| `GET_EQ_BANK` | `03` |  |
| `SET_EQ_BANK` | `04` |  |
| `GET_EQ_PARAMETER` | `05` |  |
| `SET_EQ_PARAMETER` | `06` |  |
| `SAVE_EQ_PARAMETER` | `07` |  |
| `GET_EQ_NICKNAME` | `08` |  |
| `SET_EQ_NICKNAME` | `09` |  |

## GAIA v3 connection (feature 73)

| Command | Hex id | Restriction |
|---|---|---|
| `GET_SECONDARY_STATE` | `01` |  |
| `GET_PAIRED_DEVICE_LIST` | `02` | Private data or connection ownership; separate review |
| `GET_PAIRED_DEVICE_INFO` | `03` | Private data or connection ownership; separate review |
| `CONNECT_TO_PAIRED_DEVICE` | `04` | Private data or connection ownership; separate review |
| `DISCONNECT_PAIRED_DEVICE` | `05` | Private data or connection ownership; separate review |
| `CHANGE_PROFILE_OF_CONNECTED_DEVICE` | `06` |  |
| `REMOVE_PAIRED_DEVICE` | `09` | Private data or connection ownership; separate review |
| `ENTER_PAIRING_MODE_FOR_MULTIPOINT` | `0A` |  |
| `CANCEL_PAIRING_MODE_FOR_MULTIPOINT` | `0B` |  |
| `GET_APP_BD_ADDRESS` | `0C` | Private data or connection ownership; separate review |
| `GET_NUMBER_OF_APP_CONNECTIONS` | `0D` | Private data or connection ownership; separate review |

## GAIA v3 calls (feature 74)

| Command | Hex id | Restriction |
|---|---|---|
| `GET_CALL_STATE` | `01` |  |
| `GET_HFP_CODEC` | `02` |  |
| `GET_SIDETONE_SETTING` | `03` |  |
| `SET_SIDETONE_SETTING` | `04` |  |
| `GET_CALL_EQ_PRESET` | `05` | Exclude from initial control implementation |
| `SET_CALL_EQ_PRESET` | `06` | Exclude from initial control implementation |

## Airoha basic

| Command | Hex id | Restriction |
|---|---|---|
| `GET_PRIMARY_FIRMWARE_VERSION` | `00` |  |
| `GET_BUILD_NUMBER` | `01` |  |
| `GET_SECONDARY_FIRMWARE_VERSION` | `02` |  |
| `GET_DEVICE_NAME` | `03` |  |
| `SET_DEVICE_NAME` | `04` |  |
| `GET_BATTERY_INFO` | `05` |  |
| `GET_STREAMING_CODEC` | `06` |  |
| `GET_VOLUME_STEP` | `07` |  |
| `SET_VOLUME_STEP` | `08` |  |
| `GET_VOLUME_LEVEL` | `09` |  |
| `SET_VOLUME_LEVEL` | `0A` |  |
| `GET_VOLUME_BALANCE` | `0B` |  |
| `SET_VOLUME_BALANCE` | `0C` |  |
| `GET_LOW_LATENCY_MODE` | `0D` |  |
| `SET_LOW_LATENCY_MODE` | `0E` |  |
| `AV_REMOTE_CONTROL` | `0F` |  |
| `GET_EAR_DETECTION_MODE` | `10` |  |
| `SET_EAR_DETECTION_MODE` | `11` |  |
| `GET_AUTO_PLAY_PAUSE_MODE` | `12` |  |
| `SET_AUTO_PLAY_PAUSE_MODE` | `13` |  |
| `GET_EAR_DETECTION_POWER_OFF_TIMER` | `14` |  |
| `SET_EAR_DETECTION_POWER_OFF_TIMER` | `15` |  |
| `GET_GFPS_MODEL_ID` | `16` | Exclude from initial control implementation |
| `GET_TOUCH_SENSITIVITY` | `17` |  |
| `SET_TOUCH_SENSITIVITY` | `18` |  |
| `GET_PLAYING_MEDIA_TITLE` | `19` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ALBUM` | `1A` | Private data or connection ownership; separate review |
| `GET_PLAYING_MEDIA_ARTIST` | `1B` | Private data or connection ownership; separate review |
| `GET_LR_INFO` | `1D` |  |
| `DEVICE_RESET` | `1E` | Exclude from initial control implementation |
| `GET_PLAYING_MEDIA_STATE` | `1F` | Private data or connection ownership; separate review |
| `GET_CODEC_MODE_V2` | `20` |  |
| `SET_CODEC_MODE_V2` | `21` |  |
| `GET_KEY_ASSIGN_SETTINGS` | `22` |  |
| `SET_KEY_ASSIGN_SETTINGS` | `23` |  |
| `GET_ASSISTANT_SETTINGS` | `24` |  |
| `SET_ASSISTANT_SETTINGS` | `25` |  |
| `GET_MULTI_CLICK_INTERVAL` | `26` |  |
| `SET_MULTI_CLICK_INTERVAL` | `27` |  |
| `GET_VP_LANGUAGE` | `28` |  |
| `SET_VP_LANGUAGE` | `29` |  |
| `GET_MULTIPOINT_SETTING` | `2A` |  |
| `SET_MULTIPOINT_SETTING` | `2B` |  |
| `GET_PRODUCT_ID` | `2C` |  |
| `FACTORY_RESET` | `2D` | Exclude from initial control implementation |
| `GET_OPERATION_LOCK` | `2E` |  |
| `SET_OPERATION_LOCK` | `2F` |  |
| `GET_AUTO_POWER_OFF_SETTING` | `30` |  |
| `SET_AUTO_POWER_OFF_SETTING` | `31` |  |
| `GET_BUDS_TIMER_PARAMETERS` | `32` |  |
| `SET_BUDS_TIMER_PARAMETERS` | `33` |  |
| `SET_BUDS_TIMER_STATE` | `35` |  |
| `PLAY_BUDS_TIMER_SOUND` | `36` |  |
| `GET_SECONDARY_RSSI` | `37` |  |
| `GET_COLOR_ID` | `38` |  |
| `GET_BUDS_ATTACH_SETTING` | `3B` |  |
| `SET_BUDS_ATTAÇH_SETTING` | `3C` |  |
| `GET_LEA_MODE` | `3D` |  |
| `SET_LEA_MODE` | `3E` |  |
| `GET_BGM_MODE` | `3F` |  |
| `SET_BGM_MODE` | `40` |  |

## Airoha ANC

| Command | Hex id | Restriction |
|---|---|---|
| `GET_AMBIENCE_MODE` | `00` |  |
| `SET_AMBIENCE_MODE` | `01` |  |
| `START_NC_OPTIMIZATION` | `02` | Separate calibration/measurement review |
| `GET_NC_PERSONALIZATION_STATE` | `04` | Separate calibration/measurement review |
| `START_NC_PERSONALIZATION` | `05` | Separate calibration/measurement review |
| `RESET_NC_PERSONALIZATION` | `07` | Exclude from initial control implementation |
| `CANCEL_NC_PERSONALIZATION` | `08` | Separate calibration/measurement review |
| `GET_AMBIENCE_MODE_CYCLE` | `09` |  |
| `SET_AMBIENCE_MODE_CYCLE` | `0A` |  |
| `GET_TALK_THROUGH_STATE` | `0B` |  |
| `SET_TALK_THROUGH_STATE` | `0C` |  |
| `GET_TALK_THROUGH_STREAMING_LEVEL` | `0D` |  |
| `SET_TALK_THROUGH_STREAMING_LEVEL` | `0E` |  |
| `GET_TALK_THROUGH_TYPE` | `0F` |  |
| `SET_TALK_THROUGH_TYPE` | `10` |  |
| `GET_HT_LEVEL` | `11` |  |
| `SET_HT_LEVEL` | `12` |  |

## Airoha EQ

| Command | Hex id | Restriction |
|---|---|---|
| `GET_EQ_NICKNAME` | `00` |  |
| `SET_EQ_NICKNAME` | `01` |  |
| `GET_EQ_STATE` | `02` |  |
| `SET_EQ_STATE` | `03` |  |
| `GET_EQ_PARAMETER_ID` | `04` |  |
| `SET_EQ_PARAMETER_ID` | `05` |  |

## Airoha connection

| Command | Hex id | Restriction |
|---|---|---|
| `GET_SECONDARY_STATE` | `00` |  |
| `GET_PAIRED_DEVICE_LIST` | `01` | Private data or connection ownership; separate review |
| `GET_PAIRED_DEVICE_INFO` | `02` | Private data or connection ownership; separate review |
| `CONNECT_TO_PAIRED_DEVICE` | `03` | Private data or connection ownership; separate review |
| `DISCONNECT_PAIRED_DEVICE` | `04` | Private data or connection ownership; separate review |
| `CHANGE_PROFILE_OF_CONNECTED_DEVICE` | `05` |  |
| `REMOVE_PAIRED_DEVICE` | `08` | Private data or connection ownership; separate review |
| `ENTER_PAIRING_MODE_FOR_MULTIPOINT` | `09` |  |
| `CANCEL_PAIRING_MODE_FOR_MULTIPOINT` | `0A` |  |
| `GET_APP_BD_ADDRESS` | `0B` | Private data or connection ownership; separate review |
| `GET_NUMBER_OF_APP_CONNECTIONS` | `0C` | Private data or connection ownership; separate review |
| `GET_LEA_BONDED_DEVICE_LIST` | `0E` | Private data or connection ownership; separate review |
| `GET_LEA_BONDED_DEVICE_INFO` | `0F` | Private data or connection ownership; separate review |
| `CONNECT_TO_LEA_BONDED_DEVICE` | `10` | Private data or connection ownership; separate review |
| `DISCONNECT_LEA_BONDED_DEVICE` | `11` | Private data or connection ownership; separate review |
| `REMOVE_LEA_BONDED_DEVICE` | `13` | Private data or connection ownership; separate review |
| `GET_LEA_BROADCAST_STATE` | `14` |  |

## Airoha factory command namespace (excluded)

| Command | Hex id | Restriction |
|---|---|---|
| `GET_MODE` | `00` |  |
| `SET_MODE` | `01` |  |
| `GET_COLOR_ID` | `02` |  |
| `SET_COLOR_ID` | `03` | Exclude from initial control implementation |
| `GET_FW_VERSION` | `04` |  |
| `GET_BATTERY_LEVEL` | `05` |  |
| `GET_ROLE` | `06` |  |
| `GET_ADDRESS` | `07` | Private data or connection ownership; separate review |
| `GET_RESET` | `09` | Exclude from initial control implementation |
| `SET_SHIPPING_MODE` | `0A` | Exclude from initial control implementation |
| `SET_AMBIENCE_MODE` | `0F` |  |
| `GET_DEVICE_NAME` | `10` |  |

## GAIA v2 outer commands (vendor 000A)

These wrap the GAIA v2 AT subcommand payload or perform a separate standard GAIA operation. `AT_COMMAND` is not an AT subcommand.

| Command | Hex id |
|---|---|
| `SET_AUDIO_PROMPT_LANGUAGE` | `0212` |
| `GET_AUDIO_PROMPT_LANGUAGE` | `0292` |
| `AV_REMOTE_CONTROL` | `021F` |
| `GET_AUDIO_PROMPT_CONTROL` | `028A` |
| `SET_AUDIO_PROMPT_CONTROL` | `020A` |
| `GET_CURRENT_BATTERY_LEVEL` | `0302` |
| `GET_APPLICATION_VERSION` | `0304` |
| `DEVICE_RESET` | `0202` |
| `ENTER_BLUETOOTH_PAIRING_MODE` | `021D` |
| `SET_DATA_END_POINT_MODE` | `022E` |
| `GET_BOOT_MODE` | `0282` |
| `GET_USER_EQ_CONTROL` | `02A0` |
| `SET_USER_EQ_CONTROL` | `0220` |
| `GET_EQ_CONTROL` | `0294` |
| `SET_EQ_CONTROL` | `0214` |
| `GET_USER_EQ_PARAMETER` | `029A` |
| `GET_USER_EQ_PARAMETER_GROUP` | `029B` |
| `SET_USER_EQ_PARAMETER` | `021A` |
| `SET_USER_EQ_PARAMETER_GROUP` | `021B` |
| `RETRIEVE_PS_KEY` | `0710` |
| `AT_COMMAND` | `0800` |
