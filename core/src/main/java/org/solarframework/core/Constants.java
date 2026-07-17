package org.solarframework.core;

import org.solarframework.core.util.ImageUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.ZoneId;

public class Constants {
    
    public static ZoneId ProgramZoneId = ZoneId.of("Europe/Paris");

    public static void main(String[] args) throws IOException {

        BufferedImage img2 = ImageUtils.replaceAllMatchingColor(
                new File("D:/Letters.png"),
                Color.decode("#000000"),
                new Color(0,0,0, 0), 180
        );
        ImageIO.write(img2, "png", new File("D:/Letters2.png"));
    }
}
