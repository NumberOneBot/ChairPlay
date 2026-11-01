# HEVC fixture

Three solid-red 320x240 YUV420 frames (one IDR, two P), generated with FFmpeg/libx265:

    ffmpeg -f lavfi -i color=c=red:size=320x240:rate=30 -frames:v 3 -c:v libx265 -pix_fmt yuv420p -x265-params bframes=0:keyint=30:repeat-headers=0:log-level=error:pools=1 -tag:v hvc1 fixture.mp4
    ffmpeg -i fixture.mp4 -c:v copy -bsf:v hevc_mp4toannexb -f hevc fixture.hevc

`hvcC.bin` is the MP4's hvcC box payload, without its size and type header. Each `frameN.hevc` contains one slice NAL with a four-byte Annex B start code, without parameter sets. This matches the desktop sink's separate configuration/frame callbacks. The fixture checks actual HEVC decoding, pixel values, and codec changes on both screens.

The 320x240 size also fits the GPU decoder; the earlier 64x48 fixture was below its supported size.
