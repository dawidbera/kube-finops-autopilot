package io.kubefinops.recommender.repository;

import io.kubefinops.recommender.domain.RecommendationReport;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecommendationReportRepository extends MongoRepository<RecommendationReport, String> {
}
