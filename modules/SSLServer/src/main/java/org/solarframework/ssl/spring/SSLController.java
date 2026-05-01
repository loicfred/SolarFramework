package org.solarframework.ssl.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;

@RestController
public class SSLController {
    protected static SSLService WAMPSERVER;

    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request, @RequestHeader("Host") String host) throws Exception {
        String baseDir = WAMPSERVER.getPath(host.split(":")[0]);
        if (baseDir == null) return ResponseEntity.notFound().build();
        String filePath = request.getRequestURI().substring(request.getContextPath().length());
        if (filePath.contains("..")) return ResponseEntity.badRequest().build();
        if (filePath.startsWith("/_")) filePath = filePath.replaceFirst("/_", "/");
        Resource resource;
        if (baseDir.startsWith("http")) {
            resource = new UrlResource(baseDir + filePath);
        } else {
            if (filePath.equals("/")) filePath += "index.html";
            resource = new UrlResource(Paths.get(baseDir, filePath).toUri());
        }
        if (!resource.exists()) return ResponseEntity.notFound().build();
        HttpHeaders headers = WAMPSERVER.getHeaders(host.split(":")[0]);
        headers.setContentType(getMediaType(filePath));
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    private static @NonNull MediaType getMediaType(String path) {
        MediaType mediaType = MediaType.TEXT_HTML;
        if (path.endsWith(".ico")) mediaType = MediaType.valueOf("image/x-icon");
        else if (path.endsWith(".png")) mediaType = MediaType.IMAGE_PNG;
        else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mediaType = MediaType.IMAGE_JPEG;
        else if (path.endsWith(".css")) mediaType = MediaType.valueOf("text/css");
        else if (path.endsWith(".js")) mediaType = MediaType.valueOf("application/javascript");
        else if (path.endsWith(".html")) mediaType = MediaType.TEXT_HTML;
        return mediaType;
    }
    
    private static boolean isImage(String path) {
        return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg");
    }
}