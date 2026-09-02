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

    static void main(String[] args) throws IOException {
        //removeBlackBackground("D:/blue.png", "D:/blue2.png", 200);

        BufferedImage img = ImageUtils.recolorImage(
                new File("D:/blue.png"),
                Color.decode("#FF00FF")
        );
        ImageIO.write(img, "png", new File("D:/blue2.png"));
    }
}
