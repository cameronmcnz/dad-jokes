package com.mcnz.probe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

@SpringBootApplication
@EnableScheduling
@RestController
@RequestMapping("/api")
@Tag(name = "Kubernetes Demo APIs", description = "RESTful APIs to introspect environment and simulate stress")
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    private final Environment environment;

    public App(Environment environment) {
        this.environment = environment;
    }
 

    @GetMapping("/env/info")
    @Operation(summary = "Get container and pod info")
    public Map<String, String> getContainerInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("podName", environment.getProperty("POD_NAME", "unknown"));
        info.put("namespace", environment.getProperty("POD_NAMESPACE", "default"));
        info.put("nodeName", environment.getProperty("NODE_NAME", "unknown"));
        info.put("containerId", UUID.randomUUID().toString());
        return info;
    }

    @GetMapping("/env/resources")
    @Operation(summary = "Get JVM and system metrics")
    public Map<String, Object> getResourceStats() {
        Map<String, Object> stats = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        stats.put("memoryFreeMB", runtime.freeMemory() / 1024 / 1024);
        stats.put("memoryUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        stats.put("memoryTotalMB", runtime.totalMemory() / 1024 / 1024);
        stats.put("availableProcessors", runtime.availableProcessors());
        return stats;
    }

    @GetMapping("/env/threads")
    @Operation(summary = "Get current thread count")
    public Map<String, Object> getThreadStats() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Map<String, Object> result = new HashMap<>();
        result.put("threadCount", threadMXBean.getThreadCount());
        result.put("peakThreadCount", threadMXBean.getPeakThreadCount());
        return result;
    }

    @PostMapping("/simulate/cpu")
    @Operation(summary = "Simulate CPU load")
    public String simulateCpuLoad(@RequestParam(defaultValue = "30") int durationSeconds) {
        new Thread(() -> {
            long end = System.currentTimeMillis() + durationSeconds * 1000;
            while (System.currentTimeMillis() < end) {
                Math.sqrt(Math.random());
            }
        }).start();
        return "CPU load started for " + durationSeconds + " seconds";
    }

    @PostMapping("/simulate/memory")
    @Operation(summary = "Simulate memory leak")
    public String simulateMemoryLeak(@RequestParam(defaultValue = "100") int megabytes) {
        List<byte[]> memoryEater = new ArrayList<>();
        for (int i = 0; i < megabytes; i++) {
            memoryEater.add(new byte[1024 * 1024]);
        }
        return "Allocated ~" + megabytes + "MB memory";
    }

    @PostMapping("/simulate/threads")
    @Operation(summary = "Spawn multiple threads")
    public String spawnThreads(@RequestParam(defaultValue = "50") int count) {
        for (int i = 0; i < count; i++) {
            new Thread(() -> {
                try {
                    Thread.sleep(100000);
                } catch (InterruptedException ignored) {}
            }).start();
        }
        return count + " threads started";
    }

    @PostMapping("/simulate/crash")
    @Operation(summary = "Force crash via exception")
    public String crashApp() {
        throw new RuntimeException("Simulated crash");
    }

    @PostMapping("/simulate/exit")
    @Operation(summary = "Exit the app to simulate crash loop")
    public String exitApp() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                System.exit(1);
            }
        }, 1000);
        return "App will exit in 1 second";
    }

    @GetMapping("/simulate/timeout")
    @Operation(summary = "Simulate long request")
    public String timeout(@RequestParam(defaultValue = "10") int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000);
        return "Completed after " + seconds + " seconds";
    }

    @GetMapping("/env/listfiles")
    @Operation(summary = "List files in /workingdirectory")
    public Map<String, Object> listWorkingDirectoryFiles() {
        File dir = new File("/workingdirectory");
        Map<String, Object> result = new HashMap<>();
        if (dir.exists() && dir.isDirectory()) {
            String[] files = dir.list();
            result.put("fileCount", files != null ? files.length : 0);
            result.put("files", files != null ? Arrays.asList(files) : Collections.emptyList());
        } else {
            result.put("error", "/workingdirectory does not exist or is not a directory");
        }
        return result;
    }
    
    @PostMapping("/env/writefiles")
    @Operation(summary = "Write a probe file into /workingdirectory with timestamp")
    public Map<String, Object> writeProbeFile() {
        Map<String, Object> result = new HashMap<>();
        File dir = new File("/workingdirectory");
        if (!dir.exists() || !dir.isDirectory()) {
            String msg = "/workingdirectory does not exist or is not a directory";
            result.put("error", msg);
            System.err.println("[ERROR] " + msg);
            return result;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        File file = new File(dir, "probe-" + timestamp + ".txt");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Probe file created at " + timestamp);
            result.put("fileName", file.getName());
            result.put("message", "File written successfully");
            System.out.println("[INFO] Successfully wrote file: " + file.getAbsolutePath());
        } catch (IOException e) {
            String errorType = file.exists() && !file.canWrite() ? "File exists but is read-only" : "IOException occurred";
            result.put("error", errorType);
            result.put("details", e.getMessage());
            System.err.println("[ERROR] Failed to write file: " + file.getAbsolutePath());
            e.printStackTrace();
        }
        return result;
    }
    
    @GetMapping("/env/logtest")
    @Operation(summary = "Generate sample log messages of various levels")
    public Map<String, Object> logTest() {
        Logger logger = LoggerFactory.getLogger(App.class);
        Map<String, Object> result = new HashMap<>();

        logger.trace("This is a TRACE log message - very fine-grained");
        logger.debug("This is a DEBUG log message - useful for debugging");
        logger.info("This is an INFO log message - general operational info");
        logger.warn("This is a WARN log message - something unexpected but not broken");
        logger.error("This is an ERROR log message - something went wrong");

        System.out.println("System.out.println: standard console output");
        System.err.println("System.err.println: standard error output");

        result.put("message", "Log messages emitted. Check application logs.");
        return result;
    }

    @GetMapping("/aws/info")
    @Operation(summary = "Get AWS metadata (Account and Region info)")
    public Map<String, String> getAwsInfo() {
        Map<String, String> result = new HashMap<>();
        try {
            StsClient stsClient = StsClient.builder()
                    .region(Region.AWS_GLOBAL)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            GetCallerIdentityResponse identity = stsClient.getCallerIdentity();
            result.put("account", identity.account());
            result.put("arn", identity.arn());
            result.put("userId", identity.userId());
            result.put("region", Region.of(System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "us-east-1").toString());

            // Try to fetch EC2 instance metadata (instance-id and type)
            String instanceId = readFile("/var/lib/cloud/data/instance-id")
                .orElse(System.getenv("EC2_INSTANCE_ID"));
            String instanceType = readFile("/var/lib/cloud/data/instance-type")
                .orElse(System.getenv("EC2_INSTANCE_TYPE"));

            if (instanceId != null) result.put("instanceId", instanceId);
            if (instanceType != null) result.put("instanceType", instanceType);
        } catch (Exception e) {
            result.put("error", "Not running in AWS or credentials unavailable: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/aws/dynamodb/tables")
    @Operation(summary = "List DynamoDB tables visible to this app")
    public Map<String, Object> listDynamoDbTables() {
        Map<String, Object> result = new HashMap<>();
        String regionName = resolveAwsRegion();

        result.put("region", regionName);

        try (DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(Region.of(regionName))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            ListTablesResponse response = dynamoDb.listTables();
            result.put("tables", response.tableNames());
            result.put("tableCount", response.tableNames().size());
        } catch (Exception e) {
            result.put("error", "Unable to list DynamoDB tables");
            result.put("details", e.getMessage());
        }

        return result;
    }

    @GetMapping("/aws/s3/buckets")
    @Operation(summary = "List S3 buckets visible to this app")
    public Map<String, Object> listS3Buckets() {
        Map<String, Object> result = new HashMap<>();
        String regionName = resolveAwsRegion();
        result.put("region", regionName);

        try (S3Client s3 = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            ListBucketsResponse response = s3.listBuckets();
            List<String> buckets = response.buckets().stream()
                    .map(b -> b.name())
                    .toList();
            result.put("buckets", buckets);
            result.put("bucketCount", buckets.size());
        } catch (Exception e) {
            result.put("error", "Unable to list S3 buckets");
            result.put("details", e.getMessage());
        }

        return result;
    }

    @GetMapping("/aws/s3/buckets/{bucketName}/objects")
    @Operation(summary = "List objects in an S3 bucket")
    public Map<String, Object> listS3BucketObjects(@org.springframework.web.bind.annotation.PathVariable String bucketName,
                                                   @RequestParam(required = false, defaultValue = "") String prefix) {
        Map<String, Object> result = new HashMap<>();
        String regionName = resolveAwsRegion();
        result.put("region", regionName);
        result.put("bucket", bucketName);
        result.put("prefix", prefix);

        try (S3Client s3 = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            ListObjectsV2Response response = s3.listObjectsV2(r -> r.bucket(bucketName).prefix(prefix));
            List<Map<String, Object>> objects = new ArrayList<>();
            for (S3Object obj : response.contents()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("key", obj.key());
                entry.put("size", obj.size());
                entry.put("lastModified", obj.lastModified() != null ? obj.lastModified().toString() : null);
                objects.add(entry);
            }
            result.put("objects", objects);
            result.put("objectCount", objects.size());
            result.put("isTruncated", response.isTruncated());
        } catch (Exception e) {
            result.put("error", "Unable to list objects in bucket");
            result.put("details", e.getMessage());
        }

        return result;
    }

    @GetMapping("/aws/s3/download")
    @Operation(summary = "Download an S3 object by bucket and key")
    public org.springframework.http.ResponseEntity<?> downloadS3Object(@RequestParam String bucket,
                                                                       @RequestParam String key) {
        String regionName = resolveAwsRegion();
        try (S3Client s3 = S3Client.builder()
                .region(Region.of(regionName))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> response = s3.getObjectAsBytes(request);

            String contentType = response.response().contentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + key + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(response.asByteArray());
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unable to download S3 object");
            error.put("details", e.getMessage());
            error.put("bucket", bucket);
            error.put("key", key);
            error.put("region", regionName);
            return org.springframework.http.ResponseEntity.status(500).body(error);
        }
    }

    private Optional<String> readFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return Optional.of(reader.readLine());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String resolveAwsRegion() {
        String regionName = System.getenv("AWS_REGION");
        if (regionName == null || regionName.isBlank()) {
            regionName = System.getenv("AWS_DEFAULT_REGION");
        }
        if (regionName == null || regionName.isBlank()) {
            regionName = "us-east-1";
        }
        return regionName;
    }
} 
