package com.denizcan.stockorderpayment.domain.order;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="stock_publication_lines") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class StockPublicationLine {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long productId;
    @Column(nullable=false,length=100) private String barcode;
    @Column(nullable=false) private int quantity;
    public StockPublicationLine(Long product,String barcode,int quantity) {this.productId=product;this.barcode=barcode;this.quantity=quantity;}
}
