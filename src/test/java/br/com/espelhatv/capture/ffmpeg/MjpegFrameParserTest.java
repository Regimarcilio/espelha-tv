package br.com.espelhatv.capture.ffmpeg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MjpegFrameParserTest {

    @Test
    void extractsSingleFrame() {
        byte[] stream = concat(new byte[]{(byte) 0xFF, (byte) 0xD8},
                new byte[]{0x01, 0x02, (byte) 0xFF, 0x00, 0x03},
                new byte[]{(byte) 0xFF, (byte) 0xD9});

        List<byte[]> frames = new ArrayList<>();
        new MjpegFrameParser().feed(stream, stream.length, frames::add);

        assertEquals(1, frames.size());
        assertArrayEquals(stream, frames.get(0));
    }

    @Test
    void handlesSplitMarkersAndMultipleFrames() {
        byte[] first = concat(new byte[]{(byte) 0xFF, (byte) 0xD8}, new byte[]{0x10, 0x11});
        byte[] second = concat(new byte[]{(byte) 0xFF, (byte) 0xD8}, new byte[]{0x20, 0x21},
                new byte[]{(byte) 0xFF, (byte) 0xD9});

        List<byte[]> frames = new ArrayList<>();
        MjpegFrameParser parser = new MjpegFrameParser();
        parser.feed(first, first.length, frames::add);
        // EOI do primeiro frame chega dividido entre dois buffers.
        parser.feed(new byte[]{(byte) 0xFF}, 1, frames::add);
        parser.feed(concat(new byte[]{(byte) 0xD9}, second), 1 + second.length, frames::add);

        assertEquals(2, frames.size());
        assertArrayEquals(concat(first, new byte[]{(byte) 0xFF, (byte) 0xD9}), frames.get(0));
        assertArrayEquals(second, frames.get(1));
    }

    @Test
    void ignoresGarbageBeforeSoi() {
        byte[] frame = concat(new byte[]{(byte) 0xFF, (byte) 0xD8}, new byte[]{0x42},
                new byte[]{(byte) 0xFF, (byte) 0xD9});
        byte[] stream = concat(new byte[]{0x00, (byte) 0xFF, 0x00, 0x13}, frame);

        List<byte[]> frames = new ArrayList<>();
        new MjpegFrameParser().feed(stream, stream.length, frames::add);

        assertEquals(1, frames.size());
        assertArrayEquals(frame, frames.get(0));
    }

    @Test
    void waitsForEoiBeforeEmitting() {
        List<byte[]> frames = new ArrayList<>();
        MjpegFrameParser parser = new MjpegFrameParser();

        byte[] start = concat(new byte[]{(byte) 0xFF, (byte) 0xD8}, new byte[]{0x01, 0x02});
        parser.feed(start, start.length, frames::add);
        assertEquals(0, frames.size());

        parser.feed(new byte[]{(byte) 0xFF, (byte) 0xD9}, 2, frames::add);
        assertEquals(1, frames.size());
    }

    @Test
    void doesNotTreatStuffedFfAsEoi() {
        byte[] stream = concat(new byte[]{(byte) 0xFF, (byte) 0xD8},
                new byte[]{0x01, (byte) 0xFF, 0x00, 0x02},
                new byte[]{(byte) 0xFF, (byte) 0xD9});

        List<byte[]> frames = new ArrayList<>();
        new MjpegFrameParser().feed(stream, stream.length, frames::add);

        assertEquals(1, frames.size());
        assertArrayEquals(stream, frames.get(0));
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
