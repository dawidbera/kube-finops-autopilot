package io.kubefinops.policy.repository;

import io.kubefinops.policy.domain.Recommendation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationRepository extends MongoRepository<Recommendation, String> {
    List<Recommendation> findByNamespaceAndStatusIn(String namespace, List<String> statuses);
}
