package com.denizcan.stockorderpayment.web.branch;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branches;

    @GetMapping
    public List<BranchResponse> list() {
        return branches.findAllByOrderByNameAsc().stream().map(BranchResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@Valid @RequestBody BranchRequest request) {
        try {
            return BranchResponse.from(branches.saveAndFlush(new Branch(request.name().strip())));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu şube adı zaten kullanımda.", exception);
        }
    }

    public record BranchRequest(@NotBlank @Size(max = 100) String name) {}

    public record BranchResponse(Long id, String name) {
        static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getName());
        }
    }
}
