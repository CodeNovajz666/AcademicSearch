package com.example.literaturesearch.dto.response;

import com.example.literaturesearch.entity.Literature;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiteratureResponse {
    private Long id;
    private String title;
    private String authors;
    private String abstractText;
    private String journal;
    private Integer year;
    private String doi;
    private String fileName;
<<<<<<< HEAD
    private String processStatus;
    private String mainContent;
    private String methods;
    private String experiments;
    private String conclusions;
    /** 当前用户的私有状态（未登录场景或无记录时为 null） */
    private Boolean starred;
    private Boolean read;
    private String note;
    /** 上传者（社区总库展示"来自谁"） */
    private Long userId;
    private String uploaderName;
    /** true=已贡献到社区总库；false=私有；null=历史数据（视为公开） */
    private Boolean isPublic;
    /** 社区板块（computer-science/engineering/science/medicine/social-science） */
    private String category;
=======
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
    private LocalDateTime createdAt;

    public static LiteratureResponse fromEntity(Literature literature) {
        return LiteratureResponse.builder()
                .id(literature.getId())
                .title(literature.getTitle())
                .authors(literature.getAuthors())
                .abstractText(literature.getAbstractText())
                .journal(literature.getJournal())
                .year(literature.getYear())
                .doi(literature.getDoi())
                .fileName(literature.getFileName())
<<<<<<< HEAD
                // 历史数据无状态字段，视为已就绪
                .processStatus(literature.getProcessStatus() == null
                        ? "READY" : literature.getProcessStatus())
                .mainContent(literature.getMainContent())
                .methods(literature.getMethods())
                .experiments(literature.getExperiments())
                .conclusions(literature.getConclusions())
                .userId(literature.getUserId())
                .isPublic(literature.getIsPublic())
                .category(literature.getCategory())
                .createdAt(literature.getCreatedAt())
                .build();
    }

    public LiteratureResponse withState(com.example.literaturesearch.entity.UserLiteratureState state) {
        if (state == null) {
            return this;
        }
        setStarred(state.getStarred());
        setRead(state.getReadStatus());
        setNote(state.getNote());
        return this;
    }
=======
                .createdAt(literature.getCreatedAt())
                .build();
    }
>>>>>>> b6fcf7be5afab621f5f617923f0784264c8a43e4
}