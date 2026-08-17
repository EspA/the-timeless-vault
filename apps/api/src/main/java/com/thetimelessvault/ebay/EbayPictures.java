package com.thetimelessvault.ebay;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class EbayPictures {

    static final int MIN_EDGE = 500;

    private EbayPictures() {
    }

    public record Prepared(byte[] bytes, String contentType, boolean upscaled) {
    }

    public static Prepared ensureMinimum(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return new Prepared(bytes, contentType, false);
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new Prepared(bytes, contentType == null ? "image/jpeg" : contentType, false);
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int longest = Math.max(width, height);
            if (longest >= MIN_EDGE) {
                return new Prepared(bytes, contentType == null ? "image/jpeg" : contentType, false);
            }
            double scale = MIN_EDGE / (double) longest;
            int nextWidth = Math.max(1, (int) Math.round(width * scale));
            int nextHeight = Math.max(1, (int) Math.round(height * scale));
            BufferedImage out = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = out.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, nextWidth, nextHeight);
            graphics.drawImage(image, 0, 0, nextWidth, nextHeight, null);
            graphics.dispose();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", buffer);
            return new Prepared(buffer.toByteArray(), "image/jpeg", true);
        } catch (IOException e) {
            return new Prepared(bytes, contentType == null ? "image/jpeg" : contentType, false);
        }
    }
}
