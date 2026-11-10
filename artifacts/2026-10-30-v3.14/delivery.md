# SpatialLoop V3.14

交付 APK：[spatial-loop-v3.14.apk](spatial-loop-v3.14.apk)。
应用 `com.example.spatialdj.loopv3`，versionCode 44 / versionName 3.14。
已通过 ADB 覆盖安装到 B3110 `PB331XPGL6040019G`，保留应用数据。
校验值见 [SHA256SUMS](SHA256SUMS)。

## 本版修复

- 两盏对向 800 lux 中性平行光覆盖四面墙及地板，保留两盏节拍扫射灯；
  共四个实际光源，无阴影贴图。三块可见顶棚灯板为外观模型。
- 环境光由 +3 升到 +4 EV，强度翻倍；墙面/地板/顶棚基色分别改为
  `#788793` / `#596B78` / `#627581`。
- 自动演奏从预备拍开始保持完整编曲页面；六次录音存入 Drop，
  随后十个播放乐段按实际 SongPart、标题、各轨音符数同步显示。
  空轨显示“本段休止”，Outro 反馈跟随实际有声轨。
- 源乐谱、内置乐谱及生成器的段落元数据一致；外部 UI 演奏驱动适配完整模式。
- 默认台高仍为 1.10m，总音量 100%，混音增益 1.6；保留四人舞蹈及房间 PA。

## 已完成验证

- JVM：83 项通过，0 失败。见 `TEST-*.xml` 和 `final-build.log`。
- 真机自动演奏：132.705 秒通过，六次录音、十个乐段、各轨音符数量、
  完整页面控件/字体/溢出、工具页、取消/后台恢复、原工程恢复及保存文件保护。
  200/200 次音频抽样有声，左右声道新增欠载均为 0。
  见 [final-automatic.log](final-automatic.log)、
  [automatic-report.json](automatic-report.json)。
- 真机房间：10.009 秒通过，实际四光源、入射方向覆盖、24 个运动关节/脚、
  白肤色材质、扫射轨迹、1.10m 台高、PA 绑定、退出释放和重新进入。
  见 [final-room.log](final-room.log)、[club-room-report.json](club-room-report.json)。
- 最后只修正了一处 SceneReady 日志中的旧光源数量，重新构建及安装；
  不影响上述已验证的渲染和播放逻辑。见 `final-package-build.log`、
  `final-install.log`、`final-launch.log`、`installed-version.txt`。

## 性能与验证范围

使用自动演奏期间真实 PxrMetric 的 128 个样本，帧率范围 72–92，
中位数 72 FPS；系统刷新率由初段约 90Hz 转为 72Hz，后段稳定在约 72 FPS。
GPU 帧时中位数 11.8ms，最大 21.22ms；GPU 使用率中位数 82%，峰值 99%。
因此不声称本版持续 90 FPS。见 [final-performance.json](final-performance.json)。

初次九光源及后续大范围顶灯诊断配置在 B3110 播放时触发 GPU_NO_RETIRE，
导致 Spatial Runtime 重启，未作为交付配置。缩小局部光照范围及更换顶棚
聚光仍未解决；最终采用对向平行光并提亮材质。最终两项真机回归均完成，
采集日志未再出现该故障。`diagnostic-*`、`native-*`、`runtime-failure-*`
及早期 `build.log` 是排查记录，不代表最终测试结果。

亮度观感仍由用户佩戴确认，本轮没有截图验证。自动演奏控件测试使用测试端注入，
不等同于真实指尖精度或延迟验证；用户暂停的相关检查保持暂停。
本轮未执行会另存音乐的外部苏珊娜全流程。
