package com.denizcan.stockorderpayment.service.order;

import com.denizcan.stockorderpayment.domain.order.*;
import com.denizcan.stockorderpayment.repository.order.*;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.denizcan.stockorderpayment.web.order.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class OrderLifecycleService {
    private final OrderRepository orders;
    private final StockRepository stockRows;
    private final StockService stock;
    private final OrderStateChangeRepository changes;
    @Transactional public OrderResponse change(Long id,OrderStatus expected,OrderStatus target,String actor,String note) {
        if(target!=OrderStatus.SHIPPED&&target!=OrderStatus.CANCELLED) throw conflict("Yalnızca sevk veya iptal işlemi yapılabilir.");
        var order=orders.findForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Sipariş bulunamadı."));
        if(order.getStatus()==target) return OrderMapper.toResponse(order); // Retry after a lost response is a no-op.
        if(order.getStatus()!=expected || order.getStatus()!=OrderStatus.CREATED&&order.getStatus()!=OrderStatus.PAID)
            throw conflict("Siparişin durumu değişmiş veya bu işlem artık yapılamıyor. Listeyi yenile.");
        SortedMap<Long,Integer> items=new TreeMap<>();order.getItems().forEach(i->items.merge(i.getProductId(),i.getQuantity(),Math::addExact));
        // Pre-lock all stock rows before updating any product total, matching batch transfer ordering.
        items.keySet().forEach(product->stockRows.findByProductIdForUpdate(product).orElseThrow(()->conflict("Sipariş stoğu bulunamadı.")));
        String description="Sipariş #"+id+" · "+actor+" · "+(target==OrderStatus.SHIPPED?"Sevk":"İptal")+(note==null||note.isBlank()?"":" · "+note.strip());
        if(description.length()>255) description=description.substring(0,255);
        for(var item:items.entrySet()) {
            if(target==OrderStatus.SHIPPED) stock.consumeReserved(item.getKey(),item.getValue(),description);
            else stock.release(item.getKey(),item.getValue(),description);
        }
        changes.save(new OrderStateChange(id,order.getStatus(),target,actor,note==null||note.isBlank()?null:note.strip()));
        order.changeStatus(target);return OrderMapper.toResponse(order);
    }
    public record Change(Long id,OrderStatus previousStatus,OrderStatus newStatus,String username,String note,java.time.Instant createdAt) {}
    @Transactional(readOnly=true) public List<Change> history(Long id) {
        if(!orders.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Sipariş bulunamadı.");
        return changes.findTop50ByOrderIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(c->new Change(c.getId(),c.getPreviousStatus(),c.getNewStatus(),c.getUsername(),c.getNote(),c.getCreatedAt())).toList();
    }
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
