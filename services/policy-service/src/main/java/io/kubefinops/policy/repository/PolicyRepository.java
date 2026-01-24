package io.kubefinops.policy.repository;

import io.kubefinops.policy.domain.Policy;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PolicyRepository extends MongoRepository<Policy, String> {
    List<Policy> findByNamespaceOrNamespaceIsNull(String namespace);
}
