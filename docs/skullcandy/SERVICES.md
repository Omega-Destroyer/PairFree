# Bragi BOS-RPC service inventory

Protocol facts from Skullcandy 5.0.6 bundled schemas, checked against `com/bragi/sdk/android/transport/rpc/q.java`. Research only. Schema source text is not redistributed. See [PROTOCOL.md](PROTOCOL.md) for framing, safety boundaries, and the difference between schema availability and per-model availability.

| Service id (decimal) | Schema | Methods (decimal ids) |
|---|---|---|
| 3 | `bos_action_button_rpc.proto` | `get_info` 1, `get_action` 2, `set_action` 3, `inject` 4, `invoke` 5, `subscribe` 6, `get_info_v2` 7 |
| 21 | `bos_audio_out_rpc.proto` | `mode_get` 1, `mode_set` 2 |
| 25 | `bos_authenticated_access.proto` | `hello` 1, `authenticate` 2, `trust_establishment_start` 3, `trust_establishment_commit` 4, `trust_establishment_nonce` 5, `trust_establishment_confirm` 6, `trust_establishment_abort` 7 |
| 7 | `bos_bluetooth.proto` | `devicename_get` 1, `devicename_set` 2 |
| 18 | `bos_calibration.proto` | `start` 1, `stop` 2, `info` 3, `reset` 4 |
| 23 | `bos_crusher.proto` | `config_get` 1, `state_get` 2, `state_set` 3 |
| 14 | `bos_data_streamer.proto` | `start` 1, `stop` 2, `state` 3, `list_algos` 4, `list_channels` 5, `subscribe` 6 |
| 2 | `bos_device_info_rpc.proto` | `dev_info` 1, `sys_info` 2, `batt_info` 3, `mode_info` 4, `time_get` 5, `time_set` 6 |
| 9 | `bos_feature_rpc.proto` | `get_info` 1, `set_state` 2 |
| 4 | `bos_hearing.proto` | `mode_read` 1, `mode_configure` 2, `mode_info` 3, `peq_read_state` 4, `peq_read_conf` 5, `peq_info` 6, `peq_configure` 7, `peq_enable` 8, `peq_disable` 9, `sidetone_read` 10, `sidetone_configure` 11, `sidetone_info` 12 |
| 12 | `bos_insertion.proto` | `mode_get` 1, `mode_set` 2, `trigger_get` 3, `trigger_set` 4 |
| 6 | `bos_language_rpc.proto` | `lang_info` 1, `lang_configure` 2 |
| 8 | `bos_low_latency_rpc.proto` | `get` 1, `set` 2 |
| 15 | `bos_media_player_rpc.proto` | `play` 2, `stop` 3, `pause` 4, `next` 5, `prev` 6, `get_playback_info` 7 |
| 10 | `bos_mimi_rpc.proto` | `info` 1, `reset` 2, `activate` 3, `deactivate` 4, `set_user_id` 5, `set_intensity` 6, `set_preset` 7 |
| 13 | `bos_power_manager.proto` | `timeout_get` 1 |
| 1 | `bos_rpc_echo.proto` | `echo` 1 |
| 22 | `bos_sounds.proto` | `options_get` 1, `options_set` 2 |
| 17 | `bos_spatial_audio.proto` | `info_get` 1, `audio_source_mode_set` 2 |
| 19 | `bos_spatial_audio_dolby.proto` | `hop_configure` 1, `hop_read` 2, `iht_configure` 3, `iht_read` 4, `iht_walk_detection_state` 5, `iht_ref_axis_set` 6, `iht_upright_calibrate_update` 7, `iht_manual_recentering_trigger` 8, `lwh_configure` 9, `lwh_read` 10, `set_client_dolby_audio_processing_support` 11, `read_client_dolby_audio_processing_support` 12, `set_hrtf_processing_override` 13, `read_hrtf_processing_override` 14 |
| 100 | `bos_test_diagnostics.proto` | `get_memory_info` 1, `get_task_info` 2, `get_models` 3, `get_pool_info` 4, `boot_count` 5 |
| 5 | `bos_voice_control_rpc.proto` | `info` 1, `disable` 2, `enable` 3, `streaming_stop` 4, `streaming_start` 5, `mode_set` 6, `subscribe` 7, `get_vc_crash_info` 100, `vc_reset_crash_info` 101 |

The authenticated-access service is local device pairing/session security, not permission to use vendor server credentials. Calibration, diagnostics, streaming, feature provisioning, voice audio upload, time writes and identity-related calls need separate review and are excluded from an initial implementation. `bos_rpc.proto` contains an example service in comments; it is not a real service and is excluded here.
