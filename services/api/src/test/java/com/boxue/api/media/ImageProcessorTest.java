package com.boxue.api.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ImageProcessorTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void resizesAndStripsExifFromJpeg() throws Exception {
        BufferedImage image = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, (x * 31 + y * 17) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpeg);
        byte[] withExif = injectExif(jpeg.toByteArray());

        MediaProperties properties = new MediaProperties();
        properties.setMaxDimension(800);
        ImageProcessor processor = new ImageProcessor(properties, new SvgRasterizer());
        ProcessedImage result = processor.process(withExif, "image/jpeg");

        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.width()).isEqualTo(800);
        assertThat(result.height()).isEqualTo(400);
        assertThat(new String(result.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(result.bytes()).isNotEmpty();
    }

    @Test
    void rasterizesSvgToPngAndDropsScripts() {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="400" height="200" viewBox="0 0 400 200">
              <script>alert('xss')</script>
              <rect width="400" height="200" fill="#409eff"/>
              <circle cx="200" cy="100" r="50" fill="#ffffff"/>
            </svg>
            """;
        MediaProperties properties = new MediaProperties();
        properties.setMaxDimension(1600);
        ImageProcessor processor = new ImageProcessor(properties, new SvgRasterizer());
        ProcessedImage result = processor.process(svg.getBytes(StandardCharsets.UTF_8), "image/svg+xml");

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.width()).isEqualTo(400);
        assertThat(result.height()).isEqualTo(200);
        assertThat(result.bytes()[0] & 0xFF).isEqualTo(0x89);
        assertThat(result.bytes()[1]).isEqualTo((byte) 'P');
        assertThat(result.bytes()[2]).isEqualTo((byte) 'N');
        assertThat(result.bytes()[3]).isEqualTo((byte) 'G');
    }

    private byte[] injectExif(byte[] jpeg) throws Exception {
        byte[] payload = "Exif\0\0FAKE_EXIF_PAYLOAD".getBytes(StandardCharsets.ISO_8859_1);
        int segmentLength = payload.length + 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(0xFF);
        output.write(0xE1);
        output.write((segmentLength >> 8) & 0xFF);
        output.write(segmentLength & 0xFF);
        output.write(payload);
        output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }
}

