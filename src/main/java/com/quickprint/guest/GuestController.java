package com.quickprint.guest;

import com.quickprint.file.FileService;
import com.quickprint.receive.TooManyAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

// Public, no account: anyone can send one file and gets back the generated
// nickname + fixed code to hand to the receiver. Abuse is contained by the
// per-IP rate limit, the image/PDF-only policy, one-time use and a short TTL.
@RestController
@RequestMapping("/api/guest")
public class GuestController {

    private final FileService files;
    private final GuestUploadRateLimiter rateLimiter;

    public GuestController(FileService files, GuestUploadRateLimiter rateLimiter) {
        this.files = files;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> upload(@RequestParam("file") MultipartFile file,
                                      HttpServletRequest request) {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            throw new TooManyAttemptsException();
        }
        FileService.GuestUpload result = files.uploadGuest(file);
        return Map.of("nickname", result.nickname(), "code", result.code());
    }
}
