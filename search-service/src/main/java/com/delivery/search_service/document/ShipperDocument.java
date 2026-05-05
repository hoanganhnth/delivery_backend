package com.delivery.search_service.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "shipper")
public class ShipperDocument {
    @Id
    private String id;
    
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;
    
    @Field(type = FieldType.Keyword)
    private String vehicleType;
    
    @Field(type = FieldType.Keyword)
    private String licensePlate;
    
    @Field(type = FieldType.Double)
    private Double rating;
    
    @Field(type = FieldType.Boolean)
    private Boolean isOnline;
}
