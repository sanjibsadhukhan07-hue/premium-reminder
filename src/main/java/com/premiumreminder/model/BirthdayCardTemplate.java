package com.premiumreminder.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "birthday_card_template")
@Getter
@Setter
@NoArgsConstructor
public class BirthdayCardTemplate {

    @Id
    private Long id = 1L;

    @Lob
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Basic(fetch = FetchType.LAZY)
    private byte[] imageData;

    private String contentType;
    private String fileName;

    private int width;
    private int height;

    private Integer nameY;
    private Integer dateY;
    private Integer nameFontSizeMax;
    private Integer nameFontSizeMin;
    private Integer dateFontSize;
    private Integer sideMargin;

    private String textColorHex;
    private String shadowColorHex;

    private LocalDateTime updatedAt = LocalDateTime.now();
}