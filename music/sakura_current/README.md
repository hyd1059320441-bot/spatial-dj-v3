# 樱花电流 / Sakura Current

128 BPM，A 小调，四拍电子 House。原创旋律、Am–F–C–G 和声，带铺垫、
渐强鼓滚、两段 Drop、间奏和收尾。64 小节约 2 分钟，加 2 秒效果尾音。
所有声音由脚本本地合成，没有下载采样或调用在线服务。

在本目录执行：

```sh
python3 compose.py
python3 compose.py --bpm 132 --play
```

依赖 `numpy`；当前机器已具备。检测到 `ffmpeg` 时还会生成 MP3。
默认输出都在 `output/`：WAV、MP3、MIDI、编曲信息与五轨 `.loop` 工程。
MIDI 可继续在 DAW 中换音色、改旋律；WAV/MP3 可直接播放。

`.loop` 是当前 Spatial Loop V3.1 支持的四小节五轨版本，按应用实际音符
范围编写。完整版 WAV 包含更丰富的合成音色、段落和混音；工程循环使用
应用自带音色，因此两者听感不同。生成脚本不会覆盖设备上的已有工程。
