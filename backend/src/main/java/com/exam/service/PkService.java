package com.exam.service;

import com.exam.dto.PkAnswerDetail;
import com.exam.dto.PkAnswerRequest;
import com.exam.dto.PkRankingItem;
import com.exam.dto.PkStateResponse;
import com.exam.entity.*;
import com.exam.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PkService {
    private final PkSessionRepository pkSessionRepository;
    private final PkAnswerRepository pkAnswerRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int QUESTION_COUNT = 10;
    private static final int TIME_PER_QUESTION = 30;
    private static final int SCORE_PER_QUESTION = 10;
    private static final int DISCONNECT_TIMEOUT_SECONDS = 60;

    public PkService(PkSessionRepository pkSessionRepository, PkAnswerRepository pkAnswerRepository,
                     QuestionRepository questionRepository, UserRepository userRepository) {
        this.pkSessionRepository = pkSessionRepository;
        this.pkAnswerRepository = pkAnswerRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public PkSession createMatch(String username) {
        User player = userRepository.findByUsername(username).orElseThrow();

        Optional<PkSession> existing = pkSessionRepository.findActiveSessionByPlayerId(player.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        List<PkSession> available = pkSessionRepository.findAvailableSessions(player.getId());
        if (!available.isEmpty()) {
            PkSession session = available.get(0);
            session.setPlayer2(player);
            session.setState("IN_PROGRESS");
            session.setStartTime(LocalDateTime.now());
            session.setCurrentQuestionStartTime(LocalDateTime.now());
            session.setPlayer1LastActive(LocalDateTime.now());
            session.setPlayer2LastActive(LocalDateTime.now());
            generateQuestions(session);
            return pkSessionRepository.save(session);
        }

        PkSession session = new PkSession();
        session.setPlayer1(player);
        session.setState("WAITING");
        session.setQuestionCount(QUESTION_COUNT);
        session.setIsBotGame(false);
        session.setPlayer1LastActive(LocalDateTime.now());
        return pkSessionRepository.save(session);
    }

    @Transactional
    public PkSession createBotMatch(String username) {
        User player = userRepository.findByUsername(username).orElseThrow();

        Optional<PkSession> existing = pkSessionRepository.findActiveSessionByPlayerId(player.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        User bot = userRepository.findByUsername("pk_bot").orElseGet(() -> {
            User newBot = new User();
            newBot.setUsername("pk_bot");
            newBot.setPassword("N/A");
            newBot.setRole("BOT");
            newBot.setFullName("PK小助手");
            return userRepository.save(newBot);
        });

        PkSession session = new PkSession();
        session.setPlayer1(player);
        session.setPlayer2(bot);
        session.setState("IN_PROGRESS");
        session.setStartTime(LocalDateTime.now());
        session.setCurrentQuestionStartTime(LocalDateTime.now());
        session.setQuestionCount(QUESTION_COUNT);
        session.setIsBotGame(true);
        session.setPlayer1LastActive(LocalDateTime.now());
        session.setPlayer2LastActive(LocalDateTime.now());
        generateQuestions(session);
        return pkSessionRepository.save(session);
    }

    private void generateQuestions(PkSession session) {
        List<Question> allQuestions = questionRepository.findAll();
        List<Question> objectiveQuestions = allQuestions.stream()
                .filter(q -> "SINGLE".equals(q.getType()) || "MULTI".equals(q.getType()) || "JUDGE".equals(q.getType()))
                .collect(Collectors.toList());

        Collections.shuffle(objectiveQuestions);
        List<Question> selected = objectiveQuestions.subList(0, Math.min(QUESTION_COUNT, objectiveQuestions.size()));

        List<Long> questionIds = selected.stream().map(Question::getId).collect(Collectors.toList());
        try {
            session.setQuestionIds(objectMapper.writeValueAsString(questionIds));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize question IDs", e);
        }
    }

    private List<Long> parseQuestionIds(String questionIdsStr) {
        try {
            return objectMapper.readValue(questionIdsStr, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Question getQuestionByIndex(PkSession session, int index) {
        List<Long> questionIds = parseQuestionIds(session.getQuestionIds());
        if (index < 0 || index >= questionIds.size()) return null;
        return questionRepository.findById(questionIds.get(index)).orElse(null);
    }

    private boolean isPlayer1(PkSession session, Long playerId) {
        return session.getPlayer1() != null && session.getPlayer1().getId().equals(playerId);
    }

    private User getOpponent(PkSession session, Long playerId) {
        if (isPlayer1(session, playerId)) {
            return session.getPlayer2();
        }
        return session.getPlayer1();
    }

    @Transactional
    public PkAnswer submitAnswer(Long sessionId, String username, PkAnswerRequest request) {
        User player = userRepository.findByUsername(username).orElseThrow();
        PkSession session = pkSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("PK session not found"));

        if (!"IN_PROGRESS".equals(session.getState())) {
            throw new RuntimeException("PK session is not in progress");
        }

        if (!isPlayerInSession(session, player.getId())) {
            throw new RuntimeException("You are not in this PK session");
        }

        if (isPlayer1(session, player.getId())) {
            session.setPlayer1LastActive(LocalDateTime.now());
        } else {
            session.setPlayer2LastActive(LocalDateTime.now());
        }

        int currentIndex = session.getCurrentQuestionIndex();
        Optional<PkAnswer> existingAnswer = pkAnswerRepository
                .findBySessionIdAndPlayerIdAndQuestionIndex(sessionId, player.getId(), currentIndex);
        if (existingAnswer.isPresent()) {
            return existingAnswer.get();
        }

        Question question = getQuestionByIndex(session, currentIndex);
        if (question == null) {
            throw new RuntimeException("Question not found");
        }

        boolean correct = checkAnswer(question, request.getAnswer());

        PkAnswer answer = new PkAnswer();
        answer.setSession(session);
        answer.setPlayer(player);
        answer.setQuestionId(question.getId());
        answer.setQuestionIndex(currentIndex);
        answer.setAnswer(request.getAnswer());
        answer.setIsCorrect(correct);
        answer.setTimeUsed(request.getTimeUsed());
        answer.setAnsweredAt(LocalDateTime.now());
        pkAnswerRepository.save(answer);

        if (correct) {
            if (isPlayer1(session, player.getId())) {
                session.setPlayer1Score(session.getPlayer1Score() + SCORE_PER_QUESTION);
            } else {
                session.setPlayer2Score(session.getPlayer2Score() + SCORE_PER_QUESTION);
            }
        }

        pkSessionRepository.save(session);

        checkAndAdvanceQuestion(session);

        return answer;
    }

    private boolean isPlayerInSession(PkSession session, Long playerId) {
        return (session.getPlayer1() != null && session.getPlayer1().getId().equals(playerId))
                || (session.getPlayer2() != null && session.getPlayer2().getId().equals(playerId));
    }

    private boolean checkAnswer(Question question, String userAnswer) {
        if (userAnswer == null || question.getAnswer() == null) return false;
        return question.getAnswer().trim().equals(userAnswer.trim());
    }

    private void checkAndAdvanceQuestion(PkSession session) {
        int currentIndex = session.getCurrentQuestionIndex();
        boolean p1Answered = pkAnswerRepository
                .findBySessionIdAndPlayerIdAndQuestionIndex(session.getId(), session.getPlayer1().getId(), currentIndex)
                .isPresent();
        boolean p2Answered = session.getPlayer2() != null && pkAnswerRepository
                .findBySessionIdAndPlayerIdAndQuestionIndex(session.getId(), session.getPlayer2().getId(), currentIndex)
                .isPresent();

        if (p1Answered && p2Answered) {
            advanceToNextQuestion(session);
        }
    }

    private void advanceToNextQuestion(PkSession session) {
        int nextIndex = session.getCurrentQuestionIndex() + 1;
        if (nextIndex >= session.getQuestionCount()) {
            finishSession(session);
        } else {
            session.setCurrentQuestionIndex(nextIndex);
            session.setCurrentQuestionStartTime(LocalDateTime.now());
            pkSessionRepository.save(session);
        }
    }

    private void finishSession(PkSession session) {
        session.setState("FINISHED");
        session.setEndTime(LocalDateTime.now());

        int p1Score = session.getPlayer1Score();
        int p2Score = session.getPlayer2Score();

        if (p1Score > p2Score) {
            session.setWinner(session.getPlayer1());
        } else if (p2Score > p1Score) {
            session.setWinner(session.getPlayer2());
        }

        pkSessionRepository.save(session);
    }

    @Transactional
    public PkStateResponse getState(Long sessionId, String username) {
        User player = userRepository.findByUsername(username).orElseThrow();
        PkSession session = pkSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("PK session not found"));

        if (!isPlayerInSession(session, player.getId())) {
            throw new RuntimeException("You are not in this PK session");
        }

        if (isPlayer1(session, player.getId())) {
            session.setPlayer1LastActive(LocalDateTime.now());
        } else {
            session.setPlayer2LastActive(LocalDateTime.now());
        }
        pkSessionRepository.save(session);

        if (session.getIsBotGame() && "IN_PROGRESS".equals(session.getState())) {
            simulateBotAnswer(session);
        }

        PkStateResponse response = new PkStateResponse();
        response.setSessionId(session.getId());
        response.setState(session.getState());
        response.setCurrentQuestionIndex(session.getCurrentQuestionIndex());
        response.setTotalQuestions(session.getQuestionCount());
        response.setIsBotGame(session.getIsBotGame());

        User opponent = getOpponent(session, player.getId());
        if (opponent != null) {
            response.setOpponentId(opponent.getId());
            response.setOpponentName(opponent.getFullName() != null ? opponent.getFullName() : opponent.getUsername());
        }

        if (isPlayer1(session, player.getId())) {
            response.setMyScore(session.getPlayer1Score());
            response.setOpponentScore(session.getPlayer2Score());
        } else {
            response.setMyScore(session.getPlayer2Score());
            response.setOpponentScore(session.getPlayer1Score());
        }

        if ("IN_PROGRESS".equals(session.getState())) {
            Question currentQuestion = getQuestionByIndex(session, session.getCurrentQuestionIndex());
            response.setCurrentQuestion(currentQuestion);

            boolean opponentAnswered = pkAnswerRepository
                    .findBySessionIdAndPlayerIdAndQuestionIndex(sessionId, opponent.getId(), session.getCurrentQuestionIndex())
                    .isPresent();
            response.setOpponentAnswered(opponentAnswered);

            int timeLeft = calculateTimeLeft(session);
            response.setTimeLeft(timeLeft);

            if (timeLeft <= 0) {
                forceTimeoutAnswers(session);
            }
        }

        if ("FINISHED".equals(session.getState())) {
            response.setResult(session.getWinner() == null ? "DRAW" :
                    (session.getWinner().getId().equals(player.getId()) ? "WIN" : "LOSE"));
            if (session.getWinner() != null) {
                response.setWinnerName(session.getWinner().getFullName() != null ?
                        session.getWinner().getFullName() : session.getWinner().getUsername());
            }

            List<PkAnswer> myAnswers = pkAnswerRepository.findBySessionIdAndPlayerIdOrderByQuestionIndexAsc(sessionId, player.getId());
            List<PkAnswer> opponentAnswers = pkAnswerRepository.findBySessionIdAndPlayerIdOrderByQuestionIndexAsc(sessionId, opponent.getId());

            response.setMyAnswers(convertToAnswerDetails(myAnswers, session));
            response.setOpponentAnswers(convertToAnswerDetails(opponentAnswers, session));

            List<Question> allQuestions = new ArrayList<>();
            for (int i = 0; i < session.getQuestionCount(); i++) {
                Question q = getQuestionByIndex(session, i);
                if (q != null) allQuestions.add(q);
            }
            response.setQuestions(allQuestions);
        }

        return response;
    }

    private int calculateTimeLeft(PkSession session) {
        if (session.getCurrentQuestionStartTime() == null) return TIME_PER_QUESTION;
        long elapsedSeconds = java.time.Duration.between(session.getCurrentQuestionStartTime(), LocalDateTime.now()).getSeconds();
        int timeLeft = TIME_PER_QUESTION - (int) elapsedSeconds;
        return Math.max(0, timeLeft);
    }

    private void forceTimeoutAnswers(PkSession session) {
        int currentIndex = session.getCurrentQuestionIndex();
        List<Long> playerIds = new ArrayList<>();
        if (session.getPlayer1() != null) playerIds.add(session.getPlayer1().getId());
        if (session.getPlayer2() != null) playerIds.add(session.getPlayer2().getId());

        for (Long pid : playerIds) {
            Optional<PkAnswer> answer = pkAnswerRepository
                    .findBySessionIdAndPlayerIdAndQuestionIndex(session.getId(), pid, currentIndex);
            if (answer.isEmpty()) {
                Question question = getQuestionByIndex(session, currentIndex);
                PkAnswer timeoutAnswer = new PkAnswer();
                timeoutAnswer.setSession(session);
                timeoutAnswer.setPlayer(userRepository.findById(pid).orElse(null));
                timeoutAnswer.setQuestionId(question != null ? question.getId() : null);
                timeoutAnswer.setQuestionIndex(currentIndex);
                timeoutAnswer.setAnswer("");
                timeoutAnswer.setIsCorrect(false);
                timeoutAnswer.setTimeUsed(TIME_PER_QUESTION);
                timeoutAnswer.setAnsweredAt(LocalDateTime.now());
                pkAnswerRepository.save(timeoutAnswer);
            }
        }
        advanceToNextQuestion(session);
    }

    private List<PkAnswerDetail> convertToAnswerDetails(List<PkAnswer> answers, PkSession session) {
        return answers.stream().map(a -> {
            PkAnswerDetail detail = new PkAnswerDetail();
            detail.setQuestionIndex(a.getQuestionIndex());
            detail.setQuestionId(a.getQuestionId());
            detail.setAnswer(a.getAnswer());
            detail.setIsCorrect(a.getIsCorrect());
            detail.setTimeUsed(a.getTimeUsed());
            Question q = getQuestionByIndex(session, a.getQuestionIndex());
            if (q != null) detail.setCorrectAnswer(q.getAnswer());
            return detail;
        }).collect(Collectors.toList());
    }

    private void simulateBotAnswer(PkSession session) {
        if (session.getPlayer2() == null || !"pk_bot".equals(session.getPlayer2().getUsername())) return;

        int currentIndex = session.getCurrentQuestionIndex();
        Optional<PkAnswer> botAnswer = pkAnswerRepository
                .findBySessionIdAndPlayerIdAndQuestionIndex(session.getId(), session.getPlayer2().getId(), currentIndex);

        if (botAnswer.isPresent()) return;

        Random random = new Random();
        int delaySeconds = random.nextInt(15) + 5;

        if (session.getCurrentQuestionStartTime() == null) return;
        long elapsedInQuestion = java.time.Duration.between(session.getCurrentQuestionStartTime(), LocalDateTime.now()).getSeconds();

        if (elapsedInQuestion >= delaySeconds) {
            Question question = getQuestionByIndex(session, currentIndex);
            if (question == null) return;

            boolean correct = random.nextDouble() < 0.6;
            String answer = correct ? question.getAnswer() : generateWrongAnswer(question);

            PkAnswer pkAnswer = new PkAnswer();
            pkAnswer.setSession(session);
            pkAnswer.setPlayer(session.getPlayer2());
            pkAnswer.setQuestionId(question.getId());
            pkAnswer.setQuestionIndex(currentIndex);
            pkAnswer.setAnswer(answer);
            pkAnswer.setIsCorrect(correct);
            pkAnswer.setTimeUsed(delaySeconds);
            pkAnswer.setAnsweredAt(LocalDateTime.now());
            pkAnswerRepository.save(pkAnswer);

            if (correct) {
                session.setPlayer2Score(session.getPlayer2Score() + SCORE_PER_QUESTION);
            }
            pkSessionRepository.save(session);

            checkAndAdvanceQuestion(session);
        }
    }

    private String generateWrongAnswer(Question question) {
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            try {
                List<Map<String, Object>> options = objectMapper.readValue(question.getOptions(),
                        new TypeReference<List<Map<String, Object>>>() {});
                String correct = question.getAnswer();
                List<String> wrongOptions = options.stream()
                        .map(o -> o.get("label").toString())
                        .filter(l -> !l.equals(correct))
                        .collect(Collectors.toList());
                if (!wrongOptions.isEmpty()) {
                    Collections.shuffle(wrongOptions);
                    return wrongOptions.get(0);
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkDisconnections() {
        List<PkSession> activeSessions = pkSessionRepository.findByStateOrderByCreatedAtDesc("IN_PROGRESS");
        LocalDateTime now = LocalDateTime.now();

        for (PkSession session : activeSessions) {
            boolean p1Disconnected = session.getPlayer1LastActive() == null ||
                    java.time.Duration.between(session.getPlayer1LastActive(), now).getSeconds() > DISCONNECT_TIMEOUT_SECONDS;
            boolean p2Disconnected = session.getPlayer2LastActive() == null ||
                    java.time.Duration.between(session.getPlayer2LastActive(), now).getSeconds() > DISCONNECT_TIMEOUT_SECONDS;

            if (p1Disconnected && p2Disconnected) {
                session.setState("FINISHED");
                session.setEndTime(now);
                session.setWinner(null);
                pkSessionRepository.save(session);
            } else if (p1Disconnected) {
                session.setState("FINISHED");
                session.setEndTime(now);
                session.setWinner(session.getPlayer2());
                pkSessionRepository.save(session);
            } else if (p2Disconnected) {
                session.setState("FINISHED");
                session.setEndTime(now);
                session.setWinner(session.getPlayer1());
                pkSessionRepository.save(session);
            }
        }
    }

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void processBotTurns() {
        List<PkSession> botGames = pkSessionRepository.findByStateOrderByCreatedAtDesc("IN_PROGRESS").stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsBotGame()))
                .collect(Collectors.toList());

        for (PkSession session : botGames) {
            simulateBotAnswer(session);
        }
    }

    public List<PkRankingItem> getWeeklyRanking() {
        LocalDateTime weekStart = LocalDateTime.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        List<PkSession> sessions = pkSessionRepository.findWeeklyRankedSessions(weekStart);

        Map<Long, PkRankingItem> rankingMap = new HashMap<>();

        for (PkSession session : sessions) {
            if (session.getPlayer1() != null) {
                updateRankingMap(rankingMap, session.getPlayer1(), session);
            }
            if (session.getPlayer2() != null && !"BOT".equals(session.getPlayer2().getRole())) {
                updateRankingMap(rankingMap, session.getPlayer2(), session);
            }
        }

        List<PkRankingItem> rankings = new ArrayList<>(rankingMap.values());
        rankings.sort((a, b) -> {
            int winCompare = b.getWins().compareTo(a.getWins());
            if (winCompare != 0) return winCompare;
            return b.getWinRate().compareTo(a.getWinRate());
        });

        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRank(i + 1);
        }

        return rankings;
    }

    private void updateRankingMap(Map<Long, PkRankingItem> map, User user, PkSession session) {
        PkRankingItem item = map.computeIfAbsent(user.getId(), k -> {
            PkRankingItem r = new PkRankingItem();
            r.setUserId(user.getId());
            r.setUsername(user.getUsername());
            r.setFullName(user.getFullName());
            r.setWins(0);
            r.setTotalGames(0);
            r.setWinRate(0.0);
            return r;
        });

        item.setTotalGames(item.getTotalGames() + 1);
        if (session.getWinner() != null && session.getWinner().getId().equals(user.getId())) {
            item.setWins(item.getWins() + 1);
        }
        if (item.getTotalGames() > 0) {
            item.setWinRate((double) item.getWins() / item.getTotalGames() * 100);
        }
    }

    @Transactional
    public PkSession cancelMatch(String username) {
        User player = userRepository.findByUsername(username).orElseThrow();
        Optional<PkSession> waitingSession = pkSessionRepository.findByStateOrderByCreatedAtDesc("WAITING").stream()
                .filter(s -> s.getPlayer1() != null && s.getPlayer1().getId().equals(player.getId()))
                .findFirst();

        if (waitingSession.isPresent()) {
            PkSession session = waitingSession.get();
            session.setState("CANCELLED");
            return pkSessionRepository.save(session);
        }
        return null;
    }

    @Transactional
    public PkSession forfeit(String username) {
        User player = userRepository.findByUsername(username).orElseThrow();
        Optional<PkSession> active = pkSessionRepository.findActiveSessionByPlayerId(player.getId());

        if (active.isPresent()) {
            PkSession session = active.get();
            session.setState("FINISHED");
            session.setEndTime(LocalDateTime.now());

            User opponent = getOpponent(session, player.getId());
            session.setWinner(opponent);

            pkSessionRepository.save(session);
            return session;
        }
        return null;
    }
}
