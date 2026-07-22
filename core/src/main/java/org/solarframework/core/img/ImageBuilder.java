package org.solarframework.core.img;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ImageBuilder implements AutoCloseable {
    protected int GIFFrameTime = 40;
    protected BufferedImage PNG = null;
    protected List<BufferedImage> GIF = new ArrayList<>();

    public synchronized File DownloadPNGToFile() {
        return DownloadPNGToFile(null);
    }
    public synchronized File DownloadPNGToFile(File output) {
        try {
            if (output == null) output = new File("./temp/" + hashCode() + ".png");
            ImageIO.write(PNG, "png", output);
        } catch (IOException ignored) {}
        return output;
    }
    public synchronized File DownloadGIFToFile() {
        return DownloadGIFToFile(null);
    }
    public synchronized File DownloadGIFToFile(File output) {
        if (output == null) output = new File("./temp/" + hashCode() + ".gif");
        try (ImageOutputStream outputstream = new FileImageOutputStream(output);
             GifSequenceWriter writer = new GifSequenceWriter(outputstream, BufferedImage.TRANSLUCENT, GIFFrameTime, true)) {
            for (BufferedImage frame : GIF) writer.writeToSequence(frame);
        } catch (IOException ignored) {}
        return output;
    }

    @Override
    public void close() {
        PNG = null;
        GIF = null;
    }
}
