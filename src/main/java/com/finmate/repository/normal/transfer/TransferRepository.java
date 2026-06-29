package com.finmate.repository.normal.transfer;

import com.finmate.domain.normal.transfer.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
}
