package com.boxue.api.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import net.coobird.thumbnailator.Thumbnails;

@Component
public class ImageProcessor {

    private final MediaProperties properties;
    private final SvgRasterizer svgRasterizer;

    public ImageProcessor(MediaProperties properties, SvgRasterizer svgRasterizer) {
        this.properties = properties;
        this.svgRasterizer = svgRasterizer;
    }

    public ProcessedImage process(byte[] input, String mimeType) {
        if ("image/gif".equals(mimeType)) {
            Integer[] size = imageSize(input);
            return new ProcessedImage(input, mimeType, size[0], size[1]);
        }
        if ("image/svg+xml".equals(mimeType)) {
            return svgRasterizer.rasterize(input, maxDimension());
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(input));
        } catch (Exception ex) {
            throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_IMAGE_INVALID", "图片内容无法解析");
        }
        if (image == null) {
            throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_IMAGE_INVALID", "图片内容无法解析");
        }

        int maxDimension = maxDimension();
        double scale = Math.min(1.0, maxDimension / (double) Math.max(image.getWidth(), image.getHeight()));
        int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
        boolean alpha = image.getColorModel().hasAlpha();
        String outputFormat = alpha || "image/png".equals(mimeType) ? "png" : "jpg";
        String outputMime = "png".equals(outputFormat) ? "image/png" : "image/jpeg";

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            var builder = Thumbnails.of(image).size(targetWidth, targetHeight).outputFormat(outputFormat);
            if ("jpg".equals(outputFormat)) {
                builder.outputQuality(Math.max(0.5, Math.min(properties.getJpegQuality(), 0.95)));
            }
            builder.toOutputStream(output);
            return new ProcessedImage(output.toByteArray(), outputMime, targetWidth, targetHeight);
        } catch (Exception ex) {
            throw new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_IMAGE_PROCESSING_FAILED",
                "图片压缩失败，请更换图片后重试");
        }
    }

    private int maxDimension() {
        return Math.max(320, Math.min(properties.getMaxDimension(), 4096));
    }

    private Integer[] imageSize(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image == null ? new Integer[] {null, null}
                : new Integer[] {image.getWidth(), image.getHeight()};
        } catch (Exception ex) {
            return new Integer[] {null, null};
        }
    }
}
