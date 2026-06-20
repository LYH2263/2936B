package com.exam.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExamDiffResultDTO {
    private Long leftVersionId;
    private Integer leftVersionNumber;
    private Long rightVersionId;
    private Integer rightVersionNumber;
    private List<ExamDiffItemDTO> diffItems;
    private int addedCount;
    private int removedCount;
    private int scoreChangedCount;
    private int sequenceChangedCount;
}
