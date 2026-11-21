package com.denizcan.stockorderpayment.repository.user;

import com.denizcan.stockorderpayment.domain.user.EmployeeRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRoleRepository extends JpaRepository<EmployeeRole, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from EmployeeRole r where r.id=:id")
    java.util.Optional<EmployeeRole> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @EntityGraph(attributePaths = "permissions")
    List<EmployeeRole> findAllByOrderByNameAsc();
}
