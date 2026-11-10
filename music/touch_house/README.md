# 用真实界面控件演奏《触碰电流》

运行：

```sh
python3 /Users/bytedance/Documents/ai/spatial-dj-v3/music/touch_house/perform.py
```

连接一台 B3110 即可；多设备时加 `--serial PB331XPGL6040019G`。
脚本自动构建并安装当前 App 与 instrumentation APK。已有本次构建可加
`--skip-build`。工程和导出的音乐保留在设备上。

## 演奏步骤

谱子在 `score.json`，每个 `at` 是本圈中的拍数，每个 `pad` 是该轨的音垫索引：

```json
{
  "lane": 0,
  "notes": [
    {"at": 0, "pad": 0},
    {"at": 1, "pad": 0},
    {"at": 2, "pad": 0},
    {"at": 3, "pad": 0}
  ]
}
```

以上实际执行：定位 `lane0` → 接触并抬起 → 定位 `record` → 接触并抬起 →
等待四拍预备/下一圈 → 提前定位 `pad-0-0` → 第 0/1/2/3 拍分别接触并抬起 →
等满圈自动提交。下一轨重新定位页面上的控件。已有轨再次录音会叠录。

完整谱子是 130 BPM、四小节 House：底鼓 → 镲片叠录 → 拍手 →
反拍贝斯 → 旋律 → Am/F/C/G 和弦，共 101 音符，各轨 `[34, 11, 16, 32, 8]`。
速度通过界面「BPM ±5」按钮设置。贝斯只使用界面已有的 A1/C2/D2/E2，
没有为脚本增加隐藏乐器。

驱动保持「完整编曲」页面，明确选择 Drop 后录入上述四小节；
录音与恢复监听均使用同一部分。导出遵循完整编曲规则，包含其他未录部分的静音时长。

## 输入方式与验证

驱动位于
`app/src/androidTest/java/com/example/spatialdj/p170bd28c/UiMusicPerformance.kt`。
`locate(id)` 查询当前 Compose 控件注册表的实时布局；`click(id)` 将其接触面位置转换
到实际空间面板，回放指尖从前方到接触再抬起的输入，经过生产 `PanelInput.hands` 路径。
每次接触都检查定位后的布局仍有效。普通 Android 点击回调为空，不能作为演奏输入。
这是 instrumentation 中的模拟指尖输入，不代表真实手部追踪的触碰验证。

录音、叠录、调速、调音量、新建、保存与 WAV 导出都通过这些界面控件执行。
驱动只读取引擎/界面状态用于等待和断言，没有调用 ViewModel 业务动作、
引擎写入方法、导入预制工程或外部音频合成脚本。WAV 是点击「导出 WAV」后的应用产物。
每轨的实际拍数与音垫必须逐个匹配谱子，超过量化容差立即报错。
保存前保留设备上原有的 `saved.loop` 为 `before-ui-music-时间戳.loop`。

`runs/时间戳/` 保留执行谱子、instrumentation 日志、实时控件坐标及节拍记录、
导出的 `touch-house.wav` 和 `touch-house.loop`。`latest-run.txt` 指向最近一次成功运行。

Android 在 instrumentation 结束后会关闭被测进程。脚本默认再通过相同控件定位
执行「工具箱 → 载入工程 → 再次载入确认 → 返回演奏 → Drop → 试听本段」，
保持监听 600 秒。加 `--listen 0` 可只保存、导出而不启动监听。
监听期间可以手动调音量或操作 App；退出/停止会按正常界面逻辑生效。
监听结束后，如需再次播放，可打开 App，按上述载入/播放步骤操作。
