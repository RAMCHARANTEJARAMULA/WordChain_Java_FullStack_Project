
package com.wordchain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class WordChainApplication {
    public static void main(String[] args) {
        SpringApplication.run(WordChainApplication.class, args);
    }
}

@Entity
class GameRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String code;

    public String status = "WAITING";
    public String createdAt = Instant.now().toString();
    public int currentTurn = 0;

    public GameRoom() {}

    GameRoom(String code) {
        this.code = code;
    }
}

@Entity
class GamePlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;
    public String roomCode;
    public int score = 0;
    public int seat = 0;

    public GamePlayer() {}

    GamePlayer(String name, String code, int seat) {
        this.name = name;
        this.roomCode = code;
        this.seat = seat;
    }
}

@Entity
class PlayedWord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String roomCode;
    public String playerName;
    public String word;
    public String playedAt = Instant.now().toString();

    public PlayedWord() {}

    PlayedWord(String code, String player, String word) {
        this.roomCode = code;
        this.playerName = player;
        this.word = word;
    }
}

interface RoomRepo extends JpaRepository<GameRoom, Long> {
    Optional<GameRoom> findByCode(String code);
}

interface PlayerRepo extends JpaRepository<GamePlayer, Long> {
    List<GamePlayer> findByRoomCodeOrderBySeatAsc(String roomCode);

    Optional<GamePlayer> findByRoomCodeAndNameIgnoreCase(
        String roomCode, String name);

    long countByRoomCode(String roomCode);
}

interface WordRepo extends JpaRepository<PlayedWord, Long> {
    List<PlayedWord> findByRoomCodeOrderByIdAsc(String roomCode);

    boolean existsByRoomCodeAndWordIgnoreCase(
        String roomCode, String word);
}

@Service
class GameService {
    final RoomRepo rooms;
    final PlayerRepo players;
    final WordRepo words;

    GameService(RoomRepo r, PlayerRepo p, WordRepo w) {
        rooms = r;
        players = p;
        words = w;
    }

    String clean(String s) {
        return s == null ? "" : s.trim();
    }

    @Transactional
    Map<String, Object> create(String rawName) {
        String name = clean(rawName);

        if (name.isBlank() || name.length() > 24) {
            throw new IllegalArgumentException(
                "Enter a name (1–24 characters).");
        }

        String code;
        do {
            code = UUID.randomUUID().toString()
                .substring(0, 6).toUpperCase();
        } while (rooms.findByCode(code).isPresent());

        rooms.save(new GameRoom(code));
        players.save(new GamePlayer(name, code, 0));

        return state(code);
    }

    @Transactional
    Map<String, Object> join(String rawCode, String rawName) {
        String code = clean(rawCode).toUpperCase();
        String name = clean(rawName);

        if (name.isBlank() || name.length() > 24) {
            throw new IllegalArgumentException(
                "Enter a name (1–24 characters).");
        }

        GameRoom room = rooms.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException(
                "Room code not found."));

        List<GamePlayer> ps =
            players.findByRoomCodeOrderBySeatAsc(code);

        if (ps.stream().anyMatch(
            p -> p.name.equalsIgnoreCase(name))) {
            return state(code);
        }

        if (ps.size() >= 4) {
            throw new IllegalArgumentException(
                "This room already has 4 players.");
        }

        players.save(new GamePlayer(name, code, ps.size()));

        if (ps.size() >= 1) {
            room.status = "PLAYING";
        }

