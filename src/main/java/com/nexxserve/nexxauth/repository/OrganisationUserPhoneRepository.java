package com.nexxserve.nexxauth.repository;

import com.nexxserve.nexxauth.entity.OrganisationUserPhone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationUserPhoneRepository extends JpaRepository<OrganisationUserPhone, Long> {

    List<OrganisationUserPhone> findByUserId(Long userId);

    Optional<OrganisationUserPhone> findByOrganisationIdAndPhone(Long organisationId, String phone);

    boolean existsByOrganisationIdAndPhone(Long organisationId, String phone);
}
