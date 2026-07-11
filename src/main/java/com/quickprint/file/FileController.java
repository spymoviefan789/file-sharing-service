package com.quickprint.file;

import com.quickprint.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService files;
    private final CurrentUser currentUser;

    public FileController(FileService files, CurrentUser currentUser) {
        this.files = files;
        this.currentUser = currentUser;
    }

    // Multipart upload: the file plus its nickname, optional usage limit
    // (omit for unlimited) and one-time flag.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(@RequestParam("file") MultipartFile file,
                               @RequestParam("nickname") String nickname,
                               @RequestParam(value = "shareLimit", required = false) Integer shareLimit,
                               @RequestParam(value = "oneTime", defaultValue = "false") boolean oneTime,
                               Principal principal) {
        StoredFile stored = files.upload(currentUser.require(principal), file, nickname, shareLimit, oneTime);
        return FileResponse.from(stored);
    }

    @GetMapping
    public List<FileResponse> list(Principal principal) {
        return files.list(currentUser.require(principal)).stream()
                .map(FileResponse::from)
                .toList();
    }
}
