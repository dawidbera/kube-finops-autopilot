package io.kubefinops.recommender;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubefinops.recommender.domain.RecommendationReport;
import io.kubefinops.recommender.repository.RecommendationReportRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final RecommendationReportRepository repository;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${minio.bucket}")
    private String bucketName;

    public void generateAndStoreReport(String recommendationId, String workloadRef, Map<String, String> suggested, Double savings) {
        log.info("Generating detailed report for recommendation: {}", recommendationId);
        
        try {
            // 1. Ensure bucket exists
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            // 2. Prepare report content
            RecommendationReport report = RecommendationReport.builder()
                    .id(UUID.randomUUID().toString())
                    .recommendationId(recommendationId)
                    .workloadRef(workloadRef)
                    .suggestedResources(suggested)
                    .estimatedMonthlySavings(savings)
                    .s3Path(String.format("s3://%s/reports/%s.json", bucketName, recommendationId))
                    .generatedAt(Instant.now())
                    .build();

            // 3. Upload to MinIO
            byte[] content = objectMapper.writeValueAsBytes(report);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object("reports/" + recommendationId + ".json")
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType("application/json")
                            .build()
            );

            // 4. Save metadata to MongoDB
            repository.save(report);
            log.info("Report successfully uploaded to S3 and indexed: {}", report.getS3Path());

        } catch (Exception e) {
            log.error("Failed to generate or upload report for recommendation {}", recommendationId, e);
        }
    }
}
