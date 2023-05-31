package com.arpangroup.awsproject.controller;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.IOUtils;
import org.apache.http.entity.ContentType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@RestController()
@RequestMapping("/files")
public class UploadController {
    private final AmazonS3 s3Client;
    private final String s3BucketName;
    private final ServletContext context;
    private final ServletRequest request;

    public UploadController(AmazonS3 s3Client, String s3BucketName, ServletContext context, ServletRequest request) {
        this.s3Client = s3Client;
        this.s3BucketName = s3BucketName;
        this.context = context;
        this.request = request;
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam(name = "file") MultipartFile file) throws IOException {
        // converting multipartFile into file type object
        File modifiedFile = new File(Objects.requireNonNull(file.getOriginalFilename()));
        FileOutputStream fos = new FileOutputStream(modifiedFile);
        fos.write(file.getBytes());

        // generating unique fileName
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // logic to upload file to s3 bucket
        s3Client.putObject(s3BucketName, fileName, modifiedFile);

        // delete the file
        modifiedFile.delete();
        return "file upload successfully in s3 bucket: " + fileName;
    }

    @GetMapping("")
    public List<Map<String, String>> getAllFiles () throws IOException {
        ObjectListing listing = s3Client.listObjects(s3BucketName);

//        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
//                .withBucketName(s3BucketName)
//                .withPrefix("m");
//        ObjectListing listing = s3Client.listObjects(listObjectsRequest);

        List<S3ObjectSummary> summaries = listing.getObjectSummaries();
        while (listing.isTruncated()) {
            listing = s3Client.listNextBatchOfObjects(listing);
            summaries.addAll(listing.getObjectSummaries());
        }

        List<Map<String, String>> results = new ArrayList<>();
        String baseUrl = request.getRemoteHost() + ":" + request.getServerPort() + context.getContextPath() + "/files/download?file=";
        for (S3ObjectSummary summary : summaries) {
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put("file", summary.getKey());
            resultMap.put("url", baseUrl + summary.getKey());
            resultMap.put("lastModified", summary.getLastModified().toString());
            resultMap.put("size", summary.getSize()+"");
            results.add(resultMap);
        }
        return results;

    }


    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadFileFromS3 (@RequestParam(name = "file") String fileName) throws IOException {
        S3Object s3Object = s3Client.getObject(s3BucketName, fileName);

        S3ObjectInputStream objectContent = s3Object.getObjectContent();
        byte[] byteArray = IOUtils.toByteArray(objectContent);

        // to download the file
        ByteArrayResource resource = new ByteArrayResource(byteArray);
        return ResponseEntity.ok()
                .contentLength(byteArray.length)
                .header("content-type", ContentType.APPLICATION_OCTET_STREAM.getMimeType())
                .header("content-disposition", "attachment;filename=\"" +fileName + "\"")
                .body(resource);
    }


    @DeleteMapping("/{fileName}")
    public ResponseEntity<String> deletFile (@PathVariable String fileName) throws IOException{
        s3Client.deleteObject(s3BucketName, fileName);
        return ResponseEntity.ok("Delete success");
    }
}
