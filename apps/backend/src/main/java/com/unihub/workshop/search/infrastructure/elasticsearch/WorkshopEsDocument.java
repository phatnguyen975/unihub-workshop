package com.unihub.workshop.search.infrastructure.elasticsearch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "workshops")
public class WorkshopEsDocument {

    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Keyword)
    private String organizationId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword, index = false)
    private String coverImageUrl;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Keyword)
    private String timezone;

    @Field(type = FieldType.Date)
    private Instant startsAt;

    @Field(type = FieldType.Date)
    private Instant endsAt;

    @Field(type = FieldType.Integer)
    private Integer capacity;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private Instant publishedAt;

    @Field(type = FieldType.Nested)
    private List<SpeakerEsSubDocument> speakers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpeakerEsSubDocument {

        @Field(type = FieldType.Keyword)
        private String id;

        @Field(type = FieldType.Text)
        private String name;

        @Field(type = FieldType.Text)
        private String bio;

        @Field(type = FieldType.Keyword, index = false)
        private String avatarUrl;

        @Field(type = FieldType.Keyword, index = false)
        private String linkedinUrl;

        @Field(type = FieldType.Keyword)
        private String email;

        @Field(type = FieldType.Integer)
        private Integer sortOrder;
    }
}
