package com.denizcan.stockorderpayment.service.branch;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.repository.branch.BranchStockRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchManagementService {
    private final BranchRepository branches;
    private final BranchStockRepository stock;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<BranchInfo> list(boolean includeArchived) {
        return branches.findAllByOrderByNameAsc().stream().filter(b -> includeArchived || b.isActive()).map(BranchInfo::from).toList();
    }

    @Transactional
    public BranchInfo create(String name, Branch.Kind kind) {
        Branch branch = new Branch(name.strip());
        branch.revise(name, kind == null ? Branch.Kind.BRANCH : kind, true);
        return save(branch);
    }

    @Transactional
    public BranchInfo update(Long id, long version, String name, Branch.Kind kind, boolean active) {
        Branch branch = branches.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube/depo bulunamadı."));
        if (branch.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "Şube/depo değişmiş. Listeyi yenileyip tekrar düzenle.");
        if (!active && branch.isActive()) {
            if (stock.existsByBranchIdAndQuantityGreaterThan(id, 0)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Arşivlemeden önce buradaki stokları aktar veya çıkış kaydet.");
            if (users.existsByBranchId(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Arşivlemeden önce atanmış çalışanları başka şubeye veya merkeze taşı.");
        }
        branch.revise(name, kind, active);
        return save(branch);
    }

    private BranchInfo save(Branch branch) {
        try { return BranchInfo.from(branches.saveAndFlush(branch)); }
        catch (DataIntegrityViolationException exception) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu şube/depo adı zaten kullanımda."); }
    }

    public record BranchInfo(Long id, String name, Branch.Kind kind, boolean active, long version) {
        static BranchInfo from(Branch b) { return new BranchInfo(b.getId(), b.getName(), b.getKind(), b.isActive(), b.getVersion()); }
    }
}
