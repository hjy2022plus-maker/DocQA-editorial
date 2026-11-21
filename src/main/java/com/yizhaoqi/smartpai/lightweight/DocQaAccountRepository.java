package com.yizhaoqi.smartpai.lightweight;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface DocQaAccountRepository extends JpaRepository<DocQaAccount, Long> {
    Optional<DocQaAccount> findByUsername(String username);
}
