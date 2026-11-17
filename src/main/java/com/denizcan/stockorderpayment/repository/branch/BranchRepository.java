package com.denizcan.stockorderpayment.repository.branch;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByOrderByNameAsc();
}
