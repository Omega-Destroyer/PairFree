# Soundcore 5.0.35-435: registered models and protocol routes

The app registration table was executed in an offline API 30 research emulator. **161 registration keys** were returned by `SeriesHelp.getInitProductModel()`. These are keys, including regional aliases and separate cases, rather than 161 distinct retail earbud products. UUID values below came from model getters; service/manager routes came from the recovered model bodies. No device manager was initialized or connected.

`?` in the service column means routing is delegated to the listed manager, not that there are no commands. A common builder exposes more commands than every individual model supports; inheritance alone is not proof of a feature. See [recovery evidence](APK_RECOVERY_5_0_35.md) and [SDK profiles](SDK_PROFILES_5_0_35.md).

| Registration | Model class | SPP UUID | Manager route | Explicit command / analysis services |
| --- | --- | --- | --- | --- |
| A3004 | A3004ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3004 | Cmm2BtDeviceManager | A3004AnalysisService, A3004CmdService |
| A3004311 | A3004311ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B300B | Cmm2BtDeviceManager | A3004AnalysisService, A3004CmdService |
| A3004C | A3004CProductModel | 0CF12D31-FAC3-4553-BD80-D6832E73004C | Cmm2BtDeviceManager | A3004CAnalysisService, A3004cCmdService |
| A3005 | A3005ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3005 | Cmm2BtDeviceManager | A3005AnalysisService, A3005CmdService |
| A3012 | A3012ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3012 | Cmm2BtDeviceManager | A3012AnalysisService, A3012CmdService |
| A3025 | A3025ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B302F | Cmm2BtDeviceManager | A3025AnalysisService, A3025CmdService |
| A3027 | A3027ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3028 | A3028ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager, Cmm2BtDeviceManager | ? |
| A3028CC | A3028CCProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B302A | A3951DeviceManager, Cmm2BtDeviceManager | ? |
| A3029 | A3029ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3029XR | A3029XProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3030 | A3030ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3033 | A3033ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3033 | A3951DeviceManager | ? |
| A3033EU | A3033EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3033 | A3951DeviceManager | ? |
| A3035 | A3035ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3035 | Cmm2BtDeviceManager | A3035AnalysisService, A3035CmdService |
| A3035CS | A3035CSProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B303A | Cmm2BtDeviceManager | A3035AnalysisService, A3035CmdService |
| A3040 | A3040ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3040 | Cmm2BtDeviceManager | A3040AnalysisService, A3040CmdService |
| A3045 | A3045ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3025 | A3951DeviceManager | ? |
| A3045ZA1 | A3045ZA1ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B304B | Cmm2BtDeviceManager | A3004AnalysisService, A3004CmdService |
| A3062 | A3062ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3062 | Cmm2BtDeviceManager | A3062AnalysisService, A3062CmdService |
| A3116 | A3116ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3116 | A3116BtDeviceManager | ? |
| A3117 | A3117ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3117 | A3145DeviceManager | ? |
| A3118 | A3118ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3118 | A3145DeviceManager | ? |
| A3119 | A3119ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3119 | A3145DeviceManager | ? |
| A3123 | A3123ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3123 | A3116BtDeviceManager | ? |
| A3125 | A3125ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3125 | A3145DeviceManager | ? |
| A3126 | A3126ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3126 | A3145DeviceManager | ? |
| A3127 | A3127ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3127 | A3145DeviceManager | ? |
| A3129 | A3129ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3129 | A3145DeviceManager | ? |
| A3130 | A3130ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3130 | Cmm2BtDeviceManager | A3130AnalysisService, A3130CmdService |
| A3131 | A3131ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3131 | Cmm2BtDeviceManager | A3131AnalysisService, A3131CmdService |
| A3133 | A3133ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3133 | Cmm2BtDeviceManager | A3133AnalysisService, A3133CmdService |
| A3134 | A3134ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3134 | Cmm2BtDeviceManager | A3134AnalysisService, A3134CmdService |
| A3134Z11 | A3134Z11ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B31B4 | Cmm2BtDeviceManager | A3134AnalysisService, A3134CmdService |
| A3135 | A3135ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3135 | Cmm2BtDeviceManager | A3135AnalysisService, A3135CmdService |
| A3136 | A3136ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3136 | Cmm2BtDeviceManager | A3136AnalysisService, A3136CmdService |
| A3138 | A3138ProduceModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3138 | Cmm2BtDeviceManager | A3138AnalysisService, A3138CmdService |
| A3138J11 | A3138J11ProduceModel | 0CF12D31-FAC3-4553-BD80-D6832E7B31B8 | Cmm2BtDeviceManager | A3138AnalysisService, A3138CmdService |
| A3145 | A3145ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3145 | A3145DeviceManager | ? |
| A3148 | A3148ProduceModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3148 | Cmm2BtDeviceManager | A3148AnalysisService, A3148CmdService |
| A3161 | A3161ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3161 | BaseDeviceManager | ? |
| A3162 | A3162ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3162 | BaseDeviceManager | ? |
| A3163 | A3163ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3163 | Inherited | ? |
| A3165 | A3165ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3165 | A3165DeviceManager | ? |
| A3166 | A3166ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3166 | Cmm2BtDeviceManager | A3166AnalysisService, A3166CmdService |
| A3167 | A3167ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3167 | A3145DeviceManager | ? |
| A3171 | A3171ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3171 | Cmm2BtDeviceManager | A3172AnalysisService, A3172CmdService |
| A3172 | A3172ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3172 | Cmm2BtDeviceManager | A3172AnalysisService, A3172CmdService |
| A31A3 | A31A3ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B31A3 | Cmm2BtDeviceManager | A31A3AnalysisService, A31A3CmdService |
| A31A3012 | A31A3012ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B31B3 | Cmm2BtDeviceManager | A31A3AnalysisService, A31A3CmdService |
| A31X1 | A31X1ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B31B1 | Cmm2BtDeviceManager | A3172AnalysisService, A3172CmdService |
| A3201 | A3201ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3201 | A3201DeviceManager | ? |
| A3300 | A3300ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3300 | A3300DeviceManager | ? |
| A3301 | A3301ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3301 | A3301DeviceManager | ? |
| A3302 | A3302ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3302 | A3301DeviceManager | ? |
| A3330 | A3330ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3330 | Cmm2BtDeviceManager | A3330AnalysisService, A3330CmdService |
| A3331 | A3331ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3331 | Cmm2BtDeviceManager | A3331AnalysisService, A3331CmdService |
| A3372 | A3372ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3372 | A3372BtDeviceManager | ? |
| A3388 | A3388ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3388 | Cmm2BtDeviceManager | A3388AnalysisService, A3388CmdService |
| A3388P42 | A3388P42ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7C3388 | Cmm2BtDeviceManager | A3388AnalysisService, A3388CmdService |
| A3390 | A3390ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3390 | RaveDeviceManager | ? |
| A3390E | A3390EProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B339A | A3395DeviceManager | ? |
| A3390J | A3390JProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B339C | A3395DeviceManager | ? |
| A3391 | A3391ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3391 | RaveDeviceManager | ? |
| A3391E | A3391PProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B339B | A3395DeviceManager | ? |
| A3392 | A3392ProduceModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3392 | RaveDeviceManager | ? |
| A3393 | A3393ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3393 | RaveDeviceManager | ? |
| A3395 | A3395ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3395 | A3395DeviceManager | ? |
| A3396 | A3396ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3396 | A3395DeviceManager | ? |
| A3398 | A3398ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3398 | Cmm2BtDeviceManager | A3398AnalysisService, A3398CmdService |
| A3399 | A3399ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3399 | Cmm2BtDeviceManager | A3399AnalysisService, A3399CmdService |
| A33A0 | A33A0ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B33A0 | A3395DeviceManager | ? |
| A33A1 | A33A1ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B33A1 | Cmm2BtDeviceManager | A33A1AnalysisService, A33A1CmdService |
| A33A1J11 | A33A1J11ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B33AB | Cmm2BtDeviceManager | A33A1AnalysisService, A33A1CmdService |
| A3600 | A3600ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3600 | Cmm2BtDeviceManager | A3600AnalysisService, A3600CmdService |
| A3850 | A3850ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3850 | Cmm2BtDeviceManager | A3850AnalysisService, A3850CmdService |
| A3871 | A3871ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3871 | Cmm2BtDeviceManager | A3871AnalysisService, A3871CmdService |
| A3872 | A3872ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3872 | Cmm2BtDeviceManager | A3871AnalysisService, A3871CmdService |
| A3873 | A3873ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3873 | Cmm2BtDeviceManager | A3871AnalysisService, A3871CmdService |
| A3874 | A3874ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3874 | Cmm2BtDeviceManager | A3874AnalysisService, A3874CmdService |
| A3874X | A3874XProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B387A | Cmm2BtDeviceManager | A3874AnalysisService, A3874CmdService |
| A3875 | A3875ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3875 | Cmm2BtDeviceManager | A3875AnalysisService, A3875CmdService |
| A3876 | A3876ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3876 | Cmm2BtDeviceManager | A3876AnalysisService, A3876CmdService |
| A3878 | A3878ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3878 | Cmm2BtDeviceManager | A3876CmdService, A3878AnalysisService |
| A3909 | A3909ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3909DeviceManager | ? |
| A3910 | A3910ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3909DeviceManager | ? |
| A3913 | A3913ProduceModel | 00001101-0000-1000-8000-00805f9b34fb | A3909DeviceManager | ? |
| A3926 | A3926ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3926 | A3951DeviceManager | ? |
| A3926Z11 | A3926Z11ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B392B | A3951DeviceManager | ? |
| A3927 | A3927ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3927 | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3927EU | A3927EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B392A | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3930 | A3930ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3931 | A3931ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3931XR | A3931XProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3933 | A3933ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3933 | A3933DeviceManager | ? |
| A3935 | A3935ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3935W | A3935WProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B393C | A3951DeviceManager | ? |
| A3936 | A3936ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3936 | Cmm2BtDeviceManager | A3936AnalysisService, A3936CmdService |
| A3937 | A3937ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3937 | Cmm2BtDeviceManager | A3955AnalysisService, A3955CmdService |
| A3939 | A3939ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3939 | A3933DeviceManager | ? |
| A3939WEU | A3939EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B393D | A3933DeviceManager | ? |
| A3943 | A3943ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3943 | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3944 | A3944ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3944 | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3944EU | A3944EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B394C | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3945 | A3945ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3945 | A3933DeviceManager | ? |
| A3947 | A3947ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3947 | Cmm2BtDeviceManager | A3947AnalysisService, A3947CmdService |
| A3948 | A3948ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3948 | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3948A25 | A3948A25ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B394F | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3949 | A3949ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3949 | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3949P25 | A3949P25ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B394A | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3949R50 | A3949R50ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B394B | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3951 | A3951ProductModel | 00001101-0000-1000-8000-00805f9b34fb | A3951DeviceManager | ? |
| A3952 | A3952ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3952 | Cmm2BtDeviceManager | A3952AnalysisService, A3952CmdService |
| A3953 | A3953ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3953 | Cmm2BtDeviceManager | A3953AnalysisService, A3953CmdService |
| A3954 | A3954ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3954 | Cmm2BtDeviceManager | A3954AnalysisService, A3954CmdService |
| A3954-Box | A3954BoxProductModel | null | Cmm2BtDeviceManager | ? |
| A3955 | A3955ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3955 | Cmm2BtDeviceManager | A3955AnalysisService, A3955CmdService |
| A3957 | A3957ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3957 | Cmm2BtDeviceManager | A3957AnalysisService, A3957CmdService |
| A3958 | A3958ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3958 | Cmm2BtDeviceManager | A3958AnalysisService, A3958CmdService |
| A3959 | A3959ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3959 | Cmm2BtDeviceManager | A3958AnalysisService, A3958CmdService |
| A3959R50 | A3959R50ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B395B | Cmm2BtDeviceManager | A3958AnalysisService, A3958CmdService |
| A3961 | A3961ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3961 | A3933DeviceManager | ? |
| A3968 | A3968ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3968 | Cmm2BtDeviceManager | A3968AnalysisService, A3968CmdService |
| A3969 | A3969ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3969 | Cmm2BtDeviceManager | A3948AnalysisService, A3948CmdService |
| A3982 | A3982ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3982 | A3933DeviceManager | ? |
| A3982EU | A3982EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3982 | A3933DeviceManager | ? |
| A3983 | A3983ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3983 | A3933DeviceManager | ? |
| A3983EU | A3983EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3983 | A3933DeviceManager | ? |
| A3992 | A3992ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3992 | A3933DeviceManager | ? |
| A3992EU | A3992EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3992 | A3933DeviceManager | ? |
| A3993 | A3993ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3993 | A3933DeviceManager | ? |
| A3993EU | A3993EUProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3993 | A3933DeviceManager | ? |
| A3994 | A3994ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3994 | Cmm2BtDeviceManager | A3994AnalysisService, A3994CmdService |
| A6610 | A6610ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B6610 | Cmm2BtDeviceManager | A6610AnalysisService, A6610CmdService |
| A6611 | A6611ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B6611 | Cmm2BtDeviceManager | A6611AnalysisService, A6611CmdService |
| D1101 | D1101ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1101 | Cmm2BtDeviceManager | D1101AnalysisService, D1101CmdService |
| D1105 | D1105ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1105 | Cmm2BtDeviceManager | D1105AnalysisService, D1105CmdService |
| D1200 | D1200ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1200 | Cmm2BtDeviceManager | D1200AnalysisService, D1200CmdService |
| D1202 | D1202ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1202 | Cmm2BtDeviceManager | D1202AnalysisService, D1202CmdService |
| D1202C | D1202CProductModel | 0CF12D31-FAC3-4553-BD80-D6832E71202C | Cmm2BtDeviceManager | D1202AnalysisService, D1202CmdService |
| D1203 | A3956XProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B3956 | Cmm2BtDeviceManager | A3956AnalysisService, A3956CmdService |
| D1203-Box | A3956XBoxProductModel | null | BoxBleDeviceManager, Cmm2BtDeviceManager | A3956AnalysisService, A3956CmdService |
| D1204 | A3956ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B395C | Cmm2BtDeviceManager | A3956AnalysisService, A3956CmdService |
| D1204-Box | A3956BoxProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B395D | BoxSppDeviceManager, Cmm2BtDeviceManager | A3956AnalysisService, A3956CmdService |
| D1205 | D1205ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1205 | Cmm2BtDeviceManager | D1205AnalysisService, D1205CmdService |
| D1206 | D1206ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1206 | Cmm2BtDeviceManager | D1206AnalysisService, D1206CmdService |
| D1301 | D1301ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1301 | Cmm2BtDeviceManager | D1301AnalysisService, D1301CmdService |
| D1301-Box | D1301BoxProductModel | null | Cmm2BtDeviceManager | ? |
| D1301S | D1301SProductModel | 0CF12D31-FAC3-4553-BD80-D6832E71301A | Cmm2BtDeviceManager | D1301CmdService, D1301SAnalysisService |
| D1301S-Box | D1301SBoxProductModel | null | Cmm2BtDeviceManager | ? |
| D1303 | D1303ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1303 | Cmm2BtDeviceManager | D1303AnalysisService, D1303CmdService |
| D1303-Box | D1303BoxProductModel | null | Cmm2BtDeviceManager | ? |
| D1402 | D1402ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1402 | Cmm2BtDeviceManager | D1402AnalysisService, D1402CmdService |
| D1404 | D1404ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1404 | Cmm2BtDeviceManager | D1404AnalysisService, D1404CmdService |
| D1406 | D1406ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1406 | Cmm2BtDeviceManager | D1406AnalysisService, D1406CmdService |
| D1407 | D1407ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D1407 | Cmm2BtDeviceManager | D1406AnalysisService, D1406CmdService |
| D3200 | D3200ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D3200 | D3200DeviceManager | D3200AnalysisService, D3200CmdService |
| D5100 | D5100ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D5100 | Cmm2BtDeviceManager | D5100AnalysisService, D5100CmdService |
| D5103 | D5103ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7D5103 | Cmm2BtDeviceManager | D5103AnalysisService, D5103CmdService |
| Z5180 | Z5180ProductModel | 0CF12D31-FAC3-4553-BD80-D6832E7B5180 | CmmBtDeviceManager | ? |
| Z6111 | Z6111ProductModel | null | CmmBtDeviceManager | ? |

## Interpretation and remaining work

Every registration has a recovered model body. The shared and model-specific command/parser classes were recovered too. This table records routing and transport evidence; it does not certify every setting or a complete implementation. Per-model feature gates, payload field meanings, preset selection and firmware branches still need to be traced through callers and checked against the serializers. None of these registrations has been tested against physical hardware in this exercise.

The legacy `getServiceUUID()` getter commonly returns `FB349B5F8000008000100000DAF51201`. That value is preserved as vendor metadata; do not use it as the GATT service UUID for every product. The newer SDK declares normal Android UUID strings per channel. The six `isBoxConnected()` getters that depend on live device state threw in the unconnected harness; no box-presence result is asserted for them.
