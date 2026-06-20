package com.exam.dto;

import com.exam.entity.PkAnswer;
import com.exam.entity.Question;
import java.util.List;

public class PkStateResponse {
    private Long sessionId;
    private String state;
    private Integer currentQuestionIndex;
    private Integer totalQuestions;
    private Question currentQuestion;
    private Integer myScore;
    private Integer opponentScore;
    private String opponentName;
    private Long opponentId;
    private Boolean opponentAnswered;
    private Integer timeLeft;
    private Boolean isBotGame;
    private String result;
    private String winnerName;
    private List<PkAnswerDetail> myAnswers;
    private List<PkAnswerDetail> opponentAnswers;
    private List<Question> questions;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Integer getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(Integer currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Question getCurrentQuestion() { return currentQuestion; }
    public void setCurrentQuestion(Question currentQuestion) { this.currentQuestion = currentQuestion; }

    public Integer getMyScore() { return myScore; }
    public void setMyScore(Integer myScore) { this.myScore = myScore; }

    public Integer getOpponentScore() { return opponentScore; }
    public void setOpponentScore(Integer opponentScore) { this.opponentScore = opponentScore; }

    public String getOpponentName() { return opponentName; }
    public void setOpponentName(String opponentName) { this.opponentName = opponentName; }

    public Long getOpponentId() { return opponentId; }
    public void setOpponentId(Long opponentId) { this.opponentId = opponentId; }

    public Boolean getOpponentAnswered() { return opponentAnswered; }
    public void setOpponentAnswered(Boolean opponentAnswered) { this.opponentAnswered = opponentAnswered; }

    public Integer getTimeLeft() { return timeLeft; }
    public void setTimeLeft(Integer timeLeft) { this.timeLeft = timeLeft; }

    public Boolean getIsBotGame() { return isBotGame; }
    public void setIsBotGame(Boolean isBotGame) { this.isBotGame = isBotGame; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

    public List<PkAnswerDetail> getMyAnswers() { return myAnswers; }
    public void setMyAnswers(List<PkAnswerDetail> myAnswers) { this.myAnswers = myAnswers; }

    public List<PkAnswerDetail> getOpponentAnswers() { return opponentAnswers; }
    public void setOpponentAnswers(List<PkAnswerDetail> opponentAnswers) { this.opponentAnswers = opponentAnswers; }

    public List<Question> getQuestions() { return questions; }
    public void setQuestions(List<Question> questions) { this.questions = questions; }
}