        rooms.save(room);
        return state(code);
    }

    // UPDATE: Rename a player and update their word history.
    @Transactional
    Map<String, Object> renamePlayer(
        String rawCode, String rawOldName, String rawNewName) {

        String code = clean(rawCode).toUpperCase();
        String oldName = clean(rawOldName);
        String newName = clean(rawNewName);

        if (oldName.isBlank()) {
            throw new IllegalArgumentException(
                "Enter the existing player name.");
        }

        if (newName.isBlank() || newName.length() > 24) {
            throw new IllegalArgumentException(
                "Enter a new name between 1 and 24 characters.");
        }

        rooms.findByCode(code).orElseThrow(
            () -> new IllegalArgumentException(
                "Room code not found."));

        GamePlayer player = players
            .findByRoomCodeAndNameIgnoreCase(code, oldName)
            .orElseThrow(() -> new IllegalArgumentException(
                "Player not found."));

        List<GamePlayer> roomPlayers =
            players.findByRoomCodeOrderBySeatAsc(code);

        boolean duplicate = roomPlayers.stream().anyMatch(
            p -> !Objects.equals(p.id, player.id)
                && p.name.equalsIgnoreCase(newName));

        if (duplicate) {
            throw new IllegalArgumentException(
                "Another player already uses that name.");
        }

        player.name = newName;
        players.save(player);

        List<PlayedWord> history =
            words.findByRoomCodeOrderByIdAsc(code);

        for (PlayedWord w : history) {
            if (w.playerName.equalsIgnoreCase(oldName)) {
                w.playerName = newName;
            }
        }

        words.saveAll(history);

        return state(code);
    }

    @Transactional
    Map<String, Object> play(
        String rawCode, String rawName, String rawWord) {

        String code = clean(rawCode).toUpperCase();
        String name = clean(rawName);
        String word = clean(rawWord).toLowerCase()
            .replaceAll("\\s+", "");

        GameRoom room = rooms.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException(
                "Room code not found."));

        List<GamePlayer> ps =
            players.findByRoomCodeOrderBySeatAsc(code);

        if (ps.isEmpty()) {
            throw new IllegalArgumentException(
                "Join the room first.");
        }

        if (!ps.get(room.currentTurn % ps.size())
            .name.equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("It is not your turn.");
        }

        if (!word.matches("[a-zA-Z]{2,24}")) {
            throw new IllegalArgumentException(
                "Use a word with 2–24 English letters.");
        }

        List<PlayedWord> history =
            words.findByRoomCodeOrderByIdAsc(code);

        if (!history.isEmpty()) {
            String lastWord = history.get(history.size() - 1).word;
            String lastLetter = lastWord.substring(
                lastWord.length() - 1);

            if (!word.toLowerCase().startsWith(
                lastLetter.toLowerCase())) {
                throw new IllegalArgumentException(
                    "Your word must start with '"
                    + lastLetter.toUpperCase() + "'.");
            }
        }

        if (words.existsByRoomCodeAndWordIgnoreCase(code, word)) {
            throw new IllegalArgumentException(
                "That word was already used.");
        }

        words.save(new PlayedWord(code, name, word));

        GamePlayer player = players
            .findByRoomCodeAndNameIgnoreCase(code, name)
            .orElseThrow();

        player.score += word.length();
        players.save(player);

        room.currentTurn = (room.currentTurn + 1) % ps.size();
        room.status = "PLAYING";
        rooms.save(room);

        return state(code);
    }

    Map<String, Object> state(String rawCode) {
        String code = clean(rawCode).toUpperCase();

        GameRoom room = rooms.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException(
                "Room code not found."));

        List<GamePlayer> ps =
            players.findByRoomCodeOrderBySeatAsc(code);

        List<PlayedWord> ws =
            words.findByRoomCodeOrderByIdAsc(code);

        Map<String, Object> out = new LinkedHashMap<>();

        out.put("code", room.code);
        out.put("status", room.status);

        out.put("players", ps.stream()
            .map(p -> Map.of(
                "name", p.name,
                "score", p.score,
                "seat", p.seat))
            .collect(Collectors.toList()));

        out.put("words", ws.stream()
            .map(w -> Map.of(
                "id", w.id,
                "word", w.word,
                "playerName", w.playerName,
                "playedAt", w.playedAt))
            .collect(Collectors.toList()));

        out.put("currentPlayer", ps.isEmpty()
            ? "" : ps.get(room.currentTurn % ps.size()).name);

        out.put("nextLetter", ws.isEmpty()
            ? "Any letter"
            : ws.get(ws.size() - 1).word
                .substring(ws.get(ws.size() - 1).word.length() - 1)
                .toUpperCase());

        out.put("currentTurn", room.currentTurn);
        return out;
    }

    @Transactional
    void delete(String rawCode) {
        String code = clean(rawCode).toUpperCase();

        if (rooms.findByCode(code).isEmpty()) {
            throw new IllegalArgumentException(
                "Room code not found.");
        }

        words.deleteAll(
            words.findByRoomCodeOrderByIdAsc(code));

        players.deleteAll(
            players.findByRoomCodeOrderBySeatAsc(code));

        rooms.delete(rooms.findByCode(code).orElseThrow());
    }
}

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {
    "http://localhost:5173",
    "http://localhost:4173",
    "https://*.vercel.app",
    "https://*.netlify.app",
    "https://*.onrender.com"
})
class GameController {
    final GameService service;

    GameController(GameService s) {
        service = s;
    }

    record NameRequest(String playerName) {}
    record WordRequest(String playerName, String word) {}
    record PlayerUpdateRequest(String oldName, String newName) {}

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok", "app", "WordChain API");
    }

    @PostMapping("/rooms")
    ResponseEntity<?> create(@RequestBody NameRequest r) {
        return run(() -> service.create(r.playerName()));
    }

    @PostMapping("/rooms/{code}/join")
    ResponseEntity<?> join(
        @PathVariable String code, @RequestBody NameRequest r) {
        return run(() -> service.join(code, r.playerName()));
    }

    @GetMapping("/rooms/{code}")
    ResponseEntity<?> state(@PathVariable String code) {
        return run(() -> service.state(code));
    }

    @GetMapping("/rooms/{code}/words")
    ResponseEntity<?> words(@PathVariable String code) {
        return run(() -> service.state(code).get("words"));
    }

    @PostMapping("/rooms/{code}/words")
    ResponseEntity<?> play(
        @PathVariable String code, @RequestBody WordRequest r) {
        return run(() ->
            service.play(code, r.playerName(), r.word()));
    }

    @PutMapping("/rooms/{code}/players")
    ResponseEntity<?> updatePlayer(
        @PathVariable String code,
        @RequestBody PlayerUpdateRequest r) {
        return run(() ->
            service.renamePlayer(code, r.oldName(), r.newName()));
    }

    @DeleteMapping("/rooms/{code}")
    ResponseEntity<?> delete(@PathVariable String code) {
        return run(() -> {
            service.delete(code);
            return Map.of("message", "Room deleted");
        });
    }

    ResponseEntity<?> run(
        java.util.function.Supplier<Object> fn) {
        try {
            return ResponseEntity.ok(fn.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("error", "Unexpected server error."));
        }
    }
}
