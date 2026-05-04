package org.solarframework.core.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.HashMap;
import java.util.Map;

import static org.solarframework.core.util.OtherUtils.getHexValue;

public class ImageUtils {

    public static BufferedImage roundImageCorners(BufferedImage img, int radius) {
        BufferedImage roundedImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TRANSLUCENT);
        Graphics2D g2d = roundedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setClip(new RoundRectangle2D.Float(0, 0, img.getWidth(), img.getHeight(), radius, radius));
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return roundedImage;
    }

    public static BufferedImage fillPNG(File input, Color fillColor, int blackAmount) throws IOException {
        return fillPNG(ImageIO.read(input), fillColor, blackAmount);
    }
    public static BufferedImage fillPNG(String url, Color fillColor, int blackAmount) throws IOException {
        return fillPNG(ImageIO.read(URI.create(url).toURL()), fillColor, blackAmount);
    }
    public static BufferedImage fillPNG(InputStream image, Color fillColor, int blackAmount) throws IOException {
        return fillPNG(ImageIO.read(image), fillColor, blackAmount);
    }
    public static BufferedImage fillPNG(byte[] imageBytes, Color fillColor, int blackAmount) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return fillPNG(ImageIO.read(is), fillColor, blackAmount);
        }
    }
    public static BufferedImage fillPNG(BufferedImage image, Color fillColor, int blackAmount) throws IOException {
        if (image == null) throw new IOException("Invalid image.");

        int width = image.getWidth();
        int height = image.getHeight();

        int red = fillColor.getRed();
        int green = fillColor.getGreen();
        int blue = fillColor.getBlue();

        // Subtract the black amount from each RGB component
        red = Math.max(0, red - blackAmount);
        green = Math.max(0, green - blackAmount);
        blue = Math.max(0, blue - blackAmount);

        // Create a new color with the adjusted RGB values
        fillColor = new Color(red, green, blue);

        // Iterate through each pixel and fill non-transparent pixels
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF; // Extract the alpha channel
                double transparencyPercentage = (alpha / 255.0) * 100.0;
                if (transparencyPercentage > 20) { // more than 20% transparent
                    // Set the pixel color to the desired fill color
                    image.setRGB(x, y, fillColor.getRGB());
                }
            }
        }
        return image;
    }

    public static BufferedImage replaceAllMatchingColor(File input, Color oldColor, Color newColor, int tolerance) throws IOException {
        return replaceAllMatchingColor(ImageIO.read(input), oldColor, newColor, tolerance);
    }
    public static BufferedImage replaceAllMatchingColor(String url, Color oldColor, Color newColor, int tolerance) throws IOException {
        return replaceAllMatchingColor(ImageIO.read(URI.create(url).toURL()), oldColor, newColor, tolerance);
    }
    public static BufferedImage replaceAllMatchingColor(InputStream image, Color oldColor, Color newColor, int tolerance) throws IOException {
        return replaceAllMatchingColor(ImageIO.read(image), oldColor, newColor, tolerance);
    }
    public static BufferedImage replaceAllMatchingColor(byte[] imageBytes, Color oldColor, Color newColor, int tolerance) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return replaceAllMatchingColor(ImageIO.read(is), oldColor, newColor, tolerance);
        }
    }
    public static BufferedImage replaceAllMatchingColor(BufferedImage image, Color oldColor, Color newColor, int tolerance) throws IOException {
        if (image == null) throw new IOException("Invalid image.");
        int width = image.getWidth(), height = image.getHeight();
        int targetR = oldColor.getRed(), targetG = oldColor.getGreen(), targetB = oldColor.getBlue();
        int newRGBA = newColor.getRGB();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                if (Math.abs(r - targetR) <= tolerance && Math.abs(g - targetG) <= tolerance && Math.abs(b - targetB) <= tolerance) {
                    result.setRGB(x, y, newRGBA);
                } else {
                    result.setRGB(x, y, pixel);
                }
            }
        }
        return result;
    }



    public static BufferedImage reducePixelColor(File input, Color rgbRemove, boolean isZeroTransparent) throws IOException {
        return reducePixelColor(ImageIO.read(input), rgbRemove, isZeroTransparent);
    }
    public static BufferedImage reducePixelColor(String url, Color rgbRemove, boolean isZeroTransparent) throws IOException {
        return reducePixelColor(ImageIO.read(URI.create(url).toURL()), rgbRemove, isZeroTransparent);
    }
    public static BufferedImage reducePixelColor(InputStream image, Color rgbRemove, boolean isZeroTransparent) throws IOException {
        return reducePixelColor(ImageIO.read(image), rgbRemove, isZeroTransparent);
    }
    public static BufferedImage reducePixelColor(byte[] imageBytes, Color rgbRemove, boolean isZeroTransparent) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return reducePixelColor(ImageIO.read(is), rgbRemove, isZeroTransparent);
        }
    }
    public static BufferedImage reducePixelColor(BufferedImage image, Color rgbRemove, boolean isZeroTransparent) throws IOException {
        if (image == null) throw new IOException("Invalid image.");
        int width = image.getWidth();
        int height = image.getHeight();

        int removeR = rgbRemove.getRed();
        int removeG = rgbRemove.getGreen();
        int removeB = rgbRemove.getBlue();
        int removeA = rgbRemove.getAlpha();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int pixel = image.getRGB(x, y);

                int a = (pixel >> 24) & 0xff;
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                r = Math.max(0, r - removeR);
                g = Math.max(0, g - removeG);
                b = Math.max(0, b - removeB);
                a = Math.max(0, a - removeA);

                // optional rule: fully transparent if black
                if (isZeroTransparent && r == 0 && g == 0 && b == 0) a = 0;
                int newPixel = (a << 24) | (r << 16) | (g << 8) | b;
                result.setRGB(x, y, newPixel);
            }
        }
        return result;
    }


   public static BufferedImage reducePixelColorWithRoof(File input, Color rgbRemove, Color roof, boolean isZeroTransparent) throws IOException {
        return reducePixelColorWithRoof(ImageIO.read(input), rgbRemove, roof, isZeroTransparent);
    }
    public static BufferedImage reducePixelColorWithRoof(String url, Color rgbRemove, Color roof, boolean isZeroTransparent) throws IOException {
        return reducePixelColorWithRoof(ImageIO.read(URI.create(url).toURL()), rgbRemove, roof, isZeroTransparent);
    }
    public static BufferedImage reducePixelColorWithRoof(InputStream image, Color rgbRemove, Color roof, boolean isZeroTransparent) throws IOException {
        return reducePixelColorWithRoof(ImageIO.read(image), rgbRemove, roof, isZeroTransparent);
    }
    public static BufferedImage reducePixelColorWithRoof(byte[] imageBytes, Color rgbRemove, Color roof, boolean isZeroTransparent) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return reducePixelColorWithRoof(ImageIO.read(is), rgbRemove, roof, isZeroTransparent);
        }
    }
    public static BufferedImage reducePixelColorWithRoof(BufferedImage image, Color rgbRemove, Color roof, boolean isZeroTransparent) throws IOException {
        if (image == null) throw new IOException("Invalid image.");
        int width = image.getWidth();
        int height = image.getHeight();

        int removeR = rgbRemove.getRed();
        int removeG = rgbRemove.getGreen();
        int removeB = rgbRemove.getBlue();
        int removeA = rgbRemove.getAlpha();

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                int pixel = image.getRGB(x, y);

                int a = (pixel >> 24) & 0xff;
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;

                if (r <= roof.getRed() && g <= roof.getGreen() && b <= roof.getBlue() && a <= roof.getAlpha()) {
                    r = Math.max(0, r - removeR);
                    g = Math.max(0, g - removeG);
                    b = Math.max(0, b - removeB);
                    a = Math.max(0, a - removeA);
                    if (isZeroTransparent && r == 0 && g == 0 && b == 0) a = 0;
                }

                // optional rule: fully transparent if black
                int newPixel = (a << 24) | (r << 16) | (g << 8) | b;
                result.setRGB(x, y, newPixel);
            }
        }
        return result;
    }



    public static BufferedImage fillImageWhiteBlack(BufferedImage input, Color white, Color black) throws IOException {
        if (input == null) throw new IOException("Invalid image.");

        // Load the PNG image
        BufferedImage image = new BufferedImage(input.getWidth(null), input.getHeight(null), BufferedImage.TRANSLUCENT);
        Graphics2D g2d = image.createGraphics();
        g2d.drawImage(input, 0, 0, input.getWidth(null), input.getHeight(null), null);
        g2d.dispose();
        // Get the width and height of the image
        int width = image.getWidth();
        int height = image.getHeight();
        if (black.getRed() + black.getBlue() + black.getGreen() > 500) {
            black = new Color((int) Math.max(0, black.getRed() * 0.9 - 80), (int) Math.max(0, black.getGreen() * 0.9 - 80), (int) Math.max(0, black.getBlue() * 0.9 - 80));
        } else if (black.getRed() + black.getBlue() + black.getGreen() > 400) {
            black = new Color((int) Math.max(0, black.getRed() * 0.9 - 65), (int) Math.max(0, black.getGreen() * 0.9 - 65), (int) Math.max(0, black.getBlue() * 0.9 - 65));
        } else if (black.getRed() + black.getBlue() + black.getGreen() > 300) {
            black = new Color((int) Math.max(0, black.getRed() * 0.9 - 50), (int) Math.max(0, black.getGreen() * 0.9 - 50), (int) Math.max(0, black.getBlue() * 0.9 - 50));
        } else if (black.getRed() + black.getBlue() + black.getGreen() > 200) {
            black = new Color((int) Math.max(0, black.getRed() * 0.9 - 35), (int) Math.max(0, black.getGreen() * 0.9 - 35), (int) Math.max(0, black.getBlue() * 0.9 - 35));
        }

        // Iterate through each pixel and fill non-transparent pixels
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF; // Extract the alpha channel
                double transparencyPercentage = (alpha / 255.0) * 100.0;
                if (transparencyPercentage > 20) { // more than 20% transparent
                    Color color = new Color(image.getRGB(x, y));
                    if (color.getBlue() == 0 && color.getRed() == 0 && color.getGreen() == 0) {
                        image.setRGB(x, y, black.getRGB());
                    } else if (color.getBlue() == 255 && color.getRed() == 255 && color.getGreen() == 255) {
                        image.setRGB(x, y, white.getRGB());
                    }
                }
            }
        }
        return image;
    }

    private static boolean isWhitish(Color color, int threshold) {
        return (color.getRed() > threshold && color.getGreen() > threshold && color.getBlue() > threshold);
    }
    private static boolean isBlackish(Color color, int threshold) {
        return (color.getRed() < threshold && color.getGreen() < threshold && color.getBlue() < threshold);
    }

    public static double getWidthOfAttributedString(Graphics2D graphics2D, AttributedString attributedString) {
        AttributedCharacterIterator characterIterator = attributedString.getIterator();
        FontRenderContext fontRenderContext = graphics2D.getFontRenderContext();
        LineBreakMeasurer lbm = new LineBreakMeasurer(characterIterator, fontRenderContext);
        TextLayout textLayout = lbm.nextLayout(Integer.MAX_VALUE);
        return textLayout.getBounds().getWidth();
    }
    public static AttributedString AddText(Graphics2D g2d, String Text, Color color, Font font, int x, int y, float size, boolean underLined, boolean fromBack) {
        AttributedString ClanName = new AttributedString(Text);
        if (font != null) {
            ClanName.addAttribute(TextAttribute.FONT, font.deriveFont(size));
        } else {
            ClanName.addAttribute(TextAttribute.SIZE, size);
        }
        if (underLined) {
            ClanName.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, 0, Text.length());
        }
        TextLayout textlayout = new TextLayout(ClanName.getIterator(), g2d.getFontRenderContext());
        Shape shape;
        if (fromBack) {
            shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x - getWidthOfAttributedString(g2d, ClanName), y));
        } else {
            shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x, y));
        }
        g2d.setColor(color);
        g2d.fill(shape);
        return ClanName;
    }
    public static AttributedString AddText(Graphics2D g2d, String Text, Color color, Font font, int x, int y, float size, boolean underLined, boolean fromBack, boolean outline, float outlineSize, Color outlineColor) {
        AttributedString ClanName = new AttributedString(Text);
        if (font != null) {
            ClanName.addAttribute(TextAttribute.FONT, font.deriveFont(size));
        } else {
            ClanName.addAttribute(TextAttribute.SIZE, size);
        }
        if (underLined) {
            ClanName.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, 0, Text.length());
        }
        TextLayout textlayout = new TextLayout(ClanName.getIterator(), g2d.getFontRenderContext());
        Shape shape;
        if (fromBack) {
            shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x - getWidthOfAttributedString(g2d, ClanName), y));
        } else {
            shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x, y));
        }

        Rectangle bounds = shape.getBounds();
        int padding = 2;  // Adjust the padding to make the gradient more prominent
        int gradientRadius = Math.max(bounds.width + padding, bounds.height + padding);  // Ensure the gradient covers the full width and height
        Point2D center = new Point2D.Float(x + bounds.width / 2f, y - bounds.height / 2f);
        float[] fractions = {0f, 1f};
        Color[] colors = {color.equals(Color.white) ? new Color(0, 0, 0, 40) : new Color(255, 255, 255, 40), new Color(0, 0, 0, 0)};  // Black to transparent
        RadialGradientPaint gradientPaint = new RadialGradientPaint(center, gradientRadius, fractions, colors);
        g2d.setPaint(gradientPaint);
        g2d.fill(new Rectangle2D.Double(bounds.getX() - (double) (padding / 2), bounds.getY() - (double) (padding / 2), bounds.getWidth() + padding, bounds.getHeight() + padding));


        if (outline) {
            g2d.setColor(outlineColor);
            g2d.setStroke(new BasicStroke(outlineSize));
            g2d.draw(shape);
        }
        g2d.setColor(color);
        g2d.fill(shape);
        return ClanName;
    }
    public static AttributedString AddTextCentered(Graphics2D g2d, String Text, Color color, Font font, int x, int y, float size) {
        AttributedString ClanName = new AttributedString(Text);
        if (font != null) {
            ClanName.addAttribute(TextAttribute.FONT, font.deriveFont(size));
        } else {
            ClanName.addAttribute(TextAttribute.SIZE, size);
        }
        TextLayout textlayout = new TextLayout(ClanName.getIterator(), g2d.getFontRenderContext());
        Shape shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x - (getWidthOfAttributedString(g2d, ClanName) / 2), y));
        g2d.setColor(color);
        g2d.fill(shape);
        return ClanName;
    }
    public static AttributedString AddTextCentered(Graphics2D g2d, String Text, Color color, Font font, int x, int y, float size, boolean underLined, boolean fromBack, boolean outline, float outlineSize, Color outlineColor) {
        AttributedString ClanName = new AttributedString(Text);
        if (font != null) {
            ClanName.addAttribute(TextAttribute.FONT, font.deriveFont(size));
        } else {
            ClanName.addAttribute(TextAttribute.SIZE, size);
        }
        if (underLined) {
            ClanName.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, 0, Text.length());
        }
        TextLayout textlayout = new TextLayout(ClanName.getIterator(), g2d.getFontRenderContext());
        Shape shape;
        if (fromBack) {
            shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x - getWidthOfAttributedString(g2d, ClanName), y));
        } else {
            shape = textlayout.getOutline(AffineTransform.getTranslateInstance(x - (getWidthOfAttributedString(g2d, ClanName) / 2), y));
        }

        Rectangle bounds = shape.getBounds();
        int padding = 2;  // Adjust the padding to make the gradient more prominent
        int gradientRadius = Math.max(bounds.width + padding, bounds.height + padding);  // Ensure the gradient covers the full width and height
        Point2D center = new Point2D.Float(x + bounds.width / 2f, y - bounds.height / 2f);
        float[] fractions = {0f, 1f};
        Color[] colors = {color.equals(Color.white) ? new Color(0, 0, 0, 40) : new Color(255, 255, 255, 40), new Color(0, 0, 0, 0)};  // Black to transparent
        RadialGradientPaint gradientPaint = new RadialGradientPaint(center, gradientRadius, fractions, colors);
        g2d.setPaint(gradientPaint);
        g2d.fill(new Rectangle2D.Double(bounds.getX() - (double) (padding / 2), bounds.getY() - (double) (padding / 2), bounds.getWidth() + padding, bounds.getHeight() + padding));


        if (outline) {
            g2d.setColor(outlineColor);
            g2d.setStroke(new BasicStroke(outlineSize));
            g2d.draw(shape);
        }
        g2d.setColor(color);
        g2d.fill(shape);
        return ClanName;
    }

    public static BufferedImage CutDiagonalTopLeft(BufferedImage originalImage) {

        // Calculate the width and height of the cropped image
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Create a new BufferedImage object to hold the cropped image
        BufferedImage croppedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Get the Graphics2D object of the cropped image
        Graphics2D g2d = croppedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Define a polygon that represents the transparent triangle to cut out of the image
        Polygon polygon = new Polygon();
        polygon.addPoint(0, 0); // Top Left
        polygon.addPoint(0, height); // Bottom Left
        polygon.addPoint((width / 2), (8 + height) / 2);
        polygon.addPoint((width / 2) - 20, (8 + height) / 2);

        polygon.addPoint(width - 32, 0); //Top Right

        // Clip the graphics context to the polygon
        g2d.setClip(polygon);

        // Draw the original image onto the clipped graphics context
        g2d.drawImage(originalImage, 0, 0, null);

        // Save the cropped image to a file

        g2d.dispose();
        return croppedImage;
    }
    public static BufferedImage CutDiagonalBottomRight(BufferedImage originalImage) {

        // Calculate the width and height of the cropped image
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Create a new BufferedImage object to hold the cropped image
        BufferedImage croppedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Get the Graphics2D object of the cropped image
        Graphics2D g2d = croppedImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Define a polygon that represents the transparent triangle to cut out of the image
        Polygon polygon = new Polygon();
        polygon.addPoint(width, height); // Bottom Left 500 500
        polygon.addPoint(32, height); // Bottom Left
        polygon.addPoint((width / 2) + 20,(-8 + height) / 2);
        polygon.addPoint((width / 2), (-8 + height) / 2);

        polygon.addPoint(width, 0); //Top Right

        // Clip the graphics context to the polygon
        g2d.setClip(polygon);

        // Draw the original image onto the clipped graphics context
        g2d.drawImage(originalImage, 0, 0, null);

        // Save the cropped image to a file

        g2d.dispose();
        return croppedImage;
    }

    public static BufferedImage CutTransparentBorders(File input) throws IOException {
        return CutTransparentBorders(ImageIO.read(input));
    }
    public static BufferedImage CutTransparentBorders(String url) throws IOException {
        return CutTransparentBorders(ImageIO.read(URI.create(url).toURL()));
    }
    public static BufferedImage CutTransparentBorders(InputStream image) throws IOException {
        return CutTransparentBorders(ImageIO.read(image));
    }
    public static BufferedImage CutTransparentBorders(byte[] imageBytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return CutTransparentBorders(ImageIO.read(is));
        }
    }
    public static BufferedImage CutTransparentBorders(BufferedImage image) throws IOException {
        if (image == null || image.getWidth() == 0 || image.getHeight() == 0) throw new IOException("Invalid image.");

        int boxWidth = image.getWidth();
        int boxHeight = image.getHeight();

        int x1 = boxWidth, y1 = boxHeight, x2 = 0, y2 = 0;
        boolean hasOpaque = false;

        for (int y = 0; y < boxHeight; y++) {
            for (int x = 0; x < boxWidth; x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                if (alpha != 0) {
                    if (x < x1) x1 = x;
                    if (x > x2) x2 = x;
                    if (y < y1) y1 = y;
                    if (y > y2) y2 = y;
                    hasOpaque = true;
                }
            }
        }

        if (!hasOpaque) {
            // No opaque pixels found, return transparent image of same size
            return new BufferedImage(boxWidth, boxHeight, BufferedImage.TYPE_INT_ARGB);
        }

        int newWidth = x2 - x1 + 1;
        int newHeight = y2 - y1 + 1;

        BufferedImage cropped = image.getSubimage(x1, y1, newWidth, newHeight);

        // Scale the cropped image to fit into original box size
        BufferedImage scaled = new BufferedImage(boxWidth, boxHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        double ratio = Math.min((double) boxWidth / newWidth, (double) boxHeight / newHeight);
        int scaledWidth = (int) (newWidth * ratio);
        int scaledHeight = (int) (newHeight * ratio);

        int xOffset = (boxWidth - scaledWidth) / 2;
        int yOffset = (boxHeight - scaledHeight) / 2;

        g.drawImage(cropped, xOffset, yOffset, scaledWidth, scaledHeight, null);
        g.dispose();
        return scaled;
    }
    public static File CutTransparentBorders(File input, File output, int boxWidth, int boxHeight) throws IOException {
        BufferedImage image = ImageIO.read(input);
        // Determine the new dimensions of the cropped image
        int x1 = 0, y1 = 0, x2 = image.getWidth() - 1, y2 = image.getHeight() - 1;
        boolean foundOpaquePixel = false;

        // Find the leftmost opaque pixel column
        while (!foundOpaquePixel && x1 <= x2) {
            for (int y = y1; y <= y2; y++) {
                if ((image.getRGB(x1, y) >> 24) != 0x00) {
                    foundOpaquePixel = true;
                    break;
                }
            }
            if (!foundOpaquePixel) {
                x1++;
            }
        }

        // Find the rightmost opaque pixel column
        foundOpaquePixel = false;
        while (!foundOpaquePixel && x2 >= x1) {
            for (int y = y1; y <= y2; y++) {
                if ((image.getRGB(x2, y) >> 24) != 0x00) {
                    foundOpaquePixel = true;
                    break;
                }
            }
            if (!foundOpaquePixel) {
                x2--;
            }
        }

        // Find the topmost opaque pixel row
        foundOpaquePixel = false;
        while (!foundOpaquePixel && y1 <= y2) {
            for (int x = x1; x <= x2; x++) {
                if ((image.getRGB(x, y1) >> 24) != 0x00) {
                    foundOpaquePixel = true;
                    break;
                }
            }
            if (!foundOpaquePixel) {
                y1++;
            }
        }

        // Find the bottommost opaque pixel row
        foundOpaquePixel = false;
        while (!foundOpaquePixel && y2 >= y1) {
            for (int x = x1; x <= x2; x++) {
                if ((image.getRGB(x, y2) >> 24) != 0x00) {
                    foundOpaquePixel = true;
                    break;
                }
            }
            if (!foundOpaquePixel) {
                y2--;
            }
        }

        // Crop the image to the new dimensions
        int newWidth = x2 - x1 + 1;
        int newHeight = y2 - y1 + 1;
        BufferedImage croppedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = croppedImage.createGraphics();
        g.drawImage(image, 0, 0, newWidth, newHeight, x1, y1, x2 + 1, y2 + 1, null);
        g.dispose();

        // Scale the cropped image to fit exactly in the box
        int scaledWidth, scaledHeight;
        if ((double)newWidth / boxWidth > (double)newHeight / boxHeight) {
            scaledWidth = boxWidth;
            scaledHeight = (int)((double)newHeight / newWidth * boxWidth);
        } else {
            scaledWidth = (int)((double)newWidth / newHeight * boxHeight);
            scaledHeight = boxHeight;
        }
        BufferedImage scaledImage = new BufferedImage(boxWidth, boxHeight, BufferedImage.TYPE_INT_ARGB);
        g = scaledImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(croppedImage, (boxWidth - scaledWidth) / 2, (boxHeight - scaledHeight) / 2, scaledWidth, scaledHeight, null);
        g.dispose();
        ImageIO.write(scaledImage, "png", output);
        return output;
    }

    public static boolean doesImageHasTransparentPixel(File input) throws IOException {
        return doesImageHasTransparentPixel(ImageIO.read(input));
    }
    public static boolean doesImageHasTransparentPixel(String url) throws IOException {
        return doesImageHasTransparentPixel(ImageIO.read(URI.create(url).toURL()));
    }
    public static boolean doesImageHasTransparentPixel(InputStream image) throws IOException {
        return doesImageHasTransparentPixel(ImageIO.read(image));
    }
    public static boolean doesImageHasTransparentPixel(byte[] imageBytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return doesImageHasTransparentPixel(ImageIO.read(is));
        }
    }
    public static boolean doesImageHasTransparentPixel(BufferedImage image) throws IOException {
        if (image == null) throw new IOException("Could not open or find the image");

        int width = image.getWidth();
        int height = image.getHeight();

        // Iterate through all pixels
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Get the RGB values of the pixel
                int rgb = image.getRGB(x, y);

                // Check if the pixel is transparent
                if ((rgb & 0xFF000000) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Color getDominantColor(File file) throws IOException {
        return file.exists() ? getDominantColor(ImageIO.read(file)) : Color.black;
    }
    public static Color getDominantColor(String url) throws IOException {
        return getDominantColor(ImageIO.read(URI.create(url).toURL()));
    }
    public static Color getDominantColor(InputStream image) throws IOException {
        return getDominantColor(ImageIO.read(image));
    }
    public static Color getDominantColor(byte[] imageBytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            return getDominantColor(ImageIO.read(is));
        }
    }
    public static Color getDominantColor(BufferedImage image) throws IOException {
        if (image == null) throw new IOException("Could not open or find the image");

        int width = image.getWidth();
        int height = image.getHeight();
        Map<Integer, Integer> colorCount = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (pixel != -16777216 && pixel != -1 && alpha > 0) {
                    colorCount.put(pixel, colorCount.getOrDefault(pixel, 0) + 1);
                }
            }
        }

        int maxCount = 0;
        int dominantColorRGB = 0;
        for (Map.Entry<Integer, Integer> entry : colorCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominantColorRGB = entry.getKey();
            }
        }

        Color color = new Color(dominantColorRGB);
        if (color.getRed() + color.getGreen() + color.getBlue() > 400) {
            color = adjustColor(color);
        }

        return color;
    }

    public static Color adjustColor(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        if (r > g && r > b) {
            return g > b ? new Color(Math.max(1, r - 10), Math.max(1, g - 10), Math.max(1, b - 60))
                    : new Color(Math.max(1, r - 10), Math.max(1, g - 60), Math.max(1, b - 10));
        } else if (g > r && g > b) {
            return r > b ? new Color(Math.max(1, r - 10), Math.max(1, g - 10), Math.max(1, b - 60))
                    : new Color(Math.max(1, r - 60), Math.max(1, g - 10), Math.max(1, b - 10));
        } else if (b > r && b > g) {
            return r > g ? new Color(Math.max(1, r - 10), Math.max(1, g - 60), Math.max(1, b - 10))
                    : new Color(Math.max(1, r - 60), Math.max(1, g - 10), Math.max(1, b - 10));
        } else {
            return new Color(Math.max(1, r - 25), Math.max(1, g - 25), Math.max(1, b - 25));
        }
    }

    public static String mixColors(Color color1, Color color2) {
        return mixColors(getHexValue(color1), getHexValue(color2));
    }
    public static String mixColors(String color1, String color2) {
        color1 = color1.replaceAll("#", "");
        color2 = color2.replaceAll("#", "");
        int r1 = Integer.parseInt(color1.substring(0, 2), 16);
        int g1 = Integer.parseInt(color1.substring(2, 4), 16);
        int b1 = Integer.parseInt(color1.substring(4, 6), 16);

        int r2 = Integer.parseInt(color2.substring(0, 2), 16);
        int g2 = Integer.parseInt(color2.substring(2, 4), 16);
        int b2 = Integer.parseInt(color2.substring(4, 6), 16);

        int mixedR = (r1 + r2) / 2;
        int mixedG = (g1 + g2) / 2;
        int mixedB = (b1 + b2) / 2;

        return "#" + String.format("%02X%02X%02X", mixedR, mixedG, mixedB);
    }

    public static File ResizeImage(File input, File output, double multiplier) throws IOException {
        ImageIO.write(ResizeImage(ImageIO.read(input),multiplier), "png", output);
        return output;
    }
    public static BufferedImage ResizeImage(BufferedImage img, double multiplier) throws IOException {
        if (img == null) throw new IOException("Could not open or find the image");
        BufferedImage scaledImage = new BufferedImage((int) (img.getWidth() * multiplier), (int) (img.getHeight() * multiplier), BufferedImage.TRANSLUCENT);
        Graphics2D g = scaledImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, (int) (img.getWidth() * multiplier), (int) (img.getHeight() * multiplier), null);
        g.dispose();
        return scaledImage;
    }
    public static InputStream ResizeImage(InputStream stream, double multiplier) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(stream);

            BufferedImage scaledImage = new BufferedImage((int) (image.getWidth(null) * multiplier), (int) (image.getHeight(null) * multiplier), BufferedImage.TRANSLUCENT);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.drawImage(image, 0, 0, (int) (image.getWidth(null) * multiplier), (int) (image.getHeight(null) * multiplier), null);
            g2d.dispose();

            ImageIO.write(scaledImage, "PNG", baos);
            try (InputStream inputStream = new ByteArrayInputStream(baos.toByteArray())) {
                return inputStream;
            }
        }
    }

    public static int countPixelOccurrences(Color color, BufferedImage image) {
        int count = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (color.equals(new Color(image.getRGB(x, y)))) {
                    count++;
                }
            }
        }
        return count;
    }

    public static Image createRightFadeGradient(Image originalImage, int amount, int withWidth, int withHeight) {
        if (amount <= 0) return originalImage;
        BufferedImage fadedImage = new BufferedImage(withWidth, withHeight, BufferedImage.TRANSLUCENT);
        Graphics2D g2d = fadedImage.createGraphics();
        g2d.drawImage(originalImage, 0, 0, withWidth, withHeight, null);
        GradientPaint gradient = new GradientPaint(
                withWidth - amount, 0, new Color(255, 255, 255, 255),
                withWidth, 0, new Color(255, 255, 255, 0),
                false
        );
        g2d.setComposite(AlphaComposite.DstIn);
        g2d.setPaint(gradient);
        g2d.fillRect(withWidth - amount, 0, amount, withHeight);
        g2d.dispose();
        return fadedImage;
    }

    public static File ImageToFile(BufferedImage image, File output) {
        try {
            ImageIO.write(image, "png", output);
            return output;
        } catch (IOException e) {
            return null;
        }
    }
    public static InputStream ImageToInputStream(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }
    public static InputStream FileToInputStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

}