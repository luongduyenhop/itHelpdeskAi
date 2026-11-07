package com.aihelpdesk.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllowedEmailDomainRepository extends JpaRepository<AllowedEmailDomain, Long> {

    Optional<AllowedEmailDomain> findByDomainName(String domainName);

    boolean existsByDomainName(String domainName);

    Optional<AllowedEmailDomain> findByDomainNameAndActiveTrue(String domainName);
}
