package com.quickprint.receive;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

// Public — no account. The code travels in the request body, never the URL.
@RestController
@RequestMapping("/api/receive")
public class ReceiveController {

    private final ReceiveService receive;
    private final ReceiveRateLimiter rateLimiter;

    public ReceiveController(ReceiveService receive, ReceiveRateLimiter rateLimiter) {
        this.receive = receive;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/{nickname}")
    public ResponseEntity<byte[]> receive(@PathVariable String nickname,
                                          @RequestParam("code") String code,
                                          @RequestParam(value = "mode", defaultValue = "download") String mode,
                                          HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new TooManyAttemptsException();
        }
        boolean preview = mode.equalsIgnoreCase("preview");
        ReceiveService.Fetched fetched = receive.fetch(nickname, code, preview);

        // attachment => browser saves it; inline => browser renders it.
        ContentDisposition disposition = ContentDisposition
                .builder(preview ? "inline" : "attachment")
                .filename(fetched.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                // Stop the browser from second-guessing the declared type.
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(fetched.contentType()))
                .body(fetched.content());
    }
}
