package com.exam.dto;

public class PkAnswerDetail {
    private Integer questionIndex;
    private Long questionId;
    private String answer;
    private Boolean isCorrect;
    private Integer timeUsed;
    private String correctAnswer;

    public Integer getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(Integer questionIndex) { this.questionIndex = questionIndex; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }

    public Integer getTimeUsed() { return timeUsed; }
    public void setTimeUsed(Integer timeUsed) { this.timeUsed = timeUsed; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
}
