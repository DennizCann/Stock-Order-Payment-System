package com.denizcan.stockorderpayment.repository.user;

import com.denizcan.stockorderpayment.domain.user.EmployeeRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRoleRepository extends JpaRepository<EmployeeRole, Long> {
    @EntityGraph(attributePaths = "permissions")
    List<EmployeeRole> findAllByOrderByNameAsc();
}
