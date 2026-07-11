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

        BufferedImage img = ImageUtils.reducePixelColor(
                new File("D:/blue.png"),
                Color.decode("#FFFFFF"), false
        );
        ImageIO.write(img, "png", new File("D:/blue2.png"));


//        BufferedImage img2 = ImageUtils.replaceAllMatchingColor(
//                new File("D:/Brick.png"),
//                Color.decode("#000000"),
//                new Color(5,5,40), 150
//        );
//        ImageIO.write(img2, "png", new File("D:/Brick2.png"));
    }
}
