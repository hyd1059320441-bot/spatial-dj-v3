# 苏珊娜舞曲：控件自动演奏

将 Stephen Foster 1848 年的公有领域旋律《Oh! Susanna / 哦，苏珊娜》开头两句，
改编为 130 BPM、四小节 House 循环。鼓、镲片、拍手、反拍贝斯与 C/G 和弦重新编排；
保留熟悉的主旋律音高顺序，节奏按当前音垫重新安排。

```sh
python3 /Users/bytedance/Documents/ai/spatial-dj-v3/music/susanna_house/perform.py
```

已有构建可加 `--skip-build`；多设备时加 `--serial PB331XPGL6040019G`。

脚本复用 `../touch_house/perform.py` 和 `UiMusicPerformance`，通过实时控件 ID 定位、
模拟指尖接触与抬起，依次新建工程、调速、选轨、录音、叠录、敲音垫、保存和导出。
所有音符由实际界面控件录入。没有离线合成歌曲、导入现成工程或调用引擎写入捷径。
当前驱动始终使用完整编曲页面，明确选中 Drop 录制四小节，恢复播放时点「试听本段」。

30 个旋律音符只用现有 C4/D4/E4/G4/A4 音垫。总计 100 个录音音符，
各轨数量为 `[34, 11, 16, 30, 9]`；每轨提交后逐个核对拍点与音垫。

旋律资料：https://www.zebrakeys.com/blog/oh-susanna/

`runs/时间戳/` 保存谱子、控件接触与节拍记录、执行日志、`susanna-house.wav`
和 `susanna-house.loop`。WAV 来自点击应用的「导出 WAV」；
完整编曲模式包含五段各四小节，长约 36.92 秒，未录部分保留静音。旧版 Drop 模式产物为四圈、约 29.54 秒。
`latest-run.txt` 指向最近一次成功执行。

成功后会重新定位「载入工程」「Drop」「试听本段」，在真机上保持播放 600 秒。
可用 `--listen 0` 关闭恢复播放。此前保存的工程由驱动备份在设备的
`before-ui-music-时间戳.loop`，第一段《触碰电流》的本地谱子与产物也保留。
