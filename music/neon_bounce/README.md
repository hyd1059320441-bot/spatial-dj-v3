# 霓虹弹跳

原创 Future Bounce，128 BPM，A 小调，Am / F / C / G。旋律、编排及采样全部在本工程
生成，没有使用第三方歌曲录音或旋律。

Demo 内点击「自动演奏」：四拍预备，然后依次录入底鼓、镲、拍手、贝斯、旋律、和弦。
录制约 47 秒后进入完整编排；40 小节、75 秒的完整编排持续循环。按钮可停止并恢复
演奏前的工程，工具箱的「恢复原工程」可取消自动工程；不会覆盖 saved.loop。
自动演奏从预备拍开始即保持完整编曲页面。六次录音进入 Drop；
之后的段落按钮高亮、各轨音符数和休止提示跟随正在播放的乐段。
JSON 的 `part` 使用 `SongPart` 名称，重复 Drop 片段保留各自的编排与标题。

完整编排：Drop 1 → Breakdown → Build-up（军鼓加速与上升音效）→ Drop 2 → Outro。
音色包括冲击底鼓、分层拍手、镲、滤波锯齿贝斯与正弦低音、立体声失谐主音、
和弦短奏、冲击及上升。混音使用底鼓侧链、立体声延迟、短混响和无前瞻软限幅。

- `neon-bounce.mp3`：320 kbps 完整编排试听。
- `neon-bounce.wav`：48 kHz、16 bit、立体声；由应用同一 LoopEngine 与 SoundBank
  离线渲染，略过录音叠加过程。文件没有额外的第三方母带处理。
- `score.json`：录音步骤与完整编排。
- `produce.py`：确定性生成乐谱及 28 个立体声音色，依赖 NumPy。
- `sound-bank-report.json`、`master-analysis.log`：音色及响度证据。

重建音色与乐谱：`python3 music/neon_bounce/produce.py`。
随后运行 `:app:testDebugUnitTest --tests '*NeonProductionTest*'`，
测试会从实际生产音色和应用引擎重建完整 WAV。MP3 使用 FFmpeg 编码。

艺术水准需要试听评价；工程与信号检查不等同于 DJ Mag 排名认证。
