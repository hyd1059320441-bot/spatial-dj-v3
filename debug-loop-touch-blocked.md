# [OPEN] loop-touch-blocked

用户反馈：第三版在 B3110 上手势完全无法控制，视觉太丑，要求偏动漫。
继续完成输入修复和动漫电玩风格改造；保留直接手指接触、无捏合/旋钮/模型生成。

## 可证伪假设

1. AttachmentPanel 的父级 pointerInput 没有收到原生 Poke 事件。
   观测原生事件种类、pressed/down、坐标和接收计数。
2. HandTracking 坐标转换或面板原点/法向不一致，真实指尖被近距离门限拒绝。
   观测真实指尖转换前后数据、面板尺寸与原生事件对应位置。
3. 最新数据使用原对象更新，或回调与轮询时间不同，100ms 新鲜度门限拒绝所有触碰。
   观测 sample identity、相邻样本字段变化、fresh age 和 hands count。

## 插桩阶段（已完成）

当时未改业务逻辑。此前 28 个 JVM 测试和音序器真机输入不能代替真实触碰证据。
先采集运行时日志，按证据定位后修复，并完成风格改造及安装。
用户可随时中止调试；在用户确认前保留调试记录与采集环境。

## pre-fix 运行时证据

采集地址 `http://127.0.0.1:7777/event`，通过 adb reverse 转发。
server 原始记录 `.dbg/trae-debug-log-loop-touch-blocked.ndjson`。
- C: provider `SUCCESS/SUPPORTED/STARTED`，典型数据 age 11–13 ms，否定“全部数据过期”。
- B: 左食指有有效非零坐标；右手 pose 存在但指尖恒为 (0,0,0)，不能把非空 pose
  当作有效手追踪。左食指转换后常在 panel z≈-0.10 m。不是触碰瞬间证据，不据此判定坐标错误。
- A: 当前窗口没有收到 native pointer down/up；窗口没有已核验的人工触碰动作，
  不能将“无事件”宣称为已经确认系统 Poke 失效。

## 修复方向依据

SDK 官方 `spatial-design_输入与交互_体感交互.md` 第 31 行明确建议：
严格的手指接触检测使用 HandTracking，并为指尖配置碰撞检测。
因此将直接敲击/滑动改为几何接触输入，而不是两个不同时钟/路由来源叠加的
native Poke + 指尖 gate。这属于满足用户原始“直接触碰、只有手指”的实现纠正，
不是恢复旧版捏合/腕部角度/旋钮手势。
新路径必须完整测试真实控件的接触投影、前后面、跨帧扫掠、抬指重触发、
快速敲击、多指捕获、零坐标和丢追踪；不能再用仅调用 ViewModel 的测试宣称触碰修复。

## post-fix V3.1

已经完成指尖几何接触路径、可见指尖圆点、动漫街机外观和最终安装。
SDK TIP 全局米坐标经真实 Attachment Entity 转换；过滤零坐标/无效 wrist，
不再依赖 native pointer。12 mm 接触、24 mm 抬指释放、跨帧扫掠、每指捕获、
推杆独占、丢追踪/过期/后台/换页释放。原生事件仅消费，不触发或重复操作。

- A: 原生路由缺失是否为用户当时失败的实际原因仍未得到人工事件证据。
  新路径已完全去掉这项依赖，测试时无需 native down/up。
- B: 修复后的 `PanelInput.hands` 网络证据记录实测控件上的 contact hit；
  19:52:47–19:52:57，五条轨道各录入 4 音符，包含两只手 ID 同帧敲击。
  数据经过实际 panel/stage 转换，触发真正控件回调和音频引擎；
  仍属于 instrumentation 合成接触，不宣称已验证真人手感。
- C: `SUCCESS/SUPPORTED/STARTED`，post-fix 典型 age 11–24 ms、validTips 10；
  过期/丢失数据会释放，pose 非空不再等同于手追踪有效。

37 JVM 测试（20 接触、14 音频、3 文件）和 1 真机综合接触回放通过。
5 轨连续播放 30 秒，60/60 有声；Runtime 30 个样本全部 90 FPS；
WAV 1,024,044 bytes，保存/载入/推杆/静音/台高/后台音频回收通过。
最终仅补充 Launcher 图标/名称，再编译和跑单元测试；重装最终 APK 后
冷启动成功，versionCode 31/versionName 3.1，19 控件均在面板内。

交付：`artifacts/2026-10-27-v3.1/delivery.md` / `SpatialLoop-v3.1.apk`。
用户未确认实际手指操作，维持 OPEN，不清理插桩及采集证据。
用户可说“停止调试”中止。收集器 host port 7778 / device reverse 7777→7778。
原始单线程采集器存在未关闭连接时曾无响应，伴随 ADB 无响应；
恢复 ADB 后使用 `.dbg/resilient-collector.py`，限制每个请求 2 秒并并发接收。
原日志保留，未提交任何工单。
