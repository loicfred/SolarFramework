package org.solarframework.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarReader extends ClassLoader {
    private final JarFile jar;

    public JarReader(Path jarPath) throws IOException {
        super("JarReader", Thread.currentThread().getContextClassLoader());
        jar = new JarFile(jarPath.toFile());
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        JarEntry entry = jar.getJarEntry("BOOT-INF/classes/" + name.replace('.', '/') + ".class");
        if (entry == null) throw new ClassNotFoundException(name);
        try (InputStream in = jar.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
