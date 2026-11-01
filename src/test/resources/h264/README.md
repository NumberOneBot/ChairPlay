# H.264 fixture for VideoDecoderTest

320x240, 3 frames (one IDR, two P), produced once with the ffmpeg 7.1 program bundled in `org.bytedeco:ffmpeg:7.1-1.5.11` (windows-x86_64 jar, `ffmpeg.exe`), because no ffmpeg CLI is installed:

    ffmpeg -f lavfi -i testsrc=size=320x240:rate=30 -frames:v 3 -c:v libopenh264 -pix_fmt yuv420p -f h264 raw.h264

(The `h264_mp4toannexb` filter is unnecessary: `-f h264` already writes Annex B.) A short Python script then split `raw.h264` on start codes:

- `avcC.bin` — an AVCDecoderConfigurationRecord built from the stream's SPS and PPS (lengthSizeMinusOne = 3), i.e. what `onVideoConfig` receives;
- `frame0.h264` .. `frame2.h264` — the three slice NAL units, each with a 4-byte `00 00 00 01` prefix and no SPS/PPS, i.e. what `onVideoFrame` receives.
